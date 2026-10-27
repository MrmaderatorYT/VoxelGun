package com.ccs.voxelstrike.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * One administrative region: the place firms sit in, the front advances through, and the budget
 * pays for.
 *
 * <p>A region may or may not have a special economic zone. {@link EconomicZone#NONE} means it has
 * none, and that is the ordinary case: zones are a policy, and policy is scarce.
 */
public final class Region {
    public final int id;
    public final String name;
    /** May be {@link EconomicZone#NONE}. Mutable: the state can create or revoke a zone. */
    private EconomicZone zone;
    /** Kilometres from this region to the hostile border at the start of the war. */
    public final float borderDistanceKm;
    /** Working-age people, in thousands. Falls as people are mobilised or displaced. */
    public float labor;
    /** Roads, power and water, 0..1. Damaged by fighting, repaired by the budget. */
    public float infrastructure;
    /** Set every tick from the front. */
    public RegionStatus status = RegionStatus.REAR;
    /** Kilometres between this region and the fighting. Negative once overrun. */
    public float frontDistanceKm;
    /** Ticks of tax relief left, granted by the state on top of any zone. */
    public int taxHolidayTicks;

    public final List<Firm> firms = new ArrayList<>();

    /** Output produced here in the last tick, in money units. */
    public double lastOutput;
    /** Workers the firms here wanted in the last tick, in thousands. */
    public double lastLaborDemand;

    public Region(int id, String name, EconomicZone zone, float borderDistanceKm, float labor,
                  float infrastructure) {
        this.id = id;
        this.name = name;
        this.zone = zone == null ? EconomicZone.NONE : zone;
        this.borderDistanceKm = borderDistanceKm;
        this.frontDistanceKm = borderDistanceKm;
        this.labor = labor;
        this.infrastructure = infrastructure;
    }

    public EconomicZone zone() {
        return zone;
    }

    /** True when the region has no special economic zone. */
    public boolean hasNoZone() {
        return zone.isNone();
    }

    /** Creates, replaces or (with {@link EconomicZone#NONE}) revokes this region's zone. */
    public void setZone(EconomicZone next) {
        this.zone = next == null ? EconomicZone.NONE : next;
    }

    /** 0..1 labour available against what the firms here asked for. */
    public double laborAvailability() {
        if (lastLaborDemand <= 0.0) {
            return 1.0;
        }
        return Math.min(1.0, labor / lastLaborDemand);
    }

    /** Unemployed share of the local workforce, 0..1. */
    public double unemployment() {
        if (labor <= 0.0) {
            return 0.0;
        }
        return Math.max(0.0, 1.0 - lastLaborDemand / labor);
    }

    /** What one unit of capital in this sector produces here, before the war is applied. */
    public double productivity(Sector sector) {
        return sector.outputPerCapital() * zone.productivityFor(sector)
                * (0.55 + 0.45 * infrastructure);
    }

    /** Effective tax multiplier, combining the standing zone break with any holiday. */
    public double taxMultiplier() {
        return zone.taxMultiplier() * (taxHolidayTicks > 0 ? 0.0 : 1.0);
    }

    /** Capital still operating here, ignoring firms that are mid-move or already gone. */
    public double operatingCapital() {
        double total = 0.0;
        for (Firm firm : firms) {
            if (firm.region == this && firm.state == Firm.State.OPERATING) {
                total += firm.capital;
            }
        }
        return total;
    }
}
