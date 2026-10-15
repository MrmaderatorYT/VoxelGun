package com.ccs.voxelgun.map.maps;

import com.ccs.voxelgun.map.MapBuilder;
import com.ccs.voxelgun.map.scenery.DecorRecipes;
import com.ccs.voxelgun.map.scenery.MapScenery;
import com.ccs.voxelgun.voxel.Materials;

/** One continuous rural battlefield: a market village, orchard, working farm and mill pond. */
final class FarmlandBattlefield {
    private static final int W = 640;
    private static final int D = 576;
    private static final int G = MapBuilder.GROUND;

    private FarmlandBattlefield() {}

    static void build(MapBuilder b) {
        b.fill(0, 0, 0, W - 1, G - 2, D - 1, Materials.DIRT);
        b.slab(0, 0, W - 1, D - 1, G - 1, Materials.GRASS);
        // Large meadow patches follow the landscape rather than the old district boundaries.
        ellipse(b, 93, 418, 88, 137, Materials.GRASS_BRIGHT);
        ellipse(b, 540, 463, 69, 89, Materials.GRASS_BRIGHT);
        ellipse(b, 255, 55, 113, 44, Materials.DIRT);
        ellipse(b, 563, 70, 62, 46, Materials.GRASS_BRIGHT);

        watercourse(b);
        fields(b);
        orchard(b);
        village(b);
        workingFarm(b);
        millMeadow(b);
        riversidePark(b);
        lanesAndCrossings(b);
        roadsideCover(b);
        callouts(b);
        preserveRoutes(b);
    }

    private static void watercourse(MapBuilder b) {
        // The entire canal has a one-block-deep bed. Anyone can wade out on either bank.
        for (int z = 4; z < D - 4; z++) {
            int cx = riverX(z);
            int half = 6 + (int) (2 * Math.sin(z * .023) + 2);
            for (int x = cx - half - 3; x <= cx + half + 3; x++) {
                b.set(x, G - 1, z, Math.abs(x - cx) <= half
                        ? Materials.WATER : Materials.SAND);
            }
        }
        for (int z = 385; z <= 451; z++) {
            for (int x = 384; x <= 476; x++) {
                double r = (x - 432.0) * (x - 432.0) / (44 * 44)
                        + (z - 418.0) * (z - 418.0) / (30 * 30);
                if (r <= 1.1) b.set(x, G - 1, z, r <= 1
                        ? Materials.WATER : Materials.SAND);
            }
        }
        // Reeds sit on the outer banks; no continuous hedge seals the channel.
        for (int z = 24; z < D - 24; z += 29) {
            int x = riverX(z) + (z % 2 == 0 ? -12 : 13);
            b.fill(x, G, z, x + 1, G + 1, z + 3, Materials.LEAVES);
        }
    }

    private static int riverX(int z) {
        return 407 + (int) (23 * Math.sin(z * .013) + 9 * Math.sin(z * .037));
    }

    private static void fields(MapBuilder b) {
        // The northern wheat field has a curved boundary and long open furrows.
        for (int z = 17; z <= 78; z++) {
            int inset = (int) (11 + 9 * Math.sin(z * .055));
            for (int x = 180 + inset; x <= 305 - inset; x++) {
                b.set(x, G - 1, z, (x / 7) % 4 == 0 ? Materials.DIRT : Materials.HAY);
            }
        }
        // A broad western pasture stays green; hay bales are gathered around its curved track.
        for (int z = 343; z < 548; z++) {
            int edge = 116 + (int) (15 * Math.sin(z * .021));
            for (int x = 39; x < edge; x++) {
                if ((x + z / 2) % 23 < 12) b.set(x, G - 1, z, Materials.HAY);
            }
        }
        // Different farming pattern in the southern field: diagonal ploughed strips and a
        // broad grassy headland. These are ground-level crops, so the field remains playable.
        for (int z = 344; z < 550; z++) {
            for (int x = 184; x < 302; x++) {
                if ((x - 242) * (x - 242) / 3600.0
                        + (z - 448) * (z - 448) / 12000.0 < 1) {
                    b.set(x, G - 1, z, ((x + z) / 6) % 3 == 0
                            ? Materials.DIRT : Materials.HAY);
                }
            }
        }
        path(b, 54, 331, 119, 416, 5, Materials.DIRT);
        path(b, 119, 416, 72, 552, 5, Materials.DIRT);
        path(b, 174, 362, 258, 421, 5, Materials.DIRT);
        path(b, 258, 421, 293, 552, 5, Materials.DIRT);
        hay(b, 70, 361, 7, 4, 5);
        hay(b, 114, 441, 9, 3, 6);
        hay(b, 51, 506, 7, 3, 8);
        hay(b, 196, 388, 9, 3, 6);
        hay(b, 275, 450, 8, 4, 6);
        hay(b, 208, 515, 12, 3, 7);
        hay(b, 278, 58, 10, 4, 6);
        b.tree(128, G, 382, 10);
        b.tree(59, G, 457, 8);
        b.tree(288, G, 534, 9);
        // Broken hedgerows offer a flank without turning each field into an enclosed pen.
        hedge(b, 175, 336, true, 36);
        hedge(b, 244, 336, true, 57);
        hedge(b, 143, 373, false, 41);
        hedge(b, 143, 445, false, 22);
        hedge(b, 175, 502, false, 37);
        hedge(b, 345, 31, false, 39);
    }

    private static void orchard(MapBuilder b) {
        // An actual orchard with open row aisles, a diagonal picking path and a packing shed.
        ellipse(b, 93, 119, 57, 105, Materials.GRASS_BRIGHT);
        for (int row = 0; row < 12; row++) {
            int z = 26 + row * 18;
            for (int column = 0; column < 5; column++) {
                int x = 45 + column * 20 + (row % 2) * 3;
                if (z >= 168 && z <= 213 && x < 104 || Math.abs(z - 96) < 9) continue;
                if (Math.abs(x - (47 + (z - 24) * .43)) < 7) continue;
                b.tree(x, G, z, 5 + (row + column) % 3);
                // A few bright fruit clusters make this read as cultivated trees.
                b.scenery().box(x - 1.1f, G + 5.5f, z - 1.8f,
                        .45f, .45f, .45f, Materials.CORAL);
            }
        }
        path(b, 47, 24, 137, 232, 4, Materials.DIRT);
        path(b, 35, 160, 143, 160, 4, Materials.DIRT);
        countryBuilding(b, 58, 182, 38, 25, 8, Materials.BRICK_TAN,
                Materials.BRICK_TAN_ALT, Materials.ROOF_TILE);
        b.doorway(77, G, 182, 1, 7, 5);
        b.doorway(95, G, 194, 0, 5, 5);
        b.crate(63, G, 198, 4, Materials.CRATE, Materials.WOOD_DARK);
        b.crate(85, G, 190, 3, Materials.CRATE, Materials.WOOD_DARK);
        b.fill(61, G, 187, 69, G + 1, 188, Materials.WOOD);
        canopy(b, 58, 170, 38, 10, 7, Materials.CANVAS);
        DecorRecipes.sign(b.scenery(), 62, 9.2f, 181.84f, "ORCHARD", .4f,
                Materials.TRIM_RED);
        // Picnic garden gives a sheltered cross-route between the orchard and market village.
        ellipse(b, 106, 252, 35, 22, Materials.GRASS_BRIGHT);
        path(b, 76, 250, 161, 250, 5, Materials.STONE_LIGHT);
        b.tree(88, G, 234, 8);
        b.tree(123, G, 267, 7);
        bench(b, 94, 257, 9);
        bench(b, 120, 241, 8);
        b.fill(74, G, 249, 78, G + 1, 253, Materials.STONE_LIGHT);
    }

    private static void village(MapBuilder b) {
        // The market is organised around a generous circular square and a smaller garden.
        ellipse(b, 235, 218, 50, 49, Materials.STONE_LIGHT);
        ring(b, 235, 218, 41, 6, Materials.DIRT);
        ellipse(b, 235, 218, 27, 27, Materials.TILE_FLOOR);
        path(b, 159, 218, 320, 218, 5, Materials.STONE_LIGHT);
        path(b, 235, 99, 235, 283, 5, Materials.STONE_LIGHT);
        // A stone well identifies the middle of the circle without sealing its circulation.
        for (int z = 213; z <= 223; z++) {
            for (int x = 230; x <= 240; x++) {
                int r = (x - 235) * (x - 235) + (z - 218) * (z - 218);
                if (r >= 12 && r <= 25) b.fill(x, G, z, x, G + 1, z, Materials.STONE);
            }
        }
        b.fill(231, G + 2, 218, 231, G + 6, 218, Materials.WOOD_DARK);
        b.fill(239, G + 2, 218, 239, G + 6, 218, Materials.WOOD_DARK);
        b.roof(229, 215, 241, 221, G + 7, Materials.ROOF_TILE);
        // Farm shop: wide storefront, rear delivery entrance, usable shelves and counter.
        countryBuilding(b, 212, 135, 47, 31, 10, Materials.BRICK_WHITE,
                Materials.BRICK_WHITE_ALT, Materials.ROOF_TEAL);
        b.doorway(235, G, 165, 1, 7, 5);
        b.doorway(235, G, 135, 1, 5, 5);
        b.doorway(258, G, 151, 0, 5, 5);
        b.fill(216, G, 141, 218, G + 3, 154, Materials.WOOD_DARK);
        b.fill(250, G, 141, 252, G + 3, 154, Materials.WOOD_DARK);
        b.fill(222, G, 143, 228, G + 1, 145, Materials.WOOD_LIGHT);
        b.fill(242, G, 154, 252, G + 1, 156, Materials.WOOD);
        DecorRecipes.awning(b.scenery(), 215, 8.5f, 166.15f, 41, Materials.TRIM_RED);
        DecorRecipes.sign(b.scenery(), 219, 11.2f, 134.84f, "FARM SHOP", .52f,
                Materials.TRIM_RED);
        countryBuilding(b, 176, 184, 29, 28, 8, Materials.BRICK_TAN,
                Materials.BRICK_TAN_ALT, Materials.ROOF_TILE);
        b.doorway(204, G, 198, 0, 5, 5);
        b.fill(181, G, 190, 187, G + 2, 193, Materials.STONE);
        b.fill(180, G, 204, 195, G + 1, 206, Materials.WOOD_LIGHT);
        DecorRecipes.sign(b.scenery(), 180, 9, 183.84f, "BAKERY", .35f,
                Materials.WOOD_DARK);
        countryBuilding(b, 281, 181, 27, 36, 9, Materials.BRICK_BURGUNDY,
                Materials.BRICK_BURGUNDY_ALT, Materials.ROOF_BLUE);
        b.doorway(281, G, 200, 0, 5, 5);
        b.fill(287, G, 187, 294, G + 1, 190, Materials.WOOD_DARK);
        b.fill(300, G, 205, 304, G + 1, 211, Materials.SKY_BLUE);
        countryBuilding(b, 211, 258, 45, 19, 8, Materials.BRICK_TEAL,
                Materials.BRICK_TEAL_ALT, Materials.ROOF_TILE);
        b.doorway(233, G, 258, 1, 5, 5);
        b.doorway(233, G, 276, 1, 5, 5);
        b.fill(216, G, 262, 225, G + 1, 264, Materials.WOOD_DARK);
        b.fill(245, G, 267, 250, G + 2, 273, Materials.WOOD);
        DecorRecipes.sign(b.scenery(), 216, 9, 257.84f, "VILLAGE INN", .3f,
                Materials.TRIM_BLUE);
        marketStall(b, 208, 231, Materials.TRIM_RED);
        marketStall(b, 254, 226, Materials.TRIM_BLUE);
        b.tree(193, G, 236, 8);
        b.tree(274, G, 240, 9);
        bench(b, 219, 190, 8);
        bench(b, 242, 242, 9);
        DecorRecipes.flowers(b.scenery(), 221, G, 254, Materials.CORAL);
        DecorRecipes.flowers(b.scenery(), 250, G, 170, Materials.SUNNY);
    }

    private static void workingFarm(MapBuilder b) {
        // This eastern farm is a single complex of distinct working spaces, not a second hamlet.
        b.slab(501, 128, 603, 266, G - 1, Materials.DIRT);
        barn(b, 516, 140, 58, 38, Materials.BRICK_BURGUNDY, Materials.ROOF_TILE);
        b.doorway(545, G, 140, 1, 9, 7);
        b.doorway(545, G, 177, 1, 9, 7);
        b.doorway(516, G, 159, 0, 7, 6);
        hay(b, 522, 146, 10, 4, 9);
        hay(b, 560, 162, 9, 5, 10);
        canopy(b, 507, 141, 8, 35, 8, Materials.ROOF_TEAL);
        // Grain silo has a round silhouette and a broad walk-around yard.
        b.pillar(588, G, 190, 31, 9, Materials.CONCRETE);
        b.pillar(588, G + 31, 190, 2, 10, Materials.STEEL);
        for (int y = G + 7; y < G + 30; y += 7) {
            for (int z = 180; z <= 200; z++) {
                for (int x = 578; x <= 598; x++) {
                    int r = (x - 588) * (x - 588) + (z - 190) * (z - 190);
                    if (r >= 75 && r <= 96) b.set(x, y, z, Materials.STEEL);
                }
            }
        }
        barn(b, 514, 220, 48, 34, Materials.WOOD, Materials.ROOF_TEAL);
        b.doorway(538, G, 220, 1, 9, 6);
        b.doorway(538, G, 253, 1, 7, 6);
        b.doorway(561, G, 236, 0, 7, 6);
        b.fill(518, G, 224, 521, G + 3, 248, Materials.WOOD_DARK);
        b.fill(549, G, 224, 552, G + 2, 230, Materials.HAY);
        b.fill(549, G, 243, 552, G + 2, 249, Materials.HAY);
        canopy(b, 568, 226, 28, 17, 8, Materials.ROOF_TILE);
        b.crate(574, G, 230, 4, Materials.CRATE, Materials.WOOD_DARK);
        b.crate(588, G, 236, 3, Materials.CRATE, Materials.WOOD_DARK);
        b.barrel(566, G, 205, Materials.BARREL_TEAL);
        b.barrel(572, G, 203, Materials.BARREL_RUST);
        b.fill(530, G, 197, 536, G + 2, 201, Materials.PAINT_RED);
        b.fill(532, G + 3, 198, 534, G + 4, 200, Materials.STEEL);
        DecorRecipes.sign(b.scenery(), 526, 12, 139.84f, "GRAIN BARN", .45f,
                Materials.TRIM_RED);
        DecorRecipes.sign(b.scenery(), 520, 10, 219.84f, "DAIRY", .4f,
                Materials.TRIM_BLUE);
        // Northern pasture, cattle shelter and broken paddock fences.
        canopy(b, 526, 34, 43, 18, 7, Materials.ROOF_BLUE);
        hay(b, 531, 38, 8, 3, 8);
        b.fill(504, G, 64, 542, G + 1, 64, Materials.WOOD_DARK);
        b.fill(556, G, 64, 595, G + 1, 64, Materials.WOOD_DARK);
        b.tree(594, G, 38, 10);
        b.tree(510, G, 76, 8);
        path(b, 547, 70, 547, 137, 6, Materials.DIRT);
    }

    private static void millMeadow(MapBuilder b) {
        // A canal-side pump house has front, rear and side exits into its stone yard.
        b.slab(339, 332, 382, 378, G - 1, Materials.STONE_LIGHT);
        countryBuilding(b, 345, 340, 30, 27, 8, Materials.BRICK_TAN,
                Materials.BRICK_TAN_ALT, Materials.ROOF_BLUE);
        b.doorway(359, G, 340, 1, 5, 5);
        b.doorway(359, G, 366, 1, 5, 5);
        b.doorway(374, G, 353, 0, 5, 5);
        b.fill(349, G, 345, 353, G + 3, 348, Materials.STEEL);
        DecorRecipes.sign(b.scenery(), 349, 9.4f, 339.84f, "PUMP HOUSE", .28f,
                Materials.TRIM_BLUE);
        // Windmill is a unique visible destination, with an accessible ground-floor passage.
        b.pillar(545, G, 409, 22, 11, Materials.BRICK_WHITE);
        b.pillar(545, G + 22, 409, 8, 8, Materials.WOOD_DARK);
        b.pillar(545, G + 30, 409, 3, 10, Materials.ROOF_TILE);
        b.clear(541, G, 397, 549, G + 5, 421);
        b.clear(533, G, 405, 557, G + 5, 413);
        b.slab(539, 403, 551, 415, G - 1, Materials.WOOD);
        b.fill(542, G + 12, 398, 547, G + 15, 398, Materials.FRAME_LIGHT);
        // Mill sails use thin opaque scenery cuboids: visible at range, never snag movement.
        MapScenery s = b.scenery();
        s.box(543.6f, 14, 397.75f, 2.8f, 37, .65f, Materials.WOOD_DARK);
        s.box(526.5f, 31.3f, 397.75f, 37, 2.8f, .65f, Materials.WOOD_DARK);
        s.box(543.8f, 36, 397.3f, 6, 14, .35f, Materials.CANVAS);
        s.box(539.5f, 15, 397.3f, 6, 13, .35f, Materials.CANVAS);
        s.box(527.5f, 31.5f, 397.3f, 13, 6, .35f, Materials.CANVAS);
        s.box(550, 27.5f, 397.3f, 13, 6, .35f, Materials.CANVAS);
        ellipse(b, 545, 409, 35, 33, Materials.STONE_LIGHT);
        // The mill yard opens to the bridge, farm, orchard and southern lane.
        path(b, 478, 406, 546, 406, 7, Materials.DIRT);
        path(b, 545, 373, 545, 481, 6, Materials.DIRT);
        barn(b, 521, 334, 43, 28, Materials.BRICK_TAN, Materials.ROOF_TEAL);
        b.doorway(542, G, 334, 1, 7, 6);
        b.doorway(542, G, 361, 1, 7, 6);
        hay(b, 525, 340, 8, 3, 7);
        b.fill(553, G, 343, 558, G + 2, 353, Materials.WOOD_DARK);
        hay(b, 574, 381, 9, 4, 7);
        hay(b, 506, 431, 8, 3, 8);
        b.tree(580, G, 427, 11);
        b.tree(513, G, 453, 9);
        b.tree(596, G, 357, 10);
        // An old stone sheepfold in the south-east is open on three sides and has no roof.
        b.slab(524, 506, 579, 543, G - 1, Materials.DIRT);
        b.fill(533, G, 512, 567, G + 4, 512, Materials.STONE);
        b.fill(533, G, 512, 533, G + 3, 535, Materials.STONE_LIGHT);
        b.fill(533, G, 535, 548, G + 2, 535, Materials.STONE);
        b.fill(563, G, 529, 567, G + 3, 535, Materials.STONE);
        b.doorway(549, G, 512, 1, 7, 5);
        b.doorway(533, G, 522, 0, 5, 5);
        b.tree(582, G, 528, 10);
        b.tree(507, G, 530, 8);
        bench(b, 451, 455, 10);
        b.tree(455, G, 461, 9);
        b.tree(370, G, 429, 10);
        b.tree(391, G, 458, 8);
        DecorRecipes.sign(s, 539, 10, 397.84f, "MILL", .5f, Materials.TRIM_RED);
    }

    private static void lanesAndCrossings(MapBuilder b) {
        // Roads are authored in world space; their shoulders vary with the surrounding place.
        for (int x : new int[]{16, 160, 320, 480, 623}) {
            b.slab(x - 5, 3, x + 5, D - 4, G - 1, Materials.DIRT);
        }
        for (int z : new int[]{96, 288, 480}) {
            b.slab(3, z - 6, W - 4, z + 6, G - 1, Materials.DIRT);
            bridge(b, z, 13);
        }
        bridge(b, 192, 7);
        bridge(b, 362, 9);
        path(b, 305, 192, riverX(192) - 15, 192, 4, Materials.STONE_LIGHT);
        path(b, riverX(192) + 15, 192, 518, 192, 4, Materials.DIRT);
        path(b, 375, 362, riverX(362) - 15, 362, 5, Materials.STONE_LIGHT);
        path(b, riverX(362) + 15, 362, 515, 362, 5, Materials.STONE_LIGHT);
        // The lake-side path is an outer loop, not a dead-end jetty.
        path(b, 350, 385, 367, 452, 5, Materials.DIRT);
        path(b, 367, 452, 467, 463, 5, Materials.DIRT);
        path(b, 467, 463, 480, 386, 5, Materials.DIRT);
        // Low open-sided riverside shelter overlooks the water without covering a route core.
        canopy(b, 346, 233, 24, 16, 7, Materials.ROOF_BLUE);
        b.fill(346, G, 247, 369, G + 2, 248, Materials.WOOD_DARK);
        bench(b, 350, 242, 10);
        b.tree(364, G, 263, 9);
        b.tree(353, G, 166, 8);
        b.tree(458, G, 226, 10);
    }

    private static void riversidePark(MapBuilder b) {
        // Small copses and a wandering towpath make the bank a usable sheltered flank. Tree
        // groups follow the water and field edges rather than filling a repeated scatter grid.
        path(b, 349, 114, 370, 176, 3, Materials.DIRT);
        path(b, 370, 176, 348, 236, 3, Materials.DIRT);
        path(b, 348, 236, 363, 277, 3, Materials.DIRT);
        int[][] trees = {
                {351, 32, 10}, {368, 44, 12}, {358, 64, 9}, {384, 61, 11},
                {351, 137, 10}, {368, 131, 11}, {383, 153, 12}, {352, 179, 9},
                {385, 204, 11}, {370, 217, 10}, {351, 253, 12}, {381, 258, 9},
                {454, 135, 10}, {449, 153, 12}, {460, 176, 9}, {444, 244, 10},
                {350, 507, 11}, {369, 518, 12}, {346, 539, 10}, {377, 546, 9},
                {91, 323, 10}, {119, 331, 12}, {588, 470, 11}, {601, 448, 10}
        };
        for (int[] tree : trees) {
            int x = tree[0], z = tree[1], height = tree[2];
            b.tree(x, G, z, height);
            // Broad irregular crowns distinguish these shade trees from the little fruit rows.
            b.pillar(x - 1, G + height - 3, z + 1, 3, 4, Materials.LEAVES);
            b.pillar(x + 2, G + height - 1, z - 1, 3, 3, Materials.LEAVES);
        }
        b.fill(352, G, 223, 360, G + 1, 225, Materials.STONE_LIGHT);
        bench(b, 377, 237, 8);
        DecorRecipes.flowers(b.scenery(), 377, G, 241, Materials.CORAL);
    }

    private static void roadsideCover(MapBuilder b) {
        // Deliberately staggered shoulders break long sights while preserving wide road cores.
        hay(b, 52, 276, 8, 3, 5);
        b.sandbags(112, G, 299, true, 9, 2);
        b.fill(184, G, 277, 194, G + 2, 279, Materials.STONE);
        b.crate(268, G, 299, 4, Materials.CRATE, Materials.WOOD_DARK);
        b.sandbags(350, G, 278, true, 12, 2);
        hay(b, 441, 298, 9, 3, 5);
        b.fill(504, G, 277, 516, G + 2, 279, Materials.WOOD_DARK);
        b.crate(579, G, 299, 5, Materials.CRATE, Materials.WOOD_DARK);
        b.fill(149, G, 129, 151, G + 2, 140, Materials.STONE);
        hay(b, 170, 314, 7, 3, 6);
        b.fill(308, G, 150, 310, G + 2, 163, Materials.STONE_LIGHT);
        b.sandbags(330, G, 236, false, 11, 2);
        hay(b, 306, 411, 6, 4, 8);
        b.fill(489, G, 122, 491, G + 2, 133, Materials.WOOD_DARK);
        b.sandbags(469, G, 322, false, 9, 2);
        b.fill(490, G, 446, 493, G + 2, 455, Materials.STONE_LIGHT);
        b.sandbags(92, G, 85, true, 10, 2);
        b.fill(354, G, 106, 368, G + 2, 108, Materials.STONE_LIGHT);
        hay(b, 579, 83, 9, 3, 5);
        b.fill(346, G, 469, 359, G + 2, 471, Materials.WOOD_DARK);
        hay(b, 578, 491, 9, 3, 6);
        // Off-axis screens protect the two deployment grounds, with openings between pieces.
        b.fill(34, G, 264, 36, G + 4, 280, Materials.STONE);
        b.fill(43, G, 298, 45, G + 4, 315, Materials.WOOD_DARK);
        b.fill(603, G, 295, 605, G + 4, 312, Materials.STONE);
        b.fill(594, G, 263, 596, G + 4, 279, Materials.WOOD_DARK);
        b.tree(41, G, 242, 9);
        b.tree(599, G, 332, 10);
    }

    private static void callouts(MapBuilder b) {
        MapScenery s = b.scenery();
        s.landmark("Orchard", 91, 138, 97);
        s.landmark("Market Circle", 235, 218, 68);
        s.landmark("Wheat Fields", 242, 54, 60);
        s.landmark("Grain Farm", 548, 189, 87);
        s.landmark("Canal Bridge", riverX(288), 288, 34);
        s.landmark("Pump House", 359, 354, 32);
        s.landmark("Mill Pond", 432, 418, 45);
        s.landmark("Windmill", 545, 409, 59);
        s.landmark("South Pasture", 236, 443, 92);
        DecorRecipes.standingSign(s, 174, G, 307, "MARKET", .3f, Materials.TRIM_RED);
        DecorRecipes.standingSign(s, 492, G, 310, "FARM", .35f, Materials.TRIM_BLUE);
        DecorRecipes.standingSign(s, 346, G, 492, "MILL POND", .28f, Materials.TRIM_BLUE);
        DecorRecipes.standingSign(s, 130, G, 111, "ORCHARD", .28f, Materials.TRIM_RED);
    }

    private static void preserveRoutes(MapBuilder b) {
        // Keep every strategic spine and objective usable after all roofs, trees and props.
        for (int x : new int[]{16, 160, 320, 480, 623}) {
            b.clear(x - 3, G, 3, x + 3, 63, D - 4);
            b.slab(x - 3, 3, x + 3, D - 4, G - 1, Materials.DIRT);
        }
        for (int z : new int[]{96, 288, 480}) {
            b.clear(3, G, z - 3, W - 4, 63, z + 3);
        }
        for (int[] p : new int[][]{{160, 288}, {320, 96}, {320, 288},
                {320, 480}, {480, 288}}) {
            for (int z = p[1] - 14; z <= p[1] + 14; z++) {
                for (int x = p[0] - 14; x <= p[0] + 14; x++) {
                    if ((x - p[0]) * (x - p[0]) + (z - p[1]) * (z - p[1]) <= 196) {
                        b.clear(x, G, z, x, 63, z);
                        b.set(x, G - 1, z, Materials.STONE_LIGHT);
                    }
                }
            }
        }
        for (int[] p : new int[][]{{4, 27}, {612, 635}}) {
            b.clear(p[0], G, 260, p[1], 63, 315);
            b.slab(p[0], 260, p[1], 315, G - 1, Materials.DIRT);
        }
        b.slab(5, 286, 26, 290, G - 1, Materials.TEAM_BLUE);
        b.slab(613, 286, 634, 290, G - 1, Materials.TEAM_RED);
    }

    private static void bridge(MapBuilder b, int z, int width) {
        int x = riverX(z);
        b.slab(x - 19, z - width / 2, x + 19, z + width / 2, G - 1,
                Materials.DECK_WOOD);
        b.fill(x - 17, G, z - width / 2 - 1, x + 17, G + 1,
                z - width / 2 - 1, Materials.WOOD_DARK);
        b.fill(x - 17, G, z + width / 2 + 1, x + 17, G + 1,
                z + width / 2 + 1, Materials.WOOD_DARK);
        // Flat shoulders on each side let the crossing be approached from the towpath too.
        b.slab(x - 22, z - width / 2 - 2, x - 18, z + width / 2 + 2,
                G - 1, Materials.STONE_LIGHT);
        b.slab(x + 18, z - width / 2 - 2, x + 22, z + width / 2 + 2,
                G - 1, Materials.STONE_LIGHT);
    }

    private static void countryBuilding(MapBuilder b, int x, int z, int width, int depth,
                                        int height, int wall, int alt, int roof) {
        int x1 = x + width - 1, z1 = z + depth - 1;
        b.slab(x, z, x1, z1, G - 1, Materials.WOOD_LIGHT);
        b.brickWalls(x, G, z, x1, G + height - 1, z1, wall, alt);
        b.trimBand(x, z, x1, z1, G, G, Materials.STONE_LIGHT);
        b.quoins(x, z, x1, z1, G + 1, G + height - 1, Materials.PLASTER);
        b.trimBand(x, z, x1, z1, G + height - 1, G + height - 1, Materials.WOOD_DARK);
        for (int wx = x + 6; wx <= x1 - 5; wx += 11) {
            b.framedWindow(wx, G + 2, z, 1, 3, 3, true);
            b.framedWindow(wx, G + 2, z1, 1, 3, 3, true);
        }
        for (int wz = z + 6; wz <= z1 - 5; wz += 11) {
            b.framedWindow(x, G + 2, wz, 0, 3, 3, true);
            b.framedWindow(x1, G + 2, wz, 0, 3, 3, true);
        }
        b.roof(x - 1, z - 1, x1 + 1, z1 + 1, G + height, roof);
        b.doorway(x + width / 2, G, z, 1, 5, 5);
        b.doorway(x + width / 2, G, z1, 1, 5, 5);
    }

    private static void barn(MapBuilder b, int x, int z, int width, int depth,
                             int wall, int roof) {
        b.slab(x, z, x + width - 1, z + depth - 1, G - 1, Materials.DIRT);
        b.walls(x, G, z, x + width - 1, G + 10, z + depth - 1, wall);
        b.quoins(x, z, x + width - 1, z + depth - 1, G, G + 10, Materials.WOOD_DARK);
        b.roof(x - 1, z - 1, x + width, z + depth, G + 11, roof);
        for (int wx = x + 8; wx < x + width - 6; wx += 12) {
            b.window(wx, G + 6, z, wx + 3, G + 8, z, true);
            b.window(wx, G + 6, z + depth - 1, wx + 3, G + 8, z + depth - 1, true);
        }
    }

    private static void canopy(MapBuilder b, int x, int z, int width, int depth,
                               int height, int roof) {
        b.slab(x, z, x + width - 1, z + depth - 1, G + height, roof);
        for (int px : new int[]{x, x + width - 1}) {
            for (int pz : new int[]{z, z + depth - 1}) {
                b.fill(px, G, pz, px, G + height - 1, pz, Materials.WOOD_DARK);
            }
        }
    }

    private static void marketStall(MapBuilder b, int x, int z, int color) {
        canopy(b, x, z, 12, 8, 6, color);
        b.fill(x + 1, G, z + 5, x + 10, G + 1, z + 6, Materials.WOOD);
        for (int i = 0; i < 3; i++) {
            b.scenery().box(x + 2 + i * 3, G + 2.0f, z + 5.2f, 2, .6f, 1.2f,
                    i == 0 ? Materials.CORAL : i == 1 ? Materials.SUNNY : Materials.MINT);
        }
    }

    private static void bench(MapBuilder b, int x, int z, int width) {
        b.scenery().box(x, G + .7f, z, width, .3f, 1.3f, Materials.WOOD_DARK);
        b.scenery().box(x, G + 1.1f, z + 1.15f, width, .7f, .18f, Materials.WOOD);
        b.scenery().box(x + 1, G, z + .25f, .2f, .8f, .8f, Materials.STEEL);
        b.scenery().box(x + width - 1.2f, G, z + .25f, .2f, .8f, .8f, Materials.STEEL);
    }

    private static void hay(MapBuilder b, int x, int z, int width, int height, int depth) {
        b.fill(x, G, z, x + width - 1, G + height - 1, z + depth - 1, Materials.HAY);
        b.fill(x + width / 3, G, z, x + width / 3, G + height - 1,
                z + depth - 1, Materials.CANVAS);
    }

    private static void hedge(MapBuilder b, int x, int z, boolean alongX, int length) {
        b.fill(x, G, z, x + (alongX ? length - 1 : 1), G + 1,
                z + (alongX ? 1 : length - 1), Materials.LEAVES);
    }

    private static void path(MapBuilder b, int ax, int az, int bx, int bz, int halfWidth,
                             int material) {
        int steps = Math.max(Math.abs(bx - ax), Math.abs(bz - az));
        for (int i = 0; i <= steps; i++) {
            int x = ax + (bx - ax) * i / Math.max(1, steps);
            int z = az + (bz - az) * i / Math.max(1, steps);
            b.slab(x - halfWidth, z - halfWidth, x + halfWidth, z + halfWidth,
                    G - 1, material);
        }
    }

    private static void ellipse(MapBuilder b, int cx, int cz, int rx, int rz, int material) {
        for (int z = Math.max(0, cz - rz); z <= Math.min(D - 1, cz + rz); z++) {
            for (int x = Math.max(0, cx - rx); x <= Math.min(W - 1, cx + rx); x++) {
                if ((x - cx) * (x - cx) / (double) (rx * rx)
                        + (z - cz) * (z - cz) / (double) (rz * rz) <= 1) {
                    b.set(x, G - 1, z, material);
                }
            }
        }
    }

    private static void ring(MapBuilder b, int cx, int cz, int radius, int halfWidth,
                             int material) {
        int outer = radius + halfWidth, inner = radius - halfWidth;
        for (int z = cz - outer; z <= cz + outer; z++) {
            for (int x = cx - outer; x <= cx + outer; x++) {
                int r = (x - cx) * (x - cx) + (z - cz) * (z - cz);
                if (r <= outer * outer && r >= inner * inner) b.set(x, G - 1, z, material);
            }
        }
    }
}
