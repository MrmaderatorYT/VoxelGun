package com.ccs.voxelgun.voxel;

/**
 * The map volume. Java owns the authoritative {@code byte[]} for cheap single-voxel reads while a
 * mirrored native copy answers every multi-voxel query (ray casts, sight tests, brick field).
 *
 * <p>Build-time writes use set/commit. Runtime destructible removals update the native mirror,
 * height map and chunk revisions; renderers consume those revisions as bounded partial uploads.
 */
public final class VoxelWorld {
    public static final int CHUNK = 16;

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final byte[] blocks;
    private final int[] heights;
    private final float[] rayScratch = new float[11];
    private long nativeHandle;
    private final int[] chunkVersions;
    private int revision;

    public VoxelWorld(int sizeX, int sizeY, int sizeZ) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.blocks = new byte[sizeX * sizeY * sizeZ];
        this.heights = new int[sizeX * sizeZ];
        this.chunkVersions = new int[chunksX() * chunksY() * chunksZ()];
        if (NativeBridge.available()) {
            nativeHandle = NativeBridge.createVolume(sizeX, sizeY, sizeZ);
        }
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public byte[] blocks() {
        return blocks;
    }

    public long nativeHandle() {
        return nativeHandle;
    }

    public int chunksX() {
        return (sizeX + CHUNK - 1) / CHUNK;
    }

    public int chunksY() {
        return (sizeY + CHUNK - 1) / CHUNK;
    }

    public int chunksZ() {
        return (sizeZ + CHUNK - 1) / CHUNK;
    }

    public boolean inside(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < sizeX && y < sizeY && z < sizeZ;
    }

    public byte get(int x, int y, int z) {
        if (!inside(x, y, z)) {
            return 0;
        }
        return blocks[x + sizeX * (y + sizeY * z)];
    }

    public void set(int x, int y, int z, int material) {
        if (!inside(x, y, z)) {
            return;
        }
        blocks[x + sizeX * (y + sizeY * z)] = (byte) material;
    }

    public synchronized int revision() { return revision; }
    public synchronized int chunkVersion(int index) { return chunkVersions[index]; }

    /** Runtime edits are removals only: surviving distance-field values stay conservative. */
    public synchronized boolean removeDestructible(int x, int y, int z) {
        if (!inside(x, y, z) || y < 3 || !destructible(get(x, y, z) & 255)) return false;
        blocks[x + sizeX * (y + sizeY * z)] = Materials.AIR;
        if (nativeHandle != 0) NativeBridge.setVoxel(nativeHandle, x, y, z, 0);
        int column = x + sizeX * z;
        if (heights[column] == y + 1) {
            int top = y;
            while (top > 0 && !Materials.solid(get(x, top - 1, z))) top--;
            heights[column] = top;
        }
        markChunk(x, y, z);
        if (x % CHUNK == 0) markChunk(x - 1, y, z);
        if (x % CHUNK == CHUNK - 1) markChunk(x + 1, y, z);
        if (y % CHUNK == 0) markChunk(x, y - 1, z);
        if (y % CHUNK == CHUNK - 1) markChunk(x, y + 1, z);
        if (z % CHUNK == 0) markChunk(x, y, z - 1);
        if (z % CHUNK == CHUNK - 1) markChunk(x, y, z + 1);
        revision++;
        return true;
    }

    private void markChunk(int x, int y, int z) {
        if (inside(x, y, z)) chunkVersions[x / CHUNK + chunksX()
                * (y / CHUNK + chunksY() * (z / CHUNK))]++;
    }

    public static boolean destructible(int material) {
        return material == (Materials.WOOD & 255) || material == (Materials.WOOD_DARK & 255)
                || material == (Materials.WOOD_LIGHT & 255) || material == (Materials.CRATE & 255)
                || material == (Materials.SANDBAG & 255) || material == (Materials.CANVAS & 255)
                || material == (Materials.HAY & 255) || material == (Materials.GLASS & 255);
    }

    /**
     * True when the voxel stops movement (water does not).
     *
     * <p>There is deliberately no floor under {@code y = 0}: maps built on floating islands need
     * bodies to fall out of the world, and the match loop turns that fall into a death.
     */
    public boolean solid(int x, int y, int z) {
        return Materials.solid(get(x, y, z));
    }

    public boolean solidAt(float x, float y, float z) {
        return solid(floor(x), floor(y), floor(z));
    }

    /** True when the voxel stops bullets and sight (glass and foliage do not). */
    public boolean opaqueAt(int x, int y, int z) {
        return Materials.opaque(get(x, y, z));
    }

    public int height(int x, int z) {
        if (x < 0 || z < 0 || x >= sizeX || z >= sizeZ) {
            return 0;
        }
        return heights[x + sizeX * z];
    }

    /** Mirrors the volume into native memory and refreshes the derived height map. */
    public void commit() {
        if (nativeHandle != 0) {
            NativeBridge.uploadVolume(nativeHandle, blocks);
            byte[] map = NativeBridge.buildHeightMap(nativeHandle);
            if (map != null) {
                for (int i = 0; i < heights.length && i < map.length; i++) {
                    heights[i] = map[i] & 0xFF;
                }
                return;
            }
        }
        computeHeightsInJava();
    }

    private void computeHeightsInJava() {
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                int top = 0;
                for (int y = sizeY - 1; y >= 0; y--) {
                    if (Materials.solid(get(x, y, z))) {
                        top = y + 1;
                        break;
                    }
                }
                heights[x + sizeX * z] = top;
            }
        }
    }

    /**
     * First level at or above {@code fromY} where a body of {@code clearance} blocks fits with
     * solid ground under it, or -1.
     *
     * <p>This is not the same as the column height: inside a building the column's top block is
     * the roof, and a spawn resolved against that would drop the player onto it.
     */
    public int standableLevel(int x, int z, int fromY, int clearance) {
        for (int y = Math.max(1, fromY); y < sizeY - clearance; y++) {
            if (!solid(x, y - 1, z)) {
                continue;
            }
            boolean clear = true;
            for (int head = 0; head < clearance; head++) {
                if (solid(x, y + head, z)) {
                    clear = false;
                    break;
                }
            }
            if (clear) {
                return y;
            }
        }
        return -1;
    }

    public byte[] brickField(int brick) {
        if (nativeHandle == 0) {
            return null;
        }
        return NativeBridge.buildBrickField(nativeHandle, brick);
    }

    /**
     * Casts a ray against the map.
     *
     * @param hitTransparent when true glass and water also stop the ray (used by the camera, not
     *                       by bullets)
     */
    public boolean raycast(float ox, float oy, float oz, float dx, float dy, float dz,
                           float maxDistance, boolean hitTransparent, RayHit out) {
        if (nativeHandle != 0) {
            synchronized (rayScratch) {
                if (!NativeBridge.raycast(nativeHandle, ox, oy, oz, dx, dy, dz, maxDistance,
                        hitTransparent, rayScratch)) {
                    return false;
                }
                out.set(rayScratch);
                return true;
            }
        }
        return javaRaycast(ox, oy, oz, dx, dy, dz, maxDistance, hitTransparent, out);
    }

    public boolean lineOfSight(float ax, float ay, float az, float bx, float by, float bz) {
        if (nativeHandle != 0) {
            return NativeBridge.lineOfSight(nativeHandle, ax, ay, az, bx, by, bz);
        }
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1e-4f) {
            return true;
        }
        RayHit hit = new RayHit();
        return !javaRaycast(ax, ay, az, dx, dy, dz, distance - 0.01f, false, hit);
    }

    /** Pure-Java fallback traversal; keeps unit tests and JNI-less devices working. */
    private boolean javaRaycast(float ox, float oy, float oz, float dx, float dy, float dz,
                                float maxDistance, boolean hitTransparent, RayHit out) {
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-6f) {
            return false;
        }
        dx /= length;
        dy /= length;
        dz /= length;

        int ix = floor(ox);
        int iy = floor(oy);
        int iz = floor(oz);
        byte start = get(ix, iy, iz);
        if (start != 0 && (hitTransparent || Materials.opaque(start))) {
            out.set(0f, ox, oy, oz, -dx, -dy, -dz, start & 0xFF, ix, iy, iz);
            return true;
        }

        int stepX = dx > 0 ? 1 : -1;
        int stepY = dy > 0 ? 1 : -1;
        int stepZ = dz > 0 ? 1 : -1;
        float invX = dx != 0 ? Math.abs(1f / dx) : 1e30f;
        float invY = dy != 0 ? Math.abs(1f / dy) : 1e30f;
        float invZ = dz != 0 ? Math.abs(1f / dz) : 1e30f;
        float tMaxX = dx != 0 ? (dx > 0 ? (ix + 1 - ox) : (ox - ix)) * invX : 1e30f;
        float tMaxY = dy != 0 ? (dy > 0 ? (iy + 1 - oy) : (oy - iy)) * invY : 1e30f;
        float tMaxZ = dz != 0 ? (dz > 0 ? (iz + 1 - oz) : (oz - iz)) * invZ : 1e30f;

        for (int guard = 0; guard < 2048; guard++) {
            float t;
            int axis;
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                t = tMaxX;
                tMaxX += invX;
                ix += stepX;
                axis = 0;
            } else if (tMaxY <= tMaxZ) {
                t = tMaxY;
                tMaxY += invY;
                iy += stepY;
                axis = 1;
            } else {
                t = tMaxZ;
                tMaxZ += invZ;
                iz += stepZ;
                axis = 2;
            }
            if (t > maxDistance) {
                return false;
            }
            if ((ix < 0 && stepX < 0) || (ix >= sizeX && stepX > 0)) return false;
            if ((iy < 0 && stepY < 0) || (iy >= sizeY && stepY > 0)) return false;
            if ((iz < 0 && stepZ < 0) || (iz >= sizeZ && stepZ > 0)) return false;
            if (!inside(ix, iy, iz)) {
                continue;
            }
            byte material = get(ix, iy, iz);
            if (material == 0) {
                continue;
            }
            if (!hitTransparent && !Materials.opaque(material)) {
                continue;
            }
            float nx = axis == 0 ? -stepX : 0f;
            float ny = axis == 1 ? -stepY : 0f;
            float nz = axis == 2 ? -stepZ : 0f;
            out.set(t, ox + dx * t, oy + dy * t, oz + dz * t, nx, ny, nz, material & 0xFF, ix, iy, iz);
            return true;
        }
        return false;
    }

    /** True when an axis aligned box centred on x/z with its feet at y overlaps solid voxels. */
    public boolean boxCollides(float x, float y, float z, float halfWidth, float height) {
        int x0 = floor(x - halfWidth);
        int x1 = floor(x + halfWidth);
        int y0 = floor(y);
        int y1 = floor(y + height);
        int z0 = floor(z - halfWidth);
        int z1 = floor(z + halfWidth);
        for (int zi = z0; zi <= z1; zi++) {
            for (int yi = y0; yi <= y1; yi++) {
                for (int xi = x0; xi <= x1; xi++) {
                    if (solid(xi, yi, zi)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void release() {
        if (nativeHandle != 0) {
            NativeBridge.destroyVolume(nativeHandle);
            nativeHandle = 0;
        }
    }

    public static int floor(float value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }
}
