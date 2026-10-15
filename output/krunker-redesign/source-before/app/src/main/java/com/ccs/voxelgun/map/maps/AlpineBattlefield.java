package com.ccs.voxelgun.map.maps;

import com.ccs.voxelgun.map.MapBuilder;
import com.ccs.voxelgun.voxel.Materials;

/** A single mountain resort valley, graded before buildings, paths or trees are placed. */
final class AlpineBattlefield {
    private static final int[] ROUTE_X = {160, 320, 480};
    private static final int[] ROUTE_Z = {96, 288, 480};
    private static final int[] OBJECTIVE_X = {160, 320, 320, 320, 480};
    private static final int[] OBJECTIVE_Z = {288, 96, 288, 480, 288};
    // Rotated, branching spines give the valley long faces, broken crests and saddles.
    // The lower western slopes are forested foothills; the eastern and northern massifs dominate.
    private static final double[][] RIDGES = {
            {44, 10, 118, 52, 26, 35}, {72, 25, 94, 72, 22, 25},
            {183, 10, 281, 52, 29, 49}, {221, 48, 276, 17, 24, 41},
            {350, 14, 450, 64, 27, 45}, {389, 54, 443, 19, 23, 35},
            {532, 116, 591, 244, 32, 48}, {523, 208, 584, 158, 25, 40},
            {49, 124, 111, 252, 24, 28}, {68, 198, 126, 229, 19, 22},
            {349, 125, 454, 181, 26, 30}, {404, 156, 439, 125, 20, 25},
            {48, 332, 114, 458, 29, 24}, {74, 428, 127, 387, 22, 20},
            {197, 334, 281, 444, 33, 51}, {202, 421, 268, 365, 25, 40},
            {525, 326, 592, 445, 32, 50}, {519, 393, 580, 428, 24, 41},
            {352, 515, 453, 552, 25, 38}, {397, 543, 438, 511, 20, 28},
            {191, 524, 283, 552, 23, 22}, {525, 515, 594, 557, 25, 35}
    };
    private static final int[][] BUILDING_PADS = {
            {197, 157, 252, 198}, {262, 159, 297, 188}, {198, 215, 230, 243},
            {263, 214, 304, 247}, {231, 222, 257, 258}, {347, 323, 413, 367},
            {107, 257, 142, 279}, {508, 258, 543, 282}, {270, 504, 296, 527},
            {387, 398, 424, 424}
    };
    private static final int[][] VILLAGE_WALK = {
            {160, 288}, {182, 267}, {213, 254}, {246, 218}, {270, 200}, {320, 198}
    };
    private static final int[][] SHORE_WALK = {
            {320, 400}, {355, 389}, {389, 396}, {415, 392}, {451, 386}, {490, 400},
            {506, 426}, {490, 455}, {451, 466}, {414, 452}, {391, 426}, {389, 396}
    };
    private static final int[][] SKI_TRAIL = {
            {326, 96}, {348, 118}, {373, 137}, {431, 157}, {451, 135},
            {398, 111}, {371, 87}, {422, 66}, {451, 42}
    };
    private static final int[][] WEST_TRAIL = {
            {32, 288}, {62, 267}, {97, 234}, {117, 203}, {93, 178},
            {62, 158}, {82, 130}, {128, 112}, {160, 96}
    };

    private AlpineBattlefield() {}

    static void build(MapBuilder b) {
        int width = b.world().sizeX(), depth = b.world().sizeZ();
        int[] heights = terrain(width, depth);
        grade(heights, width, depth);
        for (int z = 0; z < depth; z++) for (int x = 0; x < width; x++) {
            int top = heights[x + width * z];
            int surface = surface(x, z, top, width);
            b.fill(x, 0, z, x, top - 1, z, Materials.STONE);
            if (surface == Materials.GRASS || surface == Materials.GRASS_BRIGHT
                    || surface == Materials.DIRT) {
                b.fill(x, Math.max(1, top - 2), z, x, top - 1, z, Materials.DIRT);
            }
            b.set(x, top, z, surface);
        }
        village(b);
        station(b);
        bridge(b, heights, width);
        lake(b, heights, width);
        outposts(b);
        plazasAndCover(b);
        forest(b, heights, width, depth);
        b.boundary(3, 10, Materials.STONE);
    }

    private static int[] terrain(int width, int depth) {
        int[] h = new int[width * depth];
        for (int z = 0; z < depth; z++) for (int x = 0; x < width; x++) {
            double mountain = 0;
            for (int ridge = 0; ridge < RIDGES.length; ridge++) {
                double[] r = RIDGES[ridge];
                double dx = r[2] - r[0], dz = r[3] - r[1];
                double t = Math.max(0, Math.min(1,
                        ((x - r[0]) * dx + (z - r[1]) * dz) / (dx * dx + dz * dz)));
                double px = r[0] + t * dx, pz = r[1] + t * dz;
                double cross = Math.sqrt(sq(x - px) + sq(z - pz));
                double widthAlongCrest = r[4] * (1 + .17 * Math.sin(t * 9 + ridge));
                double face = Math.max(0, 1 - cross / (widthAlongCrest * 1.75));
                double crest = .84 + .12 * Math.cos(t * 8.3 + ridge * .71)
                        + .09 * Math.sin(t * 17.1 + ridge * 1.31);
                mountain = Math.max(mountain, r[5] * Math.pow(face, 1.2) * crest);
            }
            double folds = 2.7 * Math.sin(x * .061) * Math.sin(z * .047)
                    + 1.9 * Math.sin(x * .117 + z * .083);
            int top = Math.max(2, Math.min(49, (int) Math.round(2 + mountain + folds)));
            // Route floors are authored here; the distance transform blends their banks into
            // each individual summit instead of imposing repeated square ridge profiles.
            if (flatSite(x, z, width)) top = 2;
            // Ice sits in a shallow, organic basin; the ring remains walkable at every bank.
            double water = lakeRadius(x, z);
            if (water < 1.12) top = 2;
            // A narrow stream cuts the gorge. Grading also makes its floor a walk-out route.
            double gorge = sq((x - 403) / 25.0) + sq((z - 218) / 59.0);
            if (gorge < 1) top = Math.min(top, 2 + (int) Math.round(gorge * 6));
            if (z >= 212 && z <= 230) {
                if (x >= 348 && x <= 460) top = Math.min(top, 7);
            }
            h[x + width * z] = top;
        }
        return h;
    }

    /** Min-plus distance transform guarantees <= one voxel of rise at every ground edge. */
    private static void grade(int[] h, int width, int depth) {
        for (int z = 0; z < depth; z++) for (int x = 0; x < width; x++) {
            int i = x + width * z;
            if (x > 0) h[i] = Math.min(h[i], h[i - 1] + 1);
            if (z > 0) h[i] = Math.min(h[i], h[i - width] + 1);
        }
        for (int z = depth - 1; z >= 0; z--) for (int x = width - 1; x >= 0; x--) {
            int i = x + width * z;
            if (x + 1 < width) h[i] = Math.min(h[i], h[i + 1] + 1);
            if (z + 1 < depth) h[i] = Math.min(h[i], h[i + width] + 1);
        }
    }

    private static boolean flatSite(int x, int z, int width) {
        if (x <= 27 || x >= width - 28) return true;
        if ((x >= 33 && x <= 47 || x >= width - 48 && x <= width - 34)
                && (z >= 258 && z <= 282 || z >= 294 && z <= 318)) return true;
        for (int rx : ROUTE_X) if (Math.abs(x - rx) <= 3) return true;
        for (int rz : ROUTE_Z) if (Math.abs(z - rz) <= 3) return true;
        for (int i = 0; i < OBJECTIVE_X.length; i++) {
            if (sq(x - OBJECTIVE_X[i]) + sq(z - OBJECTIVE_Z[i]) <= 21 * 21) return true;
        }
        // The old rectangular settlements become a small resort around a shared village green.
        if (sq((x - 250) / 57.0) + sq((z - 207) / 52.0) < 1) return true;
        for (int[] site : BUILDING_PADS) {
            if (x >= site[0] && x <= site[2] && z >= site[1] && z <= site[3]) return true;
        }
        if (Math.abs(x - 379) <= 4 && z >= 288 && z <= 329) return true;
        if (x >= 320 && x <= 351 && Math.abs(z - 345) <= 4) return true;
        // A narrow lane links the village front doors to the main valley route.
        return pathDistance(x, z, VILLAGE_WALK) <= 4;
    }

    private static int surface(int x, int z, int top, int width) {
        double snowline = 28 + 3 * Math.sin(x * .031) * Math.sin(z * .043)
                + 2 * Math.sin(x * .017 + z * .023);
        int material = top >= snowline ? Materials.SNOW : top >= 19 ? Materials.STONE_LIGHT
                : top >= 7 ? Materials.GRASS : Materials.GRASS_BRIGHT;
        if (top >= 20 && Math.sin(x * .037) * Math.sin(z * .029) > .48) material = Materials.STONE_LIGHT;
        if (top < 19 && Math.sin(x * .063) * Math.sin(z * .049)
                + .5 * Math.sin(x * .091 + z * .072) > 1.2) material = Materials.DIRT;
        double shore = lakeRadius(x, z);
        if (shore < 1) return Materials.ICE;
        if (shore < 1.21) return Materials.STONE_LIGHT;
        if (pathDistance(x, z, SHORE_WALK) <= 3) material = Materials.DIRT;
        if (pathDistance(x, z, WEST_TRAIL) <= 3) material = Materials.DIRT;
        if (pathDistance(x, z, SKI_TRAIL) <= 5) material = Materials.SNOW;
        if (pathDistance(x, z, VILLAGE_WALK) <= 4) material = Materials.CONCRETE;
        if (sq((x - 250) / 23.0) + sq((z - 207) / 15.0) < 1) material = Materials.CONCRETE;
        // Only the main valley avenue and the station approach are paved. Their shoulders
        // meander while the central navigation corridor stays open for vehicles and infantry.
        int avenue = 5 + (int) Math.round(2 * Math.sin(x * .027));
        int northRoad = 5 + (int) Math.round(2 * Math.sin(z * .035));
        if (Math.abs(z - 288) <= avenue && top == 2
                || Math.abs(x - 320) <= northRoad && top == 2
                || Math.abs(x - 379) <= 4 && z >= 288 && z <= 329
                || x >= 320 && x <= 351 && Math.abs(z - 345) <= 4) material = Materials.ASPHALT;
        if (Math.abs(z - 288) <= 1 && x % 23 < 9
                || Math.abs(x - 320) <= 1 && z % 23 < 9) material = Materials.CONCRETE;
        // A circular arrival plaza gives the route crossing a recognisable form.
        double circle = sq(x - 320) + sq(z - 288);
        if (circle < 21 * 21) material = circle > 18 * 18 ? Materials.STONE_LIGHT : Materials.CONCRETE;
        if (x <= 27 || x >= width - 28) material = Materials.GRASS;
        return material;
    }

    private static void village(MapBuilder b) {
        hotel(b, 201, 161);
        chalet(b, 266, 163, 27, 21, Materials.WOOD_DARK, Materials.TRIM_BLUE);
        shop(b, 202, 219, 25, 21, Materials.BRICK_TAN, Materials.TRIM_RED);
        shop(b, 267, 218, 34, 26, Materials.PLASTER, Materials.TRIM_BLUE);
        chapel(b);
        // A small square with separate seating, a well and planted corners; its paths remain open.
        b.pillar(250, 3, 205, 2, 3, Materials.BRICK_TAN);
        b.clear(248, 4, 203, 252, 4, 207);
        b.fill(248, 4, 203, 252, 4, 207, Materials.WATER);
        for (int x : new int[]{235, 260}) {
            b.fill(x, 3, 201, x + 5, 3, 202, Materials.DECK_WOOD);
            b.fill(x, 4, 202, x + 5, 4, 202, Materials.WOOD_DARK);
        }
        planter(b, 231, 211, 6, 3);
        planter(b, 260, 211, 5, 3);
    }

    private static void hotel(MapBuilder b, int x, int z) {
        int x1 = x + 47, z1 = z + 33;
        b.fill(x, 2, z, x1, 2, z1, Materials.CONCRETE);
        b.walls(x, 3, z, x1, 9, z1, Materials.BRICK_TAN);
        b.fill(x, 10, z, x1, 10, z1, Materials.WOOD_DARK);
        b.walls(x, 11, z, x1, 17, z1, Materials.PLASTER);
        b.doorway(x + 24, 3, z, 1, 5, 5);
        b.doorway(x + 24, 3, z1, 1, 7, 5);
        b.doorway(x1, 3, z + 17, 0, 5, 5);
        for (int wx = x + 5; wx < x1 - 3; wx += 9) {
            b.window(wx, 5, z, wx + 3, 7, z, true);
            b.window(wx, 13, z, wx + 3, 15, z, true);
            b.window(wx, 5, z1, wx + 3, 7, z1, true);
            b.window(wx, 13, z1, wx + 3, 15, z1, true);
        }
        for (int wz : new int[]{z + 6, z + 24}) {
            b.window(x, 5, wz, x, 7, wz + 4, true);
            b.window(x1, 13, wz, x1, 15, wz + 4, true);
        }
        b.fill(x - 1, 10, z1 + 1, x1 + 1, 10, z1 + 4, Materials.DECK_WOOD);
        for (int px : new int[]{x + 3, x1 - 3})
            b.fill(px, 3, z1 + 3, px, 9, z1 + 3, Materials.WOOD_DARK);
        b.fill(x - 1, 17, z - 1, x1 + 1, 17, z1 + 1, Materials.WOOD_DARK);
        gable(b, x - 2, z - 2, x1 + 2, z1 + 2, 18, Materials.ROOF, true);
        b.fill(x + 7, 22, z + 7, x + 9, 30, z + 9, Materials.BRICK_BURGUNDY);
        // The stairs terminate inside the lobby, with a genuine second-floor balcony exit.
        b.stairs(x + 4, 3, z + 7, 2, 8, 4, Materials.WOOD);
        b.doorway(x + 24, 11, z1, 1, 5, 5);
        b.fill(x + 21, 3, z + 19, x + 33, 4, z + 20, Materials.WOOD_DARK);
        b.fill(x + 35, 3, z + 24, x + 41, 3, z + 28, Materials.DECK_WOOD);
    }

    private static void chalet(MapBuilder b, int x, int z, int width, int depth, int wall, int trim) {
        int x1 = x + width - 1, z1 = z + depth - 1;
        b.fill(x, 2, z, x1, 2, z1, Materials.CONCRETE);
        b.walls(x, 3, z, x1, 10, z1, wall);
        b.fill(x, 9, z, x1, 9, z, trim);
        b.fill(x, 9, z1, x1, 9, z1, trim);
        b.doorway(x + width / 2, 3, z, 1, 5, 5);
        b.doorway(x + width / 2, 3, z1, 1, 5, 5);
        for (int offset : new int[]{4, width - 8}) {
            b.window(x + offset, 5, z, x + offset + 3, 7, z, true);
            b.window(x + offset, 5, z1, x + offset + 3, 7, z1, true);
        }
        b.window(x, 5, z + 7, x, 7, z + 11, true);
        b.window(x1, 5, z + 7, x1, 7, z + 11, true);
        gable(b, x - 2, z - 2, x1 + 2, z1 + 2, 11, Materials.ROOF_TILE, false);
        b.fill(x + 4, 13, z + 5, x + 6, 20, z + 7, Materials.BRICK_BURGUNDY);
        b.fill(x + 3, 3, z + 5, x + 6, 3, z + 10, Materials.DECK_WOOD);
    }

    private static void shop(MapBuilder b, int x, int z, int width, int depth, int wall, int trim) {
        int x1 = x + width - 1, z1 = z + depth - 1;
        b.fill(x, 2, z, x1, 2, z1, Materials.CONCRETE);
        b.walls(x, 3, z, x1, 10, z1, wall);
        b.fill(x, 10, z, x1, 10, z, trim);
        b.fill(x, 10, z1, x1, 10, z1, trim);
        b.window(x + 3, 4, z, x + width / 2 - 4, 8, z, true);
        b.window(x + width / 2 + 4, 4, z, x1 - 3, 8, z, true);
        b.doorway(x + width / 2, 3, z, 1, 5, 6);
        b.doorway(x + width / 2, 3, z1, 1, 5, 5);
        b.window(x, 5, z + 6, x, 7, z + depth - 6, true);
        b.window(x1, 5, z + 6, x1, 7, z + depth - 6, true);
        gable(b, x - 1, z - 1, x1 + 1, z1 + 1, 11, Materials.ROOF_TEAL, false);
        for (int sx = x + 4; sx < x1 - 3; sx += 7) {
            b.fill(sx, 3, z1 - 5, sx + 3, 4, z1 - 3, Materials.WOOD_DARK);
        }
        b.fill(x + 3, 3, z + depth / 2, x + 8, 4, z + depth / 2 + 1, Materials.WOOD);
    }

    private static void chapel(MapBuilder b) {
        b.fill(235, 2, 226, 253, 2, 254, Materials.CONCRETE);
        b.walls(235, 3, 226, 253, 13, 254, Materials.PLASTER);
        b.doorway(244, 3, 226, 1, 5, 7);
        b.doorway(244, 3, 254, 1, 5, 5);
        for (int z = 232; z <= 246; z += 7) {
            b.window(235, 6, z, 235, 10, z + 2, true);
            b.window(253, 6, z, 253, 10, z + 2, true);
        }
        gable(b, 234, 225, 254, 255, 14, Materials.ROOF, false);
        b.walls(236, 14, 227, 243, 25, 234, Materials.BRICK_TAN);
        b.window(237, 21, 227, 242, 23, 227, false);
        b.fill(235, 26, 226, 244, 26, 235, Materials.ROOF);
        for (int layer = 1; layer <= 5; layer++)
            b.fill(235 + layer, 26 + layer, 226 + layer, 244 - layer, 26 + layer, 235 - layer,
                    Materials.ROOF);
        b.fill(239, 31, 230, 239, 34, 230, Materials.WOOD_DARK);
        b.fill(238, 33, 230, 240, 33, 230, Materials.WOOD_DARK);
        for (int z : new int[]{233, 240, 247}) {
            b.fill(238, 3, z, 242, 3, z + 1, Materials.WOOD_DARK);
            b.fill(246, 3, z, 250, 3, z + 1, Materials.WOOD_DARK);
        }
    }

    /** Roof planes run along a ridge; their timber gables stay visible below snow. */
    private static void gable(MapBuilder b, int x0, int z0, int x1, int z1, int y, int roof, boolean snow) {
        for (int x = x0; x <= x1; x++) {
            int rise = Math.min(x - x0, x1 - x) / 2;
            b.fill(x, y + rise, z0, x, y + rise, z1, roof);
            if (snow && x > x0 + 2 && x < x1 - 2)
                b.fill(x, y + rise + 1, z0 + 1, x, y + rise + 1, z1 - 1, Materials.SNOW);
            if (rise > 0) {
                b.fill(x, y, z0 + 1, x, y + rise - 1, z0 + 1, Materials.WOOD_DARK);
                b.fill(x, y, z1 - 1, x, y + rise - 1, z1 - 1, Materials.WOOD_DARK);
            }
        }
    }

    private static void station(MapBuilder b) {
        b.fill(351, 2, 328, 409, 2, 362, Materials.CONCRETE);
        b.walls(351, 3, 328, 409, 6, 362, Materials.BRICK_TAN);
        b.walls(351, 7, 328, 409, 16, 362, Materials.PLASTER);
        b.fill(351, 16, 328, 409, 16, 328, Materials.TRIM_RED);
        b.fill(351, 16, 362, 409, 16, 362, Materials.TRIM_RED);
        b.window(357, 7, 328, 369, 13, 328, true);
        b.window(388, 7, 328, 403, 13, 328, true);
        b.window(351, 7, 334, 351, 13, 356, true);
        b.window(409, 7, 334, 409, 13, 356, true);
        b.doorway(379, 3, 328, 1, 9, 8);
        b.doorway(379, 3, 362, 1, 9, 8);
        b.doorway(351, 3, 345, 0, 7, 7);
        b.doorway(409, 3, 345, 0, 7, 9);
        gable(b, 349, 326, 411, 364, 17, Materials.ROOF_BLUE, false);
        // Ticket office, waiting benches and open boarding lanes support indoor flanking.
        b.walls(355, 3, 347, 369, 9, 358, Materials.WOOD_LIGHT);
        b.window(357, 5, 347, 366, 7, 347, false);
        b.doorway(369, 3, 352, 0, 3, 5);
        for (int z : new int[]{336, 354}) {
            b.fill(388, 3, z, 400, 3, z + 1, Materials.DECK_WOOD);
            b.fill(388, 4, z + 1, 400, 4, z + 1, Materials.WOOD_DARK);
        }
        b.fill(398, 12, 340, 416, 12, 350, Materials.STEEL);
        b.clear(399, 13, 341, 415, 17, 349);
        // A distinct glazed control tower rises above the concourse roofline.
        b.walls(399, 17, 351, 409, 29, 361, Materials.PANEL_GREY);
        b.window(400, 24, 351, 408, 27, 351, true);
        b.window(409, 24, 352, 409, 27, 360, true);
        b.fill(398, 30, 350, 410, 30, 362, Materials.SNOW);
    }

    private static void bridge(MapBuilder b, int[] h, int width) {
        // The stream is shallow; the broad footbridge offers a second, elevated crossing.
        for (int z = 163; z <= 271; z++) {
            int x = 402 + (int) Math.round(3 * Math.sin(z * .062));
            for (int dx = -2; dx <= 2; dx++) {
                int y = h[x + dx + width * z];
                if (y <= 7) b.set(x + dx, y + 1, z, Materials.WATER);
            }
        }
        b.fill(365, 8, 218, 443, 8, 224, Materials.DECK_WOOD);
        for (int x : new int[]{370, 438}) for (int z : new int[]{217, 225}) {
            int top = h[x + width * z];
            if (top < 18) b.fill(x, top + 1, z, x + 1, 18, z, Materials.WOOD_DARK);
        }
        // Explicit one-step landings meet the already graded bank without a floating lip.
        for (int i = 0; i <= 6; i++) {
            int xLeft = 364 - i, xRight = 444 + i, y = 8 - i;
            for (int z = 218; z <= 224; z++) {
                int left = h[xLeft + width * z], right = h[xRight + width * z];
                if (left < y) b.fill(xLeft, left + 1, z, xLeft, y, z, Materials.DECK_WOOD);
                if (right < y) b.fill(xRight, right + 1, z, xRight, y, z, Materials.DECK_WOOD);
            }
        }
    }

    private static void lake(MapBuilder b, int[] h, int width) {
        // Open meltwater patches are only one block deep, with continuous solid ground beneath.
        for (int z = 406; z <= 446; z++) for (int x = 427; x <= 481; x++) {
            double r = sq((x - 454) / 25.0) + sq((z - 425) / 15.0);
            if (r < .77 && Math.sin(x * .17 + z * .23) > -.3) b.set(x, 3, z, Materials.WATER);
        }
        b.fill(390, 3, 405, 416, 3, 417, Materials.DECK_WOOD);
        b.fill(414, 3, 409, 438, 3, 413, Materials.DECK_WOOD);
        for (int x : new int[]{395, 410, 427, 436}) {
            b.fill(x, 3, 409, x, 5, 409, Materials.WOOD_DARK);
            b.fill(x, 3, 413, x, 5, 413, Materials.WOOD_DARK);
        }
        // An open-sided lakeside shelter, offset from the boardwalk route.
        for (int x : new int[]{392, 403}) for (int z : new int[]{399, 404})
            b.fill(x, 3, z, x, 9, z, Materials.WOOD_DARK);
        gable(b, 390, 397, 405, 405, 10, Materials.ROOF_TEAL, false);
        b.fill(394, 3, 400, 401, 3, 401, Materials.DECK_WOOD);
    }

    private static void outposts(MapBuilder b) {
        chalet(b, 111, 261, 28, 15, Materials.WOOD_LIGHT, Materials.TRIM_BLUE);
        shop(b, 512, 262, 28, 17, Materials.WOOD_DARK, Materials.TRIM_RED);
        chalet(b, 274, 508, 19, 16, Materials.PLASTER, Materials.TRIM_BLUE);
    }

    private static void plazasAndCover(MapBuilder b) {
        for (int i = 0; i < OBJECTIVE_X.length; i++) {
            int x = OBJECTIVE_X[i], z = OBJECTIVE_Z[i];
            // Four separate low pieces leave every centre route and objective unobstructed.
            planter(b, x - 16, z - 13, 7, 3);
            planter(b, x + 10, z + 10, 7, 3);
            b.fill(x + 13, 3, z - 14, x + 13, 10, z - 14, Materials.WOOD_DARK);
            b.banner(x + 12, 10, z - 14, 6, Materials.CANVAS, Materials.WOOD_DARK);
        }
        // Offset avalanche screens protect deployment lanes while leaving the avenue open.
        for (int x : new int[]{36, 603}) {
            b.fill(x, 3, 261, x + 1, 5, 271, Materials.WOOD_DARK);
            b.fill(x, 3, 276, x + 1, 5, 282, Materials.WOOD_DARK);
        }
        for (int x : new int[]{43, 596}) {
            b.fill(x, 3, 295, x + 1, 5, 306, Materials.WOOD_DARK);
            b.fill(x, 3, 311, x + 1, 5, 317, Materials.WOOD_DARK);
        }
        // The building edges and relief provide most cover. These few pieces mark path bends.
        b.sandbags(178, 3, 267, true, 8, 2);
        // Stay outside the avenue shoulder: local route waypoints can land at z284.
        b.sandbags(490, 3, 271, false, 8, 2);
        b.crate(356, b.world().standableLevel(356, 317, 3, 2), 317, 3, Materials.CRATE, Materials.WOOD_DARK);
        b.crate(364, b.world().standableLevel(364, 317, 3, 2), 317, 4, Materials.CRATE, Materials.WOOD_DARK);
    }

    private static void planter(MapBuilder b, int x, int z, int width, int depth) {
        b.fill(x, 3, z, x + width - 1, 3, z + depth - 1, Materials.WOOD_DARK);
        b.fill(x + 1, 4, z + 1, x + width - 2, 4, z + depth - 2, Materials.LEAVES);
    }

    private static void forest(MapBuilder b, int[] h, int width, int depth) {
        // Elliptic groves and random spacing create clearings rather than a tree lattice.
        int[][] groves = {{67, 350, 48, 67}, {129, 430, 48, 36}, {216, 341, 41, 31},
                {269, 438, 34, 28}, {548, 116, 49, 43}, {565, 332, 41, 33},
                {552, 453, 47, 29}, {367, 524, 29, 31}, {106, 61, 38, 26},
                {354, 177, 23, 39}, {524, 523, 31, 27}};
        for (int i = 0; i < 3800; i++) {
            int x = 34 + b.random().nextInt(width - 68), z = 8 + b.random().nextInt(depth - 16);
            int top = h[x + width * z];
            if (top < 5 || top > 29 || reservedTree(x, z, width)) continue;
            boolean grove = false;
            for (int[] g : groves) {
                if (sq((x - g[0]) / (double) g[2]) + sq((z - g[1]) / (double) g[3]) < 1) {
                    grove = true; break;
                }
            }
            if (!grove && i % 13 != 0) continue;
            int height = 8 + b.random().nextInt(6);
            boolean clear = true;
            for (int dz = -3; dz <= 3 && clear; dz++) for (int dx = -3; dx <= 3 && clear; dx++) {
                if (reservedTree(x + dx, z + dz, width)) { clear = false; break; }
                for (int y = top + 4; y <= top + height + 1; y++) {
                    if (b.world().solid(x + dx, y, z + dz)) { clear = false; break; }
                }
            }
            if (!clear) continue;
            b.fill(x, top + 1, z, x, top + height, z, Materials.TRUNK);
            for (int level = 4; level <= height; level++) {
                int radius = Math.min(3, (height - level + 2) / 3);
                for (int dz = -radius; dz <= radius; dz++) for (int dx = -radius; dx <= radius; dx++) {
                    if (dx * dx + dz * dz <= radius * radius + 1) {
                        int material = level == height && top > 19 ? Materials.SNOW : Materials.LEAVES;
                        b.set(x + dx, top + level, z + dz, material);
                    }
                }
            }
        }
    }

    private static boolean reservedTree(int x, int z, int width) {
        if (flatSite(x, z, width) || lakeRadius(x, z) < 1.5) return true;
        if (x >= 348 && x <= 460 && z >= 211 && z <= 231) return true;
        if (pathDistance(x, z, SHORE_WALK) <= 6 || pathDistance(x, z, WEST_TRAIL) <= 6
                || pathDistance(x, z, SKI_TRAIL) <= 8) return true;
        for (int rx : ROUTE_X) if (Math.abs(x - rx) <= 8) return true;
        for (int rz : ROUTE_Z) if (Math.abs(z - rz) <= 9) return true;
        return false;
    }

    private static double lakeRadius(int x, int z) {
        double dx = (x - 451) / 43.0, dz = (z - 425) / 29.0;
        return dx * dx + dz * dz + .07 * Math.sin(x * .15 + z * .09);
    }

    private static double pathDistance(int x, int z, int[][] points) {
        double closest = Double.MAX_VALUE;
        for (int i = 1; i < points.length; i++) {
            double dx = points[i][0] - points[i - 1][0], dz = points[i][1] - points[i - 1][1];
            double t = ((x - points[i - 1][0]) * dx + (z - points[i - 1][1]) * dz) / (dx * dx + dz * dz);
            t = Math.max(0, Math.min(1, t));
            double px = points[i - 1][0] + dx * t, pz = points[i - 1][1] + dz * t;
            closest = Math.min(closest, sq(x - px) + sq(z - pz));
        }
        return Math.sqrt(closest);
    }

    private static double sq(double value) { return value * value; }
}
