package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.math.Vec3;
import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.voxel.VoxelWorld;
import org.junit.Test;
import static org.junit.Assert.*;

public class PhysicsTest {
    private VoxelWorld floor() {
        VoxelWorld world = new VoxelWorld(8, 8, 8);
        for (int z = 0; z < 8; z++) for (int x = 0; x < 8; x++) world.set(x, 0, z, Materials.CONCRETE);
        world.commit(); return world;
    }

    @Test public void landingContactsFloorAndRemainsGroundedThroughoutBhopWindow() {
        VoxelWorld world = floor();
        try {
            Vec3 position = new Vec3(3.5f, 1.05f, 3.5f), velocity = new Vec3(0f, -6f, 0f);
            assertTrue(Physics.move(world, position, velocity, Match.STEP, .34f, 1.25f, true));
            assertEquals("landing must not leave a gap above the floor", 1f, position.y, .0001f);
            for (int i = 0; i < 30; i++) {
                velocity.y += Physics.GRAVITY * Match.STEP;
                assertTrue("grounded must not flicker after landing tick " + i,
                        Physics.move(world, position, velocity, Match.STEP, .34f, 1.25f, true));
                assertEquals(1f, position.y, .0001f);
            }
        } finally { world.release(); }
    }

    @Test public void upwardContactStopsBelowCeilingWithoutPenetration() {
        VoxelWorld world = floor();
        try {
            for (int z = 0; z < 8; z++) for (int x = 0; x < 8; x++) world.set(x, 3, z, Materials.CONCRETE);
            world.commit();
            Vec3 position = new Vec3(3.5f, 1f, 3.5f), velocity = new Vec3(0f, 18f, 0f);
            assertFalse(Physics.move(world, position, velocity, Match.STEP, .34f, 1.8f, false));
            assertTrue("move as far as the ceiling, rather than undoing the full tick", position.y > 1.1f);
            assertTrue(position.y + 1.8f < 3f);
            assertEquals(0f, velocity.y, 0f);
            assertFalse(world.boxCollides(position.x, position.y, position.z, .34f, 1.8f));
        } finally { world.release(); }
    }

    @Test public void wholeVoxelStairsCanBeWalkedWithoutJumpingOrHovering() {
        VoxelWorld world = floor();
        try {
            for (int x = 3; x <= 5; x++) for (int y = 1; y <= x - 2; y++)
                for (int z = 2; z <= 4; z++) world.set(x, y, z, Materials.STEEL);
            world.commit();
            Vec3 position = new Vec3(2.2f, 1f, 3.5f), velocity = new Vec3();
            for (int tick = 0; tick < 160 && position.x < 5.5f; tick++) {
                velocity.x = 3f;
                velocity.y += Physics.GRAVITY / 120f;
                Physics.move(world, position, velocity, 1f / 120f, .34f, 1.8f, true);
                assertFalse(world.boxCollides(position.x, position.y, position.z, .34f, 1.8f));
            }
            assertTrue("Every riser must be climbed at normal walk speed", position.x >= 5.5f);
            assertEquals("Feet must settle exactly on the last tread", 4f, position.y, .0001f);
        } finally { world.release(); }
    }

    @Test public void stepUpCannotClimbTwoVoxelWallOrSqueezeThroughCeiling() {
        VoxelWorld world = floor();
        try {
            for (int z = 0; z < 8; z++) {
                world.set(4, 1, z, Materials.STEEL);
                world.set(4, 2, z, Materials.STEEL);
            }
            world.commit();
            Vec3 position = new Vec3(3.6f, 1f, 3.5f), velocity = new Vec3(3f, -.2f, 0f);
            Physics.move(world, position, velocity, .1f, .34f, 1.8f, true);
            assertTrue(position.x < 3.67f);
            assertEquals(1f, position.y, .0001f);
            for (int z = 0; z < 8; z++) {
                world.set(4, 2, z, Materials.AIR);
                world.set(4, 3, z, Materials.STEEL);
            }
            world.commit();
            velocity.set(3f, -.2f, 0f);
            Physics.move(world, position, velocity, .1f, .34f, 1.8f, true);
            assertTrue("A low ceiling must refuse the step", position.x < 3.67f);
            assertEquals(1f, position.y, .0001f);
        } finally { world.release(); }
    }

    @Test public void airborneBodyDoesNotAutoStepUpAnotherVoxel() {
        VoxelWorld world = floor();
        try {
            for (int z = 0; z < 8; z++) world.set(4, 1, z, Materials.STEEL);
            world.commit();
            Vec3 position = new Vec3(3.6f, 1.3f, 3.5f), velocity = new Vec3(3f, 2f, 0f);
            Physics.move(world, position, velocity, .1f, .34f, 1.8f, true);
            assertTrue(position.x < 3.67f);
            assertTrue("Jump arc must not gain a synthetic step", position.y < 1.6f);
        } finally { world.release(); }
    }
}
