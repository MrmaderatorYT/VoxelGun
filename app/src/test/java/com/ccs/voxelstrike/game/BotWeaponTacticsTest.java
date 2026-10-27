package com.ccs.voxelstrike.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ccs.voxelstrike.weapon.WeaponClass;
import com.ccs.voxelstrike.weapon.WeaponSlot;
import com.ccs.voxelstrike.weapon.WeaponType;

import org.junit.Test;

import java.util.EnumSet;

public class BotWeaponTacticsTest {
    @Test public void bothArenaTeamsCoverEveryPermittedWeaponFamily() {
        for (int modifiers : new int[]{0, MatchModifier.HEADSHOTS_ONLY.bit}) {
            MatchConfig config = new MatchConfig();
            config.mode = GameMode.TEAM_DEATHMATCH; config.botCount = 12;
            config.seed = 9317L; config.modifiers = modifiers;
            Match match = new Match(config);
            try {
                for (Team team : new Team[]{Team.BLUE, Team.RED}) {
                    EnumSet<WeaponClass> classes = EnumSet.noneOf(WeaponClass.class);
                    for (Combatant bot : match.combatants()) if (bot.bot && bot.team == team) {
                        classes.add(bot.kit[0].weaponClass());
                        if (modifiers != 0) assertEquals(WeaponType.FLASH, bot.kit[3]);
                    }
                    for (WeaponClass type : new WeaponClass[]{WeaponClass.RIFLE, WeaponClass.SMG,
                            WeaponClass.SHOTGUN, WeaponClass.SNIPER, WeaponClass.LMG})
                        assertTrue(team + " missing " + type, classes.contains(type));
                    assertEquals(modifiers == 0, classes.contains(WeaponClass.LAUNCHER));
                }
            } finally { match.release(); }
        }
    }

    @Test public void battlefieldTeamsCycleThroughEveryGunWithinTheirClasses() {
        MatchConfig config = new MatchConfig();
        config.mode = GameMode.BATTLE_FIELD; config.totalPlayers = 49; config.seed = 9317L;
        Match match = new Match(config);
        try {
            for (Team team : new Team[]{Team.BLUE, Team.RED}) for (BattlefieldClass role : BattlefieldClass.values()) {
                EnumSet<WeaponType> weapons = EnumSet.noneOf(WeaponType.class);
                for (Combatant bot : match.combatants()) if (bot.bot && bot.team == team && bot.battlefieldClass == role)
                    weapons.add(bot.kit[0]);
                for (WeaponType gun : BattlefieldRules.weaponsForClass(role))
                    assertTrue(team + " / " + role + " missing " + gun, weapons.contains(gun));
            }
        } finally { match.release(); }
    }

    @Test
    public void sixBotsCoverAllPrimaryCombatRoles() {
        MatchConfig config = new MatchConfig();
        config.mode = GameMode.DEATHMATCH;
        config.botCount = 6;
        config.seed = 9317L;
        Match match = new Match(config);
        EnumSet<WeaponClass> classes = EnumSet.noneOf(WeaponClass.class);

        for (Combatant combatant : match.combatants()) {
            if (combatant.bot) {
                classes.add(combatant.kit[WeaponSlot.PRIMARY.ordinal()].weaponClass());
            }
        }

        assertTrue(classes.contains(WeaponClass.RIFLE));
        assertTrue(classes.contains(WeaponClass.SMG));
        assertTrue(classes.contains(WeaponClass.SHOTGUN));
        assertTrue(classes.contains(WeaponClass.SNIPER));
        assertTrue(classes.contains(WeaponClass.LMG));
        assertTrue(classes.contains(WeaponClass.LAUNCHER));
        match.release();
    }

    @Test
    public void tacticalRangesAndHoldBiasMatchWeaponPurpose() {
        assertTrue(BotBrain.preferredRangeFor(WeaponType.AWM_X)
                > BotBrain.preferredRangeFor(WeaponType.NEGEV_M));
        assertTrue(BotBrain.preferredRangeFor(WeaponType.NEGEV_M)
                > BotBrain.preferredRangeFor(WeaponType.AK15));
        assertTrue(BotBrain.preferredRangeFor(WeaponType.AK15)
                > BotBrain.preferredRangeFor(WeaponType.MP7K));
        assertTrue(BotBrain.preferredRangeFor(WeaponType.MP7K)
                > BotBrain.preferredRangeFor(WeaponType.PUMPER));

        assertTrue(BotBrain.holdBiasFor(WeaponType.AWM_X)
                > BotBrain.holdBiasFor(WeaponType.AK15));
        assertTrue(BotBrain.holdBiasFor(WeaponType.AK15)
                > BotBrain.holdBiasFor(WeaponType.MP7K));
        assertTrue(BotBrain.holdBiasFor(WeaponType.MP7K)
                > BotBrain.holdBiasFor(WeaponType.PUMPER));
        assertTrue(BotBrain.minimumSafeRangeFor(WeaponType.RPG_V)
                > WeaponType.RPG_V.splashRadius());
        assertEquals(0f, BotBrain.minimumSafeRangeFor(WeaponType.AK15), 0.001f);
    }

    @Test
    public void specialistRolesDrawSidearmsWhenTheirPrimaryIsWrongForTheRange() {
        MatchConfig config = new MatchConfig();
        config.botCount = 1;
        Match match = new Match(config);

        Combatant sniper = new Combatant(50, "Sniper");
        sniper.bot = true;
        sniper.setLoadout(WeaponType.AWM_X, WeaponType.AUTO9, WeaponType.KNIFE,
                WeaponType.FRAG);
        sniper.switchTimer = 0f;
        new BotBrain(sniper, Difficulty.REGULAR, match, 5L).chooseCombatWeapon(7f);
        assertEquals(WeaponSlot.SECONDARY.ordinal(), sniper.slot);

        Combatant breacher = new Combatant(51, "Breacher");
        breacher.bot = true;
        breacher.setLoadout(WeaponType.PUMPER, WeaponType.RANGER, WeaponType.KNIFE,
                WeaponType.FRAG);
        breacher.switchTimer = 0f;
        new BotBrain(breacher, Difficulty.REGULAR, match, 7L).chooseCombatWeapon(24f);
        assertEquals(WeaponSlot.SECONDARY.ordinal(), breacher.slot);

        Combatant assault = new Combatant(52, "Assault");
        assault.bot = true;
        assault.setLoadout(WeaponType.AK15, WeaponType.P9, WeaponType.KNIFE, WeaponType.FRAG);
        assault.switchTimer = 0f;
        new BotBrain(assault, Difficulty.REGULAR, match, 11L).chooseCombatWeapon(24f);
        assertEquals(WeaponSlot.PRIMARY.ordinal(), assault.slot);
        match.release();
    }

    @Test
    public void battlefieldBotsCarryOnlyTheirClassGunPistolAndKnife() {
        MatchConfig config = new MatchConfig();
        config.mode = GameMode.BATTLE_FIELD;
        config.totalPlayers = 4;
        config.seed = 44L;
        Match match = new Match(config);
        try {
            Combatant sniper = null;
            for (Combatant bot : match.combatants()) {
                assertEquals(bot.battlefieldClass.weaponClass(),
                        bot.weapons[0].type().weaponClass());
                assertEquals(WeaponClass.PISTOL, bot.weapons[1].type().weaponClass());
                assertEquals(WeaponClass.MELEE, bot.weapons[2].type().weaponClass());
                if (bot.bot && bot.battlefieldClass == BattlefieldClass.SNIPER) sniper = bot;
            }
            assertTrue("match did not create a sniper-class bot", sniper != null);

            sniper.switchTimer = 0f;
            new BotBrain(sniper, Difficulty.REGULAR, match, 13L).chooseCombatWeapon(5f);
            assertEquals("sniper should draw the pistol up close", 1, sniper.slot);

            sniper.switchTimer = 0f;
            new BotBrain(sniper, Difficulty.REGULAR, match, 17L).chooseCombatWeapon(60f);
            assertEquals("sniper should return to the class weapon at range", 0, sniper.slot);
        } finally {
            match.release();
        }
    }
}
