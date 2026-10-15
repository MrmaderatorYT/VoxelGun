package com.ccs.voxelgun.sim;

/**
 * Every tunable in the model, in one place, so an experiment is one object and one seed.
 *
 * <p>Nothing here changes during a run. Policy the player moves at runtime lives on
 * {@link StateBudget}; this is the world the policy operates in.
 */
public final class SimConfig {
    /** Fixed so two runs with the same seed are identical, whatever speed they were watched at. */
    public long seed = 20260916L;

    /** Real seconds of wall clock per logical tick at speed 1. */
    public float secondsPerTick = 2.0f;
    /** What one tick represents, for labels and for annualising figures. */
    public String tickLabel = "week";
    public int ticksPerYear = 52;
    /** Hard stop, so a headless sweep always terminates. */
    public int maxTicks = 520;

    // --- the enemy -------------------------------------------------------------------------
    /** Kilometres the front would advance per tick against no defence at all. */
    public float enemyPushKmPerTick = 7f;
    /** The attacker's strength, in the same units as the defender's. Stronger by construction. */
    public float enemyStrength = 100f;
    /** Enemy strength gained per tick as it mobilises further, tapering towards the cap. */
    public float enemyStrengthGrowth = 0.9f;
    /**
     * Ceiling on the attacker's strength.
     *
     * <p>Without a ceiling the attacker eventually beats any defence and every run ends in total
     * occupation, which makes the end of a run say nothing about the policy that led there. A cap
     * means a funded defence can reach a stalemate and hold it, and the interesting question
     * becomes how much economy the country still has when the line stops.
     */
    public float enemyStrengthCap = 150f;
    /** Random swing applied to each tick's advance, as a share. */
    public float frontNoise = 0.35f;

    // --- defence ---------------------------------------------------------------------------
    /** Defence strength bought by one money unit of defence spending. */
    public float defenceStrengthPerMoney = 0.0012f;
    /** Share of accumulated defence strength that decays each tick. */
    public float defenceDecay = 0.04f;
    /**
     * Defence strength per thousand mobilised workers, against a pre-war workforce of ~5 800. A
     * realistic call-up is a few per cent of that, so this is deliberately large per head.
     */
    public float defenceStrengthPerMobilised = 0.11f;

    // --- firm behaviour --------------------------------------------------------------------
    /** How many ticks ahead an owner looks when comparing futures. */
    public float decisionHorizonTicks = 26f;
    /** How fast beliefs about danger catch up with reality, 0..1 per tick. */
    public float riskAwareness = 0.18f;
    /** Multiplies the firm's own inertia into the gain it demands before moving. */
    public float inertiaWeight = 0.45f;
    /** Chance per tick that a firm which wants to move actually starts moving. */
    public float actionRatePerTick = 0.35f;
    /** Attachment to the country, as a share of capital, weighed against going abroad. */
    public float homeBias = 0.45f;
    /**
     * Scales how productive a firm expects to be abroad, on top of its sector's retention.
     *
     * <p>Neutral at 1, where the domestic option wins on fundamentals — zones, infrastructure and a
     * cheaper move — and firms only leave the country once there is nowhere safe left in it. Raise
     * it to model foreign markets, finance and subsidies pulling firms out while the country still
     * holds, and calibrate it against the real split between firms that moved inland and firms that
     * left. Below 1 it models the friction of operating in someone else's economy.
     */
    public float abroadProductivityFactor = 1.0f;
    /** How much a destination's zone pull counts, as a share of capital. */
    public float zonePullWeight = 0.8f;
    /** Share of a grant that actually lands as a lower move cost. */
    public float grantEffectiveness = 0.85f;

    // --- the state -------------------------------------------------------------------------
    public double startingTreasury = 40_000.0;
    public double baseTaxRate = 0.23;
    /** Aid per tick, in money units, arriving regardless of what the state does. */
    public double foreignAidPerTick = 2_500.0;
    /** Money units of infrastructure repair bought per unit of infrastructure spending. */
    public float infrastructureRepairPerMoney = 0.0000075f;
    /** Infrastructure lost per tick in a contested region. */
    public float infrastructureDecayContested = 0.06f;
    /** Inflation per tick per unit of emission relative to output. */
    public float emissionInflation = 0.65f;
    /** Share of the workforce that leaves a threatened region each tick. */
    public float displacementRate = 0.015f;

    public SimConfig() {}

    /** A copy with a different seed, for repeating one scenario across many runs. */
    public SimConfig withSeed(long newSeed) {
        SimConfig copy = new SimConfig();
        copy.seed = newSeed;
        copy.secondsPerTick = secondsPerTick;
        copy.tickLabel = tickLabel;
        copy.ticksPerYear = ticksPerYear;
        copy.maxTicks = maxTicks;
        copy.enemyPushKmPerTick = enemyPushKmPerTick;
        copy.enemyStrength = enemyStrength;
        copy.enemyStrengthGrowth = enemyStrengthGrowth;
        copy.enemyStrengthCap = enemyStrengthCap;
        copy.frontNoise = frontNoise;
        copy.defenceStrengthPerMoney = defenceStrengthPerMoney;
        copy.defenceDecay = defenceDecay;
        copy.defenceStrengthPerMobilised = defenceStrengthPerMobilised;
        copy.decisionHorizonTicks = decisionHorizonTicks;
        copy.riskAwareness = riskAwareness;
        copy.inertiaWeight = inertiaWeight;
        copy.actionRatePerTick = actionRatePerTick;
        copy.homeBias = homeBias;
        copy.abroadProductivityFactor = abroadProductivityFactor;
        copy.zonePullWeight = zonePullWeight;
        copy.grantEffectiveness = grantEffectiveness;
        copy.startingTreasury = startingTreasury;
        copy.baseTaxRate = baseTaxRate;
        copy.foreignAidPerTick = foreignAidPerTick;
        copy.infrastructureRepairPerMoney = infrastructureRepairPerMoney;
        copy.infrastructureDecayContested = infrastructureDecayContested;
        copy.emissionInflation = emissionInflation;
        copy.displacementRate = displacementRate;
        return copy;
    }
}
