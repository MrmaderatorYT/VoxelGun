package com.ccs.voxelgun.game;

import com.ccs.voxelgun.map.CourseLayout;
import org.junit.Test;
import static org.junit.Assert.*;

public class MovementTrialTest {
    private void cross(MovementTrial trial, int gate, boolean jump, boolean slide) {
        Combatant p = new Combatant(0, "test");
        p.position.set(CourseLayout.x(gate) + 0.2f, jump ? 3.5f : 3f, CourseLayout.z(gate));
        p.grounded = !jump;
        trial.update(0.1f, CourseLayout.x(gate) - 0.2f, p.position.y, p.position.z, p, slide);
    }
    private void finish(MovementTrial trial) {
        for (int i = 0; i < CourseLayout.count(); i++) {
            cross(trial, i, i == CourseLayout.JUMP_GATE, i == CourseLayout.SLIDE_GATE);
        }
    }

    @Test public void gatesMustBeOrderedAndRequireActualJumpAndSlide() {
        MovementTrial trial = new MovementTrial(0);
        cross(trial, 1, false, false);
        assertEquals(0, trial.nextGate());
        cross(trial, 0, false, false);
        assertTrue(trial.running());
        cross(trial, 1, false, false);
        cross(trial, 2, false, false);
        assertEquals(2, trial.nextGate());
        cross(trial, 2, true, false);
        cross(trial, 3, false, false);
        cross(trial, 4, false, false);
        assertEquals(4, trial.nextGate());
        cross(trial, 4, false, true);
        cross(trial, 5, false, false);
        cross(trial, 6, false, false);
        assertTrue(trial.finished());
        assertFalse(trial.running());
        assertEquals(trial.millis(), trial.bestMillis());
        assertTrue(trial.millis() > 0);
    }

    @Test public void resetRetainsBestAndSlowerRunCannotOverwriteIt() {
        MovementTrial trial = new MovementTrial(100);
        finish(trial);
        assertTrue(trial.finished());
        assertEquals(100, trial.bestMillis());
        trial.reset();
        assertEquals(0, trial.millis());
        assertEquals(0, trial.nextGate());
        assertEquals(100, trial.bestMillis());
    }

    @Test public void teleportOrDeathInvalidatesRun() {
        MovementTrial trial = new MovementTrial(5000);
        cross(trial, 0, false, false);
        Combatant p = new Combatant(0, "test");
        p.position.set(114f, 3f, 32f);
        trial.update(1f / 60f, 14.2f, 3f, 32f, p, true);
        assertFalse(trial.running());
        assertFalse(trial.finished());
        assertEquals(0, trial.nextGate());
        cross(trial, 0, false, false);
        p.alive = false;
        trial.update(0.1f, p.position.x, p.position.y, p.position.z, p, false);
        assertFalse(trial.running());
        assertEquals(5000, trial.bestMillis());
    }

    @Test public void gatesRejectGoingAroundAboveOrBackward() {
        MovementTrial trial = new MovementTrial(0);
        Combatant p = new Combatant(0, "test");
        p.position.set(14.2f, 3f, 39f);
        trial.update(0.1f, 13.8f, 3f, 39f, p, false);
        assertEquals(0, trial.nextGate());
        p.position.set(14.2f, 9f, 32f);
        trial.update(0.1f, 13.8f, 9f, 32f, p, false);
        assertEquals(0, trial.nextGate());
        p.position.set(13.8f, 3f, 32f);
        trial.update(0.1f, 14.2f, 3f, 32f, p, false);
        assertEquals(0, trial.nextGate());
    }

    @Test public void returningToStartPadAllowsRetry() {
        MovementTrial trial = new MovementTrial(0);
        finish(trial);
        int best = trial.bestMillis();
        Combatant p = new Combatant(0, "test");
        p.position.set(9f, 3f, 32f);
        trial.update(0.1f, 9.2f, 3f, 32f, p, false);
        assertFalse(trial.finished());
        assertEquals(0, trial.nextGate());
        assertEquals(best, trial.bestMillis());
    }
}
