package com.ccs.voxelstrike.map.maps;

import com.ccs.voxelstrike.map.GameMap;
import com.ccs.voxelstrike.map.MapBuilder;
import com.ccs.voxelstrike.map.MapEnvironment;
import com.ccs.voxelstrike.map.scenery.ClassicArenaDressing;
import com.ccs.voxelstrike.voxel.Materials;

/**
 * Grass-capped islands hanging in an empty sky, tied together by rope bridges and jump pads.
 * There is no ground: step off an edge and you fall out of the world, which the match loop
 * scores as a death. Everything is stacked vertically, so fights happen across three heights.
 */
public final class SkyIslands extends GameMap {
    private static final int BASE = 18;

    /**
     * cx, cz, top height, radius. Eight islands sit in a ring at one shared height so the rope
     * bridges between them are level; only the centre is raised, reached by four staircases.
     */
    private static final int RING = BASE;
    private static final int PEAK = BASE + 8;
    private static final int[][] ISLANDS = {
            {30, 30, RING, 15},   // 0 blue home
            {64, 24, RING, 9},    // 1
            {98, 30, RING, 12},   // 2
            {104, 64, RING, 9},   // 3
            {98, 98, RING, 15},   // 4 red home
            {64, 104, RING, 9},   // 5
            {30, 98, RING, 12},   // 6
            {24, 64, RING, 9},    // 7
            {64, 64, PEAK, 18},   // 8 central plateau
    };

    public SkyIslands() {
        super("sky_islands", "Sky Islands", "Floating · bridges and jump pads");
    }

    @Override
    public MapEnvironment environment() {
        return MapEnvironment.builder()
                .sun(-0.42f, 0.78f, 0.46f)
                .sunColor(1.00f, 0.97f, 0.90f, 5.6f)
                .sunAngle(0.024f)
                .sky(0.28f, 0.60f, 0.98f, 0.80f, 0.93f, 1.00f, 1.55f)
                .fog(0.0035f)
                .exposure(1.0f)
                .build();
    }

    @Override
    protected void generate(MapBuilder b) {
        for (int[] island : ISLANDS) {
            b.island(island[0], island[2], island[1], island[3], Materials.GRASS_BRIGHT,
                    Materials.DIRT);
        }

        homeIsland(b, 30, 30, RING, Materials.SKY_BLUE, Materials.ROOF_BLUE);
        homeIsland(b, 98, 98, RING, Materials.CORAL, Materials.CANDY_PINK);
        plateau(b);
        outposts(b);
        bridges(b);
        decoration(b);
        // Vegetation must not close the small tents' only entrance after authoring.
        for (int[] tent : new int[][]{{26, 94}, {94, 26}, {60, 20}, {60, 100}}) {
            b.clear(tent[0] + 3, RING + 2, tent[1] - 2,
                    tent[0] + 5, RING + 5, tent[1] + 2);
        }

        ClassicArenaDressing.geometry(b, id());

        blueSpawn(24, 26, 45);
        blueSpawn(30, 22, 45);
        blueSpawn(40, 28, 45);
        blueSpawn(26, 34, 45);
        blueSpawn(34, 36, 45);
        blueSpawn(22, 32, 45);
        blueSpawn(38, 32, 45);

        redSpawn(104, 102, 225);
        redSpawn(98, 106, 225);
        redSpawn(92, 100, 225);
        redSpawn(102, 94, 225);
        redSpawn(94, 92, 225);
        redSpawn(106, 96, 225);
        redSpawn(90, 96, 225);

        freeSpawn(64, 58, 180);
        freeSpawn(64, 70, 0);
        freeSpawn(58, 64, 90);
        freeSpawn(70, 64, 270);
        freeSpawn(36, 98, 315);
        freeSpawn(98, 36, 135);
        freeSpawn(64, 24, 180);
        freeSpawn(64, 104, 0);
    }

    /** Team island: a cottage, a windmill and the launch pad toward the middle. */
    private void homeIsland(MapBuilder b, int cx, int cz, int top, int wall, int roof) {
        int y = top + 1;
        cottage(b, cx - 5, y, cz - 4, wall, roof);

        // Windmill, the landmark you navigate by.
        int wx = cx + 7;
        int wz = cz + 6;
        b.pillar(wx, y, wz, 12, 2, Materials.STONE_LIGHT);
        b.cone(wx, y + 12, wz, 5, 3, roof);
        for (int i = 0; i < 4; i++) {
            double angle = Math.PI / 2 * i + Math.PI / 8;
            for (int r = 2; r <= 7; r++) {
                b.set(wx + (int) Math.round(Math.cos(angle) * r), y + 10,
                        wz + (int) Math.round(Math.sin(angle) * r), Materials.WOOD_LIGHT);
            }
        }
        b.set(wx, y + 17, wz, Materials.LANTERN);

        b.jumpPad(cx, y, cz + 10);
        b.fill(cx - 3, y, cz + 8, cx + 3, y, cz + 8, Materials.STONE_LIGHT);
    }

    private void cottage(MapBuilder b, int x, int y, int z, int wall, int roof) {
        int width = 11;
        int depth = 9;
        int height = 5;
        b.fill(x, y - 1, z, x + width, y - 1, z + depth, Materials.WOOD_LIGHT);
        b.walls(x, y, z, x + width, y + height, z + depth, wall);
        b.doorway(x + width / 2, y, z, 1, 3, 4);
        b.window(x, y + 2, z + 3, x, y + 3, z + 5, true);
        b.window(x + width, y + 2, z + 3, x + width, y + 3, z + 5, true);
        b.roof(x - 1, z - 1, x + width + 1, z + depth + 1, y + height + 1, roof);
        b.set(x + 2, y + 4, z - 1, Materials.LANTERN);
        b.landing(x + width - 1, y + height + 1, z + 2,
                x + width, z + 4, Materials.WOOD_LIGHT);
        b.stairs(x + width + 6, y, z + 2, 1, height + 2, 3, Materials.WOOD_LIGHT);
    }

    /** The high central island: a ruined arena with columns and a jump pad ring. */
    private void plateau(MapBuilder b) {
        int cx = 64;
        int cz = 64;
        int y = PEAK + 1;

        b.disc(cx, y, cz, 12, Materials.STONE_LIGHT);
        b.checker(cx - 8, cz - 8, cx + 8, cz + 8, y, Materials.STONE_LIGHT, Materials.MARBLE, 3);

        for (int i = 0; i < 8; i++) {
            double angle = Math.PI / 4 * i;
            int px = cx + (int) Math.round(Math.cos(angle) * 10);
            int pz = cz + (int) Math.round(Math.sin(angle) * 10);
            int height = 7 + (i % 3) * 2;
            b.pillar(px, y + 1, pz, height, 1, Materials.STONE_LIGHT);
            if (i % 2 == 0) {
                b.set(px, y + height + 1, pz, Materials.LANTERN);
            } else {
                b.banner(px, y + height, pz, 4, i % 4 == 1 ? Materials.GRAPE : Materials.SUNNY,
                        Materials.WOOD_DARK);
            }
        }

        // Central shrine with the gold prize block on top.
        b.fill(cx - 3, y + 1, cz - 3, cx + 3, y + 4, cz + 3, Materials.STONE_LIGHT);
        b.clear(cx - 2, y + 1, cz - 2, cx + 2, y + 3, cz + 2);
        b.doorway(cx - 3, y + 1, cz, 0, 3, 3);
        b.doorway(cx + 3, y + 1, cz, 0, 3, 3);
        b.fill(cx - 2, y + 5, cz - 2, cx + 2, y + 5, cz + 2, Materials.GOLD);
        b.cone(cx, y + 6, cz, 4, 3, Materials.ROOF_TEAL);

        // Pads that fling you back up if you land on the rim.
        b.jumpPad(cx - 11, y, cz);
        b.jumpPad(cx + 11, y, cz);
        b.jumpPad(cx, y, cz - 11);
        b.jumpPad(cx, y, cz + 11);
    }

    /** The four smaller islands: tents, hay and a lookout each. */
    private void outposts(MapBuilder b) {
        tent(b, 26, RING + 1, 94, Materials.CANDY_PINK);
        tent(b, 94, RING + 1, 26, Materials.MINT);
        tent(b, 60, RING + 1, 20, Materials.SUNNY);
        tent(b, 60, RING + 1, 100, Materials.GRAPE);

        for (int[] spot : new int[][]{{24, 64, RING + 1}, {104, 64, RING + 1}}) {
            int x = spot[0];
            int z = spot[1];
            int y = spot[2];
            b.walls(x - 2, y, z - 2, x + 2, y + 6, z + 2, Materials.WOOD_LIGHT);
            b.doorway(x - 2, y, z, 0, 3, 4);
            b.fill(x - 3, y + 7, z - 3, x + 3, y + 7, z + 3, Materials.WOOD);
            b.cone(x, y + 8, z, 4, 3, Materials.ROOF_BLUE);
            b.jumpPad(x, y, z + 4);
            b.set(x, y + 12, z, Materials.LANTERN);
        }
    }

    private void tent(MapBuilder b, int x, int y, int z, int cloth) {
        b.fill(x, y, z, x + 8, y, z + 8, Materials.WOOD_LIGHT);
        for (int level = 0; level < 5; level++) {
            b.fill(x + level, y + level + 1, z, x + 8 - level, y + level + 1, z + 8, cloth);
            b.clear(x + level + 1, y + level + 1, z + 1, x + 7 - level, y + level + 1, z + 7);
        }
        b.fill(x + 4, y + 1, z, x + 4, y + 4, z, Materials.AIR);
        b.crateStack(x + 2, y + 1, z + 2, 2, Materials.HAY, Materials.WOOD_DARK);
        b.set(x + 4, y + 6, z + 4, Materials.LANTERN);
    }

    /**
     * The ring of rope bridges plus the four staircases onto the plateau. Spans are measured
     * against the island radii so both ends land on solid ground.
     */
    private void bridges(MapBuilder b) {
        // Ring, clockwise from the blue island.
        b.bridge(43, RING, 25, true, 15, 4, Materials.WOOD_LIGHT, Materials.WOOD_DARK);
        b.bridge(71, RING, 25, true, 19, 4, Materials.WOOD_LIGHT, Materials.WOOD_DARK);
        b.bridge(99, RING, 41, false, 17, 4, Materials.WOOD_LIGHT, Materials.WOOD_DARK);
        b.bridge(99, RING, 71, false, 17, 4, Materials.WOOD_LIGHT, Materials.WOOD_DARK);
        b.bridge(71, RING, 99, true, 19, 4, Materials.WOOD_LIGHT, Materials.WOOD_DARK);
        b.bridge(41, RING, 99, true, 17, 4, Materials.WOOD_LIGHT, Materials.WOOD_DARK);
        b.bridge(25, RING, 71, false, 17, 4, Materials.WOOD_LIGHT, Materials.WOOD_DARK);
        b.bridge(25, RING, 43, false, 15, 4, Materials.WOOD_LIGHT, Materials.WOOD_DARK);

        // Staircases up to the plateau from the four cardinal islands.
        climbToPlateau(b, 62, 34, 2);
        climbToPlateau(b, 62, 94, 3);
        climbToPlateau(b, 33, 62, 0);
        climbToPlateau(b, 95, 62, 1);
    }

    /** One staircase from the ring height up to the plateau, with a landing at the top. */
    private void climbToPlateau(MapBuilder b, int x, int z, int direction) {
        int steps = PEAK - RING + 1;
        b.stairs(x, RING, z, direction, steps, 5, Materials.STONE_LIGHT);
        int endX = x + (direction == 0 ? steps - 1 : direction == 1 ? -(steps - 1) : 0);
        int endZ = z + (direction == 2 ? steps - 1 : direction == 3 ? -(steps - 1) : 0);
        // Short landing so the last step meets the plateau rim rather than stopping short.
        if (direction <= 1) {
            int toward = direction == 0 ? 4 : -4;
            b.fill(endX, PEAK, endZ, endX + toward, PEAK, endZ + 4, Materials.STONE_LIGHT);
        } else {
            int toward = direction == 2 ? 4 : -4;
            b.fill(endX, PEAK, endZ, endX + 4, PEAK, endZ + toward, Materials.STONE_LIGHT);
        }
    }

    /** Trees, flowers and hanging lanterns to keep the islands from reading as bare discs. */
    private void decoration(MapBuilder b) {
        for (int[] island : ISLANDS) {
            int cx = island[0];
            int cz = island[1];
            int y = island[2] + 1;
            int radius = island[3];
            for (int i = 0; i < 5; i++) {
                double angle = b.random().nextFloat() * Math.PI * 2;
                float distance = radius * (0.55f + b.random().nextFloat() * 0.35f);
                int x = cx + (int) (Math.cos(angle) * distance);
                int z = cz + (int) (Math.sin(angle) * distance);
                if (b.world().get(x, y, z) != 0) {
                    continue;
                }
                if (i % 3 == 0) {
                    b.tree(x, y, z, 5 + i % 3);
                } else {
                    b.set(x, y, z, i % 2 == 0 ? Materials.SUNNY : Materials.CANDY_PINK);
                }
            }
        }
    }
}
