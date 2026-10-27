package com.ccs.voxelstrike.game;

/** Shared jump budget for humans and bot requests. Held bhop is ground-only, never an air tap. */
public final class JumpRules {
    private JumpRules() {}
    public static boolean jump(Combatant c, float speed, boolean allowAirJump, boolean explicitPress) {
        if (!c.alive || (!c.grounded && (!allowAirJump || !explicitPress || c.airJumpUsed))) return false;
        if (!c.grounded) c.airJumpUsed = true;
        c.velocity.y = speed;
        c.grounded = false;
        return true;
    }
}
