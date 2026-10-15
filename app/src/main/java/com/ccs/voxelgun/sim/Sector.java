package com.ccs.voxelgun.sim;

/**
 * What a firm makes, and how hard it is to pick that up and move it.
 *
 * <p>Mobility is the whole point of the model: a mine cannot be relocated at any price, a software
 * house can be running again in another region within a week. Everything between those two poles is
 * a question of how much capital survives the move.
 */
public enum Sector {
    /** Pinned to the ore. Relocation is not a choice it has. */
    MINING("Mining", 0.00f, 0.90f, 0.34f, 0.55f, 0.05f, 6),
    /** Heavy plant: movable in principle, ruinous in practice. */
    HEAVY("Heavy industry", 0.18f, 0.55f, 0.30f, 0.62f, 0.35f, 12),
    /** Land-bound, but stock and machinery can be pulled back. */
    AGRICULTURE("Agriculture", 0.22f, 0.45f, 0.26f, 0.48f, 0.20f, 8),
    /** Lines can be crated and re-erected in a warehouse. */
    LIGHT("Light industry", 0.62f, 0.30f, 0.22f, 0.70f, 0.60f, 4),
    /** Follows its customers; rebuilding means renting premises. */
    SERVICES("Services", 0.78f, 0.22f, 0.18f, 0.80f, 0.75f, 2),
    /** Capital is people and laptops: the most mobile thing in the economy. */
    IT("IT", 0.95f, 0.12f, 0.15f, 0.42f, 1.00f, 1);

    private final String label;
    /** 0 = immovable, 1 = moves at will. Scales both the odds and the cost of relocating. */
    private final float mobility;
    /** Share of a firm's capital burned by a domestic move, before mobility relief. */
    private final float relocationCostShare;
    /** Output per unit of capital per tick, before region and zone effects. */
    private final float outputPerCapital;
    /** Workers required per unit of capital. */
    private final float laborPerCapital;
    /** How much of its output the sector keeps when it moves abroad instead of inland. */
    private final float abroadRetention;
    /** Ticks of downtime while relocating, before mobility shortens it. */
    private final int relocationTicks;

    Sector(String label, float mobility, float relocationCostShare, float outputPerCapital,
           float laborPerCapital, float abroadRetention, int relocationTicks) {
        this.label = label;
        this.mobility = mobility;
        this.relocationCostShare = relocationCostShare;
        this.outputPerCapital = outputPerCapital;
        this.laborPerCapital = laborPerCapital;
        this.abroadRetention = abroadRetention;
        this.relocationTicks = relocationTicks;
    }

    public String label() {
        return label;
    }

    public float mobility() {
        return mobility;
    }

    public float outputPerCapital() {
        return outputPerCapital;
    }

    public float laborPerCapital() {
        return laborPerCapital;
    }

    public float abroadRetention() {
        return abroadRetention;
    }

    /** Share of capital destroyed by moving inland; mobility buys most of it back. */
    public float domesticMoveCostShare() {
        return Math.min(0.95f, relocationCostShare * (1f - 0.65f * mobility));
    }

    /** Leaving the country costs more: distance, customs, a workforce that does not follow. */
    public float abroadMoveCostShare() {
        return Math.min(0.95f, domesticMoveCostShare() * 1.45f);
    }

    /** Ticks of zero output while the move is in progress. */
    public int relocationTicks() {
        return Math.max(1, Math.round(relocationTicks * (1f - 0.5f * mobility)));
    }
}
