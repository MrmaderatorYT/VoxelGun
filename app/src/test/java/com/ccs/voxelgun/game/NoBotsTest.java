package com.ccs.voxelgun.game;

import com.ccs.voxelgun.input.InputState;
import com.ccs.voxelgun.map.Maps;

import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Solo exploration keeps the selected map/ruleset but never creates an opposing army. */
public class NoBotsTest {
    @Test public void stableBitSurvivesSanitizingAndConfigCopyInEveryMode() {
        assertEquals(131072, MatchModifier.NO_BOTS.bit);
        int mask = MatchModifier.NO_BOTS.bit | MatchModifier.BUNNY_HOP.bit
                | MatchModifier.NO_FALL_DAMAGE.bit;
        for (GameMode mode : GameMode.values()) {
            assertTrue(mode.label(), MatchModifier.NO_BOTS.supports(mode));
            assertEquals(mode.label(), mask, MatchModifier.sanitize(mask, mode));
            MatchConfig config = config(mode);
            config.modifiers = mask;
            MatchConfig copy = config.copy();
            assertEquals(mode.label(), mask, copy.modifiers);
            assertSame(mode, copy.mode);
            // Enabling solo must not discard the user's normal multiplayer-sized selection.
            assertEquals(7, copy.botCount);
            assertEquals(BattlefieldRules.MAX_PLAYERS, copy.totalPlayers);
        }
    }

    @Test public void everyModeHasExactlyTheHumanAndNoRoundClock() {
        for (GameMode mode : GameMode.values()) {
            Match match = new Match(config(mode));
            try {
                assertTrue(mode.label(), match.soloExploration());
                assertEquals(mode.label(), 1, match.combatants().size());
                assertSame(match.player(), match.combatants().get(0));
                assertFalse(mode.label(), match.player().bot);
                assertTrue(mode.label(), match.player().alive);
                assertEquals(mode.label(), 0f, match.timeLimitSeconds(), 0f);
                assertFalse(mode.label(), match.snapshot().timed);
                assertEquals(mode.label(), 0f, match.snapshot().timeLeft, 0f);
                assertEquals(mode.label(), 0, match.snapshot().enemiesAlive);
                assertEquals(mode.label(), 1, match.snapshot().scoreboardCount);
                if (match.snapshot().objectiveCount > 0) {
                    assertEquals(mode.label(), "SOLO EXPLORATION", match.snapshot().objectiveStatus);
                    assertEquals(mode.label(), 0f, match.snapshot().objectiveSeconds, 0f);
                }
            } finally {
                match.release();
            }
        }
    }

    @Test public void fallingHumanCanRespawnInEveryModeIncludingBombAndSurvival() {
        for (GameMode mode : GameMode.values()) {
            Match match = new Match(config(mode));
            try {
                advance(match, 3.5f);
                assertEquals(mode.label(), Match.State.PLAYING, match.state());
                match.player().position.y = -8f;
                match.update(Match.STEP, new InputState());
                assertFalse(mode.label(), match.player().alive);
                advance(match, 4f);
                assertEquals(mode.label(), Match.State.PLAYING, match.state());
                assertTrue(mode.label(), match.player().alive);
                assertTrue(mode.label(), match.player().health > 0f);
                assertTrue(mode.label(), match.player().position.y > -6f);
                assertEquals(mode.label(), 1, match.combatants().size());
            } finally {
                match.release();
            }
        }
    }

    @Test public void emptyOppositionDoesNotWinBombRoundsOrStartNewZombieWaves() {
        for (GameMode mode : new GameMode[]{GameMode.BOMB_PLANT, GameMode.ZOMBIE}) {
            Match match = new Match(config(mode));
            try {
                advance(match, 94f); // Crosses the 45 s wave timer and several bomb round breaks.
                assertEquals(mode.label(), Match.State.PLAYING, match.state());
                assertFalse(mode.label(), match.snapshot().finished);
                assertEquals(mode.label(), 0, match.snapshot().blueScore);
                assertEquals(mode.label(), 0, match.snapshot().redScore);
                assertEquals(mode.label(), 1, match.snapshot().wave);
                assertEquals(mode.label(), 1, match.combatants().size());
            } finally {
                match.release();
            }
        }
    }

    @Test public void elapsedNormalTimeLimitCannotFinishSoloTimedModes() throws Exception {
        Field clock = Match.class.getDeclaredField("clock");
        clock.setAccessible(true);
        for (GameMode mode : GameMode.values()) {
            if (mode.timeLimitSeconds() <= 0f) continue;
            Match match = new Match(config(mode));
            try {
                advance(match, 3.5f);
                // Fast-forward only elapsed match time, leaving fixed-step physics untouched.
                clock.setFloat(match, Math.max(mode.timeLimitSeconds(), 3600f) + 1f);
                advance(match, .1f);
                assertEquals(mode.label(), Match.State.PLAYING, match.state());
                assertFalse(mode.label(), match.snapshot().finished);
                assertFalse(mode.label(), match.snapshot().timed);
            } finally {
                match.release();
            }
        }
    }

    @Test public void battlefieldConquestExplorationKeepsMarkersWithoutTicketLossOnDeath() {
        MatchConfig config = config(GameMode.BATTLE_FIELD);
        config.conquest = true;
        Match match = new Match(config);
        try {
            advance(match, 3.5f);
            assertEquals(1, match.combatants().size());
            assertTrue(match.snapshot().conquest);
            assertEquals(Conquest.POINTS, match.snapshot().objectiveCount);
            int blueBefore = match.conquest().tickets(Team.BLUE);
            int redBefore = match.conquest().tickets(Team.RED);
            match.player().position.y = -8f;
            match.update(Match.STEP, new InputState());
            assertFalse(match.player().alive);
            advance(match, 4f);
            assertTrue(match.player().alive);
            assertEquals(Match.State.PLAYING, match.state());
            assertEquals(blueBefore, match.conquest().tickets(Team.BLUE));
            assertEquals(redBefore, match.conquest().tickets(Team.RED));
            assertEquals(Conquest.POINTS, match.snapshot().objectiveCount);
            assertFalse(match.snapshot().timed);
        } finally {
            match.release();
        }
    }

    private static MatchConfig config(GameMode mode) {
        MatchConfig config = new MatchConfig();
        config.mode = mode;
        config.modifiers = MatchModifier.NO_BOTS.bit;
        config.botCount = 7;
        config.totalPlayers = BattlefieldRules.MAX_PLAYERS;
        config.mapIndex = mode.battlefield() ? Maps.firstBattlefieldIndex() : 0;
        config.seed = 7492L;
        return config;
    }

    private static void advance(Match match, float seconds) {
        InputState input = new InputState();
        int steps = (int) Math.ceil(seconds / Match.STEP);
        for (int i = 0; i < steps; i++) match.update(Match.STEP, input);
    }
}
