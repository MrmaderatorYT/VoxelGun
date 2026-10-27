package com.ccs.voxelstrike;

import android.graphics.Bitmap;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.ccs.voxelstrike.map.GameMap;
import com.ccs.voxelstrike.map.Maps;
import com.ccs.voxelstrike.ui.MapPreview;
import com.ccs.voxelstrike.voxel.VoxelWorld;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Native build plus a visual artifact for checking the complete, expanded alpine landscape. */
@RunWith(AndroidJUnit4.class)
public class AlpinePresentationInstrumentedTest {
    @Test public void fullAlpineWorldHasUsableDeploymentsAndRendersPreview() throws Exception {
        GameMap map = Maps.create(Maps.firstBattlefieldIndex() + 2);
        VoxelWorld world = map.build(9L);
        try {
            for (var spawn : map.freeSpawns()) {
                assertTrue(world.standableLevel((int) spawn.x, (int) spawn.z, (int) spawn.y, 2) >= 0);
            }
            Bitmap preview = MapPreview.render(world, map);
            try {
                File output = new File(InstrumentationRegistry.getInstrumentation().getTargetContext()
                        .getExternalFilesDir(null), "alps-preview.png");
                try (FileOutputStream stream = new FileOutputStream(output)) {
                    assertTrue(preview.compress(Bitmap.CompressFormat.PNG, 100, stream));
                }
            } finally { preview.recycle(); }
        } finally { world.release(); }
    }
}
