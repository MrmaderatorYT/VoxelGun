package com.ccs.voxelstrike.render;

import android.opengl.GLES30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * One static quad drawn many times with per-instance data.
 *
 * <p>The instance layout is shared by particles, tracers and decals: {@code vec3, float, vec3,
 * float}. Each frame the caller fills {@link #data()} and calls {@link #upload(int)}.
 */
public final class InstancedQuads {
    public static final int FLOATS_PER_INSTANCE = 8;

    private final int[] vao = new int[1];
    private final int[] buffers = new int[2];
    private final float[] data;
    private final FloatBuffer buffer;
    private final int capacity;
    private int count;

    public InstancedQuads(int capacity) {
        this.capacity = capacity;
        data = new float[capacity * FLOATS_PER_INSTANCE];
        buffer = ByteBuffer.allocateDirect(data.length * Float.BYTES).order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        float[] corners = {-0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f};
        FloatBuffer cornerBuffer = ByteBuffer.allocateDirect(corners.length * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        cornerBuffer.put(corners).position(0);

        GLES30.glGenVertexArrays(1, vao, 0);
        GLES30.glGenBuffers(2, buffers, 0);
        GLES30.glBindVertexArray(vao[0]);

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffers[0]);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, corners.length * Float.BYTES, cornerBuffer,
                GLES30.GL_STATIC_DRAW);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 2 * Float.BYTES, 0);

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffers[1]);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.length * Float.BYTES, null,
                GLES30.GL_STREAM_DRAW);
        int stride = FLOATS_PER_INSTANCE * Float.BYTES;
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 0);
        GLES30.glVertexAttribDivisor(1, 1);
        GLES30.glEnableVertexAttribArray(2);
        GLES30.glVertexAttribPointer(2, 1, GLES30.GL_FLOAT, false, stride, 3 * Float.BYTES);
        GLES30.glVertexAttribDivisor(2, 1);
        GLES30.glEnableVertexAttribArray(3);
        GLES30.glVertexAttribPointer(3, 3, GLES30.GL_FLOAT, false, stride, 4 * Float.BYTES);
        GLES30.glVertexAttribDivisor(3, 1);
        GLES30.glEnableVertexAttribArray(4);
        GLES30.glVertexAttribPointer(4, 1, GLES30.GL_FLOAT, false, stride, 7 * Float.BYTES);
        GLES30.glVertexAttribDivisor(4, 1);

        GLES30.glBindVertexArray(0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
    }

    public float[] data() {
        return data;
    }

    public int capacity() {
        return capacity;
    }

    public void upload(int instances) {
        count = Math.min(instances, capacity);
        if (count == 0) {
            return;
        }
        buffer.position(0);
        buffer.put(data, 0, count * FLOATS_PER_INSTANCE);
        buffer.position(0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffers[1]);
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, count * FLOATS_PER_INSTANCE * Float.BYTES,
                buffer);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
    }

    public void draw() {
        if (count == 0) {
            return;
        }
        GLES30.glBindVertexArray(vao[0]);
        GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLE_STRIP, 0, 4, count);
        GLES30.glBindVertexArray(0);
    }

    public void release() {
        GLES30.glDeleteVertexArrays(1, vao, 0);
        GLES30.glDeleteBuffers(2, buffers, 0);
    }
}
