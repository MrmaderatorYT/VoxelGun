package com.ccs.voxelstrike.map;

import com.ccs.voxelstrike.voxel.Materials;

/**
 * Build-time combat primitives, not a universal arena stencil. Individual maps choose passages
 * and palettes; these helpers preserve ground and never punch trenches into completed terrain.
 */
public final class CombatLayout {
    private CombatLayout() {}

    /** Connects existing streets while leaving roofs/lintels above head clearance intact. */
    public static void walkway(MapBuilder b, int x0, int z0, int x1, int z1, int floor) {
        b.fill(x0, MapBuilder.GROUND - 1, z0, x1, MapBuilder.GROUND - 1, z1, floor);
        b.clear(x0, MapBuilder.GROUND, z0, x1, MapBuilder.GROUND + 3, z1);
    }

    /**
     * Three existing avenues remain clear down their centres. Cover sits on alternating shoulders
     * so an exposed short crossing and a longer cover-to-cover route are both available.
     */
    public static void battlefield(MapBuilder b, int theme) {
        int body = theme == 0 ? Materials.CONCRETE_DARK
                : theme == 1 ? Materials.HAY : Materials.SANDBAG;
        int cap = theme == 0 ? Materials.STEEL
                : theme == 1 ? Materials.WOOD_DARK : Materials.CANVAS;
        for (int lane = 0; lane < 3; lane++) {
            int z = 96 + lane * 192;
            for (int x = 54; x < b.world().sizeX() - 48; x += 42) {
                int side = ((x / 42 + lane) & 1) == 0 ? -1 : 1;
                // Stay on the level road shoulder: alpine height grading is untouched.
                shoulderCover(b, x, z + side * 6, 5, body, cap);
                if (theme == 0 && x % 84 < 42) {
                    // Ruined roadside kiosks instead of visually identical crate rows.
                    int feet = b.world().standableLevel(x + 2, z + side * 9, 3, 2);
                    if (feet >= 0) b.fill(x, feet + 4, z + side * 9,
                            x + 4, feet + 4, z + side * 9, Materials.PAINT_BLUE);
                }
            }
        }
        deploymentShield(b, 32, false, body, cap);
        deploymentShield(b, b.world().sizeX() - 33, true, body, cap);
    }

    private static void shoulderCover(MapBuilder b, int x, int z, int length, int body, int cap) {
        for (int dx = 0; dx < length; dx++) {
            int feet = b.world().standableLevel(x + dx, z, MapBuilder.GROUND, 2);
            if (feet < 0) continue;
            // Do not place a high box on a roof or buried terrain column.
            if (feet > 7 || b.world().solid(x + dx, feet + 2, z)) continue;
            b.fill(x + dx, feet, z, x + dx, feet + 1, z, body);
            b.set(x + dx, feet + 2, z, cap);
        }
    }

    /** Two offset screens block an across-map spawn shot, without closing either team exit. */
    private static void deploymentShield(MapBuilder b, int x, boolean mirrored, int body, int cap) {
        int middle = b.world().sizeZ() / 2;
        for (int z = middle - 8; z <= middle + 8; z++) {
            if (z >= middle - 2 && z <= middle + 2) continue;
            shoulderCover(b, x, z, 1, body, cap);
        }
        int front = x + (mirrored ? -10 : 10);
        for (int z = middle - 3; z <= middle + 3; z++)
            shoulderCover(b, front, z, 1, body, cap);
    }
}
