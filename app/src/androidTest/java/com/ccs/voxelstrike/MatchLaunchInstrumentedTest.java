package com.ccs.voxelstrike;

import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.ccs.voxelstrike.game.GameMode;
import com.ccs.voxelstrike.game.Match;
import com.ccs.voxelstrike.map.Maps;
import com.ccs.voxelstrike.ui.MatchActivity;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Launch through Android's real singleTask routing, not a direct lifecycle callback. */
@RunWith(AndroidJUnit4.class)
public class MatchLaunchInstrumentedTest {
    @Test public void choosingAnotherMapReplacesStoppedMatch() {
        try (ActivityScenario<MatchActivity> scenario = ActivityScenario.launch(request(0))) {
            AtomicReference<Match> previous = new AtomicReference<>();
            AtomicReference<MatchActivity> previousActivity = new AtomicReference<>();
            for (int map : new int[]{1, 0}) {
                scenario.onActivity(activity -> {
                    previous.set(match(activity)); previousActivity.set(activity);
                });
                scenario.moveToState(Lifecycle.State.CREATED);
                launch(request(map));
                awaitReplacement(scenario, previous.get(), Maps.create(map).id());
                assertTrue("Old match Activity must complete onDestroy", previousActivity.get().isDestroyed());
                assertEquals("Old native voxel volume must be released", 0L,
                        previous.get().world().nativeHandle());
            }
        }
    }

    @Test public void startingSameMapStillCreatesFreshMatch() {
        try (ActivityScenario<MatchActivity> scenario = ActivityScenario.launch(request(0))) {
            AtomicReference<Match> previous = new AtomicReference<>();
            scenario.onActivity(activity -> previous.set(match(activity)));
            launch(request(0));
            awaitReplacement(scenario, previous.get(), Maps.create(0).id());
        }
    }

    @Test public void replayingOriginalLaunchOnlyResumesExistingMatch() {
        Intent original = request(0);
        try (ActivityScenario<MatchActivity> scenario = ActivityScenario.launch(original)) {
            AtomicReference<Match> previous = new AtomicReference<>();
            scenario.onActivity(activity -> previous.set(match(activity)));
            scenario.moveToState(Lifecycle.State.CREATED);
            launch(original);
            scenario.moveToState(Lifecycle.State.RESUMED);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                assertSame(previous.get(), match(activity));
                assertEquals(View.VISIBLE, activity.findViewById(R.id.pause_overlay).getVisibility());
            });
        }
    }

    private Intent request(int map) {
        return MatchActivity.intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                GameMode.RANGE.ordinal(), map, 0, 1);
    }

    private void launch(Intent intent) {
        // Do not copy ActivityScenario's CLEAR_TASK flag when returning to an existing task.
        InstrumentationRegistry.getInstrumentation().getTargetContext()
                .startActivity(new Intent(intent).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void awaitReplacement(ActivityScenario<MatchActivity> scenario, Match old, String mapId) {
        AtomicBoolean replaced = new AtomicBoolean();
        long deadline = SystemClock.uptimeMillis() + 20000;
        do {
            try {
                scenario.onActivity(activity -> {
                    Match current = match(activity);
                    replaced.set(current != old && current.map().id().equals(mapId)
                            && activity.findViewById(R.id.pause_overlay).getVisibility() == View.GONE);
                });
            } catch (IllegalStateException rebuilding) {
                // The old Activity may be between destruction and the new onCreate.
            }
            if (replaced.get()) return;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("Start reused the old match instead of creating map " + mapId);
    }

    private static Match match(MatchActivity activity) {
        try {
            Field field = MatchActivity.class.getDeclaredField("match");
            field.setAccessible(true);
            return (Match) field.get(activity);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
}
