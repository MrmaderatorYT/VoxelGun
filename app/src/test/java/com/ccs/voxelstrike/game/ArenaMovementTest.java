package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.input.InputState;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArenaMovementTest {
    private static final float SPRINT = 7.7f, DT = 1f / 60f;
    private float speed(Combatant p) {
        return (float) Math.hypot(p.velocity.x, p.velocity.z);
    }

    @Test public void slideNeedsSprintAndCrouchEdgeAndHasFiniteDuration() {
        ArenaMovement movement = new ArenaMovement();
        Combatant p = new Combatant(0, "test");
        InputState input = new InputState();
        p.grounded = true;
        p.velocity.x = SPRINT;
        input.sprinting = input.crouching = true;
        movement.update(p, input, 2.7f, 0f, SPRINT, false, DT);
        assertTrue(movement.sliding());
        assertTrue(speed(p) > SPRINT);
        for (int i = 0; i < 60; i++) movement.update(p, input, 2.7f, 0f, SPRINT, false, DT);
        assertFalse("holding crouch cannot restart a slide", movement.sliding());
        assertEquals(2.7f, speed(p), 0.001f);
        movement.reset();
        input.sprinting = false;
        p.velocity.x = SPRINT;
        movement.update(p, input, 2.7f, 0f, SPRINT, false, DT);
        assertFalse(movement.sliding());
    }

    @Test public void releaseOrAirborneCancelsSlide() {
        ArenaMovement movement = new ArenaMovement();
        Combatant p = new Combatant(0, "test");
        InputState input = new InputState();
        p.grounded = true;
        p.velocity.x = SPRINT;
        input.sprinting = input.crouching = true;
        movement.update(p, input, 2.7f, 0f, SPRINT, false, DT);
        input.crouching = false;
        movement.update(p, input, SPRINT, 0f, SPRINT, false, DT);
        assertFalse(movement.sliding());
        movement.reset();
        input.crouching = true;
        p.grounded = false;
        movement.update(p, input, 2.7f, 0f, SPRINT, false, DT);
        assertFalse(movement.sliding());
    }

    @Test public void withoutBhopSteeringAndReleaseAreImmediateOnGroundAndInAir() {
        for (boolean grounded : new boolean[]{true, false}) {
            ArenaMovement movement = new ArenaMovement();
            Combatant p = new Combatant(0, "test");
            InputState input = new InputState();
            p.grounded = grounded;
            float vertical = grounded ? 0f : -3f;
            p.velocity.set(SPRINT, vertical, -2f);

            movement.update(p, input, 0f, SPRINT, SPRINT, false, DT);
            assertEquals("old heading must not carry into the new one", 0f, p.velocity.x, .001f);
            assertEquals(SPRINT, p.velocity.z, .001f);
            assertEquals("horizontal steering must leave gravity to Physics",
                    vertical, p.velocity.y, .001f);

            movement.update(p, input, -2.7f, 0f, SPRINT, false, DT);
            assertEquals("reverse/analog input must set the requested speed", -2.7f,
                    p.velocity.x, .001f);
            assertEquals(0f, p.velocity.z, .001f);
            assertEquals(vertical, p.velocity.y, .001f);

            movement.update(p, input, 0f, 0f, SPRINT, false, DT);
            assertEquals("release must stop horizontal motion in the same tick",
                    0f, speed(p), .001f);
            assertEquals(vertical, p.velocity.y, .001f);
        }
    }

    @Test public void timedSlideLandingsStackAndScaleWithDoubleSpeed() {
        for (float reference : new float[]{SPRINT, SPRINT * 2f}) {
            ArenaMovement movement = new ArenaMovement();
            Combatant p = new Combatant(0, "test");
            InputState input = new InputState();
            p.velocity.x = reference;
            for (int i = 0; i < 20; i++) {
                input.setDeviceSprint(false);
                p.grounded = false;
                p.velocity.y = -3f;
                movement.update(p, input, reference, 0f, reference, true, DT);
                input.setDeviceSprint(true);
                movement.update(p, input, reference, 0f, reference, true, DT);
                float before = speed(p);
                p.grounded = true;
                p.velocity.y = 0f;
                movement.update(p, input, reference, 0f, reference, true, DT);
                assertTrue(movement.sliding());
                assertTrue(movement.hopReady());
                assertTrue("landing alone cannot award speed", speed(p) <= before);
                movement.onJump(p, true);
                assertTrue(speed(p) >= before - 0.02f);
                assertTrue(speed(p) <= reference * ArenaMovement.BHOP_MAX_SPEED_FACTOR + 0.001f);
            }
            assertEquals(reference * ArenaMovement.BHOP_MAX_SPEED_FACTOR, speed(p), 0.04f);
            input.setDeviceSprint(false);
            for (int i = 0; i < 90; i++) movement.update(p, input, reference, 0f, reference, true, DT);
            assertEquals(reference, speed(p), 0.001f);
        }
    }

    @Test public void holdingJumpOnGroundNeverGrantsFreeSpeed() {
        ArenaMovement movement = new ArenaMovement();
        Combatant p = new Combatant(0, "test");
        InputState input = new InputState();
        p.grounded = true;
        input.setDeviceJump(true);
        for (int i = 0; i < 120; i++) movement.update(p, input, SPRINT, 0f, SPRINT, true, DT);
        assertEquals(SPRINT, speed(p), 0.001f);
    }

    @Test public void crouchHeldBeforeTakeoffCannotArmBhop() {
        for (boolean ascending : new boolean[]{false, true}) {
            ArenaMovement movement = new ArenaMovement();
            Combatant p = new Combatant(0, "test");
            InputState input = new InputState();
            p.velocity.x = SPRINT;
            p.grounded = true;
            input.setDeviceSprint(true);
            movement.update(p, input, SPRINT, 0f, SPRINT, true, DT);
            p.grounded = false;
            p.velocity.y = ascending ? 3f : -3f;
            for (int i = 0; i < 20; i++) movement.update(p, input, 2.7f, 0f, SPRINT, true, DT);
            p.grounded = true;
            movement.update(p, input, 2.7f, 0f, SPRINT, true, DT);
            assertFalse(movement.hopReady());
            assertTrue(speed(p) < SPRINT);
            for (int i = 0; i < 60; i++) movement.update(p, input, 2.7f, 0f, SPRINT, true, DT);
            assertEquals(2.7f, speed(p), 0.001f);
        }
    }

    @Test public void airborneCrouchOnAscentArmsButOnlyTimedJumpRewards() {
        ArenaMovement movement = new ArenaMovement();
        Combatant p = new Combatant(0, "test"); InputState input = new InputState();
        p.velocity.set(SPRINT, 3f, 0f); input.crouching = true;
        movement.update(p, input, 2.7f, 0f, SPRINT, true, DT);
        for (int i = 0; i < 25; i++) movement.update(p, input, 2.7f, 0f, SPRINT, true, DT);
        p.grounded = true; p.velocity.y = 0f;
        movement.update(p, input, 2.7f, 0f, SPRINT, true, DT);
        assertTrue(movement.hopReady()); assertTrue(speed(p) <= SPRINT);
        movement.onJump(p, true); assertTrue(speed(p) > SPRINT + .5f);
        float boosted = speed(p); movement.onJump(p, true);
        assertEquals("reward must be consumed exactly once", boosted, speed(p), .001f);
    }

    @Test public void missedWindowAndProlongedCrouchDecayInsteadOfRewarding() {
        ArenaMovement movement = new ArenaMovement();
        Combatant p = new Combatant(0, "test"); InputState input = new InputState();
        p.velocity.set(SPRINT * 1.8f, -3f, 0f); input.crouching = true;
        movement.update(p, input, 2.7f, 0f, SPRINT, true, DT);
        p.grounded = true; p.velocity.y = 0f;
        movement.update(p, input, 2.7f, 0f, SPRINT, true, DT);
        for (int i = 0; i < 15; i++) movement.update(p, input, 2.7f, 0f, SPRINT, true, DT);
        assertFalse(movement.hopReady()); float before = speed(p);
        assertTrue("missed combo retains some momentum while losing it", before > 2.7f);
        movement.onJump(p, true);
        assertEquals("late jump gives no boost", before, speed(p), .001f);
        for (int i = 0; i < 90; i++) {
            before = speed(p); movement.update(p, input, 2.7f, 0f, SPRINT, true, DT);
            assertTrue(speed(p) <= before + .001f);
            assertTrue("no sudden velocity snap", before - speed(p) < .4f);
        }
        assertEquals(2.7f, speed(p), .001f);
    }

    @Test public void jumpMustBeReleasedBeforeAirborneCrouch() {
        ArenaMovement movement = new ArenaMovement();
        Combatant p = new Combatant(0, "test"); InputState input = new InputState();
        p.velocity.set(SPRINT, -3f, 0f);
        input.setDeviceJump(true); input.crouching = true;
        movement.update(p, input, 2.7f, 0f, SPRINT, true, DT);
        p.grounded = true; p.velocity.y = 0f;
        movement.update(p, input, 2.7f, 0f, SPRINT, true, DT);
        assertFalse(movement.hopReady());
    }

    @Test public void releaseAfterLandingKeepsMomentumForJumpButNotIndefinitely() {
        ArenaMovement movement = new ArenaMovement();
        Combatant p = new Combatant(0, "test");
        InputState input = new InputState();
        p.velocity.x = SPRINT;
        p.velocity.y = -3f;
        input.crouching = true;
        for (int i = 0; i < 60; i++) movement.update(p, input, 2.7f, 0f, SPRINT, true, DT);
        p.grounded = true;
        movement.update(p, input, 2.7f, 0f, SPRINT, true, DT);
        float boosted = speed(p);
        input.crouching = false;
        movement.update(p, input, SPRINT, 0f, SPRINT, true, DT);
        assertEquals(boosted, speed(p), 0.001f);
        assertTrue(movement.hopReady());
        for (int i = 0; i < 20; i++) movement.update(p, input, SPRINT, 0f, SPRINT, true, DT);
        assertEquals(SPRINT, speed(p), 0.001f);
        movement.bufferJump();
        movement.reset();
        assertFalse(movement.bufferedJump());
        assertFalse(movement.hopReady());
    }

    @Test public void noModifierOrCrouchingDoesNotGainBhopSpeed() {
        ArenaMovement movement = new ArenaMovement();
        Combatant p = new Combatant(0, "test");
        InputState input = new InputState();
        p.grounded = true;
        input.setTouchJump(true);
        movement.update(p, input, SPRINT, 0f, SPRINT, false, DT);
        assertEquals(SPRINT, speed(p), 0.001f);
        input.crouching = true;
        for (int i = 0; i < 60; i++) movement.update(p, input, 2.7f, 0f, SPRINT, true, DT);
        assertEquals(2.7f, speed(p), 0.001f);
    }

    private void launchTimedHop(ArenaMovement movement, Combatant p, InputState input) {
        p.velocity.set(SPRINT, -3f, 0f);
        input.crouching = true;
        movement.update(p, input, SPRINT, 0f, SPRINT, true, DT);
        p.grounded = true;
        movement.update(p, input, SPRINT, 0f, SPRINT, true, DT);
        assertTrue(movement.hopReady());
        assertTrue(JumpRules.jump(p, 8f, false, true));
        movement.onJump(p, true);
        input.crouching = false;
    }

    @Test public void armedAirborneCrouchTurnsVelocityInsteadOfAddingSidewaysInertia() {
        ArenaMovement movement = new ArenaMovement();
        Combatant p = new Combatant(0, "test"); InputState input = new InputState();
        p.velocity.set(SPRINT, -3f, 0f);
        input.crouching = true;
        movement.update(p, input, 0f, 2.7f, SPRINT, true, DT);
        assertEquals(0f, p.velocity.x, .001f);
        assertEquals(SPRINT, p.velocity.z, .001f);
        assertEquals("steering must not alter jump/gravity", -3f, p.velocity.y, .001f);
    }

    @Test public void successfulHopRemainsSteerableAfterReleasingCrouchAndCanReverse() {
        ArenaMovement movement = new ArenaMovement();
        Combatant p = new Combatant(0, "test"); InputState input = new InputState();
        launchTimedHop(movement, p, input);
        float boosted = speed(p);
        movement.update(p, input, 0f, SPRINT, SPRINT, true, DT);
        assertEquals(0f, p.velocity.x, .001f);
        assertEquals(boosted, p.velocity.z, .001f);
        movement.update(p, input, -2.7f, 0f, SPRINT, true, DT);
        assertEquals(-boosted, p.velocity.x, .001f);
        assertEquals(0f, p.velocity.z, .001f);
        assertEquals(8f, p.velocity.y, .001f);
    }

    @Test public void repeatedBhopTurnsCannotAddSpeedOrSlowWithAnalogInput() {
        ArenaMovement movement = new ArenaMovement();
        Combatant p = new Combatant(0, "test"); InputState input = new InputState();
        launchTimedHop(movement, p, input);
        float boosted = speed(p);
        for (int i = 0; i < 240; i++) {
            float angle = i * .43f;
            float wishX = (float) Math.cos(angle) * .7f;
            float wishZ = (float) Math.sin(angle) * .7f;
            movement.update(p, input, wishX, wishZ, SPRINT, true, DT);
            assertEquals(boosted, speed(p), .001f);
            assertEquals(wishX / .7f * boosted, p.velocity.x, .001f);
            assertEquals(wishZ / .7f * boosted, p.velocity.z, .001f);
        }
    }

    @Test public void releasingMovementDuringBhopKeepsLastHeadingAndSpeed() {
        ArenaMovement movement = new ArenaMovement();
        Combatant p = new Combatant(0, "test"); InputState input = new InputState();
        launchTimedHop(movement, p, input);
        float boosted = speed(p);
        for (int i = 0; i < 10; i++) movement.update(p, input, 0f, 0f, SPRINT, true, DT);
        assertEquals(boosted, p.velocity.x, .001f);
        assertEquals(0f, p.velocity.z, .001f);
    }

    @Test public void bhopLandingSlideAndReleasedCrouchWindowFollowNewDirection() {
        ArenaMovement movement = new ArenaMovement();
        Combatant p = new Combatant(0, "test"); InputState input = new InputState();
        launchTimedHop(movement, p, input);
        movement.update(p, input, SPRINT, 0f, SPRINT, true, DT);
        input.crouching = true;
        movement.update(p, input, SPRINT, 0f, SPRINT, true, DT);
        float entrySpeed = speed(p);
        p.grounded = true;
        movement.update(p, input, 0f, 2.7f, SPRINT, true, DT);
        assertTrue(movement.sliding()); assertTrue(movement.hopReady());
        assertEquals(0f, p.velocity.x, .001f);
        assertTrue(p.velocity.z > entrySpeed - .02f);
        assertTrue("landing must not award a boost", speed(p) <= entrySpeed);
        input.crouching = false;
        movement.update(p, input, 0f, -SPRINT, SPRINT, true, DT);
        assertFalse(movement.sliding()); assertTrue(movement.hopReady());
        assertEquals(0f, p.velocity.x, .001f);
        float beforeJump = speed(p);
        assertEquals(-beforeJump, p.velocity.z, .001f);
        assertTrue(JumpRules.jump(p, 8f, false, true));
        movement.onJump(p, true);
        assertTrue("only the new timed jump awards speed", -p.velocity.z > beforeJump + .25f);
    }

    @Test public void missedWindowEndsDirectSteeringAndKeepsNormalMomentumDecay() {
        ArenaMovement movement = new ArenaMovement();
        Combatant p = new Combatant(0, "test"); InputState input = new InputState();
        launchTimedHop(movement, p, input);
        movement.update(p, input, SPRINT, 0f, SPRINT, true, DT);
        input.crouching = true;
        movement.update(p, input, SPRINT, 0f, SPRINT, true, DT);
        p.grounded = true;
        movement.update(p, input, SPRINT, 0f, SPRINT, true, DT);
        for (int i = 0; i < 12; i++) movement.update(p, input, SPRINT, 0f, SPRINT, true, DT);
        assertFalse(movement.hopReady());
        float before = speed(p);
        movement.update(p, input, 0f, 2.7f, SPRINT, true, DT);
        assertTrue(p.velocity.x > 0f);
        assertEquals(0f, p.velocity.z, .001f);
        assertTrue(speed(p) < before);
        assertTrue(JumpRules.jump(p, 8f, false, true));
        movement.onJump(p, true);
        movement.update(p, input, 0f, SPRINT, SPRINT, true, DT);
        assertTrue("a late jump has ordinary air strafe, not an active chain", p.velocity.x > 0f);
        assertTrue(p.velocity.z > 0f);
    }

    @Test public void resetClearsBhopSteeringAlongWithTheCombo() {
        ArenaMovement movement = new ArenaMovement();
        Combatant p = new Combatant(0, "test"); InputState input = new InputState();
        launchTimedHop(movement, p, input);
        float boosted = speed(p);
        movement.reset();
        movement.update(p, input, 0f, SPRINT, SPRINT, true, DT);
        assertEquals(boosted, p.velocity.x, .001f);
        assertTrue(p.velocity.z > 0f);
    }

    @Test public void aimingOrDisablingModifierEndsBhopSteering() {
        for (boolean aiming : new boolean[]{true, false}) {
            ArenaMovement movement = new ArenaMovement();
            Combatant p = new Combatant(0, "test"); InputState input = new InputState();
            launchTimedHop(movement, p, input);
            p.aiming = aiming;
            movement.update(p, input, SPRINT, 0f, SPRINT, aiming, DT);
            p.aiming = false;
            float beforeX = p.velocity.x;
            movement.update(p, input, 0f, SPRINT, SPRINT, true, DT);
            assertEquals(beforeX, p.velocity.x, .001f);
            assertTrue(p.velocity.z > 0f);
        }
    }

    @Test public void ordinaryJumpWithModifierButNoComboStillUsesAirStrafe() {
        ArenaMovement movement = new ArenaMovement();
        Combatant p = new Combatant(0, "test"); InputState input = new InputState();
        p.velocity.set(SPRINT, 3f, 0f);
        movement.update(p, input, 0f, SPRINT, SPRINT, true, DT);
        assertEquals(SPRINT, p.velocity.x, .001f);
        assertTrue(p.velocity.z > 0f);
    }
}
