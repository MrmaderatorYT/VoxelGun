package com.ccs.voxelstrike.sim;

/**
 * A region's special economic zone, or the absence of one.
 *
 * <p>A zone is a standing policy attached to the ground: it favours some sectors with productivity
 * and tax relief, and it makes the region more attractive to firms looking for somewhere to land.
 * {@link #NONE} is a first-class case, not a placeholder — a plain region with no zone at all is the
 * baseline every other zone is measured against, and most regions should have it.
 */
public enum EconomicZone {
    /** No zone. Ordinary tax, ordinary productivity, no pull on relocating firms. */
    NONE("No zone", 1.00f, 1.00f, 1.00f, 0.00f),
    /** Serviced plots and power for manufacturing. */
    INDUSTRIAL_PARK("Industrial park", 0.70f, 1.18f, 1.00f, 0.35f, Sector.HEAVY, Sector.LIGHT),
    /** Elevators, processing and rail for the land sector. */
    AGRO_CLUSTER("Agro cluster", 0.80f, 1.15f, 1.00f, 0.20f, Sector.AGRICULTURE),
    /** Flat tax and connectivity; the cheapest zone to stand up and the easiest to lose abroad. */
    TECH_HUB("Tech hub", 0.45f, 1.25f, 1.00f, 0.55f, Sector.IT, Sector.SERVICES),
    /** Roads, rail and warehousing: helps everyone a little, and helps arrivals most. */
    LOGISTICS_HUB("Logistics hub", 0.85f, 1.08f, 1.12f, 0.60f),
    /** Extraction licences and tailings infrastructure. Cannot be recreated elsewhere. */
    MINING_BASIN("Mining basin", 0.90f, 1.22f, 1.00f, 0.05f, Sector.MINING);

    private final String label;
    /** Multiplier on the tax the state collects here. Below 1 is a standing tax break. */
    private final float taxMultiplier;
    /** Productivity multiplier for the sectors the zone was built for. */
    private final float favouredProductivity;
    /** Productivity multiplier for everyone else in the zone. */
    private final float generalProductivity;
    /** 0..1 pull on firms choosing where to relocate, on top of the plain economics. */
    private final float relocationAttraction;
    private final Sector[] favoured;

    EconomicZone(String label, float taxMultiplier, float favouredProductivity,
                 float generalProductivity, float relocationAttraction, Sector... favoured) {
        this.label = label;
        this.taxMultiplier = taxMultiplier;
        this.favouredProductivity = favouredProductivity;
        this.generalProductivity = generalProductivity;
        this.relocationAttraction = relocationAttraction;
        this.favoured = favoured;
    }

    public String label() {
        return label;
    }

    /** True when this region has no special zone at all. */
    public boolean isNone() {
        return this == NONE;
    }

    public float taxMultiplier() {
        return taxMultiplier;
    }

    public float relocationAttraction() {
        return relocationAttraction;
    }

    public boolean favours(Sector sector) {
        for (Sector s : favoured) {
            if (s == sector) {
                return true;
            }
        }
        return false;
    }

    /** Productivity multiplier this zone gives that sector. {@link #NONE} always returns 1. */
    public float productivityFor(Sector sector) {
        return favours(sector) ? favouredProductivity : generalProductivity;
    }

    public Sector[] favouredSectors() {
        return favoured.clone();
    }
}
