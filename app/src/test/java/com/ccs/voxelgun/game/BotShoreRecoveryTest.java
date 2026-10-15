package com.ccs.voxelgun.game;

import com.ccs.voxelgun.input.InputState;
import com.ccs.voxelgun.weapon.WeaponType;
import org.junit.Test;

import static org.junit.Assert.*;

/** Captured positions from the real map sweep, where only the body edge touches shallow water. */
public class BotShoreRecoveryTest {
    @Test public void activePirateBotsDoNotRepeatTheSameRoofedWaterEntry() {
        for (boolean mirror : new boolean[] {false, true}) {
            MatchConfig config = new MatchConfig();
            config.mapIndex = 5;
            config.mode = mirror ? GameMode.TEAM_DEATHMATCH : GameMode.CRYSTAL_HEIST;
            config.seed = mirror ? 20351519L : 20261519L;
            config.difficulty = mirror ? Difficulty.VETERAN : Difficulty.REGULAR;
            config.botCount = 7;
            if (mirror) {
                config.modifiers = MatchModifier.MIRROR_LOADOUT.bit | MatchModifier.HEADSHOTS_ONLY.bit;
                config.primary = WeaponType.RPG_V;
            }
            Match match = new Match(config);
            try {
                match.enableBotDiagnostics();
                InputState input = new InputState();
                for (int i = 0; i < 2310; i++) match.update(Match.STEP, input);
                int trappedBotId = mirror ? 4 : 2;
                boolean observed = false;
                for (BotDiagnostics.BotSnapshot bot : match.botDiagnostics().snapshot().bots) {
                    if (bot.id != trappedBotId) continue;
                    observed = true;
                    assertTrue(bot.activeSeconds > 20f);
                    assertTrue("swimming escape must be followed by a useful new route",
                            bot.idleSeconds / bot.activeSeconds < .25);
                    assertTrue("the escaped bot must not remain trapped at the shoreline",
                            bot.maxStallSeconds < 3.0);
                }
                assertTrue(observed);
            } finally { match.release(); }
        }
    }

    @Test public void pirateCoveRoofDoesNotTrapASwimmerBesideTheCurrentDryTile() {
        for (float corner : new float[] {.89f, .93f}) {
            MatchConfig config = new MatchConfig();
            config.mode = GameMode.RANGE;
            config.mapIndex = 5;
            config.botCount = 1;
            config.seed = 20261519L;
            Match match = new Match(config);
            try {
                InputState input = new InputState();
                for (int i = 0; i < 210; i++) match.update(Match.STEP, input);
                Combatant bot = match.combatants().get(1);
                bot.position.set(38f + corner, 3.1999f, 48f + corner);
                bot.velocity.set(0f, 0f, 0f);
                bot.grounded = false;
                assertTrue(Swimming.inWater(match.world(), bot));
                assertFalse(match.world().boxCollides(bot.position.x, bot.position.y,
                        bot.position.z, Combatant.RADIUS, bot.height()));
                boolean escaped = false;
                for (int i = 0; i < 180; i++) {
                    match.update(Match.STEP, input);
                    assertFalse("escaping must obey the existing roof/body collision",
                            match.world().boxCollides(bot.position.x, bot.position.y,
                                    bot.position.z, Combatant.RADIUS, bot.height()));
                    if (bot.grounded && !Swimming.inWater(match.world(), bot)) {
                        escaped = true;
                        break;
                    }
                }
                assertTrue("the tile beneath the swimmer is a reachable dry exit", escaped);
                assertTrue(bot.alive);
                // A passive target stays safely dry after control returns to its normal rules.
                for (int i = 0; i < 180; i++) match.update(Match.STEP, input);
                assertTrue(bot.grounded);
                assertFalse(Swimming.inWater(match.world(), bot));
                assertEquals(0f, bot.velocity.x, .001f);
                assertEquals(0f, bot.velocity.z, .001f);
            } finally { match.release(); }
        }
    }
}
