package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.math.Vec3;
import com.ccs.voxelstrike.voxel.VoxelWorld;

/** Axis-separated voxel collision with automatic step-up, shared by players and bots. */
public final class Physics {
    public static final float GRAVITY = -22f;
    public static final float TERMINAL_VELOCITY = -46f;
    public static final float STEP_HEIGHT = 1.02f;
    private static final float MAX_SUBSTEP = 0.20f;

    private Physics() {}

    /**
     * Integrates one movement step.
     *
     * @return true when the body ended the step standing on something
     */
    public static boolean move(VoxelWorld world, Vec3 position, Vec3 velocity, float dt,
                               float radius, float height, boolean allowStepUp) {
        float dx = velocity.x * dt;
        float dy = velocity.y * dt;
        float dz = velocity.z * dt;

        float longest = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        int steps = Math.max(1, (int) Math.ceil(longest / MAX_SUBSTEP));
        float stepX = dx / steps;
        float stepY = dy / steps;
        float stepZ = dz / steps;

        boolean grounded = false;
        for (int i = 0; i < steps; i++) {
            grounded |= axis(world, position, velocity, stepX, 0f, 0f, radius, height, allowStepUp);
            grounded |= axis(world, position, velocity, 0f, 0f, stepZ, radius, height, allowStepUp);
            grounded |= axis(world, position, velocity, 0f, stepY, 0f, radius, height, false);
        }
        return grounded;
    }

    /** Moves along one axis; vertical contacts resolve to the voxel face, not the previous pose. */
    private static boolean axis(VoxelWorld world, Vec3 position, Vec3 velocity, float dx, float dy,
                                float dz, float radius, float height, boolean allowStepUp) {
        if (dx == 0f && dy == 0f && dz == 0f) {
            return false;
        }
        position.x += dx;
        position.y += dy;
        position.z += dz;
        if (!world.boxCollides(position.x, position.y, position.z, radius, height)) {
            return false;
        }

        if (allowStepUp && (dx != 0f || dz != 0f) && velocity.y <= 0f
                && onGround(world, position.x - dx, position.y, position.z - dz, radius)) {
            float previousY = position.y;
            // Voxels have whole-height faces: settle on the next face, not a hovering
            // arbitrary offset. Only a supported body may step; air jumps do not climb walls.
            float ledgeY = (float) Math.floor(previousY + 0.001f) + 1f;
            if (ledgeY - previousY <= STEP_HEIGHT
                    && !world.boxCollides(position.x, ledgeY, position.z, radius, height)) {
                position.y = ledgeY;
                return true;
            }
            position.y = previousY;
        }

        position.x -= dx;
        position.z -= dz;
        if (dx != 0f) {
            velocity.x = 0f;
        }
        if (dz != 0f) {
            velocity.z = 0f;
        }
        if (dy != 0f) {
            boolean landed = dy < 0f;
            // Substeps are smaller than one voxel, so only the leading Y plane can enter
            // a new solid layer. Rolling the whole step back leaves a gap and makes
            // grounded flicker on subsequent gravity ticks (breaking timed jumps).
            position.y = landed ? (float) Math.ceil(position.y)
                    : (float) Math.floor(position.y + height) - height - 0.0001f;
            velocity.y = 0f;
            return landed;
        }
        return false;
    }

    private static boolean onGround(VoxelWorld world, float x, float y, float z, float radius) {
        return world.boxCollides(x, y - .08f, z, radius, .05f);
    }

    /** True when there is solid ground within {@code tolerance} below the body. */
    public static boolean onGround(VoxelWorld world, Vec3 position, float radius, float tolerance) {
        return world.boxCollides(position.x, position.y - tolerance, position.z, radius, 0.05f);
    }

    /**
     * Pushes a body out of geometry it spawned inside of.
     *
     * <p>Searches nearby columns first and only then lifts, so a spawn placed inside a pillar
     * steps aside instead of ending up on the roof.
     */
    public static void resolveSpawn(VoxelWorld world, Vec3 position, float radius, float height) {
        if (!world.boxCollides(position.x, position.y, position.z, radius, height)) {
            return;
        }
        float originX = position.x;
        float originZ = position.z;
        float originY = position.y;
        for (int ring = 1; ring <= 6; ring++) {
            for (int step = 0; step < 8; step++) {
                double angle = step * Math.PI / 4.0;
                float x = originX + (float) Math.cos(angle) * ring;
                float z = originZ + (float) Math.sin(angle) * ring;
                for (float lift = 0f; lift <= 4f; lift += 1f) {
                    if (!world.boxCollides(x, originY + lift, z, radius, height)) {
                        position.set(x, originY + lift, z);
                        return;
                    }
                }
            }
        }
        // Nothing nearby is clear; rise straight up as a last resort.
        for (int attempt = 0; attempt < 32; attempt++) {
            position.y += 1f;
            if (!world.boxCollides(position.x, position.y, position.z, radius, height)) {
                return;
            }
        }
    }
}
