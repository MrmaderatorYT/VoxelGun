package com.ccs.voxelgun.map.maps;

import com.ccs.voxelgun.map.GameMap;
import com.ccs.voxelgun.map.MapBuilder;
import com.ccs.voxelgun.map.MapEnvironment;
import com.ccs.voxelgun.map.scenery.ClassicArenaDressing;
import com.ccs.voxelgun.voxel.Materials;

/**
 * Tropical cove built around a beached galleon. Bright sand, shallow water you can wade through,
 * boardwalks between the islets and three deck levels of rigging to fight over — the map is a
 * playground with a landmark in the middle rather than a symmetric competitive layout.
 */
public final class PirateCove extends GameMap {
    private static final int G = MapBuilder.GROUND;
    private static final int SHIP_X = 46;
    private static final int SHIP_Z = 52;
    private static final int SHIP_LENGTH = 44;
    private static final int SHIP_WIDTH = 18;

    public PirateCove() {
        super("pirate_cove", "Pirate Cove", "Tropical · galleon and boardwalks");
    }

    @Override
    public MapEnvironment environment() {
        return MapEnvironment.builder()
                .sun(-0.36f, 0.82f, 0.44f)
                .sunColor(1.00f, 0.96f, 0.86f, 5.4f)
                .sunAngle(0.026f)
                .sky(0.24f, 0.58f, 0.95f, 0.72f, 0.90f, 1.00f, 1.45f)
                .fog(0.006f)
                .exposure(1.05f)
                .build();
    }

    @Override
    protected void generate(MapBuilder b) {
        seabedAndWater(b);
        beaches(b);
        galleon(b);
        docks(b);
        village(b);

        ClassicArenaDressing.geometry(b, id());

        blueSpawn(18, 22, 135);
        blueSpawn(24, 18, 135);
        blueSpawn(16, 30, 135);
        blueSpawn(28, 26, 135);
        blueSpawn(20, 36, 135);
        blueSpawn(32, 20, 135);
        blueSpawn(26, 42, 135);

        redSpawn(102, 108, 315);
        redSpawn(104, 108, 315);
        redSpawn(110, 96, 315);
        redSpawn(98, 100, 315);
        redSpawn(106, 88, 315);
        redSpawn(94, 108, 315);
        redSpawn(112, 82, 315);

        freeSpawn(64, 20, 180);
        freeSpawn(64, 108, 0);
        freeSpawn(20, 64, 90);
        freeSpawn(108, 40, 250);
        freeSpawn(38, 96, 45);
        freeSpawn(88, 24, 200);
        freeSpawn(64, 40, 180);
        freeSpawn(64, 88, 0);
    }

    /** Sandy seabed with a shallow lagoon; the water is waist deep, never over your head. */
    private void seabedAndWater(MapBuilder b) {
        b.speckle(0, 0, 0, SIZE_X - 1, G - 2, SIZE_Z - 1, Materials.SAND_LIGHT, Materials.SAND,
                0.18f);
        b.speckle(0, G - 1, 0, SIZE_X - 1, G - 1, SIZE_Z - 1, Materials.SAND_LIGHT, Materials.SAND,
                0.12f);
        // Lagoon: one block scooped out and filled, so wading reads visually without drowning.
        for (int z = 0; z < SIZE_Z; z++) {
            for (int x = 0; x < SIZE_X; x++) {
                if (!onBeach(x, z)) {
                    b.set(x, G - 1, z, Materials.SAND);
                    b.set(x, G, z, Materials.WATER);
                }
            }
        }
        // Reef ring so the arena has an edge without a grey wall around it.
        for (int i = 0; i < 360; i += 3) {
            double angle = Math.toRadians(i);
            int x = 64 + (int) Math.round(Math.cos(angle) * 60);
            int z = 64 + (int) Math.round(Math.sin(angle) * 60);
            int height = 4 + (i / 30) % 4;
            b.fill(x, G, z, x, G + height, z, Materials.STONE_LIGHT);
            if (i % 12 == 0) {
                b.palm(x, G + height + 1, z, 5);
            }
        }
    }

    /** Two big sand shelves plus the central bar the galleon rests on. */
    private boolean onBeach(int x, int z) {
        if (distance(x, z, 26, 28) < 24) {
            return true;
        }
        if (distance(x, z, 102, 100) < 24) {
            return true;
        }
        return distance(x, z, 64, 64) < 30;
    }

    private static float distance(int x, int z, int cx, int cz) {
        float dx = x - cx;
        float dz = z - cz;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private void beaches(MapBuilder b) {
        for (int i = 0; i < 14; i++) {
            b.palm(18 + i * 3, G, 12 + (i % 4) * 5, 5 + i % 3);
            b.palm(96 + (i % 6) * 4, G, 112 - (i % 5) * 4, 5 + i % 3);
        }
        b.crateStack(34, G, 36, 3, Materials.WOOD_LIGHT, Materials.WOOD_DARK);
        b.crateStack(92, G, 92, 3, Materials.WOOD_LIGHT, Materials.WOOD_DARK);
        for (int i = 0; i < 6; i++) {
            b.barrel(22 + i * 4, G, 44, Materials.WOOD_DARK);
            b.barrel(100 - i * 4, G, 84, Materials.WOOD_DARK);
        }
    }

    /** The landmark: a three-deck galleon with masts, sails and a crow's nest. */
    private void galleon(MapBuilder b) {
        int x0 = SHIP_X;
        int x1 = SHIP_X + SHIP_LENGTH - 1;
        int z0 = SHIP_Z - SHIP_WIDTH / 2;
        int z1 = SHIP_Z + SHIP_WIDTH / 2;

        // Hull: tapering at bow and stern so it reads as a boat, not a box.
        for (int i = 0; i < SHIP_LENGTH; i++) {
            float t = Math.min(i, SHIP_LENGTH - 1 - i) / (float) (SHIP_LENGTH * 0.32f);
            int inset = Math.round((1f - Math.min(1f, t)) * (SHIP_WIDTH / 2f - 1f));
            b.fill(x0 + i, G, z0 + inset, x0 + i, G + 5, z1 - inset, Materials.WOOD);
        }
        // Hollow it out and lay the gun deck.
        for (int i = 2; i < SHIP_LENGTH - 2; i++) {
            float t = Math.min(i, SHIP_LENGTH - 1 - i) / (float) (SHIP_LENGTH * 0.32f);
            int inset = Math.round((1f - Math.min(1f, t)) * (SHIP_WIDTH / 2f - 1f)) + 1;
            if (z0 + inset >= z1 - inset) {
                continue;
            }
            b.clear(x0 + i, G + 1, z0 + inset, x0 + i, G + 4, z1 - inset);
            b.fill(x0 + i, G + 1, z0 + inset, x0 + i, G + 1, z1 - inset, Materials.WOOD_LIGHT);
        }

        // Main deck with a hatch down into the hold.
        b.fill(x0 + 2, G + 6, z0 + 2, x1 - 2, G + 6, z1 - 2, Materials.WOOD_LIGHT);
        b.clear(x0 + 14, G + 6, SHIP_Z - 2, x0 + 18, G + 6, SHIP_Z + 2);
        b.stairs(x0 + 13, G + 1, SHIP_Z - 2, 0, 6, 5, Materials.WOOD);
        b.landing(x0 + 19, G + 6, SHIP_Z - 2, x0 + 21, SHIP_Z + 2, Materials.WOOD_LIGHT);

        // Bulwarks with gun ports.
        b.fill(x0 + 2, G + 7, z0 + 2, x1 - 2, G + 8, z0 + 2, Materials.WOOD);
        b.fill(x0 + 2, G + 7, z1 - 2, x1 - 2, G + 8, z1 - 2, Materials.WOOD);
        for (int i = 6; i < SHIP_LENGTH - 6; i += 5) {
            b.clear(x0 + i, G + 7, z0 + 2, x0 + i + 1, G + 7, z0 + 2);
            b.clear(x0 + i, G + 7, z1 - 2, x0 + i + 1, G + 7, z1 - 2);
            b.fill(x0 + i, G + 3, z0 + 1, x0 + i, G + 3, z0 + 2, Materials.STEEL);
            b.fill(x0 + i, G + 3, z1 - 2, x0 + i, G + 3, z1 - 1, Materials.STEEL);
        }

        // Raised quarterdeck at the stern, reached by two stairs.
        b.fill(x1 - 12, G + 7, z0 + 3, x1 - 3, G + 9, z1 - 3, Materials.WOOD);
        b.fill(x1 - 12, G + 10, z0 + 3, x1 - 3, G + 10, z1 - 3, Materials.WOOD_LIGHT);
        b.stairs(x1 - 14, G + 7, z0 + 3, 0, 4, 3, Materials.WOOD);
        b.stairs(x1 - 14, G + 7, z1 - 5, 0, 4, 3, Materials.WOOD);
        b.fill(x1 - 12, G + 11, z0 + 3, x1 - 12, G + 11, z1 - 3, Materials.WOOD);

        // Masts, yards and sails.
        mast(b, x0 + 12, SHIP_Z, 22, 9);
        mast(b, x0 + 24, SHIP_Z, 27, 11);
        mast(b, x0 + 34, SHIP_Z, 18, 7);

        // Crow's nest on the tallest mast, the sniper perch.
        int nestY = G + 7 + 27;
        b.disc(x0 + 24, nestY, SHIP_Z, 3, Materials.WOOD);
        b.fill(x0 + 21, nestY + 1, SHIP_Z - 3, x0 + 27, nestY + 1, SHIP_Z + 3, Materials.AIR);
        for (int i = 0; i < 360; i += 45) {
            double angle = Math.toRadians(i);
            b.set(x0 + 24 + (int) Math.round(Math.cos(angle) * 3), nestY + 1,
                    SHIP_Z + (int) Math.round(Math.sin(angle) * 3), Materials.WOOD_DARK);
        }
        b.set(x0 + 24, nestY + 5, SHIP_Z, Materials.LANTERN);

        // Treasure in the hold, because of course.
        b.fill(x0 + 30, G + 2, SHIP_Z - 2, x0 + 33, G + 3, SHIP_Z + 2, Materials.WOOD_DARK);
        b.fill(x0 + 31, G + 4, SHIP_Z - 1, x0 + 32, G + 4, SHIP_Z + 1, Materials.GOLD);
        b.set(x0 + 6, G + 5, SHIP_Z, Materials.LANTERN);
        b.set(x0 + 26, G + 5, SHIP_Z, Materials.LANTERN);

        // Boarding ramps so the ship is reachable from both beaches.
        b.stairs(x0 - 6, G, SHIP_Z - 3, 0, 7, 6, Materials.WOOD);
        b.stairs(x1 + 6, G, SHIP_Z - 3, 1, 7, 6, Materials.WOOD);
        b.landing(x0 + 1, G + 6, SHIP_Z - 3, x0 + 4, SHIP_Z + 2, Materials.WOOD_LIGHT);
        b.landing(x1 - 4, G + 6, SHIP_Z - 3, x1 - 1, SHIP_Z + 2, Materials.WOOD_LIGHT);
    }

    /** One mast with its yard arm and a hanging sail. */
    private void mast(MapBuilder b, int x, int z, int height, int sailWidth) {
        int base = G + 6;
        b.fill(x, base, z, x, base + height, z, Materials.WOOD_DARK);
        int yard = base + height - 6;
        b.fill(x, yard, z - sailWidth / 2, x, yard, z + sailWidth / 2, Materials.WOOD_DARK);
        b.fill(x, yard - 5, z - sailWidth / 2 + 1, x, yard - 1, z + sailWidth / 2 - 1,
                Materials.CANVAS);
        b.set(x, base + height + 1, z, Materials.CORAL);
    }

    /** Boardwalks and jetties tying the beaches to the ship. */
    private void docks(MapBuilder b) {
        b.bridge(30, G + 1, 50, true, 18, 5, Materials.WOOD_LIGHT, Materials.WOOD_DARK);
        b.bridge(90, G + 1, 54, true, 16, 5, Materials.WOOD_LIGHT, Materials.WOOD_DARK);
        b.bridge(60, G + 1, 24, false, 22, 5, Materials.WOOD_LIGHT, Materials.WOOD_DARK);
        b.bridge(62, G + 1, 76, false, 24, 5, Materials.WOOD_LIGHT, Materials.WOOD_DARK);

        // Pilings under the walkways.
        for (int i = 0; i < 18; i += 4) {
            b.fill(30 + i, G, 50, 30 + i, G, 54, Materials.WOOD_DARK);
            b.fill(90 + i % 16, G, 54, 90 + i % 16, G, 58, Materials.WOOD_DARK);
        }
        for (int i = 0; i < 6; i++) {
            b.set(32 + i * 3, G + 4, 49, Materials.LANTERN);
            b.set(92 + i * 2, G + 4, 53, Materials.LANTERN);
        }
    }

    /** A handful of bright huts on the two beaches, plus a lookout tower. */
    private void village(MapBuilder b) {
        hut(b, 16, 20, Materials.CORAL, Materials.ROOF_TEAL);
        hut(b, 30, 14, Materials.SUNNY, Materials.ROOF_BLUE);
        hut(b, 14, 34, Materials.MINT, Materials.CORAL);
        hut(b, 100, 96, Materials.SKY_BLUE, Materials.CANDY_PINK);
        hut(b, 108, 108, Materials.CANDY_PINK, Materials.ROOF_TEAL);
        hut(b, 92, 104, Materials.SUNNY, Materials.ROOF_BLUE);

        // Lookout tower with a jump pad at the base for a fast climb.
        int x = 64;
        int z = 100;
        b.walls(x - 4, G, z - 4, x + 4, G + 20, z + 4, Materials.WOOD);
        b.doorway(x - 4, G, z, 0, 3, 4);
        b.landing(x - 3, G + 16, z - 3, x + 3, z + 3, Materials.WOOD_LIGHT);
        for (int level = 0; level < 4; level++) {
            boolean east = level % 2 == 0;
            int y = G + level * 4;
            if (level < 3) b.landing(east ? x + 1 : x - 3, y + 3, z - 3,
                    east ? x + 3 : x - 1, z + 3, Materials.WOOD_LIGHT);
            b.stairs(east ? x - 3 : x + 3, y, east ? z - 3 : z + 2,
                    east ? 0 : 1, level == 3 ? 5 : 4, 2, Materials.WOOD_LIGHT);
        }
        b.window(x - 4, G + 17, z - 1, x - 4, G + 19, z + 1, false);
        b.window(x + 4, G + 17, z - 1, x + 4, G + 19, z + 1, false);
        b.window(x - 1, G + 17, z - 4, x + 1, G + 19, z - 4, false);
        b.window(x - 1, G + 17, z + 4, x + 1, G + 19, z + 4, false);
        b.cone(x, G + 21, z, 5, 5, Materials.ROOF_TEAL);
        b.set(x, G + 26, z, Materials.LANTERN);
        b.jumpPad(x + 6, G, z);
        b.jumpPad(24, G, 64);
        b.jumpPad(104, G, 64);
    }

    /** Small painted hut with an open door and a bright roof. */
    private void hut(MapBuilder b, int x, int z, int wall, int roof) {
        int width = 9;
        int depth = 8;
        int height = 5;
        b.fill(x, G - 1, z, x + width, G - 1, z + depth, Materials.WOOD_LIGHT);
        b.walls(x, G, z, x + width, G + height, z + depth, wall);
        b.doorway(x + width / 2, G, z, 1, 3, 4);
        b.window(x, G + 2, z + depth / 2, x, G + 3, z + depth / 2 + 1, true);
        b.window(x + width, G + 2, z + depth / 2, x + width, G + 3, z + depth / 2 + 1, true);
        b.roof(x - 1, z - 1, x + width + 1, z + depth + 1, G + height + 1, roof);
        b.set(x + width / 2, G + 4, z - 1, Materials.LANTERN);
    }
}
