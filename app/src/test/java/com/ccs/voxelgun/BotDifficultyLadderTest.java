package com.ccs.voxelgun;

import static org.junit.Assert.assertTrue;

import com.ccs.voxelgun.game.Combatant;
import com.ccs.voxelgun.game.Difficulty;
import com.ccs.voxelgun.game.GameMode;
import com.ccs.voxelgun.game.Match;
import com.ccs.voxelgun.game.MatchConfig;
import com.ccs.voxelgun.input.InputState;

import org.junit.Test;

/**
 * Guards the difficulty ladder.
 *
 * <p>Sight range used to be short enough at the lower tiers that bots never met an enemy at all:
 * Recruit fired zero shots in ninety seconds, so "easy" meant "inert" rather than "beatable".
 * These tests assert that every tier actually fights and that skill still separates them.
 */
public class BotDifficultyLadderTest {
    private static final int ACTIVITY_RUNS = 2;
    private static final int LADDER_RUNS = 10;
    private static final float SECONDS = 45f;

    private static MatchConfig config(Difficulty difficulty, long seed) {
        MatchConfig config = new MatchConfig();
        config.mode = GameMode.TEAM_DEATHMATCH;
        config.mapIndex = 0;
        config.botCount = 7;
        config.difficulty = difficulty;
        config.seed = seed;
        return config;
    }

    /** Returns {shots, kills} summed over the bots of a few runs at this difficulty. */
    private static int[] sample(Difficulty difficulty, int runs) {
        int shots = 0;
        int kills = 0;
        for (int run = 0; run < runs; run++) {
            Match match = new Match(config(difficulty, 7000L + run * 13L));
            InputState idle = new InputState();
            int steps = (int) (SECONDS / Match.STEP);
            for (int i = 0; i < steps; i++) {
                match.update(Match.STEP, idle);
                for (Combatant bot : match.combatants()) {
                    // Firing marks a real shot this simulation step. Comparing selected-weapon
                    // ammunition across frames incorrectly counted weapon swaps as shots.
                    if (bot.bot && bot.firing) shots++;
                }
            }
            for (Combatant bot : match.combatants()) {
                if (bot.bot) {
                    kills += bot.kills;
                }
            }
            match.release();
        }
        return new int[] {shots, kills};
    }

    @Test
    public void everyDifficultyActuallyFights() {
        for (Difficulty difficulty : Difficulty.values()) {
            int[] result = sample(difficulty, ACTIVITY_RUNS);
            // Shots, not kills: a Recruit can legitimately go a short sample without a kill, but
            // firing nothing at all is the symptom of the bug this guards against.
            assertTrue(difficulty.label() + " bots never fired a shot", result[0] > 0);
        }
    }

    @Test
    public void skillStillSeparatesTheTiers() {
        // Both teams have the same skill in each match: improved defense also changes survival,
        // routes and encounter frequency. Two short seeds can therefore tie on total kills even
        // when the difficulty ladder is working. Keep activity smoke tests short, and compare
        // this broader deterministic self-play sample without weakening the strict ordering.
        int[] recruit = sample(Difficulty.RECRUIT, LADDER_RUNS);
        int[] elite = sample(Difficulty.ELITE, LADDER_RUNS);
        String counts = " (Recruit shots=" + recruit[0] + ", kills=" + recruit[1]
                + "; Elite shots=" + elite[0] + ", kills=" + elite[1] + ")";
        assertTrue("Elite never killed anyone" + counts, elite[1] > 0);
        assertTrue("Elite was no deadlier than Recruit" + counts, elite[1] > recruit[1]);
        assertTrue("Elite did not shoot more than Recruit" + counts, elite[0] > recruit[0]);
    }

    /**
     * Bots used to see each other constantly and shoot almost never: semi-automatics fired a
     * single round because nothing ever let the trigger up, and a third of every firefight was
     * spent walking away from it. This pins the fixed behaviour down.
     */
    @Test
    public void botsInSightOfEachOtherActuallyFight() {
        MatchConfig config = config(Difficulty.REGULAR, 77L);
        config.mode = GameMode.DEATHMATCH;
        Match match = new Match(config);
        InputState idle = new InputState();

        int firingTicks = 0;
        int aliveTicks = 0;
        int steps = (int) (60f / Match.STEP);
        for (int i = 0; i < steps; i++) {
            match.update(Match.STEP, idle);
            for (Combatant bot : match.combatants()) {
                if (!bot.bot || !bot.alive) {
                    continue;
                }
                aliveTicks++;
                if (bot.firing) {
                    firingTicks++;
                }
            }
        }
        int kills = 0;
        for (Combatant bot : match.combatants()) {
            if (bot.bot) {
                kills += bot.kills;
            }
        }
        // A shot leaves the barrel on one tick in many even under sustained fire, so the bar is
        // deliberately low; before the fix this sat near a tenth of it.
        assertTrue("bots barely pulled the trigger: " + (100f * firingTicks / aliveTicks) + "%",
                firingTicks > aliveTicks / 50);
        assertTrue("a minute of free-for-all produced " + kills + " kills", kills >= 5);
        match.release();
    }

    /**
     * A semi-automatic must keep firing, not stop after the first round.
     *
     * <p>{@code canFire} demands the trigger come back up between shots. Bots held it down
     * forever, so a pistol, a sniper rifle or a knife fired exactly once per engagement.
     */
    @Test
    public void semiAutomaticsKeepFiring() {
        MatchConfig config = config(Difficulty.ELITE, 5L);
        config.mode = GameMode.DEATHMATCH;
        Match match = new Match(config);
        InputState idle = new InputState();

        int shots = 0;
        int steps = (int) (60f / Match.STEP);
        for (int i = 0; i < steps; i++) {
            match.update(Match.STEP, idle);
            for (Combatant bot : match.combatants()) {
                if (!bot.bot || bot.weapon() == null
                        || bot.weaponType().automatic() || bot.weaponType().melee()) {
                    continue;   // count only the weapons that need a trigger reset
                }
                if (bot.firing) shots++;
            }
        }
        // One shot per bot per engagement was the broken behaviour; a minute of deathmatch with
        // semi-automatics in hand should be far past that.
        assertTrue("semi-automatic weapons fired " + shots + " rounds in a minute", shots > 20);
        match.release();
    }
}
