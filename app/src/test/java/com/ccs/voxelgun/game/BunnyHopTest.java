package com.ccs.voxelgun.game;

import com.ccs.voxelgun.input.InputState;
import com.ccs.voxelgun.voxel.Materials;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercise real landing/jump physics rather than just the input mailbox. */
public class BunnyHopTest {
    private Match match(boolean bhop) {
        MatchConfig config = new MatchConfig();
        config.mode = GameMode.RANGE;
        config.mapIndex = 0;
        config.botCount = 0;
        config.seed = 4242L;
        config.modifiers = bhop ? MatchModifier.BUNNY_HOP.bit : 0;
        Match match = new Match(config);
        // A deterministic flat patch avoids measuring spawn descent or a roof above the spawn.
        for (int z = 22; z <= 27; z++) {
            for (int x = 62; x <= 67; x++) {
                for (int y = 0; y <= 20; y++) {
                    match.world().set(x, y, z, y == 0 ? Materials.CONCRETE : Materials.AIR);
                }
            }
        }
        match.world().commit();
        match.player().position.set(64.5f, 1f, 24.5f);
        match.player().velocity.set(0f, 0f, 0f);
        match.player().grounded = true;
        return match;
    }

    private int takeoffs(Match match, InputState input, int steps) {
        int count = 0;
        for (int i = 0; i < steps; i++) {
            boolean grounded = match.player().grounded;
            match.update(Match.STEP, input);
            if (grounded && !match.player().grounded && match.player().velocity.y > 0f) count++;
        }
        return count;
    }

    @Test public void bhopRequiresFreshJumpPressInsteadOfAutomaticHeldJump() {
        Match match = match(true);
        try {
            InputState input = new InputState();
            takeoffs(match, input, 60);
            input.setDeviceJump(true);
            input.queueJump();
            assertEquals("holding Jump cannot complete new timed combos", 1, takeoffs(match, input, 240));
            input.setDeviceJump(false);
            assertEquals("release must prevent any further takeoff", 0, takeoffs(match, input, 120));
            assertTrue(match.player().grounded);
        } finally { match.release(); }
    }

    @Test public void matchWithoutBhopDoesNotRepeatHeldJump() {
        Match match = match(false);
        try {
            InputState input = new InputState();
            takeoffs(match, input, 60);
            input.setTouchJump(true);
            input.queueJump();
            assertEquals(1, takeoffs(match, input, 240));
            assertEquals(0, takeoffs(match, input, 120));
        } finally { match.release(); }
    }

    @Test public void modifierCoexistsWithFogAndIsSupportedInEveryMode() {
        int mask = MatchModifier.BUNNY_HOP.bit | MatchModifier.FOG.bit;
        for (GameMode mode : GameMode.values()) {
            assertEquals(mask, MatchModifier.sanitize(mask, mode));
        }
    }

    @Test public void heldBhopDoesNotCancelCrouch() {
        Match match = match(true);
        try {
            InputState input = new InputState();
            input.crouching = true;
            input.setDeviceJump(true);
            assertEquals(0, takeoffs(match, input, 120));
            assertTrue(input.crouching);
        } finally { match.release(); }
    }

    @Test public void jumpShiftJumpChainsAccelerateWithRealCollisionPhysics() {
        Match match = match(true);
        try {
            InputState input = new InputState();
            takeoffs(match, input, 240); // Finish the 3.2-second match countdown.
            for (int x = 62; x <= 120; x++) {
                for (int z = 22; z <= 27; z++) {
                    for (int y = 0; y <= 20; y++) {
                        match.world().set(x, y, z, y == 0 ? Materials.CONCRETE : Materials.AIR);
                    }
                }
            }
            match.world().commit();
            match.player().yaw = 0f; // Travel along the cleared corridor, not the spawn's facing.
            input.moveX = 1f;
            input.queueJump();
            match.update(Match.STEP, input);
            assertTrue(match.player().velocity.y > 0f);
            float previous = (float) Math.hypot(match.player().velocity.x, match.player().velocity.z);
            for (int hop = 0; hop < 3; hop++) {
                boolean pressed = false;
                int ticks = 0;
                while (!match.player().grounded && ticks++ < 120) {
                    if (!pressed && match.player().velocity.y < 0f && match.player().position.y < 1.7f) {
                        input.setDeviceSprint(true); // Actual default Shift route, not Ctrl.
                        pressed = true;
                    }
                    match.update(Match.STEP, input);
                }
                assertTrue("must reach a real voxel floor", match.player().grounded);
                assertTrue("must press Shift on descent", pressed);
                // The landing is observed by horizontal movement on the following fixed tick.
                input.queueJump();
                match.update(Match.STEP, input);
                assertFalse("Space must launch even while Shift stays held", match.player().grounded);
                assertTrue("hop " + hop + " y=" + match.player().position.y + " vy="
                        + match.player().velocity.y + " x=" + match.player().position.x
                        + " z=" + match.player().position.z + " crouch=" + match.player().crouching,
                        match.player().velocity.y > 0f);
                float speed = (float) Math.hypot(match.player().velocity.x, match.player().velocity.z);
                assertTrue("each timed chain must increase speed: " + previous + " -> " + speed,
                        speed > previous + 0.25f);
                previous = speed;
                input.setDeviceSprint(false);
                match.update(Match.STEP, input);
            }
        } finally { match.release(); }
    }

    @Test public void touchJumpJustBeforeLandingIsBufferedAndLaunchesFromCrouch() {
        Match match = match(true);
        try {
            InputState input = new InputState();
            takeoffs(match, input, 240);
            match.player().grounded = false;
            match.player().position.y = 1.15f;
            match.player().velocity.set(4f, -6f, 0f);
            input.crouching = true; // Touch crouch toggle.
            input.queueJump();
            match.update(Match.STEP, input);
            assertTrue("cannot jump before floor contact without Double Jump", match.player().velocity.y < 0f);
            boolean jumped = false;
            for (int i = 0; i < 6; i++) {
                match.update(Match.STEP, input);
                if (match.player().velocity.y > 0f) { jumped = true; break; }
            }
            assertTrue("latched touch press must survive until timed slide landing", jumped);
            assertFalse(input.crouching);
            assertTrue(match.player().velocity.x > 4.4f);
        } finally { match.release(); }
    }

    @Test public void sprintKeyDoesNotCrouchWithoutBhopModifier() {
        Match match = match(false);
        try {
            InputState input = new InputState();
            takeoffs(match, input, 240);
            input.setDeviceSprint(true);
            input.moveY = 1f;
            input.queueJump();
            match.update(Match.STEP, input);
            assertFalse(match.player().crouching);
            assertTrue(match.player().sprinting);
            assertTrue(match.player().velocity.y > 0f);
        } finally { match.release(); }
    }

    @Test public void keyboardBhopTurnsWithCameraAndMovementKeysWithoutDrifting() {
        checkCameraRelativeBhopSteering(false);
    }

    @Test public void touchBhopTurnsWithCameraAndJoystickWithoutDrifting() {
        checkCameraRelativeBhopSteering(true);
    }

    /** Exercise the same resolved movement/look routes used by both input devices. */
    private void checkCameraRelativeBhopSteering(boolean touch) {
        Match match = match(true);
        try {
            InputState input = new InputState();
            for (int x = 58; x <= 80; x++) {
                for (int z = 8; z <= 34; z++) {
                    for (int y = 0; y <= 20; y++) {
                        match.world().set(x, y, z, y == 0 ? Materials.CONCRETE : Materials.AIR);
                    }
                }
            }
            match.world().commit();
            takeoffs(match, input, 240);
            match.player().yaw = 0f;
            if (touch) input.setTouchMove(0f, 1f);
            else input.setKeyboardMove(0f, 1f);
            if (touch) input.setTouchJump(true);
            else input.setDeviceJump(true);
            input.queueJump();
            match.update(Match.STEP, input);
            assertTrue(match.player().velocity.y > 0f);
            if (touch) input.setTouchJump(false);
            else input.setDeviceJump(false);
            float initialSpeed = horizontalSpeed(match);
            // Fresh crouch in the air, then a fresh Jump immediately after real floor contact.
            input.crouching = true;
            for (int i = 0; i < 120 && !match.player().grounded; i++) {
                match.update(Match.STEP, input);
            }
            assertTrue(match.player().grounded);
            input.queueJump();
            match.update(Match.STEP, input);
            assertTrue(match.player().velocity.y > 0f);
            assertFalse(input.crouching);
            float boosted = horizontalSpeed(match);
            assertTrue(boosted > initialSpeed + .25f);
            float oldX = match.player().position.x;
            float oldZ = match.player().position.z;
            // The production look mailbox, not a direct edit of the player's yaw.
            input.addLook(-(float) Math.PI / (2f * .0031f * input.lookSensitivity), 0f);
            match.update(Match.STEP, input);
            assertEquals(-(float) Math.PI / 2f, match.player().yaw, .001f);
            assertEquals(-boosted, match.player().velocity.x, .001f);
            assertEquals(0f, match.player().velocity.z, .001f);
            assertTrue(match.player().position.x < oldX);
            assertEquals("old forward axis must not keep drifting", oldZ, match.player().position.z, .001f);
            // Changing movement direction is equally immediate, and never another boost.
            if (touch) input.setTouchMove(1f, 0f);
            else input.setKeyboardMove(1f, 0f);
            match.update(Match.STEP, input);
            assertEquals(0f, match.player().velocity.x, .001f);
            assertEquals(-boosted, match.player().velocity.z, .001f);
            assertEquals(boosted, horizontalSpeed(match), .001f);
            if (touch) input.setTouchMove(-1f, 0f);
            else input.setKeyboardMove(-1f, 0f);
            match.update(Match.STEP, input);
            assertEquals(0f, match.player().velocity.x, .001f);
            assertEquals(boosted, match.player().velocity.z, .001f);
        } finally { match.release(); }
    }

    private float horizontalSpeed(Match match) {
        return (float) Math.hypot(match.player().velocity.x, match.player().velocity.z);
    }
}
