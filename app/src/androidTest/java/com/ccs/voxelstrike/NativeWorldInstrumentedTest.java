package com.ccs.voxelstrike;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.ccs.voxelstrike.game.Destruction;
import com.ccs.voxelstrike.game.NavGrid;
import com.ccs.voxelstrike.voxel.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** On-device checks use the real JNI implementation, not the desktop Java fallback. */
@RunWith(AndroidJUnit4.class)
public class NativeWorldInstrumentedTest {
    @Test public void destructionKeepsNativeCollisionAndSightInSync() {
        assertTrue("Native library must be loaded on device", NativeBridge.available());
        VoxelWorld world = new VoxelWorld(32, 16, 32);
        try {
            for (int z = 0; z < 32; z++) for (int x = 0; x < 32; x++) world.set(x, 2, z, Materials.CONCRETE);
            world.set(15, 3, 12, Materials.CRATE); world.commit();
            assertTrue(world.boxCollides(15.5f, 3, 12.5f, .3f, 1.8f));
            assertFalse(world.lineOfSight(14, 3.5f, 12.5f, 17, 3.5f, 12.5f));
            assertEquals(1, new Destruction(world, new NavGrid(world)).blast(15.5f, 3.5f, 12.5f, 2));
            assertFalse(world.boxCollides(15.5f, 3, 12.5f, .3f, 1.8f));
            assertTrue(world.lineOfSight(14, 3.5f, 12.5f, 17, 3.5f, 12.5f));
            assertFalse(VoxelMesher.meshChunk(world, 0, 0, 0, VoxelMesher.PASS_OPAQUE).isEmpty());
        } finally { world.release(); }
    }
}
