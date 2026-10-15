package com.ccs.voxelgun.voxel;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** Interleaved mesh ready for upload: position, normal, material id and corner occlusion. */
public final class MeshData {
    public static final int FLOATS_PER_VERTEX = 8;
    private static final MeshData EMPTY = new MeshData(FloatBuffer.allocate(0).asReadOnlyBuffer(),
            IntBuffer.allocate(0).asReadOnlyBuffer(), 0, 0);

    private final FloatBuffer vertices;
    private final IntBuffer indices;
    private final int vertexCount;
    private final int indexCount;

    public MeshData(FloatBuffer vertices, IntBuffer indices, int vertexCount, int indexCount) {
        this.vertices = vertices;
        this.indices = indices;
        this.vertexCount = vertexCount;
        this.indexCount = indexCount;
    }

    public static MeshData empty() {
        return EMPTY;
    }

    /** Decodes the packed buffer produced by the native mesher. */
    public static MeshData fromPacked(byte[] packed) {
        if (packed == null || packed.length < 8) {
            return empty();
        }
        ByteBuffer source = ByteBuffer.wrap(packed).order(ByteOrder.nativeOrder());
        int vertexCount = source.getInt();
        int indexCount = source.getInt();
        if (vertexCount <= 0 || indexCount <= 0) {
            return empty();
        }
        int vertexBytes = vertexCount * FLOATS_PER_VERTEX * Float.BYTES;
        ByteBuffer vertexBuffer = ByteBuffer.allocateDirect(vertexBytes).order(ByteOrder.nativeOrder());
        ByteBuffer indexBuffer = ByteBuffer.allocateDirect(indexCount * Integer.BYTES)
                .order(ByteOrder.nativeOrder());

        ByteBuffer slice = source.slice().order(ByteOrder.nativeOrder());
        slice.limit(vertexBytes);
        vertexBuffer.put(slice).flip();
        source.position(source.position() + vertexBytes);
        indexBuffer.put(source.slice()).flip();

        return new MeshData(vertexBuffer.asFloatBuffer(), indexBuffer.asIntBuffer(), vertexCount,
                indexCount);
    }

    public FloatBuffer vertices() {
        return vertices;
    }

    public IntBuffer indices() {
        return indices;
    }

    public int vertexCount() {
        return vertexCount;
    }

    public int indexCount() {
        return indexCount;
    }

    public boolean isEmpty() {
        return indexCount == 0;
    }
}
