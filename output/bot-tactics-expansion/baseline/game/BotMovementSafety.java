package com.ccs.voxelgun.game;

import com.ccs.voxelgun.voxel.VoxelWorld;

/**
 * Bounded, cached locomotion clearance. Reads authoritative Java voxels: no extra JNI sight rays,
 * path searches or allocations per bot. Physics remains authoritative for every body contact.
 */
public final class BotMovementSafety {
    private static final float PROBE_INTERVAL = .12f;
    private float probeLeft;
    private float lastX, lastZ, lastDirectionX, lastDirectionZ;
    private int revision = -1;
    private boolean hopClear;

    public void reset() { probeLeft = 0f; revision = -1; hopClear = false; }

    public boolean canHop(Combatant c, VoxelWorld world, float wishX, float wishZ, float dt) {
        probeLeft -= dt;
        float lengthSquared = wishX * wishX + wishZ * wishZ;
        if (lengthSquared < .001f) return false;
        // Preserve the takeoff decision while flying. Sampling at the raised feet would mistake
        // a normal jump arc for a cliff and erase the freshly armed landing combination.
        if (!c.grounded) return hopClear;
        float inverseLength = 1f / (float) Math.sqrt(lengthSquared);
        float dx = wishX * inverseLength, dz = wishZ * inverseLength;
        int currentRevision = world.revision();
        float movedX = c.position.x - lastX, movedZ = c.position.z - lastZ;
        if (probeLeft <= 0f || currentRevision != revision
                || movedX * movedX + movedZ * movedZ > 1f
                || dx * lastDirectionX + dz * lastDirectionZ < .92f) {
            hopClear = corridorClear(c, world, dx, dz, .70f, Combatant.HEIGHT + 1.4f);
            probeLeft = PROBE_INTERVAL;
            revision = currentRevision;
            lastX = c.position.x; lastZ = c.position.z;
            lastDirectionX = dx; lastDirectionZ = dz;
        }
        return hopClear;
    }

    /** Called once when selecting an evasive maneuver, not continuously for every combat frame. */
    public static boolean canDodge(Combatant c, VoxelWorld world, float dx, float dz,
                                   boolean jumping) {
        return corridorClear(c, world, dx, dz, .15f,
                Combatant.HEIGHT + (jumping && c.grounded ? 1.4f : 0f));
    }

    private static boolean corridorClear(Combatant c, VoxelWorld world, float dx, float dz,
                                         float lateral, float height) {
        float sideX = -dz * lateral, sideZ = dx * lateral;
        for (int step = 0; step < 3; step++) {
            float distance = step == 0 ? 0f : step == 1 ? .9f : 2.2f;
            float x = c.position.x + dx * distance, z = c.position.z + dz * distance;
            if (!(supported(world, x, c.position.y, z)
                    || !c.grounded && world.solidAt(x, c.position.y - 2.05f, z))
                    || !bodyClear(world, x, c.position.y, z, height)
                    || !bodyClear(world, x + sideX, c.position.y, z + sideZ, height)
                    || !bodyClear(world, x - sideX, c.position.y, z - sideZ, height)) {
                return false;
            }
        }
        return true;
    }

    private static boolean supported(VoxelWorld world, float x, float y, float z) {
        // A single descending stair is valid; missing support beneath the entire lead is not.
        return world.solidAt(x, y - .05f, z) || world.solidAt(x, y - 1.05f, z);
    }

    private static boolean bodyClear(VoxelWorld world, float x, float y, float z, float height) {
        int minX = (int) Math.floor(x - Combatant.RADIUS);
        int maxX = (int) Math.floor(x + Combatant.RADIUS);
        int minZ = (int) Math.floor(z - Combatant.RADIUS);
        int maxZ = (int) Math.floor(z + Combatant.RADIUS);
        int minY = (int) Math.floor(y + .03f);
        int maxY = (int) Math.floor(y + height - .03f);
        // Keep bodies away from the edge of the volume as well as ordinary missing floors.
        if (minX < 0 || minZ < 0 || maxX >= world.sizeX() || maxZ >= world.sizeZ()
                || minY < 0 || maxY >= world.sizeY()) return false;
        for (int by = minY; by <= maxY; by++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                for (int bx = minX; bx <= maxX; bx++) {
                    if (world.solid(bx, by, bz)) return false;
                }
            }
        }
        return true;
    }
}
