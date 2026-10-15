package com.ccs.voxelgun.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ccs.voxelgun.input.InputState;
import com.ccs.voxelgun.map.Maps;
import com.ccs.voxelgun.weapon.WeaponClass;
import com.ccs.voxelgun.weapon.WeaponSlot;
import com.ccs.voxelgun.weapon.WeaponType;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/** End-to-end rules that distinguish the large Battle Field mode from the arena modes. */
public class BattlefieldModeTest {
    @Test
    public void playerCountIsAlwaysEvenAndWithinTheHundredPlayerCap() {
        assertEquals(2, BattlefieldRules.sanitizeTotalPlayers(-5));
        assertEquals(2, BattlefieldRules.sanitizeTotalPlayers(2));
        assertEquals(4, BattlefieldRules.sanitizeTotalPlayers(3));
        assertEquals(100, BattlefieldRules.sanitizeTotalPlayers(99));
        assertEquals(100, BattlefieldRules.sanitizeTotalPlayers(101));
    }

    @Test
    public void matchBuildsEqualTeamsAndClassSpecificLoadouts() {
        Match match = battlefield(false, BattlefieldRules.MAX_PLAYERS);
        try {
            int blue = 0;
            int red = 0;
            Set<String> occupied = new HashSet<>();
            for (Combatant combatant : match.combatants()) {
                if (combatant.team == Team.BLUE) blue++;
                if (combatant.team == Team.RED) red++;
                assertEquals(combatant.battlefieldClass.weaponClass(),
                        combatant.weapons[WeaponSlot.PRIMARY.ordinal()].type().weaponClass());
                assertEquals(WeaponClass.PISTOL,
                        combatant.weapons[WeaponSlot.SECONDARY.ordinal()].type().weaponClass());
                assertEquals(WeaponClass.MELEE,
                        combatant.weapons[WeaponSlot.MELEE.ordinal()].type().weaponClass());
                assertTrue(combatant.weapons[WeaponSlot.GRENADE.ordinal()] == null);
                assertTrue("two soldiers share a deployment point",
                        occupied.add(combatant.position.x + ":" + combatant.position.z));
            }
            assertEquals(50, blue);
            assertEquals(50, red);
            assertEquals(100, match.combatants().size());
            assertFalse(match.buyOpen());
        } finally {
            match.release();
        }
    }

    @Test
    public void everyWeaponClassGetsOnlyItsLongGunPlusPistolAndKnife() {
        for (BattlefieldClass role : BattlefieldClass.values()) {
            MatchConfig config = config(false, 2);
            config.battlefieldClass = role;
            Match match = new Match(config);
            try {
                Combatant player = match.player();
                assertEquals(role.weaponClass(), player.weapons[0].type().weaponClass());
                assertEquals(WeaponType.P9, player.weapons[1].type());
                assertEquals(WeaponType.KNIFE, player.weapons[2].type());
                assertTrue(player.weapons[3] == null);
            } finally {
                match.release();
            }
        }
    }

    @Test
    public void sprintingPlayerCanKeepMovingAndFire() {
        Match match = battlefield(false, 2);
        try {
            run(match, 3.3f);
            parkBots(match);
            Combatant player = match.player();
            int ammoBefore = player.weapon().ammo();
            InputState movingAndFiring = new InputState();
            movingAndFiring.setTouchMove(0f, 1f);
            movingAndFiring.setTouchSprint(true);
            movingAndFiring.setTouchFire(true);
            match.update(Match.STEP, movingAndFiring);

            assertTrue("forward movement did not engage sprint", player.sprinting);
            assertTrue("sprint swallowed the fire input", player.firing);
            assertTrue("weapon did not fire while sprinting", player.weapon().ammo() < ammoBefore);
        } finally {
            match.release();
        }
    }

    @Test
    public void customScoreAndClockReachTheHud() {
        MatchConfig config = config(false, 2);
        config.scoreLimit = 375;
        config.timeLimitSeconds = 25f * 60f;
        Match match = new Match(config);
        try {
            HudSnapshot hud = match.snapshot();
            assertEquals(375, hud.scoreLimit);
            assertTrue(hud.timed);
            assertEquals(25f * 60f, hud.timeLeft, 0.001f);
        } finally {
            match.release();
        }
    }

    @Test
    public void friendlyFireOnlyHurtsInHardcoreAndNeverAwardsAKill() {
        Match normal = battlefield(false, 4);
        try {
            Combatant ally = blueAlly(normal);
            float health = ally.health;
            float armor = ally.armor;
            normal.applyDamage(ally, normal.player(), 40f, false, WeaponType.AK15);
            assertEquals(health, ally.health, 0f);
            assertEquals(armor, ally.armor, 0f);
        } finally {
            normal.release();
        }

        Match hardcore = battlefield(true, 4);
        try {
            Combatant ally = blueAlly(hardcore);
            hardcore.applyDamage(ally, hardcore.player(), 500f, false, WeaponType.AK15);
            assertFalse(ally.alive);
            assertEquals(0, hardcore.player().kills);
            assertEquals(0, hardcore.player().score);
            assertTrue(hardcore.hideTeamIndicators());
        } finally {
            hardcore.release();
        }
    }

    @Test
    public void healthRegeneratesAfterFifteenSecondsExceptInHardcore() {
        Match normal = battlefield(false, 2);
        try {
            parkBots(normal);
            normal.player().health = 40f;
            normal.player().lastDamageTime = BattlefieldRules.REGENERATION_DELAY_SECONDS;
            run(normal, 4.5f);
            assertTrue("normal mode did not regenerate", normal.player().health > 40f);
        } finally {
            normal.release();
        }

        Match hardcore = battlefield(true, 2);
        try {
            parkBots(hardcore);
            hardcore.player().health = 40f;
            hardcore.player().lastDamageTime = BattlefieldRules.REGENERATION_DELAY_SECONDS;
            run(hardcore, 4.5f);
            assertEquals(40f, hardcore.player().health, 0.001f);
        } finally {
            hardcore.release();
        }
    }

    private static Match battlefield(boolean hardcore, int totalPlayers) {
        return new Match(config(hardcore, totalPlayers));
    }

    private static MatchConfig config(boolean hardcore, int totalPlayers) {
        MatchConfig config = new MatchConfig();
        config.mode = GameMode.BATTLE_FIELD;
        config.mapIndex = Maps.firstBattlefieldIndex();
        config.totalPlayers = totalPlayers;
        config.hardcore = hardcore;
        config.seed = 4242L;
        config.battlefieldPrimary = WeaponType.VEKTOR_AR;
        config.battlefieldSniper = WeaponType.DRAGUNOV_S;
        config.battlefieldLmg = WeaponType.BULLDOG_LMG;
        return config;
    }

    private static Combatant blueAlly(Match match) {
        for (Combatant combatant : match.combatants()) {
            if (combatant != match.player() && combatant.team == Team.BLUE) {
                return combatant;
            }
        }
        throw new AssertionError("match has no blue bot");
    }

    private static void parkBots(Match match) {
        for (Combatant combatant : match.combatants()) {
            if (combatant.bot) {
                combatant.alive = false;
                combatant.respawnTimer = 1000f;
            }
        }
    }

    private static void run(Match match, float seconds) {
        InputState idle = new InputState();
        for (int i = 0; i < Math.ceil(seconds / Match.STEP); i++) {
            match.update(Match.STEP, idle);
        }
    }
}
