package com.ccs.voxelgun;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ccs.voxelgun.sim.Policy;
import com.ccs.voxelgun.sim.SimBatch;
import com.ccs.voxelgun.sim.SimConfig;

import org.junit.Test;

import java.util.List;

/** The batch path: the sweep has to be fast, reproducible, and produce a usable table. */
public class SimBatchTest {

    private static SimConfig base() {
        SimConfig config = new SimConfig();
        config.maxTicks = 200;
        return config;
    }

    @Test
    public void sweepIsReproducible() {
        long[] seeds = SimBatch.seeds(100L, 4);
        String first = SimBatch.toCsv(
                SimBatch.sweep(base(), 200, Policy.standardSet(), seeds));
        String second = SimBatch.toCsv(
                SimBatch.sweep(base(), 200, Policy.standardSet(), seeds));
        assertEquals(first, second);
    }

    @Test
    public void sweepProducesARowPerRun() {
        Policy[] policies = Policy.standardSet();
        long[] seeds = SimBatch.seeds(1L, 5);
        List<SimBatch.Result> results = SimBatch.sweep(base(), 150, policies, seeds);
        assertEquals(policies.length * seeds.length, results.size());
        assertEquals(results.size() + 2,
                SimBatch.toCsv(results).trim().split("\n").length + 1);
    }

    /** Printing money leaves prices higher than paying for the same war out of taxes. */
    @Test
    public void emissionPolicyEndsWithHigherPrices() {
        long[] seeds = SimBatch.seeds(50L, 5);
        double printed = 0.0;
        double sound = 0.0;
        for (long seed : seeds) {
            printed += SimBatch.run(base(), 150, Policy.printMoney(), seed).end.priceLevel;
            sound += SimBatch.run(base(), 150, Policy.balanced(), seed).end.priceLevel;
        }
        assertTrue(printed > sound);
    }

    /** Spending nothing on the front loses more ground than a balanced budget. */
    @Test
    public void defenceSpendingSlowsTheFront() {
        long[] seeds = SimBatch.seeds(80L, 5);
        double heavyDefence = 0.0;
        double heavyRelocation = 0.0;
        for (long seed : seeds) {
            heavyDefence += SimBatch.run(base(), 150, Policy.defenceOnly(), seed)
                    .end.frontAdvanceKm;
            heavyRelocation += SimBatch.run(base(), 150, Policy.relocationHeavy(), seed)
                    .end.frontAdvanceKm;
        }
        assertTrue("money moved from defence to grants must cost ground",
                heavyDefence < heavyRelocation);
    }

    /** A sweep of 100 runs is a batch job, not an overnight one. */
    @Test
    public void sweepIsFast() {
        long start = System.nanoTime();
        SimBatch.sweep(base(), 200, Policy.standardSet(), SimBatch.seeds(500L, 20));
        long millis = (System.nanoTime() - start) / 1_000_000L;
        assertTrue("100 runs took " + millis + " ms", millis < 30_000L);
    }
}
