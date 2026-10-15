package com.ccs.voxelgun.game;

import com.ccs.voxelgun.math.MathUtil;
import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.voxel.VoxelWorld;
import org.junit.Test;

import static org.junit.Assert.*;

/** Fast/low-gravity hops must inspect their actual flight corridor before taking off. */
public class BotHopLandingSafetyTest {
    @Test public void lowGravityRefreshesCachedPermissionAndRejectsADistantLavaLanding() {
        VoxelWorld world = floor(96);
        try {
            stripe(world, 26, 0, Materials.LAVA);
            Combatant bot = bot();
            BotMovementSafety safety = new BotMovementSafety();
            assertTrue("ordinary hop ends before the lava", safety.canHop(bot, world, 10f, 0f, 10f, 1f, Match.STEP));
            assertFalse("low gravity extends this same hop into lava beyond the walking probe",
                    safety.canHop(bot, world, 10f, 0f, 10f, .4f, Match.STEP));
        } finally { world.release(); }
    }

    @Test public void gainedSpeedInvalidatesShortSafeProbeBeforeItsCacheTimerExpires() {
        VoxelWorld world = floor(96);
        try {
            stripe(world, 29, 0, Materials.LAVA);
            Combatant bot = bot();
            BotMovementSafety safety = new BotMovementSafety();
            assertTrue(safety.canHop(bot, world, 5.2f, 0f, 5.2f, 1f, Match.STEP));
            assertFalse("a faster timed hop reused the earlier short corridor",
                    safety.canHop(bot, world, 5.2f, 0f, 27f, 1f, Match.STEP));
        } finally { world.release(); }
    }

    @Test public void theEntireFlightNeedsSupportEvenWhenFarLandingWouldBeSolid() {
        for (int hazard : new int[]{Materials.AIR, Materials.LAVA, Materials.WATER & 255}) {
            VoxelWorld world = floor(96);
            try {
                stripe(world, 24, hazard == (Materials.WATER & 255) ? 1 : 0, hazard);
                Combatant bot = bot();
                assertFalse("hop crossed hazard " + hazard + " hidden beyond the short walking probe",
                        new BotMovementSafety().canHop(bot, world, 12f, 0f, 12f, .4f, Match.STEP));
            } finally { world.release(); }
        }
    }

    @Test public void lowGravityChecksTheHigherArcAgainstADistantCeiling() {
        VoxelWorld world = floor(96);
        try {
            for (int x = 19; x <= 23; x++) for (int z = 5; z <= 11; z++)
                world.set(x, 5, z, Materials.CONCRETE);
            world.commit();
            Combatant bot = bot();
            BotMovementSafety safety = new BotMovementSafety();
            assertTrue("ordinary arc fits below the beam", safety.canHop(bot, world, 10f, 0f, 10f, 1f, Match.STEP));
            assertFalse("the low-gravity arc would hit the beam far beyond takeoff headroom checks",
                    safety.canHop(bot, world, 10f, 0f, 10f, .4f, Match.STEP));
        } finally { world.release(); }
    }

    @Test public void aNarrowSupportedStripDoesNotMakeItsUnsafeLandingSidesAcceptable() {
        VoxelWorld world = floor(96);
        try {
            for (int x = 23; x <= 42; x++) {
                world.set(x, 0, 7, Materials.LAVA);
                world.set(x, 0, 9, Materials.LAVA);
            }
            world.commit();
            assertFalse(new BotMovementSafety().canHop(bot(), world, 12f, 0f, 12f, .4f, Match.STEP));
        } finally { world.release(); }
    }

    @Test public void controllerIncludesTheNextEarnedSpeedBoostInItsTakeoffDecision() {
        VoxelWorld world = floor(96);
        try {
            // At 12m/s normal gravity the unboosted corridor ends before x23; a timed reward
            // extends it beyond x23. The controller must account for the reward before launch.
            stripe(world, 23, 0, Materials.LAVA);
            Combatant bot = bot();
            bot.velocity.x = 12f;
            BotBhopController controller = new BotBhopController(1f, bot.id);
            controller.update(bot, world, 12f, 0f, 15.4f, true, 1f, Match.STEP);
            assertTrue("the controller launched before checking the next speed reward", bot.grounded);
            assertEquals(0f, bot.velocity.y, 0f);
            assertTrue("unsafe hopping must still permit ordinary ground navigation", bot.velocity.x > 0f);
        } finally { world.release(); }
    }

    @Test public void doubleSpeedLowGravityStillAllowsRealHopChainsOnASafeLongFloor() {
        VoxelWorld world = floor(512);
        try {
            Combatant bot = bot();
            BotBhopController controller = new BotBhopController(1f, bot.id);
            int takeoffs = 0;
            for (int i = 0; i < 600 && takeoffs < 4; i++) {
                boolean grounded = bot.grounded;
                float previousY = bot.velocity.y;
                controller.update(bot, world, 10.4f, 0f, 15.4f, true, .4f, Match.STEP);
                if (grounded && !bot.grounded) takeoffs++;
                if (!grounded) assertEquals("predicting a landing must not change airborne gravity",
                        previousY, bot.velocity.y, .0001f);
                bot.crouchBlend = MathUtil.clamp(MathUtil.damp(bot.crouchBlend,
                        bot.crouching ? 1f : 0f, 13f, Match.STEP), 0f, 1f);
                bot.velocity.y = Math.max(Physics.TERMINAL_VELOCITY,
                        bot.velocity.y + Physics.GRAVITY * .4f * Match.STEP);
                bot.grounded = Physics.move(world, bot.position, bot.velocity, Match.STEP,
                        Combatant.RADIUS, bot.height(), true);
                if (bot.grounded) bot.airJumpUsed = false;
                assertTrue(bot.position.y >= .99f);
            }
            assertEquals("prediction disabled legitimate modifier-compatible hops", 4, takeoffs);
        } finally { world.release(); }
    }

    @Test public void unreasonableFlightDistanceIsRejectedInsteadOfSpendingAnUnboundedProbe() {
        VoxelWorld world = floor(512);
        try {
            assertFalse(new BotMovementSafety().canHop(bot(), world, 200f, 0f, 200f, .1f, Match.STEP));
        } finally { world.release(); }
    }

    private static Combatant bot() {
        Combatant bot = new Combatant(2, "Landing scout");
        bot.bot = true;
        bot.grounded = true;
        bot.position.set(12.5f, 1f, 8.5f);
        return bot;
    }

    private static VoxelWorld floor(int width) {
        VoxelWorld world = new VoxelWorld(width, 16, 16);
        for (int x = 0; x < width; x++) for (int z = 0; z < 16; z++) world.set(x, 0, z, Materials.CONCRETE);
        world.commit();
        return world;
    }

    private static void stripe(VoxelWorld world, int x, int y, int material) {
        for (int z = 0; z < world.sizeZ(); z++) world.set(x, y, z, material);
        world.commit();
    }
}
