package com.ccs.voxelgun.model;

/**
 * A small voxel grid used for characters, weapons and props.
 *
 * <p>Models look down local -Z so that a yaw rotation lines them up with the world forward axis.
 * The pivot marks the point the model is attached at (a joint, or the grip of a gun).
 */
public final class VoxelModel {
    public final int sizeX;
    public final int sizeY;
    public final int sizeZ;
    public final byte[] voxels;
    public float pivotX;
    public float pivotY;
    public float pivotZ;

    public VoxelModel(int sizeX, int sizeY, int sizeZ) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.voxels = new byte[sizeX * sizeY * sizeZ];
    }

    public VoxelModel pivot(float x, float y, float z) {
        pivotX = x;
        pivotY = y;
        pivotZ = z;
        return this;
    }

    public void set(int x, int y, int z, int material) {
        if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
            return;
        }
        voxels[x + sizeX * (y + sizeY * z)] = (byte) material;
    }

    public byte get(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
            return 0;
        }
        return voxels[x + sizeX * (y + sizeY * z)];
    }

    public VoxelModel box(int x0, int y0, int z0, int x1, int y1, int z1, int material) {
        for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                    set(x, y, z, material);
                }
            }
        }
        return this;
    }

    /** Replaces only voxels that already hold {@code from}; used for team recolouring. */
    public VoxelModel recolor(int from, int to) {
        for (int i = 0; i < voxels.length; i++) {
            if ((voxels[i] & 0xFF) == (from & 0xFF)) {
                voxels[i] = (byte) to;
            }
        }
        return this;
    }

    public VoxelModel copy() {
        VoxelModel clone = new VoxelModel(sizeX, sizeY, sizeZ);
        System.arraycopy(voxels, 0, clone.voxels, 0, voxels.length);
        clone.pivotX = pivotX;
        clone.pivotY = pivotY;
        clone.pivotZ = pivotZ;
        return clone;
    }
}
