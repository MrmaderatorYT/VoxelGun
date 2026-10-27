package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.voxel.VoxelWorld;

/** Bounded blast editing. Ground and structural concrete/steel cannot collapse. */
public final class Destruction {
    private final VoxelWorld world;
    private final NavGrid nav;
    public Destruction(VoxelWorld world, NavGrid nav) { this.world = world; this.nav = nav; }

    public int blast(float x, float y, float z, float radius) {
        float r = Math.min(4f, Math.max(0f, radius));
        int minX = Math.max(0, (int) Math.floor(x - r));
        int maxX = Math.min(world.sizeX() - 1, (int) Math.ceil(x + r));
        int minZ = Math.max(0, (int) Math.floor(z - r));
        int maxZ = Math.min(world.sizeZ() - 1, (int) Math.ceil(z + r));
        int minY = Math.max(3, (int) Math.floor(y - r));
        int maxY = Math.min(world.sizeY() - 1, (int) Math.ceil(y + r));
        int removed = 0;
        for (int bz = minZ; bz <= maxZ && removed < 128; bz++) {
            for (int by = minY; by <= maxY && removed < 128; by++) {
                for (int bx = minX; bx <= maxX && removed < 128; bx++) {
                    float dx = bx + .5f - x, dy = by + .5f - y, dz = bz + .5f - z;
                    if (dx * dx + dy * dy + dz * dz <= r * r
                            && world.removeDestructible(bx, by, bz)) removed++;
                }
            }
        }
        if (removed > 0) nav.refreshArea(world, minX, minZ, maxX, maxZ);
        return removed;
    }
}
