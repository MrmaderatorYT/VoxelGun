package com.ccs.voxelstrike.map.scenery;

import com.ccs.voxelstrike.map.MapBuilder;
import com.ccs.voxelstrike.voxel.Materials;

final class UrbanDressing {
    private UrbanDressing() {}
    static void build(MapBuilder b, boolean battlefield) {
        MapScenery s = b.scenery();
        if (battlefield) {
            s.landmark("Freight Station", 315, 175, 66);
            s.landmark("Garage Court", 316, 346, 42);
            s.landmark("Ruins", 380, 350, 40);
            DecorRecipes.standingSign(s, 274, 3, 160.8f, "STATION", .35f, Materials.TRIM_BLUE);
            DecorRecipes.sign(s, 359, 8, 329.9f, "RUINS", .3f, Materials.BRICK_BURGUNDY);
            // Open-front garage bays on an existing flat district, away from spawns/objectives.
            for (int i = 0; i < 3; i++) garage(b, 294 + i * 15, 340, i);
            b.fill(294, 2, 332, 337, 2, 339, Materials.ASPHALT);
            for (int x = 296; x < 337; x += 14) b.fill(x, 2, 333, x, 2, 337, Materials.SUNNY);
            busStop(b, 272, 268);
            busStop(b, 368, 300);
            DecorRecipes.sign(s, 296, 8, 339.8f, "GARAGES", .3f, Materials.TRIM_RED);
            // Ruined facade: actual jagged openings, with non-colliding plaster flakes/rebar.
            for (int y = 4; y <= 8; y++) b.clear(389 - (y % 3), y, 330, 397, y, 331);
            for (int i = 0; i < 6; i++) s.box(388 + i * 1.4f, 8.2f, 329.9f, .07f, 1.8f, .07f, Materials.STEEL);
            DecorRecipes.flowers(s, 286, 3, 350, Materials.CORAL);
            DecorRecipes.fence(s, 290, 3, 354, 10, Materials.TRIM_GREY);
            for (int x : new int[]{177, 333, 493}) streetSign(s, x, 3, 306);
        } else {
            s.landmark("Water Tower", 40, 42, 22);
            s.landmark("Bus Stop", 25, 65, 20);
            s.landmark("Garage Court", 88, 108, 20);
            // Roof equipment stays off the central objective and the existing stair access.
            b.fill(37, 12, 40, 44, 19, 47, Materials.STEEL);
            b.fill(36, 20, 39, 45, 20, 48, Materials.PAINT_BLUE);
            DecorRecipes.pipe(s, 44.9f, 13, 40, 7, false, Materials.COPPER);
            garage(b, 80, 104, 0);
            garage(b, 95, 104, 1);
            shelterDecor(s, 24, 3, 58);
            DecorRecipes.sign(s, 38, 15, 39.85f, "TOWER", .25f, Materials.TRIM_BLUE);
            DecorRecipes.sign(s, 81, 8, 103.85f, "GARAGES", .25f, Materials.TRIM_RED);
            DecorRecipes.flowers(s, 52, 3, 92, Materials.CANDY_PINK);
            DecorRecipes.fence(s, 52, 3, 94, 7, Materials.TRIM_GREY);
            streetSign(s, 48, 3, 45);
            DecorRecipes.vent(s, 32, 7, 33.85f);
            DecorRecipes.vent(s, 80, 7, 33.85f);
            // A broken facade and exposed reinforcing bars distinguish the south-west block.
            b.clear(34, 7, 76, 38, 9, 76);
            for (int i = 0; i < 4; i++) s.box(34 + i, 9, 75.9f, .06f, 1.5f, .06f, Materials.STEEL);
        }
    }
    private static void garage(MapBuilder b, int x, int z, int color) {
        int material = color == 1 ? Materials.BRICK_ORANGE : Materials.BRICK_WHITE;
        b.fill(x, 2, z, x + 11, 2, z + 12, Materials.CONCRETE);
        b.walls(x, 3, z, x + 11, 8, z + 12, material);
        b.fill(x, 9, z, x + 11, 9, z + 12, Materials.PANEL_GREY);
        b.clear(x + 2, 3, z, x + 9, 7, z + 1);
        DecorRecipes.vent(b.scenery(), x + 2, 7.2f, z - .12f);
        DecorRecipes.pipe(b.scenery(), x + 11.1f, 5, z + 2, 8, false, Materials.COPPER);
    }
    private static void busStop(MapBuilder b, int x, int z) {
        // Physical roof and rear wall provide useful cover; slender posts/bench do not snag.
        b.fill(x, 7, z, x + 11, 7, z + 4, Materials.PAINT_BLUE);
        b.fill(x, 3, z + 4, x + 11, 6, z + 4, Materials.PANEL_GREY);
        shelterDecor(b.scenery(), x, 3, z);
    }
    private static void shelterDecor(MapScenery s, float x, float y, float z) {
        s.box(x, y + 4, z, 11, .22f, 4.5f, Materials.TRIM_BLUE);
        s.box(x + .15f, y, z + .3f, .15f, 4, .15f, Materials.STEEL);
        s.box(x + 10.7f, y, z + .3f, .15f, 4, .15f, Materials.STEEL);
        s.box(x + 2, y + .7f, z + 3.2f, 6, .2f, .8f, Materials.WOOD);
        DecorRecipes.sign(s, x + 1, y + 4.4f, z - .04f, "BUS", .25f, Materials.TRIM_BLUE);
        s.box(x + 9, y + .7f, z + 3.7f, 1.5f, 2.5f, .1f, Materials.PLASTER);
        for (int i = 0; i < 4; i++) s.box(x + 9.2f, y + 1 + i * .45f, z + 3.6f, 1, .08f, .1f, Materials.TRIM_BLUE);
    }
    private static void streetSign(MapScenery s, float x, float y, float z) {
        s.box(x, y, z, .12f, 3.5f, .12f, Materials.STEEL);
        DecorRecipes.sign(s, x - .9f, y + 2.7f, z - .12f, "STOP", .16f, Materials.TRIM_RED);
    }
}
