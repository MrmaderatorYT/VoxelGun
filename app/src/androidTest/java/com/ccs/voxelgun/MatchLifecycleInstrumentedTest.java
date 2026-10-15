package com.ccs.voxelgun;

import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.ccs.voxelgun.game.GameMode;
import com.ccs.voxelgun.map.Maps;
import com.ccs.voxelgun.ui.HudView;
import com.ccs.voxelgun.ui.MatchActivity;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Exercise actual Android callbacks and Window event dispatch, not Button.performClick(). */
@RunWith(AndroidJUnit4.class)
public class MatchLifecycleInstrumentedTest {
    @Test public void peripheralReconnectionDoesNotRecreateTheLiveMatch() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ActivityInfo info = context.getPackageManager().getActivityInfo(
                new ComponentName(context, MatchActivity.class), 0);
        // A waking Bluetooth keyboard can change its type as well as its accessibility.
        // InputManager already handles hot-plugging; rebuilding the world would lose the
        // paused match. Check the installed manifest, not a source-only approximation.
        int peripheralChanges = ActivityInfo.CONFIG_KEYBOARD | ActivityInfo.CONFIG_KEYBOARD_HIDDEN;
        assertEquals("all dynamically handled peripheral changes must preserve the live match",
                peripheralChanges, info.configChanges & peripheralChanges);
    }

    @Test public void foregroundMatchPreventsAutomaticScreenSleep() {
        Intent intent = MatchActivity.intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                GameMode.RANGE.ordinal(), 0, 0, 1);
        try (ActivityScenario<MatchActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> assertTrue("idle gameplay must not lose its rendering surface to screen timeout",
                    (activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0));
        }
    }

    @Test public void manualScreenLockPausesAndOsTouchCanResume() throws Exception {
        KeyguardManager keyguard = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getSystemService(KeyguardManager.class);
        // This test dismisses only an unsecured keyguard; it never enters/bypasses a PIN.
        assumeTrue("Manual lock test needs an unsecured test device",
                keyguard != null && !keyguard.isKeyguardSecure());
        Intent intent = MatchActivity.intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                GameMode.RANGE.ordinal(), 0, 0, 1);
        try (ActivityScenario<MatchActivity> scenario = ActivityScenario.launch(intent)) {
            awaitState(scenario, Lifecycle.State.RESUMED);
            awaitWindowFocus(scenario);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            java.util.concurrent.atomic.AtomicReference<MatchActivity> original =
                    new java.util.concurrent.atomic.AtomicReference<>();
            scenario.onActivity(original::set);
            for (int cycle = 0; cycle < 3; cycle++) {
                try {
                    // KEEP_SCREEN_ON must prevent only idle timeout, not an explicit power-key sleep.
                    shellAndWait("input keyevent KEYCODE_SLEEP");
                    awaitState(scenario, Lifecycle.State.CREATED);
                } finally {
                    // Leave the device awake even if the lifecycle assertion above fails.
                    shellAndWait("input keyevent KEYCODE_WAKEUP");
                    shellAndWait("wm dismiss-keyguard");
                }
                awaitState(scenario, Lifecycle.State.RESUMED);
                awaitWindowFocus(scenario);
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                final float[] center = new float[2];
                scenario.onActivity(activity -> {
                    assertSame("waking peripherals must not recreate the running world", original.get(), activity);
                    assertFalse(activity.isFinishing());
                    assertEquals("Unlocking must retain the manually paused match",
                            View.VISIBLE, activity.findViewById(R.id.pause_overlay).getVisibility());
                    View button = activity.findViewById(R.id.button_resume);
                    assertTrue(button.isShown());
                    assertTrue(button.isEnabled());
                    int[] location = new int[2];
                    button.getLocationOnScreen(location);
                    center[0] = location[0] + button.getWidth() / 2f;
                    center[1] = location[1] + button.getHeight() / 2f;
                });
                long now = SystemClock.uptimeMillis();
                for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
                    MotionEvent event = MotionEvent.obtain(now, SystemClock.uptimeMillis(),
                            action, center[0], center[1], 0);
                    event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                    // Queue both events through Android rather than invoking the button directly.
                    try {
                        assertTrue(InstrumentationRegistry.getInstrumentation().getUiAutomation()
                                .injectInputEvent(event, false));
                    } finally { event.recycle(); }
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                scenario.onActivity(activity -> {
                    assertEquals("OS touch must reach Resume after manual screen lock",
                            View.GONE, activity.findViewById(R.id.pause_overlay).getVisibility());
                    assertFalse(activity.isFinishing());
                });
            }
        }
    }

    /** Drain the shell pipe so wake completes before attempting keyguard dismissal. */
    private void shellAndWait(String command) throws java.io.IOException {
        try (java.io.InputStream output = new ParcelFileDescriptor.AutoCloseInputStream(
                InstrumentationRegistry.getInstrumentation().getUiAutomation()
                        .executeShellCommand(command))) {
            byte[] buffer = new byte[256];
            while (output.read(buffer) != -1) { /* No shell output/user data is logged. */ }
        }
    }

    @Test public void escapeWithoutKeyboardSourcePausesRatherThanLeavingMatch() {
        Intent intent = MatchActivity.intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                GameMode.RANGE.ordinal(), 0, 0, 1);
        try (ActivityScenario<MatchActivity> scenario = ActivityScenario.launch(intent)) {
            for (int cycle = 0; cycle < 2; cycle++) {
                scenario.onActivity(activity -> {
                    escape(activity, KeyEvent.ACTION_DOWN, 0);
                    assertEquals(View.VISIBLE, activity.findViewById(R.id.pause_overlay).getVisibility());
                    escape(activity, KeyEvent.ACTION_DOWN, 2); // Held key must not unpause.
                    assertEquals(View.VISIBLE, activity.findViewById(R.id.pause_overlay).getVisibility());
                    escape(activity, KeyEvent.ACTION_UP, 0);
                    assertFalse(activity.isFinishing());
                    escape(activity, KeyEvent.ACTION_DOWN, 0);
                    escape(activity, KeyEvent.ACTION_UP, 0);
                    assertEquals(View.GONE, activity.findViewById(R.id.pause_overlay).getVisibility());
                    assertFalse(activity.isFinishing());
                });
            }
        }
    }

    private void escape(MatchActivity activity, int action, int repeat) {
        long now = SystemClock.uptimeMillis();
        assertTrue(activity.dispatchKeyEvent(new KeyEvent(now, now, action,
                KeyEvent.KEYCODE_ESCAPE, repeat, 0, -1, 0, 0, InputDevice.SOURCE_UNKNOWN)));
    }

    @Test public void mouseCanResumeAfterBackgroundingInDesktopMode() { exercise(InputDevice.SOURCE_MOUSE, true, false); }
    @Test public void fingerCanResumeAfterBackgrounding() { exercise(InputDevice.SOURCE_TOUCHSCREEN, false, false); }
    @Test public void battlefieldFingerCanResumeAfterBackgrounding() { exercise(InputDevice.SOURCE_TOUCHSCREEN, false, true); }
    @Test public void fingerCanResumeAfterHomeInDesktopMode() throws Exception {
        Intent intent = MatchActivity.intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                GameMode.RANGE.ordinal(), 0, 0, 1);
        try (ActivityScenario<MatchActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                long now = SystemClock.uptimeMillis();
                activity.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_W, 0, 0, -1, 0, 0, InputDevice.SOURCE_KEYBOARD));
                activity.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP,
                        KeyEvent.KEYCODE_W, 0, 0, -1, 0, 0, InputDevice.SOURCE_KEYBOARD));
            });
            for (int cycle = 0; cycle < 2; cycle++) {
                InstrumentationRegistry.getInstrumentation().getUiAutomation()
                        .executeShellCommand("input keyevent KEYCODE_HOME").close();
                awaitState(scenario, Lifecycle.State.CREATED);
                InstrumentationRegistry.getInstrumentation().getTargetContext()
                        // ActivityScenario adds CLEAR_TASK to its launch intent; don't reuse it.
                        .startActivity(new Intent(intent).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                awaitState(scenario, Lifecycle.State.RESUMED);
                awaitWindowFocus(scenario);
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                final float[] center = new float[2];
                scenario.onActivity(activity -> {
                    assertEquals(View.VISIBLE, activity.findViewById(R.id.pause_overlay).getVisibility());
                    View button = activity.findViewById(R.id.button_resume);
                    int[] location = new int[2]; button.getLocationOnScreen(location);
                    center[0] = location[0] + button.getWidth() / 2f;
                    center[1] = location[1] + button.getHeight() / 2f;
                });
                // Inject through the OS input pipeline, including focus and pointer capture.
                long now = SystemClock.uptimeMillis();
                for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
                    MotionEvent event = MotionEvent.obtain(now, SystemClock.uptimeMillis(),
                            action, center[0], center[1], 0);
                    event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                    // Queue the pair together; an attached mouse can otherwise hover/cancel
                    // the synthetic touch while synchronous injection waits after DOWN.
                    try { assertTrue(InstrumentationRegistry.getInstrumentation().getUiAutomation()
                            .injectInputEvent(event, false)); } finally { event.recycle(); }
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                scenario.onActivity(activity -> assertEquals("OS touch must reach Resume after Home",
                        View.GONE, activity.findViewById(R.id.pause_overlay).getVisibility()));
            }
        }
    }

    private void awaitState(ActivityScenario<MatchActivity> scenario, Lifecycle.State expected) {
        long deadline = SystemClock.uptimeMillis() + 10000;
        while (scenario.getState() != expected && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(50);
        }
        assertEquals(expected, scenario.getState());
    }

    private void awaitWindowFocus(ActivityScenario<MatchActivity> scenario) {
        java.util.concurrent.atomic.AtomicBoolean focused = new java.util.concurrent.atomic.AtomicBoolean();
        long deadline = SystemClock.uptimeMillis() + 10000;
        do {
            scenario.onActivity(activity -> focused.set(activity.hasWindowFocus()));
            if (focused.get()) return;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("Match window did not regain OS input focus after Home");
    }

    private void exercise(int source, boolean desktop, boolean battlefield) {
        Intent intent = MatchActivity.intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                battlefield ? GameMode.BATTLE_FIELD.ordinal() : GameMode.RANGE.ordinal(),
                battlefield ? Maps.firstBattlefieldIndex() : 0, 0, 1);
        try (ActivityScenario<MatchActivity> scenario = ActivityScenario.launch(intent)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            awaitWindowFocus(scenario);
            if (desktop) scenario.onActivity(activity -> {
                // A keyboard event engages the same desktop mode as a real mouse/keyboard.
                long now = SystemClock.uptimeMillis();
                activity.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_W, 0, 0, -1, 0, 0, InputDevice.SOURCE_KEYBOARD));
                activity.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP,
                        KeyEvent.KEYCODE_W, 0, 0, -1, 0, 0, InputDevice.SOURCE_KEYBOARD));
                assertFalse(((HudView) activity.findViewById(R.id.hud)).touchControlsVisible());
            });
            for (int cycle = 0; cycle < 2; cycle++) {
                scenario.moveToState(Lifecycle.State.CREATED);
                scenario.moveToState(Lifecycle.State.RESUMED);
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                awaitWindowFocus(scenario);
                scenario.onActivity(activity -> {
                    assertEquals(View.VISIBLE, activity.findViewById(R.id.pause_overlay).getVisibility());
                    click(activity, R.id.button_resume, source);
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                scenario.onActivity(activity -> assertEquals("Resume click was swallowed after returning",
                        View.GONE, activity.findViewById(R.id.pause_overlay).getVisibility()));
            }
        }
    }

    private void click(MatchActivity activity, int id, int source) {
        View button = activity.findViewById(id);
        assertTrue(button.isShown()); assertTrue(button.isEnabled());
        int[] location = new int[2]; button.getLocationInWindow(location);
        float x = location[0] + button.getWidth() / 2f;
        float y = location[1] + button.getHeight() / 2f;
        long now = SystemClock.uptimeMillis();
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent event;
            if (source == InputDevice.SOURCE_MOUSE) {
                // A SOURCE_MOUSE flag alone leaves the legacy obtain() overload with
                // a finger tool and no pressed mouse button. Reproduce a real primary
                // mouse press instead of relying on touch fallback handling.
                MotionEvent.PointerProperties pointer = new MotionEvent.PointerProperties();
                pointer.id = 0; pointer.toolType = MotionEvent.TOOL_TYPE_MOUSE;
                MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
                coords.x = x; coords.y = y; coords.size = 1f;
                coords.pressure = action == MotionEvent.ACTION_DOWN ? 1f : 0f;
                int buttons = action == MotionEvent.ACTION_DOWN ? MotionEvent.BUTTON_PRIMARY : 0;
                event = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, 1,
                        new MotionEvent.PointerProperties[]{pointer},
                        new MotionEvent.PointerCoords[]{coords}, 0, buttons,
                        1f, 1f, -1, 0, InputDevice.SOURCE_MOUSE, 0);
                assertEquals(MotionEvent.TOOL_TYPE_MOUSE, event.getToolType(0));
                assertEquals(buttons, event.getButtonState());
            } else {
                event = MotionEvent.obtain(now, now + (action == MotionEvent.ACTION_UP ? 30 : 0),
                        action, x, y, 0);
                event.setSource(source);
            }
            try { activity.dispatchTouchEvent(event); } finally { event.recycle(); }
        }
    }
}
