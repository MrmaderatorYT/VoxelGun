package com.ccs.voxelgun;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ccs.voxelgun.game.Combatant;
import com.ccs.voxelgun.game.Difficulty;
import com.ccs.voxelgun.game.GameMode;
import com.ccs.voxelgun.game.Match;
import com.ccs.voxelgun.game.MatchConfig;
import com.ccs.voxelgun.input.InputState;
import com.ccs.voxelgun.weapon.WeaponType;

import org.junit.Test;

/** The practice range and the per-weapon career tallies, exercised headlessly. */
public class RangeAndWeaponStatsTest {
    private static MatchConfig config(GameMode mode, int bots) {
        MatchConfig config = new MatchConfig();
        config.mode = mode;
        config.mapIndex = 0;
        config.botCount = bots;
        config.difficulty = Difficulty.REGULAR;
        config.seed = 4242L;
        return config;
    }

    private static void run(Match match, InputState input, float seconds) {
        int steps = (int) (seconds / Match.STEP);
        for (int i = 0; i < steps; i++) {
            match.update(Match.STEP, input);
        }
    }

    @Test
    public void rangeTargetsHoldTheirGroundAndNeverShootBack() {
        Match match = new Match(config(GameMode.RANGE, 5));
        Combatant target = match.combatants().get(1);
        float startX = target.position.x;
        float startZ = target.position.z;

        run(match, new InputState(), 20f);

        assertTrue("a range target wandered off", Math.abs(target.position.x - startX) < 0.5f
                && Math.abs(target.position.z - startZ) < 0.5f);
        for (Combatant combatant : match.combatants()) {
            if (combatant.bot) {
                assertEquals("a target fired at the player", combatant.weaponType().magazine(),
                        combatant.weapon().ammo());
            }
        }
        assertEquals("the player took damage on the range", 100f, match.player().health, 0.01f);
        match.release();
    }

    @Test
    public void rangeNeverEndsOnItsOwn() {
        Match match = new Match(config(GameMode.RANGE, 3));
        run(match, new InputState(), 60f);
        assertTrue("the range ended by itself", match.result().mode == null);
        match.release();
    }

    @Test
    public void firingRecordsShotsAndHitsForTheWeaponInHand() {
        Match match = new Match(config(GameMode.DEATHMATCH, 3));
        run(match, new InputState(), 3.3f);

        WeaponType carried = match.player().weaponType();
        InputState input = new InputState();
        input.fireHeld = true;
        run(match, input, 2.5f);

        int[] tally = match.weaponTally(carried);
        assertTrue("no shots were counted", tally[0] > 0);
        assertTrue("hits cannot exceed shots", tally[1] <= tally[0]);
        match.release();
    }

    @Test
    public void aShotgunBlastCountsOnceHoweverManyPelletsLand() {
        Match match = new Match(config(GameMode.DEATHMATCH, 3));
        run(match, new InputState(), 3.3f);
        Combatant player = match.player();
        player.setSingleWeapon(WeaponType.BREAKER12);
        player.slot = WeaponType.BREAKER12.slot().ordinal();
        player.switchTimer = 0f;

        InputState input = new InputState();
        input.fireHeld = true;
        run(match, input, 3f);

        int[] tally = match.weaponTally(WeaponType.BREAKER12);
        assertTrue("the shotgun never fired", tally[0] > 0);
        assertTrue("more hits than shots: pellets were counted separately",
                tally[1] <= tally[0]);
        match.release();
    }

    @Test
    public void aimingOnlyWorksWithOptics() {
        Match match = new Match(config(GameMode.DEATHMATCH, 1));
        run(match, new InputState(), 3.3f);
        Combatant player = match.player();

        InputState input = new InputState();
        input.setDeviceAim(true);          // the keyboard/mouse path, not the on-screen button

        player.setSingleWeapon(WeaponType.AK15);
        player.slot = WeaponType.AK15.slot().ordinal();
        player.switchTimer = 0f;
        run(match, input, 1f);
        assertFalse("a rifle went to the sights", player.aiming);

        player.setSingleWeapon(WeaponType.AWM_X);
        player.slot = WeaponType.AWM_X.slot().ordinal();
        player.switchTimer = 0f;
        run(match, input, 1f);
        assertTrue("the sniper refused to scope", player.aiming);
        match.release();
    }
}
