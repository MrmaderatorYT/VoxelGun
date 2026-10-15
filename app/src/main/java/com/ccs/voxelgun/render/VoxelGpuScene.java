package com.ccs.voxelgun.render;

import android.opengl.GLES30;

import com.ccs.voxelgun.voxel.EmissiveLights;
import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.voxel.VoxelWorld;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * The acceleration structure the ray tracer traverses.
 *
 * <p>Three GPU resources: the material volume as an 8-bit 3D texture, a brick-resolution
 * Chebyshev empty-space distance field for skipping, and the material palette as a small
 * floating point lookup texture.
 */
public final class VoxelGpuScene {
    /** Must match BRICK_SHIFT in the tracing shader. */
    public static final int BRICK = 8;

    private final int[] volumeTexture = new int[1];
    private final int[] brickTexture = new int[1];
    private final int[] paletteTexture = new int[1];
    private final int[] lightTexture = new int[1];
    private int lightCount;
    private int sizeX;
    private int sizeY;
    private int sizeZ;
    private int brickX;
    private int brickY;
    private int brickZ;
    private boolean tracingSupported;
    private boolean tracingEnabled;
    private int[] chunkVersions;
    private int observedRevision;
    private int editCursor;
    private final ByteBuffer editBuffer = ByteBuffer.allocateDirect(16 * 16 * 16);

    public void upload(VoxelWorld world) {
        upload(world, true);
    }

    public void setTracingEnabled(VoxelWorld world, boolean enabled) {
        boolean desired = enabled && tracingSupported;
        if (desired != tracingEnabled) upload(world, desired);
    }

    public boolean tracingEnabled() { return tracingEnabled; }

    public void upload(VoxelWorld world, boolean enableTracing) {
        release();
        sizeX = world.sizeX();
        sizeY = world.sizeY();
        sizeZ = world.sizeZ();
        chunkVersions = new int[world.chunksX() * world.chunksY() * world.chunksZ()];
        for (int i = 0; i < chunkVersions.length; i++) chunkVersions[i] = world.chunkVersion(i);
        observedRevision = world.revision(); editCursor = chunkVersions.length;

        int[] limit = new int[1];
        GLES30.glGetIntegerv(GLES30.GL_MAX_3D_TEXTURE_SIZE, limit, 0);
        tracingSupported = sizeX <= limit[0] && sizeY <= limit[0] && sizeZ <= limit[0];
        tracingEnabled = enableTracing && tracingSupported;
        if (!tracingEnabled) {
            // Geometry still renders in full detail. Avoid an invalid oversized RT volume.
            uploadRasterFallback(world);
            return;
        }

        ByteBuffer volume = ByteBuffer.allocateDirect(world.blocks().length)
                .order(ByteOrder.nativeOrder());
        volume.put(world.blocks());
        volume.position(0);

        GLES30.glGenTextures(1, volumeTexture, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, volumeTexture[0]);
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1);
        GLES30.glTexImage3D(GLES30.GL_TEXTURE_3D, 0, GLES30.GL_R8UI, sizeX, sizeY, sizeZ, 0,
                GLES30.GL_RED_INTEGER, GLES30.GL_UNSIGNED_BYTE, volume);
        setIntegerSampling(GLES30.GL_TEXTURE_3D);

        byte[] bricks = world.brickField(BRICK);
        brickX = (sizeX + BRICK - 1) / BRICK;
        brickY = (sizeY + BRICK - 1) / BRICK;
        brickZ = (sizeZ + BRICK - 1) / BRICK;
        if (bricks == null) {
            bricks = fallbackBrickField();
        }
        ByteBuffer brickBuffer = ByteBuffer.allocateDirect(bricks.length)
                .order(ByteOrder.nativeOrder());
        brickBuffer.put(bricks);
        brickBuffer.position(0);

        GLES30.glGenTextures(1, brickTexture, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, brickTexture[0]);
        GLES30.glTexImage3D(GLES30.GL_TEXTURE_3D, 0, GLES30.GL_R8UI, brickX, brickY, brickZ, 0,
                GLES30.GL_RED_INTEGER, GLES30.GL_UNSIGNED_BYTE, brickBuffer);
        setIntegerSampling(GLES30.GL_TEXTURE_3D);

        uploadPalette();
        uploadLights(EmissiveLights.build(world));
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, 0);
    }

    /**
     * All-zero field: every brick is treated as occupied, which disables empty-space skipping
     * but keeps traversal correct when the native library could not be loaded.
     */
    private byte[] fallbackBrickField() {
        return new byte[brickX * brickY * brickZ];
    }

    public boolean tracingSupported() {
        return tracingSupported;
    }

    /** Bounded partial uploads; deletions cannot invalidate conservative empty-space distances. */
    public boolean updateEdits(VoxelWorld world) {
        if (!tracingEnabled) return false;
        if (observedRevision != world.revision()) {
            observedRevision = world.revision(); editCursor = 0;
        }
        if (editCursor >= chunkVersions.length) return false; // No idle-frame GL texture binds.
        int uploads = 0;
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, volumeTexture[0]);
        while (editCursor < chunkVersions.length && uploads < 6) {
            int i = editCursor++;
            int version = world.chunkVersion(i);
            if (version == chunkVersions[i]) continue;
            int ox = (i % world.chunksX()) * 16;
            int oy = (i / world.chunksX() % world.chunksY()) * 16;
            int oz = (i / (world.chunksX() * world.chunksY())) * 16;
            int sx = Math.min(16, sizeX - ox), sy = Math.min(16, sizeY - oy);
            int sz = Math.min(16, sizeZ - oz);
            editBuffer.clear();
            synchronized (world) {
                for (int z = 0; z < sz; z++) for (int y = 0; y < sy; y++)
                    for (int x = 0; x < sx; x++) editBuffer.put(world.get(ox + x, oy + y, oz + z));
            }
            editBuffer.flip();
            GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1);
            GLES30.glTexSubImage3D(GLES30.GL_TEXTURE_3D, 0, ox, oy, oz, sx, sy, sz,
                    GLES30.GL_RED_INTEGER, GLES30.GL_UNSIGNED_BYTE, editBuffer);
            chunkVersions[i] = version; uploads++;
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, 0);
        return uploads > 0;
    }

    private void uploadRasterFallback(VoxelWorld world) {
        ByteBuffer empty = ByteBuffer.allocateDirect(1);
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1);
        for (int[] texture : new int[][]{volumeTexture, brickTexture}) {
            GLES30.glGenTextures(1, texture, 0);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, texture[0]);
            GLES30.glTexImage3D(GLES30.GL_TEXTURE_3D, 0, GLES30.GL_R8UI, 1, 1, 1, 0,
                    GLES30.GL_RED_INTEGER, GLES30.GL_UNSIGNED_BYTE, empty);
            setIntegerSampling(GLES30.GL_TEXTURE_3D);
        }
        brickX = brickY = brickZ = 1;
        uploadPalette();
        // Raster shaders never sample emissive light lists. Avoid scanning the whole map.
        lightCount = 0;
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, 0);
    }

    private void uploadPalette() {
        float[] table = Materials.table();
        FloatBuffer buffer = ByteBuffer.allocateDirect(table.length * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        buffer.put(table);
        buffer.position(0);

        GLES30.glGenTextures(1, paletteTexture, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, paletteTexture[0]);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, Materials.COUNT * 2, 1, 0,
                GLES30.GL_RGBA, GLES30.GL_FLOAT, buffer);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER,
                GLES30.GL_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER,
                GLES30.GL_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S,
                GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T,
                GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
    }

    /** Uploads the emissive light list as a lookup texture, two texels per light. */
    private void uploadLights(EmissiveLights lights) {
        lightCount = lights.count();
        float[] table = lights.data();
        FloatBuffer buffer = ByteBuffer.allocateDirect(table.length * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        buffer.put(table);
        buffer.position(0);

        GLES30.glGenTextures(1, lightTexture, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, lightTexture[0]);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA32F,
                EmissiveLights.MAX_LIGHTS * 2, 1, 0, GLES30.GL_RGBA, GLES30.GL_FLOAT, buffer);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER,
                GLES30.GL_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER,
                GLES30.GL_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S,
                GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T,
                GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
    }

    public int lightCount() {
        return lightCount;
    }

    private static void setIntegerSampling(int target) {
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST);
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST);
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE);
    }

    /** Binds volume, brick field, palette and light list to four consecutive texture units. */
    public void bind(Shader shader, int firstUnit) {
        shader.bindTexture("uVoxels", firstUnit, GLES30.GL_TEXTURE_3D, volumeTexture[0]);
        shader.bindTexture("uBricks", firstUnit + 1, GLES30.GL_TEXTURE_3D, brickTexture[0]);
        shader.bindTexture("uPalette", firstUnit + 2, GLES30.GL_TEXTURE_2D, paletteTexture[0]);
        shader.bindTexture("uLights", firstUnit + 3, GLES30.GL_TEXTURE_2D, lightTexture[0]);
        shader.set("uVolumeSize", sizeX, sizeY, sizeZ);
        shader.set("uBrickSize", brickX, brickY, brickZ);
        shader.set("uLightCount", lightCount);
    }

    /** Binds only the palette, for passes that shade without tracing. */
    public void bindPalette(Shader shader, int unit) {
        shader.bindTexture("uPalette", unit, GLES30.GL_TEXTURE_2D, paletteTexture[0]);
    }

    public void release() {
        if (volumeTexture[0] != 0) {
            GLES30.glDeleteTextures(1, volumeTexture, 0);
            volumeTexture[0] = 0;
        }
        if (brickTexture[0] != 0) {
            GLES30.glDeleteTextures(1, brickTexture, 0);
            brickTexture[0] = 0;
        }
        if (paletteTexture[0] != 0) {
            GLES30.glDeleteTextures(1, paletteTexture, 0);
            paletteTexture[0] = 0;
        }
        if (lightTexture[0] != 0) {
            GLES30.glDeleteTextures(1, lightTexture, 0);
            lightTexture[0] = 0;
        }
        lightCount = 0;
    }
}
