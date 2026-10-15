package com.ccs.voxelgun;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.ccs.voxelgun.app.KeyBindings;
import com.ccs.voxelgun.input.DeviceInput;
import com.ccs.voxelgun.input.InputState;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/** Real Android key dispatch and bind persistence, isolated from the player's preferences. */
@RunWith(AndroidJUnit4.class)
public class TacticalKeyBindingsInstrumentedTest {
    private static final String TEST_FILE = "voxelgun_tactical_bindings_test";
    private Context context;

    @Before public void isolatedPreferenceStore() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        target.getSharedPreferences(TEST_FILE, Context.MODE_PRIVATE).edit().clear().commit();
        context = new ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences(TEST_FILE, mode);
            }
        };
    }

    @After public void removeOnlyTestPreferences() {
        InstrumentationRegistry.getInstrumentation().getTargetContext().deleteSharedPreferences(TEST_FILE);
    }

    @Test public void defaultFunctionKeysDoNotControlBotsAndTrainingRestartIsOneShot() {
        KeyBindings bindings = new KeyBindings(context);
        InputState input = new InputState();
        DeviceInput device = new DeviceInput(input, bindings);
        int[] keys = {KeyEvent.KEYCODE_F6, KeyEvent.KEYCODE_F7, KeyEvent.KEYCODE_F8};
        for (int code : keys) {
            assertNull(bindings.actionFor(code));
            assertFalse(device.onKey(key(code, KeyEvent.ACTION_DOWN, 0)));
            assertFalse(input.consumeCourseRestart());
            assertFalse(device.onKey(key(code, KeyEvent.ACTION_DOWN, 2)));
            assertFalse(device.onKey(key(code, KeyEvent.ACTION_UP, 0)));
        }
        for (KeyBindings.Action action : KeyBindings.Action.values())
            assertFalse("Bot orders must not appear in the rebindable-action list", action.name().startsWith("SQUAD_"));
        assertTrue(device.onKey(key(KeyEvent.KEYCODE_T, KeyEvent.ACTION_DOWN, 0)));
        assertTrue(input.consumeCourseRestart());
        assertFalse(input.consumeCourseRestart());
        assertTrue(device.onKey(key(KeyEvent.KEYCODE_T, KeyEvent.ACTION_DOWN, 1)));
        assertFalse(input.consumeCourseRestart());
        assertTrue(device.onKey(key(KeyEvent.KEYCODE_T, KeyEvent.ACTION_UP, 0)));
        assertFalse(input.consumeCourseRestart());
        assertEquals("T: Restart course", bindings.courseRestartHint());
    }

    @Test public void rebindingConflictsMoveTheRealActionAndItsHintWithoutFallback() {
        KeyBindings bindings = new KeyBindings(context);
        bindings.bind(KeyBindings.Action.FIRE, KeyEvent.KEYCODE_F6);
        bindings.bind(KeyBindings.Action.COURSE_RESTART, KeyEvent.KEYCODE_B);
        assertEquals(0, bindings.keyFor(KeyBindings.Action.BUY));
        assertEquals(KeyBindings.Action.FIRE, bindings.actionFor(KeyEvent.KEYCODE_F6));
        assertEquals(KeyBindings.Action.COURSE_RESTART, bindings.actionFor(KeyEvent.KEYCODE_B));
        assertNull(bindings.actionFor(KeyEvent.KEYCODE_T));

        InputState input = new InputState();
        DeviceInput device = new DeviceInput(input, bindings);
        assertTrue(device.onKey(key(KeyEvent.KEYCODE_F6, KeyEvent.ACTION_DOWN, 0)));
        assertTrue(input.fireHeld); assertFalse(input.consumeCourseRestart());
        assertTrue(device.onKey(key(KeyEvent.KEYCODE_F6, KeyEvent.ACTION_UP, 0)));
        assertFalse(input.fireHeld);
        assertTrue(device.onKey(key(KeyEvent.KEYCODE_B, KeyEvent.ACTION_DOWN, 0)));
        assertTrue(input.consumeCourseRestart()); assertFalse(input.fireHeld);
        assertFalse(input.consumeCourseRestart());
        assertFalse("old restart key must not remain an alias", device.onKey(key(KeyEvent.KEYCODE_T,
                KeyEvent.ACTION_DOWN, 0)));
        assertEquals("B: Restart course", bindings.courseRestartHint());

        KeyBindings restored = new KeyBindings(context);
        for (KeyBindings.Action action : KeyBindings.Action.values())
            assertEquals(bindings.keyFor(action), restored.keyFor(action));
    }

    @Test public void legacyBotCommandPreferencesAreIgnoredAndPauseDropsPendingRestart() {
        // Existing player profiles can contain obsolete command entries. Names, not enum
        // positions, persist the unrelated actions, and stale orders are not dispatched.
        context.getSharedPreferences(TEST_FILE, Context.MODE_PRIVATE).edit()
                .putInt("SQUAD_ATTACK", KeyEvent.KEYCODE_F6)
                .putInt("SQUAD_DEFEND", KeyEvent.KEYCODE_F7)
                .putInt("SQUAD_FOLLOW", KeyEvent.KEYCODE_F8)
                .putInt("JUMP", KeyEvent.KEYCODE_J).commit();
        KeyBindings bindings = new KeyBindings(context);
        assertEquals(KeyEvent.KEYCODE_J, bindings.keyFor(KeyBindings.Action.JUMP));
        InputState input = new InputState();
        DeviceInput device = new DeviceInput(input, bindings);
        assertFalse(device.onKey(key(KeyEvent.KEYCODE_F6, KeyEvent.ACTION_DOWN, 0)));
        assertFalse(device.onKey(key(KeyEvent.KEYCODE_F7, KeyEvent.ACTION_DOWN, 0)));
        assertFalse(device.onKey(key(KeyEvent.KEYCODE_F8, KeyEvent.ACTION_DOWN, 0)));
        assertTrue(device.onKey(key(KeyEvent.KEYCODE_T, KeyEvent.ACTION_DOWN, 0)));
        input.releaseAll(); device.releaseAll();
        assertFalse(input.consumeCourseRestart());
    }

    private static KeyEvent key(int code, int action, int repeat) {
        long now = SystemClock.uptimeMillis();
        return new KeyEvent(now, now, action, code, repeat, 0, -1, 0, 0, InputDevice.SOURCE_KEYBOARD);
    }
}
