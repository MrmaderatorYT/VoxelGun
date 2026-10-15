package com.ccs.voxelgun.game;

import com.ccs.voxelgun.math.Vec3;
import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.voxel.VoxelWorld;
import com.ccs.voxelgun.weapon.WeaponType;
import org.junit.Test;
import static org.junit.Assert.*;

public class BotEvasionControllerTest {
    private VoxelWorld floor() {
        VoxelWorld world = new VoxelWorld(32, 12, 32);
        for (int z = 0; z < world.sizeZ(); z++) for (int x = 0; x < world.sizeX(); x++) {
            world.set(x, 0, z, Materials.CONCRETE);
        }
        world.commit(); return world;
    }

    private Combatant bot(int id) {
        Combatant c = new Combatant(id, "Rusher"); c.setSingleWeapon(WeaponType.MP7K);
        c.position.set(12.5f, 1f, 12.5f); c.grounded = true; c.lastDamageTime = 100f;
        return c;
    }

    private Combatant shooter() {
        Combatant c = new Combatant(99, "Enemy"); c.position.set(12.5f, 1f, 4.5f);
        c.yaw = (float) Math.PI; c.firing = true; return c;
    }

    private int reactionTicks(Difficulty difficulty) {
        Combatant c = bot(2), target = shooter();
        BotEvasionController controller = new BotEvasionController(difficulty, BotCombatStyle.RUSHER);
        for (int tick = 1; tick < 120; tick++) {
            controller.observe(c, target, Match.STEP);
            if (controller.active()) return tick;
        }
        return 120;
    }

    @Test public void visibleIncomingShotHasRealSkillScaledReactionDelay() {
        assertTrue(reactionTicks(Difficulty.ELITE) > 1);
        assertTrue(reactionTicks(Difficulty.ELITE) < reactionTicks(Difficulty.RECRUIT));
    }

    @Test public void firingAwayFromBotIsNotAnIncomingThreat() {
        Combatant c = bot(2), target = shooter(); target.yaw = 0f;
        BotEvasionController controller = new BotEvasionController(Difficulty.ELITE, BotCombatStyle.RUSHER);
        for (int tick = 0; tick < 120; tick++) controller.observe(c, target, Match.STEP);
        assertFalse(controller.active());
    }

    @Test public void groundedDodgeBuildsSprintBeforeSharedSlideAndStaysUnderCap() {
        VoxelWorld world = floor();
        try {
            Combatant c = bot(2), target = shooter(); Vec3 wish = new Vec3();
            BotEvasionController controller = new BotEvasionController(Difficulty.ELITE, BotCombatStyle.RUSHER);
            boolean crouched = false, sprinted = false; float reference = 7.7f;
            for (int tick = 0; tick < 65; tick++) {
                controller.observe(c, target, Match.STEP);
                assertFalse("slide archetype does not spend any jump budget",
                        controller.steer(c, world, wish, reference, false, Match.STEP));
                if (controller.active()) {
                    sprinted |= c.sprinting; crouched |= c.crouching;
                    assertTrue(Math.hypot(c.velocity.x, c.velocity.z)
                            <= reference * ArenaMovement.MAX_SPEED_FACTOR + .001f);
                }
            }
            assertTrue("slide first reaches the player's real sprint threshold", sprinted);
            assertTrue("then makes a new crouch press", crouched);
            assertFalse("finite response finishes even if enemy keeps firing", controller.active());
        } finally { world.release(); }
    }

    @Test public void evasiveJumpIsOneExplicitGroundPressNeverAnAirJump() {
        VoxelWorld world = floor();
        try {
            Combatant c = bot(6), target = shooter(); Vec3 wish = new Vec3();
            BotEvasionController controller = new BotEvasionController(Difficulty.ELITE, BotCombatStyle.RUSHER);
            int requests = 0;
            for (int tick = 0; tick < 65; tick++) {
                controller.observe(c, target, Match.STEP);
                if (controller.steer(c, world, wish, 7.7f, false, Match.STEP)) {
                    requests++; assertTrue(c.grounded);
                    assertTrue(JumpRules.jump(c, 7.2f, false, true));
                }
                assertFalse(c.airJumpUsed);
            }
            assertEquals(1, requests);
        } finally { world.release(); }
    }

    @Test public void precisionWeaponDoesNotEvadeIntoUncontrolledAirShots() {
        Combatant c = bot(6), target = shooter(); c.setSingleWeapon(WeaponType.AWM_X);
        BotEvasionController controller = new BotEvasionController(Difficulty.ELITE, BotCombatStyle.SNIPER);
        for (int tick = 0; tick < 100; tick++) controller.observe(c, target, Match.STEP);
        assertFalse(controller.active());
    }

    @Test public void airborneReactionStrafesWithoutChangingVerticalPhysicsOrSpendingAirJump() {
        VoxelWorld world = floor();
        try {
            Combatant c = bot(6), target = shooter(); Vec3 wish = new Vec3();
            c.position.y = 2.15f; c.grounded = false; c.velocity.set(0f, -1.6f, -5.2f);
            BotEvasionController controller = new BotEvasionController(Difficulty.ELITE, BotCombatStyle.RUSHER);
            for (int tick = 0; tick < 30; tick++) {
                controller.observe(c, target, Match.STEP);
                assertFalse(controller.steer(c, world, wish, 7.7f, false, Match.STEP));
                assertEquals("steering cannot synthesize gravity/jump impulses", -1.6f, c.velocity.y, .0001f);
                assertFalse(c.airJumpUsed);
            }
            assertTrue("actual projected air acceleration changes the horizontal path", c.velocity.x > 1f);
        } finally { world.release(); }
    }
}
