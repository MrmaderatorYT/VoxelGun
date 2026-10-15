package com.ccs.voxelgun.render;

import android.opengl.GLES30;
import java.nio.ByteBuffer;

/** A tiny programmatically generated hash tile; replaces repeated fragment ALU on mobile GPUs. */
public final class ProceduralSurfaceNoise {
    public static final int SIZE = 256; // Must match the cached branch in surface_material.glsl.
    private final int[] texture = new int[1];

    public void create() {
        ByteBuffer pixels = ByteBuffer.allocateDirect(SIZE * SIZE);
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            pixels.put((byte) Math.round(hash(x, y) * 255f));
        }
        pixels.flip();
        GLES30.glGenTextures(1, texture, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture[0]);
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R8, SIZE, SIZE, 0,
                GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, pixels);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
    }

    /** The existing GLSL hash, evaluated once at startup. R8 quantisation is visually negligible. */
    public static float hash(int x, int y) {
        float px = fract(x * .1031f), py = fract(y * .1030f), pz = fract(x * .0973f);
        float dot = px * (py + 33.33f) + py * (pz + 33.33f) + pz * (px + 33.33f);
        px += dot; py += dot; pz += dot;
        return fract((px + py) * pz);
    }

    private static float fract(float x) { return x - (float) Math.floor(x); }

    public void bind(Shader shader, int unit) {
        shader.bindTexture("uSurfaceNoise", unit, GLES30.GL_TEXTURE_2D, texture[0]);
    }

    public void release() {
        if (texture[0] != 0) { GLES30.glDeleteTextures(1, texture, 0); texture[0] = 0; }
    }
}
