package com.ccs.voxelgun.sim;

/**
 * A named policy: everything the state decides, packaged so a run can be labelled by it.
 *
 * <p>An experiment is a set of these run over the same seeds. The point of naming them is that the
 * summary table then has a column you can group by.
 */
public final class Policy {
    public final String name;
    public final double taxRate;
    public final double emissionRate;
    /** Share of the pre-war workforce called up. A few per cent is a full war economy. */
    public final double mobilisationRate;
    public final double defence;
    public final double relocation;
    public final double infrastructure;
    public final double social;
    /** Zone to create in every region that has none, or null to leave them without one. */
    public final EconomicZone zoneForPlainRegions;

    public Policy(String name, double taxRate, double emissionRate, double mobilisationRate,
                  double defence, double relocation, double infrastructure, double social,
                  EconomicZone zoneForPlainRegions) {
        this.name = name;
        this.taxRate = taxRate;
        this.emissionRate = emissionRate;
        this.mobilisationRate = mobilisationRate;
        this.defence = defence;
        this.relocation = relocation;
        this.infrastructure = infrastructure;
        this.social = social;
        this.zoneForPlainRegions = zoneForPlainRegions;
    }

    /** Everything into the army, nothing into keeping firms in the country. */
    public static Policy defenceOnly() {
        return new Policy("defence-only", 0.26, 0.0, 0.10, 0.95, 0.0, 0.03, 0.02, null);
    }

    /** The baseline a thesis compares everything against. */
    public static Policy balanced() {
        return new Policy("balanced", 0.23, 0.0, 0.07, 0.60, 0.20, 0.12, 0.08, null);
    }

    /** Heavy relocation grants at the cost of the front. */
    public static Policy relocationHeavy() {
        return new Policy("relocation-heavy", 0.23, 0.0, 0.07, 0.40, 0.45, 0.10, 0.05, null);
    }

    /** Balanced spending, plus a zone in every region that had none. */
    public static Policy zonesEverywhere() {
        return new Policy("zones-everywhere", 0.23, 0.0, 0.07, 0.60, 0.20, 0.12, 0.08,
                EconomicZone.LOGISTICS_HUB);
    }

    /** Pay for the war with the printing press. */
    public static Policy printMoney() {
        return new Policy("emission", 0.23, 0.12, 0.07, 0.60, 0.20, 0.12, 0.08, null);
    }

    public static Policy[] standardSet() {
        return new Policy[] {
            defenceOnly(), balanced(), relocationHeavy(), zonesEverywhere(), printMoney()
        };
    }

    /** Applies this policy to a fresh simulation, before the first tick. */
    public void applyTo(WarSim sim) {
        sim.budget().taxRate = taxRate;
        sim.budget().emissionRate = emissionRate;
        sim.budget().mobilisationRate = mobilisationRate;
        sim.budget().allocate(defence, relocation, infrastructure, social);
        if (zoneForPlainRegions != null) {
            for (Region region : sim.regions()) {
                if (region.hasNoZone()) {
                    region.setZone(zoneForPlainRegions);
                }
            }
        }
    }
}
