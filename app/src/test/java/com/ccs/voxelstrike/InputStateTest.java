package com.ccs.voxelstrike;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ccs.voxelstrike.input.InputState;

import org.junit.Test;

/** The merge between the touch HUD and a physical keyboard or mouse. */
public class InputStateTest {
    @Test
    public void heldJumpDoesNotTurnATapIntoRepeatedRequests() {
        InputState input = new InputState();
        input.setTouchJump(true);
        input.queueJump();
        assertTrue(input.consumeJump());
        assertFalse("repetition belongs to match rules, not personal input settings", input.consumeJump());
        assertTrue(input.jumpHeld);
        input.setTouchJump(false);
        assertFalse("releasing jump stops repetition", input.consumeJump());
    }

    @Test
    public void bothJumpSourcesMergeAndPauseClearsThemWithoutChangingPreference() {
        InputState input = new InputState();
        input.autoJump = true;
        input.setTouchJump(true);
        input.setDeviceJump(true);
        input.setTouchJump(false);
        assertTrue("keyboard still holds jump", input.jumpHeld);
        input.setTouchJump(true);
        input.setDeviceJump(false);
        assertTrue("finger still holds jump", input.jumpHeld);
        input.releaseAll();
        assertFalse(input.jumpHeld);
        assertFalse(input.consumeJump());
        assertTrue(input.autoJump);
        input.setDeviceJump(false);
        assertFalse("no stale touch hold after pause", input.jumpHeld);
    }

    @Test
    public void heldJumpKeepsExplicitPressesSeparateFromCrouching() {
        InputState input = new InputState();
        input.setDeviceJump(true);
        input.crouching = true;
        assertFalse(input.consumeJump());
        input.queueJump();
        assertTrue("a fresh explicit press may still stand the player up", input.consumeJump());
    }

    @Test
    public void keyboardMovementOverridesTheStickWhileHeld() {
        InputState input = new InputState();
        input.setTouchMove(0.5f, 0.5f);
        assertEquals(0.5f, input.moveX, 1e-5f);

        input.setKeyboardMove(-1f, 0f);
        assertEquals(-1f, input.moveX, 1e-5f);
        assertEquals(0f, input.moveY, 1e-5f);

        // Releasing the keys hands control back to the stick rather than stopping the player.
        input.setKeyboardMove(0f, 0f);
        assertEquals(0.5f, input.moveX, 1e-5f);
        assertEquals(0.5f, input.moveY, 1e-5f);
    }

    @Test
    public void eitherSourceCanHoldFireAndAim() {
        InputState input = new InputState();
        input.setTouchFire(true);
        assertTrue(input.fireHeld);

        input.setDeviceFire(true);
        input.setTouchFire(false);
        assertTrue("mouse still holding the trigger", input.fireHeld);

        input.setDeviceFire(false);
        assertFalse(input.fireHeld);

        input.setDeviceAim(true);
        assertTrue(input.aimHeld);
        input.setTouchAim(true);
        input.setDeviceAim(false);
        assertTrue(input.aimHeld);
    }

    @Test
    public void releaseAllClearsEverySource() {
        InputState input = new InputState();
        input.autoJump = true;
        input.setTouchMove(1f, 1f);
        input.setKeyboardMove(1f, 1f);
        input.setDeviceFire(true);
        input.setTouchAim(true);
        input.setDeviceSprint(true);
        input.queueJump();

        input.releaseAll();

        assertEquals(0f, input.moveX, 1e-5f);
        assertEquals(0f, input.moveY, 1e-5f);
        assertFalse(input.fireHeld);
        assertFalse(input.aimHeld);
        assertFalse(input.sprinting);
        assertFalse(input.slideHeld);
        assertFalse(input.consumeJump());
        assertTrue("releaseAll must preserve gameplay preferences", input.autoJump);

        // And the sources really are cleared, not just the resolved fields.
        input.setTouchMove(0.25f, 0f);
        assertEquals(0.25f, input.moveX, 1e-5f);
    }

    @Test
    public void oneShotActionsAreConsumedExactlyOnce() {
        InputState input = new InputState();
        input.queueReload();
        assertTrue(input.consumeReload());
        assertFalse(input.consumeReload());

        input.requestSlot(1);
        assertEquals(1, input.consumeSlotRequest());
        assertEquals(-1, input.consumeSlotRequest());
    }

    @Test public void onlyDeviceSprintSuppliesTheModeScopedSlideAlias() {
        InputState input = new InputState();
        input.setTouchSprint(true);
        assertFalse(input.slideHeld);
        input.setDeviceSprint(true);
        assertTrue(input.slideHeld);
        assertFalse("mode rules, not the mailbox, decide crouching", input.crouching);
        input.setDeviceSprint(false);
        assertFalse(input.slideHeld);
        assertTrue("touch still holds normal sprint", input.sprinting);
    }
}
