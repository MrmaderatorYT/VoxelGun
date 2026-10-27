package com.ccs.voxelstrike.map.scenery;

import com.ccs.voxelstrike.map.MapBuilder;
import com.ccs.voxelstrike.voxel.Materials;

final class VillageDressing {
    private VillageDressing() {}
    static void build(MapBuilder b) {
        MapScenery s = b.scenery();
        s.landmark("Market", 64, 64, 24);
        s.landmark("Bell Tower", 48, 104, 23);
        s.landmark("Blue House", 86, 23, 22);
        // Recolor only brick, never overwrite the existing doors, glazing or frames.
        for (int x = 78; x <= 95; x++) for (int z = 16; z <= 29; z++) for (int y = 3; y <= 8; y++) {
            int material = b.world().get(x, y, z);
            if (material == Materials.BRICK_WHITE || material == Materials.BRICK_WHITE_ALT)
                b.set(x, y, z, (y + x) % 2 == 0 ? Materials.BRICK_TEAL : Materials.BRICK_TEAL_ALT);
        }
        // A genuinely walkable balcony with an outside approach, not a floating visual platform.
        b.fill(80, 7, 13, 88, 7, 15, Materials.DECK_WOOD);
        b.stairs(76, 3, 13, 0, 5, 3, Materials.WOOD);
        b.clear(84, 8, 16, 86, 10, 17);
        DecorRecipes.fence(s, 80, 8, 13, 8, Materials.FRAME_LIGHT);
        DecorRecipes.awning(s, 81, 6.5f, 14, 8, Materials.TRIM_BLUE);
        DecorRecipes.sign(s, 82, 10.5f, 15.9f, "BLUE HOUSE", .19f, Materials.TRIM_BLUE);
        DecorRecipes.sign(s, 55, 8, 49.9f, "MARKET", .24f, Materials.TRIM_RED);
        DecorRecipes.sign(s, 45, 15, 97.94f, "TOWER", .22f, Materials.WOOD_DARK);
        // A clock and bell silhouette make the church visible above the low roofs.
        s.box(47, 20, 97.85f, 2, 2, .12f, Materials.SUNNY);
        s.box(47.92f, 20.92f, 97.8f, .13f, .8f, .05f, Materials.WOOD_DARK);
        s.box(47.3f, 20.92f, 97.8f, .7f, .13f, .05f, Materials.WOOD_DARK);
        s.box(47.7f, 21, 99, .8f, 1.3f, .8f, Materials.COPPER);
        int[][] beds = {{18,72},{37,78},{80,31},{101,32},{17,36},{98,91}};
        int[] colors = {Materials.CANDY_PINK, Materials.SUNNY, Materials.CORAL};
        for (int i = 0; i < beds.length; i++) {
            int x = beds[i][0], z = beds[i][1];
            b.fill(x, 2, z, x + 3, 2, z + 1, Materials.DIRT);
            DecorRecipes.flowers(s, x, 3, z, colors[i % colors.length]);
            DecorRecipes.fence(s, x - .3f, 3, z - .4f, 4, Materials.FRAME_LIGHT);
        }
        DecorRecipes.awning(s, 18, 6.8f, 72.2f, 8, Materials.TRIM_RED);
        DecorRecipes.awning(s, 38, 6.8f, 78.2f, 7, Materials.SUNNY);
        DecorRecipes.sign(s, 19, 7.6f, 73.85f, "BAKERY", .2f, Materials.TRIM_RED);
        DecorRecipes.sign(s, 38, 7.2f, 79.85f, "CAFE", .23f, Materials.WOOD_DARK);
        for (int x = 50; x < 80; x += 8) {
            DecorRecipes.awning(s, x, 7.25f, 49.3f, 5, x % 16 == 2 ? Materials.TRIM_RED : Materials.TRIM_BLUE);
            DecorRecipes.flowers(s, x + .7f, 5.1f, 51, Materials.CORAL);
        }
    }
}
