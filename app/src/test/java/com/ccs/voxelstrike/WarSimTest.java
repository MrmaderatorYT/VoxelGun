package com.ccs.voxelstrike;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.ccs.voxelstrike.sim.EconomicZone;
import com.ccs.voxelstrike.sim.Firm;
import com.ccs.voxelstrike.sim.Region;
import com.ccs.voxelstrike.sim.Scenarios;
import com.ccs.voxelstrike.sim.Sector;
import com.ccs.voxelstrike.sim.SimConfig;
import com.ccs.voxelstrike.sim.SimLog;
import com.ccs.voxelstrike.sim.SimMetrics;
import com.ccs.voxelstrike.sim.WarSim;

import org.junit.Test;

/** The war economy: the clock has to be honest before any of the results mean anything. */
public class WarSimTest {

    private static WarSim sim(long seed) {
        SimConfig config = new SimConfig();
        config.seed = seed;
        config.maxTicks = 220;
        return Scenarios.defaultCountry(config, 300);
    }

    /** Watching a run fast must not change it: speed only converts to whole ticks. */
    @Test
    public void speedDoesNotChangeTheOutcome() {
        WarSim slow = sim(42L);
        WarSim fast = sim(42L);
        for (int i = 0; i < 120; i++) {
            slow.advance(2f, 1f);
        }
        for (int i = 0; i < 6; i++) {
            fast.advance(2f, 20f);
        }
        assertEquals(slow.tick(), fast.tick());
        assertEquals(slow.metrics().gdp, fast.metrics().gdp, 1e-9);
        assertEquals(slow.metrics().capitalAbroad, fast.metrics().capitalAbroad, 1e-9);
    }

    /** Headless batch runs and a watched run are the same simulation. */
    @Test
    public void headlessMatchesRealTime() {
        WarSim watched = sim(7L);
        WarSim headless = sim(7L);
        for (int i = 0; i < 60; i++) {
            watched.advance(2f, 1f);
        }
        headless.runTicks(60);
        assertEquals(SimLog.toCsv(watched.history()), SimLog.toCsv(headless.history()));
    }

    @Test
    public void differentSeedsDiverge() {
        WarSim a = sim(1L);
        WarSim b = sim(2L);
        a.runTicks(80);
        b.runTicks(80);
        assertNotEquals(a.metrics().capitalLost, b.metrics().capitalLost, 1e-9);
    }

    @Test
    public void pauseStopsTimeButNotPolicy() {
        WarSim sim = sim(3L);
        sim.runTicks(10);
        sim.setPaused(true);
        int before = sim.tick();
        assertEquals(0, sim.advance(60f, 8f));
        sim.budget().allocate(0.3, 0.5, 0.1, 0.1);
        assertEquals(before, sim.tick());
        sim.setPaused(false);
        assertTrue(sim.advance(4f, 1f) > 0);
    }

    /** The invasion has to bite: a passive state loses ground and output. */
    @Test
    public void theWarDamagesTheEconomy() {
        WarSim sim = sim(11L);
        sim.runTicks(1);
        double baseline = sim.metrics().gdp;
        sim.runTicks(100);
        SimMetrics end = sim.metrics();
        assertTrue(end.frontAdvanceKm > 0f);
        assertTrue(end.gdp < baseline);
        assertTrue(end.capitalLost > 0.0);
    }

    /** Firms leave, and the ones that can leave easiest leave first. */
    @Test
    public void mobileSectorsRelocateAndMinesCannot() {
        WarSim sim = sim(5L);
        sim.runTicks(120);
        int itMoved = 0;
        int miningMoved = 0;
        for (Firm firm : sim.firms()) {
            if (firm.lastMoveTick < 0) {
                continue;
            }
            if (firm.sector == Sector.IT) {
                itMoved++;
            } else if (firm.sector == Sector.MINING) {
                miningMoved++;
            }
        }
        assertEquals("a mine cannot relocate at any price", 0, miningMoved);
        assertTrue("mobile firms should relocate", itMoved > 0);
    }

    /**
     * Relocation grants should save capital — but only compared against the same defence budget.
     * Funding grants out of the front is a different experiment, and the model does not promise
     * that one comes out ahead.
     */
    @Test
    public void relocationGrantsSaveCapitalAtEqualDefence() {
        double lostWithout = 0.0;
        double lostWith = 0.0;
        for (long seed = 60L; seed < 65L; seed++) {
            WarSim without = sim(seed);
            without.budget().allocate(0.55, 0.00, 0.03, 0.42);
            without.runTicks(200);
            lostWithout += without.metrics().capitalLost;

            WarSim with = sim(seed);
            with.budget().allocate(0.55, 0.40, 0.03, 0.02);
            with.runTicks(200);
            lostWith += with.metrics().capitalLost;
        }
        assertTrue("grants paid for out of social spending should save capital, not cost it: "
                + lostWith + " vs " + lostWithout, lostWith < lostWithout);
    }

    /** A region may simply have no special economic zone, and most do not. */
    @Test
    public void regionsMayHaveNoEconomicZone() {
        WarSim sim = sim(9L);
        int noZone = 0;
        for (Region region : sim.regions()) {
            if (region.hasNoZone()) {
                noZone++;
                assertEquals(EconomicZone.NONE, region.zone());
                assertEquals(1f, region.zone().productivityFor(Sector.IT), 1e-6);
                assertEquals(1f, region.zone().taxMultiplier(), 1e-6);
                assertEquals(0f, region.zone().relocationAttraction(), 1e-6);
            }
        }
        assertTrue("the no-zone case is the control group and must exist", noZone >= 3);
    }

    /** Zones can be created and revoked at runtime, including back to none. */
    @Test
    public void zonesCanBeGrantedAndRevoked() {
        WarSim sim = sim(13L);
        Region plain = null;
        for (Region region : sim.regions()) {
            if (region.hasNoZone()) {
                plain = region;
                break;
            }
        }
        assertTrue(plain != null);
        double before = plain.productivity(Sector.IT);
        plain.setZone(EconomicZone.TECH_HUB);
        assertTrue(plain.productivity(Sector.IT) > before);
        plain.setZone(EconomicZone.NONE);
        assertEquals(before, plain.productivity(Sector.IT), 1e-9);
        assertTrue(plain.hasNoZone());
    }

    /** Printing money raises prices and buys nothing real. */
    @Test
    public void emissionShowsUpInPrices() {
        WarSim sound = sim(17L);
        sound.runTicks(60);
        WarSim printing = sim(17L);
        printing.budget().emissionRate = 0.15;
        printing.runTicks(60);
        assertTrue(printing.metrics().priceLevel > sound.metrics().priceLevel);
    }

    @Test
    public void csvHasARowPerTick() {
        WarSim sim = sim(29L);
        sim.runTicks(25);
        String csv = SimLog.toCsv(sim.history());
        String[] lines = csv.trim().split("\n");
        assertEquals(26, lines.length);
        assertEquals(SimLog.HEADER, lines[0]);
        assertEquals(SimLog.HEADER.split(",", -1).length, lines[1].split(",", -1).length);
    }
}
