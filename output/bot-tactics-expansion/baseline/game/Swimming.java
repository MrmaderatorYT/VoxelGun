package com.ccs.voxelgun.game;

import com.ccs.voxelgun.math.MathUtil;
import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.voxel.VoxelWorld;

/** Shared allocation-free water locomotion. Physics still owns every collision. */
public final class Swimming {
    public static final float MOVE_SPEED = 4.2f;
    public static final float RISE_SPEED = 4f;
    /** Same launch speed as a normal jump: enough to clear the one-voxel pool rim. */
    private static final float SURFACE_EXIT_SPEED = 7.4f;
    private static final float SINK_SPEED = -1.4f;

    private Swimming() {}

    /** Checks the occupied body, not neighbouring water or water below dry feet. */
    public static boolean inWater(VoxelWorld world, Combatant c) {
        return wetLayer(world, c) >= 0;
    }

    private static int wetLayer(VoxelWorld world, Combatant c) {
        int x0 = VoxelWorld.floor(c.position.x - Combatant.RADIUS);
        int x1 = VoxelWorld.floor(c.position.x + Combatant.RADIUS);
        int z0 = VoxelWorld.floor(c.position.z - Combatant.RADIUS);
        int z1 = VoxelWorld.floor(c.position.z + Combatant.RADIUS);
        int y0 = Math.max(0, VoxelWorld.floor(c.position.y + .02f));
        int y1 = Math.min(world.sizeY() - 1,
                VoxelWorld.floor(c.position.y + c.height() - .02f));
        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) {
                if (world.get(x, y, z) == Materials.WATER) return y;
            }
        }
        return -1;
    }

    /** Local top of the contiguous water touching the body, or -1 on dry land. */
    public static int surface(VoxelWorld world, Combatant c) {
        int y = wetLayer(world, c);
        if (y < 0) return -1;
        int highest = -1;
        int x0 = VoxelWorld.floor(c.position.x - Combatant.RADIUS);
        int x1 = VoxelWorld.floor(c.position.x + Combatant.RADIUS);
        int z0 = VoxelWorld.floor(c.position.z - Combatant.RADIUS);
        int z1 = VoxelWorld.floor(c.position.z + Combatant.RADIUS);
        for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) {
            if (world.get(x, y, z) != Materials.WATER) continue;
            int top = y;
            while (top < world.sizeY() && world.get(x, top, z) == Materials.WATER) top++;
            highest = Math.max(highest, top);
        }
        return highest;
    }

    /** Returns whether water replaced gravity this step. A released Jump does not keep boosting. */
    public static boolean applyVertical(VoxelWorld world, Combatant c, boolean rise, float dt) {
        int surface = surface(world, c);
        if (surface < 0) return false;
        if (rise) {
            c.velocity.y = c.position.y >= surface - .45f ? SURFACE_EXIT_SPEED : RISE_SPEED;
        } else {
            c.velocity.y = MathUtil.damp(Math.min(0f, c.velocity.y), SINK_SPEED, 5f, dt);
            c.velocity.y = Math.max(-2.5f, c.velocity.y);
        }
        return true;
    }

    /** Water drag removes slide/bhop momentum without allocating input or temporary vectors. */
    public static void steer(Combatant c, float wishX, float wishZ, float dt) {
        float speed = (float)Math.sqrt(wishX * wishX + wishZ * wishZ);
        if (speed > MOVE_SPEED) { wishX *= MOVE_SPEED / speed; wishZ *= MOVE_SPEED / speed; }
        c.velocity.x = MathUtil.damp(c.velocity.x, wishX, 12f, dt);
        c.velocity.z = MathUtil.damp(c.velocity.z, wishZ, 12f, dt);
        speed = (float)Math.hypot(c.velocity.x, c.velocity.z);
        if (speed > MOVE_SPEED) {
            c.velocity.x *= MOVE_SPEED / speed; c.velocity.z *= MOVE_SPEED / speed;
        }
        c.sprinting = false;
    }
}
