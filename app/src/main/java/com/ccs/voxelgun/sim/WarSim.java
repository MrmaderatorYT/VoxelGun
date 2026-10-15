package com.ccs.voxelgun.sim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The war economy: a fixed-step simulation of regions, firms and a state under invasion.
 *
 * <p>The clock is the important part. {@link #advance(float, float)} takes real frame time and a
 * speed multiplier and only ever converts them into whole ticks — the tick itself never sees a
 * delta time. A run at speed 1 and the same run at speed 20 therefore produce identical numbers,
 * which is what makes a fast-forwarded playthrough and a headless sweep comparable evidence.
 *
 * <p>The player may change anything on {@link #budget()} at any time, including while paused. It
 * takes effect from the next tick.
 */
public final class WarSim {
    private final SimConfig config;
    private final List<Region> regions;
    private final List<Firm> firms;
    private final StateBudget budget;
    private final Front front;
    private final Random random;

    private int tick;
    private float accumulator;
    private boolean paused;
    private boolean finished;
    private double gdpBaseline;
    private double capitalLost;
    private double laborMobilised;
    private double laborDisplaced;
    /** Workforce at the first tick, so mobilisation has a ceiling to be a share of. */
    private final double initialLabor;

    /** Counters filled in mid-tick, before the tick's row exists. Reset by every measurement. */
    private final SimMetrics pending = new SimMetrics();
    private final List<SimMetrics> history = new ArrayList<>();

    public WarSim(SimConfig config, List<Region> regions, List<Firm> firms) {
        this.config = config;
        this.regions = regions;
        this.firms = firms;
        this.budget = new StateBudget(config);
        this.front = new Front(config);
        this.random = new Random(config.seed);
        double labor = 0.0;
        for (Region region : regions) {
            labor += region.labor;
        }
        this.initialLabor = labor;
    }

    // --- clock ---------------------------------------------------------------------------------

    /**
     * Feeds wall-clock time in. {@code speed} is the player's fast-forward: 1 is real time, 0
     * freezes without counting as a pause. Returns how many ticks were run.
     */
    public int advance(float deltaSeconds, float speed) {
        if (paused || finished || deltaSeconds <= 0f || speed <= 0f) {
            return 0;
        }
        accumulator += deltaSeconds * speed;
        int ran = 0;
        // Capped so a long stall or a huge speed cannot spin the whole war in one frame.
        while (accumulator >= config.secondsPerTick && ran < 64 && !finished) {
            accumulator -= config.secondsPerTick;
            step();
            ran++;
        }
        return ran;
    }

    /** Runs whole ticks with no clock at all: the headless path for batch experiments. */
    public void runTicks(int count) {
        for (int i = 0; i < count && !finished; i++) {
            step();
        }
    }

    /** Runs to the configured end, or until nothing is left to simulate. */
    public void runToEnd() {
        while (!finished) {
            step();
        }
    }

    public boolean paused() {
        return paused;
    }

    public void setPaused(boolean value) {
        paused = value;
    }

    public boolean finished() {
        return finished;
    }

    public int tick() {
        return tick;
    }

    /** 0..1 progress through the current tick, for a smooth HUD between steps. */
    public float tickFraction() {
        return Math.min(1f, accumulator / config.secondsPerTick);
    }

    // --- the tick ------------------------------------------------------------------------------

    private void step() {
        tick++;
        budget.normalise();

        double output = produce();
        if (tick == 1) {
            gdpBaseline = output;
        }
        collectAndSpend(output);
        moveFront();
        decideRelocations();
        advanceMoves();
        applyWarDamage();
        moveLabor();

        SimMetrics row = measure(output);
        history.add(row);
        if (tick >= config.maxTicks || row.capitalDomestic <= 0.0) {
            finished = true;
        }
    }

    /** Output, and the labour demand behind it. Mobilisation is taken off the top. */
    private double produce() {
        for (Region region : regions) {
            double demand = 0.0;
            for (Firm firm : region.firms) {
                if (firm.region == region) {
                    demand += firm.laborDemand();
                }
            }
            region.lastLaborDemand = demand;
        }
        double total = 0.0;
        for (Region region : regions) {
            double regional = 0.0;
            for (Firm firm : region.firms) {
                if (firm.region == region) {
                    regional += firm.output();
                }
            }
            region.lastOutput = regional;
            total += regional;
        }
        return total;
    }

    private void collectAndSpend(double output) {
        double revenue = 0.0;
        for (Region region : regions) {
            if (!region.status.isHeld()) {
                continue;
            }
            revenue += region.lastOutput * budget.taxRate * region.taxMultiplier();
            if (region.taxHolidayTicks > 0) {
                region.taxHolidayTicks--;
            }
        }
        double emission = output * budget.emissionRate;
        revenue += config.foreignAidPerTick + emission;
        budget.lastEmission = emission;
        budget.lastRevenue = revenue;
        budget.treasury += revenue;

        // Printing money shows up in prices, not in anything real.
        if (output > 0.0) {
            budget.priceLevel *= 1.0 + config.emissionInflation * (emission / output);
        }

        double spend = Math.max(0.0, budget.treasury);
        budget.treasury -= spend;
        budget.lastSpending = spend;
        budget.lastDefenceSpend = spend * budget.defenceShare;
        budget.lastRelocationSpend = spend * budget.relocationShare;
        budget.lastInfrastructureSpend = spend * budget.infrastructureShare;
        budget.lastSocialSpend = spend * budget.socialShare;

        budget.defenceStrength *= 1.0 - config.defenceDecay;
        budget.defenceStrength += budget.lastDefenceSpend * config.defenceStrengthPerMoney
                / budget.priceLevel;
        // The army is a standing level, not something bought again every tick.
        budget.armyStrength = laborMobilised * config.defenceStrengthPerMobilised;

        double repair = budget.lastInfrastructureSpend * config.infrastructureRepairPerMoney
                / budget.priceLevel;
        for (Region region : regions) {
            if (region.status.isHeld()) {
                region.infrastructure = Math.min(1f,
                        region.infrastructure + (float) (repair / regions.size()));
            }
        }
        budget.lastGrantCoverage = grantCoverage();
    }

    /**
     * How much of a typical move the relocation budget would pay for. Firms see this figure when
     * they decide, which is why a grant announced is a grant that works before it is even spent.
     */
    private double grantCoverage() {
        double exposedMoveCost = 0.0;
        for (Firm firm : firms) {
            if (firm.state == Firm.State.OPERATING && firm.region != null
                    && firm.region.status != RegionStatus.REAR) {
                exposedMoveCost += firm.capital * firm.sector.domesticMoveCostShare();
            }
        }
        if (exposedMoveCost <= 0.0) {
            return 0.0;
        }
        double real = budget.lastRelocationSpend / budget.priceLevel;
        return Math.min(1.0, real / exposedMoveCost);
    }

    private void moveFront() {
        front.advance(regions, budget.totalDefence(), config, random);
    }

    private void decideRelocations() {
        List<Region> candidates = new ArrayList<>();
        for (Region region : regions) {
            if (region.status.isHeld()) {
                candidates.add(region);
            }
        }
        for (Firm firm : firms) {
            firm.perceiveRisk(config.riskAwareness, random);
            Firm.Decision decision = firm.decide(candidates, config, budget.lastGrantCoverage,
                    random);
            if (decision == null) {
                continue;
            }
            startMove(firm, decision);
        }
    }

    private void startMove(Firm firm, Firm.Decision decision) {
        double costShare = decision.abroad()
                ? firm.sector.abroadMoveCostShare() : firm.sector.domesticMoveCostShare();
        double grossCost = firm.capital * costShare;
        double grant = 0.0;
        if (!decision.abroad()) {
            double wanted = grossCost * budget.lastGrantCoverage * config.grantEffectiveness;
            grant = Math.min(wanted, budget.lastRelocationSpend / budget.priceLevel);
            firm.grantsReceived += grant;
        }
        double borne = Math.max(0.0, grossCost - grant);
        firm.capital -= borne;
        firm.relocationLosses += borne;
        capitalLost += borne;

        firm.state = Firm.State.RELOCATING;
        firm.leavingCountry = decision.abroad();
        firm.destination = decision.target();
        firm.moveTicksLeft = firm.sector.relocationTicks();
        firm.lastMoveTick = tick;
        if (firm.region != null) {
            firm.region.firms.remove(firm);
        }
        firm.region = null;
        if (decision.abroad()) {
            pending.movesStartedAbroad++;
        } else {
            pending.movesStartedInland++;
        }
    }

    private void advanceMoves() {
        for (Firm firm : firms) {
            if (firm.state != Firm.State.RELOCATING) {
                continue;
            }
            if (--firm.moveTicksLeft > 0) {
                continue;
            }
            if (firm.leavingCountry) {
                firm.state = Firm.State.ABROAD;
                firm.region = null;
                firm.destination = null;
                continue;
            }
            Region target = firm.destination;
            if (target == null || !target.status.isHeld()) {
                // The destination fell while the trucks were on the road: try again next tick.
                target = bestHeldRegion();
            }
            if (target == null) {
                firm.state = Firm.State.LOST;
                capitalLost += firm.capital;
                firm.capital = 0.0;
                continue;
            }
            firm.region = target;
            firm.destination = null;
            firm.state = Firm.State.OPERATING;
            target.firms.add(firm);
        }
    }

    private Region bestHeldRegion() {
        Region best = null;
        for (Region region : regions) {
            if (!region.status.isHeld()) {
                continue;
            }
            if (best == null || region.frontDistanceKm > best.frontDistanceKm) {
                best = region;
            }
        }
        return best;
    }

    /** What the war takes from whoever stayed. */
    private void applyWarDamage() {
        for (Region region : regions) {
            if (region.status == RegionStatus.CONTESTED) {
                region.infrastructure = Math.max(0f,
                        region.infrastructure - config.infrastructureDecayContested);
            }
            float risk = region.status.capitalRisk();
            if (risk <= 0f) {
                continue;
            }
            for (Firm firm : new ArrayList<>(region.firms)) {
                if (firm.state != Firm.State.OPERATING) {
                    continue;
                }
                if (region.status == RegionStatus.OCCUPIED) {
                    capitalLost += firm.capital;
                    firm.capital = 0.0;
                    firm.state = Firm.State.LOST;
                    continue;
                }
                double hit = firm.capital * risk * 0.06 * (0.5 + random.nextDouble());
                firm.capital = Math.max(0.0, firm.capital - hit);
                capitalLost += hit;
                if (firm.capital <= 1e-6) {
                    firm.state = Firm.State.LOST;
                }
            }
            if (region.status == RegionStatus.OCCUPIED) {
                region.firms.clear();
                // The people are not gone from the country's problem, only from its statistics.
                laborDisplaced += region.labor;
                region.labor = 0f;
            }
        }
    }

    /**
     * Mobilisation, and people leaving regions the firms are also leaving.
     *
     * <p>Mobilisation is a share of the pre-war workforce, not a tap that runs forever: once the
     * target is reached the call-ups stop. Without that ceiling an army grows without limit and the
     * invasion quietly becomes unwinnable for the attacker.
     */
    private void moveLabor() {
        double target = initialLabor * budget.mobilisationRate;
        double room = Math.max(0.0, target - laborMobilised);
        double mobilisedThisTick = 0.0;
        for (Region region : regions) {
            if (!region.status.isHeld()) {
                continue;
            }
            double call = Math.min(room, region.labor * 0.08);
            room -= call;
            region.labor -= (float) call;
            mobilisedThisTick += call;
            if (region.status == RegionStatus.THREATENED
                    || region.status == RegionStatus.CONTESTED) {
                float leaving = region.labor * config.displacementRate
                        * (region.status == RegionStatus.CONTESTED ? 3f : 1f);
                region.labor -= leaving;
                laborDisplaced += leaving;
            }
        }
        laborMobilised += mobilisedThisTick;
    }

    // --- measurement ---------------------------------------------------------------------------

    private SimMetrics measure(double output) {
        SimMetrics m = new SimMetrics();
        m.movesStartedInland = pending.movesStartedInland;
        m.movesStartedAbroad = pending.movesStartedAbroad;
        pending.movesStartedInland = 0;
        pending.movesStartedAbroad = 0;
        m.tick = tick;
        m.gdp = output;
        m.gdpBaseline = gdpBaseline;
        m.capitalLost = capitalLost;
        double domestic = 0.0;
        double abroad = 0.0;
        double relocated = 0.0;
        double outsideZones = 0.0;
        for (Firm firm : firms) {
            switch (firm.state) {
                case OPERATING -> {
                    domestic += firm.capital;
                    m.firmsOperating++;
                    if (firm.region != firm.origin) {
                        relocated += firm.capital;
                    }
                    if (firm.region != null && firm.region.hasNoZone()) {
                        outsideZones += firm.capital;
                    }
                }
                case RELOCATING -> {
                    domestic += firm.capital;
                    m.firmsRelocating++;
                }
                case ABROAD -> {
                    abroad += firm.capital;
                    m.firmsAbroad++;
                }
                case LOST -> m.firmsLost++;
            }
        }
        m.capitalDomestic = domestic;
        m.capitalAbroad = abroad;
        m.capitalRelocatedInland = relocated;
        m.capitalShareOutsideZones = domestic <= 0.0 ? 0.0 : outsideZones / domestic;

        m.treasury = budget.treasury;
        m.revenue = budget.lastRevenue;
        m.spending = budget.lastSpending;
        m.defenceSpend = budget.lastDefenceSpend;
        m.relocationSpend = budget.lastRelocationSpend;
        m.grantCoverage = budget.lastGrantCoverage;
        m.priceLevel = budget.priceLevel;
        m.defenceStrength = budget.totalDefence();

        m.frontAdvanceKm = front.advanceKm;
        m.frontMovementKm = front.lastAdvanceKm;
        double labor = 0.0;
        double unemployed = 0.0;
        for (Region region : regions) {
            switch (region.status) {
                case OCCUPIED -> m.regionsOccupied++;
                case CONTESTED -> m.regionsContested++;
                case THREATENED -> m.regionsThreatened++;
                default -> { }
            }
            labor += region.labor;
            unemployed += region.labor * region.unemployment();
        }
        m.laborMobilised = laborMobilised;
        m.laborDisplaced = laborDisplaced;
        m.laborHeld = labor;
        m.unemployment = labor <= 0.0 ? 0.0 : unemployed / labor;
        return m;
    }

    // --- access --------------------------------------------------------------------------------

    public SimConfig config() {
        return config;
    }

    public StateBudget budget() {
        return budget;
    }

    public Front front() {
        return front;
    }

    public List<Region> regions() {
        return Collections.unmodifiableList(regions);
    }

    public List<Firm> firms() {
        return Collections.unmodifiableList(firms);
    }

    /** The last completed tick's figures. Empty until the first tick has run. */
    public SimMetrics metrics() {
        return history.isEmpty() ? new SimMetrics() : history.get(history.size() - 1);
    }

    /** Every tick so far, oldest first. This is the dataset. */
    public List<SimMetrics> history() {
        return Collections.unmodifiableList(history);
    }
}
