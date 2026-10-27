package com.ccs.voxelstrike;

import android.content.Context;
import android.graphics.Rect;
import android.opengl.GLSurfaceView;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.ccs.voxelstrike.app.GameSettings;
import com.ccs.voxelstrike.game.GameMode;
import com.ccs.voxelstrike.input.InputState;
import com.ccs.voxelstrike.map.Maps;
import com.ccs.voxelstrike.ui.MatchActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** Training retry uses real touch handling; battlefield pause exposes no bot commands. */
@RunWith(AndroidJUnit4.class)
public class TacticalMenuInstrumentedTest {
    @Test public void movementPauseRetryButtonResumesAndQueuesExactlyOneRestart() {
        Context context = context();
        GameSettings original = new GameSettings(context);
        GameSettings prepared = new GameSettings(context); prepared.matchModifiers = 0; prepared.save();
        try (ActivityScenario<MatchActivity> scenario = ActivityScenario.launch(MatchActivity.intent(
                context, GameMode.MOVEMENT_TRAINING.ordinal(), 0, 0, 1))) {
            try {
                pauseWithoutGlConsumption(scenario);
                awaitControl(scenario, R.id.button_course_restart);
                onUi(scenario, activity -> {
                    View button = activity.findViewById(R.id.button_course_restart);
                    assertEquals(activity.getString(R.string.match_course_restart), ((TextView) button).getText().toString());
                    touch(activity, button);
                });
                // ACTION_UP posts the button's PerformClick callback. Its effects cannot be
                // asserted in the same UI runnable that dispatched the touch.
                awaitVisibility(scenario, R.id.pause_overlay, View.GONE);
                onUi(scenario, activity -> {
                    assertEquals(View.GONE, activity.findViewById(R.id.pause_overlay).getVisibility());
                    InputState input = input(activity);
                    assertFalse(input.paused);
                    assertTrue(input.consumeCourseRestart());
                    assertFalse(input.consumeCourseRestart());
                });
            } finally {
                finishBeforeScenarioClose(scenario);
            }
        } finally { original.save(); }
    }

    @Test public void battlefieldPauseHasNoBotCommandsOrTrainingRetry() {
        Context context = context();
        GameSettings original = new GameSettings(context);
        GameSettings prepared = new GameSettings(context);
        prepared.matchModifiers = 0; prepared.battlefieldTotalPlayers = 4; prepared.save();
        try (ActivityScenario<MatchActivity> scenario = ActivityScenario.launch(MatchActivity.intent(
                context, GameMode.BATTLE_FIELD.ordinal(), Maps.firstBattlefieldIndex(), 3, 1))) {
            try {
                pauseWithoutGlConsumption(scenario);
                onUi(scenario, activity -> {
                    View restart = activity.findViewById(R.id.button_course_restart);
                    assertEquals("Course retry is training-only", View.GONE, restart.getVisibility());
                    assertFalse(restart.isShown());
                    InputState input = input(activity);
                    assertTrue(input.paused);
                    assertEquals(View.VISIBLE, activity.findViewById(R.id.pause_overlay).getVisibility());
                    assertFalse(input.consumeCourseRestart());
                    touch(activity, activity.findViewById(R.id.button_resume));
                });
                awaitVisibility(scenario, R.id.pause_overlay, View.GONE);
                onUi(scenario, activity -> assertFalse(input(activity).paused));
            } finally {
                finishBeforeScenarioClose(scenario);
            }
        } finally { original.save(); }
    }

    private static void finishBeforeScenarioClose(ActivityScenario<MatchActivity> scenario) {
        // Both tests deliberately stop only the GL consumer, not the Activity. Loading
        // animation and the HUD pump can therefore keep the UI busy after Resume. Core
        // 1.5.0's close() waits for global idleness before requesting finish, which can
        // stall indefinitely. Request the real Activity lifecycle transition on the
        // main thread first; onPause stops the HUD pump before Scenario closes normally.
        onUi(scenario, MatchActivity::finish);
    }

    private static void pauseWithoutGlConsumption(ActivityScenario<MatchActivity> scenario) {
        onUi(scenario, activity -> {
            // Stop the GL consumer so the UI mailbox assertions cannot race a rendered frame.
            ((GLSurfaceView) activity.findViewById(R.id.surface)).onPause();
            pause(activity);
        });
        // A stopped GL consumer can leave the loading overlay visible while the HUD
        // keeps posting updates. Wait for the control we need, not global UI idleness.
        awaitControl(scenario, R.id.button_resume);
    }

    private static void pause(MatchActivity activity) {
        // A legitimate focus/orientation transition can already have paused a fresh
        // activity. The setup needs a paused state, not an unconditional ESC toggle.
        if (activity.findViewById(R.id.pause_overlay).getVisibility() == View.VISIBLE) {
            assertTrue("Visible pause overlay must agree with input state", input(activity).paused);
            return;
        }
        long now = SystemClock.uptimeMillis();
        assertTrue(activity.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_ESCAPE, 0, 0, -1, 0, 0, InputDevice.SOURCE_KEYBOARD)));
        assertTrue(activity.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_ESCAPE, 0, 0, -1, 0, 0, InputDevice.SOURCE_KEYBOARD)));
        assertEquals(View.VISIBLE, activity.findViewById(R.id.pause_overlay).getVisibility());
    }

    private static void touch(MatchActivity activity, View target) {
        assertTrue(target.isShown()); assertTrue(target.isEnabled());
        assertTrue(target.getWidth() > 0 && target.getHeight() > 0);
        int[] position = new int[2]; target.getLocationInWindow(position);
        float x = position[0] + target.getWidth() / 2f, y = position[1] + target.getHeight() / 2f;
        Rect visible = new Rect();
        assertTrue("Pause control must intersect the visible window", target.getGlobalVisibleRect(visible));
        Log.i("TacticalMenuTest", "touch center=" + x + "," + y + " visible=" + visible
                + " size=" + target.getWidth() + "x" + target.getHeight()
                + " focus=" + activity.hasWindowFocus());
        long now = SystemClock.uptimeMillis();
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent event = MotionEvent.obtain(now, now + (action == MotionEvent.ACTION_UP ? 30 : 0),
                    action, x, y, 0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try { activity.dispatchTouchEvent(event); } finally { event.recycle(); }
        }
    }

    private static void awaitControl(ActivityScenario<MatchActivity> scenario, int id) {
        AtomicBoolean ready = new AtomicBoolean();
        long deadline = SystemClock.uptimeMillis() + 10000;
        do {
            onUi(scenario, activity -> {
                View view = activity.findViewById(id);
                Rect visible = new Rect();
                ready.set(activity.hasWindowFocus() && view.isShown() && view.isEnabled()
                        && view.getWidth() > 0 && view.getHeight() > 0
                        && view.getGlobalVisibleRect(visible) && !visible.isEmpty());
            });
            if (ready.get()) return;
            SystemClock.sleep(25);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("Pause control did not become visible, laid out and focused within 10 seconds");
    }

    private static void awaitVisibility(ActivityScenario<MatchActivity> scenario, int id, int expected) {
        AtomicInteger observed = new AtomicInteger(-1);
        long deadline = SystemClock.uptimeMillis() + 10000;
        do {
            onUi(scenario, activity -> observed.set(activity.findViewById(id).getVisibility()));
            if (observed.get() == expected) return;
            SystemClock.sleep(25);
        } while (SystemClock.uptimeMillis() < deadline);
        assertEquals("Touch callback did not change the overlay within 10 seconds", expected, observed.get());
    }

    private static void onUi(ActivityScenario<MatchActivity> scenario,
            ActivityScenario.ActivityAction<MatchActivity> action) {
        // ActivityScenario 1.5.0 waits for global idleness before onActivity when
        // called off-main. This FPS has a continuous HUD pump. Enter on the main
        // thread instead, retaining Scenario's current-Activity/recreation checks;
        // Android's default ControlledLooper is a no-op, unlike Robolectric's.
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> scenario.onActivity(action));
    }

    private static InputState input(MatchActivity activity) {
        try {
            Field field = MatchActivity.class.getDeclaredField("input"); field.setAccessible(true);
            return (InputState) field.get(activity);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
}
