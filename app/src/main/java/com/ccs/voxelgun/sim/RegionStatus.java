package com.ccs.voxelgun.sim;

/**
 * How close the war has come to a region. Derived from the front each tick, never set by hand.
 *
 * <p>The multipliers are what the war does to a place before anyone decides to leave: shelling,
 * blackouts, staff who stopped coming in. The risk figure is what firms weigh when they decide
 * whether to.
 */
public enum RegionStatus {
    /** Far from the fighting. Normal output, and where relocating firms want to go. */
    REAR("Rear", 1.00f, 0.02f, true),
    /** Within reach of strikes. Output dips, and the clock starts ticking. */
    THREATENED("Threatened", 0.82f, 0.22f, true),
    /** Fighting on the ground. Half the economy stops; leaving is already hard. */
    CONTESTED("Contested", 0.35f, 0.65f, false),
    /** Lost. Whatever did not leave is gone from the national economy. */
    OCCUPIED("Occupied", 0.00f, 1.00f, false);

    private final String label;
    /** Multiplier on everything produced here. */
    private final float outputMultiplier;
    /** 0..1 chance-weighted expectation of losing capital left in place, per tick. */
    private final float capitalRisk;
    /** Whether a firm here can still organise a move out. */
    private final boolean canRelocateOut;

    RegionStatus(String label, float outputMultiplier, float capitalRisk, boolean canRelocateOut) {
        this.label = label;
        this.outputMultiplier = outputMultiplier;
        this.capitalRisk = capitalRisk;
        this.canRelocateOut = canRelocateOut;
    }

    public String label() {
        return label;
    }

    public float outputMultiplier() {
        return outputMultiplier;
    }

    public float capitalRisk() {
        return capitalRisk;
    }

    public boolean canRelocateOut() {
        return canRelocateOut;
    }

    /** True while the region still belongs to the national economy. */
    public boolean isHeld() {
        return this != OCCUPIED;
    }
}
