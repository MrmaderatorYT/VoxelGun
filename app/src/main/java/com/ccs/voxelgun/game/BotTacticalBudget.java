package com.ccs.voxelgun.game;

/** Shared cap for optional position/cover sight queries, separate from essential perception. */
public final class BotTacticalBudget {
    private int remaining = 8;
    private boolean recoveryAvailable = true;
    public void beginStep(int maximum) { remaining = Math.max(0, maximum); recoveryAvailable = true; }
    public boolean take() { if (remaining <= 0) return false; remaining--; return true; }
    public boolean takeRecovery() {
        if (!recoveryAvailable) return false;
        recoveryAvailable = false; return true;
    }
    public int remaining() { return remaining; }
}
