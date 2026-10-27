package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.weapon.WeaponSlot;
import com.ccs.voxelstrike.weapon.WeaponType;
import com.ccs.voxelstrike.voxel.Materials;
import org.junit.Test;
import static org.junit.Assert.*;

public class BotCombatStyleTest {
    @Test public void stylesFollowPersistentPrimaryRatherThanSelectedPistol() {
        Combatant c = new Combatant(7, "Marksman");
        c.setLoadout(WeaponType.AWM_X, WeaponType.P9, WeaponType.KNIFE, WeaponType.FRAG);
        assertEquals(BotCombatStyle.SNIPER, BotCombatStyle.forCombatant(c));
        c.slot = WeaponSlot.SECONDARY.ordinal();
        assertEquals(BotCombatStyle.SNIPER, BotCombatStyle.forCombatant(c));
        c.setLoadout(WeaponType.MP7K, WeaponType.P9, WeaponType.KNIFE, WeaponType.FRAG);
        assertEquals(BotCombatStyle.RUSHER, BotCombatStyle.forCombatant(c));
        c.setLoadout(WeaponType.NEGEV_M, WeaponType.P9, WeaponType.KNIFE, WeaponType.FRAG);
        assertEquals(BotCombatStyle.CAUTIOUS, BotCombatStyle.forCombatant(c));
    }

    @Test public void assaultTeamHasDifferentDecisionsNotIdenticalAimOnly() {
        Combatant c = new Combatant(4, "Assault");
        c.setSingleWeapon(WeaponType.AK15);
        assertEquals(BotCombatStyle.RUSHER, BotCombatStyle.forCombatant(c));
        Combatant other = new Combatant(2, "Support");
        other.setSingleWeapon(WeaponType.AK15);
        assertEquals(BotCombatStyle.CAUTIOUS, BotCombatStyle.forCombatant(other));
        assertTrue(BotCombatStyle.RUSHER.rangeFactor() < BotCombatStyle.CAUTIOUS.rangeFactor());
        assertTrue(BotCombatStyle.RUSHER.holdFactor() < BotCombatStyle.CAUTIOUS.holdFactor());
        assertTrue(BotCombatStyle.RUSHER.advanceFactor() > BotCombatStyle.CAUTIOUS.advanceFactor());
    }

    @Test public void difficultyChangesMovementReactionAndDecisionFrequency() {
        assertTrue(Difficulty.ELITE.movementReactionSeconds()
                < Difficulty.RECRUIT.movementReactionSeconds());
        assertTrue(Difficulty.ELITE.evasionCooldownSeconds()
                < Difficulty.RECRUIT.evasionCooldownSeconds());
        assertTrue(Difficulty.ELITE.sniperSettleSeconds()
                < Difficulty.RECRUIT.sniperSettleSeconds());
        for (Difficulty d : Difficulty.values()) {
            assertTrue(d.movementReactionSeconds() >= .09f);
            assertTrue(d.evasionCooldownSeconds() > 1f);
            assertTrue(d.sniperSettleSeconds() >= .10f);
        }
    }

    @Test public void aNewSniperKitCancelsAnExistingAssaultChase() throws Exception {
        MatchConfig config = new MatchConfig();
        config.mode = GameMode.DEATHMATCH;
        config.botCount = 0;
        Match match = new Match(config);
        try {
            Combatant bot = new Combatant(4, "Changing role");
            bot.bot = true;
            // Put the acoustic cue in an unobstructed lane, rather than under the map at (0,0,0).
            for (int x = 58; x <= 82; x++) for (int z = 28; z <= 32; z++)
                for (int y = 0; y < match.world().sizeY(); y++)
                    match.world().set(x, y, z, y == 0 ? Materials.CONCRETE : Materials.AIR);
            match.world().commit();
            match.nav().refreshArea(match.world(), 58, 28, 82, 32);
            bot.position.set(60.5f, 1f, 30.5f);
            bot.setLoadout(WeaponType.MP7K, WeaponType.P9, WeaponType.KNIFE, null);
            BotBrain brain = new BotBrain(bot, Difficulty.REGULAR, match, 71L);
            brain.hearGunshot(80.5f, 1f, 30.5f, 1f);
            java.lang.reflect.Field state = BotBrain.class.getDeclaredField("state");
            state.setAccessible(true);
            assertEquals("HUNT", state.get(brain).toString());
            bot.setLoadout(WeaponType.AWM_X, WeaponType.P9, WeaponType.KNIFE, null);
            assertEquals(BotCombatStyle.SNIPER, brain.combatStyle());
            assertEquals("a role change must not preserve an old rush toward gunfire",
                    "PATROL", state.get(brain).toString());
        } finally { match.release(); }
    }
}
