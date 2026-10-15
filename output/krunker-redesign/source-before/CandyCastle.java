package com.ccs.voxelgun.map.maps;

import com.ccs.voxelgun.map.GameMap;
import com.ccs.voxelgun.map.MapBuilder;
import com.ccs.voxelgun.map.MapEnvironment;
import com.ccs.voxelgun.voxel.Materials;

/**
 * A storybook castle in sweet-shop colours: a moat, two drawbridges, painted turrets with
 * conical roofs, a banner-lined great hall and battlements you can run the whole way around.
 * Bright, tall and deliberately unserious.
 */
public final class CandyCastle extends GameMap {
    private static final int G = MapBuilder.GROUND;
    private static final int KEEP_X0 = 44;
    private static final int KEEP_Z0 = 44;
    private static final int KEEP_X1 = 84;
    private static final int KEEP_Z1 = 84;
    private static final int WALL_HEIGHT = 12;

    public CandyCastle() {
        super("candy_castle", "Candy Castle", "Storybook · towers and battlements");
    }

    @Override
    public MapEnvironment environment() {
        return MapEnvironment.builder()
                .sun(-0.52f, 0.72f, 0.46f)
                .sunColor(1.00f, 0.94f, 0.80f, 5.2f)
                .sunAngle(0.028f)
                .sky(0.36f, 0.62f, 0.96f, 0.96f, 0.80f, 0.88f, 1.40f)
                .fog(0.007f)
                .exposure(1.05f)
                .build();
    }

    @Override
    protected void generate(MapBuilder b) {
        meadow(b);
        moat(b);
        curtainWall(b);
        towers(b);
        keep(b);
        courtyard(b);
        approach(b);

        blueSpawn(16, 60, 90);
        blueSpawn(16, 68, 90);
        blueSpawn(22, 56, 90);
        blueSpawn(22, 72, 90);
        blueSpawn(12, 64, 90);
        blueSpawn(26, 62, 90);
        blueSpawn(26, 70, 90);

        redSpawn(111, 60, 270);
        redSpawn(111, 68, 270);
        redSpawn(105, 56, 270);
        redSpawn(105, 72, 270);
        redSpawn(115, 64, 270);
        redSpawn(101, 62, 270);
        redSpawn(101, 70, 270);

        freeSpawn(64, 20, 180);
        freeSpawn(64, 108, 0);
        freeSpawn(64, 64, 0);
        freeSpawn(50, 50, 135);
        freeSpawn(78, 78, 315);
        freeSpawn(50, 78, 45);
        freeSpawn(78, 50, 225);
        freeSpawn(24, 30, 135);
    }

    private void meadow(MapBuilder b) {
        b.speckle(0, 0, 0, SIZE_X - 1, G - 1, SIZE_Z - 1, Materials.GRASS_BRIGHT, Materials.GRASS,
                0.2f);
        // Flower patches.
        for (int i = 0; i < 90; i++) {
            int x = b.random().nextInt(SIZE_X);
            int z = b.random().nextInt(SIZE_Z);
            if (b.world().get(x, G, z) != 0) {
                continue;
            }
            int flower = switch (i % 4) {
                case 0 -> Materials.CANDY_PINK;
                case 1 -> Materials.SUNNY;
                case 2 -> Materials.GRAPE;
                default -> Materials.CORAL;
            };
            b.set(x, G, z, flower);
        }
        for (int i = 0; i < 16; i++) {
            b.tree(8 + (i % 4) * 6, G, 8 + (i / 4) * 6, 5 + i % 3);
            b.tree(SIZE_X - 10 - (i % 4) * 6, G, SIZE_Z - 10 - (i / 4) * 6, 5 + i % 3);
        }
        b.boundary(3, 10, Materials.STONE_LIGHT);
    }

    /** Water ring around the castle; the drawbridges are the only dry way in. */
    private void moat(MapBuilder b) {
        int inner = 26;
        int outer = 33;
        for (int z = 0; z < SIZE_Z; z++) {
            for (int x = 0; x < SIZE_X; x++) {
                float distance = ring(x, z);
                if (distance < inner || distance > outer) {
                    continue;
                }
                b.fill(x, G - 2, z, x, G - 1, z, Materials.AIR);
                b.set(x, G - 2, z, Materials.STONE_LIGHT);
                b.set(x, G - 1, z, Materials.WATER);
                b.set(x, G, z, Materials.WATER);
            }
        }
    }

    /** Chebyshev-ish distance from the castle centre, so the moat is a rounded square. */
    private static float ring(int x, int z) {
        float dx = Math.abs(x - 64);
        float dz = Math.abs(z - 64);
        return (float) Math.sqrt(dx * dx + dz * dz) * 0.55f + Math.max(dx, dz) * 0.45f;
    }

    /** Battlemented curtain wall with a walkway all the way around. */
    private void curtainWall(MapBuilder b) {
        int x0 = KEEP_X0 - 14;
        int z0 = KEEP_Z0 - 14;
        int x1 = KEEP_X1 + 14;
        int z1 = KEEP_Z1 + 14;

        b.walls(x0, G, z0, x1, G + WALL_HEIGHT, z1, Materials.STONE_LIGHT);
        // Wall walk.
        b.fill(x0, G + WALL_HEIGHT + 1, z0, x1, G + WALL_HEIGHT + 1, z1, Materials.STONE_LIGHT);
        b.clear(x0 + 3, G + WALL_HEIGHT + 1, z0 + 3, x1 - 3, G + WALL_HEIGHT + 1, z1 - 3);

        // Crenellations.
        for (int i = 0; i <= x1 - x0; i += 3) {
            b.set(x0 + i, G + WALL_HEIGHT + 2, z0, Materials.STONE_LIGHT);
            b.set(x0 + i, G + WALL_HEIGHT + 2, z1, Materials.STONE_LIGHT);
        }
        for (int i = 0; i <= z1 - z0; i += 3) {
            b.set(x0, G + WALL_HEIGHT + 2, z0 + i, Materials.STONE_LIGHT);
            b.set(x1, G + WALL_HEIGHT + 2, z0 + i, Materials.STONE_LIGHT);
        }

        // Gatehouses west and east.
        gate(b, x0, 64, true);
        gate(b, x1, 64, false);

        // Stairs up to the wall walk from inside the courtyard.
        int deck = G + WALL_HEIGHT + 1;
        b.landing(38, deck, 30, 40, 32, Materials.STONE_LIGHT);
        b.landing(88, deck, 96, 90, 98, Materials.STONE_LIGHT);
        b.landing(30, deck, 86, 32, 88, Materials.STONE_LIGHT);
        b.landing(96, deck, 38, 98, 40, Materials.STONE_LIGHT);
        b.stairs(38, G, 46, 3, WALL_HEIGHT + 2, 3, Materials.STONE_LIGHT);
        b.stairs(88, G, 82, 2, WALL_HEIGHT + 2, 3, Materials.STONE_LIGHT);
        b.stairs(46, G, 86, 1, WALL_HEIGHT + 2, 3, Materials.STONE_LIGHT);
        b.stairs(82, G, 38, 0, WALL_HEIGHT + 2, 3, Materials.STONE_LIGHT);
    }

    /** Gate tunnel plus the plank drawbridge over the moat. */
    private void gate(MapBuilder b, int wallX, int z, boolean west) {
        b.doorway(wallX, G, z, 0, 7, 6);
        b.fill(wallX, G + 6, z - 3, wallX, G + 7, z + 3, Materials.WOOD_DARK);
        int direction = west ? -1 : 1;
        for (int i = 1; i <= 12; i++) {
            b.fill(wallX + direction * i, G, z - 3, wallX + direction * i, G,
                    z + 3, Materials.WOOD_LIGHT);
            if (i % 4 == 0) {
                b.fill(wallX + direction * i, G + 1, z - 4, wallX + direction * i, G + 3, z - 4,
                        Materials.WOOD_DARK);
                b.fill(wallX + direction * i, G + 1, z + 4, wallX + direction * i, G + 3, z + 4,
                        Materials.WOOD_DARK);
            }
        }
        int inner = west ? KEEP_X0 : KEEP_X1;
        b.landing(Math.min(wallX, inner) - 1, G, z - 3,
                Math.max(wallX, inner) + 1, z + 3, Materials.DECK_WOOD);
        b.banner(wallX + direction * 2, G + 9, z - 5, 5, Materials.CANDY_PINK, Materials.WOOD_DARK);
        b.banner(wallX + direction * 2, G + 9, z + 5, 5, Materials.SKY_BLUE, Materials.WOOD_DARK);
    }

    /** Four corner turrets in different colours, each climbable. */
    private void towers(MapBuilder b) {
        int[][] corners = {
                {KEEP_X0 - 14, KEEP_Z0 - 14}, {KEEP_X1 + 14, KEEP_Z0 - 14},
                {KEEP_X0 - 14, KEEP_Z1 + 14}, {KEEP_X1 + 14, KEEP_Z1 + 14},
        };
        int[] roofs = {Materials.CANDY_PINK, Materials.MINT, Materials.SKY_BLUE, Materials.GRAPE};

        for (int i = 0; i < corners.length; i++) {
            int x = corners[i][0];
            int z = corners[i][1];
            b.pillar(x, G, z, WALL_HEIGHT + 8, 4, Materials.STONE_LIGHT);
            b.pillar(x, G, z, WALL_HEIGHT + 7, 3, Materials.AIR);
            b.fill(x - 4, G + WALL_HEIGHT + 8, z - 4, x + 4, G + WALL_HEIGHT + 8, z + 4,
                    Materials.STONE_LIGHT);
            b.cone(x, G + WALL_HEIGHT + 9, z, 8, 5, roofs[i]);
            b.set(x, G + WALL_HEIGHT + 17, z, Materials.LANTERN);

            // The wall walk enters a real lookout room, not a disconnected decorative spiral.
            b.disc(x, G + WALL_HEIGHT + 1, z, 3, Materials.WOOD_LIGHT);
            b.doorway(x + (x < 64 ? 4 : -4), G + WALL_HEIGHT + 2, z, 0, 3, 4);
            b.doorway(x, G + WALL_HEIGHT + 2, z + (z < 64 ? 4 : -4), 1, 3, 4);
            // Arrow slits.
            for (int level = 4; level < WALL_HEIGHT + 6; level += 5) {
                b.clear(x - 4, G + level, z, x + 4, G + level + 1, z);
                b.clear(x, G + level, z - 4, x, G + level + 1, z + 4);
            }
        }
    }

    /** The keep: a two-storey great hall with banners and a roof deck. */
    private void keep(MapBuilder b) {
        b.fill(KEEP_X0, G - 1, KEEP_Z0, KEEP_X1, G - 1, KEEP_Z1, Materials.STONE_LIGHT);
        b.walls(KEEP_X0, G, KEEP_Z0, KEEP_X1, G + 9, KEEP_Z1, Materials.STONE_LIGHT);
        b.fill(KEEP_X0, G + 10, KEEP_Z0, KEEP_X1, G + 10, KEEP_Z1, Materials.WOOD_LIGHT);
        b.walls(KEEP_X0, G + 11, KEEP_Z0, KEEP_X1, G + 16, KEEP_Z1, Materials.STONE_LIGHT);
        b.fill(KEEP_X0, G + 17, KEEP_Z0, KEEP_X1, G + 17, KEEP_Z1, Materials.STONE_LIGHT);

        // Great hall doors on all four sides.
        b.doorway(KEEP_X0, G, 64, 0, 5, 5);
        b.doorway(KEEP_X1, G, 64, 0, 5, 5);
        b.doorway(64, G, KEEP_Z0, 1, 5, 5);
        b.doorway(64, G, KEEP_Z1, 1, 5, 5);

        // Windows on both floors.
        for (int i = KEEP_X0 + 6; i < KEEP_X1 - 4; i += 8) {
            b.window(i, G + 4, KEEP_Z0, i + 1, G + 6, KEEP_Z0, true);
            b.window(i, G + 4, KEEP_Z1, i + 1, G + 6, KEEP_Z1, true);
            b.window(i, G + 13, KEEP_Z0, i + 1, G + 15, KEEP_Z0, true);
            b.window(i, G + 13, KEEP_Z1, i + 1, G + 15, KEEP_Z1, true);
        }

        // Hall interior: a carpet, long tables, banners and a throne.
        b.fill(KEEP_X0 + 8, G - 1, 62, KEEP_X1 - 8, G - 1, 66, Materials.CANDY_PINK);
        for (int i = 0; i < 3; i++) {
            int tx = KEEP_X0 + 10 + i * 10;
            b.fill(tx, G, 54, tx + 6, G + 1, 56, Materials.WOOD_LIGHT);
            b.fill(tx, G, 72, tx + 6, G + 1, 74, Materials.WOOD_LIGHT);
            b.set(tx + 3, G + 2, 55, Materials.LANTERN);
            b.set(tx + 3, G + 2, 73, Materials.LANTERN);
        }
        for (int i = 0; i < 4; i++) {
            b.banner(KEEP_X0 + 6 + i * 9, G + 8, KEEP_Z0 + 1, 5,
                    i % 2 == 0 ? Materials.SKY_BLUE : Materials.CANDY_PINK, Materials.GOLD);
            b.banner(KEEP_X0 + 6 + i * 9, G + 8, KEEP_Z1 - 1, 5,
                    i % 2 == 0 ? Materials.MINT : Materials.GRAPE, Materials.GOLD);
        }
        b.fill(KEEP_X1 - 8, G, 61, KEEP_X1 - 6, G + 4, 67, Materials.GOLD);
        b.clear(KEEP_X1 - 8, G, 62, KEEP_X1 - 7, G + 1, 66);

        // Stairs to the upper floor and the roof hatch.
        b.stairs(KEEP_X0 + 2, G, KEEP_Z0 + 2, 0, 11, 4, Materials.WOOD_LIGHT);
        b.landing(KEEP_X0 + 13, G + 10, KEEP_Z0 + 2, KEEP_X0 + 16, KEEP_Z0 + 5, Materials.WOOD_LIGHT);
        b.stairs(KEEP_X1 - 2, G + 11, KEEP_Z1 - 6, 1, 7, 3, Materials.WOOD_LIGHT);
        b.landing(KEEP_X1 - 11, G + 17, KEEP_Z1 - 6, KEEP_X1 - 9, KEEP_Z1 - 4, Materials.STONE_LIGHT);

        // Roof deck with crenellations and a jump pad back down into the courtyard.
        for (int i = KEEP_X0; i <= KEEP_X1; i += 3) {
            b.set(i, G + 18, KEEP_Z0, Materials.STONE_LIGHT);
            b.set(i, G + 18, KEEP_Z1, Materials.STONE_LIGHT);
        }
        for (int i = KEEP_Z0; i <= KEEP_Z1; i += 3) {
            b.set(KEEP_X0, G + 18, i, Materials.STONE_LIGHT);
            b.set(KEEP_X1, G + 18, i, Materials.STONE_LIGHT);
        }
        b.fill(60, G + 18, 60, 68, G + 22, 68, Materials.ROOF_TEAL);
        b.cone(64, G + 23, 64, 6, 5, Materials.CANDY_PINK);
        b.set(64, G + 29, 64, Materials.GOLD);
    }

    /** Between wall and keep: market stalls, hay, a well and jump pads onto the wall walk. */
    private void courtyard(MapBuilder b) {
        b.speckle(KEEP_X0 - 13, G - 1, KEEP_Z0 - 13, KEEP_X1 + 13, G - 1, KEEP_Z1 + 13,
                Materials.STONE_LIGHT, Materials.STONE, 0.25f);

        for (int i = 0; i < 4; i++) {
            int x = KEEP_X0 - 11 + i * 4;
            b.fill(x, G, KEEP_Z0 - 10, x + 2, G + 1, KEEP_Z0 - 7, Materials.WOOD_LIGHT);
            b.fill(x - 1, G + 4, KEEP_Z0 - 11, x + 3, G + 4, KEEP_Z0 - 6,
                    i % 2 == 0 ? Materials.CORAL : Materials.MINT);
            b.fill(x - 1, G, KEEP_Z0 - 11, x - 1, G + 4, KEEP_Z0 - 11, Materials.WOOD_DARK);

            int z = KEEP_Z1 + 7;
            b.fill(KEEP_X1 + 5, G, z + i * 4, KEEP_X1 + 7, G + 1, z + i * 4 + 2,
                    Materials.WOOD_LIGHT);
            b.fill(KEEP_X1 + 4, G + 4, z + i * 4 - 1, KEEP_X1 + 8, G + 4, z + i * 4 + 3,
                    i % 2 == 0 ? Materials.SUNNY : Materials.GRAPE);
        }

        b.crateStack(KEEP_X0 - 8, G, KEEP_Z1 + 6, 3, Materials.HAY, Materials.WOOD_DARK);
        b.crateStack(KEEP_X1 + 6, G, KEEP_Z0 - 8, 3, Materials.HAY, Materials.WOOD_DARK);

        // Well.
        b.pillar(64, G, 38, 2, 3, Materials.STONE_LIGHT);
        b.clear(64, G, 38, 64, G + 1, 38);
        b.set(64, G, 38, Materials.WATER);
        b.fill(62, G + 5, 36, 66, G + 5, 40, Materials.ROOF_TEAL);
        b.fill(62, G + 2, 36, 62, G + 4, 36, Materials.WOOD_DARK);
        b.fill(66, G + 2, 40, 66, G + 4, 40, Materials.WOOD_DARK);

        b.jumpPad(KEEP_X0 - 12, G, 56);
        b.jumpPad(KEEP_X1 + 12, G, 72);
        b.jumpPad(56, G, KEEP_Z0 - 12);
        b.jumpPad(72, G, KEEP_Z1 + 12);
    }

    /** Approaches from the two spawn meadows: hedged lanes with cover on the way in. */
    private void approach(MapBuilder b) {
        for (int side = 0; side < 2; side++) {
            int direction = side == 0 ? 1 : -1;
            int start = side == 0 ? 10 : SIZE_X - 11;
            for (int i = 0; i < 5; i++) {
                int x = start + direction * i * 4;
                b.fill(x, G, 54, x + 1, G + 2, 56, Materials.LEAVES);
                b.fill(x, G, 72, x + 1, G + 2, 74, Materials.LEAVES);
            }
            b.crateStack(start + direction * 12, G, 62, 2, Materials.HAY, Materials.WOOD_DARK);
            b.crateStack(start + direction * 12, G, 68, 2, Materials.HAY, Materials.WOOD_DARK);
            b.set(start + direction * 6, G + 4, 64, Materials.LANTERN);
            b.fill(start + direction * 6, G, 64, start + direction * 6, G + 3, 64,
                    Materials.WOOD_DARK);
        }
    }
}
