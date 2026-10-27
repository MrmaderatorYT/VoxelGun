package com.ccs.voxelstrike;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.ccs.voxelstrike.game.ArenaMovement;
import com.ccs.voxelstrike.game.BattlefieldRules;
import com.ccs.voxelstrike.game.Combatant;
import com.ccs.voxelstrike.game.Difficulty;
import com.ccs.voxelstrike.game.GameMode;
import com.ccs.voxelstrike.game.Match;
import com.ccs.voxelstrike.game.MatchConfig;
import com.ccs.voxelstrike.game.MatchModifier;
import com.ccs.voxelstrike.game.Team;
import com.ccs.voxelstrike.input.InputState;
import com.ccs.voxelstrike.map.Maps;
import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.voxel.NativeBridge;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real Match/BotBrain simulation and JNI-backed collision; never changes saved settings. */
@RunWith(AndroidJUnit4.class)
public class BotMovementInstrumentedTest {
    @Test public void gunshotInvestigationBuildsBhopSpeedAcrossThreeNativeLandings() {
        assertTrue("exercise native Android voxel collision", NativeBridge.available());
        MatchConfig config = new MatchConfig();
        config.mode = GameMode.DEATHMATCH;
        config.botCount = 1;
        config.difficulty = Difficulty.ELITE;
        config.seed = 93431L;
        config.modifiers = MatchModifier.BUNNY_HOP.bit | MatchModifier.NO_RELOAD.bit;
        Match match = new Match(config);
        try {
            assertEquals(2, match.combatants().size());
            assertTrue(match.world().nativeHandle() != 0);
            Combatant bot = match.combatants().get(1);
            // Keep the real FSM in navigation rather than a firefight. No private state hooks:
            // actual player shots below invoke Match.alertBots -> BotBrain.hearGunshot.
            match.player().team = Team.BLUE;
            bot.team = Team.RED;
            for (int x = 50; x <= 116; x++) {
                for (int z = 20; z <= 29; z++) {
                    for (int y = 0; y < match.world().sizeY(); y++) {
                        match.world().set(x, y, z, y == 0 ? Materials.CONCRETE : Materials.AIR);
                    }
                }
            }
            // A fabric baffle blocks sight but still lets this 40-block gunshot be heard.
            // Concrete now attenuates that shot below range; this fixture tests hopping.
            for (int z = 20; z <= 29; z++) for (int y = 1; y <= 7; y++)
                match.world().set(96, y, z, Materials.CANVAS);
            match.world().commit();
            match.nav().refreshArea(match.world(), 50, 20, 116, 29);
            InputState input = new InputState();
            for (int i = 0; i < 300 && match.state() == Match.State.COUNTDOWN; i++) {
                match.update(Match.STEP, input);
            }
            assertEquals(Match.State.PLAYING, match.state());
            match.player().position.set(100.5f, 1f, 24.5f);
            match.player().velocity.set(0f, 0f, 0f);
            match.player().grounded = true;
            match.player().yaw = (float) Math.PI / 2f; // Fire away from the approaching bot.
            bot.position.set(60.5f, 1f, 24.5f);
            bot.velocity.set(0f, 0f, 0f);
            bot.grounded = true;
            bot.crouching = false;
            bot.crouchBlend = 0f;
            input.setDeviceFire(true);
            float previousTakeoffSpeed = 0f;
            int takeoffs = 0;
            boolean crouchedAirborne = false;
            for (int tick = 0; tick < 600 && takeoffs < 4; tick++) {
                boolean grounded = bot.grounded;
                float before = horizontalSpeed(bot);
                match.update(Match.STEP, input);
                if (grounded && !bot.grounded && bot.velocity.y > 0f) {
                    float after = horizontalSpeed(bot);
                    if (takeoffs > 0) {
                        assertTrue("bot must make a fresh crouch during the flight", crouchedAirborne);
                        assertTrue("timed bot hop " + takeoffs + ": " + previousTakeoffSpeed
                                + " -> " + after, after > previousTakeoffSpeed + .20f);
                    }
                    takeoffs++;
                    previousTakeoffSpeed = after;
                    crouchedAirborne = false;
                } else {
                    if (grounded && takeoffs > 0) {
                        assertTrue("landing alone is not a speed reward", horizontalSpeed(bot) <= before + .02f);
                    }
                    if (!bot.grounded && bot.crouching) crouchedAirborne = true;
                }
                float reference = 7.7f * bot.weaponType().moveSpeedMultiplier();
                assertTrue("same cap as the human movement controller",
                        horizontalSpeed(bot) <= reference * ArenaMovement.BHOP_MAX_SPEED_FACTOR + .02f);
                assertTrue("no hidden health/damage shortcut", bot.alive);
            }
            assertEquals("initial jump followed by three timed bot hops", 4, takeoffs);
            assertTrue("three landings must advance down the corridor, x=" + bot.position.x,
                    bot.position.x > 70.5f);
            assertEquals(0, bot.kills);
            assertEquals(0, match.player().kills);
            input.releaseAll();
        } finally { match.release(); }
    }

    @Test public void nativeSoloBombAndBattlefieldStayOpenAndRespawnAfterVoidFall() {
        assertTrue("exercise native Android voxel collision", NativeBridge.available());
        for (GameMode mode : new GameMode[]{GameMode.BOMB_PLANT, GameMode.BATTLE_FIELD}) {
            MatchConfig config = new MatchConfig();
            config.mode = mode;
            config.modifiers = MatchModifier.NO_BOTS.bit;
            config.botCount = 7;
            config.totalPlayers = BattlefieldRules.MAX_PLAYERS;
            config.conquest = mode == GameMode.BATTLE_FIELD;
            config.mapIndex = mode == GameMode.BATTLE_FIELD ? Maps.firstBattlefieldIndex() : 0;
            config.seed = 42194L;
            Match match = new Match(config);
            try {
                assertTrue(mode.label(), match.world().nativeHandle() != 0);
                assertTrue(mode.label(), match.soloExploration());
                assertEquals(mode.label(), 1, match.combatants().size());
                assertEquals(mode.label(), 0f, match.timeLimitSeconds(), 0f);
                InputState input = new InputState();
                advance(match, input, 12f);
                assertEquals(mode.label(), Match.State.PLAYING, match.state());
                assertFalse(mode.label(), match.snapshot().finished);
                assertFalse(mode.label(), match.snapshot().timed);
                assertEquals(mode.label(), 0, match.snapshot().enemiesAlive);
                int blueTickets = match.conquest() == null ? 0 : match.conquest().tickets(Team.BLUE);
                int redTickets = match.conquest() == null ? 0 : match.conquest().tickets(Team.RED);
                match.player().position.y = -8f;
                match.update(Match.STEP, input);
                assertFalse(mode.label(), match.player().alive);
                advance(match, input, 4f);
                assertTrue(mode.label(), match.player().alive);
                assertEquals(mode.label(), Match.State.PLAYING, match.state());
                assertEquals(mode.label(), 1, match.combatants().size());
                if (match.conquest() != null) {
                    assertEquals(blueTickets, match.conquest().tickets(Team.BLUE));
                    assertEquals(redTickets, match.conquest().tickets(Team.RED));
                    assertTrue(match.snapshot().objectiveCount > 0);
                }
            } finally { match.release(); }
        }
    }

    private float horizontalSpeed(Combatant c) {
        return (float) Math.hypot(c.velocity.x, c.velocity.z);
    }

    private void advance(Match match, InputState input, float seconds) {
        for (int i = 0; i < Math.ceil(seconds / Match.STEP); i++) match.update(Match.STEP, input);
    }
}
