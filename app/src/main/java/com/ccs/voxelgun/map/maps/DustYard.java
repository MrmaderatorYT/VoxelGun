package com.ccs.voxelgun.map.maps;

import com.ccs.voxelgun.map.GameMap;
import com.ccs.voxelgun.map.CombatLayout;
import com.ccs.voxelgun.map.MapBuilder;
import com.ccs.voxelgun.map.MapEnvironment;
import com.ccs.voxelgun.map.scenery.ClassicArenaDressing;
import com.ccs.voxelgun.voxel.Materials;

/**
 * Sun-baked desert compound. Mirrored bases, a raised mid plaza and two flank routes, so the
 * three classic engagement ranges (long mid, mid-range flank, close tunnel) are all present.
 */
public final class DustYard extends GameMap {
    private static final int G = MapBuilder.GROUND;

    public DustYard() {
        super("dust_yard", "Dust Yard", "Desert compound · long mid");
    }

    @Override
    public MapEnvironment environment() {
        return MapEnvironment.builder()
                .sun(-0.46f, 0.74f, 0.49f)
                .sunColor(1.00f, 0.90f, 0.72f, 5.0f)
                .sunAngle(0.028f)
                .sky(0.26f, 0.42f, 0.72f, 0.86f, 0.78f, 0.62f, 1.15f)
                .fog(0.0085f)
                .exposure(1.0f)
                .build();
    }

    /** Mirrors an x coordinate for the second half of the map. */
    private static int mx(int x, int side) {
        return side == 0 ? x : SIZE_X - 1 - x;
    }

    @Override
    protected void generate(MapBuilder b) {
        b.ground(Materials.SAND, Materials.SANDSTONE, 0.14f);
        b.speckle(0, G - 1, 0, SIZE_X - 1, G - 1, SIZE_Z - 1, Materials.SAND, Materials.DIRT, 0.08f);
        // Lower enclosing walls let the new bazaar rooflines read against the sky.
        b.boundary(4, 11, Materials.BRICK_TAN, Materials.BRICK_TAN_ALT, Materials.TRIM_RED);

        for (int side = 0; side < 2; side++) {
            base(b, side);
            northTunnel(b, side);
            southYard(b, side);
        }
        midPlaza(b);
        rooftops(b);
        combatRoutes(b);

        // Kept clear of the interior staircase, which climbs x 20..24 / z 58..61.
        ClassicArenaDressing.geometry(b, id());

        blueSpawn(16, 58, 90);
        blueSpawn(16, 64, 90);
        blueSpawn(16, 70, 90);
        blueSpawn(20, 52, 90);
        blueSpawn(20, 76, 90);
        blueSpawn(26, 52, 90);
        blueSpawn(26, 76, 90);

        redSpawn(111, 58, 270);
        redSpawn(111, 64, 270);
        redSpawn(111, 70, 270);
        redSpawn(107, 52, 270);
        redSpawn(107, 76, 270);
        redSpawn(101, 52, 270);
        redSpawn(101, 76, 270);

        freeSpawn(64, 24, 180);
        freeSpawn(64, 104, 0);
        freeSpawn(38, 40, 135);
        freeSpawn(90, 40, 225);
        freeSpawn(38, 88, 45);
        freeSpawn(90, 88, 315);
        freeSpawn(54, 64, 90);
        freeSpawn(48, 64, 90);
        freeSpawn(80, 64, 270);
    }

    /** Walled spawn compound with a covered exit and a sniper balcony. */
    private void base(MapBuilder b, int side) {
        int wall = Materials.SANDSTONE;
        int x0 = mx(10, side);
        int x1 = mx(30, side);
        int lo = Math.min(x0, x1);
        int hi = Math.max(x0, x1);

        b.walls(lo, G, 48, hi, G + 8, 80, wall);
        b.fill(lo, G - 1, 48, hi, G - 1, 80, Materials.CONCRETE);
        b.speckle(lo, G - 1, 48, hi, G - 1, 80, Materials.CONCRETE, Materials.SAND, 0.2f);

        // Exit toward mid plus two side doors.
        int exit = mx(30, side);
        b.doorway(exit, G, 64, 0, 7, 5);
        b.doorway(exit, G, 54, 0, 3, 4);
        b.doorway(exit, G, 74, 0, 3, 4);

        // Balcony overlooking the exit.
        int balcony = mx(26, side);
        b.fill(Math.min(balcony, mx(30, side)), G + 4, 56, Math.max(balcony, mx(30, side)), G + 4, 72,
                Materials.WOOD);
        b.stairs(mx(20, side), G, 58, side == 0 ? 0 : 1, 5, 4, Materials.SANDSTONE);
        b.landing(mx(25, side), G + 4, 58, mx(26, side), 61, Materials.WOOD);
        b.fill(Math.min(balcony, mx(30, side)), G + 5, 56, Math.max(balcony, mx(30, side)), G + 5, 56,
                Materials.SANDSTONE);
        b.fill(Math.min(balcony, mx(30, side)), G + 5, 72, Math.max(balcony, mx(30, side)), G + 5, 72,
                Materials.SANDSTONE);

        // Team colour banner so players can read which side they are on at a glance.
        int banner = side == 0 ? Materials.TEAM_BLUE : Materials.TEAM_RED;
        b.fill(mx(11, side), G + 3, 60, mx(11, side), G + 6, 68, banner);

        b.crateStack(mx(34, side), G, 52, 2, Materials.CRATE, Materials.WOOD_DARK);
        b.crateStack(mx(34, side), G, 76, 2, Materials.CRATE, Materials.WOOD_DARK);
        b.oilDrum(mx(36, side), G, 64);
        b.panelCrate(mx(30, side), G, 62, 5);
    }

    /** Covered flank: a narrow tunnel with light shafts punched through the roof. */
    private void northTunnel(MapBuilder b, int side) {
        int x0 = mx(28, side);
        int x1 = mx(58, side);
        int lo = Math.min(x0, x1);
        int hi = Math.max(x0, x1);

        b.fill(lo, G, 22, hi, G + 6, 34, Materials.SANDSTONE);
        b.clear(lo, G, 24, hi, G + 4, 32);
        b.fill(lo, G - 1, 24, hi, G - 1, 32, Materials.CONCRETE);

        for (int x = lo + 4; x < hi; x += 7) {
            b.clear(x, G + 5, 27, x + 1, G + 6, 29);
        }
        for (int x = lo + 3; x < hi; x += 9) {
            b.crate(x, G, 25, 3, Materials.CRATE, Materials.WOOD_DARK);
        }

        // Tunnel mouths.
        b.doorway(mx(28, side), G, 28, 0, 5, 4);
        b.fill(mx(58, side), G, 24, mx(58, side), G + 4, 32, Materials.AIR);

        // Connector down to the base.
        b.clear(mx(30, side), G, 40, mx(34, side), G + 4, 48);
        b.fill(mx(29, side), G, 38, mx(35, side), G + 6, 38, Materials.SANDSTONE);
        b.doorway(mx(32, side), G, 38, 1, 5, 4);
    }

    /** Open southern yard with arches, sandbags and a shipping container. */
    private void southYard(MapBuilder b, int side) {
        for (int i = 0; i < 3; i++) {
            int x = mx(38 + i * 10, side);
            b.fill(x, G, 92, x, G + 6, 92, Materials.SANDSTONE);
            b.fill(x, G, 108, x, G + 6, 108, Materials.SANDSTONE);
            b.fill(x, G + 7, 92, x, G + 7, 108, Materials.SANDSTONE);
        }
        b.sandbags(mx(42, side), G, 98, true, 10, 2);
        b.sandbags(mx(52, side), G, 104, false, 8, 2);
        b.container(mx(34, side), G, 96, true, 12, Materials.PAINT_BLUE);
        b.crateStack(mx(48, side), G, 88, 3, Materials.CRATE, Materials.WOOD_DARK);
        b.tree(mx(30, side), G, 104, 5);

        // Rear balcony route: a real deck joins the rear stairs to the base, not midair.
        b.landing(mx(26, side), G + 4, 70, mx(33, side), 91, Materials.WOOD);
        b.stairs(Math.min(mx(26, side), mx(29, side)), G, 82, 2, 5, 4,
                Materials.SANDSTONE);
        b.clear(mx(26, side), G + 5, 72, mx(33, side), G + 8, 80);
    }

    /** Raised centre platform: the long-range duel spot, reachable from four ramps. */
    private void midPlaza(MapBuilder b) {
        b.fill(54, G - 1, 44, 74, G - 1, 84, Materials.CONCRETE);
        b.tiledFloor(54, 44, 74, 84, G - 1, Materials.TILE_FLOOR, Materials.TILE_GROUT, 4);

        b.brickFill(58, G, 56, 70, G + 2, 72, Materials.BRICK_TAN, Materials.BRICK_TAN_ALT);
        b.clear(60, G, 58, 68, G + 1, 70);
        b.fill(58, G + 3, 56, 70, G + 3, 72, Materials.WOOD);
        b.doorway(58, G, 64, 0, 5, 3);
        b.doorway(70, G, 64, 0, 5, 3);

        b.stairs(56, G, 58, 0, 3, 6, Materials.SANDSTONE);
        b.stairs(72, G, 58, 1, 3, 6, Materials.SANDSTONE);
        b.fill(58, G + 4, 56, 58, G + 4, 72, Materials.SANDSTONE);
        b.fill(70, G + 4, 56, 70, G + 4, 72, Materials.SANDSTONE);

        b.crate(52, G, 48, 4, Materials.CRATE, Materials.WOOD_DARK);
        b.crate(72, G, 80, 4, Materials.CRATE, Materials.WOOD_DARK);
        b.oilDrum(76, G, 50);
        b.oilDrum(50, G, 80);

        // Well in the middle of the plaza; the lamp gives the ray tracer a local light source.
        b.pillar(64, G, 64, 2, 3, Materials.STONE);
        b.clear(64, G, 64, 64, G + 1, 64);
        b.lamp(64, G + 5, 64, Materials.STEEL);
        b.fill(64, G + 6, 64, 64, G + 8, 64, Materials.STEEL);
    }

    /** Roof access on the mid buildings so vertical play is possible. */
    private void rooftops(MapBuilder b) {
        for (int side = 0; side < 2; side++) {
            int x = mx(44, side);
            b.fill(Math.min(x, mx(50, side)), G, 40, Math.max(x, mx(50, side)), G + 5, 44,
                    Materials.SANDSTONE);
            b.clear(Math.min(x, mx(50, side)) + 1, G, 41, Math.max(x, mx(50, side)) - 1, G + 3, 43);
            b.stairs(mx(52, side), G, 41, side == 0 ? 1 : 0, 6, 3, Materials.WOOD);
            b.lamp(mx(47, side), G + 6, 42, Materials.AIR);
        }
    }

    private void combatRoutes(MapBuilder b) {
        for (int side = 0; side < 2; side++) {
            // Separate north/south spawn exits: flanking no longer depends on jumping a wall.
            b.doorway(mx(22, side), G, 48, 1, 5, 4);
            b.doorway(mx(22, side), G, 80, 1, 5, 4);
            CombatLayout.walkway(b, mx(20, side), 34, mx(24, side), 48, Materials.CONCRETE);
            CombatLayout.walkway(b, mx(20, side), 80, mx(24, side), 112, Materials.CONCRETE);
            CombatLayout.walkway(b, mx(20, side), 108, mx(56, side), 112, Materials.SAND);
            // Sideways connector lets a tunnel player flank the raised mid position.
            CombatLayout.walkway(b, mx(52, side), 32, mx(56, side), 52, Materials.CONCRETE);
            // Clear a mistakenly crate-blocked main doorway, then screen it farther forward.
            CombatLayout.walkway(b, mx(29, side), 61, mx(36, side), 67, Materials.CONCRETE);
            b.fill(mx(40, side), G, 60, mx(40, side), G + 3, 68, Materials.SANDSTONE);
            b.sandbags(mx(48, side), G, 110, true, 6, 3);
            b.sandbags(mx(70, side), G, 106, true, 6, 3);
            // Back door below the rooftop makes camping its sole approach harder.
            b.doorway(mx(47, side), G, 44, 1, 3, 4);
        }
    }
}
