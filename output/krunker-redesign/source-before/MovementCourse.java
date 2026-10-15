package com.ccs.voxelgun.map.maps;

import com.ccs.voxelgun.map.*;
import com.ccs.voxelgun.voxel.Materials;

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
        blueSpawn(8, 32, 90);
        redSpawn(8, 32, 90);
        freeSpawn(8, 32, 90);
        for (int x = 12; x < 124; x += 18) b.tree(x, 3, 80, 6);
    }
}
