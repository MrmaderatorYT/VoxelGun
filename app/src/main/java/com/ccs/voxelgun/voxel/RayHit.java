package com.ccs.voxelgun.voxel;

/** Reusable ray cast result; hot paths keep one instance per system instead of allocating. */
public final class RayHit {
    public float distance;
    public float x;
    public float y;
    public float z;
    public float normalX;
    public float normalY;
    public float normalZ;
    public int material;
    public int blockX;
    public int blockY;
    public int blockZ;

    public void set(float[] packed) {
        set(packed[0], packed[1], packed[2], packed[3], packed[4], packed[5], packed[6],
                (int) packed[7], (int) packed[8], (int) packed[9], (int) packed[10]);
    }

    public void set(float distance, float x, float y, float z, float nx, float ny, float nz,
                    int material, int blockX, int blockY, int blockZ) {
        this.distance = distance;
        this.x = x;
        this.y = y;
        this.z = z;
        this.normalX = nx;
        this.normalY = ny;
        this.normalZ = nz;
        this.material = material;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
    }
}
