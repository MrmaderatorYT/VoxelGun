package com.ccs.voxelstrike;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.ccs.voxelstrike.app.KeyBindings;
import com.ccs.voxelstrike.game.*;
import com.ccs.voxelstrike.input.DeviceInput;
import com.ccs.voxelstrike.input.InputState;
import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.voxel.NativeBridge;
import com.ccs.voxelstrike.ui.HudView;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real Android KeyEvents → DeviceInput → match simulation with native-backed voxel volumes. */
@RunWith(AndroidJUnit4.class)
public class BhopInstrumentedTest {
    @Test public void physicalJumpSprintBindingJumpBuildsSpeedAcrossThreeLandings() {
        runChain(false, false);
    }

    @Test public void remappedJumpAndSprintBuildSpeedWithoutSpaceOrShift() {
        runChain(true, false);
    }

    @Test public void remappedJumpAndCrouchBuildSpeedWithoutSpaceOrShift() {
        runChain(true, true);
    }

    @Test public void lateJumpAfterProlongedSlideDoesNotRewardWithNativePhysics() {
        runChain(true, true, true);
    }

    @Test public void remappedBhopTurnsAndReversesWithoutDriftAcrossThreeLandings() {
        runChain(true, true, false, true);
    }

    private void runChain(boolean remapped, boolean useCrouch) {
        runChain(remapped, useCrouch, false);
    }

    private void runChain(boolean remapped, boolean useCrouch, boolean missWindow) {
        runChain(remapped, useCrouch, missWindow, false);
    }

    private void runChain(boolean remapped, boolean useCrouch, boolean missWindow, boolean turn) {
        assertTrue("exercise the Android build with native voxel volumes", NativeBridge.available());
        InputState input = new InputState();
        KeyBindings bindings = new KeyBindings(InstrumentationRegistry.getInstrumentation().getTargetContext());
        if (remapped) remapLocally(bindings);
        KeyBindings.Action slideAction = useCrouch ? KeyBindings.Action.CROUCH : KeyBindings.Action.SPRINT;
        DeviceInput device = new DeviceInput(input, bindings);
        if (remapped) {
            assertFalse(device.onKey(event(KeyEvent.KEYCODE_SHIFT_LEFT, true)));
            assertFalse(device.onKey(event(KeyEvent.KEYCODE_SPACE, true)));
            assertFalse(input.slideHeld); assertFalse(input.jumpHeld);
            assertEquals("J → release J → K / L in air → land → J quickly", bindings.bhopHint());
        }
        MatchConfig config = new MatchConfig();
        config.mode = GameMode.RANGE;
        config.botCount = 0;
        config.modifiers = MatchModifier.BUNNY_HOP.bit;
        Match match = new Match(config);
        try {
            for (int x = 50; x <= 120; x++) {
                for (int z = 20; z <= 30; z++) {
                    for (int y = 0; y <= 20; y++) {
                        match.world().set(x, y, z, y == 0 ? Materials.CONCRETE : Materials.AIR);
                    }
                }
            }
            match.world().commit();
            match.player().position.set(60.5f, 1f, 24.5f);
            match.player().velocity.set(0f, 0f, 0f);
            match.player().yaw = 0f;
            match.player().grounded = true;
            for (int i = 0; i < 240; i++) match.update(Match.STEP, input);
            key(device, bindings, KeyBindings.Action.RIGHT, true);
            key(device, bindings, KeyBindings.Action.JUMP, true);
            match.update(Match.STEP, input);
            key(device, bindings, KeyBindings.Action.JUMP, false);
            assertTrue(match.player().velocity.y > 0f);
            float previous = horizontalSpeed(match);
            for (int hop = 0; hop < (missWindow ? 1 : 3); hop++) {
                boolean slidePressed = false;
                int ticks = 0;
                while (!match.player().grounded && ticks++ < 120) {
                    if (!slidePressed && ticks >= 3) {
                        key(device, bindings, slideAction, true);
                        assertTrue(useCrouch ? input.crouching : input.slideHeld);
                        slidePressed = true;
                    }
                    match.update(Match.STEP, input);
                }
                assertTrue(match.player().grounded);
                assertTrue(slidePressed);
                for (int i = 0; i < 3; i++) match.update(Match.STEP, input);
                assertTrue("landing and holding crouch alone cannot grant speed",
                        horizontalSpeed(match) <= previous + .02f);
                if (missWindow) for (int i = 0; i < 20; i++) match.update(Match.STEP, input);
                float beforeJump = horizontalSpeed(match);
                key(device, bindings, KeyBindings.Action.JUMP, true);
                match.update(Match.STEP, input);
                assertTrue("bound Jump while slide action held must launch: hop=" + hop
                        + " x=" + match.player().position.x + " y=" + match.player().position.y
                        + " vy=" + match.player().velocity.y + " crouch=" + match.player().crouching
                        + " blend=" + match.player().crouchBlend + " headroomCollision="
                        + match.world().boxCollides(match.player().position.x, match.player().position.y,
                        match.player().position.z, Combatant.RADIUS, Combatant.HEIGHT),
                        match.player().velocity.y > 0f);
                float speed = horizontalSpeed(match);
                if (missWindow) {
                    assertTrue("late jump must not award speed", speed <= beforeJump + .02f);
                    assertTrue("extended slide must lose momentum", speed < previous);
                } else {
                    assertTrue("timed hop " + hop + ": " + previous + " -> " + speed,
                            speed > previous + .25f);
                }
                previous = speed;
                key(device, bindings, KeyBindings.Action.JUMP, false);
                key(device, bindings, slideAction, false);
                match.update(Match.STEP, input);
                if (turn) {
                    float boosted = horizontalSpeed(match);
                    float oldX = match.player().position.x;
                    input.addLook(-(float) Math.PI / (2f * .0031f * input.lookSensitivity), 0f);
                    match.update(Match.STEP, input);
                    assertEquals(0f, match.player().velocity.x, .001f);
                    assertEquals(-boosted, match.player().velocity.z, .001f);
                    assertEquals("no drift along the old axis after turning", oldX,
                            match.player().position.x, .001f);
                    key(device, bindings, KeyBindings.Action.RIGHT, false);
                    key(device, bindings, KeyBindings.Action.LEFT, true);
                    match.update(Match.STEP, input);
                    assertEquals(0f, match.player().velocity.x, .001f);
                    assertEquals(boosted, match.player().velocity.z, .001f);
                    assertEquals("turns and reversals cannot grant speed", boosted, horizontalSpeed(match), .001f);
                    key(device, bindings, KeyBindings.Action.LEFT, false);
                    key(device, bindings, KeyBindings.Action.RIGHT, true);
                    input.addLook((float) Math.PI / (2f * .0031f * input.lookSensitivity), 0f);
                    match.update(Match.STEP, input);
                    assertEquals(boosted, match.player().velocity.x, .001f);
                    assertEquals(0f, match.player().velocity.z, .001f);
                }
            }
            device.releaseAll();
            assertFalse(input.slideHeld);
            assertFalse(input.jumpHeld);
        } finally { match.release(); }
    }

    private float horizontalSpeed(Match match) {
        return (float) Math.hypot(match.player().velocity.x, match.player().velocity.z);
    }

    private void key(DeviceInput device, KeyBindings bindings, KeyBindings.Action action, boolean down) {
        assertTrue(device.onKey(event(bindings.keyFor(action), down)));
    }

    private KeyEvent event(int code, boolean down) {
        long now = SystemClock.uptimeMillis();
        return new KeyEvent(now, now, down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP,
                code, 0, 0, -1, 0, 0, InputDevice.SOURCE_KEYBOARD);
    }

    /** Change this isolated object only. Never save/reset the player's persisted keyboard layout. */
    private void remapLocally(KeyBindings bindings) {
        try {
            var field = KeyBindings.class.getDeclaredField("keys"); field.setAccessible(true);
            int[] keys = (int[]) field.get(bindings);
            for (KeyBindings.Action action : KeyBindings.Action.values()) keys[action.ordinal()] = action.defaultKey();
            keys[KeyBindings.Action.JUMP.ordinal()] = KeyEvent.KEYCODE_J;
            keys[KeyBindings.Action.SPRINT.ordinal()] = KeyEvent.KEYCODE_K;
            keys[KeyBindings.Action.CROUCH.ordinal()] = KeyEvent.KEYCODE_L;
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    @Test public void hintsFollowBindingsAndSwitchToActionNamesForTouch() {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        KeyBindings bindings = new KeyBindings(instrumentation.getTargetContext());
        remapLocally(bindings);
        instrumentation.runOnMainSync(() -> {
            HudView hud = new HudView(instrumentation.getTargetContext());
            hud.setBhopKeyboardHint(bindings.bhopHint());
            assertFalse(hud.bhopControlHint().contains("Shift"));
            hud.setTouchControlsVisible(false);
            assertEquals("J → release J → K / L in air → land → J quickly", hud.bhopControlHint());
            hud.setTouchControlsVisible(true);
            assertEquals("jump → release → crouch in air → land → jump quickly", hud.bhopControlHint());
        });
        try {
            var field = KeyBindings.class.getDeclaredField("keys"); field.setAccessible(true);
            int[] keys = (int[]) field.get(bindings);
            keys[KeyBindings.Action.SPRINT.ordinal()] = 0;
            assertEquals("J → release J → L in air → land → J quickly", bindings.bhopHint());
            keys[KeyBindings.Action.CROUCH.ordinal()] = 0;
            assertEquals("J → release J → Crouch/Slide (unbound) in air → land → J quickly", bindings.bhopHint());
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
}
