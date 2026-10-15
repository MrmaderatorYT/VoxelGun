package com.ccs.voxelgun.game;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.voxel.VoxelWorld;

import org.junit.Test;

/** Pins contextual auto-jump to climbable ledges rather than turning it into bunny hopping. */
public class AutoJumpTest {
    private static VoxelWorld flatWorld() {
        VoxelWorld world = new VoxelWorld(12, 8, 12);
        for (int z = 0; z < 12; z++) {
            for (int x = 0; x < 12; x++) {
                world.set(x, 0, z, Materials.CONCRETE);
            }
        }
        world.commit();
        return world;
    }

    @Test
    public void onlyAClimbableLedgeRequestsAnAutomaticJump() {
        VoxelWorld world = flatWorld();
        Combatant player = new Combatant(0, "Player");
        player.position.set(3.2f, 1f, 5.5f);

        assertFalse("flat ground must not cause repeated jumping",
                Match.shouldAutoJump(world, player, 5f, 0f));

        world.set(4, 1, 5, Materials.CRATE);
        world.commit();
        assertTrue("a one-block ledge should be climbed",
                Match.shouldAutoJump(world, player, 5f, 0f));

        world.set(4, 2, 5, Materials.CRATE);
        world.commit();
        assertFalse("a two-block wall is not a valid auto-jump",
                Match.shouldAutoJump(world, player, 5f, 0f));

        world.release();
    }
}
