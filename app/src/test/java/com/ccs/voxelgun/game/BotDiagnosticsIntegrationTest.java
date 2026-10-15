package com.ccs.voxelgun.game;

import com.ccs.voxelgun.input.InputState;
import org.junit.Test;

import static org.junit.Assert.*;

public class BotDiagnosticsIntegrationTest {
    @Test public void enablingTelemetryDoesNotChangeSeededCombat() {
        MatchConfig config = new MatchConfig();
        config.mode = GameMode.TEAM_DEATHMATCH;
        config.botCount = 5;
        config.seed = 20261014L;
        Match control = new Match(config);
        Match observed = new Match(config);
        try {
            observed.enableBotDiagnostics();
            InputState input = new InputState();
            for (int i = 0; i < 1200; i++) {
                control.update(Match.STEP, input);
                observed.update(Match.STEP, input);
            }
            for (int i = 0; i < control.combatants().size(); i++) {
                Combatant expected = control.combatants().get(i);
                Combatant actual = observed.combatants().get(i);
                assertEquals(expected.position.x, actual.position.x, 0f);
                assertEquals(expected.position.y, actual.position.y, 0f);
                assertEquals(expected.position.z, actual.position.z, 0f);
                assertEquals(expected.yaw, actual.yaw, 0f);
                assertEquals(expected.kills, actual.kills);
                assertEquals(expected.deaths, actual.deaths);
                assertEquals(expected.health, actual.health, 0f);
            }
            BotDiagnostics.Snapshot metrics = observed.botDiagnostics().snapshot();
            assertTrue(metrics.activeSeconds > 20f);
            assertTrue(metrics.movingSeconds > 5f);
            assertTrue(metrics.distance > 20f);
            assertEquals(0L, metrics.invalidSamples);
        } finally {
            control.release();
            observed.release();
        }
    }

    @Test public void realRangeMatchRecordsTargetsAsIntentionalHolds() {
        MatchConfig config = new MatchConfig();
        config.mode = GameMode.RANGE;
        config.botCount = 3;
        Match match = new Match(config);
        try {
            assertNull("normal matches must have no diagnostics cost", match.botDiagnostics());
            match.enableBotDiagnostics();
            InputState input = new InputState();
            for (int i = 0; i < 900; i++) match.update(Match.STEP, input);
            BotDiagnostics.Snapshot result = match.botDiagnostics().snapshot();
            assertEquals(3, result.bots.length);
            assertTrue(result.holdSeconds > 30f);
            assertEquals(0f, result.stalledSeconds, 0f);
            assertEquals(0f, result.spinningSeconds, 0f);
            assertTrue(result.idleSeconds < .2f);
        } finally { match.release(); }
    }
}
