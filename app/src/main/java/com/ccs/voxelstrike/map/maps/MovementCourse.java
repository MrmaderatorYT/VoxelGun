package com.ccs.voxelstrike.map.maps;

import com.ccs.voxelstrike.map.*;
import com.ccs.voxelstrike.map.scenery.DecorRecipes;
import com.ccs.voxelstrike.voxel.Materials;

/** Dedicated procedural training course; does not change existing catalog/spawn indices. */
public final class MovementCourse extends GameMap {
    public MovementCourse() { super("movement_course_v1", "Movement Course", "Sprint · jump · slide · finish"); }
    @Override public MapEnvironment environment() {
        return MapEnvironment.builder().sun(-0.4f, 0.85f, 0.3f)
                .sunColor(1f, 0.95f, 0.85f, 3.5f)
                .sky(0.3f, 0.55f, 0.85f, 0.88f, 0.95f, 1f, 1.1f)
                .fog(0.003f).exposure(1f).build();
    }
    @Override protected void generate(MapBuilder b) {
        b.fill(0, 0, 0, 127, 1, 127, Materials.DIRT);
        b.fill(0, 2, 0, 127, 2, 127, Materials.GRASS_BRIGHT);
        b.boundary(3, 8, Materials.BRICK_WHITE);
        for (int x = 6; x <= 120; x++) {
            int center = 32;
            for (int gate = 1; gate < CourseLayout.count(); gate++) {
                if (x >= CourseLayout.x(gate - 1) && x <= CourseLayout.x(gate)) {
                    float t = (x - CourseLayout.x(gate - 1))
                            / (float) (CourseLayout.x(gate) - CourseLayout.x(gate - 1));
                    center = Math.round(CourseLayout.z(gate - 1)
                            + (CourseLayout.z(gate) - CourseLayout.z(gate - 1)) * t);
                    break;
                }
            }
            b.fill(x, 2, center - 7, x, 2, center + 7, Materials.ASPHALT);
            b.set(x, 2, center - 7, Materials.KERB);
            b.set(x, 2, center + 7, Materials.KERB);
            if (x % 6 < 3) b.set(x, 2, center, Materials.SUNNY);
        }
        for (int i = 0; i < CourseLayout.count(); i++) {
            int x = CourseLayout.x(i), z = CourseLayout.z(i);
            int color = i == 0 ? Materials.MINT : i == CourseLayout.count() - 1
                    ? Materials.SUNNY : i == CourseLayout.SLIDE_GATE ? Materials.CORAL : Materials.SKY_BLUE;
            b.fill(x, 2, z - 6, x, 2, z + 6, color);
            b.fill(x, 3, z - 7, x, 8, z - 7, color);
            b.fill(x, 3, z + 7, x, 8, z + 7, color);
            b.fill(x, 9, z - 7, x, 9, z + 7, color);
        }
        // Jump rail before the airborne checkpoint; ample run-up/landing room.
        b.fill(44, 3, 40, 45, 3, 54, Materials.WOOD_LIGHT);
        // A low visual canopy and an explicitly slide-gated checkpoint (voxel gap = 2 metres).
        b.fill(82, 5, 26, 86, 5, 38, Materials.PANEL_GREY);
        b.fill(8, 2, 27, 11, 2, 37, Materials.MINT);
        trainingPark(b);
        blueSpawn(8, 32, 90);
        redSpawn(8, 32, 90);
        freeSpawn(8, 32, 90);
        for (int x = 12; x < 124; x += 18) b.tree(x, 3, 80, 6);
    }

    /** Frame the sprint lane with a small sports campus; the timed route stays unchanged. */
    private void trainingPark(MapBuilder b) {
        b.building(12, 7, 23, 12, 8, Materials.BRICK_TEAL,
                Materials.BRICK_TEAL_ALT, Materials.FRAME_LIGHT);
        b.building(91, 7, 24, 12, 8, Materials.BRICK_ORANGE,
                Materials.BRICK_ORANGE_ALT, Materials.FRAME_LIGHT);
        DecorRecipes.sign(b.scenery(), 17, 9, 6.85f, "TRAINING", .3f, Materials.TRIM_BLUE);
        DecorRecipes.sign(b.scenery(), 96, 9, 6.85f, "FINISH", .3f, Materials.TRIM_RED);
        b.tiledFloor(9, 64, 118, 74, 2, Materials.TILE_FLOOR, Materials.TILE_GROUT, 6);
        // Open spectator shelters, paired planting beds and a furnished rest promenade.
        for (int x : new int[]{14, 48, 82}) {
            b.fill(x, 3, 66, x, 8, 66, Materials.WOOD_DARK);
            b.fill(x + 20, 3, 66, x + 20, 8, 66, Materials.WOOD_DARK);
            b.fill(x, 3, 74, x, 8, 74, Materials.WOOD_DARK);
            b.fill(x + 20, 3, 74, x + 20, 8, 74, Materials.WOOD_DARK);
            b.fill(x - 1, 9, 65, x + 21, 9, 75, Materials.PLASTER);
            b.fill(x - 1, 10, 65, x + 21, 10, 65, Materials.TRIM_BLUE);
            b.fill(x + 3, 3, 72, x + 8, 3, 73, Materials.WOOD_LIGHT);
            b.fill(x + 12, 3, 72, x + 17, 3, 73, Materials.WOOD_LIGHT);
            b.fill(x + 3, 4, 73, x + 8, 4, 73, Materials.WOOD_DARK);
            b.fill(x + 12, 4, 73, x + 17, 4, 73, Materials.WOOD_DARK);
            DecorRecipes.flowers(b.scenery(), x + 2, 3, 61, Materials.CORAL);
            b.fill(x, 3, 95, x + 22, 3, 97, Materials.CONCRETE);
            b.fill(x, 3, 98, x + 22, 4, 100, Materials.STONE_LIGHT);
            b.fill(x, 3, 101, x + 22, 5, 103, Materials.CONCRETE);
            b.fill(x, 3, 104, x + 22, 6, 106, Materials.STONE_LIGHT);
            for (int seat = 1; seat < 22; seat += 3) {
                b.set(x + seat, 5, 99, Materials.TRIM_BLUE);
                b.set(x + seat, 7, 105, Materials.CORAL);
            }
        }
        for (int x : new int[]{8, 42, 76, 110}) {
            b.fill(x, 3, 112, x + 9, 3, 119, Materials.PLASTER);
            b.fill(x + 1, 4, 113, x + 8, 5, 118, Materials.LEAVES);
            b.tree(x + 4, 3, 116, 7);
        }
        b.scenery().landmark("Start Pavilion", 20, 30, 22);
        b.scenery().landmark("Jump Yard", 51, 48, 18);
        b.scenery().landmark("Finish Pavilion", 104, 32, 20);
    }
}
