package com.ccs.voxelgun.map.maps;

import com.ccs.voxelgun.map.GameMap;
import com.ccs.voxelgun.map.CombatLayout;
import com.ccs.voxelgun.map.MapBuilder;
import com.ccs.voxelgun.map.MapEnvironment;
import com.ccs.voxelgun.map.scenery.ClassicArenaDressing;
import com.ccs.voxelgun.voxel.Materials;

/**
 * Daylight village: houses with enterable interiors, a market square, hedged lanes and a
 * church tower that owns the long diagonal. Neutral daylight keeps turf and facade colors clear.
 */
public final class Village extends GameMap {
    private static final int G = MapBuilder.GROUND;

    public Village() {
        super("village", "Village", "Outdoor · rooftops and lanes");
    }

    @Override
    public MapEnvironment environment() {
        return MapEnvironment.builder()
                .sun(-0.65f, 0.72f, 0.40f)
                .sunColor(1.00f, 0.95f, 0.84f, 3.8f)
                .sunAngle(0.035f)
                .sky(0.28f, 0.48f, 0.76f, 0.83f, 0.90f, 0.96f, 1.10f)
                .fog(0.006f)
                .exposure(0.95f)
                .build();
    }

    @Override
    protected void generate(MapBuilder b) {
        b.fill(0, 0, 0, SIZE_X - 1, G - 2, SIZE_Z - 1, Materials.DIRT);
        b.fill(0, G - 1, 0, SIZE_X - 1, G - 1, SIZE_Z - 1, Materials.GRASS);
        // Keep the village contained while its roofs, trees and tower define the skyline.
        b.boundary(4, 9, Materials.BRICK_WHITE, Materials.BRICK_WHITE_ALT, Materials.TRIM_RED);

        roads(b);
        houses(b);
        marketSquare(b);
        church(b);
        outskirts(b);
        combatRoutes(b);

        ClassicArenaDressing.geometry(b, id());

        blueSpawn(16, 16, 135);
        blueSpawn(20, 22, 135);
        blueSpawn(24, 16, 135);
        blueSpawn(16, 26, 135);
        blueSpawn(26, 24, 135);
        blueSpawn(22, 30, 135);
        blueSpawn(30, 18, 135);

        redSpawn(111, 111, 315);
        redSpawn(105, 105, 315);
        redSpawn(111, 101, 315);
        redSpawn(114, 106, 315);
        redSpawn(103, 99, 315);
        redSpawn(97, 105, 315);
        redSpawn(107, 95, 315);

        freeSpawn(64, 20, 180);
        freeSpawn(64, 106, 0);
        freeSpawn(20, 64, 90);
        freeSpawn(106, 64, 270);
        freeSpawn(64, 70, 45);
        freeSpawn(40, 88, 45);
        freeSpawn(88, 40, 225);
        freeSpawn(46, 46, 135);
    }

    /** Cobbled cross roads that connect every district. */
    private void roads(MapBuilder b) {
        b.speckle(6, G - 1, 58, 121, G - 1, 69, Materials.STONE, Materials.CONCRETE_DARK, 0.25f);
        b.speckle(58, G - 1, 6, 69, G - 1, 121, Materials.STONE, Materials.CONCRETE_DARK, 0.25f);
        for (int i = 12; i < 118; i += 18) {
            b.fill(i, G, 56, i, G + 4, 56, Materials.WOOD_DARK);
            b.lamp(i, G + 5, 56, Materials.WOOD_DARK);
            b.fill(56, G, i, 56, G + 4, i, Materials.WOOD_DARK);
            b.lamp(56, G + 5, i, Materials.WOOD_DARK);
        }
    }

    /** Six houses; three of them have roof access via crates or an outside stair. */
    private void houses(MapBuilder b) {
        // Six exteriors in three liveries, so no two neighbouring facades read the same.
        b.building(16, 74, 16, 14, 6, Materials.BRICK_WHITE, Materials.BRICK_WHITE_ALT,
                Materials.TRIM_RED);
        b.building(36, 80, 14, 16, 5, Materials.BRICK_ORANGE, Materials.BRICK_ORANGE_ALT,
                Materials.TRIM_RED);
        b.building(78, 16, 18, 14, 6, Materials.BRICK_WHITE, Materials.BRICK_WHITE_ALT,
                Materials.TRIM_BLUE);
        b.building(100, 34, 14, 14, 5, Materials.BRICK_TAN, Materials.BRICK_TAN_ALT,
                Materials.TRIM_RED);
        b.building(16, 38, 14, 12, 5, Materials.BRICK_ORANGE, Materials.BRICK_ORANGE_ALT,
                Materials.TRIM_GREY);
        b.building(96, 74, 16, 16, 6, Materials.BRICK_WHITE, Materials.BRICK_WHITE_ALT,
                Materials.TRIM_RED);

        // Interior furniture and a fireplace, so the insides are not empty boxes.
        b.fill(19, G, 78, 22, G + 1, 80, Materials.WOOD);
        b.fill(82, G, 20, 85, G + 1, 22, Materials.WOOD);
        b.fill(99, G, 78, 102, G + 1, 80, Materials.WOOD);
        b.fill(30, G, 78, 30, G + 4, 80, Materials.BRICK);
        b.set(30, G, 79, Materials.LAVA);

        // Roof access.
        b.landing(16, G + 6, 78, 19, 80, Materials.CONCRETE);
        b.stairs(9, G, 78, 0, 7, 3, Materials.WOOD);
        b.landing(92, G + 6, 20, 95, 22, Materials.CONCRETE);
        b.stairs(102, G, 20, 1, 7, 3, Materials.WOOD);
        b.landing(96, G + 6, 74, 99, 77, Materials.CONCRETE);
        b.stairs(96, G, 67, 2, 7, 3, Materials.WOOD);
    }

    /** Market square: stalls, crates and a well, the natural mid-map fight. */
    private void marketSquare(MapBuilder b) {
        // Paved square with grout lines, a planted kerb at each corner and a pool on one side:
        // the Block Strike courtyard, rather than a patch of noise.
        b.tiledFloor(46, 46, 82, 82, G - 1, Materials.TILE_FLOOR, Materials.TILE_GROUT, 4);
        b.kerb(46, 46, 51, 51, G - 1, Materials.GRASS_BRIGHT);
        b.kerb(77, 77, 82, 82, G - 1, Materials.GRASS_BRIGHT);
        b.pool(58, 84, 70, 92, G - 1, 3);
        b.deck(56, 94, 72, 97, G - 1);
        for (int i = 0; i < 4; i++) {
            int x = 50 + i * 8;
            b.fill(x, G, 50, x + 4, G + 1, 53, Materials.WOOD);
            b.fill(x, G + 4, 50, x + 4, G + 4, 53, Materials.CANVAS);
            b.fill(x, G, 50, x, G + 4, 50, Materials.WOOD_DARK);
            b.fill(x + 4, G, 53, x + 4, G + 4, 53, Materials.WOOD_DARK);

            int z = 74;
            b.fill(x, G, z, x + 4, G + 1, z + 3, Materials.WOOD);
            b.fill(x, G + 4, z, x + 4, G + 4, z + 3, Materials.CANVAS);
            b.fill(x, G, z, x, G + 4, z, Materials.WOOD_DARK);
        }

        b.pillar(64, G, 64, 2, 3, Materials.STONE);
        b.clear(64, G, 64, 64, G + 1, 64);
        b.fill(63, G + 2, 63, 65, G + 4, 65, Materials.AIR);
        b.fill(63, G + 5, 63, 65, G + 5, 65, Materials.ROOF);
        b.fill(63, G + 2, 63, 63, G + 4, 63, Materials.WOOD_DARK);
        b.fill(65, G + 2, 65, 65, G + 4, 65, Materials.WOOD_DARK);

        b.crateStack(48, G, 64, 2, Materials.CRATE, Materials.WOOD_DARK);
        b.crateStack(78, G, 64, 2, Materials.CRATE, Materials.WOOD_DARK);
        b.oilDrum(56, G, 68);
        b.oilDrum(72, G, 60);
        b.panelCrate(66, G, 48, 5);
    }

    /** Stone tower with an internal stair; the map's overwatch position. */
    private void church(MapBuilder b) {
        int x = 30;
        int z = 96;
        b.walls(x, G, z, x + 12, G + 10, z + 16, Materials.STONE);
        b.fill(x, G + 11, z, x + 12, G + 11, z + 16, Materials.WOOD_DARK);
        b.doorway(x + 6, G, z, 1, 3, 4);
        b.window(x, G + 4, z + 4, x, G + 7, z + 6, true);
        b.window(x + 12, G + 4, z + 10, x + 12, G + 7, z + 12, true);
        b.roof(x - 1, z - 1, x + 13, z + 17, G + 12, Materials.ROOF);

        int tx = x + 14;
        b.walls(tx, G, z + 2, tx + 8, G + 23, z + 10, Materials.STONE);
        b.fill(tx, G + 24, z + 2, tx + 8, G + 24, z + 10, Materials.ROOF);
        b.doorway(tx, G, z + 6, 0, 3, 4);
        for (int level = 0; level < 5; level++) {
            int y = G + level * 4;
            int direction = level % 2 == 0 ? 0 : 1;
            if (direction == 0)
                b.landing(tx + 5, y + 3, z + 3, tx + 7, z + 9, Materials.WOOD);
            else
                b.landing(tx + 1, y + 3, z + 3, tx + 3, z + 9, Materials.WOOD);
            b.stairs(tx + (direction == 0 ? 1 : 7), y,
                    z + (direction == 0 ? 3 : 8), direction, 4, 2, Materials.WOOD);
        }
        b.window(tx + 3, G + 20, z + 2, tx + 5, G + 22, z + 2, false);
        b.window(tx + 3, G + 20, z + 10, tx + 5, G + 22, z + 10, false);
        b.window(tx, G + 20, z + 5, tx, G + 22, z + 7, false);
        b.window(tx + 8, G + 20, z + 5, tx + 8, G + 22, z + 7, false);
        b.lamp(tx + 4, G + 23, z + 6, Materials.AIR);
    }

    /** Hedges, trees and fences that shape the outer lanes. */
    private void outskirts(MapBuilder b) {
        for (int i = 0; i < 7; i++) {
            b.tree(12 + i * 4, G, 110 + (i % 2) * 4, 5 + (i % 3));
            b.tree(110 - (i % 2) * 4, G, 14 + i * 4, 5 + (i % 3));
        }
        b.fence(40, G, 24, true, 20, 3);
        b.fence(76, G, 100, true, 20, 3);
        b.fence(24, G, 40, false, 14, 3);
        b.fence(100, G, 76, false, 14, 3);

        for (int i = 0; i < 5; i++) {
            b.fill(88 + i * 4, G, 108, 90 + i * 4, G + 2, 112, Materials.LEAVES);
        }
        b.crateStack(112, G, 40, 3, Materials.CRATE, Materials.WOOD_DARK);
        b.crateStack(14, G, 92, 3, Materials.CRATE, Materials.WOOD_DARK);
    }

    /** Market is the short exposed route; garden lanes give two longer covered alternatives. */
    private void combatRoutes(MapBuilder b) {
        // Connect outer gardens to the cross roads without removing any house facade.
        CombatLayout.walkway(b, 34, 28, 39, 60, Materials.STONE);
        CombatLayout.walkway(b, 88, 68, 93, 100, Materials.STONE);
        CombatLayout.walkway(b, 10, 54, 36, 59, Materials.STONE);
        CombatLayout.walkway(b, 92, 69, 117, 74, Materials.STONE);
        // Low stone planters offer cover on the outer streets, not in their running centre.
        for (int z : new int[]{42, 66, 90}) {
            b.fill(13, G, z, 15, G + 2, z + 4, Materials.BRICK_WHITE);
            b.fill(112, G, 127 - z - 4, 114, G + 2, 127 - z, Materials.BRICK_WHITE);
            b.fill(13, G + 3, z, 15, G + 3, z + 4, Materials.LEAVES);
            b.fill(112, G + 3, 127 - z - 4, 114, G + 3, 127 - z, Materials.LEAVES);
        }
        // The church sniper position now has ground-level approaches from three sides.
        b.doorway(36, G, 112, 1, 3, 4);
        b.doorway(42, G, 104, 0, 3, 4);
        b.doorway(48, G, 106, 1, 3, 4);
        // Offset courtyard screens protect deployment from the long diagonal, with open ends.
        b.fill(25, G, 33, 31, G + 2, 33, Materials.WOOD_DARK);
        b.fill(96, G, 94, 102, G + 2, 94, Materials.WOOD_DARK);
    }
}
