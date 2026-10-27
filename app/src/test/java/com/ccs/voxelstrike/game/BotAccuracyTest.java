package com.ccs.voxelstrike.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ccs.voxelstrike.weapon.WeaponType;

import org.junit.Test;

/** Regression coverage for finite bot accuracy. */
public class BotAccuracyTest {
    @Test
    public void precisionWeaponsKeepADifficultyOrderedMotorErrorCone() {
        MatchConfig config = new MatchConfig();
        config.botCount = 1;
        Match match = new Match(config);
        float previous = Float.MAX_VALUE;

        for (Difficulty difficulty : Difficulty.values()) {
            Combatant bot = new Combatant(99, "Accuracy probe");
            bot.bot = true;
            BotBrain brain = new BotBrain(bot, difficulty, match, 19L);
            float spread = brain.shotSpread(WeaponType.AWM_X,
                    WeaponType.AWM_X.adsSpread());

            assertTrue(difficulty.label() + " AWM cone collapsed to a perfect ray",
                    spread >= 0.6f);
            assertTrue(difficulty.label() + " broke the accuracy ladder", spread < previous);
            previous = spread;
        }
        match.release();
    }

    @Test
    public void nativeWideSpreadStillWinsOverTheMotorErrorFloor() {
        MatchConfig config = new MatchConfig();
        config.botCount = 1;
        Match match = new Match(config);
        Combatant bot = new Combatant(99, "Spread probe");
        bot.bot = true;
        BotBrain brain = new BotBrain(bot, Difficulty.ELITE, match, 23L);

        float nativeCone = WeaponType.PUMPER.hipSpread();
        assertEquals(nativeCone * Difficulty.ELITE.spreadMultiplier(),
                brain.shotSpread(WeaponType.PUMPER, nativeCone), 0.001f);
        assertTrue("launcher bypassed bot inaccuracy",
                brain.shotSpread(WeaponType.RPG_V, WeaponType.RPG_V.adsSpread()) > 0.5f);
        match.release();
    }
}
