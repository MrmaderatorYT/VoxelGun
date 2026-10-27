package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.voxel.VoxelWorld;

/** Bounded, cached shore selection; underwater escape never requests A* from a trapped floor. */
public final class BotSwimmingController {
    private static final int SEARCH_RADIUS = 24;
    private float targetX, targetZ, retry, exitGrace;
    private int explorationAttempt;
    private boolean target;

    public void reset() { target = false; retry = exitGrace = 0f; explorationAttempt = 0; }
    public boolean active() { return exitGrace > 0f; }

    /** True while wet or coasting from the surface toward the chosen bank. */
    public boolean update(Combatant c, VoxelWorld world, float dt) {
        boolean wet = Swimming.inWater(world, c);
        if (!wet && (c.grounded || exitGrace <= 0f)) { reset(); return false; }
        exitGrace = wet ? .85f : Math.max(0f, exitGrace - dt);
        // Finish on the same tick the grace expires so BotBrain can observe the transition
        // and discard its submerged route, including long low-gravity surface launches.
        if (!wet && exitGrace <= 0f) { reset(); return false; }
        retry -= dt;
        float dx = targetX - c.position.x, dz = targetZ - c.position.z;
        if (wet && retry <= 0f) {
            int surface = Swimming.surface(world, c);
            target = findNearbyExit(c, world, surface) || findShore(c, world, surface)
                    || findWaterWaypoint(c, world, surface);
            retry = 1f; // At most one bounded search per second, including failed searches.
            dx = targetX - c.position.x; dz = targetZ - c.position.z;
        }
        float distance = (float)Math.sqrt(dx * dx + dz * dz);
        boolean wantsMovement = target && distance > .05f;
        if (wantsMovement)
            Swimming.steer(c, dx / distance * Swimming.MOVE_SPEED,
                    dz / distance * Swimming.MOVE_SPEED, dt);
        else Swimming.steer(c, 0f, 0f, dt);
        c.crouching = false;
        return true;
    }

    /**
     * A body can touch water only at its edge while standing beside a dry tile, including the
     * current cell. Under a pier the upward surface route is blocked, but stepping back onto that
     * tile is clear. Check the actual body-height crossing before planning an above-water exit.
     */
    private boolean findNearbyExit(Combatant c, VoxelWorld w, int surface) {
        if (surface < 1 || c.position.y < surface - 1.25f) return false;
        int cx = VoxelWorld.floor(c.position.x), cz = VoxelWorld.floor(c.position.z);
        int highestFeet = Math.min(surface + 1, VoxelWorld.floor(c.position.y + Physics.STEP_HEIGHT));
        for (int radius = 0; radius <= 2; radius++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                for (int x = cx - radius; x <= cx + radius; x++) {
                    if (Math.max(Math.abs(x - cx), Math.abs(z - cz)) != radius
                            || x < 1 || z < 1 || x >= w.sizeX() - 1 || z >= w.sizeZ() - 1) continue;
                    for (int feet = highestFeet; feet >= Math.max(1, surface - 2); feet--) {
                        if (!w.solid(x, feet - 1, z)
                                || w.get(x, feet - 1, z) == Materials.LAVA
                                || wetColumn(w, x, feet, z)
                                || w.boxCollides(x + .5f, feet, z + .5f,
                                        Combatant.RADIUS, Combatant.HEIGHT)) continue;
                        float crossingY = Math.max(c.position.y, feet);
                        if (w.boxCollides(c.position.x, crossingY, c.position.z,
                                Combatant.RADIUS, Combatant.HEIGHT)) continue;
                        if (clearCrossingAtHeight(w, c.position.x, c.position.z,
                                x + .5f, z + .5f, surface, feet, crossingY)) {
                            targetX = x + .5f; targetZ = z + .5f;
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** A distant bank must not leave a swimmer stationary outside the local search radius. */
    private boolean findWaterWaypoint(Combatant c, VoxelWorld w, int surface) {
        if (surface < 1) return false;
        float centerX = w.sizeX() * .5f - c.position.x;
        float centerZ = w.sizeZ() * .5f - c.position.z;
        float heading = centerX * centerX + centerZ * centerZ > 1f
                ? (float)Math.atan2(centerZ, centerX)
                : (c.id + explorationAttempt) * (float)(Math.PI / 4);
        explorationAttempt++;
        for (int sample = 0; sample < 8; sample++) {
            // Prefer the interior, then neighbouring headings if a pier/reef blocks the way.
            int turn = (sample + 1) / 2 * (sample % 2 == 1 ? 1 : -1);
            float angle = heading + turn * (float)(Math.PI / 4);
            float x = c.position.x + (float)Math.cos(angle) * 6f;
            float z = c.position.z + (float)Math.sin(angle) * 6f;
            if (x < 1 || z < 1 || x >= w.sizeX() - 1 || z >= w.sizeZ() - 1
                    || w.get(VoxelWorld.floor(x), surface - 1, VoxelWorld.floor(z))
                    != Materials.WATER) continue;
            if (clearCrossing(w, c.position.x, c.position.z, x, z, surface, surface + 1)) {
                targetX = x; targetZ = z;
                return true;
            }
        }
        return false;
    }

    private boolean findShore(Combatant c, VoxelWorld w, int surface) {
        if (surface < 1) return false;
        int cx = VoxelWorld.floor(c.position.x), cz = VoxelWorld.floor(c.position.z);
        for (int radius = 1; radius <= SEARCH_RADIUS; radius++) {
            // Visit every cell of the ring: sparse rays can miss a one-block bank opening.
            // A bot-specific starting corner also keeps groups from picking identical exits.
            int sideLength = radius * 2, perimeter = radius * 8;
            for (int sample = 0; sample < perimeter; sample++) {
                int offset = Math.floorMod(sample + c.id * 5, perimeter);
                int side = offset / sideLength, along = offset % sideLength - radius;
                int x = cx, z = cz;
                switch (side) {
                    case 0: x += along; z -= radius; break;
                    case 1: x += radius; z += along; break;
                    case 2: x -= along; z += radius; break;
                    default: x -= radius; z -= along; break;
                }
                if (x < 1 || z < 1 || x >= w.sizeX() - 1 || z >= w.sizeZ() - 1) continue;
                // Only test banks touching this water level, not distant land behind walls.
                if (!besideWater(w, x, surface - 1, z)) continue;
                for (int feet = surface + 1; feet >= Math.max(1, surface - 2); feet--) {
                    if (!w.solid(x, feet - 1, z)
                            || w.get(x, feet - 1, z) == Materials.LAVA
                            || w.boxCollides(x + .5f, feet, z + .5f,
                                    Combatant.RADIUS, Combatant.HEIGHT)
                            || wetColumn(w, x, feet, z)) continue;
                    if (clearCrossing(w, c.position.x, c.position.z, x + .5f, z + .5f,
                            surface, feet)) {
                        targetX = x + .5f; targetZ = z + .5f;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean besideWater(VoxelWorld w, int x, int y, int z) {
        return w.get(x - 1, y, z) == Materials.WATER
                || w.get(x + 1, y, z) == Materials.WATER
                || w.get(x, y, z - 1) == Materials.WATER
                || w.get(x, y, z + 1) == Materials.WATER;
    }

    private static boolean wetColumn(VoxelWorld w, int x, int y, int z) {
        return w.get(x, y, z) == Materials.WATER || w.get(x, y + 1, z) == Materials.WATER;
    }

    private static boolean clearCrossing(VoxelWorld w, float fromX, float fromZ,
                                          float toX, float toZ, int surface, int destination) {
        return clearCrossingAtHeight(w, fromX, fromZ, toX, toZ, surface, destination, surface + 1f);
    }

    private static boolean clearCrossingAtHeight(VoxelWorld w, float fromX, float fromZ,
                                                  float toX, float toZ, int surface,
                                                  int destination, float crossingY) {
        float dx = toX - fromX, dz = toZ - fromZ;
        int steps = Math.max(1, (int)Math.ceil(Math.max(Math.abs(dx), Math.abs(dz)) * 2));
        for (int i = 1; i <= steps; i++) {
            float x = fromX + dx * i / steps, z = fromZ + dz * i / steps;
            int bx = VoxelWorld.floor(x), bz = VoxelWorld.floor(z);
            if (w.boxCollides(x, crossingY, z, Combatant.RADIUS, Combatant.HEIGHT)) return false;
            // Do not "swim" across an empty-sky gap to an unrelated floating island.
            if (w.get(bx, surface - 1, bz) != Materials.WATER
                    && !w.solid(bx, destination - 1, bz)
                    && !w.solid(bx, surface - 1, bz)
                    && !w.solid(bx, surface, bz)) return false;
        }
        return true;
    }
}
