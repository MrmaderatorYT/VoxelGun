package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.voxel.VoxelWorld;
import org.junit.Test;
import static org.junit.Assert.*;

public class BotMovementSafetyTest {
    private VoxelWorld floor() {
        VoxelWorld world = new VoxelWorld(32, 12, 20);
        for (int z = 0; z < world.sizeZ(); z++) {
            for (int x = 0; x < world.sizeX(); x++) world.set(x, 0, z, Materials.CONCRETE);
        }
        world.commit(); return world;
    }

    private Combatant bot() {
        Combatant c = new Combatant(2, "Scout");
        c.position.set(12.5f, 1f, 8.5f); c.grounded = true; return c;
    }

    @Test public void openFloorAllowsHopsAndKeepsTakeoffDecisionDuringArc() {
        VoxelWorld world = floor();
        try {
            Combatant c = bot(); BotMovementSafety safety = new BotMovementSafety();
            assertTrue(safety.canHop(c, world, 5.2f, 0f, Match.STEP));
            c.grounded = false; c.position.y = 2.15f;
            assertTrue(safety.canHop(c, world, 5.2f, 0f, .2f));
            assertTrue(BotMovementSafety.canDodge(c, world, 0f, 1f, true));
        } finally { world.release(); }
    }

    @Test public void bodyCanWalkOneBlockPassageButBhopWillNotStart() {
        VoxelWorld world = floor();
        try {
            for (int x = 0; x < world.sizeX(); x++) for (int y = 1; y <= 5; y++) {
                world.set(x, y, 7, Materials.CONCRETE);
                world.set(x, y, 9, Materials.CONCRETE);
            }
            world.commit(); Combatant c = bot();
            assertFalse(world.boxCollides(c.position.x, c.position.y, c.position.z,
                    Combatant.RADIUS, Combatant.HEIGHT));
            assertFalse(new BotMovementSafety().canHop(c, world, 5.2f, 0f, Match.STEP));
            assertFalse("lateral evasion may not aim directly at the passage wall",
                    BotMovementSafety.canDodge(c, world, 0f, 1f, false));
        } finally { world.release(); }
    }

    @Test public void missingLeadFloorAndLowCeilingBothStopHopRequest() {
        VoxelWorld world = floor();
        try {
            Combatant c = bot();
            for (int z = 0; z < world.sizeZ(); z++) world.set(14, 0, z, Materials.AIR);
            world.commit();
            assertFalse(new BotMovementSafety().canHop(c, world, 5.2f, 0f, Match.STEP));
            world.set(14, 0, 8, Materials.CONCRETE);
            for (int x = 0; x < world.sizeX(); x++) for (int z = 0; z < world.sizeZ(); z++) {
                world.set(x, 3, z, Materials.CONCRETE);
            }
            world.commit();
            assertFalse(new BotMovementSafety().canHop(c, world, 5.2f, 0f, Match.STEP));
        } finally { world.release(); }
    }

    @Test public void turnRefreshesProbeEvenBeforeIntervalExpires() {
        VoxelWorld world = floor();
        try {
            Combatant c = bot();
            for (int x = 11; x <= 13; x++) for (int y = 1; y < 5; y++) {
                world.set(x, y, 10, Materials.CONCRETE);
            }
            world.commit(); BotMovementSafety safety = new BotMovementSafety();
            assertTrue(safety.canHop(c, world, 5.2f, 0f, Match.STEP));
            assertFalse(safety.canHop(c, world, 0f, 5.2f, Match.STEP));
        } finally { world.release(); }
    }
}
