package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.math.Vec3;
import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.voxel.VoxelWorld;

/**
 * Rare fallback for a body stranded on a small ledge outside the walkable navigation layer.
 * Tries at most 24 short, two-leg walks with the actual collision integrator and a confirmed dry
 * landing no more than four blocks below. Planning and execution never move the real position.
 */
public final class BotLocalRecovery {
    public static final float SPEED = 2.4f;
    public static final float MAX_DROP = 4f;
    private static final float DT = 1f / 60f;
    private static final int FIRST_STEPS = 28, SECOND_STEPS = 72;
    private static final float FIRST_SECONDS = FIRST_STEPS * DT;
    private static final int[] HEADING_ORDER = {0, 1, -1, 2, -2, 3, -3, 4};
    private final Vec3 simulatedPosition = new Vec3(), simulatedVelocity = new Vec3();
    private float firstX, firstZ, secondX, secondZ, landingX, landingY, landingZ;
    private float elapsed, duration;
    private int checkedRevision;
    private boolean active;

    public boolean active() { return active; }
    public void reset() { active = false; elapsed = duration = 0f; }

    /** Call only after ordinary supported sidesteps failed; the body must have landed first. */
    public boolean begin(Combatant c, VoxelWorld world, float gravityMultiplier,
                         float preferredX, float preferredZ) {
        reset();
        if (!c.grounded || Swimming.inWater(world, c)
                || !Float.isFinite(gravityMultiplier) || gravityMultiplier <= 0f
                || world.boxCollides(c.position.x, c.position.y, c.position.z,
                        Combatant.RADIUS, Combatant.HEIGHT)) return false;
        float heading = preferredX * preferredX + preferredZ * preferredZ > .001f
                ? (float) Math.atan2(preferredZ, preferredX) : c.yaw - (float) Math.PI / 2f;
        for (int direction : HEADING_ORDER) {
            float angle = heading + direction * (float) Math.PI / 4f;
            float dx = (float) Math.cos(angle), dz = (float) Math.sin(angle);
            for (int turn = 0; turn < 3; turn++) {
                float nextX = turn == 0 ? dx : turn == 1 ? -dz : dz;
                float nextZ = turn == 0 ? dz : turn == 1 ? dx : -dx;
                if (simulate(c, world, gravityMultiplier, dx, dz, nextX, nextZ)) {
                    firstX = dx; firstZ = dz; secondX = nextX; secondZ = nextZ;
                    elapsed = 0f;
                    checkedRevision = world.revision();
                    active = true;
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Writes only the requested horizontal velocity. Match still applies gravity and collisions.
     * The caller must suppress jumping/evasion while active and capture this velocity as intent.
     */
    public boolean update(Combatant c, VoxelWorld world, float dt) {
        if (!active) return false;
        if (!c.alive || dt <= 0f || !Float.isFinite(dt)) { reset(); return false; }
        int revision = world.revision();
        if (revision != checkedRevision) {
            checkedRevision = revision;
            if (!landingSafe(world, landingX, landingY, landingZ)) {
                reset(); c.velocity.x = c.velocity.z = 0f; return false;
            }
        }
        float dx = c.position.x - landingX, dz = c.position.z - landingZ;
        if ((elapsed >= FIRST_SECONDS && c.grounded
                && Math.abs(c.position.y - landingY) < .2f && dx * dx + dz * dz < .20f
                && landingSafe(world, c.position.x, c.position.y, c.position.z))
                || elapsed > duration + .35f) {
            reset(); c.velocity.x = c.velocity.z = 0f; return false;
        }
        boolean first = elapsed < FIRST_SECONDS;
        c.velocity.x = (first ? firstX : secondX) * SPEED;
        c.velocity.z = (first ? firstZ : secondZ) * SPEED;
        c.crouching = c.sprinting = false;
        elapsed += dt;
        return true;
    }

    private boolean simulate(Combatant c, VoxelWorld world, float gravityMultiplier,
                              float dx, float dz, float nextX, float nextZ) {
        simulatedPosition.set(c.position);
        simulatedVelocity.set(0f, 0f, 0f);
        float blocked = 0f;
        for (int step = 0; step < FIRST_STEPS + SECOND_STEPS; step++) {
            boolean first = step < FIRST_STEPS;
            simulatedVelocity.x = (first ? dx : nextX) * SPEED;
            simulatedVelocity.z = (first ? dz : nextZ) * SPEED;
            simulatedVelocity.y = Math.max(Physics.TERMINAL_VELOCITY,
                    simulatedVelocity.y + Physics.GRAVITY * gravityMultiplier * DT);
            float previousX = simulatedPosition.x, previousZ = simulatedPosition.z;
            boolean grounded = Physics.move(world, simulatedPosition, simulatedVelocity, DT,
                    Combatant.RADIUS, Combatant.HEIGHT, true);
            if (simulatedPosition.y < c.position.y - MAX_DROP
                    || simulatedPosition.y > c.position.y + Physics.STEP_HEIGHT + .05f
                    || touchesWater(world, simulatedPosition)
                    || world.boxCollides(simulatedPosition.x, simulatedPosition.y,
                            simulatedPosition.z, Combatant.RADIUS, Combatant.HEIGHT)) return false;
            int support = world.get(VoxelWorld.floor(simulatedPosition.x),
                    VoxelWorld.floor(simulatedPosition.y - .05f), VoxelWorld.floor(simulatedPosition.z));
            if (grounded && (support == Materials.LAVA || support == Materials.JUMP_PAD)) return false;
            float movedX = simulatedPosition.x - previousX;
            float movedZ = simulatedPosition.z - previousZ;
            blocked = movedX * movedX + movedZ * movedZ < .000025f ? blocked + DT : 0f;
            if (blocked > .2f) return false;
            float progressX = simulatedPosition.x - c.position.x;
            float progressZ = simulatedPosition.z - c.position.z;
            if (!first && grounded && progressX * progressX + progressZ * progressZ >= 1.44f
                    && landingSafe(world, simulatedPosition.x, simulatedPosition.y,
                            simulatedPosition.z)) {
                landingX = simulatedPosition.x; landingY = simulatedPosition.y;
                landingZ = simulatedPosition.z;
                duration = (step + 1) * DT;
                return true;
            }
        }
        return false;
    }

    private static boolean landingSafe(VoxelWorld world, float x, float y, float z) {
        int bx = VoxelWorld.floor(x), by = VoxelWorld.floor(y - .05f), bz = VoxelWorld.floor(z);
        return world.inside(bx, by, bz) && world.solid(bx, by, bz)
                && world.get(bx, by, bz) != Materials.LAVA
                && world.get(bx, by, bz) != Materials.JUMP_PAD
                && !world.boxCollides(x, y, z, Combatant.RADIUS, Combatant.HEIGHT)
                && world.get(bx, VoxelWorld.floor(y + .02f), bz) != Materials.WATER
                && world.get(bx, VoxelWorld.floor(y + 1f), bz) != Materials.WATER;
    }

    /** Swimming and jump-pad impulses would invalidate an ordinary gravity-only trajectory. */
    private static boolean touchesWater(VoxelWorld world, Vec3 position) {
        for (int x = VoxelWorld.floor(position.x - Combatant.RADIUS);
             x <= VoxelWorld.floor(position.x + Combatant.RADIUS); x++)
            for (int z = VoxelWorld.floor(position.z - Combatant.RADIUS);
                 z <= VoxelWorld.floor(position.z + Combatant.RADIUS); z++)
                for (int y = VoxelWorld.floor(position.y + .02f);
                     y <= VoxelWorld.floor(position.y + Combatant.HEIGHT - .02f); y++)
                    if (world.get(x, y, z) == Materials.WATER) return true;
        return false;
    }
}
