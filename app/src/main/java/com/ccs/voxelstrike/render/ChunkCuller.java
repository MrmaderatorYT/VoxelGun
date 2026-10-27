package com.ccs.voxelstrike.render;

import com.ccs.voxelstrike.math.Frustum;
import com.ccs.voxelstrike.voxel.VoxelWorld;

/** Allocation-free grid broad phase followed by the original distance/frustum tests. No GL. */
public final class ChunkCuller {
    private final int chunksX, chunksY, chunksZ;
    private final byte[] passes;
    private final int[] opaque, translucent;
    private int opaqueCount, translucentCount, candidates;

    public ChunkCuller(int chunksX, int chunksY, int chunksZ) {
        this.chunksX = chunksX; this.chunksY = chunksY; this.chunksZ = chunksZ;
        int total = chunksX * chunksY * chunksZ;
        passes = new byte[total];
        opaque = new int[total]; translucent = new int[total];
    }

    public void setMeshes(int index, boolean solid, boolean glass) {
        passes[index] = (byte) ((solid ? 1 : 0) | (glass ? 2 : 0));
    }

    public void cull(Frustum frustum, float cameraX, float cameraY, float cameraZ, float distance) {
        opaqueCount = translucentCount = candidates = 0;
        int size = VoxelWorld.CHUNK;
        float radius = Math.abs(distance);
        float limit = distance * distance;
        // Conservative cell bounds: keep the exact old centre-distance test below, including
        // edge chunks and camera positions outside the map. z/y/x preserves old draw order.
        int minX = lower(cameraX, radius), maxX = upper(cameraX, radius, chunksX);
        int minY = lower(cameraY, radius), maxY = upper(cameraY, radius, chunksY);
        int minZ = lower(cameraZ, radius), maxZ = upper(cameraZ, radius, chunksZ);
        for (int z = minZ; z <= maxZ; z++) {
            float mz = z * size, dz = mz + size * .5f - cameraZ;
            for (int y = minY; y <= maxY; y++) {
                float my = y * size, dy = my + size * .5f - cameraY;
                int row = chunksX * (y + chunksY * z);
                for (int x = minX; x <= maxX; x++) {
                    candidates++;
                    int index = row + x;
                    int flags = passes[index];
                    if (flags == 0) continue;
                    float mx = x * size, dx = mx + size * .5f - cameraX;
                    if (dx * dx + dy * dy + dz * dz > limit) continue;
                    if (!frustum.containsBox(mx, my, mz, mx + size, my + size, mz + size)) continue;
                    if ((flags & 1) != 0) opaque[opaqueCount++] = index;
                    if ((flags & 2) != 0) translucent[translucentCount++] = index;
                }
            }
        }
    }

    private static int lower(float camera, float radius) {
        return Math.max(0, VoxelWorld.floor((camera - radius) / VoxelWorld.CHUNK));
    }
    private static int upper(float camera, float radius, int chunks) {
        return Math.min(chunks - 1, VoxelWorld.floor((camera + radius) / VoxelWorld.CHUNK));
    }
    public int[] opaqueIndices() { return opaque; }
    public int[] translucentIndices() { return translucent; }
    public int opaqueCount() { return opaqueCount; }
    public int translucentCount() { return translucentCount; }
    public int candidateCount() { return candidates; }
}
