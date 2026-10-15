package com.ccs.voxelgun.game;

import com.ccs.voxelgun.audio.NativeSynth;
import com.ccs.voxelgun.audio.Sfx;
import com.ccs.voxelgun.input.InputState;
import com.ccs.voxelgun.map.Maps;
import com.ccs.voxelgun.map.SpawnPoint;
import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.weapon.WeaponType;
import org.junit.Test;
import static org.junit.Assert.*;

public class GameplayExpansionTest {
    private Match match(GameMode mode, int modifiers) {
        MatchConfig config = new MatchConfig();
        config.mode = mode; config.modifiers = modifiers; config.botCount = 1;
        config.difficulty = Difficulty.RECRUIT;
        Match match = new Match(config);
        for (int i = 0; i < 195; i++) match.update(Match.STEP, new InputState());
        return match;
    }
    private HudSnapshot snapshot(Match match) {
        InputState paused = new InputState(); paused.paused = true;
        match.update(0.04f, paused);
        return match.snapshot();
    }
    private void place(Match match, Combatant c, int node) {
        c.position.set(match.nav().cellX(node) + 0.5f, match.nav().nodeY(node), match.nav().cellZ(node) + 0.5f);
        c.velocity.set(0, 0, 0); c.firing = false;
    }
    private void advance(Objectives objectives, float seconds) {
        for (int i = 0; i < Math.round(seconds * 60); i++) objectives.update(Match.STEP);
    }

    @Test public void objectivesHaveReturnRoutesOnEveryMap() {
        for (int map = 0; map < Maps.count(); map++) {
            MatchConfig config = new MatchConfig(); config.mapIndex = map;
            config.mode = GameMode.CAPTURE_POINT; config.botCount = 1;
            Match match = new Match(config);
            try {
                Objectives objectives = match.objectives();
                PathFinder.Path path = new PathFinder.Path(8192);
                for (Combatant c : match.combatants()) {
                    int start = match.nav().nearestNode(c.position.x, c.position.y, c.position.z);
                    for (int i = 0; i < objectives.count(); i++) {
                        assertTrue(Maps.name(map) + " outgoing objective " + i,
                                match.pathFinder().find(start, objectives.node(i), path));
                        assertTrue(Maps.name(map) + " returning objective " + i,
                                match.pathFinder().find(objectives.node(i), objectives.base(c.team), path));
                    }
                }
                assertNotEquals(objectives.node(0), objectives.node(1));
                assertNotEquals(objectives.node(1), objectives.node(2));
            } finally { match.release(); }
        }
    }

    @Test public void hillScoresOnlyUncontestedOccupancyAndRotates() {
        Match match = match(GameMode.KING_OF_THE_HILL, 0);
        try {
            Objectives objectives = match.objectives();
            int first = objectives.node(0);
            place(match, match.player(), first);
            match.combatants().get(1).position.set(3, 4, 3);
            advance(objectives, 2.1f);
            int score = snapshot(match).blueScore;
            assertTrue(score >= 2);
            place(match, match.combatants().get(1), first);
            advance(objectives, 2f);
            assertEquals(score, snapshot(match).blueScore);
            match.player().position.set(3, 4, 3);
            match.combatants().get(1).position.set(3, 4, 3);
            advance(objectives, 56f);
            assertNotEquals(first, objectives.node(0));
        } finally { match.release(); }
    }

    @Test public void capturePointPersistsAndCanBeNeutralized() {
        Match match = match(GameMode.CAPTURE_POINT, 0);
        try {
            Objectives objectives = match.objectives();
            Combatant red = match.combatants().get(1);
            red.position.set(3, 4, 3);
            place(match, match.player(), objectives.node(0));
            advance(objectives, 6f);
            assertEquals(Team.BLUE.color(), snapshot(match).objectiveColor[0]);
            match.player().position.set(3, 4, 3);
            int score = snapshot(match).blueScore;
            advance(objectives, 2f);
            assertTrue(snapshot(match).blueScore > score);
            place(match, red, objectives.node(0));
            advance(objectives, 11f);
            assertEquals(Team.RED.color(), snapshot(match).objectiveColor[0]);
        } finally { match.release(); }
    }

    @Test public void bombCanBePlantedDefusedAndNextRoundRestoresBodies() {
        Match match = match(GameMode.BOMB_PLANT, 0);
        try {
            Objectives objectives = match.objectives();
            Combatant red = match.combatants().get(1);
            red.position.set(3, 4, 3);
            place(match, match.player(), objectives.node(0));
            advance(objectives, 3.1f);
            assertTrue(objectives.planted());
            match.player().position.set(3, 4, 3);
            place(match, red, objectives.node(0));
            advance(objectives, 5.1f);
            assertEquals(1, snapshot(match).redScore);
            assertTrue(objectives.roundPaused());
            match.player().alive = false;
            advance(objectives, 4.1f);
            assertTrue(match.player().alive);
            assertFalse(objectives.planted());
        } finally { match.release(); }
    }

    @Test public void plantedBombStillWinsAfterAttackersDie() {
        Match match = match(GameMode.BOMB_PLANT, 0);
        try {
            Objectives objectives = match.objectives();
            match.combatants().get(1).position.set(3, 4, 3);
            place(match, match.player(), objectives.node(0));
            advance(objectives, 3.1f);
            match.player().alive = false;
            advance(objectives, 35.1f);
            assertEquals(1, snapshot(match).blueScore);
        } finally { match.release(); }
    }

    @Test public void crystalDeliveryScoresAndDroppedCoreReturns() {
        Match match = match(GameMode.CRYSTAL_HEIST, 0);
        try {
            Objectives objectives = match.objectives();
            match.combatants().get(1).position.set(3, 4, 3);
            place(match, match.player(), objectives.node(0));
            objectives.update(Match.STEP);
            assertEquals(0, objectives.carrierId());
            int home = objectives.goalFor(match.player());
            place(match, match.player(), home);
            objectives.update(Match.STEP);
            assertEquals(1, snapshot(match).blueScore);
            assertEquals(-1, objectives.carrierId());
            place(match, match.player(), objectives.node(0));
            objectives.update(Match.STEP);
            place(match, match.player(), home);
            match.player().position.x += 6f;
            objectives.update(Match.STEP);
            match.player().alive = false;
            objectives.update(Match.STEP);
            advance(objectives, 20.1f);
            assertEquals(match.nav().cellX(objectives.node(0)) + 0.5f,
                    snapshot(match).objectiveX[0], 0.01f);
        } finally { match.release(); }
    }

    @Test public void killsDoNotCompleteObjectiveMatches() {
        Match match = match(GameMode.CRYSTAL_HEIST, 0);
        try {
            Combatant enemy = match.combatants().get(1);
            for (int i = 0; i < 6; i++) {
                enemy.alive = true; enemy.health = 100;
                match.applyDamage(enemy, match.player(), 500f, false, WeaponType.AK15);
            }
            assertEquals(6, match.player().kills);
            assertEquals(0, snapshot(match).blueScore);
            assertEquals(Match.State.PLAYING, match.state());
        } finally { match.release(); }
    }

    @Test public void headshotsOnlyRejectsBodyMeleeAndSplashButNotWorldDamage() {
        Match match = match(GameMode.DEATHMATCH, MatchModifier.HEADSHOTS_ONLY.bit);
        try {
            Combatant enemy = match.combatants().get(1);
            enemy.health = 100; enemy.armor = 0;
            match.applyDamage(enemy, match.player(), 30, false, WeaponType.AK15);
            match.applyDamage(enemy, match.player(), 150, false, WeaponType.KNIFE);
            match.applyDamage(enemy, match.player(), 100, false, WeaponType.FRAG);
            assertEquals(100, enemy.health, 0.01f);
            match.applyDamage(enemy, match.player(), 30, true, WeaponType.AK15);
            assertEquals(70, enemy.health, 0.01f);
            match.applyDamage(enemy, null, 10, false, null);
            assertEquals(60, enemy.health, 0.01f);
        } finally { match.release(); }
    }

    @Test public void hitKindsDistinguishArmorBodyAndHead() {
        Match match = match(GameMode.DEATHMATCH, 0);
        try {
            Combatant enemy = match.combatants().get(1);
            enemy.armor = 50;
            match.applyDamage(enemy, match.player(), 5, false, WeaponType.AK15);
            assertEquals(1, snapshot(match).hitKind);
            enemy.armor = 0;
            match.applyDamage(enemy, match.player(), 5, false, WeaponType.AK15);
            assertEquals(0, snapshot(match).hitKind);
            match.applyDamage(enemy, match.player(), 5, true, WeaponType.AK15);
            assertEquals(2, snapshot(match).hitKind);
            assertEquals(0, enemy.lastDamageTime, 0.001f);
        } finally { match.release(); }
    }

    @Test public void modeLoadoutsCannotBeOverriddenByIncompatibleModifiers() {
        assertEquals(0, MatchModifier.sanitize(2 | 4 | 64 | 128, GameMode.KNIFE_FIGHT));
        assertEquals(0, MatchModifier.sanitize(2 | 4 | 64 | 128, GameMode.GUN_GAME));
        assertEquals(128, MatchModifier.sanitize(64 | 128, GameMode.DEATHMATCH));
        Match match = match(GameMode.DEATHMATCH, MatchModifier.ONE_SHOT_RAIL.bit);
        try {
            for (Combatant c : match.combatants()) assertEquals(WeaponType.SCOUT_V, c.weaponType());
            assertFalse(match.buyOpen());
        } finally { match.release(); }
    }

    @Test public void pauseFreezesCountdownAndCombat() {
        MatchConfig config = new MatchConfig(); config.botCount = 1;
        Match match = new Match(config);
        try {
            InputState input = new InputState(); input.paused = true;
            for (int i = 0; i < 400; i++) match.update(Match.STEP, input);
            assertEquals(Match.State.COUNTDOWN, match.state());
            assertFalse(match.tryFire(match.player(), true));
            assertEquals(0, match.player().kills);
        } finally { match.release(); }
    }

    @Test public void newSoundsHaveDistinctNonSilentPcm() {
        for (int kind = Sfx.ARMOR_HIT; kind < Sfx.COUNT; kind++) {
            short[] pcm = NativeSynth.synthesize(kind, 44100, 123, 0.9f);
            long energy = 0;
            for (short value : pcm) energy += Math.abs((int) value);
            assertTrue("silent " + kind, energy > 10000);
            assertTrue(pcm.length >= 4000);
        }
        assertEquals(Sfx.STEP_METAL, Sfx.footstep(Materials.STEEL));
        assertEquals(Sfx.STEP_WOOD, Sfx.footstep(Materials.DECK_WOOD));
        assertEquals(Sfx.STEP_SOFT, Sfx.footstep(Materials.GRASS));
    }
}
