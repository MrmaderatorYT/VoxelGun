package com.ccs.voxelgun.game;

import com.ccs.voxelgun.math.Vec3;
import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.voxel.VoxelWorld;
import com.ccs.voxelgun.weapon.WeaponType;
import org.junit.Test;

import static org.junit.Assert.*;

public class BotEvasionTerrainTest {
    @Test public void anAlreadyActiveFastSlideStopsBeforeLavaOrVoidBeyondItsInitialProbe() {
        assertTrue("the unrestricted .68-second maneuver would cross this boundary",
                simulate(Materials.CONCRETE, false) > 18f);
        for (int hazard : new int[]{Materials.AIR, Materials.LAVA}) {
            float stoppedX = simulate(hazard, true);
            assertTrue("the response must really start and travel beyond the initial vicinity", stoppedX > 14.5f);
            assertTrue("the entire body must stop on the safe side", stoppedX + Combatant.RADIUS < 18f);
        }
    }

    private static float simulate(int hazard, boolean expectSafetyAbort) {
        VoxelWorld world = new VoxelWorld(40, 12, 32);
        try {
            for (int z = 0; z < world.sizeZ(); z++) for (int x = 0; x < world.sizeX(); x++)
                world.set(x, 0, z, x < 18 ? Materials.CONCRETE : hazard);
            world.commit();
            Combatant bot = new Combatant(2, "Slide response");
            bot.setSingleWeapon(WeaponType.MP7K);
            bot.position.set(12.5f, 1f, 12.5f);
            bot.grounded = true; bot.lastDamageTime = 100f;
            Combatant enemy = new Combatant(99, "Visible shooter");
            enemy.position.set(12.5f, 1f, 4.5f);
            enemy.yaw = (float) Math.PI; enemy.firing = true;
            BotEvasionController evasion = new BotEvasionController(Difficulty.ELITE, BotCombatStyle.RUSHER);
            Vec3 wish = new Vec3();
            boolean started = false, slid = false, safetyAborted = false;
            int activeTicks = 0;
            for (int tick = 0; tick < 65; tick++) {
                evasion.observe(bot, enemy, Match.STEP);
                boolean activeBeforeSteering = evasion.active();
                if (activeBeforeSteering) {
                    started = true; activeTicks++;
                    enemy.firing = false; // Observe one finite response, not a second incoming burst.
                }
                assertFalse("the selected archetype slides, without synthesizing a jump",
                        evasion.steer(bot, world, wish, 15.4f, false, Match.STEP));
                slid |= evasion.sliding() && bot.crouching;
                if (activeBeforeSteering && !evasion.active()) {
                    safetyAborted = true;
                    assertEquals(0f, wish.lengthSquared(), 0f);
                    assertEquals(0f, bot.velocity.x, 0f);
                    assertEquals(0f, bot.velocity.z, 0f);
                }
                bot.velocity.y += Physics.GRAVITY * Match.STEP;
                bot.grounded = Physics.move(world, bot.position, bot.velocity, Match.STEP,
                        Combatant.RADIUS, bot.height(), true);
                if (expectSafetyAbort) {
                    assertTrue("a long slide must not coast off the safe platform", bot.grounded);
                    assertTrue(bot.position.x + Combatant.RADIUS < 18f);
                    assertEquals(Materials.CONCRETE, world.get(VoxelWorld.floor(bot.position.x),
                            0, VoxelWorld.floor(bot.position.z)));
                }
                // Natural expiry is not a terrain abort. End the control before inertial coasting.
                if (started && !evasion.active() && !safetyAborted) break;
            }
            assertTrue(started);
            assertTrue("the test must reach the actual crouched slide phase", slid);
            assertEquals(expectSafetyAbort, safetyAborted);
            if (expectSafetyAbort) assertTrue("stop before the normal response timer expires",
                    activeTicks * Match.STEP < .68f);
            return bot.position.x;
        } finally { world.release(); }
    }
}
