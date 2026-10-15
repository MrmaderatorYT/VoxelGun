package com.ccs.voxelgun.game;

import com.ccs.voxelgun.map.Maps;
import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.voxel.VoxelWorld;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.*;

/** Deterministic search/route semantics; no host-time or FPS thresholds. */
public class BotNavigationRetryTest {
    @Test public void failedConquestWaypointKeepsItsRetryCooldown() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.blockThroughRoad();
            fixture.match.pathFinder().beginStep(100);
            invoke(fixture.brain, "patrol");
            assertTrue("the first unreachable query must leave an empty route", fixture.path.finished());
            int searched = searchStamp(fixture.match);
            assertTrue(searched > 0);
            float retry = number(fixture.brain, "repathTimer");
            assertTrue(retry > .8f);
            for (int tick = 0; tick < 12; tick++) {
                set(fixture.brain, "repathTimer", retry - (tick + 1) * Match.STEP);
                invoke(fixture.brain, "patrol");
            }
            assertEquals("same cached Conquest waypoint must not override failure backoff",
                    searched, searchStamp(fixture.match));
            set(fixture.brain, "repathTimer", 0f);
            invoke(fixture.brain, "patrol");
            assertEquals("the failed goal must remain retryable after cooldown", searched + 1,
                    searchStamp(fixture.match));
        }
    }

    @Test public void anAutomaticSupportGoalDoesNotWaitForTheOldFailedGoal() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.blockThroughRoad();
            fixture.match.pathFinder().beginStep(100);
            invoke(fixture.brain, "patrol");
            int searched = searchStamp(fixture.match);
            assertTrue(fixture.path.finished());
            assertTrue(number(fixture.brain, "repathTimer") > .8f);
            Combatant leader = fixture.match.player();
            leader.position.set(48.5f, 3f, 288.5f);
            leader.yaw = (float) Math.PI / 2f;
            leader.velocity.set(4f, 0f, 0f);
            fixture.bot.health = 30f;
            fixture.match.conquest().update(1.3f);
            assertEquals("the wounded bot independently chooses useful nearby support",
                    SquadCoordinator.MemberTactic.SUPPORT,
                    fixture.match.conquest().memberTacticFor(fixture.bot));
            assertEquals(leader.id, fixture.match.conquest().supportLeaderFor(fixture.bot));
            invoke(fixture.brain, "patrol");
            assertEquals("a genuinely new waypoint is not the old failed request", searched + 1,
                    searchStamp(fixture.match));
            assertFalse("the independently chosen local support position is reachable on our side of the wall",
                    fixture.path.finished());
        }
    }

    @Test public void distantVoxelRemovalPreservesCachedRouteAndRetryPhase() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.localRoute();
            int length = fixture.path.length(), current = fixture.path.currentNode();
            int searched = searchStamp(fixture.match);
            fixture.remoteRevision();
            fixture.brain.update(Match.STEP); // Countdown isolates terrain invalidation from FSM choices.
            assertEquals("an edit far from the next segment must not discard the cached route",
                    length, fixture.path.length());
            assertEquals(current, fixture.path.currentNode());
            assertEquals("keep each bot's existing retry phase instead of synchronizing all bots",
                    1.1f, number(fixture.brain, "repathTimer"), .0001f);
            invoke(fixture.brain, "followPath");
            assertEquals("validated local movement does not require a new A*", searched,
                    searchStamp(fixture.match));
            assertFalse(fixture.path.finished());
        }
    }

    @Test public void reusedValidNodeAtAnUnreachableHeightRejectsTheCachedEdge() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.localRoute();
            int current = fixture.path.currentNode();
            int x = fixture.match.nav().cellX(current), z = fixture.match.nav().cellZ(current);
            VoxelWorld world = fixture.match.world();
            world.set(x, 2, z, Materials.AIR);
            world.set(x, 7, z, Materials.CONCRETE);
            world.commit();
            fixture.match.nav().refreshArea(world, x, z, x, z);
            assertTrue("column/level index is reused even though its height changed",
                    fixture.match.nav().valid(current));
            assertEquals(8, fixture.match.nav().nodeY(current));
            assertFalse(world.boxCollides(x + .5f, 8f, z + .5f,
                    Combatant.RADIUS, Combatant.HEIGHT));
            fixture.remoteRevision();
            fixture.brain.update(Match.STEP);
            invoke(fixture.brain, "followPath");
            assertTrue("valid index and clear destination are not enough: a five-block rise is disconnected",
                    fixture.path.finished());
            assertEquals(0f, fixture.bot.velocity.x, .0001f);
            assertEquals(0f, fixture.bot.velocity.z, .0001f);
            assertTrue(number(fixture.brain, "repathTimer") > 0f);
        }
    }

    @Test public void voxelBodyCollisionRejectsAStaleButValidNavigationNode() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.localRoute();
            int current = fixture.path.currentNode();
            int x = fixture.match.nav().cellX(current), z = fixture.match.nav().cellZ(current);
            fixture.match.world().set(x, 3, z, Materials.WOOD);
            fixture.match.world().commit();
            // Deliberately stale NavGrid: the mover still has to consult authoritative terrain.
            assertTrue(fixture.match.nav().valid(current));
            fixture.remoteRevision();
            fixture.brain.update(Match.STEP);
            invoke(fixture.brain, "followPath");
            assertTrue("actual body clearance must be checked before steering", fixture.path.finished());
            assertEquals(0f, fixture.bot.velocity.x, .0001f);
            assertEquals(0f, fixture.bot.velocity.z, .0001f);
        }
    }

    @Test public void clearanceUsesBothHorizontalVoxelCentersBesideAWall() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.localRoute();
            int current = fixture.path.currentNode();
            int x = fixture.match.nav().cellX(current), z = fixture.match.nav().cellZ(current);
            fixture.match.world().set(x, 3, z - 1, Materials.CONCRETE);
            fixture.match.world().commit();
            fixture.match.nav().refreshArea(fixture.match.world(), x, z - 1, x, z - 1);
            fixture.remoteRevision();
            fixture.brain.update(Match.STEP);
            assertFalse(fixture.match.world().boxCollides(x + .5f, 3f, z + .5f,
                    Combatant.RADIUS, Combatant.HEIGHT));
            invoke(fixture.brain, "followPath");
            assertFalse("a wall beside, not inside, the waypoint must not invalidate it",
                    fixture.path.finished());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Match match;
        final Combatant bot;
        final BotBrain brain;
        final PathFinder.Path path;

        Fixture() throws Exception {
            MatchConfig config = new MatchConfig();
            config.mode = GameMode.BATTLE_FIELD;
            config.conquest = true;
            config.mapIndex = Maps.firstBattlefieldIndex();
            config.totalPlayers = 4;
            config.seed = 4466L;
            match = new Match(config);
            bot = match.combatants().get(2);
            bot.position.set(24.5f, 3f, 288.5f);
            bot.velocity.set(0f, 0f, 0f);
            bot.grounded = true;
            // This fixture exercises an infantry support order; snipers keep their rear position.
            bot.setLoadout(com.ccs.voxelgun.weapon.WeaponType.BULLDOG_LMG,
                    com.ccs.voxelgun.weapon.WeaponType.P9,
                    com.ccs.voxelgun.weapon.WeaponType.KNIFE, null);
            match.conquest().update(1.3f);
            brain = new BotBrain(bot, Difficulty.REGULAR, match, 998L);
            path = (PathFinder.Path) field(BotBrain.class, "path").get(brain);
            VoxelWorld world = match.world();
            for (int z = 0; z < world.sizeZ(); z++) for (int x = 0; x < 60; x++) {
                for (int y = 0; y < world.sizeY(); y++)
                    world.set(x, y, z, y == 2 ? Materials.CONCRETE : Materials.AIR);
            }
            world.commit();
            match.nav().refreshArea(world, 0, 0, 59, world.sizeZ() - 1);
        }

        void blockThroughRoad() {
            VoxelWorld world = match.world();
            for (int z = 0; z < world.sizeZ(); z++) for (int y = 0; y < world.sizeY(); y++)
                world.set(60, y, z, Materials.CONCRETE);
            world.commit();
            match.nav().refreshArea(world, 60, 0, 60, world.sizeZ() - 1);
        }

        void localRoute() throws Exception {
            set(brain, "goalNode", match.nav().nearestNode(30.5f, 3f, 288.5f));
            set(brain, "repathTimer", 0f);
            match.pathFinder().beginStep(4);
            invoke(brain, "followPath");
            assertFalse(path.finished());
            set(brain, "repathTimer", 1.1f);
        }

        void remoteRevision() {
            VoxelWorld world = match.world();
            world.set(500, 10, 500, Materials.WOOD);
            world.commit();
            assertTrue(world.removeDestructible(500, 10, 500));
        }

        @Override public void close() { match.release(); }
    }

    private static int searchStamp(Match match) throws Exception {
        return field(PathFinder.class, "currentStamp").getInt(match.pathFinder());
    }

    private static float number(BotBrain brain, String name) throws Exception {
        return field(BotBrain.class, name).getFloat(brain);
    }

    private static void set(BotBrain brain, String name, Object value) throws Exception {
        field(BotBrain.class, name).set(brain, value);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void invoke(BotBrain brain, String name) throws Exception {
        Method method = name.equals("followPath")
                ? BotBrain.class.getDeclaredMethod(name, float.class, float.class)
                : BotBrain.class.getDeclaredMethod(name, float.class);
        method.setAccessible(true);
        if (name.equals("followPath")) method.invoke(brain, Match.STEP, 1f);
        else method.invoke(brain, Match.STEP);
    }
}
