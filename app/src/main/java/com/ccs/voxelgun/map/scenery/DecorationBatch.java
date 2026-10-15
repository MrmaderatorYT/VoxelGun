package com.ccs.voxelgun.map.scenery;

import com.ccs.voxelgun.voxel.MeshData;

/** One palette-batched spatial cell; bounds include boxes spanning neighbouring cells. */
public final class DecorationBatch {
    public final MeshData mesh;
    public final float minX, minY, minZ, maxX, maxY, maxZ;
    public DecorationBatch(MeshData mesh, float[] bounds) {
        this.mesh = mesh;
        minX = bounds[0]; minY = bounds[1]; minZ = bounds[2];
        maxX = bounds[3]; maxY = bounds[4]; maxZ = bounds[5];
    }
}
