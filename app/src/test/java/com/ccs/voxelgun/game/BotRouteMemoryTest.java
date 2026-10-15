package com.ccs.voxelgun.game;

import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.voxel.VoxelWorld;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.*;

public class BotRouteMemoryTest {
    @Test public void failedCorridorGetsAnAlternativeAndBecomesAvailableAgain() {
        VoxelWorld world = floor(false);
        try {
            NavGrid nav = new NavGrid(world);
            PathFinder finder = new PathFinder(nav);
            PathFinder.Path path = new PathFinder.Path(64);
            BotRouteMemory memory = new BotRouteMemory();
            int start = nav.node(1, 3, 0), goal = nav.node(8, 3, 0), blocked = nav.node(4, 3, 0);
            assertTrue(finder.find(start, goal, path, memory));
            assertTrue(contains(path, blocked));
            memory.avoidNode(blocked);
            assertTrue(finder.find(start, goal, path, memory));
            assertFalse("the bot should pay a small detour instead of retrying the failed cell",
                    contains(path, blocked));
            assertEquals(goal, path.node(path.length() - 1));
            memory.update(12.1f);
            assertTrue(finder.find(start, goal, path, memory));
            assertTrue("a temporary failure must not permanently close the original lane",
                    contains(path, blocked));
        } finally { world.release(); }
    }

    @Test public void memoryDoesNotRemoveTheOnlyCorridorOrTheRequiredGoal() {
        VoxelWorld world = floor(true);
        try {
            NavGrid nav = new NavGrid(world);
            PathFinder finder = new PathFinder(nav);
            PathFinder.Path path = new PathFinder.Path(64);
            BotRouteMemory memory = new BotRouteMemory();
            int start = nav.node(1, 3, 0), goal = nav.node(8, 3, 0), bottleneck = nav.node(4, 3, 0);
            memory.avoidNode(bottleneck);
            memory.avoidNode(goal);
            assertTrue("memory is a route cost, not a navigation exclusion",
                    finder.find(start, goal, path, memory));
            assertTrue(contains(path, bottleneck));
            assertEquals(goal, path.node(path.length() - 1));
            assertTrue("the shared graph remains valid for other bots", nav.valid(bottleneck));
        } finally { world.release(); }
    }

    @Test public void deferredSearchPreservesRouteCursorAndNeverAddsFailureMemory() {
        VoxelWorld world = floor(false);
        try {
            NavGrid nav = new NavGrid(world);
            PathFinder finder = new PathFinder(nav);
            PathFinder.Path path = new PathFinder.Path(64);
            BotRouteMemory memory = new BotRouteMemory();
            int start = nav.node(1, 3, 0), goal = nav.node(8, 3, 0);
            assertTrue(finder.find(start, goal, path, memory));
            path.advance();
            int length = path.length(), current = path.currentNode();
            finder.beginStep(0);
            assertFalse(finder.canSearch());
            assertFalse(finder.find(start, goal, path, memory));
            assertEquals(length, path.length());
            assertEquals(current, path.currentNode());
            assertEquals(0, memory.failures(goal));
            assertEquals(0f, memory.cost(goal), 0f);
            finder.beginStep(1);
            assertTrue(finder.find(start, goal, path, memory));
            assertFalse(finder.canSearch());
        } finally { world.release(); }
    }

    @Test public void brainDoesNotBlacklistOrBackoffAGoalWhenSharedSearchBudgetIsEmpty()
            throws Exception {
        MatchConfig config = new MatchConfig();
        config.mode = GameMode.DEATHMATCH;
        config.botCount = 2;
        config.seed = 34L;
        Match match = new Match(config);
        try {
            Combatant bot = match.combatants().get(1);
            BotBrain brain = new BotBrain(bot, Difficulty.REGULAR, match, 35L);
            int goal = match.nav().nearestNode(bot.position.x + 8f, bot.position.y, bot.position.z);
            assertTrue(match.nav().valid(goal));
            field(BotBrain.class, "goalNode").setInt(brain, goal);
            field(BotBrain.class, "repathTimer").setFloat(brain, 0f);
            BotRouteMemory memory = (BotRouteMemory) field(BotBrain.class, "routeMemory").get(brain);
            match.pathFinder().beginStep(0);
            Method follow = BotBrain.class.getDeclaredMethod("followPath", float.class, float.class);
            follow.setAccessible(true);
            for (int i = 0; i < 30; i++) follow.invoke(brain, Match.STEP, 1f);
            assertEquals(goal, field(BotBrain.class, "goalNode").getInt(brain));
            assertEquals(-1, field(BotBrain.class, "failedGoalNode").getInt(brain));
            assertEquals(0f, field(BotBrain.class, "repathTimer").getFloat(brain), 0f);
            assertEquals(0, memory.failures(goal));
            assertEquals(0f, memory.cost(goal), 0f);
        } finally { match.release(); }
    }

    @Test public void memoriesAreBoundedAndFailuresExpireOrClearWhenReached() {
        BotRouteMemory memory = new BotRouteMemory();
        for (int n = 0; n < 100; n++) {
            memory.avoidNode(n);
            memory.failedGoal(n);
        }
        int remembered = 0, failed = 0;
        for (int n = 0; n < 100; n++) {
            if (memory.cost(n) > 0f) remembered++;
            if (memory.failures(n) > 0) failed++;
        }
        assertEquals(6, remembered);
        assertEquals(6, failed);
        for (int i = 0; i < 20; i++) memory.failedGoal(99);
        assertEquals(4, memory.failures(99));
        memory.reachedGoal(99);
        assertEquals(0, memory.failures(99));
        memory.failedGoal(99);
        assertEquals(1, memory.failures(99));
        memory.update(16.1f);
        for (int n = 0; n < 100; n++) {
            assertEquals(0, memory.failures(n));
            assertEquals(0f, memory.cost(n), 0f);
        }
    }

    @Test public void briefRouteDeferralsDoNotEraseASustainedWallStall() throws Exception {
        MatchConfig config = new MatchConfig();
        config.mode = GameMode.DEATHMATCH; config.botCount = 1; config.seed = 39L;
        Match match = new Match(config);
        try {
            for (int x = 55; x < 75; x++) for (int z = 20; z < 42; z++)
                for (int y = 0; y < match.world().sizeY(); y++)
                    match.world().set(x, y, z, y == 0 ? Materials.CONCRETE : Materials.AIR);
            for (int z = 20; z < 42; z++) for (int y = 1; y < 7; y++)
                match.world().set(61, y, z, Materials.CONCRETE);
            match.world().commit(); match.nav().refreshArea(match.world(), 55, 20, 74, 41);
            Combatant bot = match.combatants().get(1);
            bot.position.set(60.5f, 1f, 30.5f); bot.velocity.set(0f, 0f, 0f); bot.grounded = true;
            BotBrain brain = new BotBrain(bot, Difficulty.REGULAR, match, 40L);
            field(BotBrain.class, "goalNode").setInt(brain, match.nav().nearestNode(68.5f, 1f, 30.5f));
            com.ccs.voxelgun.math.Vec3 desired = (com.ccs.voxelgun.math.Vec3)
                    field(BotBrain.class, "desired").get(brain);
            Method detect = BotBrain.class.getDeclaredMethod("detectStuck", float.class);
            detect.setAccessible(true);
            boolean recovered = false;
            for (int tick = 0; tick < 240; tick++) {
                // A single deferred/transition frame is not a decision to hold this position.
                desired.set(tick % 30 == 29 ? 0f : 5f, 0f, 0f);
                detect.invoke(brain, Match.STEP);
                if (field(BotBrain.class, "recoveryTime").getFloat(brain) > 0f) {
                    recovered = true;
                    break;
                }
            }
            assertTrue("one zero-intent route frame must not erase several seconds blocked at a wall", recovered);
        } finally { match.release(); }
    }

    private static VoxelWorld floor(boolean corridor) {
        VoxelWorld world = new VoxelWorld(10, 8, 7);
        for (int z = 0; z < 7; z++) for (int x = 0; x < 10; x++)
            if (!corridor || z == 3) world.set(x, 0, z, Materials.CONCRETE);
        world.commit();
        return world;
    }

    private static boolean contains(PathFinder.Path path, int node) {
        for (int i = 0; i < path.length(); i++) if (path.node(i) == node) return true;
        return false;
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
}
