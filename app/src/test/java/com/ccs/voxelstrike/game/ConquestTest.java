package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.input.InputState;
import com.ccs.voxelstrike.map.Maps;
import com.ccs.voxelstrike.weapon.WeaponSlot;
import com.ccs.voxelstrike.weapon.WeaponType;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConquestTest {
    private Match create() {
        MatchConfig config = new MatchConfig();
        config.mode = GameMode.BATTLE_FIELD; config.conquest = true;
        config.mapIndex = Maps.firstBattlefieldIndex(); config.totalPlayers = 4;
        return new Match(config);
    }

    private void moveTo(Match m, Combatant c, int point) {
        int n = m.conquest().node(point);
        c.position.set(m.nav().cellX(n) + .5f, m.nav().nodeY(n), m.nav().cellZ(n) + .5f);
        c.alive = true;
    }

    private void capture(Match m, int point) {
        moveTo(m, m.player(), point);
        for (int i = 0; i < 500; i++) m.conquest().update(Match.STEP);
        assertEquals(Team.BLUE, m.conquest().owner(point));
    }

    @Test public void fivePointsTicketsAndGrenadesReachHud() {
        Match m = create();
        try {
            assertNotNull(m.conquest());
            assertEquals(5, m.snapshot().objectiveCount);
            assertEquals(250, m.snapshot().blueScore);
            assertEquals(250, m.snapshot().redScore);
            assertTrue(m.snapshot().conquest);
            assertEquals(WeaponType.FRAG, m.player().weapons[WeaponSlot.GRENADE.ordinal()].type());
            for (int i = 0; i < 5; i++) assertTrue(m.nav().valid(m.conquest().node(i)));
        } finally { m.release(); }
    }

    @Test public void contestedPointDoesNotCaptureAndOwnershipMustBeNeutralized() {
        Match m = create();
        try {
            Combatant red = m.combatants().get(1);
            moveTo(m, m.player(), 2); moveTo(m, red, 2);
            m.conquest().update(9f);
            assertTrue(m.conquest().contested(2));
            assertEquals(Team.NONE, m.conquest().owner(2));
            red.position.set(620, 3, 270);
            capture(m, 2);
            m.player().position.set(8, 3, 270);
            moveTo(m, red, 2);
            m.conquest().update(8.1f);
            assertEquals(Team.NONE, m.conquest().owner(2));
            m.conquest().update(8.1f);
            assertEquals(Team.RED, m.conquest().owner(2));
        } finally { m.release(); }
    }

    @Test public void majorityBleedsOpposingTicketsAndDeathCostsOne() {
        Match m = create();
        try {
            capture(m, 0); capture(m, 1); capture(m, 2);
            int before = m.conquest().tickets(Team.RED);
            m.conquest().update(2.1f);
            assertEquals(before - 3, m.conquest().tickets(Team.RED));
            m.conquest().death(Team.BLUE);
            assertEquals(249, m.conquest().tickets(Team.BLUE));
            for (int i = 0; i < 400; i++) m.conquest().death(Team.RED);
            assertEquals(0, m.conquest().tickets(Team.RED));
        } finally { m.release(); }
    }

    @Test public void forwardDeploymentRejectsEnemyProximityAndRecentDamage() {
        Match m = create();
        try {
            Combatant mate = m.combatants().get(2), red = m.combatants().get(1);
            moveTo(m, mate, 2); mate.lastDamageTime = 20;
            assertNotNull(m.conquest().deploymentFor(m.player()));
            mate.lastDamageTime = 0;
            assertNull(m.conquest().deploymentFor(m.player()));
            mate.lastDamageTime = 20; moveTo(m, red, 2);
            assertNull(m.conquest().deploymentFor(m.player()));
            assertEquals(Conquest.squad(m.player()), Conquest.squad(mate));
        } finally { m.release(); }
    }

    @Test public void eliminatedTicketsFinishTheMatchWithoutKillScoreVictory() {
        Match m = create();
        try {
            InputState input = new InputState();
            for (int i = 0; i < 210; i++) m.update(Match.STEP, input);
            for (int i = 0; i < 250; i++) m.conquest().death(Team.RED);
            m.update(Match.STEP, input);
            assertEquals(Match.State.FINISHED, m.state());
        } finally { m.release(); }
    }
}
