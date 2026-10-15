package com.ccs.voxelgun.map.maps;

import com.ccs.voxelgun.map.MapBuilder;
import com.ccs.voxelgun.voxel.Materials;

/** One authored city, with neighbourhoods and looping routes instead of copied city tiles. */
final class CityBattlefield {
    private static final int G = MapBuilder.GROUND;
    private static final int W = BattlefieldMaps.BATTLEFIELD_WIDTH;
    private static final int D = BattlefieldMaps.BATTLEFIELD_DEPTH;
    private static final int[] AVENUES = {16, 160, 320, 480, 623};
    private static final int[] CROSSTREETS = {96, 288, 480};
    private static final int[][] OBJECTIVES = {
            {160, 288}, {320, 96}, {320, 288}, {320, 480}, {480, 288}
    };

    private CityBattlefield() {}

    static void build(MapBuilder b) {
        b.fill(0, 0, 0, W - 1, G - 2, D - 1, Materials.DIRT);
        b.slab(0, 0, W - 1, D - 1, G - 1, Materials.GRASS);
        avenues(b);
        oldTown(b);
        marketQuarter(b);
        northernNeighbourhoods(b);
        civicCircle(b);
        transitQuarter(b);
        park(b);
        southernNeighbourhoods(b);
        streetFurniture(b);
        // Authoring never depends on a repeated district or on a later generic combat stencil.
        // These wide, level cores also connect the five capture circles and both deployments.
        keepRoutesOpen(b);
        boundary(b);
    }

    private static void avenues(MapBuilder b) {
        for (int x : AVENUES) {
            b.slab(x - 11, 4, x + 11, D - 5, G - 1, Materials.CONCRETE);
            b.slab(x - 7, 4, x + 7, D - 5, G - 1, Materials.ASPHALT);
            for (int z = 18; z < D - 18; z += 20) {
                b.slab(x, z, x, z + 7, G - 1, Materials.SUNNY);
            }
        }
        for (int z : CROSSTREETS) {
            b.slab(4, z - 12, W - 5, z + 12, G - 1, Materials.CONCRETE);
            b.slab(4, z - 8, W - 5, z + 8, G - 1, Materials.ASPHALT);
            for (int x = 36; x < W - 30; x += 20) {
                b.slab(x, z, x + 7, z, G - 1, Materials.SUNNY);
            }
            for (int x : AVENUES) {
                for (int offset = -6; offset <= 6; offset += 3) {
                    b.slab(x + offset, z - 15, x + offset + 1, z - 10,
                            G - 1, Materials.FRAME_LIGHT);
                    b.slab(x + offset, z + 10, x + offset + 1, z + 15,
                            G - 1, Materials.FRAME_LIGHT);
                }
            }
        }
    }

    private static void oldTown(MapBuilder b) {
        b.slab(34, 16, 141, 265, G - 1, Materials.STONE_LIGHT);
        // A diagonal high street and a bend through the café square avoid a block-grid layout.
        path(b, 41, 72, 133, 120, 7, Materials.CONCRETE, Materials.STONE);
        path(b, 82, 112, 78, 172, 6, Materials.CONCRETE, Materials.STONE);
        path(b, 78, 172, 99, 219, 6, Materials.CONCRETE, Materials.STONE);
        path(b, 99, 219, 133, 260, 6, Materials.CONCRETE, Materials.STONE);
        shop(b, 39, 24, 27, 27, Materials.BRICK_ORANGE,
                Materials.BRICK_ORANGE_ALT, Materials.TRIM_RED, true);
        shop(b, 96, 27, 37, 25, Materials.BRICK_WHITE,
                Materials.BRICK_WHITE_ALT, Materials.TRIM_BLUE, false);
        shop(b, 42, 122, 26, 24, Materials.BRICK_TEAL,
                Materials.BRICK_TEAL_ALT, Materials.CORAL, true);
        shop(b, 102, 138, 31, 22, Materials.BRICK_TAN,
                Materials.BRICK_TAN_ALT, Materials.TRIM_RED, false);
        shop(b, 39, 188, 28, 34, Materials.BRICK_BURGUNDY,
                Materials.BRICK_BURGUNDY_ALT, Materials.SUNNY, true);
        shop(b, 112, 225, 26, 31, Materials.BRICK_WHITE,
                Materials.BRICK_WHITE_ALT, Materials.ROOF_TEAL, false);
        // A small open café terrace; tables are scenery, and the planter edges are cover.
        disc(b, 106, 190, 20, Materials.TILE_FLOOR);
        ring(b, 106, 190, 17, 20, Materials.TILE_GROUT);
        planter(b, 85, 179, 5, 11, Materials.CORAL);
        planter(b, 123, 186, 5, 12, Materials.SUNNY);
        broadTree(b, 112, 205, 10, 4);
        broadTree(b, 78, 33, 8, 4);
        broadTree(b, 133, 70, 10, 4);
        broadTree(b, 41, 165, 9, 3);
        broadTree(b, 77, 245, 10, 4);
        // The west capture point has its own market stalls, with approaches between them.
        stall(b, 104, 269, Materials.TRIM_RED);
        stall(b, 123, 307, Materials.TRIM_BLUE);
        stall(b, 184, 304, Materials.ROOF_TEAL);
    }

    private static void marketQuarter(MapBuilder b) {
        b.slab(180, 22, 296, 265, G - 1, Materials.CONCRETE);
        b.slab(186, 29, 290, 79, G - 1, Materials.ASPHALT);
        // Parking is a connected forecourt, with trees breaking the long sight lines.
        for (int x = 191; x <= 281; x += 15) {
            b.slab(x, 32, x, 45, G - 1, Materials.FRAME_LIGHT);
            b.slab(x, 64, x, 77, G - 1, Materials.FRAME_LIGHT);
        }
        for (int x : new int[]{203, 247, 282}) {
            planter(b, x, 51, 5, 5, Materials.CORAL);
            broadTree(b, x + 2, 53, 9, 3);
        }
        car(b, 194, 35, Materials.TRIM_BLUE, false);
        car(b, 260, 67, Materials.TRIM_RED, false);
        car(b, 277, 34, Materials.SUNNY, false);
        supermarket(b);
        b.slab(280, 121, 299, 186, G - 1, Materials.ASPHALT);
        b.crate(285, G, 137, 4, Materials.CRATE, Materials.WOOD_DARK);
        b.crate(290, G, 154, 3, Materials.CRATE, Materials.WOOD_DARK);
        b.fill(282, G, 179, 289, G + 2, 182, Materials.STEEL);
        b.slab(178, 181, 305, 193, G - 1, Materials.STONE_LIGHT);
        apartment(b, 184, 213, 29, 36, 2, Materials.BRICK_ORANGE,
                Materials.BRICK_ORANGE_ALT, Materials.TRIM_BLUE);
        arcade(b, 237, 211, 55, 37);
    }

    /** Sales floor, produce, five aisle pairs, checkout lanes and a connected loading room. */
    private static void supermarket(MapBuilder b) {
        int x = 188, z = 126, x1 = 277, z1 = 175;
        shell(b, x, z, x1, z1, 11, Materials.BRICK_WHITE,
                Materials.BRICK_WHITE_ALT, Materials.TRIM_RED);
        b.checker(x + 1, z + 1, x1 - 1, z1 - 1, G - 1,
                Materials.TILE_FLOOR, Materials.PLASTER, 4);
        b.fill(x + 2, G + 2, z, x1 - 2, G + 6, z, Materials.GLASS);
        b.fill(x, G + 2, z + 5, x, G + 6, z1 - 5, Materials.GLASS);
        b.fill(x1, G + 1, z + 1, x1, G + 7, z1 - 1, Materials.BRICK_WHITE);
        // Wide central cross aisle and perimeter paths remain completely free of shelves.
        for (int aisle = 0; aisle < 5; aisle++) {
            int sx = 207 + aisle * 12;
            shelf(b, sx, 139, sx + 2, 146, aisle);
            shelf(b, sx, 155, sx + 2, 165, aisle + 1);
        }
        b.fill(194, G, 145, 199, G + 1, 149, Materials.WOOD_LIGHT);
        b.fill(194, G, 158, 199, G + 1, 162, Materials.WOOD_LIGHT);
        for (int i = 0; i < 3; i++) {
            int sx = 230 + i * 12;
            b.fill(sx, G, 131, sx + 5, G + 1, 133, Materials.TRIM_RED);
            b.set(sx + 1, G + 2, 132, Materials.SCREEN);
        }
        // Stockroom has a real internal portal plus a rear vehicle-width exit.
        b.fill(268, G, 136, 268, G + 7, z1 - 1, Materials.PLASTER);
        b.clear(268, G, 147, 268, G + 4, 153);
        b.crate(272, G, 140, 3, Materials.CRATE, Materials.WOOD_DARK);
        b.crate(272, G, 161, 3, Materials.CRATE, Materials.WOOD_DARK);
        portal(b, 214, z, 1, 9);
        portal(b, x, 151, 0, 7);
        portal(b, 242, z1, 1, 7);
        portal(b, x1, 151, 0, 7);
        // The frontage canopy is supported at the ends, away from both approaches.
        b.slab(188, 120, 245, 125, G + 7, Materials.TRIM_RED);
        b.fill(189, G, 121, 189, G + 6, 121, Materials.STEEL);
        b.fill(244, G, 121, 244, G + 6, 121, Materials.STEEL);
        b.fill(253, G + 12, 136, 260, G + 14, 142, Materials.STEEL);
    }

    private static void northernNeighbourhoods(MapBuilder b) {
        path(b, 350, 61, 452, 61, 7, Materials.CONCRETE, Materials.ASPHALT);
        path(b, 500, 157, 602, 157, 7, Materials.CONCRETE, Materials.ASPHALT);
        apartment(b, 355, 20, 35, 29, 3, Materials.BRICK_TAN,
                Materials.BRICK_TAN_ALT, Materials.CORAL);
        apartment(b, 417, 23, 39, 28, 2, Materials.BRICK_TEAL,
                Materials.BRICK_TEAL_ALT, Materials.SUNNY);
        apartment(b, 514, 29, 31, 39, 2, Materials.BRICK_BURGUNDY,
                Materials.BRICK_BURGUNDY_ALT, Materials.PLASTER);
        // A tucked-away courtyard between asymmetrical residential buildings.
        disc(b, 580, 55, 20, Materials.STONE_LIGHT);
        broadTree(b, 579, 54, 12, 5);
        planter(b, 564, 38, 6, 4, Materials.CORAL);
        planter(b, 591, 67, 5, 5, Materials.CANDY_PINK);
        shop(b, 352, 122, 35, 28, Materials.BRICK_WHITE,
                Materials.BRICK_WHITE_ALT, Materials.ROOF_TEAL, false);
        shop(b, 415, 124, 43, 30, Materials.BRICK_ORANGE,
                Materials.BRICK_ORANGE_ALT, Materials.TRIM_BLUE, true);
        library(b);
        fireStation(b);
        apartment(b, 552, 111, 44, 30, 3, Materials.BRICK_WHITE,
                Materials.BRICK_WHITE_ALT, Materials.TRIM_RED);
        broadTree(b, 402, 80, 12, 5);
        broadTree(b, 505, 119, 10, 4);
        broadTree(b, 589, 182, 11, 4);
        broadTree(b, 432, 242, 11, 5);
        broadTree(b, 454, 209, 9, 4);
    }

    private static void library(MapBuilder b) {
        shell(b, 348, 190, 403, 222, 12, Materials.BRICK_TAN,
                Materials.BRICK_TAN_ALT, Materials.PLASTER);
        b.slab(343, 184, 408, 189, G - 1, Materials.MARBLE);
        b.slab(344, 184, 407, 190, G + 8, Materials.PLASTER);
        for (int x : new int[]{346, 361, 390, 405}) {
            b.pillar(x, G, 186, 8, 1, Materials.MARBLE);
        }
        for (int x : new int[]{356, 393}) {
            shelf(b, x, 199, x + 2, 212, 0);
        }
        b.fill(366, G, 205, 383, G + 1, 207, Materials.WOOD);
        portal(b, 376, 190, 1, 9);
        portal(b, 376, 222, 1, 7);
        portal(b, 348, 215, 0, 5);
        for (int x : new int[]{357, 394}) {
            b.framedWindow(x, G + 3, 222, 1, 7, 4, true);
        }
    }

    private static void fireStation(MapBuilder b) {
        shell(b, 515, 201, 584, 237, 11, Materials.BRICK_BURGUNDY,
                Materials.BRICK_BURGUNDY_ALT, Materials.PLASTER);
        b.slab(510, 180, 590, 200, G - 1, Materials.ASPHALT);
        for (int x : new int[]{528, 551, 574}) {
            portal(b, x, 201, 1, 13);
            b.slab(x - 7, 199, x + 7, 200, G - 1, Materials.SUNNY);
        }
        b.fill(563, G, 211, 563, G + 7, 236, Materials.PLASTER);
        portal(b, 563, 228, 0, 5);
        portal(b, 578, 237, 1, 5);
        car(b, 525, 217, Materials.PAINT_RED, true);
        b.fill(527, G + 4, 223, 529, G + 4, 230, Materials.STEEL);
        b.fill(576, G, 221, 581, G + 1, 223, Materials.WOOD);
        // A short hose-drying tower gives this building a distinct silhouette.
        b.walls(582, G, 225, 590, G + 22, 235, Materials.BRICK_WHITE);
        b.slab(581, 224, 591, 236, G + 23, Materials.TRIM_RED);
        b.window(584, G + 15, 225, 588, G + 19, 225, false);
        portal(b, 586, 235, 1, 5);
    }

    private static void civicCircle(MapBuilder b) {
        disc(b, 320, 288, 73, Materials.CONCRETE);
        ring(b, 320, 288, 55, 66, Materials.ASPHALT);
        ring(b, 320, 288, 53, 54, Materials.KERB);
        ring(b, 320, 288, 67, 68, Materials.KERB);
        disc(b, 320, 288, 48, Materials.TILE_FLOOR);
        ring(b, 320, 288, 45, 47, Materials.MARBLE);
        // Four generous radial entries connect the roundabout to the arterial roads.
        b.slab(246, 282, 394, 294, G - 1, Materials.STONE_LIGHT);
        b.slab(314, 214, 326, 362, G - 1, Materials.STONE_LIGHT);
        // Off-axis fountain and clock keep the central capture circle unobstructed.
        fountain(b, 289, 252, 9);
        clockTower(b, 353, 253);
        pavilion(b, 278, 324, 10, Materials.ROOF_TEAL);
        planter(b, 342, 320, 13, 5, Materials.CANDY_PINK);
        planter(b, 296, 308, 5, 12, Materials.CORAL);
        broadTree(b, 275, 275, 10, 4);
        broadTree(b, 365, 309, 10, 4);
        broadTree(b, 302, 334, 9, 4);
        broadTree(b, 337, 240, 10, 4);
    }

    private static void transitQuarter(MapBuilder b) {
        b.slab(37, 336, 139, 462, G - 1, Materials.CONCRETE);
        // The depot has a barrel roof, open passenger concourse and a through rear exit.
        b.brickWalls(49, G, 348, 128, G + 9, 381,
                Materials.BRICK_WHITE, Materials.BRICK_WHITE_ALT);
        b.slab(49, 348, 128, 381, G - 1, Materials.TILE_FLOOR);
        for (int z = 346; z <= 383; z++) {
            double t = (z - 364.5) / 19.0;
            int top = G + 10 + (int) Math.round(6 * Math.sqrt(Math.max(0, 1 - t * t)));
            b.slab(47, z, 130, z, top, Materials.ROOF_BLUE);
        }
        for (int x : new int[]{65, 88, 112}) {
            portal(b, x, 348, 1, 11);
            b.framedWindow(x, G + 3, 381, 1, 7, 4, true);
        }
        portal(b, 88, 381, 1, 9);
        portal(b, 49, 365, 0, 7);
        portal(b, 128, 365, 0, 7);
        b.fill(55, G, 372, 70, G + 2, 375, Materials.WOOD);
        b.fill(107, G, 372, 121, G + 2, 375, Materials.WOOD);
        ellipseRing(b, 88, 423, 48, 30, 0.70, Materials.ASPHALT);
        ellipseDisc(b, 88, 423, 30, 16, Materials.GRASS_BRIGHT);
        broadTree(b, 78, 425, 11, 5);
        broadTree(b, 101, 419, 9, 4);
        bus(b, 51, 397, Materials.PAINT_BLUE);
        bus(b, 96, 447, Materials.CORAL);
        shelter(b, 101, 393, Materials.TRIM_BLUE);
        // A low service block, accessible from both yard and side lane.
        shell(b, 187, 348, 227, 375, 8, Materials.BRICK_TAN,
                Materials.BRICK_TAN_ALT, Materials.TRIM_GREY);
        portal(b, 187, 362, 0, 9);
        portal(b, 227, 362, 0, 7);
        b.fill(199, G, 367, 214, G + 2, 370, Materials.STEEL);
        apartment(b, 192, 412, 34, 39, 2, Materials.BRICK_TEAL,
                Materials.BRICK_TEAL_ALT, Materials.CORAL);
        // Gardens and a diagonal lane fill the space between transit and the civic south gate.
        path(b, 237, 445, 292, 377, 6, Materials.STONE_LIGHT, Materials.STONE);
        broadTree(b, 251, 403, 13, 5);
        broadTree(b, 279, 438, 11, 4);
        broadTree(b, 289, 389, 9, 3);
        planter(b, 249, 426, 12, 5, Materials.CORAL);
        stall(b, 267, 365, Materials.TRIM_RED);
    }

    private static void park(MapBuilder b) {
        // This park is one connected green district; the arterial at x=480 is its shared drive.
        ellipseDisc(b, 475, 390, 126, 72, Materials.GRASS_BRIGHT);
        ellipseRing(b, 475, 391, 113, 59, 0.84, Materials.SAND_LIGHT);
        path(b, 480, 308, 480, 464, 5, Materials.SAND_LIGHT, Materials.STONE_LIGHT);
        path(b, 357, 362, 587, 435, 5, Materials.SAND_LIGHT, Materials.STONE_LIGHT);
        path(b, 376, 441, 559, 337, 5, Materials.SAND_LIGHT, Materials.STONE_LIGHT);
        pond(b, 541, 383, 27, 17);
        // A bridge crosses the pond at ground level, so it has clear approaches on both banks.
        b.slab(512, 380, 570, 386, G - 1, Materials.DECK_WOOD);
        b.fill(518, G, 379, 564, G, 379, Materials.WOOD_DARK);
        b.fill(518, G, 387, 564, G, 387, Materials.WOOD_DARK);
        pavilion(b, 560, 437, 12, Materials.ROOF_TILE);
        pavilion(b, 408, 350, 10, Materials.ROOF_TEAL);
        sportsCourt(b, 366, 393, 61, 36);
        // Uneven groups of tall and low canopies frame the paths without forming another grid.
        int[][] trees = {
                {352, 339, 12, 5}, {365, 327, 9, 3}, {381, 338, 13, 5},
                {433, 334, 10, 4}, {452, 325, 13, 5}, {499, 325, 10, 4},
                {521, 332, 12, 5}, {579, 339, 12, 5}, {591, 363, 9, 4},
                {580, 410, 13, 5}, {596, 429, 11, 4}, {583, 454, 9, 3},
                {529, 454, 12, 5}, {505, 443, 10, 4}, {451, 451, 13, 5},
                {429, 460, 10, 4}, {389, 454, 11, 5}, {354, 427, 10, 4},
                {345, 391, 13, 5}, {438, 376, 9, 4}, {505, 408, 11, 4},
                {564, 355, 8, 3}, {449, 416, 10, 4}, {399, 373, 9, 3}
        };
        for (int[] tree : trees) broadTree(b, tree[0], tree[1], tree[2], tree[3]);
        planter(b, 493, 312, 12, 5, Materials.CANDY_PINK);
        planter(b, 452, 308, 11, 5, Materials.CORAL);
    }

    private static void southernNeighbourhoods(MapBuilder b) {
        // Southern routes provide a viable flank, with distinct garages, allotments and glasshouse.
        path(b, 40, 539, 603, 539, 6, Materials.CONCRETE, Materials.ASPHALT);
        for (int i = 0; i < 3; i++) {
            int x = 43 + i * 30;
            shell(b, x, 504, x + 23, 526, 7, Materials.BRICK_TAN,
                    Materials.BRICK_TAN_ALT, i == 1 ? Materials.TRIM_RED : Materials.TRIM_BLUE);
            portal(b, x + 12, 526, 1, 13);
            portal(b, x + 12, 504, 1, 5);
            b.fill(x + 3, G, 507, x + 8, G + 1, 510, Materials.STEEL);
        }
        apartment(b, 190, 509, 38, 27, 2, Materials.BRICK_WHITE,
                Materials.BRICK_WHITE_ALT, Materials.ROOF_TEAL);
        shop(b, 254, 511, 42, 24, Materials.BRICK_ORANGE,
                Materials.BRICK_ORANGE_ALT, Materials.TRIM_RED, true);
        sportsCourt(b, 349, 511, 91, 38);
        greenhouse(b, 512, 507, 72, 34);
        broadTree(b, 245, 552, 11, 5);
        broadTree(b, 291, 555, 10, 4);
        broadTree(b, 467, 537, 9, 4);
        broadTree(b, 595, 519, 12, 5);
        broadTree(b, 579, 554, 11, 5);
        // A southern capture approach offers cover on both sides, outside its open circle.
        planter(b, 288, 464, 10, 4, Materials.SUNNY);
        planter(b, 342, 494, 10, 4, Materials.CORAL);
    }

    private static void streetFurniture(MapBuilder b) {
        shelter(b, 228, 270, Materials.TRIM_RED);
        shelter(b, 527, 269, Materials.TRIM_BLUE);
        shelter(b, 369, 465, Materials.ROOF_TEAL);
        stall(b, 569, 307, Materials.SUNNY);
        // Bespoke cover is deliberately on shoulders and does not seal any strategic core.
        int[][] cover = {
                {65, 279}, {110, 299}, {206, 279}, {423, 299}, {545, 279},
                {76, 85}, {120, 107}, {269, 85}, {371, 107}, {539, 85},
                {80, 469}, {126, 491}, {220, 469}, {408, 491}, {559, 469}
        };
        for (int i = 0; i < cover.length; i++) {
            int[] p = cover[i];
            if (i % 3 == 0) planter(b, p[0], p[1], 7, 3, Materials.CORAL);
            else if (i % 3 == 1) car(b, p[0], p[1], Materials.TRIM_BLUE, false);
            else b.fill(p[0], G, p[1], p[0] + 6, G + 1, p[1] + 1,
                        Materials.CONCRETE_DARK);
        }
        // Staggered deployment screens protect first spawn while keeping three ways out.
        for (int x : new int[]{32, W - 33}) {
            b.fill(x, G, 269, x, G + 3, 280, Materials.CONCRETE_DARK);
            b.fill(x, G, 297, x, G + 3, 309, Materials.CONCRETE_DARK);
        }
        b.fill(43, G, 285, 43, G + 2, 291, Materials.BRICK_TAN);
        b.fill(W - 44, G, 285, W - 44, G + 2, 291, Materials.BRICK_TAN);
        int[][] lamps = {
                {37, 80}, {142, 177}, {177, 109}, {302, 197}, {340, 110},
                {463, 169}, {500, 109}, {602, 251}, {141, 334}, {177, 390},
                {302, 423}, {340, 463}, {502, 490}, {602, 490}, {238, 550}
        };
        for (int[] p : lamps) {
            b.fill(p[0], G, p[1], p[0], G + 6, p[1], Materials.STEEL);
            b.fill(p[0], G + 7, p[1], p[0] + 2, G + 7, p[1], Materials.STEEL);
            b.lamp(p[0] + 2, G + 6, p[1], Materials.AIR);
        }
    }

    private static void keepRoutesOpen(MapBuilder b) {
        for (int x : AVENUES) {
            b.slab(x - 3, 4, x + 3, D - 5, G - 1, Materials.ASPHALT);
            b.clear(x - 3, G, 4, x + 3, 63, D - 5);
        }
        for (int z : CROSSTREETS) {
            b.slab(4, z - 3, W - 5, z + 3, G - 1, Materials.ASPHALT);
            b.clear(4, G, z - 3, W - 5, 63, z + 3);
        }
        for (int[] objective : OBJECTIVES) {
            int cx = objective[0], cz = objective[1];
            for (int z = cz - 14; z <= cz + 14; z++) {
                for (int x = cx - 14; x <= cx + 14; x++) {
                    if ((x - cx) * (x - cx) + (z - cz) * (z - cz) > 14 * 14) continue;
                    b.set(x, G - 1, z, Materials.STONE_LIGHT);
                    b.clear(x, G, z, x, 63, z);
                }
            }
        }
        b.slab(4, 260, 27, 315, G - 1, Materials.CONCRETE);
        b.clear(4, G, 260, 27, 63, 315);
        b.slab(W - 28, 260, W - 5, 315, G - 1, Materials.CONCRETE);
        b.clear(W - 28, G, 260, W - 5, 63, 315);
        b.slab(5, 282, 26, 284, G - 1, Materials.TEAM_BLUE);
        b.slab(W - 27, 282, W - 6, 284, G - 1, Materials.TEAM_RED);
    }

    private static void boundary(MapBuilder b) {
        // A low stone edge frames the city without imposing the old tiled perimeter walls.
        b.walls(1, G, 1, W - 2, G + 3, D - 2, Materials.BRICK_TAN);
        b.walls(1, G + 4, 1, W - 2, G + 4, D - 2, Materials.STONE_LIGHT);
    }

    private static void shop(MapBuilder b, int x, int z, int width, int depth,
                             int brick, int alt, int trim, boolean pitched) {
        int x1 = x + width - 1, z1 = z + depth - 1;
        shell(b, x, z, x1, z1, 8, brick, alt, trim);
        b.fill(x + 3, G + 1, z, x1 - 3, G + 4, z, Materials.GLASS);
        if (pitched) b.roof(x - 1, z - 1, x1 + 1, z1 + 1, G + 9, trim);
        // Counter and storage stay to one side of a full through-passage.
        b.fill(x + 3, G, z + depth / 2, x + width / 2 - 4, G + 1,
                z + depth / 2 + 1, Materials.WOOD);
        b.fill(x1 - 3, G, z + 4, x1 - 2, G + 3, z1 - 4, Materials.WOOD_DARK);
        portal(b, x + width / 2, z, 1, 7);
        portal(b, x + width / 2, z1, 1, 5);
        b.framedWindow(x, G + 2, z + depth / 2, 0, 5, 3, true);
    }

    private static void arcade(MapBuilder b, int x, int z, int width, int depth) {
        int x1 = x + width - 1, z1 = z + depth - 1;
        shell(b, x, z, x1, z1, 9, Materials.BRICK_TAN,
                Materials.BRICK_TAN_ALT, Materials.PLASTER);
        // Open courtyard and two covered galleries create a useful looping interior route.
        b.clear(x + 11, G + 1, z + 9, x1 - 11, G + 14, z1 - 9);
        b.slab(x + 11, z + 9, x1 - 11, z1 - 9, G - 1, Materials.GRASS_BRIGHT);
        b.walls(x + 10, G, z + 8, x1 - 10, G + 6, z1 - 8, Materials.PLASTER);
        portal(b, x + 10, z + depth / 2, 0, 7);
        portal(b, x1 - 10, z + depth / 2, 0, 7);
        portal(b, x + width / 2, z + 8, 1, 7);
        portal(b, x + width / 2, z1 - 8, 1, 7);
        portal(b, x + width / 2, z, 1, 9);
        portal(b, x + width / 2, z1, 1, 7);
        broadTree(b, x + width / 2 + 7, z + depth / 2, 10, 3);
    }

    /** Two or three inhabited levels and a usable roof; stairs are real MapBuilder flights. */
    private static void apartment(MapBuilder b, int x, int z, int width, int depth, int floors,
                                  int brick, int alt, int trim) {
        int x1 = x + width - 1, z1 = z + depth - 1;
        int roof = G - 1 + floors * 7;
        b.slab(x, z, x1, z1, G - 1, Materials.CONCRETE);
        b.brickWalls(x, G, z, x1, roof - 1, z1, brick, alt);
        for (int level = 0; level < floors; level++) {
            int feet = G + level * 7;
            if (level > 0) b.slab(x, z, x1, z1, feet - 1, Materials.CONCRETE);
            b.trimBand(x, z, x1, z1, feet + 5, feet + 5, trim);
            for (int wx = x + 5; wx <= x1 - 4; wx += 9) {
                b.framedWindow(wx, feet + 2, z, 1, 5, 3, true);
                b.framedWindow(wx, feet + 2, z1, 1, 5, 3, true);
            }
            for (int wz = z + 6; wz <= z1 - 5; wz += 9) {
                b.framedWindow(x, feet + 2, wz, 0, 5, 3, true);
                b.framedWindow(x1, feet + 2, wz, 0, 5, 3, true);
            }
            // Divided flats with full-height doors; stair lobby occupies the north end.
            int divider = z + 15;
            b.fill(x + 1, feet, divider, x1 - 1, feet + 5, divider, Materials.PLASTER);
            b.clear(x + width / 2 - 2, feet, divider, x + width / 2 + 2,
                    feet + 4, divider);
            b.fill(x1 - 7, feet, z1 - 6, x1 - 3, feet + 1, z1 - 4, Materials.WOOD);
            b.fill(x + 3, feet, z1 - 4, x + 7, feet + 1, z1 - 3, Materials.WOOD_DARK);
        }
        b.slab(x, z, x1, z1, roof, Materials.CONCRETE);
        b.walls(x, roof + 1, z, x1, roof + 2, z1, trim);
        // Two alternating stair bays leave broad, connected floor landings at both ends.
        for (int level = 0; level < floors; level++) {
            int feet = G + level * 7;
            if ((level & 1) == 0) b.stairs(x + 3, feet, z + 3, 2, 7, 3, Materials.STEEL);
            else b.stairs(x + 11, feet, z + 9, 3, 7, 3, Materials.STEEL);
        }
        b.fill(x1 - 9, roof + 1, z1 - 9, x1 - 4, roof + 3, z1 - 4,
                Materials.CONCRETE_DARK);
        portal(b, x + width / 2, z, 1, 7);
        portal(b, x + width / 2, z1, 1, 7);
        b.clear(x + width / 2 - 3, G, z - 1, x + width / 2 + 3, G + 4, z + 1);
        b.clear(x + width / 2 - 3, G, z1 - 1, x + width / 2 + 3, G + 4, z1 + 1);
    }

    private static void shell(MapBuilder b, int x, int z, int x1, int z1, int height,
                              int brick, int alt, int trim) {
        b.slab(x, z, x1, z1, G - 1, Materials.TILE_FLOOR);
        b.brickWalls(x, G, z, x1, G + height - 1, z1, brick, alt);
        b.trimBand(x, z, x1, z1, G + height - 2, G + height - 1, trim);
        b.slab(x - 1, z - 1, x1 + 1, z1 + 1, G + height, trim);
    }

    private static void portal(MapBuilder b, int x, int z, int axis, int width) {
        b.framedDoor(x, G, z, axis, width, 6);
        if (axis == 0) b.clear(x - 1, G, z - width / 2, x + 1, G + 4, z + width / 2);
        else b.clear(x - width / 2, G, z - 1, x + width / 2, G + 4, z + 1);
    }

    private static void shelf(MapBuilder b, int x0, int z0, int x1, int z1, int palette) {
        b.fill(x0, G, z0, x1, G + 3, z1, Materials.WOOD_LIGHT);
        for (int y : new int[]{G, G + 2}) {
            b.slab(x0, z0, x1, z1, y, Materials.STEEL);
        }
        int[] goods = {Materials.CORAL, Materials.SUNNY, Materials.MINT, Materials.SKY_BLUE};
        for (int z = z0; z <= z1; z += 2) {
            b.set(x0, G + 1, z, goods[Math.floorMod(palette + z, goods.length)]);
            b.set(x1, G + 3, z, goods[Math.floorMod(palette + z + 1, goods.length)]);
        }
    }

    private static void planter(MapBuilder b, int x, int z, int width, int depth, int color) {
        b.fill(x, G, z, x + width - 1, G, z + depth - 1, Materials.STONE_LIGHT);
        if (width > 2 && depth > 2) {
            b.fill(x + 1, G + 1, z + 1, x + width - 2, G + 1, z + depth - 2,
                    Materials.LEAVES);
            for (int i = 1; i < width - 1; i += 3) b.set(x + i, G + 2, z + 1, color);
        }
    }

    private static void broadTree(MapBuilder b, int x, int z, int height, int radius) {
        if (protectedPosition(x, z, radius)) return;
        b.fill(x, G, z, x, G + height - 1, z, Materials.TRUNK);
        for (int dy = -2; dy <= 2; dy++) {
            int r = radius - Math.abs(dy) / 2;
            for (int dz = -r; dz <= r; dz++) for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dz * dz <= r * r && !(dx == 0 && dz == 0 && dy < 0)) {
                    b.set(x + dx, G + height - 2 + dy, z + dz, Materials.LEAVES);
                }
            }
        }
    }

    private static boolean protectedPosition(int x, int z, int radius) {
        for (int avenue : AVENUES) if (Math.abs(x - avenue) <= radius + 4) return true;
        for (int street : CROSSTREETS) if (Math.abs(z - street) <= radius + 4) return true;
        for (int[] p : OBJECTIVES) {
            int dx = x - p[0], dz = z - p[1];
            if (dx * dx + dz * dz <= (radius + 16) * (radius + 16)) return true;
        }
        return false;
    }

    private static void fountain(MapBuilder b, int x, int z, int radius) {
        for (int dz = -radius; dz <= radius; dz++) for (int dx = -radius; dx <= radius; dx++) {
            int d2 = dx * dx + dz * dz;
            if (d2 > radius * radius) continue;
            b.set(x + dx, G - 1, z + dz, Materials.POOL_EDGE);
            b.set(x + dx, G, z + dz,
                    d2 >= (radius - 2) * (radius - 2) ? Materials.POOL_EDGE : Materials.WATER);
        }
        b.pillar(x, G, z, 5, 1, Materials.MARBLE);
        b.pillar(x, G + 4, z, 1, 3, Materials.POOL_EDGE);
        b.set(x, G + 5, z, Materials.WATER);
    }

    private static void clockTower(MapBuilder b, int x, int z) {
        b.pillar(x, G, z, 2, 5, Materials.MARBLE);
        b.pillar(x, G + 2, z, 19, 3, Materials.BRICK_TAN);
        b.fill(x - 3, G + 15, z - 4, x + 3, G + 20, z + 4, Materials.PLASTER);
        b.slab(x - 5, z - 5, x + 5, z + 5, G + 21, Materials.ROOF_TEAL);
        b.roof(x - 4, z - 4, x + 4, z + 4, G + 22, Materials.ROOF_TEAL);
        b.fill(x - 1, G + 16, z - 5, x + 1, G + 18, z - 5, Materials.GOLD);
        b.set(x, G + 18, z - 5, Materials.CONCRETE_DARK);
        b.set(x + 1, G + 17, z - 5, Materials.CONCRETE_DARK);
    }

    private static void pavilion(MapBuilder b, int cx, int cz, int radius, int roof) {
        for (int dz = -radius; dz <= radius; dz++) for (int dx = -radius; dx <= radius; dx++) {
            if (Math.abs(dx) + Math.abs(dz) > radius * 3 / 2) continue;
            b.set(cx + dx, G - 1, cz + dz, Materials.DECK_WOOD);
            int edge = Math.max(Math.abs(dx), Math.abs(dz));
            int roofY = G + 7 + Math.max(0, (radius - edge) / 3);
            b.set(cx + dx, roofY, cz + dz, roof);
        }
        for (int[] p : new int[][]{{-7, -7}, {7, -7}, {-7, 7}, {7, 7}}) {
            b.fill(cx + p[0], G, cz + p[1], cx + p[0], G + 6, cz + p[1], Materials.WOOD_DARK);
        }
        b.fill(cx - 4, G, cz + radius - 2, cx + 4, G + 1, cz + radius - 1, Materials.WOOD);
    }

    private static void pond(MapBuilder b, int cx, int cz, int rx, int rz) {
        for (int z = cz - rz - 2; z <= cz + rz + 2; z++) {
            for (int x = cx - rx - 2; x <= cx + rx + 2; x++) {
                double d = ellipseDistance(x, z, cx, cz, rx, rz);
                if (d <= 1.15) b.set(x, G - 1, z, Materials.SAND_LIGHT);
                if (d < 1) {
                    // One shallow voxel of water has flat, walkable exits all around the shore.
                    b.set(x, G - 2, z, Materials.STONE);
                    b.set(x, G - 1, z, Materials.WATER);
                }
            }
        }
    }

    private static void sportsCourt(MapBuilder b, int x, int z, int width, int depth) {
        b.slab(x, z, x + width - 1, z + depth - 1, G - 1, Materials.ROOF_TEAL);
        b.slab(x + 2, z + 2, x + width - 3, z + 2, G - 1, Materials.FRAME_LIGHT);
        b.slab(x + 2, z + depth - 3, x + width - 3, z + depth - 3, G - 1, Materials.FRAME_LIGHT);
        b.slab(x + 2, z + 2, x + 2, z + depth - 3, G - 1, Materials.FRAME_LIGHT);
        b.slab(x + width - 3, z + 2, x + width - 3, z + depth - 3, G - 1, Materials.FRAME_LIGHT);
        b.slab(x + width / 2, z + 2, x + width / 2, z + depth - 3,
                G - 1, Materials.FRAME_LIGHT);
        ring(b, x + width / 2, z + depth / 2, 5, 6, Materials.FRAME_LIGHT);
        // Two goals face the long axis and are open through both front and back.
        for (int gx : new int[]{x + 2, x + width - 3}) {
            b.fill(gx, G, z + depth / 2 - 5, gx, G + 5, z + depth / 2 - 5, Materials.FRAME_LIGHT);
            b.fill(gx, G, z + depth / 2 + 5, gx, G + 5, z + depth / 2 + 5, Materials.FRAME_LIGHT);
            b.fill(gx, G + 5, z + depth / 2 - 5, gx, G + 5, z + depth / 2 + 5,
                    Materials.FRAME_LIGHT);
        }
    }

    private static void greenhouse(MapBuilder b, int x, int z, int width, int depth) {
        int x1 = x + width - 1, z1 = z + depth - 1;
        b.slab(x, z, x1, z1, G - 1, Materials.STONE_LIGHT);
        b.walls(x, G, z, x1, G + 5, z1, Materials.GLASS);
        b.walls(x, G, z, x1, G, z1, Materials.BRICK_TAN);
        for (int i = 0; i < depth; i++) {
            int roof = G + 6 + Math.min(i, depth - 1 - i) / 3;
            b.slab(x, z + i, x1, z + i, roof, Materials.GLASS);
        }
        for (int sx = x; sx <= x1; sx += 12) {
            b.fill(sx, G, z, sx, G + 6, z, Materials.FRAME_LIGHT);
            b.fill(sx, G, z1, sx, G + 6, z1, Materials.FRAME_LIGHT);
        }
        for (int sx = x + 6; sx < x1 - 5; sx += 15) {
            planter(b, sx, z + 7, 5, 7, Materials.CORAL);
            planter(b, sx, z + 22, 5, 7, Materials.SUNNY);
        }
        portal(b, x + width / 2, z, 1, 9);
        portal(b, x + width / 2, z1, 1, 9);
        portal(b, x, z + depth / 2, 0, 7);
        portal(b, x1, z + depth / 2, 0, 7);
    }

    private static void stall(MapBuilder b, int x, int z, int color) {
        b.slab(x, z, x + 11, z + 7, G + 5, color);
        b.fill(x, G, z + 6, x + 11, G + 1, z + 7, Materials.WOOD);
        for (int px : new int[]{x, x + 11}) {
            b.fill(px, G, z, px, G + 4, z, Materials.WOOD_DARK);
            b.fill(px, G + 2, z + 7, px, G + 4, z + 7, Materials.WOOD_DARK);
        }
    }

    private static void shelter(MapBuilder b, int x, int z, int color) {
        b.slab(x, z, x + 14, z + 5, G + 5, color);
        b.fill(x, G, z + 5, x + 14, G + 4, z + 5, Materials.GLASS);
        b.fill(x, G, z, x, G + 4, z, Materials.STEEL);
        b.fill(x + 14, G, z, x + 14, G + 4, z, Materials.STEEL);
        b.fill(x + 3, G, z + 4, x + 10, G, z + 4, Materials.WOOD);
    }

    private static void car(MapBuilder b, int x, int z, int color, boolean alongZ) {
        int width = alongZ ? 6 : 11, depth = alongZ ? 13 : 5;
        b.fill(x, G, z, x + width - 1, G + 1, z + depth - 1, color);
        b.fill(x + 1, G + 2, z + 1, x + width - 2, G + 3, z + depth - 2, Materials.GLASS_TINTED);
        b.slab(x + 1, z + 1, x + width - 2, z + depth - 2, G + 4, color);
        b.set(x, G, z + 1, Materials.RUBBER);
        b.set(x + width - 1, G, z + 1, Materials.RUBBER);
        b.set(x, G, z + depth - 2, Materials.RUBBER);
        b.set(x + width - 1, G, z + depth - 2, Materials.RUBBER);
    }

    private static void bus(MapBuilder b, int x, int z, int color) {
        b.fill(x, G, z, x + 27, G + 5, z + 7, color);
        b.fill(x + 2, G + 2, z, x + 25, G + 4, z, Materials.GLASS_TINTED);
        b.fill(x + 2, G + 2, z + 7, x + 25, G + 4, z + 7, Materials.GLASS_TINTED);
        b.slab(x, z, x + 27, z + 7, G + 6, Materials.PLASTER);
        b.fill(x + 1, G + 1, z + 1, x + 26, G + 4, z + 6, Materials.AIR);
        // A stranded bus is a real short flanking passage rather than a solid vehicle box.
        b.clear(x + 3, G + 1, z, x + 7, G + 4, z + 1);
        b.clear(x + 21, G + 1, z + 6, x + 25, G + 4, z + 7);
        for (int wheel : new int[]{x + 5, x + 22}) {
            b.fill(wheel, G, z, wheel + 1, G + 1, z, Materials.RUBBER);
            b.fill(wheel, G, z + 7, wheel + 1, G + 1, z + 7, Materials.RUBBER);
        }
    }

    private static void path(MapBuilder b, int x0, int z0, int x1, int z1, int radius,
                             int shoulder, int surface) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / Math.max(1, steps);
            int z = z0 + (z1 - z0) * i / Math.max(1, steps);
            disc(b, x, z, radius + 2, shoulder);
            disc(b, x, z, radius, surface);
        }
    }

    private static void disc(MapBuilder b, int cx, int cz, int radius, int material) {
        ring(b, cx, cz, 0, radius, material);
    }

    private static void ring(MapBuilder b, int cx, int cz, int inner, int outer, int material) {
        int in2 = inner * inner, out2 = outer * outer;
        for (int z = cz - outer; z <= cz + outer; z++) {
            for (int x = cx - outer; x <= cx + outer; x++) {
                int d2 = (x - cx) * (x - cx) + (z - cz) * (z - cz);
                if (d2 >= in2 && d2 <= out2) b.set(x, G - 1, z, material);
            }
        }
    }

    private static void ellipseDisc(MapBuilder b, int cx, int cz, int rx, int rz, int material) {
        ellipseRing(b, cx, cz, rx, rz, 0, material);
    }

    private static void ellipseRing(MapBuilder b, int cx, int cz, int rx, int rz,
                                    double inner, int material) {
        for (int z = cz - rz; z <= cz + rz; z++) for (int x = cx - rx; x <= cx + rx; x++) {
            double d = ellipseDistance(x, z, cx, cz, rx, rz);
            if (d >= inner * inner && d <= 1) b.set(x, G - 1, z, material);
        }
    }

    private static double ellipseDistance(int x, int z, int cx, int cz, int rx, int rz) {
        double dx = (double) (x - cx) / rx, dz = (double) (z - cz) / rz;
        return dx * dx + dz * dz;
    }
}
