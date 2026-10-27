package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.voxel.VoxelWorld;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/** Strategic decisions on the actual battlefield scale, without map-generation or Android work. */
public class BattlefrontPlannerTest {
    private static VoxelWorld world;
    private static NavGrid nav;

    @BeforeClass public static void makeFlatBattlefield() {
        world = new VoxelWorld(640, 6, 576);
        for (int z = 0; z < world.sizeZ(); z++) {
            for (int x = 0; x < world.sizeX(); x++) {
                for (int y = 0; y < 3; y++) world.set(x, y, z, Materials.CONCRETE);
            }
        }
        world.commit();
        nav = new NavGrid(world);
    }

    @AfterClass public static void releaseWorld() { world.release(); }

    @Test public void bothTeamsChooseOpponentSideInsteadOfLocalSpawnPatrols() {
        BattlefrontPlanner planner = new BattlefrontPlanner(nav);
        for (int id = 1; id <= 40; id++) {
            Combatant blue = soldier(id, Team.BLUE, 16.5f, 288.5f);
            Combatant red = soldier(id, Team.RED, 623.5f, 288.5f);
            int blueGoal = planner.destinationFor(blue);
            int redGoal = planner.destinationFor(red);
            assertTrue(nav.valid(blueGoal));
            assertTrue(nav.valid(redGoal));
            assertTrue("Blue goal must advance across the battlefield", nav.cellX(blueGoal) >= 480);
            assertTrue("Red goal must advance across the battlefield", nav.cellX(redGoal) <= 160);
            assertEquals(3, nav.nodeY(blueGoal));
            assertEquals(3, nav.nodeY(redGoal));
        }
    }

    @Test public void squadsUseAllThreeRoadLanesAndKeepTheirAssignments() {
        BattlefrontPlanner planner = new BattlefrontPlanner(nav);
        Set<Integer> blueLanes = new HashSet<>();
        Set<Integer> redLanes = new HashSet<>();
        for (int id = 1; id <= 60; id++) {
            Combatant blue = soldier(id, Team.BLUE, 16.5f, 288.5f);
            Combatant red = soldier(id, Team.RED, 623.5f, 288.5f);
            int blueGoal = planner.destinationFor(blue);
            int redGoal = planner.destinationFor(red);
            blueLanes.add(nav.cellZ(blueGoal));
            redLanes.add(nav.cellZ(redGoal));
            blue.position.set(240.5f, 3f, 200.5f);
            red.position.set(410.5f, 3f, 410.5f);
            assertEquals("moving must not reshuffle a squad's goal", blueGoal,
                    planner.destinationFor(blue));
            assertEquals(redGoal, planner.destinationFor(red));
        }
        assertEquals(Set.of(96, 288, 480), blueLanes);
        assertEquals(Set.of(96, 288, 480), redLanes);
    }

    @Test public void localWaypointsAreBoundedAndMakeForwardProgressFromDeploymentRoads() {
        BattlefrontPlanner planner = new BattlefrontPlanner(nav);
        for (Team team : new Team[]{Team.BLUE, Team.RED}) {
            for (int id : new int[]{2, 12, 22}) {
                Combatant c = soldier(id, team, team == Team.BLUE ? 16.5f : 623.5f, 288.5f);
                int goal = planner.destinationFor(c);
                for (int leg = 0; leg < 4; leg++) {
                    int waypoint = planner.waypointFor(c);
                    assertTrue("intermediate waypoint must be standable", nav.valid(waypoint));
                    float wx = nav.cellX(waypoint) + .5f;
                    float wz = nav.cellZ(waypoint) + .5f;
                    float legDistance = (float) Math.hypot(wx - c.position.x, wz - c.position.z);
                    assertTrue("local A* should not get a full-map goal: " + legDistance,
                            legDistance > 0f && legDistance <= 65f);
                    float before = distance(c.position.x, c.position.z, goal);
                    assertTrue("deployment-road waypoint must approach the strategic goal",
                            distance(wx, wz, goal) < before);
                    c.position.set(wx, nav.nodeY(waypoint), wz);
                }
            }
        }
    }

    @Test public void nearDestinationTheWaypointIsTheActualDestination() {
        BattlefrontPlanner planner = new BattlefrontPlanner(nav);
        Combatant blue = soldier(2, Team.BLUE, 16.5f, 288.5f);
        int goal = planner.destinationFor(blue);
        blue.position.set(nav.cellX(goal) - 9.5f, nav.nodeY(goal), nav.cellZ(goal) + .5f);
        assertEquals(goal, planner.waypointFor(blue));
    }

    private static float distance(float x, float z, int node) {
        return (float) Math.hypot(nav.cellX(node) + .5f - x, nav.cellZ(node) + .5f - z);
    }

    private static Combatant soldier(int id, Team team, float x, float z) {
        Combatant c = new Combatant(id, "soldier-" + id);
        c.bot = true;
        c.team = team;
        c.position.set(x, 3f, z);
        return c;
    }
}
