package com.ccs.voxelgun.game;

import com.ccs.voxelgun.input.InputState;
import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.voxel.VoxelWorld;

import org.junit.Test;

import static org.junit.Assert.*;

/** Exercises the camera and hands against real collision/jump updates, rather than offsets alone. */
public class JumpPresentationTest {
    private static Match platform(GameMode mode) {
        MatchConfig config = new MatchConfig();
        config.mode = mode;
        config.mapIndex = 0;
        config.botCount = 0;
        config.seed = 4242L;
        Match match = new Match(config);
        InputState idle = new InputState();
        for (int i = 0; i < 210; i++) match.update(Match.STEP, idle);
        VoxelWorld world = match.world();
        for (int z = 20; z <= 30; z++) {
            for (int x = 58; x <= 110; x++) {
                for (int y = 0; y <= 16; y++) {
                    world.set(x, y, z, y == 0 ? Materials.CONCRETE
                            : y == 1 && x >= 70 ? Materials.STEEL : Materials.AIR);
                }
            }
        }
        world.commit();
        Combatant player = match.player();
        player.position.set(65.5f, 1f, 25.5f);
        player.velocity.set(0f, 0f, 0f);
        player.grounded = true;
        player.yaw = 0f;
        player.crouching = false;
        player.crouchBlend = 0f;
        player.landImpact = player.viewBob = 0f;
        match.update(Match.STEP, idle);
        return match;
    }

    private static void assertCameraInsideBody(Match match) {
        Combatant player = match.player();
        assertTrue("camera fell below the clear body volume",
                match.cameraPosition.y >= player.position.y + 0.18f - 0.0001f);
        assertTrue("camera rose above the clear body volume",
                match.cameraPosition.y <= player.position.y + player.height() - 0.1f + 0.0001f);
        assertFalse("camera entered a solid terrain or ceiling voxel",
                match.world().solid(VoxelWorld.floor(match.cameraPosition.x),
                        VoxelWorld.floor(match.cameraPosition.y), VoxelWorld.floor(match.cameraPosition.z)));
    }

    @Test public void physicalStepRemainsImmediateWhileCameraRisesAndSettlesAcrossTicks() {
        Match match = platform(GameMode.RANGE);
        try {
            Combatant player = match.player();
            InputState input = new InputState();
            input.moveX = 1f;
            boolean stepped = false;
            for (int tick = 0; tick < 100; tick++) {
                float oldFeet = player.position.y;
                float oldCamera = match.cameraPosition.y;
                match.update(Match.STEP, input);
                assertCameraInsideBody(match);
                if (player.position.y - oldFeet < 0.5f) continue;
                stepped = true;
                assertEquals("collision still settles directly on the new tread", 2f, player.position.y, 0.0001f);
                assertTrue(player.grounded);
                assertEquals(0f, player.velocity.y, 0.0001f);
                assertTrue("camera must not inherit the one-voxel snap", match.cameraPosition.y - oldCamera < 0.4f);
                assertTrue("the view must continue catching up after the physical step",
                        player.eyeY() - match.cameraPosition.y > 0.5f);
                float firstCamera = match.cameraPosition.y;
                input.moveX = 0f;
                match.update(Match.STEP, input);
                assertTrue("step presentation must rise over more than one tick", match.cameraPosition.y > firstCamera);
                for (int i = 0; i < 40; i++) {
                    match.update(Match.STEP, input);
                    assertCameraInsideBody(match);
                }
                assertEquals("stationary view must recover to the real eye", player.eyeY(), match.cameraPosition.y, 0.01f);
                break;
            }
            assertTrue("the real platform was never reached", stepped);
        } finally { match.release(); }
    }

    @Test public void contextualAutoJumpKeepsItsArcAndAddsHandsAndSoftLanding() {
        Match match = platform(GameMode.RANGE);
        try {
            Combatant player = match.player();
            InputState input = new InputState();
            input.moveX = 1f;
            input.autoJump = true;
            int takeoffs = 0, airborneTicks = 0;
            float highestFeet = 1f, largestRise = 0f, lowestHands = 0f, highestHands = 0f;
            boolean landed = false;
            for (int tick = 0; tick < 100; tick++) {
                boolean wasGrounded = player.grounded;
                float oldFeet = player.position.y;
                match.update(Match.STEP, input);
                highestFeet = Math.max(highestFeet, player.position.y);
                largestRise = Math.max(largestRise, player.position.y - oldFeet);
                lowestHands = Math.min(lowestHands, match.weaponJumpOffset);
                highestHands = Math.max(highestHands, match.weaponJumpOffset);
                if (!player.grounded) airborneTicks++;
                if (wasGrounded && !player.grounded && player.velocity.y > 0f) takeoffs++;
                assertCameraInsideBody(match);
                if (!wasGrounded && player.grounded) {
                    landed = true;
                    assertEquals(2f, player.position.y, 0.0001f);
                    assertTrue("a light ledge landing needs a visible recovery", player.landImpact > 0f);
                    assertTrue("the landing reaction must remain subtle", player.landImpact < 0.25f);
                    input.moveX = 0f;
                }
            }
            assertTrue(landed);
            assertEquals("obstacle auto-jump must not repeat on the flat platform", 1, takeoffs);
            assertTrue("preserve the measured one-block obstacle flight", airborneTicks >= 26 && airborneTicks <= 28);
            assertEquals("presentation must not change ballistic jump height", 2.1833f, highestFeet, 0.02f);
            assertTrue("a real hop must rise over multiple physics ticks", largestRise < 0.13f);
            assertTrue("hands should lag downward during launch", lowestHands < -0.015f);
            assertTrue("hands should recover as the body descends", highestHands > 0.003f);
            assertEquals("presentation does not introduce fall damage", Combatant.MAX_HEALTH, player.health, 0.0001f);
            assertEquals("hands must settle when standing still", 0f, match.weaponJumpOffset, 0.002f);
        } finally { match.release(); }
    }

    @Test public void twoDiagonalRisersDoNotBypassCameraSmoothing() {
        Match match = platform(GameMode.RANGE);
        try {
            VoxelWorld world = match.world();
            for (int z = 20; z <= 30; z++) for (int x = 58; x <= 110; x++) {
                int surface = (x >= 70 ? 1 : 0) + (z >= 25 ? 1 : 0);
                for (int y = 0; y <= 4; y++)
                    world.set(x, y, z, y <= surface ? Materials.STEEL : Materials.AIR);
            }
            world.commit();
            Combatant player = match.player();
            player.position.set(69.65f, 1f, 24.65f);
            player.velocity.set(0f, 0f, 0f);
            player.grounded = true;
            match.update(Match.STEP, new InputState());
            float previousCamera = match.cameraPosition.y;
            player.velocity.set(3.7f, 0f, 3.7f);
            InputState input = new InputState();
            input.moveX = 1f;
            input.moveY = -1f;
            match.update(Match.STEP, input);
            assertEquals("both axis collisions still climb their supported risers", 3f, player.position.y, 0.0001f);
            assertTrue(player.grounded);
            assertTrue("two risers in one tick must also receive presentation smoothing",
                    match.cameraPosition.y - previousCamera < 1f);
            assertCameraInsideBody(match);
        } finally { match.release(); }
    }

    @Test public void crouchedStepAndLandingKeepCameraAboveFloorAndBelowLowCeiling() {
        Match match = platform(GameMode.RANGE);
        try {
            VoxelWorld world = match.world();
            for (int z = 20; z <= 30; z++) for (int x = 70; x <= 110; x++)
                world.set(x, 4, z, Materials.CONCRETE);
            world.commit();
            Combatant player = match.player();
            player.position.set(69.4f, 1f, 25.5f);
            player.crouching = true;
            player.crouchBlend = 1f;
            InputState input = new InputState();
            input.crouching = true;
            match.update(Match.STEP, input);
            input.moveX = 1f;
            player.landImpact = 1f; // Combine the two visual effects at crouched eye height.
            boolean stepped = false;
            for (int i = 0; i < 40; i++) {
                match.update(Match.STEP, input);
                assertCameraInsideBody(match);
                stepped |= player.position.y > 1.9f;
            }
            assertTrue("crouched collision must still climb the platform", stepped);
            assertTrue(player.crouching);
            assertEquals(2f, player.position.y, 0.0001f);
            assertTrue(match.cameraPosition.y < 4f);
        } finally { match.release(); }
    }

    @Test public void courseRetryClearsMovementPresentationInsteadOfAnimatingTheTeleport() {
        Match match = platform(GameMode.MOVEMENT_TRAINING);
        try {
            InputState input = new InputState();
            input.queueJump();
            match.update(Match.STEP, input);
            assertTrue("prepare an actual airborne weapon offset", match.weaponJumpOffset < -0.005f);
            match.player().landImpact = 0.8f;
            input.queueCourseRestart();
            input.paused = true; // Inspect the start-pad pose before another movement tick.
            match.update(Match.STEP, input);
            assertEquals(8.5f, match.player().position.x, 0.0001f);
            assertEquals(3f, match.player().position.y, 0.0001f);
            assertEquals(32.5f, match.player().position.z, 0.0001f);
            assertEquals(0f, match.weaponJumpOffset, 0.0001f);
            assertEquals(0f, match.player().landImpact, 0.0001f);
            assertEquals(match.player().eyeY(), match.cameraPosition.y, 0.0001f);
            assertCameraInsideBody(match);
        } finally { match.release(); }
    }
}
