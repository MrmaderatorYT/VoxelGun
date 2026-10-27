package com.ccs.voxelstrike;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ccs.voxelstrike.game.Combatant;
import com.ccs.voxelstrike.game.BattlefieldRoutes;
import com.ccs.voxelstrike.game.NavGrid;
import com.ccs.voxelstrike.game.PathFinder;
import com.ccs.voxelstrike.map.GameMap;
import com.ccs.voxelstrike.map.Maps;
import com.ccs.voxelstrike.map.SpawnPoint;
import com.ccs.voxelstrike.voxel.VoxelWorld;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class MapAndNavigationTest {
    @Test
    public void everyMapBuildsWithUsableSpawns() {
        for (int index = 0; index < Maps.count(); index++) {
            GameMap map = Maps.create(index);
            VoxelWorld world = map.build(1234L + index);

            assertFalse(map.id() + " has no blue spawns", map.blueSpawns().isEmpty());
            assertFalse(map.id() + " has no red spawns", map.redSpawns().isEmpty());
            assertFalse(map.id() + " has no free spawns", map.freeSpawns().isEmpty());

            checkSpawns(map, world, map.blueSpawns());
            checkSpawns(map, world, map.redSpawns());
            checkSpawns(map, world, map.freeSpawns());
            world.release();
        }
    }

    private void checkSpawns(GameMap map, VoxelWorld world, List<SpawnPoint> spawns) {
        for (SpawnPoint spawn : spawns) {
            String where = map.id() + " spawn at " + spawn.x + "," + spawn.z;
            assertTrue(where + " is outside the map",
                    spawn.x > 1 && spawn.z > 1 && spawn.x < world.sizeX() - 1
                            && spawn.z < world.sizeZ() - 1);
            // The match resolves a spawn to the first level a body fits on, so check that level.
            int level = world.standableLevel((int) spawn.x, (int) spawn.z, (int) spawn.y, 2);
            assertTrue(where + " has no footing", level >= 0);
            assertFalse(where + " is buried in geometry",
                    world.boxCollides(spawn.x, level, spawn.z, Combatant.RADIUS,
                            Combatant.HEIGHT));
        }
    }

    @Test
    public void botsCanPathBetweenOpposingSpawnsOnEveryMap() {
        for (int index = 0; index < Maps.count(); index++) {
            GameMap map = Maps.create(index);
            VoxelWorld world = map.build(99L + index);
            NavGrid grid = new NavGrid(world);
            PathFinder finder = new PathFinder(grid);
            PathFinder.Path path = new PathFinder.Path(4096);

            assertTrue(map.id() + " has no walkable surface", grid.openCount() > 500);

            SpawnPoint from = map.blueSpawns().get(0);
            SpawnPoint to = map.redSpawns().get(0);
            int start = grid.nearestNode(from.x, from.y, from.z);
            int goal = grid.nearestNode(to.x, to.y, to.z);
            assertTrue(map.id() + " blue spawn is off the navigation grid", start >= 0);
            assertTrue(map.id() + " red spawn is off the navigation grid", goal >= 0);
            int length = routeLength(map, grid, finder, start, goal, path);
            assertTrue(map.id() + " has no route between the bases", length > 0);
            assertTrue(map.id() + " produced a degenerate route", length > 4);
            world.release();
        }
    }

    @Test
    public void everySpawnCanReachThePlayableMap() {
        StringBuilder problems = new StringBuilder();
        for (int index = 0; index < Maps.count(); index++) {
            GameMap map = Maps.create(index);
            VoxelWorld world = map.build(2048L + index);
            NavGrid grid = new NavGrid(world);
            PathFinder finder = new PathFinder(grid);
            PathFinder.Path path = new PathFinder.Path(4096);
            List<SpawnPoint> teamSpawns = new ArrayList<>(map.blueSpawns());
            teamSpawns.addAll(map.redSpawns());

            checkSpawnRoutes(problems, map, world, grid, finder, path, "blue", map.blueSpawns(),
                    map.redSpawns());
            checkSpawnRoutes(problems, map, world, grid, finder, path, "red", map.redSpawns(),
                    map.blueSpawns());
            checkSpawnRoutes(problems, map, world, grid, finder, path, "free", map.freeSpawns(),
                    teamSpawns);
            world.release();
        }
        assertTrue(problems.toString(), problems.length() == 0);
    }

    private void checkSpawnRoutes(StringBuilder problems, GameMap map, VoxelWorld world,
                                  NavGrid grid, PathFinder finder, PathFinder.Path path,
                                  String label, List<SpawnPoint> starts,
                                  List<SpawnPoint> destinations) {
        for (SpawnPoint spawn : starts) {
            String where = map.id() + " " + label + " spawn at " + spawn.x + "," + spawn.z;
            int start = resolvedNode(world, grid, spawn);
            if (start < 0) {
                problems.append(where)
                        .append(" is off the navigation grid after spawn resolution\n");
                continue;
            }
            int reachable = reachableCount(grid, start);
            if (reachable < 32) {
                problems.append(where).append(" is trapped near the spawn (reachable=")
                        .append(reachable).append(")\n");
            }

            boolean canLeave = false;
            for (SpawnPoint destination : destinations) {
                int goal = resolvedNode(world, grid, destination);
                if (goal >= 0 && routeLength(map, grid, finder, start, goal, path) > 4) {
                    canLeave = true;
                    break;
                }
            }
            if (!canLeave) {
                problems.append(where).append(" cannot route out to the playable map\n");
            }
        }
    }

    private int routeLength(GameMap map, NavGrid grid, PathFinder finder, int start, int goal,
                            PathFinder.Path path) {
        if (!map.id().startsWith("battlefield_")) {
            return finder.find(start, goal, path) ? path.length() : 0;
        }
        // Large matches use local A* along authored road waypoints. A single 640-block search
        // can exhaust its 6000-node budget even when all corridors connect. Exercise the same
        // routing strategy as the live bots, requiring every leg and the final goal to succeed.
        BattlefieldRoutes routes = new BattlefieldRoutes(grid);
        Combatant walker = new Combatant(1, "route-check");
        int current = start, total = 0;
        for (int leg = 0; leg < 32 && current != goal; leg++) {
            walker.position.set(grid.cellX(current) + .5f, grid.nodeY(current),
                    grid.cellZ(current) + .5f);
            int next = routes.waypoint(walker, goal);
            if (next == current || !finder.find(current, next, path)) return 0;
            total += path.length();
            current = next;
        }
        return current == goal ? total : 0;
    }

    private int resolvedNode(VoxelWorld world, NavGrid grid, SpawnPoint spawn) {
        int level = world.standableLevel((int) spawn.x, (int) spawn.z, (int) spawn.y, 2);
        if (level < 0) {
            return -1;
        }
        return grid.nearestNode(spawn.x, level, spawn.z);
    }

    private int reachableCount(NavGrid grid, int start) {
        boolean[] seen = new boolean[grid.nodeCapacity()];
        int[] queue = new int[grid.nodeCapacity()];
        int[] neighbours = new int[8 * NavGrid.MAX_LEVELS];
        int head = 0;
        int tail = 0;
        seen[start] = true;
        queue[tail++] = start;
        while (head < tail) {
            int node = queue[head++];
            int count = grid.neighbours(node, neighbours);
            for (int i = 0; i < count; i++) {
                int neighbour = neighbours[i];
                if (!seen[neighbour]) {
                    seen[neighbour] = true;
                    queue[tail++] = neighbour;
                }
            }
        }
        return tail;
    }
}
