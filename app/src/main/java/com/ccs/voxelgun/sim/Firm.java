package com.ccs.voxelgun.sim;

import java.util.List;
import java.util.Random;

/**
 * A single business, and the only agent in the model that makes a decision.
 *
 * <p>Every tick a firm compares three futures: stay where it is and accept the risk, move inland to
 * the best region it can find, or leave the country. It does this on a lagged, noisy view of the
 * danger — that lag is deliberate, and is where the interesting results come from, because firms
 * that read the front correctly leave in time and the rest do not.
 */
public final class Firm {
    public enum State {
        /** Producing. */
        OPERATING,
        /** Between two regions: producing nothing, paying no tax. */
        RELOCATING,
        /** Gone abroad. Still alive, but no longer part of the national economy. */
        ABROAD,
        /** Capital destroyed or captured. */
        LOST
    }

    public final int id;
    public final Sector sector;
    /** Where it started. Kept so the log can tell displaced firms from native ones. */
    public final Region origin;
    /** Where it is now. Null once {@link State#ABROAD}. */
    public Region region;
    public State state = State.OPERATING;
    /** Money units of productive capital. Shrinks when moving and when shelled. */
    public double capital;

    /** 0..1. How far above break-even the gain must be before this owner acts. */
    public final float inertia;
    /** 0..1. High means the owner discounts danger and stays too long. */
    public final float riskTolerance;
    /** The firm's own lagged belief about local danger, 0..1. */
    public float perceivedRisk;

    /** Ticks left in the current move. */
    public int moveTicksLeft;
    public Region destination;
    /** True while the pending move is out of the country. */
    public boolean leavingCountry;
    /** Tick on which this firm last started a move; -1 if it never has. */
    public int lastMoveTick = -1;
    /** Capital destroyed by this firm's moves, cumulatively. */
    public double relocationLosses;
    /** Relocation grant received, cumulatively. */
    public double grantsReceived;

    public Firm(int id, Sector sector, Region origin, double capital, float inertia,
                float riskTolerance) {
        this.id = id;
        this.sector = sector;
        this.origin = origin;
        this.region = origin;
        this.capital = capital;
        this.inertia = inertia;
        this.riskTolerance = riskTolerance;
    }

    public boolean producing() {
        return state == State.OPERATING && region != null && region.status.isHeld();
    }

    /** Output this tick, before national-level effects. */
    public double output() {
        if (!producing()) {
            return 0.0;
        }
        return capital * region.productivity(sector) * region.status.outputMultiplier()
                * region.laborAvailability();
    }

    public double laborDemand() {
        return state == State.OPERATING && region != null
                ? capital * sector.laborPerCapital() : 0.0;
    }

    /**
     * Updates the owner's belief about the danger to capital left standing here, expressed as a
     * hazard per tick.
     *
     * <p>Beliefs trail reality by design: an owner two hundred kilometres from the fighting does
     * not feel it the week the line starts moving, and that lag is where firms that leave too late
     * come from.
     */
    void perceiveRisk(float awareness, Random random) {
        if (region == null) {
            perceivedRisk = 0f;
            return;
        }
        float actual = hazardOf(region.status.capitalRisk(), region.frontDistanceKm);
        float noisy = actual * (0.85f + 0.3f * random.nextFloat());
        float blend = Math.min(1f, awareness * (0.35f + 0.65f * (1f - inertia)));
        perceivedRisk += (clamp01(noisy) - perceivedRisk) * blend;
        perceivedRisk = clamp01(perceivedRisk);
    }

    /**
     * Per-tick chance of losing capital in a place: shelling now, plus the prospect of the line
     * arriving. The second term dominates, which is why distance from the front drives the model.
     */
    static float hazardOf(float statusRisk, float frontDistanceKm) {
        float attrition = statusRisk * 0.06f;
        float overrun = proximityPressure(frontDistanceKm) / 12f;
        return clamp01(attrition + overrun);
    }

    /** Danger bleeding backwards from the front line, independent of the region's status. */
    static float proximityPressure(float frontDistanceKm) {
        if (frontDistanceKm <= 0f) {
            return 1f;
        }
        if (frontDistanceKm >= 400f) {
            return 0f;
        }
        return (400f - frontDistanceKm) / 400f * 0.55f;
    }

    /** Expected ticks of production surviving a constant hazard over a horizon. */
    static double survivalSum(double hazard, double horizon) {
        if (horizon <= 0.0) {
            return 0.0;
        }
        if (hazard <= 1e-6) {
            return horizon;
        }
        double keep = 1.0 - hazard;
        return keep * (1.0 - Math.pow(keep, horizon)) / hazard;
    }

    /**
     * The decision: stay, move inland, or leave the country, valued over the same horizon.
     *
     * <p>Each option is worth the output it is expected to produce while it survives, plus whatever
     * capital is left at the end of the horizon, minus what the move itself destroys. A grant does
     * not change the destination's prospects — it only makes getting there cheaper, which is
     * exactly how a relocation subsidy works.
     */
    Decision decide(List<Region> candidates, SimConfig config, double grantCoverage,
                    Random random) {
        if (state != State.OPERATING || region == null || !region.status.canRelocateOut()) {
            return null;
        }
        if (sector.mobility() <= 0.001f) {
            return null;
        }
        double horizon = config.decisionHorizonTicks;
        double fear = 1.3 - 0.6 * riskTolerance;
        double stayHazard = Math.min(0.95, perceivedRisk * fear);
        double stayValue = capital * region.productivity(sector) * survivalSum(stayHazard, horizon)
                + capital * Math.pow(1.0 - stayHazard, horizon);

        Region best = null;
        double bestValue = Double.NEGATIVE_INFINITY;
        double relief = grantCoverage * config.grantEffectiveness;
        for (Region target : candidates) {
            if (target == region || !target.status.isHeld()) {
                continue;
            }
            double netCost = capital * sector.domesticMoveCostShare() * (1.0 - relief);
            double remaining = Math.max(0.0, capital - netCost);
            double downtime = sector.relocationTicks();
            double hazard = Math.min(0.95, hazardOf(target.status.capitalRisk(),
                    target.frontDistanceKm) * fear);
            double value = remaining * target.productivity(sector)
                    * survivalSum(hazard, Math.max(0.0, horizon - downtime))
                    + remaining * Math.pow(1.0 - hazard, horizon)
                    + capital * target.zone().relocationAttraction() * config.zonePullWeight;
            if (value > bestValue) {
                bestValue = value;
                best = target;
            }
        }

        double abroadRemaining = capital * (1.0 - sector.abroadMoveCostShare());
        double abroadValue = abroadRemaining * sector.outputPerCapital() * sector.abroadRetention()
                * config.abroadProductivityFactor
                * Math.max(0.0, horizon - sector.relocationTicks())
                + abroadRemaining
                - capital * config.homeBias;

        double threshold = stayValue * (1.0 + inertia * config.inertiaWeight);
        boolean domesticWins = best != null && bestValue >= abroadValue;
        double chosenValue = domesticWins ? bestValue : abroadValue;
        if (chosenValue <= threshold) {
            return null;
        }
        // Even a decided owner does not act the same week: paperwork, trucks, staff.
        if (random.nextFloat() > config.actionRatePerTick * (0.4f + 0.6f * sector.mobility())) {
            return null;
        }
        return domesticWins ? new Decision(best, false) : new Decision(null, true);
    }

    static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /** Where a firm decided to go. */
    record Decision(Region target, boolean abroad) {}
}
