package com.ccs.voxelstrike.game;

import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.ccs.voxelstrike.input.InputState;
import com.ccs.voxelstrike.map.Maps;
import com.ccs.voxelstrike.performance.SectionMetrics;
import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.voxel.NativeBridge;
import com.ccs.voxelstrike.weapon.WeaponSlot;
import com.ccs.voxelstrike.weapon.WeaponType;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;

import static org.junit.Assert.*;

/** Role decisions through the real FSM, Match integration and Android JNI voxel queries. */
@RunWith(AndroidJUnit4.class)
public class BotTacticalRolesInstrumentedTest {
    @Test public void sniperSidearmRetreatAndSettledFireSurviveBhopToggle() throws Exception {
        assertTrue("this regression requires the actual JNI implementation", NativeBridge.available());
        for (boolean bhop : new boolean[]{false, true}) {
            MatchConfig config = new MatchConfig();
            config.mode = GameMode.TEAM_DEATHMATCH;
            config.botCount = 1;
            config.difficulty = Difficulty.ELITE;
            config.seed = 3L; // Primary pool index 3: the actual Match brain starts as a sniper.
            config.modifiers = bhop ? MatchModifier.BUNNY_HOP.bit : 0;
            Match match = new Match(config);
            try {
                assertTrue(match.world().nativeHandle() != 0);
                flattenCorridor(match);
                InputState idle = new InputState();
                start(match, idle);
                Combatant sniper = match.combatants().get(1), enemy = match.player();
                BotBrain brain = brainFor(match, sniper);
                assertEquals(BotCombatStyle.SNIPER, brain.combatStyle());
                sniper.setLoadout(WeaponType.AWM_X, WeaponType.P9, WeaponType.KNIFE, null);
                place(sniper, 80.5f, 30.5f);
                sniper.yaw = (float) Math.PI / 2f;
                sniper.switchTimer = 0f;
                place(enemy, 88.5f, 30.5f);
                enemy.health = enemy.armor = 100000f;
                brain.onRespawn();
                float originalX = sniper.position.x;
                boolean drewPistol = false;
                for (int tick = 0; tick < 60; tick++) {
                    match.update(Match.STEP, idle);
                    drewPistol |= sniper.slot == WeaponSlot.SECONDARY.ordinal();
                    assertEquals("sniper kit remains its role while holding a pistol, bhop=" + bhop,
                            BotCombatStyle.SNIPER, brain.combatStyle());
                    assertTrue("close defense must not launch a travel/evasion hop, bhop=" + bhop,
                            sniper.grounded);
                }
                assertTrue("the specialist must actually draw its sidearm", drewPistol);
                assertTrue("pistol selection must not change retreat into a rush, bhop=" + bhop,
                        sniper.position.x < originalX - 2f);

                // Give the real perception and weapon selector a clear shot at their settled range.
                place(enemy, sniper.position.x + 38f, 30.5f);
                int shots = 0;
                boolean scoped = false;
                for (int tick = 0; tick < 240; tick++) {
                    int before = sniper.weapons[WeaponSlot.PRIMARY.ordinal()].ammo();
                    match.update(Match.STEP, idle);
                    scoped |= sniper.aiming;
                    int spent = before - sniper.weapons[WeaponSlot.PRIMARY.ordinal()].ammo();
                    if (spent <= 0) continue;
                    shots += spent;
                    assertEquals(WeaponSlot.PRIMARY.ordinal(), sniper.slot);
                    assertTrue("a marksman shot must remain scoped", sniper.aiming);
                    assertTrue("BHOP must not override the planted firing phase", sniper.grounded);
                    assertFalse(sniper.sprinting);
                    assertTrue("the actual shot cannot be taken while running",
                            Math.hypot(sniper.velocity.x, sniper.velocity.z) <= 1.21f);
                }
                assertTrue("returning to a suitable range must restore the optic", scoped);
                assertTrue("role discipline must still permit actual ammo-consuming shots", shots > 0);

                // The target disappears behind new terrain: retain the role instead of chasing it.
                int wallX = (int) ((sniper.position.x + enemy.position.x) * .5f);
                for (int z = 20; z <= 42; z++) for (int y = 1; y <= 7; y++)
                    match.world().set(wallX, y, z, Materials.CONCRETE);
                match.world().commit();
                match.nav().refreshArea(match.world(), wallX - 1, 20, wallX + 1, 42);
                assertFalse(match.world().lineOfSight(sniper.eyeX(), sniper.eyeY(), sniper.eyeZ(),
                        enemy.eyeX(), enemy.eyeY(), enemy.eyeZ()));
                for (int tick = 0; tick < 36; tick++) {
                    match.update(Match.STEP, idle);
                    assertNotEquals("lost sight must not restore the old sniper chase", "HUNT", stateOf(brain));
                }
                assertEquals("PATROL", stateOf(brain));
                brain.hearGunshot(enemy.position.x, enemy.position.y, enemy.position.z, 2f);
                assertEquals("gunfire provides direction without replacing the marksman's route",
                        "PATROL", stateOf(brain));
            } finally { match.release(); }
        }
    }

    @Test public void hundredActorsRunFiveSecondsWithBoundedTacticalSightWork() throws Exception {
        assertTrue("this regression requires the actual JNI implementation", NativeBridge.available());
        MatchConfig config = new MatchConfig();
        config.mode = GameMode.BATTLE_FIELD;
        config.mapIndex = Maps.firstBattlefieldIndex();
        config.totalPlayers = 100;
        config.seed = 84031L;
        config.modifiers = MatchModifier.BUNNY_HOP.bit;
        Match match = new Match(config);
        try {
            assertTrue(match.world().nativeHandle() != 0);
            assertEquals(100, match.combatants().size());
            InputState idle = new InputState();
            start(match, idle);
            SquadCoordinator squads = squadsFor(match.battlefront());
            long start = System.nanoTime(), worst = 0L;
            float[] initialX = new float[100], initialZ = new float[100];
            for (Combatant c : match.combatants()) {
                initialX[c.id] = c.position.x; initialZ[c.id] = c.position.z;
            }
            int maxSightQueries = 0;
            for (int tick = 0; tick < 300; tick++) {
                long before = System.nanoTime();
                match.update(Match.STEP, idle);
                worst = Math.max(worst, System.nanoTime() - before);
                maxSightQueries = Math.max(maxSightQueries, squads.supportSightQueries());
                assertTrue("one tactical planning pass exceeded its explicit JNI ray budget",
                        squads.supportSightQueries() <= 24);
                for (Combatant c : match.combatants()) {
                    assertTrue(Float.isFinite(c.position.x) && Float.isFinite(c.position.y)
                            && Float.isFinite(c.position.z));
                }
            }
            int movingBots = 0;
            for (Combatant c : match.combatants()) if (c.bot && Math.hypot(c.position.x - initialX[c.id],
                    c.position.z - initialZ[c.id]) > 1f) movingBots++;
            assertTrue("the stress scene must exercise actual bot locomotion", movingBots >= 30);
            assertEquals(Match.State.PLAYING, match.state());
            Log.i("BotTacticalRoles", "100 actors / 300 ticks: totalMs="
                    + (System.nanoTime() - start) / 1000000L + ", worstStepUs=" + worst / 1000L
                    + ", rollingAiUs=" + match.metrics().averageMicros(SectionMetrics.AI)
                    + ", maxTacticalRays=" + maxSightQueries + ", movedBots=" + movingBots);
        } finally { match.release(); }
    }

    @Test public void actualPrimaryKitChangesRefreshRolesWhilePistolSelectionDoesNot() throws Exception {
        assertTrue("this regression requires the actual JNI implementation", NativeBridge.available());
        for (MatchModifier modifier : new MatchModifier[]{MatchModifier.ONE_SHOT_RAIL,
                MatchModifier.RANDOM_WEAPONS, MatchModifier.MIRROR_LOADOUT}) {
            MatchConfig config = new MatchConfig();
            config.mode = GameMode.TEAM_DEATHMATCH;
            config.botCount = 6;
            config.seed = 3L;
            config.modifiers = modifier.bit;
            Match match = new Match(config);
            try {
                assertTrue(match.world().nativeHandle() != 0);
                // These modifiers apply after Match constructs its brains and before first update.
                for (Combatant c : match.combatants()) if (c.bot) {
                    assertEquals("post-spawn role must match the actual primary kit for " + modifier,
                            BotCombatStyle.forCombatant(c), brainFor(match, c).combatStyle());
                    if (modifier == MatchModifier.ONE_SHOT_RAIL)
                        assertEquals(BotCombatStyle.SNIPER, brainFor(match, c).combatStyle());
                }
                if (modifier != MatchModifier.MIRROR_LOADOUT) continue;
                InputState idle = new InputState();
                start(match, idle);
                Combatant bot = match.combatants().get(1);
                BotBrain brain = brainFor(match, bot);
                bot.setLoadout(WeaponType.AWM_X, WeaponType.P9, WeaponType.KNIFE, null);
                match.update(Match.STEP, idle);
                assertEquals("a real runtime primary replacement gains precision behavior",
                        BotCombatStyle.SNIPER, brain.combatStyle());
                bot.switchTimer = 0f;
                if (bot.slot != WeaponSlot.SECONDARY.ordinal())
                    assertTrue(bot.selectSlot(WeaponSlot.SECONDARY.ordinal()));
                match.update(Match.STEP, idle);
                assertEquals(WeaponSlot.SECONDARY.ordinal(), bot.slot);
                assertEquals("drawing the sidearm leaves the sniper kit's role stable",
                        BotCombatStyle.SNIPER, brain.combatStyle());
                bot.setLoadout(WeaponType.AK15, WeaponType.P9, WeaponType.KNIFE, null);
                match.update(Match.STEP, idle);
                assertEquals("a new rifle kit must clear the previous sniper role",
                        BotCombatStyle.RUSHER, brain.combatStyle());
            } finally { match.release(); }
        }
    }

    private static void flattenCorridor(Match match) {
        for (int x = 50; x <= 116; x++) for (int z = 20; z <= 42; z++)
            for (int y = 0; y < match.world().sizeY(); y++)
                match.world().set(x, y, z, y == 0 ? Materials.CONCRETE : Materials.AIR);
        match.world().commit();
        match.nav().refreshArea(match.world(), 50, 20, 116, 42);
    }

    private static void start(Match match, InputState idle) {
        for (int tick = 0; tick < 300 && match.state() == Match.State.COUNTDOWN; tick++)
            match.update(Match.STEP, idle);
        assertEquals(Match.State.PLAYING, match.state());
    }

    private static void place(Combatant c, float x, float z) {
        c.position.set(x, 1f, z); c.velocity.set(0f, 0f, 0f);
        c.grounded = true; c.crouching = false; c.crouchBlend = 0f; c.alive = true;
    }

    private static BotBrain brainFor(Match match, Combatant c) throws Exception {
        Field field = Match.class.getDeclaredField("brainById"); field.setAccessible(true);
        return ((BotBrain[]) field.get(match))[c.id];
    }

    private static String stateOf(BotBrain brain) throws Exception {
        Field field = BotBrain.class.getDeclaredField("state"); field.setAccessible(true);
        return String.valueOf(field.get(brain));
    }

    private static SquadCoordinator squadsFor(BattlefrontPlanner planner) throws Exception {
        Field field = BattlefrontPlanner.class.getDeclaredField("squads"); field.setAccessible(true);
        return (SquadCoordinator) field.get(planner);
    }
}
