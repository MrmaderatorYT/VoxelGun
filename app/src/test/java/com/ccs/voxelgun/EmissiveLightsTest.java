package com.ccs.voxelgun;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ccs.voxelgun.map.GameMap;
import com.ccs.voxelgun.map.Maps;
import com.ccs.voxelgun.voxel.EmissiveLights;
import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.voxel.VoxelWorld;

import org.junit.Test;

public class EmissiveLightsTest {
    @Test
    public void onlyEmissiveBlocksBecomeLights() {
        VoxelWorld world = new VoxelWorld(16, 16, 16);
        for (int x = 0; x < 16; x++) {
            world.set(x, 0, 0, Materials.CONCRETE);
        }
        world.commit();
        assertEquals(0, EmissiveLights.build(world).count());

        world.set(8, 4, 8, Materials.LAMP);
        world.commit();
        EmissiveLights lights = EmissiveLights.build(world);
        assertEquals(1, lights.count());
        // Position is the centre of the block, colour is non-zero.
        assertEquals(8.5f, lights.data()[0], 1e-4f);
        assertEquals(4.5f, lights.data()[1], 1e-4f);
        assertEquals(8.5f, lights.data()[2], 1e-4f);
        assertTrue(lights.data()[3] > 0f);
        assertTrue(lights.data()[4] + lights.data()[5] + lights.data()[6] > 0f);
        world.release();
    }

    @Test
    public void blocksBuriedInsideGeometryDoNotEmit() {
        VoxelWorld world = new VoxelWorld(16, 16, 16);
        for (int z = 6; z <= 10; z++) {
            for (int y = 6; y <= 10; y++) {
                for (int x = 6; x <= 10; x++) {
                    world.set(x, y, z, Materials.CONCRETE);
                }
            }
        }
        world.set(8, 8, 8, Materials.LAMP);  // fully enclosed
        world.commit();
        assertEquals(0, EmissiveLights.build(world).count());
        world.release();
    }

    @Test
    public void emissiveMapsProduceLightsWithinTheBudget() {
        for (int index = 0; index < Maps.count(); index++) {
            GameMap map = Maps.create(index);
            VoxelWorld world = map.build(7L + index);
            EmissiveLights lights = EmissiveLights.build(world);
            assertTrue(map.id() + " exceeded the light budget",
                    lights.count() <= EmissiveLights.MAX_LIGHTS);
            if (map.environment().indoor) {
                assertTrue(map.id() + " is indoors but has no lights", lights.count() > 0);
            }
            world.release();
        }
    }
}
