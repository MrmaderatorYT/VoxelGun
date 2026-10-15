package com.ccs.voxelgun.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs many simulations headless and reduces each to one row.
 *
 * <p>This is the thesis's data-collection step: the same seeds under every policy, so a difference
 * between two rows is the policy and nothing else. Nothing here renders, waits or sleeps — a sweep
 * of a few hundred runs is seconds of CPU.
 */
public final class SimBatch {
    /** One finished run, flattened. */
    public static final class Result {
        public final String policy;
        public final long seed;
        public final SimMetrics end;
        /** Area under the GDP index: total output over the war, not just where it ended. */
        public final double cumulativeGdpIndex;
        public final int ticks;

        Result(String policy, long seed, SimMetrics end, double cumulativeGdpIndex, int ticks) {
            this.policy = policy;
            this.seed = seed;
            this.end = end;
            this.cumulativeGdpIndex = cumulativeGdpIndex;
            this.ticks = ticks;
        }
    }

    public static final String SUMMARY_HEADER =
            "policy,seed,ticks,gdp_index_end,gdp_index_cumulative,capital_domestic,"
            + "capital_abroad,capital_lost,abroad_share,capital_share_outside_zones,"
            + "firms_abroad,firms_lost,front_advance_km,regions_occupied,price_level,"
            + "unemployment,labor_displaced";

    private SimBatch() {}

    /** Every policy against every seed. Firm count is held constant across the sweep. */
    public static List<Result> sweep(SimConfig base, int firmCount, Policy[] policies,
                                     long[] seeds) {
        List<Result> results = new ArrayList<>(policies.length * seeds.length);
        for (Policy policy : policies) {
            for (long seed : seeds) {
                results.add(run(base, firmCount, policy, seed));
            }
        }
        return results;
    }

    /** One run, to the end of the war or the configured horizon. */
    public static Result run(SimConfig base, int firmCount, Policy policy, long seed) {
        WarSim sim = Scenarios.defaultCountry(base.withSeed(seed), firmCount);
        policy.applyTo(sim);
        sim.runToEnd();
        double cumulative = 0.0;
        for (SimMetrics m : sim.history()) {
            cumulative += m.gdpIndex();
        }
        return new Result(policy.name, seed, sim.metrics(), cumulative, sim.tick());
    }

    /** Consecutive seeds, so a sweep is reproducible from two numbers. */
    public static long[] seeds(long first, int count) {
        long[] out = new long[count];
        for (int i = 0; i < count; i++) {
            out[i] = first + i;
        }
        return out;
    }

    public static String toCsv(List<Result> results) {
        StringBuilder sb = new StringBuilder(results.size() * 140 + SUMMARY_HEADER.length());
        sb.append(SUMMARY_HEADER).append('\n');
        for (Result r : results) {
            SimMetrics m = r.end;
            sb.append(r.policy).append(',')
                    .append(r.seed).append(',')
                    .append(r.ticks).append(',')
                    .append(fmt(m.gdpIndex())).append(',')
                    .append(fmt(r.cumulativeGdpIndex)).append(',')
                    .append(fmt(m.capitalDomestic)).append(',')
                    .append(fmt(m.capitalAbroad)).append(',')
                    .append(fmt(m.capitalLost)).append(',')
                    .append(fmt(m.abroadShare())).append(',')
                    .append(fmt(m.capitalShareOutsideZones)).append(',')
                    .append(m.firmsAbroad).append(',')
                    .append(m.firmsLost).append(',')
                    .append(fmt(m.frontAdvanceKm)).append(',')
                    .append(m.regionsOccupied).append(',')
                    .append(fmt(m.priceLevel)).append(',')
                    .append(fmt(m.unemployment)).append(',')
                    .append(fmt(m.laborDisplaced)).append('\n');
        }
        return sb.toString();
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.4f", v);
    }
}
