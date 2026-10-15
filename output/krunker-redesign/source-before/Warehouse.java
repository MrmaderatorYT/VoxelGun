package com.ccs.voxelgun.map.maps;

import com.ccs.voxelgun.map.GameMap;
import com.ccs.voxelgun.map.MapBuilder;
import com.ccs.voxelgun.map.MapEnvironment;
import com.ccs.voxelgun.voxel.Materials;

/**
 * Enclosed logistics hall. Container rows form the lanes, catwalks add a second level and the
 * glazed office block gives the reflection pass something worth tracing.
 */
public final class Warehouse extends GameMap {
    private static final int G = MapBuilder.GROUND;
    private static final int HALL_HEIGHT = 22;

    public Warehouse() {
        super("warehouse", "Warehouse", "Indoor · containers and catwalks");
    }

    @Override
    public MapEnvironment environment() {
        return MapEnvironment.builder()
                .sun(-0.18f, 0.95f, 0.26f)
                .sunColor(0.95f, 0.96f, 1.00f, 2.6f)
                .sunAngle(0.09f)
                .sky(0.34f, 0.38f, 0.45f, 0.52f, 0.55f, 0.60f, 0.75f)
                .fog(0.006f)
                .exposure(1.15f)
                .indoor(true)
                .build();
    }

    @Override
    protected void generate(MapBuilder b) {
        b.ground(Materials.CONCRETE_DARK, Materials.ASPHALT, 0.2f);
        b.tiledFloor(6, 6, 121, 121, G - 1, Materials.TILE_FLOOR, Materials.TILE_GROUT, 8);
        hall(b);
        containerRows(b);
        officeBlock(b);
        catwalks(b);
        props(b);

        blueSpawn(14, 20, 135);
        blueSpawn(18, 24, 135);
        blueSpawn(14, 28, 135);
        blueSpawn(22, 20, 135);
        blueSpawn(20, 32, 135);
        blueSpawn(26, 26, 135);
        blueSpawn(16, 36, 135);

        // Clear of the catwalk stair, which climbs x 112..115 / z 99..108.
        redSpawn(118, 107, 315);
        redSpawn(109, 103, 315);
        redSpawn(118, 99, 315);
        redSpawn(105, 107, 315);
        redSpawn(107, 95, 315);
        redSpawn(99, 110, 315);
        redSpawn(111, 91, 315);

        freeSpawn(52, 16, 180);
        freeSpawn(64, 107, 0);
        freeSpawn(20, 100, 45);
        freeSpawn(107, 22, 225);
        freeSpawn(60, 60, 90);
        freeSpawn(40, 64, 90);
        freeSpawn(88, 64, 270);
        freeSpawn(30, 60, 90);
        freeSpawn(98, 68, 270);
    }

    /** Outer hall with steel columns, roof trusses and skylights. */
    private void hall(MapBuilder b) {
        // Brick clad hall: courses in two greys with a painted band top and bottom and plaster
        // pilasters, so the inside of the shed does not read as one endless concrete surface.
        b.brickWalls(4, G, 4, 123, G + HALL_HEIGHT, 123, Materials.BRICK_WHITE,
                Materials.BRICK_WHITE_ALT);
        b.trimBand(4, 4, 123, 123, G, G + 1, Materials.TRIM_GREY);
        b.trimBand(4, 4, 123, 123, G + HALL_HEIGHT - 1, G + HALL_HEIGHT, Materials.TRIM_BLUE);
        b.quoins(4, 4, 123, 123, G + 2, G + HALL_HEIGHT - 2, Materials.PLASTER);
        for (int i = 20; i < 120; i += 20) {
            b.fill(i, G + 2, 4, i, G + HALL_HEIGHT - 2, 4, Materials.PLASTER);
            b.fill(i, G + 2, 123, i, G + HALL_HEIGHT - 2, 123, Materials.PLASTER);
            b.fill(4, G + 2, i, 4, G + HALL_HEIGHT - 2, i, Materials.PLASTER);
            b.fill(123, G + 2, i, 123, G + HALL_HEIGHT - 2, i, Materials.PLASTER);
        }
        b.fill(4, G + HALL_HEIGHT + 1, 4, 123, G + HALL_HEIGHT + 1, 123, Materials.STEEL);

        // Skylights: the only daylight source, so the traced sun shafts land in defined bands.
        for (int z = 16; z < 118; z += 24) {
            b.clear(20, G + HALL_HEIGHT + 1, z, 108, G + HALL_HEIGHT + 1, z + 5);
            b.fill(20, G + HALL_HEIGHT + 1, z, 108, G + HALL_HEIGHT + 1, z + 5, Materials.GLASS);
        }

        // Structural columns.
        for (int x = 20; x <= 108; x += 22) {
            for (int z = 20; z <= 108; z += 22) {
                b.fill(x, G, z, x + 1, G + HALL_HEIGHT, z + 1, Materials.STEEL);
                b.fill(x - 1, G + HALL_HEIGHT, z - 1, x + 2, G + HALL_HEIGHT, z + 2, Materials.STEEL);
            }
        }

        // Hazard-striped loading doors on the short walls.
        b.fill(4, G, 56, 4, G + 7, 72, Materials.HAZARD);
        b.fill(123, G, 56, 123, G + 7, 72, Materials.HAZARD);

        // Wall-mounted lamps every eight metres.
        for (int z = 14; z < 118; z += 16) {
            b.lamp(6, G + 10, z, Materials.STEEL);
            b.lamp(121, G + 10, z, Materials.STEEL);
        }
    }

    /** Three container rows that define the lanes and the cover rhythm. */
    private void containerRows(MapBuilder b) {
        int[] colors = {Materials.PAINT_BLUE, Materials.PAINT_RED, Materials.RUST, Materials.STEEL};
        for (int row = 0; row < 3; row++) {
            int z = 30 + row * 26;
            for (int i = 0; i < 4; i++) {
                int x = 18 + i * 24;
                int color = colors[(row + i) % colors.length];
                b.container(x, G, z, true, 14, color);
                if ((row + i) % 2 == 0) {
                    b.container(x + 2, G + 5, z, true, 10, colors[(row + i + 1) % colors.length]);
                }
            }
        }

        // Cross-lane containers, stacked to break the sight lines.
        b.container(56, G, 12, false, 14, Materials.RUST);
        b.container(56, G, 100, false, 14, Materials.RUST);
        b.container(88, G, 70, false, 12, Materials.PAINT_BLUE);
        b.container(34, G, 70, false, 12, Materials.PAINT_RED);
    }

    /** Two-storey glazed office; the glass and the polished floor drive the reflection pass. */
    private void officeBlock(MapBuilder b) {
        int x0 = 74;
        int z0 = 20;
        int x1 = 106;
        int z1 = 46;

        b.fill(x0, G - 1, z0, x1, G - 1, z1, Materials.TILE_DARK);
        b.walls(x0, G, z0, x1, G + 6, z1, Materials.CONCRETE);
        b.fill(x0, G + 7, z0, x1, G + 7, z1, Materials.CONCRETE);
        b.walls(x0, G + 8, z0, x1, G + 13, z1, Materials.CONCRETE);
        b.fill(x0, G + 14, z0, x1, G + 14, z1, Materials.CONCRETE);

        // Glazing on the two hall-facing walls, both floors.
        b.window(x0, G + 2, z0 + 2, x0, G + 5, z1 - 2, true);
        b.window(x0 + 2, G + 2, z1, x1 - 2, G + 5, z1, true);
        b.window(x0, G + 10, z0 + 2, x0, G + 12, z1 - 2, true);
        b.window(x0 + 2, G + 10, z1, x1 - 2, G + 12, z1, true);

        b.doorway(x0, G, z0 + 8, 0, 3, 4);
        b.doorway(x1, G, z1 - 8, 0, 3, 4);

        // Interior: partition, desks with screens, stair to the upper floor.
        b.fill(x0 + 12, G, z0 + 1, x0 + 12, G + 5, z1 - 6, Materials.PLASTIC_WHITE);
        b.doorway(x0 + 12, G, z0 + 14, 1, 3, 4);
        for (int i = 0; i < 4; i++) {
            int dx = x0 + 4 + i * 6;
            b.fill(dx, G, z0 + 4, dx + 2, G + 1, z0 + 6, Materials.PLASTIC_WHITE);
            b.set(dx + 1, G + 2, z0 + 5, Materials.SCREEN);
        }
        b.fill(x0 + 2, G + 8, z0 + 2, x1 - 2, G + 8, z1 - 2, Materials.TILE_DARK);
        b.landing(x1 - 5, G + 8, z0 + 11, x1 - 2, z0 + 14, Materials.TILE_DARK);
        b.stairs(x1 - 5, G, z0 + 2, 2, 9, 4, Materials.STEEL);
        for (int i = 0; i < 3; i++) {
            b.lamp(x0 + 8 + i * 10, G + 13, z0 + 12, Materials.CONCRETE);
            b.lamp(x0 + 8 + i * 10, G + 5, z0 + 12, Materials.CONCRETE);
        }

        // Roof access from the upper floor, used as the map's sniper perch.
        b.landing(x0 + 10, G + 14, z0 + 18, x0 + 14, z0 + 21, Materials.CONCRETE);
        b.stairs(x0 + 4, G + 9, z0 + 18, 0, 6, 4, Materials.STEEL);
        b.fill(x0, G + 15, z0, x1, G + 16, z0, Materials.STEEL);
        b.fill(x0, G + 15, z1, x1, G + 16, z1, Materials.STEEL);
        b.fill(x0, G + 15, z0, x0, G + 16, z1, Materials.STEEL);
        b.fill(x1, G + 15, z0, x1, G + 16, z1, Materials.STEEL);
    }

    /** Grated walkways connecting the container tops and the office roof. */
    private void catwalks(MapBuilder b) {
        int y = G + 10;
        b.catwalk(14, y, 60, true, 96, 4);
        b.catwalk(60, y, 14, false, 96, 4);
        b.landing(58, y, 58, 65, 65, Materials.GRATE);
        b.landing(14, y, 60, 17, 63, Materials.GRATE);
        b.landing(106, y, 60, 109, 63, Materials.GRATE);
        b.stairs(14, G, 49, 2, 11, 4, Materials.STEEL);
        b.stairs(106, G, 74, 3, 11, 4, Materials.STEEL);

        for (int x = 20; x < 108; x += 22) {
            b.fill(x, y + 4, 59, x, y + 5, 64, Materials.STEEL);
        }
        b.catwalk(74, y, 46, false, 14, 4);
        b.landing(74, G + 14, 43, 77, 46, Materials.CONCRETE);
        b.stairs(74, y + 1, 50, 3, 4, 4, Materials.STEEL);
    }

    /** Forklifts, pallets, barrels and shelving to fill the floor plan. */
    private void props(MapBuilder b) {
        for (int i = 0; i < 6; i++) {
            int x = 14 + i * 18;
            b.fill(x, G, 118, x + 5, G + 1, 120, Materials.WOOD);
            b.fill(x + 1, G + 2, 118, x + 4, G + 3, 120, Materials.CRATE);
        }
        b.oilDrum(48, G, 24);
        b.oilDrum(52, G, 24);
        b.oilDrum(50, G, 28);
        b.oilDrum(96, G, 96);
        b.oilDrum(100, G, 100);
        b.panelCrate(70, G, 34, 6);
        b.panelCrate(58, G, 92, 6);

        // Forklift silhouettes.
        forklift(b, 40, 96, true);
        forklift(b, 84, 34, false);

        // Shelving racks along the west wall.
        for (int z = 16; z < 112; z += 14) {
            b.fill(8, G, z, 12, G, z + 6, Materials.STEEL);
            b.fill(8, G + 4, z, 12, G + 4, z + 6, Materials.STEEL);
            b.fill(8, G + 8, z, 12, G + 8, z + 6, Materials.STEEL);
            b.crate(9, G + 5, z + 1, 3, Materials.CRATE, Materials.WOOD_DARK);
        }
    }

    private void forklift(MapBuilder b, int x, int z, boolean alongX) {
        int length = 5;
        if (alongX) {
            b.fill(x, G, z, x + length, G + 2, z + 2, Materials.PLASTIC_ORANGE);
            b.fill(x + length, G, z, x + length + 2, G, z + 2, Materials.STEEL);
            b.fill(x + 1, G + 3, z, x + 3, G + 4, z + 2, Materials.RUBBER);
        } else {
            b.fill(x, G, z, x + 2, G + 2, z + length, Materials.PLASTIC_ORANGE);
            b.fill(x, G, z + length, x + 2, G, z + length + 2, Materials.STEEL);
            b.fill(x, G + 3, z + 1, x + 2, G + 4, z + 3, Materials.RUBBER);
        }
    }
}
