package com.ccs.voxelstrike;

import static org.junit.Assert.assertTrue;

import com.ccs.voxelstrike.game.Difficulty;
import com.ccs.voxelstrike.game.GameMode;
import com.ccs.voxelstrike.game.Match;
import com.ccs.voxelstrike.game.MatchConfig;
import com.ccs.voxelstrike.input.InputState;
import com.ccs.voxelstrike.map.Maps;

import org.junit.Test;

/**
 * Keeps the simulation affordable on a phone.
 *
 * <p>Two regressions hid here and both cost a third of the frame budget on a tablet: a bot taking
 * cover ran two dozen path searches in one call, and a bot that could not reach its wander goal
 * re-picked one sixty times a second. Neither showed up as an average — the median step was
 * free — so this measures the worst map rather than the mean, with enough headroom that ordinary
 * machine-to-machine variation cannot fail it.
 */
public class SimulationCostTest {
    /** Generous: the regressions this guards against were 8-40x over budget. */
    private static final double MEAN_BUDGET_MS = 2.0;
    private static final double WORST_BUDGET_MS = 45.0;

    private static double[] cost(GameMode mode, int mapIndex) {
        MatchConfig config = new MatchConfig();
        config.mode = mode;
        config.mapIndex = mapIndex;
        config.botCount = 7;
        config.difficulty = Difficulty.ELITE;
        config.seed = 99L;
        Match match = new Match(config);
        InputState idle = new InputState();
        // Warm the JIT: the first seconds measure compilation, not gameplay.
        for (int i = 0; i < (int) (8f / Match.STEP); i++) {
            match.update(Match.STEP, idle);
        }
        int steps = (int) (30f / Match.STEP);
        double total = 0;
        double worst = 0;
        for (int i = 0; i < steps; i++) {
            long start = System.nanoTime();
            match.update(Match.STEP, idle);
            double ms = (System.nanoTime() - start) / 1e6;
            total += ms;
            worst = Math.max(worst, ms);
        }
        match.release();
        return new double[] {total / steps, worst};
    }

    @Test
    public void noMapMakesTheHordeExpensive() {
        for (int map = 0; map < Maps.count(); map++) {
            double[] result = cost(GameMode.ZOMBIE, map);
            assertTrue(Maps.name(map) + " averaged " + result[0] + "ms per step",
                    result[0] < MEAN_BUDGET_MS);
            assertTrue(Maps.name(map) + " spiked to " + result[1] + "ms in one step",
                    result[1] < WORST_BUDGET_MS);
        }
    }

    @Test
    public void aFullTeamFightStaysCheap() {
        double[] result = cost(GameMode.TEAM_DEATHMATCH, 0);
        assertTrue("team deathmatch averaged " + result[0] + "ms per step",
                result[0] < MEAN_BUDGET_MS);
        assertTrue("team deathmatch spiked to " + result[1] + "ms in one step",
                result[1] < WORST_BUDGET_MS);
    }
}
