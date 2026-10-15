package com.ccs.voxelgun.sim;

/**
 * One tick's worth of national figures: the row the HUD reads and the log writes.
 *
 * <p>These are the numbers the thesis is about, so they are collected whether or not anyone is
 * watching, and they are all stocks or flows for the tick just finished — nothing here is smoothed.
 */
public final class SimMetrics {
    public int tick;
    /** Output produced this tick inside the country, at constant prices. */
    public double gdp;
    /** Output at the first tick, so everything can be read as a share of pre-war capacity. */
    public double gdpBaseline;
    /** Capital still operating inside the country. */
    public double capitalDomestic;
    /** Capital that made it out of the country and now produces for someone else. */
    public double capitalAbroad;
    /** Capital destroyed: shelled, captured, or burned in the course of moving. */
    public double capitalLost;
    /** Capital that has relocated inland at least once and is still here. */
    public double capitalRelocatedInland;

    public int firmsOperating;
    public int firmsRelocating;
    public int firmsAbroad;
    public int firmsLost;
    /** Firms that started a move this tick, split by where they went. */
    public int movesStartedInland;
    public int movesStartedAbroad;

    public double treasury;
    public double revenue;
    public double spending;
    public double defenceSpend;
    public double relocationSpend;
    public double grantCoverage;
    public double priceLevel;
    public double defenceStrength;

    public float frontAdvanceKm;
    public float frontMovementKm;
    public int regionsOccupied;
    public int regionsContested;
    public int regionsThreatened;

    public double laborMobilised;
    /**
     * Unemployment among people still in held regions. It says nothing about the displaced, who
     * leave the denominator with their region — read it next to {@link #laborDisplaced}.
     */
    public double unemployment;
    /** People who left a threatened region or were caught in an occupied one, cumulatively. */
    public double laborDisplaced;
    /** Workforce still living in regions the country holds. */
    public double laborHeld;
    /** Share of surviving capital sitting in regions with no special economic zone. */
    public double capitalShareOutsideZones;

    /** GDP as a share of the first tick's, the headline figure for every chart. */
    public double gdpIndex() {
        return gdpBaseline <= 0.0 ? 0.0 : gdp / gdpBaseline;
    }

    /** Share of all capital that left the country: the number the policy is trying to minimise. */
    public double abroadShare() {
        double total = capitalDomestic + capitalAbroad + capitalLost;
        return total <= 0.0 ? 0.0 : capitalAbroad / total;
    }
}
