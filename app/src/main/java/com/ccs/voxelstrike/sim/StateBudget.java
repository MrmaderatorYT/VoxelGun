package com.ccs.voxelstrike.sim;

/**
 * The player's side of the model: what the state taxes, prints, mobilises and spends it on.
 *
 * <p>Everything here can be changed at any moment, including while the simulation is paused, and
 * takes effect from the next tick. The four spending shares are weights, not percentages: they are
 * normalised when the tick spends.
 */
public final class StateBudget {
    public double treasury;
    /** Share of taxable output collected, before zone and holiday relief. */
    public double taxRate;
    /** Money printed per tick as a share of the last tick's output. Buys spending, costs prices. */
    public double emissionRate;
    /** Share of the workforce called up, 0..1. Buys defence, costs output. */
    public double mobilisationRate;

    public double defenceShare = 0.55;
    public double relocationShare = 0.15;
    public double infrastructureShare = 0.15;
    public double socialShare = 0.15;

    /** Defence bought with money. Decays, so it has to be paid for continuously. */
    public double defenceStrength;
    /** Defence from the mobilised army. A level, not a flow: it does not decay while under arms. */
    public double armyStrength;
    /** Price level, 1.0 at the start of the war. */
    public double priceLevel = 1.0;

    // Last tick's actuals, for the HUD and the log.
    public double lastRevenue;
    public double lastSpending;
    public double lastDefenceSpend;
    public double lastRelocationSpend;
    public double lastInfrastructureSpend;
    public double lastSocialSpend;
    public double lastEmission;
    /** Share of firms' move costs the relocation budget covered last tick, 0..1. */
    public double lastGrantCoverage;

    public StateBudget(SimConfig config) {
        this.treasury = config.startingTreasury;
        this.taxRate = config.baseTaxRate;
    }

    /** What actually stands in the enemy's way: equipment bought plus people under arms. */
    public double totalDefence() {
        return defenceStrength + armyStrength;
    }

    /** Normalises the four shares so they sum to 1; called by the tick before spending. */
    void normalise() {
        defenceShare = Math.max(0.0, defenceShare);
        relocationShare = Math.max(0.0, relocationShare);
        infrastructureShare = Math.max(0.0, infrastructureShare);
        socialShare = Math.max(0.0, socialShare);
        double sum = defenceShare + relocationShare + infrastructureShare + socialShare;
        if (sum <= 0.0) {
            defenceShare = relocationShare = infrastructureShare = socialShare = 0.25;
            return;
        }
        defenceShare /= sum;
        relocationShare /= sum;
        infrastructureShare /= sum;
        socialShare /= sum;
    }

    /** Sets the four shares in one call; they are normalised, so any positive scale works. */
    public void allocate(double defence, double relocation, double infrastructure, double social) {
        this.defenceShare = defence;
        this.relocationShare = relocation;
        this.infrastructureShare = infrastructure;
        this.socialShare = social;
        normalise();
    }
}
