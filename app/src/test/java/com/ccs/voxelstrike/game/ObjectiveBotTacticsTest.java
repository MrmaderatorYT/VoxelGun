package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.voxel.VoxelWorld;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/** Objective tactics exercise real goals and unchanged scoring rules, without rendering. */
public class ObjectiveBotTacticsTest {
    private Match match(GameMode mode) {
        MatchConfig config = new MatchConfig();
        config.mode = mode;
        config.botCount = 8;
        config.seed = 4242L;
        return new Match(config);
    }

    private void place(Match match, Combatant c, int node) {
        c.position.set(match.nav().cellX(node) + .5f, match.nav().nodeY(node), match.nav().cellZ(node) + .5f);
        c.velocity.set(0f, 0f, 0f);
        c.firing = false;
    }

    private void advance(Objectives objectives, float seconds) {
        for (int i = 0; i < Math.ceil(seconds / Match.STEP); i++) objectives.update(Match.STEP);
    }

    @Test public void captureDispatchKeepsAGuardWhileReinforcingContestedPoint() {
        Team[] owners = {Team.BLUE, Team.RED, Team.RED};
        boolean[] contested = {false, true, false};
        int[] assigned = new int[3];
        for (int i = 0; i < 7; i++) {
            int point = ObjectiveBotTactics.choosePoint(Team.BLUE, owners, contested, assigned, i % 3, 7);
            assigned[point]++;
        }
        assertTrue("a persistent friendly point lost all defenders", assigned[0] > 0);
        assertTrue("contested attack was not reinforced", assigned[1] > assigned[0]);
        assertTrue("every attacker converged on the same point", assigned[2] > 0);
        assertTrue("assignment ignored the contested objective", assigned[1] >= assigned[2]);
    }

    @Test public void neutralCaptureAssignmentsRemainStableAndReachable() {
        Match match = match(GameMode.CAPTURE_POINT);
        try {
            PathFinder.Path path = new PathFinder.Path(8192);
            int[][] assigned = new int[2][3];
            for (Combatant c : match.combatants()) if (c.bot) {
                int goal = match.objectives().goalFor(c);
                int point = match.objectives().assignedPoint(c);
                assertEquals(c.id % 3, point);
                assigned[c.team == Team.BLUE ? 0 : 1][point]++;
                int start = match.nav().nearestNode(c.position.x, c.position.y, c.position.z);
                assertTrue(match.pathFinder().find(start, goal, path));
                place(match, c, goal);
                assertTrue(match.objectives().shouldHold(c));
            }
            for (int[] team : assigned) for (int count : team) assertTrue(count > 0);
            assertEquals(match.objectives().node(0), match.objectives().goalFor(match.player()));
        } finally { match.release(); }
    }

    @Test public void hillSupportsStillOccupyScoringAreaAndHaveReturnRoutes() {
        Match match = match(GameMode.KING_OF_THE_HILL);
        try {
            int support = 0, capture = 0;
            PathFinder.Path path = new PathFinder.Path(8192);
            for (Combatant c : match.combatants()) if (c.bot) {
                int goal = match.objectives().goalFor(c);
                ObjectiveBotTactics.Role role = match.objectives().roleFor(c);
                if (role == ObjectiveBotTactics.Role.HILL_SUPPORT) support++;
                if (role == ObjectiveBotTactics.Role.HILL_CAPTURE) capture++;
                assertTrue(match.pathFinder().find(match.objectives().node(0), goal, path));
                assertTrue(match.pathFinder().find(goal, match.objectives().node(0), path));
                place(match, c, goal);
                assertTrue(match.objectives().shouldHold(c));
                float dx = c.position.x - match.nav().cellX(match.objectives().node(0)) - .5f;
                float dz = c.position.z - match.nav().cellZ(match.objectives().node(0)) - .5f;
                assertTrue(dx * dx + dz * dz < Objectives.RADIUS * Objectives.RADIUS);
            }
            assertTrue("no bots provided edge support", support > 0);
            assertTrue("no bots contested the hill center", capture > 0);
        } finally { match.release(); }
    }

    @Test public void bombDesignatesOneBotAndReassignsImmediatelyAfterItsDeath() {
        Match match = match(GameMode.BOMB_PLANT);
        try {
            Objectives objectives = match.objectives();
            Combatant designated = null;
            int workers = 0;
            Set<Integer> coverSlots = new HashSet<>();
            for (Combatant c : match.combatants()) if (c.bot) {
                if (objectives.isInteractor(c)) { designated = c; workers++; }
                else coverSlots.add(objectives.goalFor(c));
            }
            assertEquals(1, workers);
            assertNotNull(designated);
            assertEquals(Team.BLUE, designated.team);
            assertTrue("the covering squad still piles onto one destination", coverSlots.size() > 2);
            place(match, designated, objectives.node(0));
            assertTrue(objectives.shouldInteract(designated));
            for (Combatant c : match.combatants()) if (c.bot && c != designated) {
                place(match, c, objectives.node(0));
                assertFalse(objectives.shouldInteract(c));
            }
            designated.alive = false;
            workers = 0;
            for (Combatant c : match.combatants()) if (objectives.isInteractor(c)) {
                workers++;
                assertTrue(c.alive);
                assertNotEquals(designated.id, c.id);
            }
            assertEquals("no immediate replacement for the dead planter", 1, workers);
        } finally { match.release(); }
    }

    @Test public void singleBotCompletesPlantAndDefuseWhileCoverTeammatesStayEligibleToFight() {
        Match match = match(GameMode.BOMB_PLANT);
        try {
            Objectives objectives = match.objectives();
            Combatant planter = null;
            for (Combatant c : match.combatants()) if (objectives.isInteractor(c)) planter = c;
            assertNotNull(planter);
            place(match, planter, objectives.node(0));
            advance(objectives, 3.1f);
            assertTrue(objectives.planted());
            assertEquals(100, planter.score);
            Combatant defuser = null;
            for (Combatant c : match.combatants()) if (objectives.isInteractor(c)) defuser = c;
            assertNotNull(defuser);
            assertEquals(Team.RED, defuser.team);
            place(match, defuser, objectives.node(0));
            assertTrue(objectives.shouldInteract(defuser));
            advance(objectives, 5.1f);
            assertTrue(objectives.roundPaused());
            assertEquals(150, defuser.score);
            assertFalse(objectives.shouldInteract(defuser));
        } finally { match.release(); }
    }

    @Test public void humanAtBombSiteCanPlantWithoutWaitingForDesignatedBot() {
        Match match = match(GameMode.BOMB_PLANT);
        try {
            Objectives objectives = match.objectives();
            place(match, match.player(), objectives.node(0));
            for (Combatant c : match.combatants()) if (c.bot && c.team == Team.BLUE) {
                place(match, c, objectives.node(0));
            }
            advance(objectives, 3.1f);
            assertTrue(objectives.planted());
            assertEquals(100, match.player().score);
            for (Combatant c : match.combatants()) if (c.bot && c.team == Team.BLUE) assertEquals(0, c.score);
        } finally { match.release(); }
    }

    @Test public void carriedCoreAssignsEscortsAndReachableInterceptionAheadOfIt() {
        Match match = match(GameMode.CRYSTAL_HEIST);
        try {
            Objectives objectives = match.objectives();
            Combatant carrier = match.combatants().get(2);
            place(match, carrier, objectives.node(0));
            objectives.update(Match.STEP);
            assertEquals(carrier.id, objectives.carrierId());
            assertEquals(objectives.base(carrier.team), objectives.goalFor(carrier));
            PathFinder.Path path = new PathFinder.Path(8192);
            Set<Integer> enemyGoals = new HashSet<>();
            int escorts = 0;
            for (Combatant c : match.combatants()) if (c.bot && c != carrier) {
                int goal = objectives.goalFor(c);
                assertTrue(match.pathFinder().find(objectives.node(0), goal, path));
                if (c.team == carrier.team) {
                    escorts++;
                    assertEquals(ObjectiveBotTactics.Role.ESCORT, objectives.roleFor(c));
                    assertNotEquals("escort obstructs the carrier", objectives.node(0), goal);
                } else {
                    enemyGoals.add(goal);
                    assertEquals(ObjectiveBotTactics.Role.INTERCEPT, objectives.roleFor(c));
                }
            }
            assertTrue(escorts > 0);
            assertTrue("all enemies chased the current core instead of intercepting", enemyGoals.size() > 1);
        } finally { match.release(); }
    }

    @Test public void corePlanningDefersWhenMovementHasConsumedThePathBudget() {
        Match match = match(GameMode.CRYSTAL_HEIST);
        try {
            Objectives objectives = match.objectives();
            place(match, match.combatants().get(2), objectives.node(0));
            match.pathFinder().beginStep(0);
            objectives.update(Match.STEP);
            for (Combatant c : match.combatants()) if (c.bot) {
                assertTrue(match.nav().valid(objectives.goalFor(c)));
            }
            assertFalse(match.pathFinder().canSearch());
            match.pathFinder().beginStep(2);
            advance(objectives, .9f);
            assertTrue("one shared carrier route consumed the whole frame budget", match.pathFinder().canSearch());
        } finally { match.release(); }
    }

    @Test public void obstructedFormationFallsBackWithoutScanningHundredsOfSightLines() {
        VoxelWorld world = new VoxelWorld(32, 10, 32);
        try {
            for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) world.set(x, 0, z, Materials.CONCRETE);
            for (int z = 12; z <= 20; z++) for (int y = 1; y <= 5; y++) world.set(12, y, z, Materials.CONCRETE);
            world.commit();
            NavGrid nav = new NavGrid(world);
            int anchor = nav.nearestNode(10.5f, 1f, 16.5f);
            Combatant bot = new Combatant(0, "Budgeted guard");
            bot.bot = true; bot.team = Team.BLUE;
            ObjectiveBotTactics tactics = new ObjectiveBotTactics(nav, world);
            int facing = nav.nearestNode(29.5f, 1f, 16.5f);
            int goal = tactics.slot(anchor, bot, 5f, 7.5f, facing);
            assertEquals("blocked preferred positions must retain the reachable objective fallback", anchor, goal);
            assertEquals("occluded finalists must stop at the per-bot sight cap", 4, tactics.lastSightQueries());
            for (int id = 1; id < 20; id++) {
                Combatant teammate = new Combatant(id, "Guard " + id); teammate.bot = true;
                assertTrue(nav.valid(tactics.slot(anchor, teammate, 5f, 7.5f, facing)));
                assertTrue(tactics.lastSightQueries() <= 4);
            }
            assertEquals(-1, tactics.slot(-1, bot, 5f, 7.5f, facing));
            assertEquals(0, tactics.lastSightQueries());
        } finally { world.release(); }
    }

    @Test public void holdingSlotNeverUsesAnIsolatedVisiblePlatform() {
        VoxelWorld world = new VoxelWorld(20, 12, 20);
        // Two separate platforms have line of sight but no walking connection.
        for (int x = 2; x <= 5; x++) for (int z = 7; z <= 12; z++) world.set(x, 2, z, Materials.STONE);
        for (int x = 8; x <= 13; x++) for (int z = 7; z <= 12; z++) world.set(x, 2, z, Materials.STONE);
        NavGrid nav = new NavGrid(world);
        int anchor = nav.nearestNode(5.5f, 3f, 9.5f);
        Combatant c = new Combatant(0, "guard"); c.bot = true; c.team = Team.BLUE;
        ObjectiveBotTactics tactics = new ObjectiveBotTactics(nav, world);
        int goal = tactics.slot(anchor, c, 5f, 7f, nav.nearestNode(12.5f, 3f, 9.5f));
        assertTrue("formation chose the disconnected platform", nav.cellX(goal) <= 5);
        PathFinder.Path path = new PathFinder.Path(256);
        assertTrue(new PathFinder(nav).find(anchor, goal, path));
    }
}
