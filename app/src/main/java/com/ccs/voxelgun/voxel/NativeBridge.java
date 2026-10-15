package com.ccs.voxelgun.voxel;

/**
 * JNI facade for the native voxel core.
 *
 * <p>Everything that touches many voxels per call lives in C++: greedy meshing, ray casting for
 * bullets and AI sight, the brick acceleration field consumed by the ray tracer, and the height
 * map used by navigation and the minimap.
 */
public final class NativeBridge {
    private static final boolean AVAILABLE = load();

    private NativeBridge() {}

    private static boolean load() {
        try {
            System.loadLibrary("voxel_native");
            return true;
        } catch (UnsatisfiedLinkError error) {
            return false;
        }
    }

    public static boolean available() {
        return AVAILABLE;
    }

    /**
     * Greedy-meshes a volume padded by one voxel on each side.
     *
     * @param padded  ({@code sx}+2)·({@code sy}+2)·({@code sz}+2) materials, x-major
     * @param pass    0 for opaque materials, 1 for translucent ones
     * @return packed buffer: int vertexCount, int indexCount, vertices, indices
     */
    public static native byte[] greedyMesh(byte[] padded, int sx, int sy, int sz, float scale, int pass);

    public static native long createVolume(int sx, int sy, int sz);

    public static native void destroyVolume(long handle);

    public static native void uploadVolume(long handle, byte[] data);

    public static native void setVoxel(long handle, int x, int y, int z, int material);

    /** {@code out} receives t, hit xyz, normal xyz, material, block xyz. */
    public static native boolean raycast(long handle, float ox, float oy, float oz, float dx,
                                         float dy, float dz, float maxDistance,
                                         boolean hitTransparent, float[] out);

    public static native boolean lineOfSight(long handle, float ax, float ay, float az, float bx,
                                             float by, float bz);

    public static native boolean boxCollides(long handle, float x, float y, float z,
                                             float halfWidth, float height);

    /** Chebyshev empty-space distance per brick, used for empty space skipping while tracing. */
    public static native byte[] buildBrickField(long handle, int brick);

    public static native byte[] buildHeightMap(long handle);
}
