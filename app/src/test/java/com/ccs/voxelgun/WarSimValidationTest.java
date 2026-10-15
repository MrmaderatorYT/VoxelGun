package com.ccs.voxelgun;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ccs.voxelgun.sim.Firm;
import com.ccs.voxelgun.sim.Policy;
import com.ccs.voxelgun.sim.Region;
import com.ccs.voxelgun.sim.Scenarios;
import com.ccs.voxelgun.sim.SimConfig;
import com.ccs.voxelgun.sim.SimMetrics;
import com.ccs.voxelgun.sim.WarSim;

import org.junit.Test;

/**
 * Validation rather than unit testing: the identities that have to hold for the numbers to mean
 * anything, and the directions the model has to respond in for it to be worth drawing conclusions
 * from.
 */
public class WarSimValidationTest {

    private static WarSim run(long seed, int ticks, Policy policy) {
        SimConfig config = new SimConfig();
        config.seed = seed;
        config.maxTicks = ticks;
        WarSim sim = Scenarios.defaultCountry(config, 250);
        policy.applyTo(sim);
        return sim;
    }

    /** Capital is never created: what is at home, abroad and destroyed must add up to what began. */
    @Test
    public void capitalIsConserved() {
        for (Policy policy : Policy.standardSet()) {
            for (long seed = 1; seed <= 3; seed++) {
                WarSim sim = run(seed, 250, policy);
                double initial = 0.0;
                for (Firm firm : sim.firms()) {
                    initial += firm.capital;
                }
                while (!sim.finished()) {
                    sim.runTicks(1);
                    SimMetrics m = sim.metrics();
                    double total = m.capitalDomestic + m.capitalAbroad + m.capitalLost;
                    assertEquals(policy.name + " seed " + seed + " tick " + m.tick,
                            initial, total, initial * 1e-9);
                }
            }
        }
    }

    /** Nothing goes negative, nothing goes NaN, and losses and occupations only ever ratchet up. */
    @Test
    public void everyFigureStaysInBounds() {
        for (Policy policy : Policy.standardSet()) {
            WarSim sim = run(17L, 250, policy);
            double lost = -1.0;
            int occupied = -1;
            while (!sim.finished()) {
                sim.runTicks(1);
                SimMetrics m = sim.metrics();
                assertTrue("gdp", m.gdp >= 0.0 && !Double.isNaN(m.gdp));
                assertTrue("capital", m.capitalDomestic >= 0.0 && m.capitalAbroad >= 0.0);
                assertTrue("prices", m.priceLevel > 0.0);
                assertTrue("unemployment", m.unemployment >= 0.0 && m.unemployment <= 1.0);
                assertTrue("losses only grow", m.capitalLost >= lost - 1e-9);
                assertTrue("ground is never recovered", m.regionsOccupied >= occupied);
                lost = m.capitalLost;
                occupied = m.regionsOccupied;
                for (Region region : sim.regions()) {
                    assertTrue("labour", region.labor >= -1e-6);
                    assertTrue("infrastructure", region.infrastructure >= -1e-6
                            && region.infrastructure <= 1.0001f);
                }
            }
        }
    }

    /** A firm is in exactly one place, or explicitly in transit. No ghosts in the region lists. */
    @Test
    public void firmsAreWhereTheModelSaysTheyAre() {
        WarSim sim = run(77L, 250, Policy.balanced());
        while (!sim.finished()) {
            sim.runTicks(1);
            for (Region region : sim.regions()) {
                for (Firm firm : region.firms) {
                    assertEquals("ghost firm in " + region.name, region, firm.region);
                }
            }
            for (Firm firm : sim.firms()) {
                if (firm.state == Firm.State.OPERATING) {
                    assertTrue("operating firm must sit in its region",
                            firm.region != null && firm.region.firms.contains(firm));
                } else if (firm.state == Firm.State.RELOCATING) {
                    assertTrue("a firm in transit is in no region", firm.region == null);
                }
            }
        }
    }

    /** A stronger attacker takes more ground. If this ever fails, the front is not a model. */
    @Test
    public void aStrongerEnemyTakesMoreGround() {
        float weak = 0f;
        float strong = 0f;
        for (long seed = 30L; seed < 35L; seed++) {
            SimConfig soft = new SimConfig();
            soft.seed = seed;
            soft.maxTicks = 200;
            soft.enemyStrength *= 0.5f;
            soft.enemyStrengthCap *= 0.5f;
            WarSim a = Scenarios.defaultCountry(soft, 250);
            Policy.balanced().applyTo(a);
            a.runToEnd();
            weak += a.metrics().frontAdvanceKm;

            SimConfig hard = new SimConfig();
            hard.seed = seed;
            hard.maxTicks = 200;
            hard.enemyStrength *= 1.5f;
            hard.enemyStrengthCap *= 1.5f;
            WarSim b = Scenarios.defaultCountry(hard, 250);
            Policy.balanced().applyTo(b);
            b.runToEnd();
            strong += b.metrics().frontAdvanceKm;
        }
        assertTrue("weak " + weak + " vs strong " + strong, strong > weak);
    }

    /**
     * A fully funded defence must be able to hold the line, otherwise every run ends in total
     * occupation and the end of a run says nothing about the policy that produced it.
     */
    @Test
    public void aFundedDefenceCanReachAStalemate() {
        WarSim sim = run(5L, 400, Policy.defenceOnly());
        sim.runToEnd();
        SimMetrics end = sim.metrics();
        assertTrue("the line should hold near the border, got " + end.frontAdvanceKm + " km",
                end.frontAdvanceKm < 120f);
        assertTrue("a holding country keeps most of its output, got " + end.gdpIndex(),
                end.gdpIndex() > 0.5);
    }

    /** The war has to cost something even when it is held: this is not a peacetime simulator. */
    @Test
    public void evenAHeldWarCostsCapital() {
        WarSim sim = run(5L, 400, Policy.defenceOnly());
        sim.runToEnd();
        assertTrue(sim.metrics().capitalLost > 0.0);
        assertTrue(sim.metrics().gdpIndex() < 1.0);
    }

    /**
     * The exit-abroad channel is calibration, not a fixed fact: at the neutral setting firms only
     * leave when the country is losing, and raising the pull of foreign markets substitutes moves
     * abroad for moves inland. Both directions have to work for the channel to be studied at all.
     */
    @Test
    public void theAbroadChannelRespondsToItsCalibration() {
        double neutralAbroad = 0.0;
        double pulledAbroad = 0.0;
        double neutralInland = 0.0;
        double pulledInland = 0.0;
        for (long seed = 700L; seed < 705L; seed++) {
            neutralAbroad += abroadShare(seed, 1.0f);
            pulledAbroad += abroadShare(seed, 2.0f);
            neutralInland += inlandMoves(seed, 1.0f);
            pulledInland += inlandMoves(seed, 2.0f);
        }
        assertEquals("at the neutral setting firms stay in the country while it holds",
                0.0, neutralAbroad, 1e-9);
        assertTrue("foreign pull must send capital abroad", pulledAbroad > 0.0);
        assertTrue("and it must do so at the expense of moves inland",
                pulledInland < neutralInland);
    }

    private static WarSim abroadRun(long seed, float factor) {
        SimConfig config = new SimConfig();
        config.seed = seed;
        config.maxTicks = 200;
        config.abroadProductivityFactor = factor;
        WarSim sim = Scenarios.defaultCountry(config, 250);
        Policy.balanced().applyTo(sim);
        sim.runToEnd();
        return sim;
    }

    private static double abroadShare(long seed, float factor) {
        return abroadRun(seed, factor).metrics().abroadShare();
    }

    private static int inlandMoves(long seed, float factor) {
        int moves = 0;
        for (SimMetrics m : abroadRun(seed, factor).history()) {
            moves += m.movesStartedInland;
        }
        return moves;
    }

    /** Immobile capital is destroyed where it stands; mobile capital survives by leaving. */
    @Test
    public void survivalTracksMobility() {
        WarSim sim = run(321L, 250, Policy.balanced());
        sim.runToEnd();
        int miningTotal = 0;
        int miningLost = 0;
        int mobileTotal = 0;
        int mobileLost = 0;
        for (Firm firm : sim.firms()) {
            switch (firm.sector) {
                case MINING -> {
                    miningTotal++;
                    if (firm.state == Firm.State.LOST) {
                        miningLost++;
                    }
                }
                case IT, SERVICES, LIGHT -> {
                    mobileTotal++;
                    if (firm.state == Firm.State.LOST) {
                        mobileLost++;
                    }
                }
                default -> { }
            }
        }
        assertTrue(miningTotal > 0 && mobileTotal > 0);
        double miningLossRate = (double) miningLost / miningTotal;
        double mobileLossRate = (double) mobileLost / mobileTotal;
        assertTrue("mines: " + miningLossRate + " vs mobile: " + mobileLossRate,
                miningLossRate > mobileLossRate);
    }

    /** The displaced do not vanish with their region: the collapse has to stay visible. */
    @Test
    public void displacedPeopleAreCounted() {
        WarSim sim = run(9L, 250, Policy.relocationHeavy());
        sim.runToEnd();
        SimMetrics end = sim.metrics();
        assertTrue("regions were lost", end.regionsOccupied > 0);
        assertTrue("so people were displaced", end.laborDisplaced > 0.0);
        assertTrue("and the held workforce shrank", end.laborHeld < 5_790.0);
    }
}
