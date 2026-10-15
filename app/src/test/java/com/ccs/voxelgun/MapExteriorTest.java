package com.ccs.voxelgun;

import static org.junit.Assert.assertTrue;

import com.ccs.voxelgun.map.GameMap;
import com.ccs.voxelgun.map.Maps;
import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.voxel.VoxelWorld;

import org.junit.Test;

/**
 * Guards the thing that separates a level from a pile of blocks: surface detail. A voxel world has
 * no textures, so brick courses, painted trim, framed openings and grout lines are the only way a
 * wall reads as a wall — and they are easy to lose in a refactor without any test noticing.
 */
public class MapExteriorTest {
    /** Maps that are meant to look like a built-up exterior. */
    private static final String[] TOWNS = {"dust_yard", "warehouse", "village"};

    @Test
    public void theBuiltUpMapsUseTheExteriorPalette() {
        for (int index = 0; index < Maps.count(); index++) {
            GameMap map = Maps.create(index);
            if (!isTown(map.id())) {
                continue;
            }
            VoxelWorld world = map.build(99L + index);
            int[] counts = histogram(world);

            int brick = counts[Materials.BRICK_WHITE] + counts[Materials.BRICK_WHITE_ALT]
                    + counts[Materials.BRICK_ORANGE] + counts[Materials.BRICK_ORANGE_ALT]
                    + counts[Materials.BRICK_TAN] + counts[Materials.BRICK_TAN_ALT];
            int trim = counts[Materials.TRIM_RED] + counts[Materials.TRIM_BLUE]
                    + counts[Materials.TRIM_GREY];
            int paving = counts[Materials.TILE_FLOOR] + counts[Materials.TILE_GROUT];

            System.out.println(map.id() + " brick=" + brick + " trim=" + trim
                    + " paving=" + paving + " frames=" + counts[Materials.FRAME_LIGHT]
                    + " drums=" + counts[Materials.BARREL_TEAL]
                    + " panels=" + counts[Materials.PANEL_GREY]);

            assertTrue(map.id() + " has no brickwork", brick > 2000);
            assertTrue(map.id() + " has no painted trim", trim > 200);
            assertTrue(map.id() + " has no paved ground", paving > 500);
            assertTrue(map.id() + " has no two tone oil drums",
                    counts[Materials.BARREL_TEAL] > 0);
            assertTrue(map.id() + " has no panelled crates", counts[Materials.PANEL_GREY] > 0);

            // Both brick tones must actually appear, or the "courses" are one flat colour again.
            assertTrue(map.id() + " lays only one brick tone", twoTones(counts));
            world.release();
        }
    }

    @Test
    public void villageHasItsPoolAndDecking() {
        GameMap village = null;
        for (int index = 0; index < Maps.count(); index++) {
            GameMap candidate = Maps.create(index);
            if ("village".equals(candidate.id())) {
                village = candidate;
                break;
            }
        }
        assertTrue("village is missing from the roster", village != null);
        VoxelWorld world = village.build(7L);
        int[] counts = histogram(world);
        assertTrue("the pool has no water", counts[Materials.slot(Materials.WATER)] > 100);
        assertTrue("the pool has no rim", counts[Materials.POOL_EDGE] > 50);
        assertTrue("there is no decking", counts[Materials.DECK_WOOD] > 20);
        world.release();
    }

    private static boolean isTown(String id) {
        for (String town : TOWNS) {
            if (town.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static boolean twoTones(int[] counts) {
        return (counts[Materials.BRICK_WHITE] > 0 && counts[Materials.BRICK_WHITE_ALT] > 0)
                || (counts[Materials.BRICK_ORANGE] > 0 && counts[Materials.BRICK_ORANGE_ALT] > 0)
                || (counts[Materials.BRICK_TAN] > 0 && counts[Materials.BRICK_TAN_ALT] > 0);
    }

    private static int[] histogram(VoxelWorld world) {
        int[] counts = new int[256];
        for (int z = 0; z < world.sizeZ(); z++) {
            for (int y = 0; y < world.sizeY(); y++) {
                for (int x = 0; x < world.sizeX(); x++) {
                    counts[Materials.slot(world.get(x, y, z))]++;
                }
            }
        }
        return counts;
    }
}
