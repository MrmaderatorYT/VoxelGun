package com.ccs.voxelgun.map.maps;

import com.ccs.voxelgun.map.GameMap;
import com.ccs.voxelgun.map.MapBuilder;
import com.ccs.voxelgun.map.MapEnvironment;
import com.ccs.voxelgun.voxel.Materials;

/**
 * Tight symmetric arena lit almost entirely by emissive blocks. There is no sun here, so what
 * you see is the traced bounce light from the neon strips and the lava pit — the map exists to
 * make the global illumination pass obvious.
 */
public final class NeonArena extends GameMap {
    private static final int G = MapBuilder.GROUND;
    private static final int CENTRE = 64;

    public NeonArena() {
        super("neon_arena", "Neon Arena", "Indoor · emissive-lit duel pit");
    }

    @Override
    public MapEnvironment environment() {
        return MapEnvironment.builder()
                .sun(-0.3f, 0.9f, 0.2f)
                .sunColor(0.55f, 0.62f, 0.85f, 0.35f)
                .sunAngle(0.15f)
                .sky(0.02f, 0.03f, 0.06f, 0.05f, 0.06f, 0.12f, 0.30f)
                .fog(0.016f)
                .exposure(1.45f)
                .indoor(true)
                .build();
    }

    @Override
    protected void generate(MapBuilder b) {
        b.ground(Materials.TILE_DARK, Materials.CONCRETE_DARK, 0.1f);
        b.checker(24, 24, 103, 103, G - 1, Materials.TILE_DARK, Materials.MARBLE, 6);

        dome(b);
        centralPit(b);
        ringPlatforms(b);
        neonRing(b);
        cover(b);

        blueSpawn(34, 64, 90);
        blueSpawn(34, 56, 90);
        blueSpawn(34, 72, 90);
        blueSpawn(42, 60, 90);
        blueSpawn(42, 68, 90);
        blueSpawn(36, 48, 90);
        blueSpawn(36, 80, 90);

        redSpawn(93, 64, 270);
        redSpawn(94, 56, 270);
        redSpawn(94, 72, 270);
        redSpawn(85, 60, 270);
        redSpawn(85, 68, 270);
        redSpawn(96, 52, 270);
        redSpawn(96, 76, 270);

        freeSpawn(64, 34, 180);
        freeSpawn(64, 93, 0);
        freeSpawn(42, 34, 135);
        freeSpawn(86, 34, 225);
        freeSpawn(42, 94, 45);
        freeSpawn(86, 94, 315);
        freeSpawn(52, 64, 90);
        freeSpawn(76, 64, 270);
    }

    /** Enclosing drum wall and roof; the arena is fully sealed. */
    private void dome(MapBuilder b) {
        int radius = 44;
        int height = 20;
        for (int z = 0; z < SIZE_Z; z++) {
            for (int x = 0; x < SIZE_X; x++) {
                int dx = x - CENTRE;
                int dz = z - CENTRE;
                int distance = dx * dx + dz * dz;
                if (distance > radius * radius && distance < (radius + 5) * (radius + 5)) {
                    b.fill(x, G, z, x, G + height, z, Materials.CONCRETE_DARK);
                } else if (distance >= (radius + 5) * (radius + 5)) {
                    b.fill(x, G - 1, z, x, G + height + 2, z, Materials.CONCRETE_DARK);
                }
                if (distance <= (radius + 5) * (radius + 5)) {
                    b.set(x, G + height + 1, z, Materials.CONCRETE_DARK);
                    b.set(x, G + height + 2, z, Materials.STEEL);
                }
            }
        }
        // Ceiling light coffers.
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0;
            int x = CENTRE + (int) (Math.cos(angle) * 26);
            int z = CENTRE + (int) (Math.sin(angle) * 26);
            b.fill(x - 2, G + height, z - 2, x + 2, G + height, z + 2, Materials.LAMP);
        }
    }

    /** Lava pit in the middle: a strong coloured light source and a hazard to path around. */
    private void centralPit(MapBuilder b) {
        int radius = 9;
        for (int z = CENTRE - radius; z <= CENTRE + radius; z++) {
            for (int x = CENTRE - radius; x <= CENTRE + radius; x++) {
                int dx = x - CENTRE;
                int dz = z - CENTRE;
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                b.fill(x, G - 3, z, x, G - 1, z, Materials.AIR);
                b.set(x, G - 3, z, Materials.LAVA);
                if (dx * dx + dz * dz > (radius - 1) * (radius - 1)) {
                    b.fill(x, G - 2, z, x, G, z, Materials.STONE);
                }
            }
        }
        // Bridge across the pit, the highest-risk route on the map.
        b.fill(CENTRE - radius - 1, G, CENTRE - 1, CENTRE + radius + 1, G, CENTRE + 1,
                Materials.GRATE);
        b.fill(CENTRE - 1, G, CENTRE - radius - 1, CENTRE + 1, G, CENTRE + radius + 1,
                Materials.GRATE);
    }

    /** Four raised platforms with ramps, arranged on the diagonals. */
    private void ringPlatforms(MapBuilder b) {
        int[][] spots = {{46, 46}, {82, 46}, {46, 82}, {82, 82}};
        for (int i = 0; i < spots.length; i++) {
            int x = spots[i][0];
            int z = spots[i][1];
            b.fill(x - 6, G, z - 6, x + 6, G + 3, z + 6, Materials.CONCRETE);
            b.fill(x - 5, G + 4, z - 5, x + 5, G + 4, z + 5, Materials.MARBLE);
            b.fill(x - 6, G + 5, z - 6, x + 6, G + 5, z - 6, Materials.CONCRETE_DARK);
            b.fill(x - 6, G + 5, z + 6, x + 6, G + 5, z + 6, Materials.CONCRETE_DARK);

            int direction = i % 2 == 0 ? 0 : 1;
            b.stairs(x + (direction == 0 ? -10 : 10), G, z - 2, direction, 5, 5, Materials.CONCRETE);

            int accent = i % 2 == 0 ? Materials.NEON_CYAN : Materials.NEON_MAGENTA;
            b.neonStrip(x - 5, G + 4, z - 6, true, 11, accent);
            b.neonStrip(x - 5, G + 4, z + 6, true, 11, accent);
        }
    }

    /** Neon band running around the drum wall at head height. */
    private void neonRing(MapBuilder b) {
        int radius = 43;
        for (int i = 0; i < 360; i += 2) {
            double angle = Math.toRadians(i);
            int x = CENTRE + (int) Math.round(Math.cos(angle) * radius);
            int z = CENTRE + (int) Math.round(Math.sin(angle) * radius);
            int material = (i / 45) % 2 == 0 ? Materials.NEON_CYAN : Materials.NEON_MAGENTA;
            b.set(x, G + 5, z, material);
            b.set(x, G + 14, z, material);
        }
        for (int i = 0; i < 4; i++) {
            double angle = Math.PI / 4 + i * Math.PI / 2;
            int x = CENTRE + (int) (Math.cos(angle) * 38);
            int z = CENTRE + (int) (Math.sin(angle) * 38);
            b.fill(x, G, z, x, G + 16, z, Materials.CHROME);
        }
    }

    /** Chest-high cover so the open floor still has fights in it. */
    private void cover(MapBuilder b) {
        int[][] blocks = {{56, 40}, {72, 40}, {56, 88}, {72, 88}, {40, 56}, {40, 72}, {88, 56},
                {88, 72}, {64, 48}, {64, 80}, {48, 64}, {80, 64}};
        for (int i = 0; i < blocks.length; i++) {
            int x = blocks[i][0];
            int z = blocks[i][1];
            int material = i % 3 == 0 ? Materials.CHROME : Materials.CONCRETE;
            b.fill(x - 2, G, z - 1, x + 2, G + 1, z + 1, material);
            if (i % 4 == 0) {
                b.set(x, G + 2, z, Materials.NEON_CYAN);
            }
        }
        // Two glass screens: they refract nothing, but the traced reflection makes them read.
        b.fill(58, G, 30, 70, G + 5, 30, Materials.GLASS_TINTED);
        b.fill(58, G, 98, 70, G + 5, 98, Materials.GLASS_TINTED);
    }
}
