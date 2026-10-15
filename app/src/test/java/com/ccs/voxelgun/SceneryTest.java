package com.ccs.voxelgun;

import com.ccs.voxelgun.map.*;
import com.ccs.voxelgun.map.scenery.*;
import com.ccs.voxelgun.math.Frustum;
import com.ccs.voxelgun.voxel.*;
import org.junit.Test;
import java.nio.FloatBuffer;
import static org.junit.Assert.*;

/** Scenery is geometry, not an extra invisible physics/navigation obstacle. */
public class SceneryTest {
    @Test public void signsReadLeftToRightFromTheirNorthFacingFront() {
        MapScenery s = new MapScenery();
        DecorRecipes.sign(s, 10, 3, 10, "FL", 1, Materials.TRIM_BLUE);
        // The first three pixels of F run screen-left to screen-right, hence descending X.
        assertEquals(16, s.boxes().get(1).x, 0);
        assertEquals(15, s.boxes().get(2).x, 0);
        assertEquals(14, s.boxes().get(3).x, 0);
        assertTrue(s.boxes().get(1).z < s.boxes().get(0).z);
    }
    @Test public void cuboidFacesHaveOutwardWindingAndDirectNativeBuffers() {
        MapScenery s = new MapScenery();
        s.box(2, 3, 4, 5, 6, 7, Materials.BRICK_TEAL);
        DecorationBatch batch = DecorationMesher.build(s)[0];
        MeshData mesh = batch.mesh;
        assertEquals(24, mesh.vertexCount()); assertEquals(36, mesh.indexCount());
        assertTrue(mesh.vertices().isDirect()); assertTrue(mesh.indices().isDirect());
        FloatBuffer v = mesh.vertices();
        for (int face = 0; face < 6; face++) {
            int a = face * 4 * 8, b = a + 8, c = a + 16;
            float ux = v.get(b) - v.get(a), uy = v.get(b+1) - v.get(a+1), uz = v.get(b+2) - v.get(a+2);
            float vx = v.get(c) - v.get(a), vy = v.get(c+1) - v.get(a+1), vz = v.get(c+2) - v.get(a+2);
            float dot = (uy*vz - uz*vy)*v.get(a+3) + (uz*vx - ux*vz)*v.get(a+4)
                    + (ux*vy - uy*vx)*v.get(a+5);
            assertTrue("face " + face, dot > 0);
            assertEquals(Materials.BRICK_TEAL & 255, (int) v.get(a+6));
        }
        assertEquals(2, batch.minX, 0); assertEquals(9, batch.maxY, 0);
        assertEquals(11, batch.maxZ, 0);
    }

    @Test public void spatialBatchingDoesNotSplitByMaterialOrClipWideBoxes() {
        MapScenery s = new MapScenery();
        s.box(0, 0, 0, 1, 1, 1, Materials.COPPER);
        s.box(5, 0, 0, 1, 1, 1, Materials.WOOD);
        s.box(60, 0, 0, 80, 2, 1, Materials.STEEL);
        DecorationBatch[] batches = DecorationMesher.build(s);
        assertEquals(2, batches.length);
        assertEquals(48, batches[0].mesh.vertexCount());
        assertEquals(60, batches[1].minX, 0); assertEquals(140, batches[1].maxX, 0);
        Frustum frustum = new Frustum();
        frustum.update(new float[]{1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1});
        assertTrue(frustum.containsBox(0,0,0,1,1,1));
        assertFalse(frustum.containsBox(60,0,0,140,2,1));
        assertEquals(0, DecorationMesher.build(new MapScenery()).length);
    }

    @Test public void flowersFenceAndSignNeverEnterCollisionOrRaycastVolume() {
        VoxelWorld world = new VoxelWorld(32, 16, 32);
        try {
            MapBuilder b = new MapBuilder(world, 3);
            b.fill(0, 0, 0, 31, 2, 31, Materials.CONCRETE);
            DecorRecipes.flowers(b.scenery(), 8, 3, 8, Materials.CORAL);
            DecorRecipes.fence(b.scenery(), 8, 3, 10, 8, Materials.WOOD);
            DecorRecipes.standingSign(b.scenery(), 12, 3, 12, "STOP", .2f, Materials.TRIM_RED);
            assertTrue(b.scenery().boxes().size() > 30);
            for (int z = 7; z <= 15; z++) for (int x = 7; x <= 21; x++) {
                assertFalse(world.boxCollides(x + .5f, 3.01f, z + .5f, .3f, 1.8f));
                assertEquals(Materials.AIR, world.get(x, 4, z));
            }
            assertFalse(world.raycast(4, 4, 10, 1, 0, 0, 20, false, new RayHit()));
        } finally { world.release(); }
    }

    @Test public void areaChoosesNearestCalloutAndReusesItsString() {
        MapScenery s = new MapScenery();
        s.landmark("Bridge", 10, 10, 12); s.landmark("Station", 20, 10, 12);
        assertSame(s.landmarks().get(0).name, s.area(11, 10));
        assertSame(s.landmarks().get(1).name, s.area(19, 10));
        assertEquals("", s.area(90, 90));
    }

    @Test public void entireCatalogAndTrainingHaveBoundedCalloutsAndOpaqueDecor() {
        for (int index = 0; index <= Maps.count(); index++) {
            GameMap map = index == Maps.count()
                    ? new com.ccs.voxelgun.map.maps.MovementCourse() : Maps.create(index);
            String id = map.id();
            VoxelWorld world = map.build(9);
            try {
                assertTrue(id, map.scenery().landmarks().size() >= 3);
                assertTrue(id, map.scenery().boxes().size() > 30);
                for (DecorBox box : map.scenery().boxes()) {
                    assertTrue(id + " negative decor", box.x >= 0 && box.y >= 0 && box.z >= 0);
                    assertTrue(id + " unbounded decor", box.x + box.width <= world.sizeX()
                            && box.y + box.height <= world.sizeY() && box.z + box.depth <= world.sizeZ());
                    assertTrue(id + " transparent decor", Materials.opaque(box.material));
                }
                for (SpawnPoint spawn : map.freeSpawns()) {
                    int level = world.standableLevel((int) spawn.x, (int) spawn.z, (int) spawn.y, 2);
                    assertTrue(id + " missing spawn floor", level >= 0);
                    assertFalse(id + " blocked spawn " + spawn.x + ":" + spawn.z, world.boxCollides(spawn.x, level + .01f,
                            spawn.z, .3f, 1.8f));
                }
            } finally { world.release(); }
        }
    }

    @Test public void rebuildingMapDoesNotDuplicateDecorAndIsDeterministic() {
        GameMap map = Maps.create(3);
        VoxelWorld world = map.build(7);
        int boxes = map.scenery().boxes().size();
        long hash = decorHash(map.scenery()); world.release();
        world = map.build(7);
        try {
            assertEquals(boxes, map.scenery().boxes().size());
            assertEquals(hash, decorHash(map.scenery()));
            assertEquals(3, map.scenery().landmarks().size());
        } finally { world.release(); }
    }

    @Test public void alpineLandmarksHaveRealWalkableGeometry() {
        VoxelWorld world = Maps.create(Maps.firstBattlefieldIndex() + 2).build(9);
        try {
            for (int x = 365; x <= 443; x++) {
                assertEquals(Materials.DECK_WOOD, world.get(x, 8, 221));
                assertFalse("bridge " + x, world.boxCollides(x + .5f, 9.01f, 221.5f, .3f, 1.8f));
            }
            assertTrue("gorge bedrock", world.solid(403, 0, 221));
            assertFalse("station door", world.boxCollides(379.5f, 3.01f, 328.5f, .3f, 1.8f));
            assertFalse("hotel lobby door", world.boxCollides(225.5f, 3.01f, 161.5f, .3f, 1.8f));
            assertTrue("station control tower", world.solid(404, 30, 356));
            int ice = 0, trunks = 0;
            for (int z = 0; z < world.sizeZ(); z++) for (int x = 0; x < world.sizeX(); x++) {
                if (world.get(x, 2, z) == Materials.ICE) ice++;
                for (int y = 3; y < world.sizeY(); y++) {
                    if (world.get(x, y, z) == Materials.TRUNK) { trunks++; break; }
                }
            }
            assertTrue("frozen lake", ice > 1000);
            assertTrue("forest clusters", trunks > 70);
        } finally { world.release(); }
    }

    @Test public void supermarketEntrancesAndVillageBalconyRemainUsable() {
        VoxelWorld city = Maps.create(Maps.firstBattlefieldIndex()).build(9);
        try {
            for (int[] door : new int[][]{{214,126},{188,151},{242,175},{277,151}}) {
                assertFalse("supermarket door", city.boxCollides(door[0] + .5f, 3.01f,
                        door[1] + .5f, .3f, 1.8f));
            }
            assertTrue("supermarket roof", city.solid(218, 14, 151));
        } finally { city.release(); }
        VoxelWorld village = Maps.create(3).build(9);
        try {
            assertTrue(village.solid(85, 7, 14));
            assertFalse(village.boxCollides(85.5f, 8.01f, 16.5f, .3f, 1.8f));
            assertEquals(Materials.BRICK_TEAL, village.get(78, 4, 17));
        } finally { village.release(); }
    }

    private static long decorHash(MapScenery scenery) {
        long hash = 17;
        for (DecorBox b : scenery.boxes()) {
            hash = hash * 31 + Float.floatToIntBits(b.x); hash = hash * 31 + Float.floatToIntBits(b.y);
            hash = hash * 31 + Float.floatToIntBits(b.z); hash = hash * 31 + b.material;
            hash = hash * 31 + Float.floatToIntBits(b.width); hash = hash * 31 + Float.floatToIntBits(b.height);
            hash = hash * 31 + Float.floatToIntBits(b.depth);
        }
        return hash;
    }
}
