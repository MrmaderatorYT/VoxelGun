package com.ccs.voxelgun;

import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.ccs.voxelgun.game.*;
import com.ccs.voxelgun.input.InputState;
import com.ccs.voxelgun.map.Maps;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Full-size native world and 100 actors on the real device; not a GPU FPS benchmark. */
@RunWith(AndroidJUnit4.class)
public class BattlefieldInstrumentedTest {
    @Test public void hundredSoldierConquestRunsWithoutInvalidPositions() {
        MatchConfig config = new MatchConfig();
        config.mode = GameMode.BATTLE_FIELD; config.conquest = true;
        config.mapIndex = Maps.firstBattlefieldIndex(); config.totalPlayers = 100;
        Match match = new Match(config);
        try {
            InputState input = new InputState();
            for (int i = 0; i < 210; i++) match.update(Match.STEP, input);
            long total = 0, maximum = 0;
            for (int i = 0; i < 600; i++) {
                long start = System.nanoTime(); match.update(Match.STEP, input);
                long elapsed = System.nanoTime() - start; total += elapsed;
                maximum = Math.max(maximum, elapsed);
            }
            Log.i("BattlefieldTest", "100 soldiers / mean simulation ms=" + total / 600e6
                    + " / worst ms=" + maximum / 1e6);
            assertEquals(100, match.combatants().size());
            assertEquals(5, match.snapshot().objectiveCount);
            for (Combatant c : match.combatants()) {
                assertTrue(Float.isFinite(c.position.x)); assertTrue(Float.isFinite(c.position.y));
                assertTrue(Float.isFinite(c.position.z));
            }
        } finally { match.release(); }
    }
}
