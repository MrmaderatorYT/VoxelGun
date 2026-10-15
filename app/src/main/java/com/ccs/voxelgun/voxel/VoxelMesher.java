package com.ccs.voxelgun.voxel;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Turns voxel regions into renderable meshes.
 *
 * <p>The native greedy mesher does the real work. The Java path is a culled-face fallback that
 * keeps the game (and the JVM unit tests) running when the {@code .so} cannot be loaded.
 */
public final class VoxelMesher {
    public static final int PASS_OPAQUE = 0;
    public static final int PASS_TRANSLUCENT = 1;

    private VoxelMesher() {}

    /** Meshes one chunk of the world; neighbour voxels are included so borders stay seamless. */
    public static MeshData meshChunk(VoxelWorld world, int chunkX, int chunkY, int chunkZ,
                                     int pass) {
        ChunkSnapshot snapshot = new ChunkSnapshot();
        snapshot.capture(world, chunkX, chunkY, chunkZ);
        return meshChunk(snapshot, pass);
    }

    /** Mesh both passes from the same captured bytes; owned/reused by one meshing worker. */
    public static MeshData meshChunk(ChunkSnapshot snapshot, int pass) {
        int flag = pass == PASS_OPAQUE ? 1 : 2;
        if ((snapshot.passes & flag) == 0) return MeshData.empty();
        int size = VoxelWorld.CHUNK;
        return mesh(snapshot.padded, size, size, size, 1f, pass);
    }

    /** Includes neighbours for faces/AO. Pass rejection must conservatively include that halo. */
    public static final class ChunkSnapshot {
        private static final int SIZE = VoxelWorld.CHUNK, STRIDE = SIZE + 2;
        private final byte[] padded = new byte[STRIDE * STRIDE * STRIDE];
        private int passes;
        private int version;

        public int version() { return version; }
        public boolean hasOpaque() { return (passes & 1) != 0; }
        public boolean hasTranslucent() { return (passes & 2) != 0; }

        public void capture(VoxelWorld world, int chunkX, int chunkY, int chunkZ) {
            if (chunkX < 0 || chunkY < 0 || chunkZ < 0 || chunkX >= world.chunksX()
                    || chunkY >= world.chunksY() || chunkZ >= world.chunksZ()) {
                throw new IllegalArgumentException("Chunk outside world");
            }
            java.util.Arrays.fill(padded, (byte) 0); // Clear clipped borders when reusing a worker.
            int ox = chunkX * SIZE, oy = chunkY * SIZE, oz = chunkZ * SIZE;
            int minX = Math.max(0, ox - 1), maxX = Math.min(world.sizeX(), ox + SIZE + 1);
            int length = maxX - minX;
            int destinationX = minX - ox + 1;
            synchronized (world) {
                version = world.chunkVersion(chunkX + world.chunksX()
                        * (chunkY + world.chunksY() * chunkZ));
                byte[] source = world.blocks();
                for (int z = 0; z < STRIDE; z++) {
                    int wz = oz + z - 1;
                    if (wz < 0 || wz >= world.sizeZ()) continue;
                    for (int y = 0; y < STRIDE; y++) {
                        int wy = oy + y - 1;
                        if (wy < 0 || wy >= world.sizeY()) continue;
                        System.arraycopy(source, minX + world.sizeX() * (wy + world.sizeY() * wz),
                                padded, destinationX + STRIDE * (y + STRIDE * z), length);
                    }
                }
            }
            passes = 0;
            // Native greedy meshing considers both sides of chunk-boundary slices. A halo
            // material can therefore emit a boundary face even if the core is empty.
            for (int z = 0; z < STRIDE && passes != 3; z++) {
                for (int y = 0; y < STRIDE && passes != 3; y++) {
                    int row = STRIDE * (y + STRIDE * z);
                    for (int x = 0; x < STRIDE; x++) {
                        int material = padded[row + x] & 255;
                        if (material != 0) passes |= Materials.opaque(material) ? 1 : 2;
                    }
                }
            }
        }
    }

    /** Meshes a standalone model volume (weapons, characters, props). */
    public static MeshData meshModel(byte[] voxels, int sx, int sy, int sz, float scale, int pass) {
        int stride = sx + 2;
        byte[] padded = new byte[(sx + 2) * (sy + 2) * (sz + 2)];
        for (int z = 0; z < sz; z++) {
            for (int y = 0; y < sy; y++) {
                for (int x = 0; x < sx; x++) {
                    padded[(x + 1) + stride * ((y + 1) + (sy + 2) * (z + 1))] =
                            voxels[x + sx * (y + sy * z)];
                }
            }
        }
        return mesh(padded, sx, sy, sz, scale, pass);
    }

    private static MeshData mesh(byte[] padded, int sx, int sy, int sz, float scale, int pass) {
        if (NativeBridge.available()) {
            return MeshData.fromPacked(NativeBridge.greedyMesh(padded, sx, sy, sz, scale, pass));
        }
        return fallbackMesh(padded, sx, sy, sz, scale, pass);
    }

    private static final int[][] FACES = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    /** Per-face culled meshing without merging; correctness over throughput. */
    private static MeshData fallbackMesh(byte[] padded, int sx, int sy, int sz, float scale,
                                         int pass) {
        int stride = sx + 2;
        int strideY = sy + 2;
        FloatList vertices = new FloatList(4096);
        IntList indices = new IntList(6144);

        for (int z = 0; z < sz; z++) {
            for (int y = 0; y < sy; y++) {
                for (int x = 0; x < sx; x++) {
                    int material = padded[(x + 1) + stride * ((y + 1) + strideY * (z + 1))] & 0xFF;
                    boolean wanted = pass == PASS_OPAQUE ? Materials.opaque(material)
                            : Materials.translucent(material);
                    if (!wanted) {
                        continue;
                    }
                    for (int[] face : FACES) {
                        int nx = x + face[0];
                        int ny = y + face[1];
                        int nz = z + face[2];
                        int neighbour = padded[(nx + 1) + stride * ((ny + 1) + strideY * (nz + 1))] & 0xFF;
                        boolean hidden = pass == PASS_OPAQUE ? Materials.opaque(neighbour)
                                : Materials.opaque(neighbour) || neighbour == material;
                        if (hidden) {
                            continue;
                        }
                        emitFace(vertices, indices, x, y, z, face, material, scale);
                    }
                }
            }
        }
        return build(vertices, indices);
    }

    private static void emitFace(FloatList vertices, IntList indices, int x, int y, int z,
                                 int[] normal, int material, float scale) {
        int base = vertices.size / MeshData.FLOATS_PER_VERTEX;
        int nx = normal[0], ny = normal[1], nz = normal[2];
        if (nx == 1) {
            vertex(vertices, x + 1, y, z, scale, nx, ny, nz, material);
            vertex(vertices, x + 1, y, z + 1, scale, nx, ny, nz, material);
            vertex(vertices, x + 1, y + 1, z + 1, scale, nx, ny, nz, material);
            vertex(vertices, x + 1, y + 1, z, scale, nx, ny, nz, material);
        } else if (nx == -1) {
            vertex(vertices, x, y, z + 1, scale, nx, ny, nz, material);
            vertex(vertices, x, y, z, scale, nx, ny, nz, material);
            vertex(vertices, x, y + 1, z, scale, nx, ny, nz, material);
            vertex(vertices, x, y + 1, z + 1, scale, nx, ny, nz, material);
        } else if (ny == 1) {
            vertex(vertices, x, y + 1, z, scale, nx, ny, nz, material);
            vertex(vertices, x + 1, y + 1, z, scale, nx, ny, nz, material);
            vertex(vertices, x + 1, y + 1, z + 1, scale, nx, ny, nz, material);
            vertex(vertices, x, y + 1, z + 1, scale, nx, ny, nz, material);
        } else if (ny == -1) {
            vertex(vertices, x, y, z + 1, scale, nx, ny, nz, material);
            vertex(vertices, x + 1, y, z + 1, scale, nx, ny, nz, material);
            vertex(vertices, x + 1, y, z, scale, nx, ny, nz, material);
            vertex(vertices, x, y, z, scale, nx, ny, nz, material);
        } else if (nz == 1) {
            vertex(vertices, x + 1, y, z + 1, scale, nx, ny, nz, material);
            vertex(vertices, x, y, z + 1, scale, nx, ny, nz, material);
            vertex(vertices, x, y + 1, z + 1, scale, nx, ny, nz, material);
            vertex(vertices, x + 1, y + 1, z + 1, scale, nx, ny, nz, material);
        } else {
            vertex(vertices, x, y, z, scale, nx, ny, nz, material);
            vertex(vertices, x + 1, y, z, scale, nx, ny, nz, material);
            vertex(vertices, x + 1, y + 1, z, scale, nx, ny, nz, material);
            vertex(vertices, x, y + 1, z, scale, nx, ny, nz, material);
        }
        indices.add(base);
        indices.add(base + 1);
        indices.add(base + 2);
        indices.add(base);
        indices.add(base + 2);
        indices.add(base + 3);
    }

    private static void vertex(FloatList vertices, int x, int y, int z, float scale,
                               int nx, int ny, int nz, int material) {
        vertices.add(x * scale);
        vertices.add(y * scale);
        vertices.add(z * scale);
        vertices.add(nx);
        vertices.add(ny);
        vertices.add(nz);
        vertices.add(material);
        vertices.add(1f);
    }

    private static MeshData build(FloatList vertices, IntList indices) {
        if (indices.size == 0) {
            return MeshData.empty();
        }
        ByteBuffer vb = ByteBuffer.allocateDirect(vertices.size * Float.BYTES)
                .order(ByteOrder.nativeOrder());
        ByteBuffer ib = ByteBuffer.allocateDirect(indices.size * Integer.BYTES)
                .order(ByteOrder.nativeOrder());
        for (int i = 0; i < vertices.size; i++) {
            vb.putFloat(vertices.data[i]);
        }
        for (int i = 0; i < indices.size; i++) {
            ib.putInt(indices.data[i]);
        }
        vb.flip();
        ib.flip();
        return new MeshData(vb.asFloatBuffer(), ib.asIntBuffer(),
                vertices.size / MeshData.FLOATS_PER_VERTEX, indices.size);
    }

    private static final class FloatList {
        float[] data;
        int size;

        FloatList(int capacity) {
            data = new float[capacity];
        }

        void add(float value) {
            if (size == data.length) {
                float[] grown = new float[size * 2];
                System.arraycopy(data, 0, grown, 0, size);
                data = grown;
            }
            data[size++] = value;
        }
    }

    private static final class IntList {
        int[] data;
        int size;

        IntList(int capacity) {
            data = new int[capacity];
        }

        void add(int value) {
            if (size == data.length) {
                int[] grown = new int[size * 2];
                System.arraycopy(data, 0, grown, 0, size);
                data = grown;
            }
            data[size++] = value;
        }
    }
}
