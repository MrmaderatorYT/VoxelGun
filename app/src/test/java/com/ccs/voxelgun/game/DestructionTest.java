package com.ccs.voxelgun.game;

import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.voxel.VoxelWorld;
import org.junit.Test;
import static org.junit.Assert.*;

public class DestructionTest {
    @Test public void removalsUpdateHeightCollisionNavigationAndAdjacentChunkVersions() {
        VoxelWorld w = new VoxelWorld(32, 16, 32);
        try {
            for (int z = 0; z < 32; z++) for (int x = 0; x < 32; x++) w.set(x, 2, z, Materials.CONCRETE);
            for (int y = 3; y <= 5; y++) w.set(15, y, 12, Materials.CRATE);
            w.set(16, 3, 12, Materials.STEEL);
            w.commit(); NavGrid nav = new NavGrid(w);
            assertEquals(6, w.height(15, 12));
            assertTrue(new Destruction(w, nav).blast(15.5f, 4.5f, 12.5f, 3) > 0);
            assertEquals(3, w.height(15, 12));
            assertEquals(Materials.AIR, w.get(15, 3, 12));
            assertEquals(Materials.STEEL, w.get(16, 3, 12));
            assertTrue(w.chunkVersion(0) > 0); assertTrue(w.chunkVersion(1) > 0);
            int node = nav.nearestNode(15.5f, 3, 12.5f);
            assertEquals(3, nav.nodeY(node));
            assertFalse(w.boxCollides(15.5f, 3, 12.5f, .3f, 1.8f));
            assertFalse(w.removeDestructible(0, 2, 0));
        } finally { w.release(); }
    }

    @Test public void blastIsBoundedAndNoopDoesNotInvalidateWorld() {
        VoxelWorld w = new VoxelWorld(32, 16, 32);
        try {
            NavGrid nav = new NavGrid(w); Destruction destruction = new Destruction(w, nav);
            assertEquals(0, destruction.blast(10, 5, 10, 999));
            assertEquals(0, w.revision());
            for (int z = 4; z < 20; z++) for (int y = 3; y < 12; y++)
                for (int x = 4; x < 20; x++) w.set(x, y, z, Materials.WOOD);
            w.commit();
            int count = destruction.blast(12, 6, 12, 999);
            assertEquals(128, count);
            assertEquals(128, w.revision());
        } finally { w.release(); }
    }
}
