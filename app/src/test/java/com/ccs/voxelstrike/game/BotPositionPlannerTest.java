package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.voxel.VoxelWorld;
import com.ccs.voxelstrike.weapon.WeaponType;
import org.junit.Test;

import static org.junit.Assert.*;

public class BotPositionPlannerTest {
    @Test public void shotgunApproachesWhileSniperFindsAReachableDistantFiringLane() {
        VoxelWorld world = floor();
        try {
            NavGrid nav = new NavGrid(world);
            PathFinder finder = new PathFinder(nav);
            BotTacticalBudget budget = new BotTacticalBudget();
            BotPositionPlanner planner = new BotPositionPlanner(world, nav, finder, budget);
            BotRouteMemory memory = new BotRouteMemory();
            Combatant bot = bot();
            float tx = 50.5f, ty = 1f, tz = 20.5f;
            int close = planner.firingPosition(bot, WeaponType.PUMPER, tx, ty, tz, -1, 0f, memory, -1);
            int far = planner.firingPosition(bot, WeaponType.AWM_X, tx, ty, tz, -1, 0f, memory, -1);
            assertTrue(close >= 0 && far >= 0);
            assertTrue("shotguns move toward their short effective range", nav.cellX(close) > 20);
            assertTrue("snipers seek a longer lane", nav.cellX(far) < 20);
            assertTrue(range(nav, far, tx, tz) > range(nav, close, tx, tz) + 10f);
            assertTrue(visible(world, nav, close, tx, ty, tz));
            assertTrue(visible(world, nav, far, tx, ty, tz));
            PathFinder.Path path = new PathFinder.Path(64);
            assertTrue(finder.find(nav.nearestNode(20.5f, 1f, 20.5f), close, path));
            assertTrue(finder.find(nav.nearestNode(20.5f, 1f, 20.5f), far, path));
        } finally { world.release(); }
    }

    @Test public void coverPairActuallyHidesThenExposesTheBotAndConnectsBothWays() {
        VoxelWorld world = floor();
        try {
            pillar(world, 23, 20);
            NavGrid nav = new NavGrid(world);
            PathFinder finder = new PathFinder(nav);
            BotTacticalBudget budget = new BotTacticalBudget();
            BotPositionPlanner planner = new BotPositionPlanner(world, nav, finder, budget);
            Combatant bot = bot();
            assertTrue(planner.coverPair(bot, 45.5f, 1f, 20.5f, -1, 0f,
                    new BotRouteMemory(), -1, 0));
            int cover = planner.coverNode(), peek = planner.peekNode();
            assertTrue(planner.usable(cover));
            assertTrue(planner.usable(peek));
            assertFalse(visible(world, nav, cover, 45.5f, 1f, 20.5f));
            assertTrue(visible(world, nav, peek, 45.5f, 1f, 20.5f));
            assertTrue(nav.connected(cover, peek));
            assertTrue("a bot must be able to return after exposing itself", nav.connected(peek, cover));
            PathFinder.Path path = new PathFinder.Path(64);
            assertTrue(finder.find(nav.nearestNode(bot.position.x, bot.position.y, bot.position.z), cover, path));
            assertTrue("successful cover validation spends sight budget", budget.remaining() < 8);
        } finally { world.release(); }
    }

    @Test public void depletedBudgetsDeferWithoutPoisoningCandidateMemory() {
        VoxelWorld world = floor();
        try {
            pillar(world, 23, 20);
            NavGrid nav = new NavGrid(world);
            PathFinder finder = new PathFinder(nav);
            BotTacticalBudget budget = new BotTacticalBudget();
            BotPositionPlanner planner = new BotPositionPlanner(world, nav, finder, budget);
            BotRouteMemory memory = new BotRouteMemory();
            Combatant bot = bot();
            finder.beginStep(0);
            assertEquals(-1, planner.firingPosition(bot, WeaponType.AK15, 45.5f, 1f, 20.5f,
                    -1, 0f, memory, -1));
            assertEquals(8, budget.remaining());
            finder.beginStep(4);
            budget.beginStep(1);
            assertFalse("a single sight probe cannot verify both hidden and exposed positions",
                    planner.coverPair(bot, 45.5f, 1f, 20.5f, -1, 0f, memory, -1, 0));
            assertEquals(-1, planner.coverNode());
            assertEquals(-1, planner.peekNode());
            assertEquals(0, budget.remaining());
            for (int i = 0; i < 20; i++) {
                assertFalse(planner.coverPair(bot, 45.5f, 1f, 20.5f, -1, 0f, memory, -1, 0));
                assertEquals(0, budget.remaining());
            }
            for (int n = 0; n < nav.nodeCapacity(); n++) assertEquals(0, memory.failures(n));
            budget.beginStep(8);
            finder.beginStep(4);
            assertTrue("a later frame can still use the deferred pair",
                    planner.coverPair(bot, 45.5f, 1f, 20.5f, -1, 0f, memory, -1, 0));
        } finally { world.release(); }
    }

    @Test public void aOneWayDropCannotBeChosenAsARepeatablePeek() {
        VoxelWorld world = floor();
        try {
            // A narrow balcony with cover at its end. Exposed ground is three blocks below;
            // it is a valid escape drop, but the bot cannot climb back after every shot.
            for (int x = 17; x <= 22; x++) world.set(x, 3, 20, Materials.CONCRETE);
            for (int y = 4; y < 10; y++) world.set(23, y, 20, Materials.CONCRETE);
            world.commit();
            NavGrid nav = new NavGrid(world);
            PathFinder finder = new PathFinder(nav);
            BotPositionPlanner planner = new BotPositionPlanner(world, nav, finder, new BotTacticalBudget());
            Combatant bot = bot();
            bot.position.y = 4f;
            int balcony = nav.nearestNode(22.5f, 4f, 20.5f);
            int ground = nav.nearestNode(22.5f, 1f, 19.5f);
            assertTrue(nav.connected(balcony, ground));
            assertFalse(nav.connected(ground, balcony));
            assertFalse("cover peek pairs must support both outward and return movement",
                    planner.coverPair(bot, 45.5f, 4f, 20.5f, -1, 0f,
                            new BotRouteMemory(), -1, 0));
        } finally { world.release(); }
    }

    @Test public void firingPositionsRespectAssignmentsAndTemporaryRejections() {
        VoxelWorld world = floor();
        try {
            NavGrid nav = new NavGrid(world);
            PathFinder finder = new PathFinder(nav);
            BotTacticalBudget budget = new BotTacticalBudget();
            BotPositionPlanner planner = new BotPositionPlanner(world, nav, finder, budget);
            BotRouteMemory memory = new BotRouteMemory();
            Combatant bot = bot();
            int anchor = nav.nearestNode(bot.position.x, bot.position.y, bot.position.z);
            int first = planner.firingPosition(bot, WeaponType.PUMPER, 50.5f, 1f, 20.5f,
                    anchor, 3f, memory, -1);
            assertTrue(first >= 0);
            assertTrue(range(nav, first, 20.5f, 20.5f) <= 3f);
            memory.avoidNode(first);
            int second = planner.firingPosition(bot, WeaponType.PUMPER, 50.5f, 1f, 20.5f,
                    anchor, 3f, memory, first);
            assertTrue(second >= 0);
            assertNotEquals(first, second);
            assertTrue(range(nav, second, 20.5f, 20.5f) <= 3f);
            int x = nav.cellX(second), z = nav.cellZ(second);
            world.set(x, nav.nodeY(second) - 1, z, Materials.AIR);
            assertFalse("stale nav data cannot make a destroyed floor a usable position", planner.usable(second));
        } finally { world.release(); }
    }

    @Test public void scoringUsesWeaponRangeShelterAndSniperHeight() {
        assertTrue(score(WeaponType.PUMPER, 7f, 1, 0f) > score(WeaponType.PUMPER, 30f, 1, 0f));
        assertTrue(score(WeaponType.MP7K, 12f, 1, 0f) > score(WeaponType.MP7K, 35f, 1, 0f));
        assertTrue(score(WeaponType.AK15, 18f, 1, 0f) > score(WeaponType.AK15, 38f, 1, 0f));
        assertTrue(score(WeaponType.BULLDOG_LMG, 24f, 1, 0f)
                > score(WeaponType.BULLDOG_LMG, 8f, 1, 0f));
        assertTrue(score(WeaponType.AWM_X, 38f, 1, 0f) > score(WeaponType.AWM_X, 8f, 1, 0f));
        assertTrue(score(WeaponType.AWM_X, 38f, 1, 3f) > score(WeaponType.AWM_X, 38f, 1, 0f));
        assertTrue(score(WeaponType.PUMPER, 7f, 1, 0f) > score(WeaponType.PUMPER, 7f, 0, 0f));
    }

    private static float score(WeaponType weapon, float range, int shelter, float elevation) {
        return BotPositionPlanner.positionScore(weapon, range, shelter, 4, elevation, 3f);
    }

    private static Combatant bot() {
        Combatant bot = new Combatant(2, "Planner");
        bot.position.set(20.5f, 1f, 20.5f);
        bot.setSingleWeapon(WeaponType.AK15);
        return bot;
    }

    private static VoxelWorld floor() {
        VoxelWorld world = new VoxelWorld(64, 12, 48);
        for (int z = 0; z < world.sizeZ(); z++) for (int x = 0; x < world.sizeX(); x++)
            world.set(x, 0, z, Materials.CONCRETE);
        world.commit();
        return world;
    }

    private static void pillar(VoxelWorld world, int x, int z) {
        for (int y = 1; y < 6; y++) world.set(x, y, z, Materials.CONCRETE);
        world.commit();
    }

    private static float range(NavGrid nav, int node, float tx, float tz) {
        return (float) Math.hypot(nav.cellX(node) + .5f - tx, nav.cellZ(node) + .5f - tz);
    }

    private static boolean visible(VoxelWorld world, NavGrid nav, int node, float tx, float ty, float tz) {
        return world.lineOfSight(nav.cellX(node) + .5f, nav.nodeY(node) + Combatant.EYE_HEIGHT,
                nav.cellZ(node) + .5f, tx, ty + Combatant.EYE_HEIGHT * .85f, tz);
    }
}
