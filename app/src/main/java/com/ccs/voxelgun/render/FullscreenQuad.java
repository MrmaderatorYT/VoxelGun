package com.ccs.voxelgun.render;

import android.opengl.GLES30;

/** Draws a screen-covering triangle; the vertex shader derives UVs from gl_VertexID. */
public final class FullscreenQuad {
    private final int[] vao = new int[1];

    public FullscreenQuad() {
        GLES30.glGenVertexArrays(1, vao, 0);
    }

    public void draw() {
        GLES30.glBindVertexArray(vao[0]);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);
        GLES30.glBindVertexArray(0);
    }

    public void release() {
        GLES30.glDeleteVertexArrays(1, vao, 0);
    }
}
