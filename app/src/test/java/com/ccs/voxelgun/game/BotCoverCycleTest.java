package com.ccs.voxelgun.game;

import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.voxel.VoxelWorld;
import com.ccs.voxelgun.weapon.WeaponType;
import org.junit.Test;

import static org.junit.Assert.*;

/** The cover sequence must finish real movement, protect reloads and abandon unsafe shelter. */
public class BotCoverCycleTest {
    @Test public void phaseClockWaitsForPhysicalArrivalAndFiringWaitsForPeekArrival() {
        try (Fixture f = new Fixture()) {
            f.start(Difficulty.REGULAR);
            f.cycle.update(f.bot, f.nav, 2f);
            assertEquals(f.cover, f.cycle.destination());
            assertFalse(f.cycle.settled());
            assertFalse(f.cycle.canFire());
            f.place(f.cover);
            f.cycle.update(f.bot, f.nav, .1f);
            assertTrue(f.cycle.hidden());
            assertFalse(f.cycle.canFire());
            f.advance(1.3f);
            assertEquals(f.peek, f.cycle.destination());
            assertFalse("selecting an exposed destination is not arrival", f.cycle.canFire());
            f.place(f.peek);
            f.cycle.update(f.bot, f.nav, .01f);
            assertTrue(f.cycle.canFire());
            assertFalse(f.cycle.hidden());
        }
    }

    @Test public void standingAboveCoverDoesNotCountAsArrivingAtIt() {
        try (Fixture f = new Fixture()) {
            f.start(Difficulty.ELITE);
            f.place(f.cover);
            f.bot.position.y += 2f;
            f.advance(1.5f);
            assertFalse(f.cycle.settled());
            assertFalse(f.cycle.hidden());
            assertFalse(f.cycle.canFire());
            assertEquals(f.cover, f.cycle.destination());
        }
    }

    @Test public void reloadStaysHiddenUntilTheWeaponAndItsPauseAreReady() {
        try (Fixture f = new Fixture()) {
            f.start(Difficulty.REGULAR);
            f.bot.weapon().consume();
            assertTrue(f.bot.weapon().startReload());
            f.place(f.cover);
            f.advance(2f);
            assertTrue("the cycle exposed a reloading bot", f.cycle.hidden());
            assertEquals(f.cover, f.cycle.destination());
            f.bot.weapon().update(f.bot.weaponType().reloadSeconds() + .1f);
            assertFalse(f.bot.weapon().reloading());
            f.cycle.update(f.bot, f.nav, .01f);
            assertTrue("reload completion must still allow a human reaction pause", f.cycle.hidden());
            f.advance(1.3f);
            assertEquals(f.peek, f.cycle.destination());
        }
    }

    @Test public void aNormalPeekReturnsToCoverAndCannotFireWhileReturning() {
        try (Fixture f = new Fixture()) {
            f.start(Difficulty.REGULAR);
            f.expose();
            assertTrue(f.cycle.canFire());
            f.advance(1.4f);
            assertEquals(f.cover, f.cycle.destination());
            assertFalse(f.cycle.canFire());
            assertFalse("the bot has not returned merely because the phase changed", f.cycle.hidden());
            f.place(f.cover);
            f.cycle.update(f.bot, f.nav, .01f);
            assertTrue(f.cycle.hidden());
            assertTrue(f.cycle.active());
        }
    }

    @Test public void startingReloadAtThePeekImmediatelyReturnsToCover() {
        try (Fixture f = new Fixture()) {
            f.start(Difficulty.REGULAR);
            f.expose();
            f.bot.weapon().consume();
            assertTrue(f.bot.weapon().startReload());
            f.cycle.update(f.bot, f.nav, .01f);
            assertEquals(f.cover, f.cycle.destination());
            assertFalse(f.cycle.canFire());
            f.place(f.cover);
            f.advance(.2f);
            assertTrue(f.cycle.hidden());
        }
    }

    @Test public void startingReloadOnTheWayToThePeekCancelsExposureBeforeArrival() {
        try (Fixture f = new Fixture()) {
            f.start(Difficulty.REGULAR);
            f.place(f.cover);
            f.advance(1.3f);
            assertEquals(f.peek, f.cycle.destination());
            f.bot.weapon().consume();
            assertTrue(f.bot.weapon().startReload());
            f.cycle.update(f.bot, f.nav, .01f);
            assertEquals("a bot should not finish walking into exposure with a reloading weapon",
                    f.cover, f.cycle.destination());
            assertFalse(f.cycle.canFire());
        }
    }

    @Test public void twoDistinctSmallHitsRejectCoverButUnchangedDamageDoesNotCountTwice() {
        try (Fixture f = new Fixture()) {
            f.start(Difficulty.REGULAR);
            f.bot.armor -= 5f;
            f.cycle.update(f.bot, f.nav, .01f);
            f.advance(.5f);
            assertTrue(f.cycle.active());
            assertFalse(f.cycle.needsRelocation());
            f.bot.health -= 2f;
            f.cycle.update(f.bot, f.nav, .01f);
            assertFalse(f.cycle.active());
            assertTrue("two separate hits identify an unsafe firing angle", f.cycle.needsRelocation());
            assertEquals("the brain needs the rejected node for its avoidance memory", f.cover, f.cycle.coverNode());
        }
    }

    @Test public void oneHeavyHitRejectsCoverAndRestartClearsTheOldDamageHistory() {
        try (Fixture f = new Fixture()) {
            f.start(Difficulty.REGULAR);
            f.bot.armor -= 25f;
            f.cycle.update(f.bot, f.nav, .01f);
            assertTrue(f.cycle.needsRelocation());
            assertFalse(f.cycle.active());
            f.cycle.reset();
            assertFalse(f.cycle.needsRelocation());
            f.start(Difficulty.ELITE);
            f.cycle.update(f.bot, f.nav, .01f);
            assertTrue("old damage poisoned a fresh cover choice", f.cycle.active());
            assertFalse(f.cycle.needsRelocation());
        }
    }

    @Test public void destroyedCoverOrPeekAndDeathCancelTheSequence() {
        for (boolean destroyPeek : new boolean[]{false, true}) {
            try (Fixture f = new Fixture()) {
                f.start(Difficulty.REGULAR);
                int removed = destroyPeek ? f.peek : f.cover;
                int x = f.nav.cellX(removed), z = f.nav.cellZ(removed);
                f.world.set(x, 0, z, Materials.AIR);
                f.nav.refreshArea(f.world, x, z, x, z);
                f.cycle.update(f.bot, f.nav, .01f);
                assertFalse(f.cycle.active());
                assertFalse(f.cycle.canFire());
                assertFalse(f.cycle.hidden());
                assertEquals(-1, f.cycle.destination());
            }
        }
        try (Fixture f = new Fixture()) {
            f.start(Difficulty.REGULAR);
            f.expose();
            f.bot.alive = false;
            f.cycle.update(f.bot, f.nav, .01f);
            assertFalse(f.cycle.active());
            assertFalse(f.cycle.canFire());
        }
    }

    @Test public void unreachableOrEmptyWeaponCoverDoesNotBecomeAnInfiniteHoldingState() {
        try (Fixture f = new Fixture()) {
            f.start(Difficulty.REGULAR);
            f.advance(10f);
            assertFalse("unreachable cover kept its movement order forever", f.cycle.active());
            assertFalse(f.cycle.canFire());
            assertFalse(f.cycle.needsRelocation());
            f.start(Difficulty.REGULAR);
            f.place(f.cover);
            while (!f.bot.weapon().empty()) f.bot.weapon().consume();
            f.advance(2f);
            assertTrue(f.cycle.hidden());
            f.advance(8f);
            assertFalse("an empty weapon trapped the bot in cover forever", f.cycle.active());
            assertFalse(f.cycle.hidden());
        }
    }

    @Test public void moreSkilledBotUsesAShorterDeliberateHiddenPause() {
        try (Fixture recruit = new Fixture(); Fixture elite = new Fixture()) {
            recruit.start(Difficulty.RECRUIT);
            elite.start(Difficulty.ELITE);
            recruit.place(recruit.cover);
            elite.place(elite.cover);
            recruit.advance(.95f);
            elite.advance(.95f);
            assertEquals(recruit.cover, recruit.cycle.destination());
            assertEquals(elite.peek, elite.cycle.destination());
            assertFalse(elite.cycle.canFire());
        }
    }

    @Test public void multiplePlannersShareTheSameFrameSightBudgetAndRecoverNextFrame() {
        try (Fixture f = new Fixture()) {
            PathFinder finder = new PathFinder(f.nav);
            BotTacticalBudget budget = new BotTacticalBudget();
            BotPositionPlanner first = new BotPositionPlanner(f.world, f.nav, finder, budget);
            BotPositionPlanner second = new BotPositionPlanner(f.world, f.nav, finder, budget);
            BotRouteMemory memory = new BotRouteMemory();
            budget.beginStep(1);
            finder.beginStep(4);
            int firstGoal = first.firingPosition(f.bot, WeaponType.AK15, 28.5f, 1f, 9.5f,
                    -1, 0f, memory, -1);
            assertTrue(firstGoal >= 0);
            assertEquals(0, budget.remaining());
            assertEquals("a second bot exceeded the shared sight budget", -1,
                    second.firingPosition(f.bot, WeaponType.AK15, 28.5f, 1f, 9.5f,
                            -1, 0f, memory, -1));
            assertEquals(0, budget.remaining());
            budget.beginStep(1);
            finder.beginStep(4);
            assertTrue("the deferred bot did not recover when a new frame replenished the budget",
                    second.firingPosition(f.bot, WeaponType.AK15, 28.5f, 1f, 9.5f,
                            -1, 0f, memory, -1) >= 0);
            assertEquals(0, memory.failures(firstGoal));
        }
    }

    @Test public void disabledOptionalTacticsLeaveTheEssentialMovementSearchAvailable() {
        try (Fixture f = new Fixture()) {
            PathFinder finder = new PathFinder(f.nav);
            BotTacticalBudget budget = new BotTacticalBudget();
            budget.beginStep(-4);
            finder.beginStep(1);
            BotPositionPlanner planner = new BotPositionPlanner(f.world, f.nav, finder, budget);
            assertEquals(-1, planner.firingPosition(f.bot, WeaponType.AK15, 28.5f, 1f, 9.5f,
                    -1, 0f, new BotRouteMemory(), -1));
            assertFalse(budget.take());
            assertEquals(0, budget.remaining());
            int start = f.nav.nearestNode(f.bot.position.x, f.bot.position.y, f.bot.position.z);
            assertTrue("optional sight work stole the final essential path search",
                    finder.find(start, f.cover, new PathFinder.Path(64)));
            assertFalse(finder.canSearch());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final VoxelWorld world = new VoxelWorld(32, 10, 24);
        final NavGrid nav;
        final Combatant bot = new Combatant(2, "Cover tester");
        final BotCoverCycle cycle = new BotCoverCycle();
        final int cover, peek;

        Fixture() {
            for (int z = 0; z < world.sizeZ(); z++) for (int x = 0; x < world.sizeX(); x++)
                world.set(x, 0, z, Materials.CONCRETE);
            world.commit();
            nav = new NavGrid(world);
            bot.setSingleWeapon(WeaponType.AK15);
            bot.bot = true;
            bot.armor = 50f;
            bot.position.set(5.5f, 1f, 9.5f);
            cover = nav.nearestNode(9.5f, 1f, 9.5f);
            peek = nav.nearestNode(10.5f, 1f, 9.5f);
        }

        void start(Difficulty difficulty) { cycle.start(cover, peek, bot, difficulty); }
        void place(int node) {
            bot.position.set(nav.cellX(node) + .5f, nav.nodeY(node), nav.cellZ(node) + .5f);
        }
        void advance(float seconds) {
            for (int i = 0; i < Math.ceil(seconds / .01f); i++) cycle.update(bot, nav, .01f);
        }
        void expose() {
            place(cover);
            advance(1.3f);
            assertEquals(peek, cycle.destination());
            place(peek);
            cycle.update(bot, nav, .01f);
        }
        @Override public void close() { world.release(); }
    }
}
