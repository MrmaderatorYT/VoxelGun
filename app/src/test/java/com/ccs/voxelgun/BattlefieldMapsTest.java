package com.ccs.voxelgun;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ccs.voxelgun.map.GameMap;
import com.ccs.voxelgun.game.NavGrid;
import com.ccs.voxelgun.game.PathFinder;
import com.ccs.voxelgun.map.Maps;
import com.ccs.voxelgun.map.SpawnPoint;
import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.voxel.VoxelWorld;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/** Locks in the scale and defining terrain of the three maps built for Battle Field. */
public class BattlefieldMapsTest {
    @Test
    public void alpineSectorJoinsHaveContinuousGroundInsteadOfStraightTrenches() {
        VoxelWorld world = Maps.create(Maps.firstBattlefieldIndex() + 2).build(9L);
        try {
            for (int z = 8; z < world.sizeZ() - 8; z++) {
                for (int x = 8; x < world.sizeX() - 8; x++) {
                    int h = groundTop(world, x, z);
                    assertTrue("ground void at " + x + ":" + z, h >= 2);
                    assertTrue("sharp east slope at " + x + ":" + z,
                            Math.abs(h - groundTop(world, x - 1, z)) <= 1);
                    assertTrue("sharp north slope at " + x + ":" + z,
                            Math.abs(h - groundTop(world, x, z - 1)) <= 1);
                }
            }
            for (int seam : new int[]{192, 384, 576}) {
                for (int z = 8; z < world.sizeZ() - 8; z++) {
                    for (int x = seam - 8; x <= seam + 8; x++) {
                        assertTrue("cliff at sector join x=" + x + " z=" + z,
                                Math.abs(groundTop(world, x, z) - groundTop(world, x - 1, z)) <= 1);
                    }
                }
            }
            for (int seam : new int[]{192, 384}) {
                for (int x = 8; x < world.sizeX() - 8; x++) {
                    for (int z = seam - 8; z <= seam + 8; z++) {
                        assertTrue("cliff at sector join z=" + z + " x=" + x,
                                Math.abs(groundTop(world, x, z) - groundTop(world, x, z - 1)) <= 1);
                    }
                }
            }
        } finally { world.release(); }
    }

    private static int groundTop(VoxelWorld world, int x, int z) {
        int top = -1;
        for (int y = 0; y < world.sizeY(); y++) {
            int slot = Materials.slot(world.get(x, y, z));
            if (slot == Materials.slot(Materials.STONE) || slot == Materials.slot(Materials.DIRT)) {
                top = y;
            } else {
                if (slot == Materials.slot(Materials.SNOW) || slot == Materials.slot(Materials.GRASS)
                        || slot == Materials.slot(Materials.GRASS_BRIGHT)
                        || slot == Materials.slot(Materials.STONE_LIGHT)
                        || slot == Materials.slot(Materials.CONCRETE)
                        || slot == Materials.slot(Materials.ASPHALT)
                        || slot == Materials.slot(Materials.ICE)) top = y;
                break; // Ignore buildings/trees above the terrain's surface layer.
            }
        }
        return top;
    }

    @Test
    public void catalogHasThreeLargeMapsWithFiftyUniqueDeploymentsPerSide() {
        int first = Maps.firstBattlefieldIndex();
        assertEquals(3, Maps.count() - first);
        for (int index = first; index < Maps.count(); index++) {
            assertTrue(Maps.isBattlefield(index));
            GameMap map = Maps.create(index);
            VoxelWorld world = map.build(1000L + index);
            try {
                assertEquals(640, world.sizeX());
                assertEquals(64, world.sizeY());
                assertEquals(576, world.sizeZ());
                assertEquals(10 * 192 * 192, world.sizeX() * world.sizeZ());
                assertTrue(map.redSpawns().get(0).x - map.blueSpawns().get(0).x > 600);
                // Sector joins must be routes rather than the old perimeter walls.
                for (int x : new int[]{192, 384, 576}) {
                    assertTrue(world.standableLevel(x, 96, 3, 2) >= 0);
                }
                assertTrue(world.standableLevel(16, 192, 3, 2) >= 0);
                assertTrue(world.standableLevel(16, 384, 3, 2) >= 0);
                assertEquals(50, map.blueSpawns().size());
                assertEquals(50, map.redSpawns().size());
                assertEquals(100, map.freeSpawns().size());
                assertUniqueAndStandable(map.id(), map.blueSpawns(), world);
                assertUniqueAndStandable(map.id(), map.redSpawns(), world);
            } finally {
                world.release();
            }
        }
    }

    @Test
    public void cityRetainsMultilevelBuildingsWithoutCopyingEveryDistrict() {
        VoxelWorld world = Maps.create(Maps.firstBattlefieldIndex()).build(7L);
        try {
            int maxLevels = 0;
            for (int z = 0; z < world.sizeZ(); z++) {
                for (int x = 0; x < world.sizeX(); x++) {
                    maxLevels = Math.max(maxLevels, standableLevels(world, x, z));
                }
            }
            assertTrue("city has no four-level apartment/roof column", maxLevels >= 4);
        } finally {
            world.release();
        }
    }

    @Test
    public void farmlandHasBroadFieldsAndAlpsHaveRealReliefAndSnow() {
        int first = Maps.firstBattlefieldIndex();
        VoxelWorld farmland = Maps.create(first + 1).build(8L);
        try {
            assertTrue("farmland has no harvested fields",
                    materialCount(farmland, Materials.HAY) > 1000);
            assertTrue("farmland has too little open grass",
                    materialCount(farmland, Materials.GRASS) > 5000);
        } finally {
            farmland.release();
        }

        VoxelWorld alps = Maps.create(first + 2).build(9L);
        try {
            int lowest = Integer.MAX_VALUE;
            int highest = 0;
            for (int z = 3; z < alps.sizeZ() - 3; z++) {
                for (int x = 3; x < alps.sizeX() - 3; x++) {
                    int height = alps.height(x, z);
                    lowest = Math.min(lowest, height);
                    highest = Math.max(highest, height);
                }
            }
            assertTrue("alpine terrain is too flat", highest - lowest >= 30);
            assertTrue("alpine ridges have no snow",
                    materialCount(alps, Materials.SNOW) > 500);
        } finally {
            alps.release();
        }
    }

    @Test
    public void fullWorldDesignsKeepStrategicRoutesAndObjectivesWalkable() {
        for (int index = Maps.firstBattlefieldIndex(); index < Maps.count(); index++) {
            GameMap map = Maps.create(index);
            VoxelWorld world = map.build(9L);
            try {
                for (int z : new int[]{96, 288, 480}) {
                    for (int x = 5; x < world.sizeX() - 5; x++) {
                        assertOpenGround(map.id(), world, x, z);
                    }
                }
                for (int x : new int[]{16, 160, 320, 480, 623}) {
                    for (int z = 5; z < world.sizeZ() - 5; z++) {
                        assertOpenGround(map.id(), world, x, z);
                    }
                }
                int[][] objectives = {{160,288}, {320,96}, {320,288}, {320,480}, {480,288}};
                for (int[] objective : objectives) {
                    for (int dz = -5; dz <= 5; dz++) for (int dx = -5; dx <= 5; dx++) {
                        assertOpenGround(map.id(), world, objective[0] + dx, objective[1] + dz);
                    }
                }
            } finally { world.release(); }
        }
    }

    @Test
    public void largeMapsDoNotReuseIdentical192BlockDistricts() {
        for (int index = Maps.firstBattlefieldIndex(); index < Maps.count(); index++) {
            GameMap map = Maps.create(index);
            VoxelWorld world = map.build(9L);
            try {
                Set<Long> signatures = new HashSet<>();
                for (int oz = 0; oz < 576; oz += 192) {
                    for (int ox = 0; ox < 576; ox += 192) {
                        long hash = 0xcbf29ce484222325L;
                        for (int z = oz + 4; z < oz + 188; z++) {
                            for (int y = 2; y < world.sizeY(); y++) {
                                for (int x = ox + 4; x < ox + 188; x++) {
                                    hash = (hash ^ (world.get(x,y,z) & 255)) * 0x100000001b3L;
                                }
                            }
                        }
                        assertTrue(map.id() + " repeats district " + ox + ":" + oz,
                                signatures.add(hash));
                    }
                }
            } finally { world.release(); }
        }
    }

    private static void assertOpenGround(String id, VoxelWorld world, int x, int z) {
        assertTrue(id + " missing road floor at " + x + ":" + z, world.solid(x, 2, z));
        assertFalse(id + " route blocked at " + x + ":" + z,
                world.boxCollides(x + .5f, 3.01f, z + .5f, .34f, 1.8f));
    }

    @Test
    public void newPublicInteriorsHaveConnectedEntrancesForBots() {
        VoxelWorld city = Maps.create(Maps.firstBattlefieldIndex()).build(9L);
        try {
            NavGrid nav = new NavGrid(city);
            PathFinder finder = new PathFinder(nav);
            assertPath("supermarket front/rear", nav, finder, 214, 3, 124, 242, 3, 177);
            assertPath("supermarket sales floor/stockroom", nav, finder, 186, 3, 151, 279, 3, 151);
        } finally { city.release(); }
        VoxelWorld farmland = Maps.create(Maps.firstBattlefieldIndex() + 1).build(9L);
        try {
            NavGrid nav = new NavGrid(farmland);
            assertPath("farm shop", nav, new PathFinder(nav), 235, 3, 133, 235, 3, 167);
        } finally { farmland.release(); }
        VoxelWorld alps = Maps.create(Maps.firstBattlefieldIndex() + 2).build(9L);
        try {
            NavGrid nav = new NavGrid(alps);
            PathFinder finder = new PathFinder(nav);
            assertPath("resort hotel lobby", nav, finder, 225, 3, 159, 225, 3, 196);
            assertPath("gondola station concourse", nav, finder, 379, 3, 326, 379, 3, 364);
            assertPath("gorge footbridge", nav, finder, 365, 9, 221, 443, 9, 221);
        } finally { alps.release(); }
    }

    private static void assertPath(String label, NavGrid nav, PathFinder finder,
                                   int x0, int y0, int z0, int x1, int y1, int z1) {
        int start = nav.nearestNode(x0 + .5f, y0, z0 + .5f);
        int end = nav.nearestNode(x1 + .5f, y1, z1 + .5f);
        assertTrue(label + " missing entrance", nav.valid(start) && nav.valid(end));
        assertEquals(label + " start X", x0, nav.cellX(start));
        assertEquals(label + " start Z", z0, nav.cellZ(start));
        assertEquals(label + " start Y", y0, nav.nodeY(start));
        assertEquals(label + " end X", x1, nav.cellX(end));
        assertEquals(label + " end Z", z1, nav.cellZ(end));
        assertEquals(label + " end Y", y1, nav.nodeY(end));
        assertTrue(label + " has no connected passage", finder.find(start, end,
                new PathFinder.Path(512)));
    }

    private static void assertUniqueAndStandable(String id, Iterable<SpawnPoint> spawns,
                                                  VoxelWorld world) {
        Set<String> occupied = new HashSet<>();
        for (SpawnPoint spawn : spawns) {
            assertTrue(id + " duplicates a deployment point",
                    occupied.add(spawn.x + ":" + spawn.z));
            assertTrue(id + " deployment has no footing",
                    world.standableLevel((int) spawn.x, (int) spawn.z, (int) spawn.y, 2) >= 0);
        }
    }

    private static int standableLevels(VoxelWorld world, int x, int z) {
        int levels = 0;
        for (int y = 1; y < world.sizeY() - 2; y++) {
            if (world.solid(x, y - 1, z) && !world.solid(x, y, z)
                    && !world.solid(x, y + 1, z)) {
                levels++;
            }
        }
        return levels;
    }

    private static int materialCount(VoxelWorld world, int material) {
        int count = 0;
        int wanted = Materials.slot(material);
        for (byte block : world.blocks()) {
            if (Materials.slot(block) == wanted) {
                count++;
            }
        }
        return count;
    }
}
