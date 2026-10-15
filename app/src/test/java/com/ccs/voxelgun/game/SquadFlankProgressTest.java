package com.ccs.voxelgun.game;

import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.voxel.VoxelWorld;
import com.ccs.voxelgun.weapon.WeaponType;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/** A flanking approach belongs to each living member and must diverge before the front line. */
public class SquadFlankProgressTest {
    private static VoxelWorld world;
    private static NavGrid nav;

    @BeforeClass
    public static void flatBattlefield() {
        world = new VoxelWorld(640, 6, 576);
        for (int z = 0; z < world.sizeZ(); z++) {
            for (int x = 0; x < world.sizeX(); x++) {
                for (int y = 0; y < 3; y++) world.set(x, y, z, Materials.CONCRETE);
            }
        }
        world.commit();
        nav = new NavGrid(world);
    }

    @AfterClass
    public static void release() {
        world.release();
    }

    @Test
    public void deadMemberAtTheApproachCannotReleaseLivingSquadMates() {
        List<Combatant> actors = soldiers();
        SquadCoordinator planner = new SquadCoordinator(nav, world, actors, null);
        Combatant dead = actors.get(30), living = actors.get(32);
        assertEquals(SquadCoordinator.Role.FLANK, planner.roleFor(living));
        int deadApproach = planner.strategicDestinationFor(dead);
        int livingApproach = planner.strategicDestinationFor(living);
        place(dead, deadApproach);
        dead.alive = false;

        // Merely reading the dead member's strategic assignment must have no squad-wide effect.
        planner.strategicDestinationFor(dead);
        assertEquals(livingApproach, planner.strategicDestinationFor(living));

        // The normal planning pass also visits dead actors while refreshing cached assignments.
        planner.update(1.3f);
        assertEquals(livingApproach, planner.strategicDestinationFor(living));
    }

    @Test
    public void eachLivingMemberCompletesItsOwnApproachBeforeAdvancingToTheObjective() {
        List<Combatant> actors = soldiers();
        SquadCoordinator planner = new SquadCoordinator(nav, world, actors, null);
        Combatant first = actors.get(30), second = actors.get(32);
        int firstApproach = planner.strategicDestinationFor(first);
        int secondApproach = planner.strategicDestinationFor(second);

        place(first, firstApproach);
        planner.update(1.3f);

        int firstFinal = planner.strategicDestinationFor(first);
        assertNotEquals("arriving member must continue toward the actual objective",
                firstApproach, firstFinal);
        assertTrue("the final target must advance beyond the approach, not another nearby slot",
                nav.cellX(firstFinal) > nav.cellX(firstApproach) + 80);
        assertEquals("one member arriving must not skip another member's approach",
                secondApproach, planner.strategicDestinationFor(second));

        // More planning cycles must preserve both the completed and unfinished phases.
        planner.update(1.3f);
        assertEquals(firstFinal, planner.strategicDestinationFor(first));
        assertEquals(secondApproach, planner.strategicDestinationFor(second));

        place(second, secondApproach);
        planner.update(1.3f);
        int secondFinal = planner.strategicDestinationFor(second);
        assertNotEquals(secondApproach, secondFinal);
        assertTrue(nav.cellX(secondFinal) > nav.cellX(secondApproach) + 80);
        assertTrue("both arrivals must converge on the same strategic sector",
                Math.abs(nav.cellX(firstFinal) - nav.cellX(secondFinal)) < 20
                        && Math.abs(nav.cellZ(firstFinal) - nav.cellZ(secondFinal)) < 20);
    }

    @Test
    public void respawnAtDeploymentRestartsAnApproachCompletedInThePreviousLife() {
        List<Combatant> actors = soldiers();
        SquadCoordinator planner = new SquadCoordinator(nav, world, actors, null);
        Combatant flanker = actors.get(30);
        int approach = planner.strategicDestinationFor(flanker);
        place(flanker, approach);
        int finalGoal = planner.strategicDestinationFor(flanker);
        assertNotEquals(approach, finalGoal);

        // An early death at this friendly-side approach does not trigger squad regrouping.
        // Its individual progress must still be reset before onDeath's early-return branch.
        flanker.alive = false;
        planner.onDeath(flanker);
        flanker.resetForSpawn(16.5f, 3, 288.5f, 0f);
        planner.update(1.3f);

        assertEquals("a new life must take the side road again instead of charging the final goal",
                approach, planner.strategicDestinationFor(flanker));
        assertEquals(approach, planner.destinationFor(flanker));
    }

    @Test
    public void deploymentReplansBothTeamsFromSpawnInsteadOfUninitializedActorCoordinates() {
        List<Combatant> actors = soldiers();
        // Match constructs its tactical planners before spawnAll assigns real positions.
        for (Combatant c : actors) c.position.set(0f, 0f, 0f);
        SquadCoordinator planner = new SquadCoordinator(nav, world, actors, null);
        for (Combatant c : actors) {
            c.resetForSpawn(c.team == Team.BLUE ? 16.5f : 623.5f, 3, 288.5f, 0f);
        }

        planner.onDeployment();

        for (int id : new int[]{30, 31}) {
            Combatant flanker = actors.get(id);
            assertEquals(SquadCoordinator.Role.FLANK, planner.roleFor(flanker));
            int approach = planner.strategicDestinationFor(flanker);
            float direction = flanker.team == Team.BLUE ? 1f : -1f;
            float forward = direction * (nav.cellX(approach) + .5f - flanker.position.x);
            assertTrue(flanker.team + " approach must be near this team's actual deployment",
                    forward > 20f && forward < 80f);
            assertTrue(flanker.team + " flank must change to a neighbouring road",
                    Math.abs(nav.cellZ(approach) + .5f - flanker.position.z) > 120f);
        }
    }

    @Test
    public void marksmanCoversTheFinalSectorOnceItsInfantryPassesTheApproach() {
        List<Combatant> actors = soldiers();
        Combatant sniper = actors.get(30);
        sniper.setLoadout(WeaponType.AWM_X, WeaponType.P9, WeaponType.KNIFE, null);
        SquadCoordinator planner = new SquadCoordinator(nav, world, actors, null);
        assertEquals(SquadCoordinator.Role.FLANK, planner.roleFor(sniper));
        int sniperApproach = planner.strategicDestinationFor(sniper);

        for (int id : new int[]{32, 34, 36, 38}) {
            Combatant infantry = actors.get(id);
            place(infantry, planner.strategicDestinationFor(infantry));
            int sector = planner.strategicDestinationFor(infantry);
            place(infantry, sector);
        }
        planner.update(1.3f);

        int sector = planner.strategicDestinationFor(sniper);
        assertTrue("marksman must stop covering the obsolete near-deployment stage",
                nav.cellX(sector) > nav.cellX(sniperApproach) + 80);
        int perch = planner.destinationFor(sniper);
        assertTrue("overwatch must advance with the infantry instead of staying at the stage",
                nav.cellX(perch) > 300);
        assertTrue("marksman must still occupy the rear band behind the infantry",
                nav.cellX(perch) + 8 < actors.get(32).position.x);
        assertEquals(SquadCoordinator.MemberTactic.OVERWATCH, planner.memberTacticFor(sniper));
        place(sniper, perch);
        assertTrue(planner.shouldHold(sniper));
    }

    @Test
    public void loneSurvivingMarksmanDoesNotWaitForeverForDeadInfantryToPassTheApproach() {
        List<Combatant> actors = soldiers();
        Combatant sniper = actors.get(30);
        sniper.setLoadout(WeaponType.AWM_X, WeaponType.P9, WeaponType.KNIFE, null);
        SquadCoordinator planner = new SquadCoordinator(nav, world, actors, null);
        int approach = planner.strategicDestinationFor(sniper);
        for (int id : new int[]{32, 34, 36, 38}) actors.get(id).alive = false;

        planner.update(1.3f);

        int sector = planner.strategicDestinationFor(sniper);
        assertTrue("a survivor needs a useful final sector even when nobody can finish the stage",
                nav.cellX(sector) > nav.cellX(approach) + 80);
        assertEquals(SquadCoordinator.MemberTactic.OVERWATCH, planner.memberTacticFor(sniper));
        assertEquals(-1, planner.supportLeaderFor(sniper));
    }

    @Test
    public void bothTeamsTakeASideRoadBeforeCrossingTheFrontLine() {
        List<Combatant> actors = soldiers();
        SquadCoordinator planner = new SquadCoordinator(nav, world, actors, null);
        BattlefieldRoutes routes = new BattlefieldRoutes(nav);
        int[] flankers = {30, 31}, attackers = {2, 1};
        for (int side = 0; side < flankers.length; side++) {
            Combatant flank = actors.get(flankers[side]);
            Combatant attack = actors.get(attackers[side]);
            assertEquals(SquadCoordinator.Role.FLANK, planner.roleFor(flank));
            assertEquals(SquadCoordinator.Role.ATTACK, planner.roleFor(attack));
            int approach = planner.strategicDestinationFor(flank);
            int flankWaypoint = routes.waypoint(flank, approach);
            int frontWaypoint = routes.waypoint(attack, planner.strategicDestinationFor(attack));

            assertTrue(flank.team + " flank approach must stay on the friendly side initially",
                    Math.abs(nav.cellX(approach) + .5f - flank.position.x) < 80f);
            assertTrue(flank.team + " flank approach must select a neighbouring arterial",
                    Math.min(Math.abs(nav.cellZ(approach) - 96),
                            Math.abs(nav.cellZ(approach) - 480)) < 10);
            assertTrue(flank.team + " flank must leave the central road before advancing",
                    Math.abs(nav.cellZ(flankWaypoint) + .5f - flank.position.z) > 20f);
            assertTrue(flank.team + " first flank leg must not charge across the map",
                    Math.abs(nav.cellX(flankWaypoint) + .5f - flank.position.x) < 12f);
            assertTrue(flank.team + " attack fixture must still use the forward central route",
                    Math.abs(nav.cellX(frontWaypoint) + .5f - attack.position.x) > 20f
                            && Math.abs(nav.cellZ(frontWaypoint) + .5f - attack.position.z) < 8f);
            assertNotEquals(flank.team + " flank and front route must diverge from deployment",
                    frontWaypoint, flankWaypoint);
        }
    }

    private static List<Combatant> soldiers() {
        List<Combatant> actors = new ArrayList<>();
        for (int id = 0; id < 40; id++) {
            Combatant c = new Combatant(id, "soldier");
            c.bot = id != 0;
            c.team = (id & 1) == 0 ? Team.BLUE : Team.RED;
            c.position.set(c.team == Team.BLUE ? 16.5f : 623.5f, 3, 288.5f);
            c.setLoadout(WeaponType.AK15, WeaponType.P9, WeaponType.KNIFE, null);
            actors.add(c);
        }
        return actors;
    }

    private static void place(Combatant c, int node) {
        assertTrue(nav.valid(node));
        c.position.set(nav.cellX(node) + .5f, nav.nodeY(node), nav.cellZ(node) + .5f);
        c.velocity.set(0f, 0f, 0f);
    }
}
