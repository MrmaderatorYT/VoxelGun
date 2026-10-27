package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.map.CourseLayout;
import org.junit.Test;
import static org.junit.Assert.*;

public class MovementRetryTest {
    private Combatant cross(MovementTrial trial, int gate, boolean airborne, boolean sliding) {
        Combatant player = new Combatant(0, "runner");
        player.position.set(CourseLayout.x(gate) + .2f, airborne ? 3.5f : 3,
                CourseLayout.z(gate));
        player.grounded = !airborne;
        trial.update(.1f, CourseLayout.x(gate) - .2f, player.position.y,
                player.position.z, player, sliding);
        return player;
    }

    @Test public void missedMovementGateOffersRetryWithoutGrantingCheckpoint() {
        MovementTrial trial = new MovementTrial(12345);
        cross(trial, 0, false, false); cross(trial, 1, false, false);
        cross(trial, CourseLayout.JUMP_GATE, false, false);
        assertTrue(trial.retryRecommended()); assertEquals(1, trial.mistakes());
        assertEquals(CourseLayout.JUMP_GATE, trial.nextGate());
        cross(trial, CourseLayout.JUMP_GATE, true, false);
        assertFalse(trial.retryRecommended());
    }

    @Test public void instantRestartRetainsBestAndClearsVelocityAndStance() {
        MovementTrial trial = new MovementTrial(12345);
        Combatant player = cross(trial, 0, false, false);
        player.velocity.set(20, -12, 8); player.crouchBlend = 1;
        player.flash = 3; player.alive = false;
        trial.restart(player);
        assertEquals(8.5f, player.position.x, 0f); assertEquals(3, player.position.y, 0f);
        assertEquals(32.5f, player.position.z, 0f); assertEquals(0, player.velocity.lengthSquared(), 0f);
        assertEquals(0, player.crouchBlend, 0f); assertEquals(0, player.flash, 0f);
        assertTrue(player.alive); assertTrue(player.grounded);
        assertEquals(12345, trial.bestMillis()); assertFalse(trial.running());
    }

    @Test public void completeRunPublishesReplayAtExactCheckpointPlanes() {
        MovementTrial trial = new MovementTrial(0);
        for (int i = 0; i < CourseLayout.count(); i++)
            cross(trial, i, i == CourseLayout.JUMP_GATE, i == CourseLayout.SLIDE_GATE);
        assertTrue(trial.finished()); assertTrue(trial.ghost().hasBest());
        GhostPose pose = new GhostPose();
        trial.ghost().sample(0, pose);
        assertEquals(CourseLayout.x(0), pose.x, 0f);
        trial.ghost().sample(trial.millis(), pose);
        assertEquals(CourseLayout.x(CourseLayout.count() - 1), pose.x, 0f);
        trial.reset(); assertTrue(trial.ghost().hasBest());
    }
}
