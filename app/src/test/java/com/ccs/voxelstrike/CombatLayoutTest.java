package com.ccs.voxelstrike;

import com.ccs.voxelstrike.game.NavGrid;
import com.ccs.voxelstrike.game.PathFinder;
import com.ccs.voxelstrike.map.*;
import com.ccs.voxelstrike.voxel.VoxelWorld;
import org.junit.Test;
import static org.junit.Assert.*;

public class CombatLayoutTest {
    @Test public void villageGardenCrosslinksAndTowerFlankDoorsStayOpen() {
        VoxelWorld world = Maps.create(3).build(99);
        try {
            for (int z = 32; z <= 58; z++) assertWalkable(world, 36, z);
            for (int z = 70; z <= 96; z++) assertWalkable(world, 90, z);
            assertWalkable(world, 36, 112); assertWalkable(world, 42, 104);
            assertWalkable(world, 48, 106);
        } finally { world.release(); }
    }

    @Test public void dustHasIndependentCoveredNorthAndSouthSpawnExits() {
        VoxelWorld world = Maps.create(0).build(99);
        try {
            for (int x : new int[]{22, 105}) {
                for (int z = 34; z <= 48; z++) assertWalkable(world, x, z);
                for (int z = 80; z <= 112; z++) assertWalkable(world, x, z);
            }
            assertWalkable(world, 30, 64); assertWalkable(world, 97, 64);
            assertFalse(world.lineOfSight(16.5f, 4.62f, 64.5f, 111.5f, 4.62f, 64.5f));
        } finally { world.release(); }
    }

    @Test public void facilityThreeRoutesAndWingCrosslinksConnectBothTeams() {
        GameMap map = Maps.create(4);
        VoxelWorld world = map.build(99);
        try {
            NavGrid nav = new NavGrid(world);
            PathFinder finder = new PathFinder(nav);
            PathFinder.Path path = new PathFinder.Path(4096);
            SpawnPoint blue = map.blueSpawns().get(0), red = map.redSpawns().get(0);
            int a = nav.nearestNode(blue.x, 3, blue.z), b = nav.nearestNode(red.x, 3, red.z);
            for (int[] route : new int[][]{{28, 100}, {65, 64}, {100, 28}}) {
                int mid = nav.nearestNode(route[0] + .5f, 3, route[1] + .5f);
                assertTrue(finder.find(a, mid, path)); assertTrue(finder.find(mid, b, path));
            }
            assertWalkable(world, 40, 64); assertWalkable(world, 90, 64);
            assertWalkable(world, 65, 63); assertWalkable(world, 65, 74);
        } finally { world.release(); }
    }

    @Test public void battlefieldCoverPreservesThreeAvenueCentresAndProtectsDeployment() {
        for (int theme = 0; theme < 3; theme++) {
            VoxelWorld world = Maps.create(Maps.firstBattlefieldIndex() + theme).build(999);
            try {
                for (int z : new int[]{96, 288, 480})
                    for (int x = 48; x <= 590; x += 7) assertWalkable(world, x, z);
                // The centre avenue stays open end-to-end. Offset deployment slots sit behind
                // the authored screens so spawning soldiers can choose a sheltered exit.
                assertFalse("exposed deployment for theme " + theme,
                        world.lineOfSight(8.5f, 4.62f, 263.5f, 631.5f, 4.62f, 308.5f));
                assertWalkable(world, 32, 288); assertWalkable(world, 607, 288);
                assertWalkable(world, 42, 282); assertWalkable(world, 597, 294);
            } finally { world.release(); }
        }
    }

    private void assertWalkable(VoxelWorld world, int x, int z) {
        assertEquals("blocked ground passage at " + x + "," + z, 3,
                world.standableLevel(x, z, 3, 2));
    }
}
