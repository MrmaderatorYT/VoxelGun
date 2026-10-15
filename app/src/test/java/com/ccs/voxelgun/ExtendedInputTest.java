package com.ccs.voxelgun;
import com.ccs.voxelgun.input.InputState;
import com.ccs.voxelgun.game.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ExtendedInputTest {
    @Test public void courseRestartIsConsumedOnceAndPauseReleaseDropsStaleRestart() {
        InputState input = new InputState();
        input.queueCourseRestart();
        assertTrue(input.consumeCourseRestart()); assertFalse(input.consumeCourseRestart());
        input.queueCourseRestart(); input.releaseAll();
        assertFalse(input.consumeCourseRestart());
    }
    @Test public void restartMailboxResetsRunAndMomentumWithoutRecreatingWorld() {
        MatchConfig config = new MatchConfig(); config.mode = GameMode.MOVEMENT_TRAINING;
        config.botCount = 0; config.courseBestMillis = 4567;
        Match match = new Match(config);
        try {
            InputState input = new InputState(); Combatant p = match.player();
            for (int i = 0; i < 210; i++) match.update(Match.STEP, input);
            assertTrue(match.combatActive());
            input.crouching = true;
            p.position.set(70, 6, 50); p.velocity.set(24, -15, 9);
            input.queueCourseRestart(); match.update(Match.STEP, input);
            assertEquals(8.5f, p.position.x, .001f); assertEquals(32.5f, p.position.z, .001f);
            assertEquals(0, p.velocity.x, .001f); assertEquals(0, p.velocity.z, .001f);
            assertEquals(4567, match.movementTrial().bestMillis());
            assertFalse(match.movementTrial().running()); assertEquals(0, match.movementTrial().nextGate());
            assertFalse("fresh retry must not reuse the old crouch hold", p.crouching);
        } finally { match.release(); }
    }
}
