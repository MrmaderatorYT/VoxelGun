package com.ccs.voxelgun.game;

import com.ccs.voxelgun.voxel.VoxelWorld;
import java.util.Arrays;

/** Terrain-valid capture slots, cached until the assignment or terrain changes. */
public final class ObjectiveFormation {
    private final NavGrid nav;
    private final VoxelWorld world;
    private final int[] centers, slots;
    private int revision = -1;

    public ObjectiveFormation(NavGrid nav, VoxelWorld world, int actors) {
        this.nav = nav; this.world = world;
        centers = new int[actors]; slots = new int[actors];
        Arrays.fill(centers, -1);
    }

    public int forBot(int center, Combatant c, float radius) {
        if (!c.bot || !nav.valid(center)) return center;
        if (revision != world.revision()) {
            revision = world.revision(); Arrays.fill(centers, -1);
        }
        if (c.id < 0 || c.id >= centers.length) return slot(nav, world, center, c, radius);
        if (centers[c.id] != center) {
            centers[c.id] = center;
            slots[c.id] = slot(nav, world, center, c, radius);
        }
        return slots[c.id];
    }

    public static int slot(NavGrid nav, VoxelWorld world, int center, Combatant c, float radius) {
        if (!c.bot || !nav.valid(center)) return center;
        float x = nav.cellX(center) + .5f, z = nav.cellZ(center) + .5f;
        float y = nav.nodeY(center);
        int preferred = Math.floorMod(c.id / 2 + (c.team == Team.RED ? 6 : 0), 12);
        float ring = Math.min(radius - 1.1f, (radius > 5f ? 6f : 2.2f) + (c.id / 24 % 2) * .6f);
        for (int i = 0; i < 12; i++) {
            float angle = (preferred + i) * (float) Math.PI / 6f;
            int n = nav.nearestNode(x + (float) Math.cos(angle) * ring, y,
                    z + (float) Math.sin(angle) * ring);
            if (!nav.valid(n)) continue;
            float sx = nav.cellX(n) + .5f, sz = nav.cellZ(n) + .5f;
            float sy = nav.nodeY(n), dx = sx - x, dz = sz - z;
            if (Math.abs(sy - y) >= 2f || dx * dx + dz * dz >= radius * radius
                    || world.boxCollides(sx, sy, sz, Combatant.RADIUS, Combatant.HEIGHT)) continue;
            if (world.lineOfSight(x, y + 1f, z, sx, sy + Combatant.EYE_HEIGHT, sz)) return n;
        }
        return center;
    }
}
