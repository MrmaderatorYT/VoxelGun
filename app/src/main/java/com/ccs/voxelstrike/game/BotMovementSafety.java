package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.voxel.VoxelWorld;
import com.ccs.voxelstrike.voxel.Materials;

/**
 * Bounded, cached locomotion clearance. Reads authoritative Java voxels: no extra JNI sight rays,
 * path searches or allocations per bot. Physics remains authoritative for every body contact.
 */
public final class BotMovementSafety {
    private static final float PROBE_INTERVAL = .12f;
    private static final float HOP_JUMP_SPEED = 7.4f;
    private static final float HOP_SAMPLE_SPACING = .5f;
    private static final int MAX_HOP_SAMPLES = 192;
    private float probeLeft;
    private float lastX, lastY, lastZ, lastDirectionX, lastDirectionZ, lastSpeed, lastGravity;
    private int revision = -1;
    private boolean hopClear;

    public void reset() { probeLeft = 0f; revision = -1; hopClear = false; }

    /** Compatibility entry point for ordinary gravity and an unboosted movement request. */
    public boolean canHop(Combatant c, VoxelWorld world, float wishX, float wishZ, float dt) {
        float speed = Math.max((float) Math.hypot(wishX, wishZ),
                (float) Math.hypot(c.velocity.x, c.velocity.z));
        return canHop(c, world, wishX, wishZ, speed, 1f, dt);
    }

    /**
     * Predict the entire hop, not merely the next walking step. Takeoff speed includes any
     * earned bhop boost; low gravity lengthens the flight and therefore the landing corridor.
     */
    public boolean canHop(Combatant c, VoxelWorld world, float wishX, float wishZ,
                          float takeoffSpeed, float gravityScale, float dt) {
        probeLeft -= dt;
        float lengthSquared = wishX * wishX + wishZ * wishZ;
        if (lengthSquared < .001f || !Float.isFinite(takeoffSpeed)
                || !Float.isFinite(gravityScale) || gravityScale <= 0f) return false;
        // Preserve the takeoff decision while flying. Sampling at raised feet would mistake a
        // normal jump arc for a cliff and erase the freshly armed landing combination.
        if (!c.grounded) return hopClear;
        float inverseLength = 1f / (float) Math.sqrt(lengthSquared);
        float dx = wishX * inverseLength, dz = wishZ * inverseLength;
        float speed = Math.max(takeoffSpeed, (float) Math.sqrt(lengthSquared));
        int currentRevision = world.revision();
        float movedX = c.position.x - lastX, movedZ = c.position.z - lastZ;
        if (probeLeft <= 0f || currentRevision != revision
                || movedX * movedX + movedZ * movedZ > .16f || Math.abs(c.position.y - lastY) > .2f
                || dx * lastDirectionX + dz * lastDirectionZ < .98f
                || Math.abs(speed - lastSpeed) > .25f || Math.abs(gravityScale - lastGravity) > .001f) {
            hopClear = hopCorridorClear(c, world, dx, dz, speed, gravityScale);
            probeLeft = PROBE_INTERVAL;
            revision = currentRevision;
            lastX = c.position.x; lastY = c.position.y; lastZ = c.position.z;
            lastDirectionX = dx; lastDirectionZ = dz;
            lastSpeed = speed; lastGravity = gravityScale;
        }
        return hopClear;
    }

    private static boolean hopCorridorClear(Combatant c, VoxelWorld world, float dx, float dz,
                                            float speed, float gravityScale) {
        float gravity = -Physics.GRAVITY * gravityScale;
        // Allow a one-block descending step, and leave one meter for frame/cache uncertainty.
        float flightTime = (HOP_JUMP_SPEED
                + (float) Math.sqrt(HOP_JUMP_SPEED * HOP_JUMP_SPEED + 2f * gravity)) / gravity;
        float distance = Math.max(2.2f, speed * flightTime + 1f);
        if (!Float.isFinite(distance) || distance > MAX_HOP_SAMPLES * HOP_SAMPLE_SPACING) return false;
        int samples = Math.max(1, (int) Math.ceil(distance / HOP_SAMPLE_SPACING));
        float sideX = -dz * .70f, sideZ = dx * .70f;
        for (int i = 0; i <= samples; i++) {
            float forward = distance * i / samples;
            float time = Math.min(flightTime, forward / Math.max(.1f, speed));
            float rise = Math.max(0f, HOP_JUMP_SPEED * time - .5f * gravity * time * time);
            float x = c.position.x + dx * forward, z = c.position.z + dz * forward;
            float height = Combatant.HEIGHT + rise + .15f;
            // Support along the route makes a blocked/shortened jump safe too. Reject lava,
            // water and void before launching, even if the nominal landing is beyond them.
            if (!supported(world, x, c.position.y, z)
                    || !supported(world, x + sideX, c.position.y, z + sideZ)
                    || !supported(world, x - sideX, c.position.y, z - sideZ)
                    || !bodyClear(world, x, c.position.y, z, height)
                    || !bodyClear(world, x + sideX, c.position.y, z + sideZ, height)
                    || !bodyClear(world, x - sideX, c.position.y, z - sideZ, height)) return false;
        }
        return true;
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
                    || !c.grounded && safeFloor(world, x, c.position.y - 2.05f, z))
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
        return safeFloor(world, x, y - .05f, z) || safeFloor(world, x, y - 1.05f, z);
    }

    private static boolean safeFloor(VoxelWorld world, float x, float y, float z) {
        int material = world.get(VoxelWorld.floor(x), VoxelWorld.floor(y), VoxelWorld.floor(z)) & 255;
        return material != Materials.LAVA && Materials.solid(material);
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
                    int material = world.get(bx, by, bz) & 255;
                    if (Materials.solid(material) || material == (Materials.WATER & 255)) return false;
                }
            }
        }
        return true;
    }
}
