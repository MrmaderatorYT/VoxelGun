package com.ccs.voxelgun.game;

import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.voxel.VoxelWorld;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/** Separation is a bounded movement intent, never a teleport or an extra jump impulse. */
public class BotCrowdAvoidanceTest {
    @Test public void exactOverlapsChooseOppositeDeterministicDirections() {
        VoxelWorld world = floor();
        try {
            Combatant a = actor(2, 8.5f, 3f, 8.5f);
            Combatant b = actor(4, 8.5f, 3f, 8.5f);
            List<Combatant> actors = Arrays.asList(a, b);
            BotCrowdAvoidance.separate(a, actors, world, Match.STEP);
            BotCrowdAvoidance.separate(b, actors, world, Match.STEP);
            assertTrue(planarSpeed(a) > .01f);
            assertTrue(planarSpeed(b) > .01f);
            assertEquals(-a.velocity.x, b.velocity.x, .00001f);
            assertEquals(-a.velocity.z, b.velocity.z, .00001f);
            assertEquals("avoidance must not teleport a bot", 8.5f, a.position.x, 0f);
            assertEquals(8.5f, b.position.z, 0f);

            Combatant repeated = actor(2, 8.5f, 3f, 8.5f);
            Combatant repeatedMate = actor(4, 8.5f, 3f, 8.5f);
            BotCrowdAvoidance.separate(repeated, Arrays.asList(repeatedMate, repeated),
                    world, Match.STEP);
            assertEquals("list order must not choose a new random escape direction",
                    a.velocity.x, repeated.velocity.x, .00001f);
            assertEquals(a.velocity.z, repeated.velocity.z, .00001f);
        } finally { world.release(); }
    }

    @Test public void crowdCorrectionIsCappedAndLeavesVerticalVelocityUntouched() {
        VoxelWorld world = floor();
        try {
            Combatant self = actor(2, 8.5f, 3f, 8.5f);
            self.velocity.set(4f, 7.4f, -3f);
            List<Combatant> actors = new ArrayList<>();
            actors.add(self);
            for (int id = 3; id < 30; id++) actors.add(actor(id, 8.45f, 3f, 8.45f));
            BotCrowdAvoidance.separate(self, actors, world, Match.STEP);
            float correction = (float) Math.hypot(self.velocity.x - 4f, self.velocity.z + 3f);
            assertTrue("many neighbors must not stack unlimited acceleration: " + correction,
                    correction > 0f && correction <= 1.8001f);
            assertEquals(7.4f, self.velocity.y, 0f);
        } finally { world.release(); }
    }

    @Test public void corpsesAndDifferentFloorsDoNotPushTheBot() {
        VoxelWorld world = floor();
        try {
            Combatant self = actor(2, 8.5f, 3f, 8.5f);
            self.velocity.set(3f, 2f, -4f);
            Combatant corpse = actor(4, 8.5f, 3f, 8.5f);
            corpse.alive = false;
            Combatant upstairs = actor(6, 8.5f, 6f, 8.5f);
            Combatant far = actor(8, 11.5f, 3f, 8.5f);
            BotCrowdAvoidance.separate(self, Arrays.asList(self, corpse, upstairs, far),
                    world, Match.STEP);
            assertEquals(3f, self.velocity.x, 0f);
            assertEquals(2f, self.velocity.y, 0f);
            assertEquals(-4f, self.velocity.z, 0f);
        } finally { world.release(); }
    }

    @Test public void deadSelfIsNeverMovedByCrowdCorrection() {
        VoxelWorld world = floor();
        try {
            Combatant self = actor(2, 8.5f, 3f, 8.5f);
            self.alive = false;
            self.velocity.set(3f, 2f, -4f);
            BotCrowdAvoidance.separate(self,
                    Arrays.asList(self, actor(4, 8.5f, 3f, 8.5f)), world, Match.STEP);
            assertEquals(3f, self.velocity.x, 0f);
            assertEquals(2f, self.velocity.y, 0f);
            assertEquals(-4f, self.velocity.z, 0f);
        } finally { world.release(); }
    }

    @Test public void enemyBodiesAreAvoidedJustLikeFriendlyBodies() {
        VoxelWorld world = floor();
        try {
            Combatant self = actor(2, 8.5f, 3f, 8.5f);
            Combatant opponent = actor(3, 8.3f, 3f, 8.5f);
            opponent.team = Team.RED;
            BotCrowdAvoidance.separate(self, Arrays.asList(self, opponent), world, Match.STEP);
            assertTrue("overlapping enemies are physical neighbors too", self.velocity.x > 0f);
        } finally { world.release(); }
    }

    @Test public void crowdPressureCannotPushAStationaryBodyIntoAVoxelWall() {
        VoxelWorld world = floor();
        try {
            for (int z = 0; z < 16; z++) for (int y = 3; y <= 5; y++) {
                world.set(9, y, z, Materials.CONCRETE);
            }
            world.commit();
            float safeX = 9f - Combatant.RADIUS - .001f;
            Combatant self = actor(2, safeX, 3f, 8.5f);
            Combatant crowd = actor(4, safeX - .15f, 3f, 8.5f);
            assertFalse(world.boxCollides(self.position.x, self.position.y, self.position.z,
                    Combatant.RADIUS, Combatant.HEIGHT));
            BotCrowdAvoidance.separate(self, Arrays.asList(self, crowd), world, Match.STEP);
            assertFalse("avoidance intent must pass the next-step body clearance test",
                    world.boxCollides(self.position.x + self.velocity.x * Match.STEP,
                            self.position.y, self.position.z + self.velocity.z * Match.STEP,
                            Combatant.RADIUS, Combatant.HEIGHT));
            assertEquals(safeX, self.position.x, 0f);
        } finally { world.release(); }
    }

    private static float planarSpeed(Combatant c) {
        return (float) Math.hypot(c.velocity.x, c.velocity.z);
    }

    private static Combatant actor(int id, float x, float y, float z) {
        Combatant c = new Combatant(id, "actor-" + id);
        c.bot = true;
        c.team = Team.BLUE;
        c.position.set(x, y, z);
        c.grounded = true;
        return c;
    }

    private static VoxelWorld floor() {
        VoxelWorld world = new VoxelWorld(16, 8, 16);
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 3; y++) world.set(x, y, z, Materials.CONCRETE);
        }
        world.commit();
        return world;
    }
}
