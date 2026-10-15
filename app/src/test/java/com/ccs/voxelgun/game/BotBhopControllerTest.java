package com.ccs.voxelgun.game;

import com.ccs.voxelgun.math.MathUtil;
import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.voxel.VoxelWorld;
import org.junit.Test;
import static org.junit.Assert.*;

/** Synthetic bot inputs must earn their rewards through real voxel floor contacts. */
public class BotBhopControllerTest {
    private static final float DT = Match.STEP;
    private static final float REFERENCE = 7.7f;
    private static final float WISH = 5.2f;

    private VoxelWorld floor() {
        VoxelWorld world = new VoxelWorld(512, 12, 16);
        for (int z = 0; z < world.sizeZ(); z++) {
            for (int x = 0; x < world.sizeX(); x++) world.set(x, 0, z, Materials.CONCRETE);
        }
        world.commit();
        return world;
    }

    private Combatant bot(int id) {
        Combatant c = new Combatant(id, "Bhop test");
        c.bot = true;
        c.position.set(12.5f, 1f, 8.5f);
        c.grounded = true;
        return c;
    }

    private float speed(Combatant c) {
        return (float) Math.hypot(c.velocity.x, c.velocity.z);
    }

    /** Same gravity, stance interpolation and collision calls as Match.integrateBody. */
    private void physics(Combatant c, VoxelWorld world) {
        c.crouchBlend = MathUtil.clamp(
                MathUtil.damp(c.crouchBlend, c.crouching ? 1f : 0f, 13f, DT), 0f, 1f);
        c.velocity.y = Math.max(Physics.TERMINAL_VELOCITY, c.velocity.y + Physics.GRAVITY * DT);
        c.grounded = Physics.move(world, c.position, c.velocity, DT,
                Combatant.RADIUS, c.height(), true);
        if (c.grounded) c.airJumpUsed = false;
    }

    private boolean step(BotBhopController controller, Combatant c, VoxelWorld world,
                         float wishX, float wishZ, boolean travel) {
        boolean grounded = c.grounded;
        float previousY = c.velocity.y;
        controller.update(c, world, wishX, wishZ, REFERENCE, travel, DT);
        boolean takeoff = grounded && !c.grounded && c.velocity.y > 0f;
        if (!grounded) {
            assertEquals("controller cannot jump again or alter gravity while airborne",
                    previousY, c.velocity.y, .0001f);
        }
        physics(c, world);
        return takeoff;
    }

    @Test public void threeTimedLandingsBuildSpeedAtEverySkillLevel() {
        for (int level = 0; level < 3; level++) {
            VoxelWorld world = floor();
            try {
                Combatant c = bot(level);
                BotBhopController controller = new BotBhopController(level * .5f, level);
                int takeoffs = 0, groundedTicks = 0;
                float previousTakeoffSpeed = 0f;
                boolean crouchedInAir = false;
                for (int i = 0; i < 400 && takeoffs < 4; i++) {
                    boolean onFloor = c.grounded;
                    float before = speed(c);
                    if (step(controller, c, world, WISH, 0f, true)) {
                        float after = speed(c);
                        if (takeoffs > 0) {
                            assertTrue("fresh airborne crouch must precede the next hop", crouchedInAir);
                            assertTrue("skill " + level + " hop " + takeoffs + ": "
                                    + previousTakeoffSpeed + " -> " + after,
                                    after > previousTakeoffSpeed + .25f);
                            assertTrue("ground reaction remains inside the real hop window",
                                    groundedTicks * DT < ArenaMovement.HOP_WINDOW_SECONDS);
                        }
                        takeoffs++;
                        previousTakeoffSpeed = after;
                        groundedTicks = 0;
                        crouchedInAir = false;
                    } else {
                        if (onFloor) {
                            groundedTicks++;
                            assertTrue("landing/crouching alone must not award speed",
                                    speed(c) <= before + .0001f);
                        }
                        if (!c.grounded && c.crouching) crouchedInAir = true;
                    }
                    assertTrue("the bot obeys the player's bhop cap",
                            speed(c) <= REFERENCE * ArenaMovement.BHOP_MAX_SPEED_FACTOR + .001f);
                }
                assertEquals("initial jump plus three successful timed hops", 4, takeoffs);
            } finally { world.release(); }
        }
    }

    @Test public void prolongedChainReachesButNeverExceedsSharedSpeedCap() {
        VoxelWorld world = floor();
        try {
            Combatant c = bot(2);
            BotBhopController controller = new BotBhopController(1f, c.id);
            int takeoffs = 0;
            for (int i = 0; i < 1000 && takeoffs < 14; i++) {
                if (step(controller, c, world, WISH, 0f, true)) takeoffs++;
                assertTrue(speed(c) <= REFERENCE * ArenaMovement.BHOP_MAX_SPEED_FACTOR + .001f);
            }
            assertEquals(14, takeoffs);
            assertEquals(REFERENCE * ArenaMovement.BHOP_MAX_SPEED_FACTOR, speed(c), .03f);
        } finally { world.release(); }
    }

    @Test public void finishingTravelCancelsChainingAndDecaysToRest() {
        VoxelWorld world = floor();
        try {
            Combatant c = bot(1);
            BotBhopController controller = new BotBhopController(.6f, c.id);
            int takeoffs = 0;
            for (int i = 0; i < 300 && takeoffs < 3; i++) {
                if (step(controller, c, world, WISH, 0f, true)) takeoffs++;
            }
            assertEquals(3, takeoffs);
            assertTrue(speed(c) > WISH + .5f);
            for (int i = 0; i < 180; i++) {
                assertFalse("holding an objective is not another bhop request",
                        step(controller, c, world, 0f, 0f, false));
            }
            assertTrue(c.grounded);
            assertEquals(0f, speed(c), .001f);
        } finally { world.release(); }
    }

    @Test public void lowCeilingPreventsSyntheticJump() {
        VoxelWorld world = floor();
        try {
            for (int x = 0; x < world.sizeX(); x++) {
                for (int z = 0; z < world.sizeZ(); z++) world.set(x, 3, z, Materials.CONCRETE);
            }
            world.commit();
            Combatant c = bot(0);
            BotBhopController controller = new BotBhopController(1f, c.id);
            assertFalse("standing body fits, but a full jump does not",
                    world.boxCollides(c.position.x, c.position.y, c.position.z,
                            Combatant.RADIUS, Combatant.HEIGHT));
            for (int i = 0; i < 180; i++) {
                assertFalse(step(controller, c, world, WISH, 0f, true));
                assertTrue(c.grounded);
                assertEquals(1f, c.position.y, .001f);
            }
        } finally { world.release(); }
    }

    @Test public void airborneTravelNeverRequestsAnExtraJump() {
        VoxelWorld world = floor();
        try {
            Combatant c = bot(0);
            c.position.y = 8f;
            c.grounded = false;
            c.velocity.set(WISH, 1f, 0f);
            BotBhopController controller = new BotBhopController(1f, c.id);
            for (int i = 0; i < 24; i++) {
                assertFalse(step(controller, c, world, WISH, 0f, true));
                assertFalse("there has not been a voxel landing yet", c.grounded);
                assertFalse("bhop cannot consume an air-jump budget", c.airJumpUsed);
            }
            assertTrue(c.crouching);
            assertTrue(c.velocity.y < 0f);
        } finally { world.release(); }
    }

    @Test public void aimingDisablesAutomaticBhop() {
        VoxelWorld world = floor();
        try {
            Combatant c = bot(0);
            c.aiming = true;
            BotBhopController controller = new BotBhopController(1f, c.id);
            for (int i = 0; i < 90; i++) {
                assertFalse(step(controller, c, world, WISH, 0f, true));
                assertTrue(c.grounded);
            }
        } finally { world.release(); }
    }

    @Test public void narrowCorridorRemainsWalkableWithoutStartingHopChain() {
        VoxelWorld world = floor();
        try {
            for (int x = 0; x < world.sizeX(); x++) for (int y = 1; y < 6; y++) {
                world.set(x, y, 7, Materials.CONCRETE);
                world.set(x, y, 9, Materials.CONCRETE);
            }
            world.commit(); Combatant c = bot(0);
            BotBhopController controller = new BotBhopController(1f, c.id);
            for (int tick = 0; tick < 180; tick++) {
                assertFalse(step(controller, c, world, WISH, 0f, true));
                assertTrue(c.grounded);
            }
            assertTrue("stopping bhop must not stop navigation", c.position.x > 25f);
            assertEquals(WISH, speed(c), .01f);
        } finally { world.release(); }
    }
}
