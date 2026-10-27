package com.ccs.voxelstrike.map.scenery;

import com.ccs.voxelstrike.map.MapBuilder;
import com.ccs.voxelstrike.voxel.Materials;

final class IndustrialDressing {
    private IndustrialDressing() {}
    static void build(MapBuilder b, String id) {
        MapScenery s = b.scenery();
        if (id.equals("warehouse")) {
            s.landmark("Container Hall", 48, 45, 26);
            s.landmark("Control Office", 90, 32, 22);
            s.landmark("Catwalk", 64, 65, 20);
            DecorRecipes.sign(s, 76, 10, 19.85f, "CONTROL", .25f, Materials.TRIM_BLUE);
            DecorRecipes.sign(s, 43, 8, 29.85f, "CARGO", .3f, Materials.TRIM_RED);
            DecorRecipes.sign(s, 56, 18, 58.85f, "CATWALK", .22f, Materials.TRIM_GREY);
            for (int x : new int[]{56,62}) s.box(x, 19, 59, .06f, 7, .06f, Materials.RUBBER);
            for (int x : new int[]{43,49}) s.box(x, 8, 30, .06f, 18, .06f, Materials.RUBBER);
            for (int z = 16; z < 112; z += 18) {
                DecorRecipes.pipe(s, 5.15f, 12, z, 14, false, Materials.COPPER);
                DecorRecipes.vent(s, z, 19, 3.85f);
            }
            DecorRecipes.pipe(s, 16, 21, 8, 96, true, Materials.STEEL);
            DecorRecipes.pipe(s, 16, 20.4f, 8.5f, 96, true, Materials.RUBBER);
            for (int row = 0; row < 3; row++) for (int i = 0; i < 4; i++) {
                int x = 18 + i * 24, z = 30 + row * 26;
                warning(s, x + 2, 3, z - .6f, 10);
                DecorRecipes.sign(s, x + 2, 6.6f, z - .15f, "CARGO", .12f, Materials.PANEL_GREY);
            }
        } else if (id.equals("facility")) {
            s.landmark("Laboratory", 30, 28, 24);
            s.landmark("Server Hall", 64, 64, 24);
            s.landmark("Reactor", 64, 98, 22);
            DecorRecipes.sign(s, 18, 8, 45.85f, "LAB", .3f, Materials.TRIM_BLUE);
            DecorRecipes.sign(s, 54, 10, 77.85f, "SERVER", .25f, Materials.CONCRETE_DARK);
            DecorRecipes.sign(s, 58, 10, 88.85f, "REACTOR", .25f, Materials.TRIM_RED);
            for (int x : new int[]{18, 88}) {
                DecorRecipes.pipe(s, x, 13.7f, 18, 24, true, Materials.COPPER);
                DecorRecipes.pipe(s, x, 13.2f, 18.7f, 24, true, Materials.RUBBER);
                DecorRecipes.vent(s, x + 4, 11.5f, 13.8f);
            }
            DecorRecipes.pipe(s, 52, 13.7f, 52, 24, true, Materials.COPPER);
            DecorRecipes.vent(s, 58, 11.5f, 49.8f);
            warning(s, 55, 3, 89, 18);
            for (int i = 0; i < 5; i++) {
                s.box(52 + i * 5, 8.2f, 55, 2, .15f, 16, Materials.RUBBER);
                DecorRecipes.vent(s, 52 + i * 5, 7, 53.8f);
            }
        } else {
            // Existing geometry remains the cover/navigation authority. Small accents are attached
            // only to exposed wall faces actually found in this map, not at guessed heights.
            String center = switch (id) {
                case "reactor_core" -> "Reactor"; case "harbor_yard" -> "Crane";
                case "train_depot" -> "Rail Junction"; case "orbital_dock" -> "Docking Tower";
                case "volcano_forge" -> "Foundry"; default -> "Blast Doors";
            };
            // Redesigned layouts own their callouts; generic dressing only supplies a fallback.
            if (s.landmarks().isEmpty()) {
                s.landmark(center, 64, 64, 25);
                s.landmark("Service Bay", 37, 35, 22);
                s.landmark("Loading Yard", 91, 93, 22);
            }
            for (int z = 20; z < 112; z += 14) {
                for (int x = 18; x < 110; x += 14) {
                    for (int y = 6; y < 12; y++) {
                        if (b.world().solid(x, y, z) && !b.world().solid(x, y, z - 1)) {
                            DecorRecipes.vent(s, x, y, z - .2f);
                            DecorRecipes.pipe(s, x + .4f, y + 1.5f, z - .24f, 3, true, Materials.COPPER);
                            break;
                        }
                    }
                }
            }
            for (int x : new int[]{34, 82}) warning(s, x, 3, 46, 10);
            // A real supported wayfinding panel at the clear central approach, not floating text.
            DecorRecipes.standingSign(s, 56, 3, 44,
                    center.toUpperCase(java.util.Locale.ROOT), .2f, Materials.CONCRETE_DARK);
        }
    }
    private static void warning(MapScenery s, float x, float y, float z, int width) {
        for (int i = 0; i < width * 2; i++) s.box(x + i * .5f, y + .012f, z,
                .45f, .015f, .5f, i % 2 == 0 ? Materials.HAZARD : Materials.RUBBER);
    }
}
