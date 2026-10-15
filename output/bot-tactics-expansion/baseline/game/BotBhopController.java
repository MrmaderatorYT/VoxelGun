package com.ccs.voxelgun.game;

import com.ccs.voxelgun.input.InputState;
import com.ccs.voxelgun.voxel.VoxelWorld;

/** Synthetic fresh inputs, using exactly the player's timing, speed reward and jump physics. */
public final class BotBhopController {
    private final ArenaMovement movement = new ArenaMovement();
    private final InputState input = new InputState();
    private final BotMovementSafety safety = new BotMovementSafety();
    private final float landingReaction;
    private float airTime, landingWait, restartWait;
    private boolean wasAirborne;

    public BotBhopController(float skill, int id) {
        landingReaction = .025f + (1f - Math.max(0f, Math.min(1f, skill))) * .07f + id % 3 * .008f;
    }

    public void reset() {
        movement.reset(); input.releaseAll(); input.crouching = false;
        safety.reset();
        airTime = landingWait = 0f; restartWait = .20f; wasAirborne = false;
    }

    public void update(Combatant c, VoxelWorld world, float wishX, float wishZ,
                       float referenceSpeed, boolean travel, float dt) {
        float wishSpeed = (float) Math.sqrt(wishX * wishX + wishZ * wishZ);
        boolean wantsHop = travel && !c.aiming && wishSpeed > referenceSpeed * .35f
                && (!c.grounded || safety.canHop(c, world, wishX, wishZ, dt));
        // A bot spawned/falling in air may prepare crouch, but never jump there. Clearance is
        // checked at the actual landing before any rewarded takeoff, not at raised feet.
        restartWait = Math.max(0f, restartWait - dt);
        if (!wantsHop) {
            movement.reset(); input.crouching = false; input.setDeviceJump(false);
            airTime = landingWait = 0f; wasAirborne = false;
        } else if (!c.grounded) {
            input.setDeviceJump(false); // Release before the next fresh airborne crouch edge.
            airTime += dt;
            if (airTime >= .06f) input.crouching = true;
            wasAirborne = true;
        } else {
            if (wasAirborne) landingWait = landingReaction;
            wasAirborne = false;
            landingWait = Math.max(0f, landingWait - dt);
        }
        movement.update(c, input, wishX, wishZ, referenceSpeed, true, dt);
        if (wantsHop) c.crouching = input.crouching;
        if (!c.grounded || !wantsHop || landingWait > 0f
                || (!movement.hopReady() && restartWait > 0f)) return;
        float x = wishX / wishSpeed, z = wishZ / wishSpeed;
        // A standing takeoff needs real headroom; do not bunny-hop into a low tunnel/prop.
        if (world.boxCollides(c.position.x, c.position.y, c.position.z,
                Combatant.RADIUS, Combatant.HEIGHT + 1.4f)
                || world.boxCollides(c.position.x + x * 1.4f, c.position.y + .1f,
                c.position.z + z * 1.4f, Combatant.RADIUS, Combatant.HEIGHT + 1.2f)) {
            restartWait = .25f; return;
        }
        boolean timed = movement.hopReady();
        c.crouching = false;
        if (JumpRules.jump(c, 7.4f, false, true)) {
            movement.onJump(c, timed);
            input.setDeviceJump(true); input.crouching = false;
            airTime = 0f; restartWait = .20f;
        }
    }
}
