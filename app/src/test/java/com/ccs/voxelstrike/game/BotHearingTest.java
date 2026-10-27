package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.voxel.VoxelWorld;
import org.junit.Test;

import static org.junit.Assert.*;

public class BotHearingTest {
    private Combatant listener() {
        Combatant bot = new Combatant(3, "Listener");
        bot.position.set(10.5f, 1f, 10.5f);
        return bot;
    }

    private void wall(VoxelWorld world, int fromX, int toX, int material) {
        for (int x = fromX; x < toX; x++) for (int y = 1; y < 7; y++)
            for (int z = 0; z < world.sizeZ(); z++) world.set(x, y, z, material);
    }

    @Test public void gunshotsRemainAudibleInAirAndThroughThinWallsButNotThickWalls() {
        VoxelWorld world = new VoxelWorld(80, 12, 32);
        try {
            Combatant bot = listener();
            BotHearing hearing = new BotHearing(34);
            assertTrue(hearing.accept(bot, world, 35.5f, 1f, 10.5f, 45f, false, Difficulty.REGULAR));
            wall(world, 20, 21, Materials.CONCRETE);
            hearing.reset();
            assertTrue(hearing.accept(bot, world, 35.5f, 1f, 10.5f, 45f, false, Difficulty.REGULAR));
            wall(world, 20, 26, Materials.CONCRETE);
            hearing.reset();
            assertFalse(hearing.accept(bot, world, 35.5f, 1f, 10.5f, 45f, false, Difficulty.REGULAR));
        } finally { world.release(); }
    }

    @Test public void separateWallsAccumulateAttenuationAndGlassIsQuieterThanConcrete() {
        VoxelWorld world = new VoxelWorld(80, 12, 32);
        try {
            Combatant bot = listener();
            BotHearing hearing = new BotHearing(10);
            wall(world, 20, 21, Materials.CONCRETE);
            wall(world, 25, 26, Materials.CONCRETE);
            wall(world, 30, 31, Materials.CONCRETE);
            assertFalse(hearing.accept(bot, world, 40.5f, 1f, 10.5f, 45f, false, Difficulty.ELITE));
            wall(world, 20, 21, Materials.GLASS);
            wall(world, 25, 26, Materials.GLASS);
            wall(world, 30, 31, Materials.GLASS);
            hearing.reset();
            assertTrue(hearing.accept(bot, world, 40.5f, 1f, 10.5f, 45f, false, Difficulty.ELITE));
        } finally { world.release(); }
    }

    @Test public void wallsMuffleFootstepsMoreThanGunfire() {
        VoxelWorld world = new VoxelWorld(64, 12, 32);
        try {
            Combatant bot = listener();
            BotHearing hearing = new BotHearing(12);
            assertTrue(hearing.accept(bot, world, 20.5f, 1f, 10.5f, 14f, true, Difficulty.ELITE));
            wall(world, 14, 16, Materials.CONCRETE);
            hearing.reset();
            assertFalse(hearing.accept(bot, world, 20.5f, 1f, 10.5f, 14f, true, Difficulty.ELITE));
            hearing.reset();
            assertTrue(hearing.accept(bot, world, 20.5f, 1f, 10.5f, 45f, false, Difficulty.ELITE));
        } finally { world.release(); }
    }

    @Test public void nearbyFootstepsInterruptDistantGunfireAndKeepAttention() {
        VoxelWorld world = new VoxelWorld(80, 12, 32);
        try {
            Combatant bot = listener();
            BotHearing hearing = new BotHearing(12);
            assertTrue(hearing.accept(bot, world, 45.5f, 1f, 10.5f, 45f, false, Difficulty.REGULAR));
            float distantUrgency = hearing.urgency();
            assertTrue("an immediate nearby step may override the distant shot's cooldown",
                    hearing.accept(bot, world, 13.5f, 1f, 10.5f, 14f, true, Difficulty.REGULAR));
            assertTrue(hearing.urgency() > distantUrgency);
            float nearX = hearing.x(), nearZ = hearing.z();
            hearing.update(1.1f);
            assertFalse(hearing.accept(bot, world, 45.5f, 1f, 10.5f, 45f, false, Difficulty.REGULAR));
            assertEquals(nearX, hearing.x(), 0f);
            assertEquals(nearZ, hearing.z(), 0f);
            hearing.update(1f);
            assertTrue("old cues eventually stop competing", hearing.accept(bot, world,
                    45.5f, 1f, 10.5f, 45f, false, Difficulty.REGULAR));
        } finally { world.release(); }
    }

    @Test public void burstRateAndRejectedProbesHaveFixedBudgets() {
        VoxelWorld world = new VoxelWorld(128, 12, 32);
        try {
            Combatant bot = listener();
            BotHearing hearing = new BotHearing(23);
            assertTrue(hearing.accept(bot, world, 55.5f, 1f, 10.5f, 45f, false, Difficulty.ELITE));
            assertTrue(hearing.probeSamples() <= 48);
            for (int event = 0; event < 200; event++) {
                assertFalse(hearing.accept(bot, world, 55.5f, 1f, 10.5f, 45f, false, Difficulty.ELITE));
                assertEquals(0, hearing.probeSamples());
            }
            assertTrue(hearing.accept(bot, world, 13.5f, 1f, 10.5f, 14f, true, Difficulty.ELITE));
            assertFalse("only one urgency override is allowed per cooldown",
                    hearing.accept(bot, world, 10.6f, 1f, 10.5f, 14f, true, Difficulty.ELITE));
            assertEquals(0, hearing.probeSamples());
            hearing.reset();
            wall(world, 20, 30, Materials.CONCRETE);
            assertFalse(hearing.accept(bot, world, 55.5f, 1f, 10.5f, 45f, false, Difficulty.ELITE));
            assertTrue(hearing.probeSamples() > 0);
            for (int event = 0; event < 200; event++) {
                assertFalse(hearing.accept(bot, world, 55.5f, 1f, 10.5f, 45f, false, Difficulty.ELITE));
                assertEquals(0, hearing.probeSamples());
            }
            hearing.update(.13f);
            assertFalse(hearing.accept(bot, world, 55.5f, 1f, 10.5f, 45f, false, Difficulty.ELITE));
            assertTrue(hearing.probeSamples() > 0 && hearing.probeSamples() <= 48);
        } finally { world.release(); }
    }

    @Test public void everyDifficultyGetsStableImpreciseCoordinates() {
        VoxelWorld world = new VoxelWorld(80, 12, 32);
        try {
            Combatant bot = listener();
            float recruitError = 0f, eliteError = 0f;
            for (Difficulty difficulty : Difficulty.values()) {
                BotHearing hearing = new BotHearing(74);
                assertTrue(hearing.accept(bot, world, 35.5f, 1f, 10.5f, 45f, false, difficulty));
                float x = hearing.x(), z = hearing.z();
                float error = (float) Math.hypot(x - 35.5f, z - 10.5f);
                assertTrue("no difficulty receives exact source coordinates", error >= .5f);
                assertTrue("the cue is still a useful neighborhood", error < 4.5f);
                if (difficulty == Difficulty.RECRUIT) recruitError = error;
                if (difficulty == Difficulty.ELITE) eliteError = error;
                hearing.update(2f);
                assertTrue(hearing.accept(bot, world, 35.5f, 1f, 10.5f, 45f, false, difficulty));
                assertEquals("bursts cannot be averaged into the true source", x, hearing.x(), 0f);
                assertEquals(z, hearing.z(), 0f);
                assertEquals(0f, bot.velocity.lengthSquared(), 0f);
                assertFalse("hearing alone cannot fire a weapon", bot.firing);
            }
            assertTrue("skill improves the estimate without eliminating error", recruitError > eliteError);
        } finally { world.release(); }
    }

    @Test public void deadListenersAndSuppressedInvestigationsCannotAcceptUrgentSounds() {
        VoxelWorld world = new VoxelWorld(64, 12, 32);
        try {
            Combatant bot = listener();
            BotHearing hearing = new BotHearing(1);
            bot.alive = false;
            assertFalse(hearing.accept(bot, world, 11f, 1f, 10.5f, 45f, false, Difficulty.ELITE));
            assertEquals(0, hearing.probeSamples());
            bot.alive = true;
            hearing.suppressFor(2f);
            hearing.update(1f);
            assertFalse(hearing.accept(bot, world, 11f, 1f, 10.5f, 14f, true, Difficulty.ELITE));
            assertEquals(0, hearing.probeSamples());
            hearing.update(1.1f);
            assertTrue(hearing.accept(bot, world, 11f, 1f, 10.5f, 14f, true, Difficulty.ELITE));
            hearing.reset();
            assertTrue("respawn clears all cooldowns", hearing.accept(bot, world,
                    11f, 1f, 10.5f, 14f, true, Difficulty.ELITE));
        } finally { world.release(); }
    }

    @Test public void invalidAndDistantEventsAreRejectedWithoutSampling() {
        VoxelWorld world = new VoxelWorld(80, 12, 32);
        try {
            Combatant bot = listener();
            BotHearing hearing = new BotHearing(1);
            assertFalse(hearing.accept(bot, world, 11f, 1f, 10.5f, 0f, true, Difficulty.ELITE));
            assertFalse(hearing.accept(bot, world, 70f, 1f, 10.5f, 45f, false, Difficulty.ELITE));
            assertFalse(hearing.accept(bot, world, Float.NaN, 1f, 10.5f, 45f, false, Difficulty.ELITE));
            assertEquals(0, hearing.probeSamples());
        } finally { world.release(); }
    }
}
