package com.ccs.voxelstrike;

import com.ccs.voxelstrike.map.Maps;
import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.voxel.VoxelWorld;
import org.junit.Test;
import static org.junit.Assert.*;

/** Visual terrain layers must not change collision heights or turn turf into random dirt cubes. */
public class TerrainStyleTest {
    @Test public void villageHasContinuousTurfOverSoil() {
        // Locate by id rather than relying on menu ordering.
        for (int i = 0; i < Maps.count(); i++) {
            if (!Maps.create(i).id().equals("village")) continue;
            VoxelWorld world = Maps.create(i).build(19L);
            try {
                int turf = 0, earthBelow = 0;
                for (int z = 5; z < world.sizeZ() - 5; z++) {
                    for (int x = 5; x < world.sizeX() - 5; x++) {
                        if (world.get(x, 2, z) == Materials.GRASS) {
                            turf++;
                            if (world.get(x, 1, z) == Materials.DIRT) earthBelow++;
                        }
                    }
                }
                assertTrue("Village must have broad green lawns", turf > 4000);
                assertEquals("Turf must sit on earth, not speckled green stone", turf, earthBelow);
                return;
            } finally { world.release(); }
        }
        fail("Village missing from catalog");
    }

    @Test public void alpineRoadsAndGrassHaveDistinctSurfaceLayers() {
        VoxelWorld world = Maps.create(Maps.firstBattlefieldIndex() + 2).build(9L);
        try {
            assertEquals(Materials.ASPHALT, world.get(322, 2, 110));
            assertEquals(Materials.CONCRETE, world.get(320, 2, 97));
            int lawns = 0;
            for (int z = 8; z < world.sizeZ() - 8; z++) {
                for (int x = 8; x < world.sizeX() - 8; x++) {
                    int y = world.height(x, z) - 1;
                    int material = world.get(x, y, z) & 255;
                    if (material == Materials.GRASS || material == Materials.GRASS_BRIGHT) {
                        assertEquals("Root layer under alpine turf", Materials.DIRT, world.get(x, y - 1, z));
                        lawns++;
                    }
                }
            }
            assertTrue("Alps must retain broad meadows", lawns > 10000);
        } finally { world.release(); }
    }
}
