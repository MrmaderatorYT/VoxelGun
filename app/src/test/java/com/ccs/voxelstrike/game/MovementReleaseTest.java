package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.input.InputState;
import com.ccs.voxelstrike.map.MapBuilder;
import com.ccs.voxelstrike.voxel.Materials;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real input and collision regression for normal walking over a one-voxel descent. */
public class MovementReleaseTest {
    private Match walkingMatch() {
        MatchConfig config = new MatchConfig();
        config.mode = GameMode.RANGE;
        config.botCount = 0;
        config.modifiers = 0;
        config.seed = 4242L;
        Match match = new Match(config);
        InputState idle = new InputState();
        for (int i = 0; i < 210; i++) match.update(Match.STEP, idle);

        MapBuilder b = new MapBuilder(match.world(), 1L);
        b.fill(40, 0, 40, 80, 2, 60, Materials.CONCRETE);
        b.fill(40, 3, 40, 50, 3, 60, Materials.CONCRETE);
        b.clear(51, 3, 40, 80, 3, 60);
        b.clear(40, 4, 40, 80, 20, 60);
        match.world().commit();
        return match;
    }

    private void placeWalker(Match match, float x) {
        Combatant player = match.player();
        player.position.set(x, 4f, 50.5f);
        player.velocity.set(0f, 0f, 0f);
        player.grounded = true;
        player.yaw = 0f;
    }

    @Test public void releasingOrdinaryWalkingStopsOnTheSameGroundedTick() {
        Match match = walkingMatch();
        try {
            placeWalker(match, 45.5f);
            InputState input = new InputState();
            input.moveX = 1f;
            for (int i = 0; i < 8; i++) match.update(Match.STEP, input);
            Combatant player = match.player();
            assertTrue(player.grounded);
            assertTrue(player.velocity.x > 0f);
            float x = player.position.x, z = player.position.z;

            input.releaseAll();
            match.update(Match.STEP, input);
            assertEquals(x, player.position.x, .0001f);
            assertEquals(z, player.position.z, .0001f);
            assertEquals(0f, player.velocity.x, .0001f);
            assertEquals(0f, player.velocity.z, .0001f);
            assertTrue(player.grounded);
        } finally { match.release(); }
    }

    @Test public void releasingAtOneVoxelDescentStopsHorizontalMotionWhileFalling() {
        Match match = walkingMatch();
        try {
            placeWalker(match, 49.9f);
            InputState input = new InputState();
            input.moveX = 1f;
            Combatant player = match.player();
            for (int i = 0; i < 90 && player.grounded; i++) {
                match.update(Match.STEP, input);
            }
            assertFalse("walking off the upper floor must begin a real descent", player.grounded);
            assertTrue(player.velocity.x > 0f);
            assertTrue(player.velocity.y < 0f);
            float x = player.position.x, y = player.position.y, z = player.position.z;

            input.releaseAll();
            match.update(Match.STEP, input);
            assertEquals("a tiny descent must not retain the released walking velocity",
                    0f, player.velocity.x, .0001f);
            assertEquals(0f, player.velocity.z, .0001f);
            assertEquals(x, player.position.x, .0001f);
            assertEquals(z, player.position.z, .0001f);
            assertTrue("stopping horizontally must not cancel gravity", player.position.y < y);

            for (int i = 0; i < 40 && !player.grounded; i++) {
                match.update(Match.STEP, input);
                assertEquals(x, player.position.x, .0001f);
                assertEquals(z, player.position.z, .0001f);
            }
            assertTrue("the player must still land on the lower floor", player.grounded);
            assertEquals(3f, player.position.y, .0001f);
            assertTrue(player.alive);
        } finally { match.release(); }
    }
}
