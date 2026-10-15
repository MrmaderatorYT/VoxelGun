package com.ccs.voxelgun.render;

import android.opengl.GLES30;

import com.ccs.voxelgun.voxel.MeshData;

/** Static VAO/VBO/IBO holding one greedy-meshed voxel mesh. */
public final class GpuMesh {
    private final int[] vao = new int[1];
    private final int[] buffers = new int[2];
    private final int indexCount;
    private boolean released;

    public GpuMesh(MeshData data) {
        indexCount = data.indexCount();
        if (indexCount == 0) {
            return;
        }
        GLES30.glGenVertexArrays(1, vao, 0);
        GLES30.glGenBuffers(2, buffers, 0);
        GLES30.glBindVertexArray(vao[0]);

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffers[0]);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,
                data.vertexCount() * MeshData.FLOATS_PER_VERTEX * Float.BYTES, data.vertices(),
                GLES30.GL_STATIC_DRAW);
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, buffers[1]);
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indexCount * Integer.BYTES,
                data.indices(), GLES30.GL_STATIC_DRAW);

        int stride = MeshData.FLOATS_PER_VERTEX * Float.BYTES;
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0);
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 3 * Float.BYTES);
        GLES30.glEnableVertexAttribArray(2);
        GLES30.glVertexAttribPointer(2, 1, GLES30.GL_FLOAT, false, stride, 6 * Float.BYTES);
        GLES30.glEnableVertexAttribArray(3);
        GLES30.glVertexAttribPointer(3, 1, GLES30.GL_FLOAT, false, stride, 7 * Float.BYTES);

        GLES30.glBindVertexArray(0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    public boolean isEmpty() {
        return indexCount == 0;
    }

    public int indexCount() {
        return indexCount;
    }

    public void draw() {
        if (indexCount == 0 || released) {
            return;
        }
        GLES30.glBindVertexArray(vao[0]);
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_INT, 0);
    }

    public void release() {
        if (released || indexCount == 0) {
            return;
        }
        released = true;
        GLES30.glDeleteVertexArrays(1, vao, 0);
        GLES30.glDeleteBuffers(2, buffers, 0);
    }
}
