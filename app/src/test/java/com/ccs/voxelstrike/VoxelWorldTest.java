package com.ccs.voxelstrike;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.voxel.RayHit;
import com.ccs.voxelstrike.voxel.VoxelWorld;

import org.junit.Test;

/** Exercises the pure-Java traversal path, which is also the fallback on JNI-less devices. */
public class VoxelWorldTest {
    private VoxelWorld world() {
        VoxelWorld world = new VoxelWorld(32, 32, 32);
        for (int z = 0; z < 32; z++) {
            for (int x = 0; x < 32; x++) {
                world.set(x, 0, z, Materials.CONCRETE);
            }
        }
        world.commit();
        return world;
    }

    @Test
    public void heightMapTracksTheTopSolidVoxel() {
        VoxelWorld world = world();
        assertEquals(1, world.height(5, 5));
        world.set(5, 4, 5, Materials.CRATE);
        world.commit();
        assertEquals(5, world.height(5, 5));
    }

    @Test
    public void rayStopsAtTheFirstOpaqueVoxel() {
        VoxelWorld world = world();
        world.set(10, 2, 5, Materials.CONCRETE);
        world.commit();

        RayHit hit = new RayHit();
        assertTrue(world.raycast(1.5f, 2.5f, 5.5f, 1f, 0f, 0f, 30f, false, hit));
        assertEquals(10, hit.blockX);
        assertEquals(Materials.CONCRETE, hit.material);
        assertEquals(-1f, hit.normalX, 1e-4f);
    }

    @Test
    public void glassDoesNotStopBulletsButStopsTheCameraProbe() {
        VoxelWorld world = world();
        world.set(8, 2, 5, Materials.GLASS);
        world.commit();

        RayHit hit = new RayHit();
        assertFalse(world.raycast(1.5f, 2.5f, 5.5f, 1f, 0f, 0f, 20f, false, hit));
        assertTrue(world.raycast(1.5f, 2.5f, 5.5f, 1f, 0f, 0f, 20f, true, hit));
        assertEquals(Materials.GLASS & 0xFF, hit.material);
    }

    @Test
    public void lineOfSightIsBlockedByWalls() {
        VoxelWorld world = world();
        assertTrue(world.lineOfSight(2f, 2f, 2f, 20f, 2f, 2f));
        for (int y = 1; y < 6; y++) {
            world.set(10, y, 2, Materials.CONCRETE);
        }
        world.commit();
        assertFalse(world.lineOfSight(2f, 2f, 2.5f, 20f, 2f, 2.5f));
    }

    @Test
    public void boxCollisionDetectsFloorAndWalls() {
        VoxelWorld world = world();
        assertFalse(world.boxCollides(5.5f, 1.0f, 5.5f, 0.34f, 1.8f));
        assertTrue(world.boxCollides(5.5f, 0.5f, 5.5f, 0.34f, 1.8f));
        // Nothing under the world: floating island maps need bodies to fall through.
        assertFalse(world.boxCollides(5.5f, -8f, 5.5f, 0.34f, 1.8f));
    }
}
