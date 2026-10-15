package com.ccs.voxelgun.game;

import com.ccs.voxelgun.input.InputState;
import com.ccs.voxelgun.math.Vec3;
import com.ccs.voxelgun.voxel.VoxelWorld;

/** Finite threat-response maneuvers; no omniscient enemy scans or random per-frame jump rolls. */
public final class BotEvasionController {
    private final Difficulty difficulty;
    private final BotCombatStyle style;
    private final ArenaMovement movement = new ArenaMovement();
    private final InputState input = new InputState();
    private float reactionLeft, cooldown, activeLeft, sprintLeft;
    private float lastDamageAge = 100f;
    private float directionX, directionZ;
    private boolean pending, needsSelection, jumping;

    public BotEvasionController(Difficulty difficulty, BotCombatStyle style) {
        this.difficulty = difficulty;
        this.style = style;
    }

    public void reset() {
        reactionLeft = cooldown = activeLeft = sprintLeft = 0f;
        lastDamageAge = 100f;
        pending = needsSelection = jumping = false;
        movement.reset(); input.releaseAll(); input.crouching = false;
    }

    public boolean active() { return activeLeft > 0f; }
    public boolean sliding() { return active() && !jumping && sprintLeft <= 0f; }

    /** Existing perceived target only: walls and field-of-view remain owned by BotBrain. */
    public void observe(Combatant self, Combatant target, float dt) {
        cooldown = Math.max(0f, cooldown - dt);
        activeLeft = Math.max(0f, activeLeft - dt);
        boolean newDamage = self.lastDamageTime < .30f
                && self.lastDamageTime + .0001f < lastDamageAge;
        lastDamageAge = self.lastDamageTime;
        boolean precision = style == BotCombatStyle.SNIPER;
        if (!self.alive || self.zombie || precision) {
            activeLeft = 0f; pending = false; return;
        }
        if (active()) return;
        if (pending) {
            reactionLeft -= dt;
            if (reactionLeft <= 0f) {
                pending = false;
                activeLeft = .68f;
                sprintLeft = .10f;
                needsSelection = true;
                movement.reset(); input.releaseAll(); input.crouching = false;
            }
            return;
        }
        if (cooldown > 0f || !(newDamage || incomingShot(self, target))) return;
        pending = true;
        reactionLeft = difficulty.movementReactionSeconds() * style.reactionFactor();
        cooldown = difficulty.evasionCooldownSeconds();
    }

    private static boolean incomingShot(Combatant self, Combatant target) {
        if (target == null || !target.alive || (!target.firing && target.muzzleFlash < .05f)) {
            return false;
        }
        float dx = self.position.x - target.position.x;
        float dz = self.position.z - target.position.z;
        float distanceSquared = dx * dx + dz * dz;
        if (distanceSquared > 34f * 34f || distanceSquared < .01f) return false;
        float dot = dx * (float) Math.sin(target.yaw) - dz * (float) Math.cos(target.yaw);
        return dot > 0f && dot * dot > distanceSquared * .94f;
    }

    /**
     * Writes a side-step wish and returns a single explicit ground-jump request. Non-bhop slides
     * use the exact same sprint threshold, crouch edge, friction and speed cap as the player.
     */
    public boolean steer(Combatant self, VoxelWorld world, Vec3 wish, float referenceSpeed,
                         boolean bhop, float dt) {
        if (!active()) return false;
        boolean requestJump = false;
        if (needsSelection) {
            needsSelection = false;
            float side = (self.id & 1) == 0 ? 1f : -1f;
            float forwardX = (float) Math.sin(self.yaw), forwardZ = -(float) Math.cos(self.yaw);
            float advance = style == BotCombatStyle.RUSHER ? .28f : -.22f;
            directionX = -forwardZ * side + forwardX * advance;
            directionZ = forwardX * side + forwardZ * advance;
            float inverseLength = 1f / (float) Math.hypot(directionX, directionZ);
            directionX *= inverseLength; directionZ *= inverseLength;
            jumping = !self.grounded || (style == BotCombatStyle.RUSHER && self.id % 3 == 0);
            // Try the other side once; never spend a path search on a brief dodge.
            if (!BotMovementSafety.canDodge(self, world, directionX, directionZ, jumping)) {
                directionX = -directionX; directionZ = -directionZ;
                if (!BotMovementSafety.canDodge(self, world, directionX, directionZ, jumping)) {
                    activeLeft = 0f; self.crouching = false; return false;
                }
            }
            requestJump = jumping && self.grounded;
        }
        wish.set(directionX * referenceSpeed * .90f, 0f,
                directionZ * referenceSpeed * .90f);
        self.aiming = false;
        self.sprinting = !jumping && sprintLeft > 0f;
        if (!bhop) {
            sprintLeft = Math.max(0f, sprintLeft - dt);
            input.sprinting = true;
            input.crouching = !jumping && sprintLeft <= 0f && self.grounded;
            movement.update(self, input, wish.x, wish.z, referenceSpeed, false, dt);
            self.crouching = input.crouching;
        }
        return requestJump;
    }
}
