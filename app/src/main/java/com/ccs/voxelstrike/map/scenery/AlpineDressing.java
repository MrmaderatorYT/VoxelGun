package com.ccs.voxelstrike.map.scenery;

import com.ccs.voxelstrike.map.MapBuilder;
import com.ccs.voxelstrike.voxel.Materials;

/** Fine resort details follow the authored valley landmarks, rather than old sector copies. */
final class AlpineDressing {
    private AlpineDressing() {}

    static void build(MapBuilder b) {
        MapScenery s = b.scenery();
        s.landmark("Village Square", 250, 207, 57);
        s.landmark("Alpen Hotel", 225, 177, 35);
        s.landmark("Ski Shop", 284, 230, 23);
        s.landmark("Gorge Bridge", 403, 221, 44);
        s.landmark("Mountain Station", 379, 345, 46);
        s.landmark("Frozen Lake", 451, 425, 48);
        s.landmark("Ski Switchbacks", 417, 108, 55);
        s.landmark("Pine Camp", 283, 515, 32);
        village(s);
        station(b, s);
        bridge(s);
        lake(b, s);
        trailMarkers(b, s);
    }

    private static void village(MapScenery s) {
        DecorRecipes.sign(s, 211, 15, 160.85f, "ALPEN HOTEL", .32f, Materials.WOOD_DARK);
        DecorRecipes.awning(s, 219, 8.2f, 159.5f, 13, Materials.TRIM_RED);
        DecorRecipes.sign(s, 207, 9, 218.85f, "BAKERY", .32f, Materials.TRIM_RED);
        DecorRecipes.awning(s, 204, 8.3f, 217.4f, 21, Materials.TRIM_RED);
        DecorRecipes.sign(s, 273, 9, 217.85f, "SKI SHOP", .32f, Materials.TRIM_BLUE);
        DecorRecipes.awning(s, 269, 8.3f, 216.4f, 29, Materials.TRIM_BLUE);
        DecorRecipes.sign(s, 273, 9.5f, 162.85f, "CHALET", .25f, Materials.TRIM_BLUE);
        // Timber balcony rails, shutters, flowers and chimney pots distinguish the hotel.
        DecorRecipes.fence(s, 200, 11.05f, 198, 49, Materials.WOOD_DARK);
        for (int x = 206; x <= 242; x += 9) {
            for (int y : new int[]{5, 13}) {
                s.box(x - .9f, y - .2f, 160.8f, .7f, 3.5f, .15f, Materials.TRIM_RED);
                s.box(x + 4.2f, y - .2f, 160.8f, .7f, 3.5f, .15f, Materials.TRIM_RED);
            }
            DecorRecipes.flowers(s, x, 12.5f, 160.35f, Materials.CORAL);
        }
        s.box(207.7f, 30, 167.7f, 3.6f, .35f, 3.6f, Materials.STEEL);
        DecorRecipes.flowers(s, 266, 4.6f, 162.4f, Materials.CORAL);
        DecorRecipes.flowers(s, 286, 4.6f, 162.4f, Materials.SUNNY);
        // The chapel clock has a visible face above the village roofs.
        s.box(237, 17, 226.8f, 5, 3.5f, .12f, Materials.FRAME_LIGHT);
        s.box(239.4f, 18.6f, 226.65f, .18f, 1.5f, .14f, Materials.WOOD_DARK);
        s.box(239.4f, 18.6f, 226.65f, 1.3f, .18f, .14f, Materials.WOOD_DARK);
        // Wooden ski racks remain visual so the shop entrance and pavement stay clear.
        for (int i = 0; i < 6; i++) {
            int color = i % 2 == 0 ? Materials.TRIM_BLUE : Materials.TRIM_RED;
            s.box(269 + i * .9f, 3.15f, 215, .18f, 4, .16f, color);
            s.box(269.3f + i * .9f, 3.15f, 215, .18f, 4, .16f, color);
        }
        s.box(268.5f, 4.1f, 214.8f, 6.5f, .15f, .4f, Materials.WOOD_DARK);
        for (int x : new int[]{232, 264}) {
            s.box(x, 3, 194, .18f, 6.5f, .18f, Materials.WOOD_DARK);
            s.box(x - .7f, 9.2f, 193.6f, 1.6f, .8f, 1, Materials.LANTERN);
        }
    }

    private static void station(MapBuilder b, MapScenery s) {
        DecorRecipes.sign(s, 360, 14, 327.85f, "MOUNTAIN STATION", .4f, Materials.TRIM_RED);
        DecorRecipes.awning(s, 372, 10.5f, 325.4f, 15, Materials.TRIM_RED);
        DecorRecipes.sign(s, 357, 8, 346.85f, "TICKETS", .26f, Materials.WOOD_DARK);
        s.box(404, 31, 356, .25f, 7, .25f, Materials.STEEL);
        s.box(400, 35.5f, 356, 8, .18f, .18f, Materials.STEEL);
        for (int i = 0; i < 5; i++)
            s.box(401 + i * 1.4f, 35, 355, .12f, 1.2f, 2, Materials.STEEL);
        // Two suspended cable runs climb toward the eastern summit, crossing above routes.
        for (int i = 0; i < 156; i++) {
            float t = i / 155f, x = 413 + i, z = 345 + 43 * t;
            float y = 22 + 36 * t - 2.5f * (float) Math.sin(t * Math.PI);
            s.box(x, y, z, 1.1f, .12f, .12f, Materials.STEEL);
            s.box(x, y, z + 3.2f, 1.1f, .12f, .12f, Materials.STEEL);
        }
        for (int i : new int[]{39, 88, 134, 155}) {
            float t = i / 155f, x = 413 + i, z = 345 + 43 * t;
            float y = 22 + 36 * t - 2.5f * (float) Math.sin(t * Math.PI);
            int ground = b.world().standableLevel((int) x, (int) z, 3, 2);
            if (ground < 0) ground = 3;
            s.box(x - .3f, ground, z + 1.3f, .6f, Math.max(.5f, y - ground), .6f, Materials.STEEL);
            s.box(x - 2, y - .1f, z - 1, 4, .35f, 5.2f, Materials.STEEL);
        }
        for (int i : new int[]{26, 67, 111}) {
            float t = i / 155f, x = 413 + i, z = 345 + 43 * t;
            float y = 22 + 36 * t - 2.5f * (float) Math.sin(t * Math.PI);
            s.box(x, y - 2, z, .15f, 2, .15f, Materials.STEEL);
            s.box(x - 2, y - 5, z - 1.2f, 4, 1, 2.5f, Materials.TRIM_RED);
            s.box(x - 2, y - 4, z - 1.2f, 4, 2, 2.5f, Materials.VISOR);
            s.box(x - 2.1f, y - 2.1f, z - 1.3f, 4.2f, .2f, 2.7f, Materials.ROOF_BLUE);
        }
        DecorRecipes.standingSign(s, 366, 3, 313, "GONDOLA", .3f, Materials.TRIM_BLUE);
    }

    private static void bridge(MapScenery s) {
        DecorRecipes.sign(s, 365, 10, 217.7f, "GORGE BRIDGE", .3f, Materials.WOOD_DARK);
        for (float side : new float[]{217.8f, 224.95f}) {
            DecorRecipes.fence(s, 365, 9.05f, side, 78, Materials.WOOD_DARK);
            for (int x = 370; x <= 438; x++) {
                float y = 18 - 5 * (float) Math.sin((x - 370) / 68f * Math.PI);
                s.box(x, y, side, 1.06f, .13f, .13f, Materials.STEEL);
                if (x % 3 == 0) s.box(x + .4f, 10.2f, side, .08f, y - 10.1f, .08f, Materials.STEEL);
            }
        }
    }

    private static void lake(MapBuilder b, MapScenery s) {
        DecorRecipes.standingSign(s, 390, 4, 419.8f, "LAKE WALK", .3f, Materials.TRIM_BLUE);
        DecorRecipes.sign(s, 392, 8, 396.85f, "LAKESIDE", .28f, Materials.WOOD_DARK);
        DecorRecipes.fence(s, 391, 4.05f, 417.1f, 24, Materials.WOOD_DARK);
        // Life ring and mooring ropes give the pier a purpose without narrowing its walkway.
        s.box(409, 5, 417.2f, 1.6f, 1.6f, .18f, Materials.CORAL);
        s.box(409.4f, 5.4f, 417.1f, .8f, .8f, .3f, Materials.FRAME_LIGHT);
        for (int x = 396; x <= 411; x += 5)
            s.box(x, 4.05f, 405, .12f, 1.7f, .12f, Materials.WOOD_DARK);
        for (int z = 393; z <= 454; z += 20) {
            int x = z < 425 ? 497 : 494;
            int ground = b.world().standableLevel(x, z, 3, 2);
            if (ground >= 0) DecorRecipes.standingSign(s, x, ground, z, "LAKE", .22f, Materials.TRIM_BLUE);
        }
    }

    private static void trailMarkers(MapBuilder b, MapScenery s) {
        int[][] markers = {{342, 119}, {377, 142}, {435, 160}, {454, 132}, {397, 108},
                {368, 84}, {425, 62}, {453, 39}, {95, 231}, {113, 200}, {65, 157}};
        for (int i = 0; i < markers.length; i++) {
            int x = markers[i][0], z = markers[i][1];
            int ground = b.world().standableLevel(x, z, 3, 2);
            if (ground < 0) continue;
            s.box(x, ground, z, .16f, 3, .16f, Materials.WOOD_DARK);
            s.box(x - .6f, ground + 2.3f, z, 1.4f, .5f, .15f,
                    i < 8 ? Materials.TRIM_BLUE : Materials.TRIM_RED);
        }
        DecorRecipes.sign(s, 277, 10, 507.85f, "PINE CAMP", .24f, Materials.WOOD_DARK);
        DecorRecipes.sign(s, 516, 9, 261.85f, "TRAIL CAFE", .25f, Materials.TRIM_RED);
        DecorRecipes.awning(s, 514, 8.4f, 260.4f, 24, Materials.TRIM_RED);
    }
}
