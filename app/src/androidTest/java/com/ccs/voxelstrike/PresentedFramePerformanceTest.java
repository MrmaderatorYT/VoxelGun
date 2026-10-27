package com.ccs.voxelstrike;

import android.opengl.GLSurfaceView;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.ccs.voxelstrike.game.GameMode;
import com.ccs.voxelstrike.game.Match;
import com.ccs.voxelstrike.map.Maps;
import com.ccs.voxelstrike.render.GameRenderer;
import com.ccs.voxelstrike.ui.MatchActivity;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real SurfaceView/vsync/HUD/simulation cadence, with the device's saved graphics choices. */
@RunWith(AndroidJUnit4.class)
public class PresentedFramePerformanceTest {
    @Test public void measurePresentedWarehouseFrames() throws Exception {
        measure("warehouse");
    }
    @Test public void measurePresentedVillageFrames() throws Exception {
        measure("village");
    }
    @Test public void measurePresentedAlpineFrames() throws Exception {
        measure("battlefield_alps");
    }
    private void measure(String mapId) throws Exception {
        int mode = mapId.startsWith("battlefield_") ? GameMode.BATTLE_FIELD.ordinal()
                : GameMode.TEAM_DEATHMATCH.ordinal();
        var intent = MatchActivity.intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                mode, Maps.indexOf(mapId), 1, 1);
        try (ActivityScenario<MatchActivity> scenario = ActivityScenario.launch(intent)) {
            Match[] match = new Match[1]; GameRenderer[] renderer = new GameRenderer[1];
            GLSurfaceView[] view = new GLSurfaceView[1];
            scenario.onActivity(activity -> {
                match[0] = (Match) field(activity, "match");
                renderer[0] = (GameRenderer) field(activity, "renderer");
                view[0] = activity.findViewById(R.id.surface);
            });
            long deadline = SystemClock.uptimeMillis() + 60000;
            boolean[] ready = new boolean[1];
            while (!ready[0] && SystemClock.uptimeMillis() < deadline) {
                CountDownLatch latch = new CountDownLatch(1);
                view[0].queueEvent(() -> { ready[0] = renderer[0].ready(); latch.countDown(); });
                assertTrue(latch.await(5, TimeUnit.SECONDS)); SystemClock.sleep(50);
            }
            assertTrue(ready[0]); SystemClock.sleep(3500);
            int[] fps = new int[16];
            for (int i = 0; i < fps.length; i++) {
                final int sample = i; CountDownLatch latch = new CountDownLatch(1);
                view[0].queueEvent(() -> { fps[sample] = match[0].snapshot().fps; latch.countDown(); });
                assertTrue(latch.await(5, TimeUnit.SECONDS)); SystemClock.sleep(500);
            }
            Arrays.sort(fps);
            Log.i("VoxelPerf", "presented_frames " + mapId + " actors=" + match[0].combatants().size()
                    + " samples=16 median_fps="
                    + fps[8] + " min_fps=" + fps[0] + " max_fps=" + fps[15]);
            assertTrue("Renderer must publish actual frame samples", fps[8] > 0);
        }
    }
    private static Object field(Object owner, String name) {
        try {
            Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
}
