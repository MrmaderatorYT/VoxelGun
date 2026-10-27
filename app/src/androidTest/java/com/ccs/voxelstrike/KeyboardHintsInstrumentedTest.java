package com.ccs.voxelstrike;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.ccs.voxelstrike.app.KeyBindings;
import com.ccs.voxelstrike.input.DeviceInput;
import com.ccs.voxelstrike.input.InputState;
import com.ccs.voxelstrike.ui.HudView;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Binding-aware hints are checked against real Android key names and key dispatch.
 * Every binding edit is confined to an isolated object: never save/reset the player's layout.
 */
@RunWith(AndroidJUnit4.class)
public class KeyboardHintsInstrumentedTest {
    @Test public void defaultHintsAdvertiseTheShopOnlyWhenAvailable() {
        KeyBindings bindings = isolatedDefaults();

        String combat = bindings.keyboardCombatHint();
        assertTrue(combat.contains("Space: Jump"));
        assertTrue(combat.contains("R: Reload"));
        assertTrue(combat.contains("G: Grenade"));
        assertTrue(combat.contains("V: Melee"));
        assertFalse(bindings.keyboardUtilityHint(false).contains("Shop"));
        assertTrue(bindings.keyboardUtilityHint(true).contains("B: Shop"));
        assertTrue(bindings.keyboardUtilityHint(false).contains("Tab: Scores"));
        assertTrue(bindings.keyboardUtilityHint(false).contains("Esc: Pause"));
    }

    @Test public void shopHintMatchesDispatchWhenBIsReboundToFire() {
        KeyBindings bindings = isolatedDefaults();
        keys(bindings)[KeyBindings.Action.BUY.ordinal()] = KeyEvent.KEYCODE_K;
        keys(bindings)[KeyBindings.Action.FIRE.ordinal()] = KeyEvent.KEYCODE_B;

        String hint = bindings.keyboardUtilityHint(true);
        assertTrue(hint.contains("K: Shop"));
        assertFalse(hint.contains("B: Shop"));
        assertEquals(KeyBindings.Action.FIRE, bindings.actionFor(KeyEvent.KEYCODE_B));
        assertEquals(KeyBindings.Action.BUY, bindings.actionFor(KeyEvent.KEYCODE_K));

        InputState input = new InputState();
        DeviceInput device = new DeviceInput(input, bindings);
        AtomicInteger shopOpens = new AtomicInteger();
        device.setBuyListener(shopOpens::incrementAndGet);

        assertTrue(device.onKey(key(KeyEvent.KEYCODE_B, KeyEvent.ACTION_DOWN)));
        assertTrue(input.fireHeld);
        assertEquals(0, shopOpens.get());
        assertTrue(device.onKey(key(KeyEvent.KEYCODE_B, KeyEvent.ACTION_UP)));
        assertFalse(input.fireHeld);
        assertTrue(device.onKey(key(KeyEvent.KEYCODE_K, KeyEvent.ACTION_DOWN)));
        assertEquals(1, shopOpens.get());
        device.releaseAll();
    }

    @Test public void unboundShopDoesNotFallbackToTheDefaultBKey() {
        KeyBindings bindings = isolatedDefaults();
        keys(bindings)[KeyBindings.Action.BUY.ordinal()] = 0;
        keys(bindings)[KeyBindings.Action.FIRE.ordinal()] = KeyEvent.KEYCODE_B;

        String hint = bindings.keyboardUtilityHint(true);
        assertTrue(hint.contains("Shop"));
        assertTrue(hint.contains("unbound"));
        assertFalse(hint.contains("B: Shop"));
        assertFalse(bindings.keyboardUtilityHint(false).contains("Shop"));
    }

    @Test public void combatHintsFollowRemappedAndUnboundActions() {
        KeyBindings bindings = isolatedDefaults();
        keys(bindings)[KeyBindings.Action.JUMP.ordinal()] = KeyEvent.KEYCODE_J;
        keys(bindings)[KeyBindings.Action.RELOAD.ordinal()] = KeyEvent.KEYCODE_P;
        keys(bindings)[KeyBindings.Action.GRENADE.ordinal()] = KeyEvent.KEYCODE_R;
        keys(bindings)[KeyBindings.Action.MELEE.ordinal()] = 0;

        String hint = bindings.keyboardCombatHint();
        assertTrue(hint.contains("J: Jump"));
        assertTrue(hint.contains("P: Reload"));
        assertTrue(hint.contains("R: Grenade"));
        assertFalse(hint.contains("Space: Jump"));
        assertFalse(hint.contains("R: Reload"));
        assertFalse(hint.contains("V: Melee"));
    }

    @Test public void pauseHintsKeepTheAlwaysAvailableEscapeFallback() {
        KeyBindings bindings = isolatedDefaults();
        keys(bindings)[KeyBindings.Action.PAUSE.ordinal()] = KeyEvent.KEYCODE_P;
        assertTrue(bindings.keyboardUtilityHint(false).contains("Esc / P: Pause"));

        keys(bindings)[KeyBindings.Action.PAUSE.ordinal()] = 0;
        assertTrue(bindings.keyboardUtilityHint(false).contains("Esc: Pause"));
        assertFalse(bindings.keyboardUtilityHint(false).contains("P: Pause"));

        keys(bindings)[KeyBindings.Action.SCOREBOARD.ordinal()] = KeyEvent.KEYCODE_O;
        assertTrue(bindings.keyboardUtilityHint(false).contains("O: Scores"));
        assertFalse(bindings.keyboardUtilityHint(false).contains("Tab: Scores"));
    }

    @Test public void attachedKeyboardHintsDoNotDisableTouchControls() {
        KeyBindings bindings = isolatedDefaults();
        remapBhopLocally(bindings);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            HudView hud = hud(bindings);
            assertTrue(hud.touchControlsVisible());
            assertFalse(hud.keyboardAvailable());

            hud.setKeyboardAvailable(true);
            assertTrue(hud.keyboardAvailable());
            assertTrue(hud.touchControlsVisible());
            assertEquals(bindings.bhopHint(), hud.bhopControlHint());

            hud.setKeyboardAvailable(false);
            assertFalse(hud.keyboardAvailable());
            assertTrue(hud.touchControlsVisible());
            assertEquals("jump → release → crouch in air → land → jump quickly",
                    hud.bhopControlHint());
        });
    }

    @Test public void hudShopHintSwitchesWithActualBuyAvailability() {
        KeyBindings bindings = isolatedDefaults();
        keys(bindings)[KeyBindings.Action.BUY.ordinal()] = KeyEvent.KEYCODE_K;
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            HudView hud = hud(bindings);
            hud.setKeyboardAvailable(true);

            hud.setBuyAvailable(false);
            assertEquals(bindings.keyboardUtilityHint(false), hud.keyboardUtilityHint());
            assertFalse(hud.keyboardUtilityHint().contains("Shop"));

            hud.setBuyAvailable(true);
            assertEquals(bindings.keyboardUtilityHint(true), hud.keyboardUtilityHint());
            assertTrue(hud.keyboardUtilityHint().contains("K: Shop"));

            hud.setBuyAvailable(false);
            assertFalse(hud.keyboardUtilityHint().contains("Shop"));
        });
    }

    @Test public void keyboardDetachDoesNotResetMouseDesktopMode() {
        KeyBindings bindings = isolatedDefaults();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            HudView hud = hud(bindings);
            hud.setTouchControlsVisible(false);
            hud.setKeyboardAvailable(true);

            hud.setKeyboardAvailable(false);
            assertFalse(hud.keyboardAvailable());
            assertFalse(hud.touchControlsVisible());
            // Desktop bhop guidance continues to use keys, as before this feature.
            assertEquals(bindings.bhopHint(), hud.bhopControlHint());
        });
    }

    @Test public void desktopBhopHintRemainsBindingAwareWithoutPhysicalKeyboardDetection() {
        KeyBindings bindings = isolatedDefaults();
        remapBhopLocally(bindings);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            HudView hud = hud(bindings);
            assertFalse(hud.keyboardAvailable());
            hud.setTouchControlsVisible(false);
            assertEquals("J → release J → K / L in air → land → J quickly",
                    hud.bhopControlHint());
            hud.setTouchControlsVisible(true);
            assertEquals("jump → release → crouch in air → land → jump quickly",
                    hud.bhopControlHint());
        });
    }

    private static HudView hud(KeyBindings bindings) {
        HudView hud = new HudView(InstrumentationRegistry.getInstrumentation().getTargetContext());
        hud.bind(new InputState(), () -> {});
        hud.setBhopKeyboardHint(bindings.bhopHint());
        hud.setKeyboardHints(bindings.keyboardCombatHint(),
                bindings.keyboardUtilityHint(false), bindings.keyboardUtilityHint(true));
        return hud;
    }

    private static KeyBindings isolatedDefaults() {
        KeyBindings bindings = new KeyBindings(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        int[] localKeys = keys(bindings);
        for (KeyBindings.Action action : KeyBindings.Action.values()) {
            localKeys[action.ordinal()] = action.defaultKey();
        }
        return bindings;
    }

    private static void remapBhopLocally(KeyBindings bindings) {
        int[] localKeys = keys(bindings);
        localKeys[KeyBindings.Action.JUMP.ordinal()] = KeyEvent.KEYCODE_J;
        localKeys[KeyBindings.Action.SPRINT.ordinal()] = KeyEvent.KEYCODE_K;
        localKeys[KeyBindings.Action.CROUCH.ordinal()] = KeyEvent.KEYCODE_L;
    }

    private static int[] keys(KeyBindings bindings) {
        try {
            Field field = KeyBindings.class.getDeclaredField("keys");
            field.setAccessible(true);
            return (int[]) field.get(bindings);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static KeyEvent key(int code, int action) {
        long now = SystemClock.uptimeMillis();
        return new KeyEvent(now, now, action, code, 0, 0, -1, 0, 0,
                InputDevice.SOURCE_KEYBOARD);
    }
}
