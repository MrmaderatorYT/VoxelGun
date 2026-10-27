package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.voxel.VoxelWorld;
import org.junit.Test;

import static org.junit.Assert.*;

public class BotLocalRecoveryTest {
    @Test public void stepsAroundAnOverheadBlockAndDropsOntoVerifiedLowerFloor() {
        for (float gravity : new float[] {1f, .4f}) {
            VoxelWorld world = pillarAndRoof(true);
            try {
                Combatant bot = trappedBot();
                assertFalse(BotMovementSafety.canDodge(bot, world, 0f, 1f, false));
                assertFalse(BotMovementSafety.canDodge(bot, world, 1f, 0f, false));
                BotLocalRecovery recovery = new BotLocalRecovery();
                assertTrue("a supported two-leg descent exists beside the pillar",
                        recovery.begin(bot, world, gravity, 0f, -1f));
                assertEquals(5.5f, bot.position.x, 0f);
                assertEquals(5f, bot.position.y, 0f);
                boolean landed = false;
                for (int i = 0; i < 150; i++) {
                    boolean driving = recovery.update(bot, world, Match.STEP);
                    bot.velocity.y += Physics.GRAVITY * gravity * Match.STEP;
                    bot.grounded = Physics.move(world, bot.position, bot.velocity, Match.STEP,
                            Combatant.RADIUS, bot.height(), true);
                    assertFalse(world.boxCollides(bot.position.x, bot.position.y, bot.position.z,
                            Combatant.RADIUS, bot.height()));
                    assertTrue("recovery must never fall blindly into the void", bot.position.y >= 3f);
                    if (bot.grounded && Math.abs(bot.position.y - 3f) < .01f) landed = true;
                    if (!driving) break;
                }
                assertTrue(landed);
                assertFalse(recovery.active());
                assertTrue(bot.position.x >= 6f);
                assertTrue(bot.position.z >= 6f);
                assertEquals(3f, bot.position.y, .01f);
            } finally { world.release(); }
        }
    }

    @Test public void refusesTheSameDescentWhenThereIsOnlyVoidBelow() {
        VoxelWorld world = pillarAndRoof(false);
        try {
            Combatant bot = trappedBot();
            BotLocalRecovery recovery = new BotLocalRecovery();
            assertFalse(recovery.begin(bot, world, .4f, 0f, -1f));
            assertEquals(5.5f, bot.position.x, 0f);
            assertEquals(5f, bot.position.y, 0f);
            assertEquals(0f, bot.velocity.lengthSquared(), 0f);
        } finally { world.release(); }
    }

    @Test public void doesNotStartAnotherRecoveryWhileAirborne() {
        VoxelWorld world = pillarAndRoof(true);
        try {
            Combatant bot = trappedBot();
            bot.grounded = false;
            assertFalse(new BotLocalRecovery().begin(bot, world, .4f, 0f, -1f));
        } finally { world.release(); }
    }

    @Test public void rejectsLavaAndJumpPadsAsOrdinarySafeLandings() {
        for (int material : new int[] {Materials.LAVA, Materials.JUMP_PAD}) {
            VoxelWorld world = pillarAndRoof(true);
            try {
                for (int x = 6; x <= 13; x++) for (int z = 0; z <= 13; z++)
                    world.set(x, 2, z, material);
                world.commit();
                assertFalse(new BotLocalRecovery().begin(trappedBot(), world, .4f, 0f, -1f));
            } finally { world.release(); }
        }
    }

    /** Scaled-down geometry of the Sky Islands pillar captured by the 35-second map sweep. */
    private static VoxelWorld pillarAndRoof(boolean lowerFloor) {
        VoxelWorld world = new VoxelWorld(16, 12, 16);
        if (lowerFloor) for (int x = 6; x <= 13; x++) for (int z = 0; z <= 13; z++)
            world.set(x, 2, z, Materials.CONCRETE);
        world.set(5, 3, 5, Materials.CONCRETE);
        world.set(5, 4, 5, Materials.CONCRETE);
        for (int x = 5; x <= 13; x++) for (int z = 0; z <= 5; z++)
            if (x != 5 || z != 5) for (int y = 6; y <= 8; y++)
                world.set(x, y, z, Materials.CONCRETE);
        world.commit();
        return world;
    }

    private static Combatant trappedBot() {
        Combatant bot = new Combatant(3, "Ledge recovery");
        bot.bot = true;
        bot.position.set(5.5f, 5f, 5.343674f);
        bot.grounded = true;
        return bot;
    }
}
