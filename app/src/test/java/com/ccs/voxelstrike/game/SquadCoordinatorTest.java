package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.voxel.VoxelWorld;
import com.ccs.voxelstrike.weapon.WeaponType;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class SquadCoordinatorTest {
    private static VoxelWorld world;
    private static NavGrid nav;

    @BeforeClass public static void flatBattlefield() {
        world = new VoxelWorld(640, 6, 576);
        for (int z = 0; z < world.sizeZ(); z++) for (int x = 0; x < world.sizeX(); x++)
            for (int y = 0; y < 3; y++) world.set(x, y, z, Materials.CONCRETE);
        world.commit(); nav = new NavGrid(world);
    }

    @AfterClass public static void release() { world.release(); }

    @Test public void attackDefendAndFlankSquadsAdvanceInsteadOfGuardingDeployment() {
        List<Combatant> actors = soldiers(80);
        SquadCoordinator planner = new SquadCoordinator(nav, world, actors, null);
        assertEquals(SquadCoordinator.Role.ATTACK, planner.roleFor(actors.get(2)));
        assertEquals(SquadCoordinator.Role.DEFEND, planner.roleFor(actors.get(10)));
        assertEquals(SquadCoordinator.Role.FLANK, planner.roleFor(actors.get(30)));
        assertTrue(nav.cellX(planner.destinationFor(actors.get(2))) > 480);
        assertTrue(nav.cellX(planner.destinationFor(actors.get(1))) < 160);
        assertTrue("defense belongs at the front, not home", nav.cellX(planner.destinationFor(actors.get(10))) > 200);
        assertFalse(planner.shouldHold(actors.get(2)));
    }

    @Test public void publicObjectiveAssignmentsSpreadTheTeamAcrossSectors() {
        List<Combatant> actors = soldiers(100);
        Field field = new Field();
        SquadCoordinator planner = new SquadCoordinator(nav, world, actors, field);
        Set<Integer> selected = new HashSet<>();
        for (int id = 0; id < 100; id += 10) selected.add(planner.objectiveFor(actors.get(id)));
        assertTrue("ten squads cannot all dogpile the middle point", selected.size() >= 3);
        int goal = planner.destinationFor(actors.get(2));
        for (int i = 1; i < actors.size(); i += 2) actors.get(i).position.set(300, 3, 96);
        assertEquals("enemy positions are not hidden tactical intelligence", goal,
                planner.destinationFor(actors.get(2)));
    }

    @Test public void defendersPrioritizeAContestedFriendlySector() {
        List<Combatant> actors = soldiers(12);
        Field field = new Field();
        field.owners[0] = Team.BLUE; field.owners[2] = Team.BLUE; field.contested[2] = true;
        SquadCoordinator planner = new SquadCoordinator(nav, world, actors, field);
        assertEquals(SquadCoordinator.Role.DEFEND, planner.roleFor(actors.get(10)));
        assertEquals(2, planner.objectiveFor(actors.get(10)));
    }

    @Test public void failedPushRalliesThenChangesTheNextApproach() {
        List<Combatant> actors = soldiers(40);
        Combatant mate = actors.get(2); mate.position.set(330, 3, 288);
        SquadCoordinator planner = new SquadCoordinator(nav, world, actors, null);
        int original = planner.destinationFor(mate);
        planner.onDeath(mate); planner.onDeath(mate);
        assertFalse(planner.regrouping(mate));
        planner.onDeath(mate);
        assertTrue(planner.regrouping(mate));
        assertEquals(SquadCoordinator.Role.REGROUP, planner.roleFor(mate));
        assertTrue(nav.cellX(planner.destinationFor(mate)) < 330);
        planner.update(6f);
        assertFalse(planner.regrouping(mate));
        assertEquals(SquadCoordinator.Role.ATTACK, planner.roleFor(mate));
        assertNotEquals("next push must not repeat the same failed lane", nav.cellZ(original),
                nav.cellZ(planner.destinationFor(mate)));
    }

    @Test public void widelySeparatedLossesDoNotTriggerAnArtificialRetreat() {
        List<Combatant> actors = soldiers(12);
        Combatant mate = actors.get(2); mate.position.set(330, 3, 288);
        SquadCoordinator planner = new SquadCoordinator(nav, world, actors, null);
        planner.onDeath(mate); planner.update(13f); planner.onDeath(mate); planner.onDeath(mate);
        assertFalse(planner.regrouping(mate));
    }

    @Test public void nearbyUsefulBotEarnsSupportWithoutPlayerCommands() {
        List<Combatant> actors = supportTeam();
        Combatant player = actors.get(0), follower = actors.get(2), leader = actors.get(4);
        SquadCoordinator planner = new SquadCoordinator(nav, world, actors, null);
        assertEquals("a player is not automatically the team's leader", -1,
                planner.supportLeaderFor(player));
        assertEquals(leader.id, planner.supportLeaderFor(follower));
        assertEquals(SquadCoordinator.MemberTactic.SUPPORT, planner.memberTacticFor(follower));
        assertEquals("support must not replace the team's strategic attack", SquadCoordinator.Role.ATTACK,
                planner.roleFor(follower));
        assertTrue(Math.abs(nav.cellX(planner.destinationFor(follower)) - leader.position.x) < 16f);
        assertEquals(-1, planner.supportLeaderFor(leader));
    }

    @Test public void idleOrDistantPlayerDoesNotPullBotsAwayFromTheirObjectives() {
        List<Combatant> actors = soldiers(40);
        Combatant player = actors.get(0);
        player.position.set(220, 3, 96);
        player.setKit(WeaponType.SPECTRE, null, null, null);
        // Being alive and in front of the spawn is not enough: this player is not advancing.
        actors.get(2).position.set(207, 3, 96);
        actors.get(2).setKit(WeaponType.BULLDOG_LMG, null, null, null);
        SquadCoordinator planner = new SquadCoordinator(nav, world, actors, null);
        for (Combatant c : actors) if (c.bot) assertEquals(-1, planner.supportLeaderFor(c));
        int goal = planner.destinationFor(actors.get(2));
        assertTrue("idle player must not turn the push into spawn/body guarding", nav.cellX(goal) > 480);
        player.position.set(440, 3, 96);
        player.velocity.set(8, 0, 0);
        planner.update(1.3f);
        assertEquals("far-away player is not a viable support anchor", -1,
                planner.supportLeaderFor(actors.get(2)));
        assertEquals(goal, planner.destinationFor(actors.get(2)));
    }

    @Test public void advancingHumanAndBotEarnSupportUnderTheSameConditions() {
        List<Combatant> actors = supportTeam();
        Combatant human = actors.get(0), independentBot = actors.get(4), follower = actors.get(2);
        human.position.set(278, 3, 96);
        human.velocity.set(8, 0, 0);
        human.setKit(WeaponType.SPECTRE, null, null, null);
        independentBot.position.set(16, 3, 288);
        independentBot.velocity.set(0, 0, 0);
        SquadCoordinator withHuman = new SquadCoordinator(nav, world, actors, null);
        assertEquals("a useful human may earn support without giving commands", human.id,
                withHuman.supportLeaderFor(follower));
        int humanSlot = withHuman.destinationFor(follower);
        human.bot = true;
        SquadCoordinator withBot = new SquadCoordinator(nav, world, actors, null);
        assertEquals("identical behavior must not depend on human authority", human.id,
                withBot.supportLeaderFor(follower));
        assertEquals(humanSlot, withBot.destinationFor(follower));
    }

    @Test public void cautiousTeamTakesInitiativeInsteadOfWaitingForIdleHuman() {
        List<Combatant> actors = supportTeam();
        for (Combatant c : actors) if (c.team == Team.BLUE)
            c.setKit(WeaponType.BULLDOG_LMG, null, null, null);
        Combatant movingBot = actors.get(4), follower = actors.get(2);
        SquadCoordinator planner = new SquadCoordinator(nav, world, actors, null);
        assertEquals(BotCombatStyle.CAUTIOUS, BotCombatStyle.forCombatant(movingBot));
        assertEquals("a cautious team must have a bot willing to advance independently", -1,
                planner.supportLeaderFor(movingBot));
        assertEquals("idle human cannot reserve the team's initiative slot", movingBot.id,
                planner.supportLeaderFor(follower));
        assertEquals(SquadCoordinator.MemberTactic.SUPPORT, planner.memberTacticFor(follower));
    }

    @Test public void redTeamAutonomouslySupportsItsOwnAdvancingBot() {
        List<Combatant> actors = soldiers(8);
        Combatant follower = actors.get(3), leader = actors.get(5), other = actors.get(7);
        follower.position.set(380, 3, 90);
        other.position.set(380, 3, 102);
        leader.position.set(362, 3, 96);
        leader.velocity.set(-8, 0, 0);
        follower.setKit(WeaponType.BULLDOG_LMG, null, null, null);
        other.setKit(WeaponType.BULLDOG_LMG, null, null, null);
        leader.setKit(WeaponType.SPECTRE, null, null, null);
        SquadCoordinator planner = new SquadCoordinator(nav, world, actors, null);
        assertEquals(leader.id, planner.supportLeaderFor(follower));
        assertEquals(leader.id, planner.supportLeaderFor(other));
        assertEquals(SquadCoordinator.MemberTactic.SUPPORT, planner.memberTacticFor(follower));
        assertNotEquals(planner.destinationFor(follower), planner.destinationFor(other));
        assertTrue("red support still advances toward the blue front",
                nav.cellX(planner.destinationFor(follower)) < follower.position.x);
        assertTrue(planner.supportSightQueries() <= 24);
    }

    @Test public void movingSupportKeepsDifferentSlotsAndReleasesDeadLeaderImmediately() {
        List<Combatant> actors = supportTeam();
        Combatant follower = actors.get(2), leader = actors.get(4), other = actors.get(6);
        SquadCoordinator planner = new SquadCoordinator(nav, world, actors, null);
        assertEquals(leader.id, planner.supportLeaderFor(follower));
        assertEquals(leader.id, planner.supportLeaderFor(other));
        int before = planner.destinationFor(follower);
        assertNotEquals("supporters must not body-stack at their ally", before,
                planner.destinationFor(other));
        follower.position.x += 9f;
        other.position.x += 9f;
        leader.position.x += 9f;
        planner.update(1.3f);
        assertEquals(leader.id, planner.supportLeaderFor(follower));
        assertNotEquals(before, planner.destinationFor(follower));
        leader.alive = false;
        assertEquals("death must invalidate support without waiting for another planning pass", -1,
                planner.supportLeaderFor(follower));
        assertNotEquals(SquadCoordinator.MemberTactic.SUPPORT, planner.memberTacticFor(follower));
    }

    @Test public void existingSupportKeepsItsOwnShoulderWhenPlanningOrderRotates() {
        List<Combatant> actors = supportTeam();
        Combatant follower = actors.get(2), leader = actors.get(4), other = actors.get(6);
        SquadCoordinator planner = new SquadCoordinator(nav, world, actors, null);
        assertEquals(leader.id, planner.supportLeaderFor(follower));
        assertEquals(leader.id, planner.supportLeaderFor(other));
        int firstShoulder = planner.destinationFor(follower);
        int secondShoulder = planner.destinationFor(other);
        assertNotEquals(firstShoulder, secondShoulder);
        // Keep physical inputs constant: the only change is rotating the bounded planning order.
        for (int pass = 0; pass < 12; pass++) {
            planner.update(1.3f);
            assertEquals("first supporter lost its valid ally on pass " + pass, leader.id,
                    planner.supportLeaderFor(follower));
            assertEquals("second supporter lost its valid ally on pass " + pass, leader.id,
                    planner.supportLeaderFor(other));
            assertEquals("first supporter oscillated between shoulders on pass " + pass,
                    firstShoulder, planner.destinationFor(follower));
            assertEquals("second supporter oscillated between shoulders on pass " + pass,
                    secondShoulder, planner.destinationFor(other));
        }
    }

    @Test public void supportReleasesIdleOutOfRangeAndBacktrackingLeaders() {
        for (int scenario = 0; scenario < 3; scenario++) {
            List<Combatant> actors = supportTeam();
            Combatant follower = actors.get(2), leader = actors.get(4);
            SquadCoordinator planner = new SquadCoordinator(nav, world, actors, null);
            assertEquals(leader.id, planner.supportLeaderFor(follower));
            if (scenario == 0) leader.velocity.set(0, 0, 0);
            else if (scenario == 1) leader.position.x += 90f;
            else leader.velocity.set(-8, 0, 0);
            assertEquals("invalid support anchor remained latched in scenario " + scenario, -1,
                    planner.supportLeaderFor(follower));
            assertNotEquals(SquadCoordinator.MemberTactic.SUPPORT, planner.memberTacticFor(follower));
            assertTrue("an abandoned support relation must resume a real forward goal",
                    nav.cellX(planner.destinationFor(follower)) > 480);
        }
    }

    @Test public void hurtCautiousBotSeeksSupportButHurtAllyCannotLeadIt() {
        List<Combatant> actors = supportTeam();
        Combatant follower = actors.get(2), leader = actors.get(4);
        follower.health = 34f;
        SquadCoordinator planner = new SquadCoordinator(nav, world, actors, null);
        assertEquals(leader.id, planner.supportLeaderFor(follower));
        leader.health = 25f;
        assertEquals("critically injured allies must not anchor a new push", -1,
                planner.supportLeaderFor(follower));
        planner.update(1.3f);
        assertNotEquals(SquadCoordinator.MemberTactic.SUPPORT, planner.memberTacticFor(follower));
    }

    @Test public void rushersAdvanceWhileSnipersCoverFromBehindInfantry() {
        List<Combatant> actors = supportTeam();
        Combatant rusher = actors.get(2), sniper = actors.get(6);
        rusher.setKit(WeaponType.SPECTRE, null, null, null);
        sniper.setKit(WeaponType.AWM_X, null, null, null);
        SquadCoordinator planner = new SquadCoordinator(nav, world, actors, null);
        assertEquals(BotCombatStyle.RUSHER, BotCombatStyle.forCombatant(rusher));
        assertEquals(BotCombatStyle.SNIPER, BotCombatStyle.forCombatant(sniper));
        assertEquals(-1, planner.supportLeaderFor(rusher));
        assertEquals(-1, planner.supportLeaderFor(sniper));
        assertEquals(SquadCoordinator.MemberTactic.OVERWATCH, planner.memberTacticFor(sniper));
        assertTrue("sniper must cover the infantry rather than deploy beyond them",
                nav.cellX(planner.destinationFor(sniper)) < actors.get(4).position.x - 10f);
    }

    @Test public void supportHasNoFollowerChainsCyclesOrUnboundedCrowds() {
        List<Combatant> actors = soldiers(100);
        Combatant leader = actors.get(4);
        leader.position.set(278, 3, 96);
        leader.velocity.set(8, 0, 0);
        leader.setKit(WeaponType.SPECTRE, null, null, null);
        // Different squads may see a useful nearby ally; nobody receives a forced assignment.
        for (Combatant c : actors) if (c.bot && c.team == Team.BLUE && c != leader) {
            c.position.set(260, 3, 92 + (c.id % 3) * 4);
            c.setKit(WeaponType.BULLDOG_LMG, null, null, null);
        }
        SquadCoordinator planner = new SquadCoordinator(nav, world, actors, null);
        for (int pass = 0; pass < 12; pass++) {
            int[] followers = new int[actors.size()];
            int supported = 0;
            for (Combatant c : actors) {
                int root = planner.supportLeaderFor(c);
                if (root < 0) continue;
                supported++;
                assertNotEquals("self-following creates an unproductive loop", c.id, root);
                assertEquals("a follower cannot itself be a support leader", -1,
                        planner.supportLeaderFor(actors.get(root)));
                assertEquals(c.team, actors.get(root).team);
                assertTrue("more than two bots crowd the same ally", ++followers[root] <= 2);
            }
            assertTrue("fixture must actually exercise support relations", supported > 0);
            assertTrue("support is a choice, not the whole army chasing one ally", supported < 10);
            assertTrue("100 actors exceeded the bounded visibility budget on pass " + pass,
                    planner.supportSightQueries() <= 24);
            planner.update(1.3f);
        }
    }

    @Test public void enemyCoordinatesNeverInfluenceAutonomousSupportChoices() {
        List<Combatant> actors = supportTeam();
        Combatant follower = actors.get(2);
        SquadCoordinator planner = new SquadCoordinator(nav, world, actors, null);
        int leader = planner.supportLeaderFor(follower), goal = planner.destinationFor(follower);
        for (Combatant c : actors) if (c.team == Team.RED) c.position.set(261, 3, 96);
        planner.update(1.3f);
        assertEquals("public squad planning must not gain hidden enemy intelligence", leader,
                planner.supportLeaderFor(follower));
        assertEquals(goal, planner.destinationFor(follower));
    }

    @Test public void supportPreservesAssignedCaptureSectorAndBoundedVisibilityWork() {
        List<Combatant> actors = supportTeam();
        Combatant follower = actors.get(2), leader = actors.get(4), other = actors.get(6);
        follower.position.set(96, 3, 282);
        other.position.set(96, 3, 294);
        leader.position.set(110, 3, 288);
        Field field = new Field();
        SquadCoordinator planner = new SquadCoordinator(nav, world, actors, field);
        assertEquals(leader.id, planner.supportLeaderFor(follower));
        assertEquals(planner.objectiveFor(leader), planner.objectiveFor(follower));
        assertEquals(planner.objectiveFor(leader), planner.objectiveFor(other));
        assertTrue("support perception has a per-pass budget", planner.supportSightQueries() <= 24);
        assertFalse("support is still locomotion, not static body guarding", planner.shouldHold(follower));
    }

    private static List<Combatant> supportTeam() {
        List<Combatant> actors = soldiers(8);
        Combatant player = actors.get(0);
        player.position.set(16.5f, 3, 480.5f);
        Combatant follower = actors.get(2), leader = actors.get(4), other = actors.get(6);
        follower.position.set(260, 3, 90);
        other.position.set(260, 3, 102);
        leader.position.set(278, 3, 96);
        leader.velocity.set(8, 0, 0);
        follower.setKit(WeaponType.BULLDOG_LMG, null, null, null);
        other.setKit(WeaponType.BULLDOG_LMG, null, null, null);
        leader.setKit(WeaponType.SPECTRE, null, null, null);
        return actors;
    }

    private static List<Combatant> soldiers(int count) {
        List<Combatant> result = new ArrayList<>();
        for (int id = 0; id < count; id++) {
            Combatant c = new Combatant(id, "soldier"); c.bot = id != 0;
            c.team = (id & 1) == 0 ? Team.BLUE : Team.RED;
            c.position.set(c.team == Team.BLUE ? 16.5f : 623.5f, 3, 288.5f);
            result.add(c);
        }
        return result;
    }

    private static final class Field implements SquadCoordinator.ObjectiveField {
        final int[] nodes = new int[5];
        final Team[] owners = {Team.NONE, Team.NONE, Team.NONE, Team.NONE, Team.NONE};
        final boolean[] contested = new boolean[5];
        Field() {
            int[] xs = {160, 320, 320, 320, 480}, zs = {288, 96, 288, 480, 288};
            for (int i = 0; i < 5; i++) nodes[i] = nav.nearestNode(xs[i], 3, zs[i]);
        }
        public int count() { return 5; }
        public int node(int i) { return nodes[i]; }
        public Team owner(int i) { return owners[i]; }
        public boolean contested(int i) { return contested[i]; }
    }
}
