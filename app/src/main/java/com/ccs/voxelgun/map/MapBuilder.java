package com.ccs.voxelgun.map;

import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.voxel.VoxelWorld;

import java.util.Random;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Block-placement primitives the maps are written in. All coordinates are inclusive. */
public final class MapBuilder {
    public static final int GROUND = 3;

    private final VoxelWorld world;
    private final Random random;
    private final List<StairFlight> stairFlights = new ArrayList<>();
    private final List<StairFlight> stairView = Collections.unmodifiableList(stairFlights);
    private final com.ccs.voxelgun.map.scenery.MapScenery scenery;

    public MapBuilder(VoxelWorld world, long seed) {
        this(world, seed, new com.ccs.voxelgun.map.scenery.MapScenery());
    }

    public MapBuilder(VoxelWorld world, long seed, com.ccs.voxelgun.map.scenery.MapScenery scenery) {
        this.world = world;
        this.random = new Random(seed);
        this.scenery = scenery;
    }

    public com.ccs.voxelgun.map.scenery.MapScenery scenery() { return scenery; }

    public VoxelWorld world() {
        return world;
    }

    public Random random() {
        return random;
    }

    public void set(int x, int y, int z, int material) {
        world.set(x, y, z, material);
    }

    public void fill(int x0, int y0, int z0, int x1, int y1, int z1, int material) {
        int ax = Math.min(x0, x1);
        int bx = Math.max(x0, x1);
        int ay = Math.min(y0, y1);
        int by = Math.max(y0, y1);
        int az = Math.min(z0, z1);
        int bz = Math.max(z0, z1);
        for (int z = az; z <= bz; z++) {
            for (int y = ay; y <= by; y++) {
                for (int x = ax; x <= bx; x++) {
                    world.set(x, y, z, material);
                }
            }
        }
    }

    public void clear(int x0, int y0, int z0, int x1, int y1, int z1) {
        fill(x0, y0, z0, x1, y1, z1, Materials.AIR);
    }

    /** Fills with two materials mixed by a per-voxel chance, which breaks up flat surfaces. */
    public void speckle(int x0, int y0, int z0, int x1, int y1, int z1, int base, int accent,
                        float chance) {
        for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                    world.set(x, y, z, random.nextFloat() < chance ? accent : base);
                }
            }
        }
    }

    /** Hollow shell: four walls plus floor and ceiling. */
    public void shell(int x0, int y0, int z0, int x1, int y1, int z1, int material) {
        fill(x0, y0, z0, x1, y1, z1, material);
        clear(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y1 - 1, z1 - 1);
    }

    /** Four vertical walls, open top and bottom. */
    public void walls(int x0, int y0, int z0, int x1, int y1, int z1, int material) {
        fill(x0, y0, z0, x1, y1, z0, material);
        fill(x0, y0, z1, x1, y1, z1, material);
        fill(x0, y0, z0, x0, y1, z1, material);
        fill(x1, y0, z0, x1, y1, z1, material);
    }

    public void slab(int x0, int z0, int x1, int z1, int y, int material) {
        fill(x0, y, z0, x1, y, z1, material);
    }

    /** Checkerboard floor; reads well under ray-traced reflections. */
    public void checker(int x0, int z0, int x1, int z1, int y, int a, int b, int cell) {
        for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                boolean even = ((x / cell) + (z / cell)) % 2 == 0;
                world.set(x, y, z, even ? a : b);
            }
        }
    }

    /** Cuts a doorway through a wall, leaving a lintel above. */
    public void doorway(int x, int y, int z, int axis, int width, int height) {
        int half = width / 2;
        if (axis == 0) {
            clear(x, y, z - half, x, y + height - 1, z + half);
        } else {
            clear(x - half, y, z, x + half, y + height - 1, z);
        }
    }

    /** Cuts a window and glazes it. */
    public void window(int x0, int y0, int z0, int x1, int y1, int z1, boolean glazed) {
        clear(x0, y0, z0, x1, y1, z1);
        if (glazed) {
            fill(x0, y0, z0, x1, y1, z1, Materials.GLASS);
        }
    }

    /** Wooden crate with a darker frame; the classic cover prop. */
    public void crate(int x, int y, int z, int size, int body, int frame) {
        fill(x, y, z, x + size - 1, y + size - 1, z + size - 1, body);
        int max = size - 1;
        for (int i = 0; i < size; i++) {
            world.set(x + i, y, z, frame);
            world.set(x + i, y, z + max, frame);
            world.set(x + i, y + max, z, frame);
            world.set(x + i, y + max, z + max, frame);
            world.set(x, y + i, z, frame);
            world.set(x + max, y + i, z, frame);
            world.set(x, y + i, z + max, frame);
            world.set(x + max, y + i, z + max, frame);
            world.set(x, y, z + i, frame);
            world.set(x + max, y, z + i, frame);
            world.set(x, y + max, z + i, frame);
            world.set(x + max, y + max, z + i, frame);
        }
    }

    /** Stack of crates of mixed sizes, used to build climbable cover. */
    public void crateStack(int x, int y, int z, int count, int body, int frame) {
        int cursorY = y;
        for (int i = 0; i < count; i++) {
            int size = 3 + random.nextInt(2);
            int offsetX = random.nextInt(3) - 1;
            int offsetZ = random.nextInt(3) - 1;
            crate(x + offsetX, cursorY, z + offsetZ, size, body, frame);
            cursorY += size;
        }
    }

    /** Shipping container with a hollow interior and open ends. */
    public void container(int x, int y, int z, boolean alongX, int length, int material) {
        int width = 4;
        int height = 5;
        int x1 = alongX ? x + length - 1 : x + width - 1;
        int z1 = alongX ? z + width - 1 : z + length - 1;
        fill(x, y, z, x1, y + height - 1, z1, material);
        clear(x + 1, y + 1, z + 1, x1 - 1, y + height - 2, z1 - 1);
        if (alongX) {
            clear(x, y + 1, z + 1, x, y + height - 2, z1 - 1);
            clear(x1, y + 1, z + 1, x1, y + height - 2, z1 - 1);
        } else {
            clear(x + 1, y + 1, z, x1 - 1, y + height - 2, z);
            clear(x + 1, y + 1, z1, x1 - 1, y + height - 2, z1);
        }
        // Corrugation: every other rib in a darker tone.
        for (int i = 0; i < length; i += 2) {
            if (alongX) {
                fill(x + i, y, z, x + i, y + height - 1, z, Materials.RUST);
                fill(x + i, y, z1, x + i, y + height - 1, z1, Materials.RUST);
            } else {
                fill(x, y, z + i, x, y + height - 1, z + i, Materials.RUST);
                fill(x1, y, z + i, x1, y + height - 1, z + i, Materials.RUST);
            }
        }
    }

    /**
     * Staircase climbing one voxel per step.
     *
     * @param direction 0 +X, 1 -X, 2 +Z, 3 -Z
     */
    public void stairs(int x, int y, int z, int direction, int steps, int width, int material) {
        StairFlight flight = new StairFlight(x, y, z, direction, steps, width);
        stairFlights.add(flight);
        for (int i = 0; i < steps; i++) {
            int sx = x + (direction == 0 ? i : direction == 1 ? -i : 0);
            int sz = z + (direction == 2 ? i : direction == 3 ? -i : 0);
            if (direction <= 1) {
                fill(sx, y, sz, sx, y + i, sz + width - 1, material);
            } else {
                fill(sx, y, sz, sx + width - 1, y + i, sz, material);
            }
        }
        clearStairHeadroom(flight);
    }

    public List<StairFlight> stairFlights() { return stairView; }

    /** Flat destination deck with standing/jumping clearance, authored before adjoining flights. */
    public void landing(int x0, int y, int z0, int x1, int z1, int material) {
        slab(x0, z0, x1, z1, y, material);
        clear(x0, y + 1, z0, x1, y + 4, z1);
    }

    /** Copies authoring data together with a tiled district's voxels. */
    public void copyStairFlights(MapBuilder source, int offsetX, int offsetZ) {
        for (StairFlight f : source.stairFlights) {
            StairFlight copy = new StairFlight(f.x + offsetX, f.y, f.z + offsetZ,
                    f.direction, f.steps, f.width);
            if (world.inside(copy.xAt(0), copy.y, copy.zAt(0))
                    && world.inside(copy.xAt(copy.steps - 1) + (copy.direction > 1 ? copy.width - 1 : 0),
                    copy.feetAt(copy.steps - 1) + 3,
                    copy.zAt(copy.steps - 1) + (copy.direction <= 1 ? copy.width - 1 : 0)))
                stairFlights.add(copy);
        }
    }

    /**
     * Final build pass: a later floor, railing or cosmetic authoring pass cannot seal a flight.
     * Deliberately removed flights (e.g. a battlefield road replacing a district) are not rebuilt.
     */
    public void finishTraversal() {
        for (int index = stairFlights.size() - 1; index >= 0; index--) {
            StairFlight flight = stairFlights.get(index);
            boolean present = true;
            for (int i = 0; i < flight.steps && present; i++) {
                for (int w = 0; w < flight.width; w++) {
                    int x = flight.xAt(i) + (flight.direction > 1 ? w : 0);
                    int z = flight.zAt(i) + (flight.direction <= 1 ? w : 0);
                    if (!world.solid(x, flight.feetAt(i) - 1, z)) { present = false; break; }
                }
            }
            if (present) clearStairHeadroom(flight);
            else stairFlights.remove(index);
        }
    }

    private void clearStairHeadroom(StairFlight f) {
        for (int i = 0; i < f.steps; i++) {
            int x = f.xAt(i), z = f.zAt(i), feet = f.feetAt(i);
            clear(x, feet, z, x + (f.direction > 1 ? f.width - 1 : 0), feet + 3,
                    z + (f.direction <= 1 ? f.width - 1 : 0));
        }
    }

    public void pillar(int x, int y, int z, int height, int radius, int material) {
        for (int dy = 0; dy < height; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (dx * dx + dz * dz <= radius * radius) {
                        world.set(x + dx, y + dy, z + dz, material);
                    }
                }
            }
        }
    }

    public void barrel(int x, int y, int z, int material) {
        for (int dy = 0; dy < 3; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (Math.abs(dx) + Math.abs(dz) <= 1 || dy == 1) {
                        world.set(x + dx, y + dy, z + dz, material);
                    }
                }
            }
        }
        world.set(x, y + 3, z, Materials.HAZARD);
    }

    public void sandbags(int x, int y, int z, boolean alongX, int length, int height) {
        for (int i = 0; i < length; i++) {
            for (int h = 0; h < height; h++) {
                int material = ((i + h) % 3 == 0) ? Materials.CANVAS : Materials.SANDBAG;
                if (alongX) {
                    world.set(x + i, y + h, z, material);
                    world.set(x + i, y + h, z + 1, material);
                } else {
                    world.set(x, y + h, z + i, material);
                    world.set(x + 1, y + h, z + i, material);
                }
            }
        }
    }

    public void lamp(int x, int y, int z, int mount) {
        world.set(x, y, z, Materials.LAMP);
        if (mount != Materials.AIR) {
            world.set(x, y + 1, z, mount);
        }
    }

    /** Neon strip; the ray tracer picks these up as area lights through the GI bounce. */
    public void neonStrip(int x, int y, int z, boolean alongX, int length, int material) {
        for (int i = 0; i < length; i++) {
            if (alongX) {
                world.set(x + i, y, z, material);
            } else {
                world.set(x, y, z + i, material);
            }
        }
    }

    public void tree(int x, int y, int z, int height) {
        fill(x, y, z, x, y + height - 1, z, Materials.TRUNK);
        int top = y + height;
        for (int dy = -2; dy <= 1; dy++) {
            int radius = dy <= 0 ? 2 : 1;
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (dx * dx + dz * dz > radius * radius + 1) {
                        continue;
                    }
                    if (dx == 0 && dz == 0 && dy < 0) {
                        continue;
                    }
                    world.set(x + dx, top + dy, z + dz, Materials.LEAVES);
                }
            }
        }
    }

    public void fence(int x, int y, int z, boolean alongX, int length, int height) {
        for (int i = 0; i < length; i++) {
            int material = i % 4 == 0 ? Materials.STEEL : Materials.GRATE;
            for (int h = 0; h < height; h++) {
                if (alongX) {
                    world.set(x + i, y + h, z, material);
                } else {
                    world.set(x, y + h, z + i, material);
                }
            }
        }
    }

    /** Elevated walkway with railings; the classic warehouse catwalk. */
    public void catwalk(int x, int y, int z, boolean alongX, int length, int width) {
        for (int i = 0; i < length; i++) {
            for (int w = 0; w < width; w++) {
                int px = alongX ? x + i : x + w;
                int pz = alongX ? z + w : z + i;
                world.set(px, y, pz, Materials.GRATE);
            }
            if (alongX) {
                world.set(x + i, y + 1, z - 1, Materials.STEEL);
                world.set(x + i, y + 1, z + width, Materials.STEEL);
            } else {
                world.set(x - 1, y + 1, z + i, Materials.STEEL);
                world.set(x + width, y + 1, z + i, Materials.STEEL);
            }
        }
    }

    /** A gabled roof over the given footprint. */
    public void roof(int x0, int z0, int x1, int z1, int y, int material) {
        int width = Math.min(x1 - x0, z1 - z0);
        for (int layer = 0; layer <= width / 2 + 1; layer++) {
            int ax = x0 + layer;
            int bx = x1 - layer;
            int az = z0 + layer;
            int bz = z1 - layer;
            if (ax > bx || az > bz) {
                break;
            }
            fill(ax, y + layer, az, bx, y + layer, bz, material);
            if (layer > 0) {
                clear(ax + 1, y + layer, az + 1, bx - 1, y + layer, bz - 1);
            }
        }
    }

    /**
     * A building, not a painted box. Brick courses in two tones, contrasting quoins on all four
     * corners, a plinth at the base, a painted trim band under the eaves, a framed door and framed
     * windows on every side, and a flat roof behind a low parapet with a tiled lip.
     *
     * <p>{@code brick}/{@code brickAlt} are the two course tones and {@code trim} paints the band,
     * the plinth and the parapet lip.
     */
    public void building(int x, int z, int width, int depth, int height, int brick, int brickAlt,
                         int trim) {
        int y = GROUND;
        int x1 = x + width - 1;
        int z1 = z + depth - 1;
        int top = y + height - 1;

        fill(x, y - 1, z, x1, y - 1, z1, Materials.STONE);
        brickWalls(x, y, z, x1, top, z1, brick, brickAlt);

        // Plinth, eaves band and quoins. Without these the wall is one flat colour from the
        // pavement to the sky, which is exactly what "just blocks" looks like.
        trimBand(x, z, x1, z1, y, y, trim);
        trimBand(x, z, x1, z1, top - 1, top, trim);
        quoins(x, z, x1, z1, y + 1, top - 2, Materials.PLASTER);

        // Interior floor and the flat roof slab.
        fill(x + 1, y, z + 1, x1 - 1, y, z1 - 1, Materials.CONCRETE);
        fill(x, top + 1, z, x1, top + 1, z1, Materials.CONCRETE);

        // Parapet with a tiled lip, so the roofline has an edge rather than stopping dead.
        trimBand(x, z, x1, z1, top + 2, top + 2, Materials.PLASTER);
        trimBand(x - 1, z - 1, x1 + 1, z1 + 1, top + 3, top + 3, Materials.ROOF_TILE);

        framedDoor(x + width / 2, y + 1, z, 1, 3, 3);
        int midZ = z + depth / 2;
        int midX = x + width / 2;
        if (height >= 5) {
            framedWindow(x, y + 3, midZ, 0, 3, 2, true);
            framedWindow(x1, y + 3, midZ, 0, 3, 2, true);
            framedWindow(midX, y + 3, z1, 1, 3, 2, true);
        }
    }

    /** Kept for callers that just want the old shape; paints it in the light brick livery. */
    public void house(int x, int z, int width, int depth, int height, int wall, int roofMaterial) {
        int brick = wall == Materials.BRICK ? Materials.BRICK_ORANGE : Materials.BRICK_WHITE;
        int alt = wall == Materials.BRICK ? Materials.BRICK_ORANGE_ALT : Materials.BRICK_WHITE_ALT;
        int trim = roofMaterial == Materials.ROOF ? Materials.TRIM_RED : Materials.TRIM_BLUE;
        building(x, z, width, depth, height, brick, alt, trim);
    }

    // ------------------------------------------------------------------ exteriors
    //
    // Everything below exists so a building reads as a building rather than as a painted box.
    // A voxel world has no textures, so surface detail has to be voxels: brick courses in two
    // tones, quoined corners, framed openings, a painted band under the eaves, grout lines in a
    // plaza. It costs nothing at run time — the greedy mesher merges by material, so a two tone
    // wall is a few more quads, not a new draw call.

    /**
     * Fills a wall face in a running bond: two tones alternating along the course, with every
     * other course offset by half a brick so the joints stagger the way real brickwork does.
     */
    public void brickFill(int x0, int y0, int z0, int x1, int y1, int z1, int base, int alt) {
        for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                int offset = (y % 2 == 0) ? 0 : 1;
                for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                    boolean light = ((x + z + offset) / 2 + y) % 2 == 0;
                    world.set(x, y, z, light ? base : alt);
                }
            }
        }
    }

    /** Four brick walls, open top and bottom. */
    public void brickWalls(int x0, int y0, int z0, int x1, int y1, int z1, int base, int alt) {
        brickFill(x0, y0, z0, x1, y1, z0, base, alt);
        brickFill(x0, y0, z1, x1, y1, z1, base, alt);
        brickFill(x0, y0, z0, x0, y1, z1, base, alt);
        brickFill(x1, y0, z0, x1, y1, z1, base, alt);
    }

    /** Painted band running right around a building; the eaves line in Block Strike terms. */
    public void trimBand(int x0, int z0, int x1, int z1, int y0, int y1, int material) {
        fill(x0, y0, z0, x1, y1, z0, material);
        fill(x0, y0, z1, x1, y1, z1, material);
        fill(x0, y0, z0, x0, y1, z1, material);
        fill(x1, y0, z0, x1, y1, z1, material);
    }

    /** Contrasting corner columns, which is what stops a facade reading as one flat slab. */
    public void quoins(int x0, int z0, int x1, int z1, int y0, int y1, int material) {
        fill(x0, y0, z0, x0, y1, z0, material);
        fill(x1, y0, z0, x1, y1, z0, material);
        fill(x0, y0, z1, x0, y1, z1, material);
        fill(x1, y0, z1, x1, y1, z1, material);
    }

    /**
     * Window opening with a light frame around it. {@code axis} is 0 for a wall lying on the X
     * plane (a west or east wall) and 1 for a north or south wall.
     */
    public void framedWindow(int x, int y, int z, int axis, int width, int height,
                             boolean glazed) {
        int half = width / 2;
        if (axis == 0) {
            fill(x, y - 1, z - half - 1, x, y + height, z + half + 1, Materials.FRAME_LIGHT);
            clear(x, y, z - half, x, y + height - 1, z + half);
            if (glazed) {
                fill(x, y, z - half, x, y + height - 1, z + half, Materials.GLASS);
            }
        } else {
            fill(x - half - 1, y - 1, z, x + half + 1, y + height, z, Materials.FRAME_LIGHT);
            clear(x - half, y, z, x + half, y + height - 1, z);
            if (glazed) {
                fill(x - half, y, z, x + half, y + height - 1, z, Materials.GLASS);
            }
        }
    }

    /**
     * Doorway with a frame and a dark recess above head height, so it reads as a door rather than
     * as a hole. The lower part is left open — bots path through these.
     */
    public void framedDoor(int x, int y, int z, int axis, int width, int height) {
        int half = width / 2;
        if (axis == 0) {
            fill(x, y, z - half - 1, x, y + height, z + half + 1, Materials.FRAME_LIGHT);
            clear(x, y, z - half, x, y + height - 1, z + half);
            fill(x, y + height - 1, z - half, x, y + height - 1, z + half, Materials.DOOR_DARK);
        } else {
            fill(x - half - 1, y, z, x + half + 1, y + height, z, Materials.FRAME_LIGHT);
            clear(x - half, y, z, x + half, y + height - 1, z);
            fill(x - half, y + height - 1, z, x + half, y + height - 1, z, Materials.DOOR_DARK);
        }
    }

    /** Plaza floor with grout lines on a grid, the way the Block Strike courtyards are paved. */
    public void tiledFloor(int x0, int z0, int x1, int z1, int y, int tile, int grout, int cell) {
        for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                boolean line = x % cell == 0 || z % cell == 0;
                world.set(x, y, z, line ? grout : tile);
            }
        }
    }

    /** Plank decking: strips of two wood tones running along X. */
    public void deck(int x0, int z0, int x1, int z1, int y) {
        for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
            int material = (z % 4 < 2) ? Materials.DECK_WOOD : Materials.DECK_WOOD_ALT;
            fill(x0, y, z, x1, y, z, material);
        }
    }

    /** Sunken pool with a raised white rim; the water is translucent, so it can be seen into. */
    public void pool(int x0, int z0, int x1, int z1, int y, int depth) {
        fill(x0 - 1, y, z0 - 1, x1 + 1, y, z1 + 1, Materials.POOL_EDGE);
        fill(x0 - 1, y + 1, z0 - 1, x1 + 1, y + 1, z1 + 1, Materials.POOL_EDGE);
        clear(x0, y + 1, z0, x1, y + 1, z1);
        fill(x0, y - depth, z0, x1, y - 1, z1, Materials.TILE_FLOOR);
        clear(x0, y - depth + 1, z0, x1, y - 1, z1);
        fill(x0, y - depth + 1, z0, x1, y, z1, Materials.WATER);
    }

    /** The big panelled shipping crate, with seams along every edge and a cross on each face. */
    public void panelCrate(int x, int y, int z, int size) {
        int max = size - 1;
        fill(x, y, z, x + max, y + max, z + max, Materials.PANEL_GREY);
        for (int i = 0; i < size; i++) {
            world.set(x + i, y, z, Materials.PANEL_SEAM);
            world.set(x + i, y + max, z, Materials.PANEL_SEAM);
            world.set(x + i, y, z + max, Materials.PANEL_SEAM);
            world.set(x + i, y + max, z + max, Materials.PANEL_SEAM);
            world.set(x, y + i, z, Materials.PANEL_SEAM);
            world.set(x + max, y + i, z, Materials.PANEL_SEAM);
            world.set(x, y + i, z + max, Materials.PANEL_SEAM);
            world.set(x + max, y + i, z + max, Materials.PANEL_SEAM);
            world.set(x, y, z + i, Materials.PANEL_SEAM);
            world.set(x + max, y, z + i, Materials.PANEL_SEAM);
            world.set(x, y + max, z + i, Materials.PANEL_SEAM);
            world.set(x + max, y + max, z + i, Materials.PANEL_SEAM);
        }
        int mid = size / 2;
        fill(x, y + mid, z, x + max, y + mid, z, Materials.PANEL_SEAM);
        fill(x, y + mid, z + max, x + max, y + mid, z + max, Materials.PANEL_SEAM);
        fill(x, y + mid, z, x, y + mid, z + max, Materials.PANEL_SEAM);
        fill(x + max, y + mid, z, x + max, y + mid, z + max, Materials.PANEL_SEAM);
    }

    /** Oil drum in the two tone livery: painted body, rusted top, ribs around the middle. */
    public void oilDrum(int x, int y, int z) {
        for (int dy = 0; dy < 4; dy++) {
            int material = dy < 2 ? Materials.BARREL_TEAL : Materials.BARREL_RUST;
            if (dy == 1 || dy == 3) {
                material = Materials.STEEL;
            }
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (Math.abs(dx) + Math.abs(dz) <= 1 || dy == 1 || dy == 2) {
                        world.set(x + dx, y + dy, z + dz, material);
                    }
                }
            }
        }
    }

    /** Kerbed planter or step edging, used to break a large paved area up. */
    public void kerb(int x0, int z0, int x1, int z1, int y, int fillMaterial) {
        trimBand(x0, z0, x1, z1, y, y, Materials.KERB);
        fill(x0 + 1, y, z0 + 1, x1 - 1, y, z1 - 1, fillMaterial);
    }

    /**
     * Perimeter wall in brick courses with a painted coping along the top. This is the wall you
     * spend the whole match looking at, so it is worth more than one flat colour.
     */
    public void boundary(int thickness, int height, int base, int alt, int coping) {
        int sx = world.sizeX() - 1;
        int sz = world.sizeZ() - 1;
        int y0 = GROUND;
        int y1 = GROUND + height;
        brickFill(0, y0, 0, sx, y1 - 1, thickness - 1, base, alt);
        brickFill(0, y0, sz - thickness + 1, sx, y1 - 1, sz, base, alt);
        brickFill(0, y0, 0, thickness - 1, y1 - 1, sz, base, alt);
        brickFill(sx - thickness + 1, y0, 0, sx, y1 - 1, sz, base, alt);
        fill(0, y1, 0, sx, y1, thickness - 1, coping);
        fill(0, y1, sz - thickness + 1, sx, y1, sz, coping);
        fill(0, y1, 0, thickness - 1, y1, sz, coping);
        fill(sx - thickness + 1, y1, 0, sx, y1, sz, coping);
    }

    /** Places a rectangular border wall around the whole map so players cannot leave. */
    public void boundary(int thickness, int height, int material) {
        int sx = world.sizeX() - 1;
        int sz = world.sizeZ() - 1;
        fill(0, GROUND, 0, sx, GROUND + height, thickness - 1, material);
        fill(0, GROUND, sz - thickness + 1, sx, GROUND + height, sz, material);
        fill(0, GROUND, 0, thickness - 1, GROUND + height, sz, material);
        fill(sx - thickness + 1, GROUND, 0, sx, GROUND + height, sz, material);
    }

    public void ground(int material, int accent, float chance) {
        speckle(0, 0, 0, world.sizeX() - 1, GROUND - 1, world.sizeZ() - 1, material, accent, chance);
    }

    /** Scatters small props over a region without stacking them on top of each other. */
    public void scatter(int x0, int z0, int x1, int z1, int count, PropPlacer placer) {
        for (int i = 0; i < count; i++) {
            int x = x0 + random.nextInt(Math.max(1, x1 - x0));
            int z = z0 + random.nextInt(Math.max(1, z1 - z0));
            if (world.get(x, GROUND, z) == 0 && world.get(x, GROUND + 1, z) == 0) {
                placer.place(this, x, GROUND, z);
            }
        }
    }

    /** Solid disc in the XZ plane, one voxel thick. */
    public void disc(int x, int y, int z, int radius, int material) {
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    world.set(x + dx, y, z + dz, material);
                }
            }
        }
    }

    /** Tapering roof cone, the shape every fairytale turret needs. */
    public void cone(int x, int y, int z, int height, int radius, int material) {
        for (int level = 0; level < height; level++) {
            int r = Math.max(0, Math.round(radius * (1f - (float) level / height)));
            disc(x, y + level, z, r, material);
        }
    }

    /**
     * A rounded floating island: a grass cap over a tapering underside, hanging in the void.
     */
    public void island(int cx, int topY, int cz, int radius, int topMaterial, int bodyMaterial) {
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                float distance = (float) Math.sqrt(dx * dx + dz * dz);
                if (distance > radius) {
                    continue;
                }
                // Deepest in the middle, thinning to a rim at the edge.
                float falloff = 1f - distance / radius;
                int depth = Math.max(1, Math.round(radius * 0.85f * falloff * falloff + 1f));
                for (int level = 0; level < depth; level++) {
                    world.set(cx + dx, topY - level, cz + dz,
                            level == 0 ? topMaterial : bodyMaterial);
                }
            }
        }
    }

    /** Plank walkway with rope-post railings, for spanning gaps between islands. */
    public void bridge(int x, int y, int z, boolean alongX, int length, int width, int deck,
                       int rail) {
        for (int i = 0; i < length; i++) {
            for (int w = 0; w < width; w++) {
                int px = alongX ? x + i : x + w;
                int pz = alongX ? z + w : z + i;
                world.set(px, y, pz, i % 5 == 0 ? rail : deck);
            }
            if (i % 4 != 0) {
                continue;
            }
            for (int h = 1; h <= 2; h++) {
                if (alongX) {
                    world.set(x + i, y + h, z - 1, rail);
                    world.set(x + i, y + h, z + width, rail);
                } else {
                    world.set(x - 1, y + h, z + i, rail);
                    world.set(x + width, y + h, z + i, rail);
                }
            }
        }
    }

    /** Hanging banner, two voxels wide, with a pole across the top. */
    public void banner(int x, int y, int z, int height, int cloth, int pole) {
        fill(x - 1, y, z, x + 1, y, z, pole);
        fill(x - 1, y - height, z, x, y - 1, z, cloth);
    }

    /** Bouncy pad plus the frame that reads as a launcher. */
    public void jumpPad(int x, int y, int z) {
        fill(x - 1, y, z - 1, x + 1, y, z + 1, Materials.STEEL);
        world.set(x, y, z, Materials.JUMP_PAD);
        world.set(x - 1, y, z, Materials.JUMP_PAD);
        world.set(x + 1, y, z, Materials.JUMP_PAD);
        world.set(x, y, z - 1, Materials.JUMP_PAD);
        world.set(x, y, z + 1, Materials.JUMP_PAD);
    }

    /** Palm: a leaning trunk with a crown of fronds. */
    public void palm(int x, int y, int z, int height) {
        for (int level = 0; level < height; level++) {
            world.set(x + level / 4, y + level, z, Materials.TRUNK);
        }
        int topX = x + height / 4;
        int topY = y + height;
        for (int i = 1; i <= 3; i++) {
            world.set(topX + i, topY - (i / 3), z, Materials.LEAVES);
            world.set(topX - i, topY - (i / 3), z, Materials.LEAVES);
            world.set(topX, topY - (i / 3), z + i, Materials.LEAVES);
            world.set(topX, topY - (i / 3), z - i, Materials.LEAVES);
        }
        world.set(topX, topY, z, Materials.LEAVES);
        world.set(topX, topY - 1, z, Materials.SUNNY);
    }

    /** Callback used by {@link #scatter}. */
    public interface PropPlacer {
        void place(MapBuilder builder, int x, int y, int z);
    }
}
