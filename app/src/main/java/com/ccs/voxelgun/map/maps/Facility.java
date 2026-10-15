package com.ccs.voxelgun.map.maps;

import com.ccs.voxelgun.map.GameMap;
import com.ccs.voxelgun.map.MapBuilder;
import com.ccs.voxelgun.map.MapEnvironment;
import com.ccs.voxelgun.map.scenery.ClassicArenaDressing;
import com.ccs.voxelgun.voxel.Materials;

/**
 * Underground research facility: polished floors, glass partitions, screen banks and emergency
 * lighting. Tight corridors open into two large labs, so close quarters dominate.
 */
public final class Facility extends GameMap {
    private static final int G = MapBuilder.GROUND;
    private static final int CEILING = G + 12;

    public Facility() {
        super("facility", "Facility", "Indoor · corridors and labs");
    }

    @Override
    public MapEnvironment environment() {
        return MapEnvironment.builder()
                .sun(-0.25f, 0.93f, 0.27f)
                .sunColor(0.72f, 0.80f, 1.00f, 0.6f)
                .sunAngle(0.12f)
                .sky(0.04f, 0.05f, 0.08f, 0.08f, 0.10f, 0.14f, 0.40f)
                .fog(0.020f)
                .exposure(1.35f)
                .indoor(true)
                .build();
    }

    @Override
    protected void generate(MapBuilder b) {
        // Solid rock everywhere; the playable space is carved out of it.
        b.fill(0, 0, 0, SIZE_X - 1, CEILING + 2, SIZE_Z - 1, Materials.STONE);

        room(b, 12, 12, 46, 46, Materials.TILE_DARK);
        room(b, 82, 82, 116, 116, Materials.TILE_DARK);
        room(b, 50, 50, 78, 78, Materials.MARBLE);
        room(b, 14, 84, 44, 114, Materials.CONCRETE);
        room(b, 84, 14, 114, 44, Materials.CONCRETE);

        corridor(b, 46, 26, 52, 32);
        corridor(b, 46, 60, 50, 68);
        corridor(b, 78, 60, 84, 68);
        corridor(b, 60, 46, 68, 50);
        corridor(b, 60, 78, 68, 84);
        corridor(b, 26, 46, 32, 84);
        corridor(b, 96, 44, 102, 82);
        corridor(b, 44, 96, 84, 102);
        corridor(b, 44, 26, 84, 32);

        labs(b);
        serverRoom(b);
        reactor(b);
        combatRoutes(b);
        lighting(b);

        ClassicArenaDressing.geometry(b, id());

        blueSpawn(20, 20, 135);
        blueSpawn(26, 18, 135);
        blueSpawn(18, 26, 135);
        blueSpawn(30, 24, 135);
        blueSpawn(40, 40, 135);
        blueSpawn(40, 16, 135);
        blueSpawn(16, 40, 135);

        redSpawn(108, 108, 315);
        redSpawn(102, 110, 315);
        redSpawn(110, 102, 315);
        redSpawn(110, 110, 315);
        redSpawn(110, 92, 315);
        redSpawn(94, 108, 315);
        redSpawn(108, 94, 315);

        freeSpawn(48, 28, 135);
        freeSpawn(28, 100, 45);
        freeSpawn(100, 28, 225);
        freeSpawn(64, 28, 180);
        freeSpawn(64, 100, 0);
        freeSpawn(28, 64, 90);
        freeSpawn(100, 64, 270);
        freeSpawn(80, 100, 315);
    }

    /** Carves a room and lays its floor and ceiling. */
    private void room(MapBuilder b, int x0, int z0, int x1, int z1, int floor) {
        b.clear(x0, G, z0, x1, CEILING - 1, z1);
        b.fill(x0, G - 1, z0, x1, G - 1, z1, floor);
        b.fill(x0, CEILING, z0, x1, CEILING, z1, Materials.CONCRETE_DARK);
    }

    /** Carves a corridor with a lower ceiling and a hazard-striped floor edge. */
    private void corridor(MapBuilder b, int x0, int z0, int x1, int z1) {
        b.clear(x0, G, z0, x1, CEILING - 5, z1);
        b.fill(x0, G - 1, z0, x1, G - 1, z1, Materials.CONCRETE);
        b.fill(x0, CEILING - 4, z0, x1, CEILING - 4, z1, Materials.CONCRETE_DARK);
        if (x1 - x0 > z1 - z0) {
            b.fill(x0, G - 1, z0, x1, G - 1, z0, Materials.HAZARD);
            b.fill(x0, G - 1, z1, x1, G - 1, z1, Materials.HAZARD);
        } else {
            b.fill(x0, G - 1, z0, x0, G - 1, z1, Materials.HAZARD);
            b.fill(x1, G - 1, z0, x1, G - 1, z1, Materials.HAZARD);
        }
    }

    /** Glass-walled lab pods with workbenches and a mezzanine. */
    private void labs(MapBuilder b) {
        for (int pod = 0; pod < 2; pod++) {
            int x = pod == 0 ? 16 : 86;
            int z = pod == 0 ? 16 : 86;

            b.fill(x + 6, G, z, x + 6, G + 5, z + 18, Materials.GLASS);
            b.clear(x + 6, G, z + 8, x + 6, G + 3, z + 10);
            b.fill(x, G, z + 18, x + 18, G + 5, z + 18, Materials.GLASS);
            b.clear(x + 8, G, z + 18, x + 10, G + 3, z + 18);

            for (int i = 0; i < 3; i++) {
                int bx = x + 9 + i * 4;
                b.fill(bx, G, z + 3, bx + 2, G + 1, z + 6, Materials.PLASTIC_WHITE);
                b.set(bx + 1, G + 2, z + 4, Materials.SCREEN);
                b.fill(bx, G, z + 12, bx + 2, G + 1, z + 15, Materials.PLASTIC_WHITE);
                b.set(bx + 1, G + 2, z + 13, Materials.SCREEN);
            }

            // Mezzanine over the pod.
            b.fill(x + 2, G + 6, z + 2, x + 16, G + 6, z + 8, Materials.GRATE);
            b.landing(x + 8, G + 6, z + 2, x + 11, z + 8, Materials.GRATE);
            b.stairs(x + 8, G, z + 15, 3, 7, 3, Materials.STEEL);
            b.fill(x + 2, G + 7, z + 1, x + 16, G + 7, z + 1, Materials.STEEL);
        }
    }

    /** Server aisles: dense cover plus a lot of emissive detail for the bounce pass. */
    private void serverRoom(MapBuilder b) {
        int x0 = 52;
        int z0 = 52;
        for (int i = 0; i < 5; i++) {
            int x = x0 + i * 5;
            b.fill(x, G, z0 + 2, x + 2, G + 4, z0 + 20, Materials.CONCRETE_DARK);
            for (int z = z0 + 3; z < z0 + 20; z += 3) {
                b.set(x, G + 1, z, Materials.SCREEN);
                b.set(x + 2, G + 3, z, Materials.NEON_CYAN);
            }
        }
        b.fill(50, G - 1, 50, 78, G - 1, 78, Materials.MARBLE);
        b.checker(50, 50, 78, 78, G - 1, Materials.MARBLE, Materials.TILE_DARK, 4);
        b.fill(50, CEILING - 1, 50, 78, CEILING - 1, 78, Materials.CHROME);
    }

    /** Reactor core: the map's brightest light and its landmark. */
    private void reactor(MapBuilder b) {
        int cx = 64;
        int cz = 98;
        b.clear(cx - 8, G, cz - 8, cx + 8, CEILING - 1, cz + 8);
        b.fill(cx - 8, G - 1, cz - 8, cx + 8, G - 1, cz + 8, Materials.CHROME);
        b.pillar(cx, G, cz, 9, 3, Materials.LAVA);
        b.pillar(cx, G, cz, 9, 4, Materials.AIR);
        b.pillar(cx, G, cz, 1, 4, Materials.CHROME);
        for (int i = 0; i < 4; i++) {
            double angle = Math.PI / 4 + i * Math.PI / 2;
            int x = cx + (int) (Math.cos(angle) * 6);
            int z = cz + (int) (Math.sin(angle) * 6);
            b.fill(x, G, z, x, G + 8, z, Materials.CHROME);
            b.set(x, G + 9, z, Materials.NEON_MAGENTA);
        }
        b.fill(cx - 8, G, cz - 8, cx + 8, G + 1, cz - 8, Materials.GRATE);
        b.fill(cx - 8, G, cz + 8, cx + 8, G + 1, cz + 8, Materials.GRATE);
    }

    /** Ceiling panels and emergency strips; without them the facility is pitch black. */
    private void lighting(MapBuilder b) {
        for (int z = 16; z < 116; z += 12) {
            for (int x = 16; x < 116; x += 12) {
                if (b.world().get(x, CEILING, z) == Materials.CONCRETE_DARK) {
                    b.fill(x - 1, CEILING, z, x + 1, CEILING, z, Materials.LAMP);
                }
                if (b.world().get(x, CEILING - 4, z) == Materials.CONCRETE_DARK) {
                    b.set(x, CEILING - 4, z, Materials.LAMP);
                }
            }
        }
        // Red emergency strips along the corridor skirting.
        for (int i = 46; i < 84; i += 6) {
            b.set(i, G, 26, Materials.EMERGENCY);
            b.set(i, G, 32, Materials.EMERGENCY);
            b.set(26, G, i + 50 > 114 ? 114 : i + 50, Materials.EMERGENCY);
        }
    }

    /** Three linked routes: short server aisle, west reactor flank, east laboratory flank. */
    private void combatRoutes(MapBuilder b) {
        corridor(b, 40, 38, 64, 44);
        corridor(b, 58, 40, 64, 50);
        corridor(b, 32, 60, 50, 68);
        corridor(b, 78, 60, 96, 68);
        // Two transverse aisles remove the server-room's single-file dead ends.
        b.clear(50, G, 62, 78, G + 3, 65);
        b.clear(50, G, 72, 78, G + 3, 75);
        // Offset blast doors make long side corridors cover-to-cover instead of laser lanes.
        b.fill(27, G, 56, 29, G + 3, 57, Materials.STEEL);
        b.fill(99, G, 72, 101, G + 3, 73, Materials.STEEL);
        for (int pod = 0; pod < 2; pod++) {
            int x = pod == 0 ? 16 : 86, z = pod == 0 ? 16 : 86;
            b.stairs(x + 16, G, z + 14, 3, 7, 3, Materials.STEEL);
            b.fill(x + 14, G + 6, z + 6, x + 18, G + 6, z + 8, Materials.GRATE);
        }
    }
}
