package com.ccs.voxelstrike.map.scenery;

import com.ccs.voxelstrike.map.MapBuilder;
import com.ccs.voxelstrike.voxel.Materials;

/** Human-scale identity and wayfinding for the continuous Battlefield city. */
public final class CityBattlefieldDressing {
    private CityBattlefieldDressing() {}

    public static void build(MapBuilder b) {
        MapScenery s = b.scenery();
        s.landmark("Old Town", 87, 171, 88);
        s.landmark("Supermarket", 234, 151, 61);
        s.landmark("Civic Circle", 320, 288, 77);
        s.landmark("Library", 376, 207, 43);
        s.landmark("Fire Station", 553, 216, 51);
        s.landmark("Transit Depot", 88, 400, 76);
        s.landmark("Lakeside Park", 497, 391, 109);
        s.landmark("Glasshouse", 548, 525, 49);

        // Signs are placed on their actual facades, beyond the collision openings below them.
        DecorRecipes.sign(s, 208, 12.2f, 125.8f, "SUPERMARKET", .55f, Materials.TRIM_RED);
        DecorRecipes.sign(s, 358, 12.1f, 189.8f, "LIBRARY", .52f, Materials.TRIM_BLUE);
        DecorRecipes.sign(s, 532, 12.2f, 200.8f, "FIRE STATION", .42f, Materials.TRIM_RED);
        DecorRecipes.sign(s, 65, 12.2f, 347.8f, "TRANSIT", .6f, Materials.TRIM_BLUE);
        DecorRecipes.sign(s, 536, 10, 506.8f, "GLASSHOUSE", .4f, Materials.ROOF_TEAL);
        DecorRecipes.standingSign(s, 490, 3, 309.8f, "PARK", .46f, Materials.ROOF_TEAL);
        DecorRecipes.standingSign(s, 250, 3, 259.8f, "CIVIC CIRCLE", .27f, Materials.TRIM_BLUE);
        DecorRecipes.standingSign(s, 42, 3, 155, "OLD TOWN", .32f, Materials.TRIM_RED);

        shopfront(s, 39, 24, 27, "BAKERY", Materials.TRIM_RED);
        shopfront(s, 96, 27, 37, "CAFE", Materials.TRIM_BLUE);
        shopfront(s, 42, 122, 26, "FLORIST", Materials.ROOF_TEAL);
        shopfront(s, 102, 138, 31, "BOOKS", Materials.TRIM_RED);
        shopfront(s, 39, 188, 28, "MARKET", Materials.TRIM_BLUE);
        shopfront(s, 112, 225, 26, "BIKES", Materials.ROOF_TEAL);
        shopfront(s, 352, 122, 35, "PHARMACY", Materials.ROOF_TEAL);
        shopfront(s, 415, 124, 43, "DELI", Materials.TRIM_BLUE);
        shopfront(s, 254, 511, 42, "GARDEN CAFE", Materials.TRIM_RED);

        // Café furniture reads at body scale, without turning terrace routes into collision traps.
        for (float[] table : new float[][]{{94, 184}, {109, 178}, {119, 195}, {96, 199}}) {
            caféTable(s, table[0], table[1], Materials.TRIM_RED);
        }
        caféTable(s, 271, 547, Materials.ROOF_TEAL);
        caféTable(s, 287, 547, Materials.ROOF_TEAL);
        for (int[] bench : new int[][]{
                {271, 268}, {345, 323}, {294, 339}, {365, 350}, {443, 362},
                {508, 353}, {574, 400}, {519, 437}, {422, 445}, {77, 434}
        }) bench(s, bench[0], bench[1]);

        // Visual produce and shop fittings sit on the actual low counters.
        for (int i = 0; i < 8; i++) {
            s.box(194.2f + (i % 4) * 1.15f, 5.05f, 145.2f + (i / 4) * 1.7f,
                    .9f, .55f, 1.1f, i % 2 == 0 ? Materials.CORAL : Materials.SUNNY);
            s.box(194.2f + (i % 4) * 1.15f, 5.05f, 158.2f + (i / 4) * 1.7f,
                    .9f, .55f, 1.1f, i % 2 == 0 ? Materials.MINT : Materials.GRASS_BRIGHT);
        }
        DecorRecipes.sign(s, 217, 8.4f, 135.8f, "FRESH", .24f, Materials.ROOF_TEAL);
        DecorRecipes.sign(s, 248, 8.4f, 135.8f, "FOOD", .24f, Materials.TRIM_RED);
        DecorRecipes.vent(s, 253, 16.2f, 135.8f);
        DecorRecipes.pipe(s, 277.12f, 8, 133, 32, false, Materials.COPPER);
        DecorRecipes.awning(s, 242, 9.1f, 209.1f, 44, Materials.TRIM_RED);
        DecorRecipes.sign(s, 250, 10.2f, 210.8f, "ARCADE", .35f, Materials.TRIM_RED);

        // Park planting varies by place: terracotta boxes, low flower beds and reed clumps.
        for (int[] flowers : new int[][]{
                {452, 308}, {497, 312}, {543, 426}, {564, 450}, {398, 342},
                {515, 516}, {532, 516}, {549, 531}, {570, 531}
        }) DecorRecipes.flowers(s, flowers[0], 3, flowers[1], Materials.CORAL);
        for (int[] reeds : new int[][]{{521, 369}, {535, 367}, {557, 370}, {559, 397}, {531, 400}}) {
            for (int i = 0; i < 5; i++) {
                s.box(reeds[0] + i * .36f, 2.8f, reeds[1] + (i % 2) * .4f,
                        .08f, 1.2f + (i % 3) * .3f, .08f, Materials.LEAVES);
                s.box(reeds[0] + i * .36f, 3.9f + (i % 3) * .3f, reeds[1] + (i % 2) * .4f,
                        .12f, .32f, .12f, Materials.WOOD_DARK);
            }
        }
        shelterSign(s, 228, 270);
        shelterSign(s, 527, 269);
        shelterSign(s, 369, 465);
        shelterSign(s, 101, 393);
        // Bus bay boards and platform maps make the transit loop identifiable at a glance.
        DecorRecipes.standingSign(s, 43, 3, 390, "BUS", .34f, Materials.TRIM_BLUE);
        DecorRecipes.standingSign(s, 126, 3, 435, "BUS", .34f, Materials.TRIM_BLUE);
        s.box(118, 3, 350.9f, 5, 4, .18f, Materials.PLASTER);
        for (int i = 0; i < 5; i++) {
            s.box(118.4f, 3.5f + i * .55f, 350.8f, 3.8f, .12f, .06f,
                    i % 2 == 0 ? Materials.TRIM_BLUE : Materials.TRIM_RED);
        }
    }

    private static void shopfront(MapScenery s, int x, int z, int width,
                                  String name, int color) {
        float pixel = Math.min(.38f, (width - 5f) / (name.length() * 4 + 1));
        float signWidth = (name.length() * 4 + 1) * pixel;
        DecorRecipes.sign(s, x + (width - signWidth) / 2, 9.1f, z - .16f,
                name, pixel, color);
        DecorRecipes.awning(s, x + 2, 7.8f, z - 1.6f, width - 4, color);
        DecorRecipes.flowers(s, x + 2.2f, 3, z - 2.5f, Materials.CORAL);
        DecorRecipes.flowers(s, x + width - 5.8f, 3, z - 2.5f, Materials.SUNNY);
    }

    private static void bench(MapScenery s, float x, float z) {
        s.box(x, 3.8f, z, 5.4f, .24f, 1.2f, Materials.WOOD);
        s.box(x, 4.15f, z + 1.05f, 5.4f, 1, .16f, Materials.WOOD_DARK);
        s.box(x + .6f, 3, z + .2f, .2f, .8f, .75f, Materials.STEEL);
        s.box(x + 4.6f, 3, z + .2f, .2f, .8f, .75f, Materials.STEEL);
    }

    private static void caféTable(MapScenery s, float x, float z, int color) {
        s.box(x, 4.5f, z, 3.4f, .2f, 3.4f, Materials.WOOD_LIGHT);
        s.box(x + 1.5f, 3, z + 1.5f, .3f, 1.5f, .3f, Materials.STEEL);
        s.box(x - 1.7f, 3.7f, z + 1, 1, .2f, 1.2f, color);
        s.box(x + 4.2f, 3.7f, z + 1, 1, .2f, 1.2f, color);
        s.box(x - 1.8f, 3.7f, z + 1.1f, .16f, 1.1f, 1, color);
        s.box(x + 5.1f, 3.7f, z + 1.1f, .16f, 1.1f, 1, color);
        s.box(x + 1.6f, 4.7f, z + 1.6f, .14f, 3.1f, .14f, Materials.STEEL);
        // Layered square parasol gives each terrace a recognisable silhouette from the street.
        s.box(x - .5f, 7.55f, z - .5f, 4.4f, .12f, 4.4f, color);
        s.box(x + .2f, 7.7f, z + .2f, 3, .15f, 3, color);
        s.box(x + .9f, 7.85f, z + .9f, 1.6f, .15f, 1.6f, color);
    }

    private static void shelterSign(MapScenery s, float x, float z) {
        DecorRecipes.sign(s, x + 4, 8.2f, z - .12f, "BUS", .3f, Materials.TRIM_BLUE);
        s.box(x + 12.5f, 4, z + 4.75f, 1.2f, 2.5f, .1f, Materials.PLASTER);
        for (int i = 0; i < 4; i++) {
            s.box(x + 12.65f, 4.2f + i * .45f, z + 4.65f, .9f, .08f, .1f, Materials.TRIM_BLUE);
        }
    }
}
