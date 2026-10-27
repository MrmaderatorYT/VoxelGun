package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.input.InputState;
import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.weapon.WeaponType;
import org.junit.Test;
import static org.junit.Assert.*;

public class MatchModifiersTest {
    @Test public void vampireRespectsZombiesHigherMaximumHealth() {
        MatchConfig config = new MatchConfig(); config.mode = GameMode.ZOMBIE;
        config.modifiers = MatchModifier.VAMPIRE.bit; config.botCount = 1;
        Match m = new Match(config);
        try {
            Combatant zombie = m.combatants().get(1);
            assertTrue(zombie.zombie);
            float maximum = zombie.health;
            zombie.health = maximum - 10f;
            m.player().armor = 0;
            m.applyDamage(m.player(), zombie, 20, false, WeaponType.SCRAP_KNIFE);
            assertEquals(maximum - 5f, zombie.health, 0.001f);
        } finally { m.release(); }
    }

    private Match match(GameMode mode, int mask) {
        MatchConfig config = new MatchConfig();
        config.mode = mode; config.modifiers = mask; config.botCount = 3; config.seed = 24;
        Match m = new Match(config);
        for (int i = 0; i < 210; i++) m.update(Match.STEP, new InputState());
        return m;
    }
    private void snapshot(Match m) { InputState input = new InputState(); input.paused = true; m.update(0.04f, input); }
    private void flatPatch(Match m) {
        for (int x = 60; x < 81; x++) for (int z = 20; z < 41; z++) for (int y = 0; y < 30; y++)
            m.world().set(x, y, z, y == 0 ? Materials.CONCRETE : Materials.AIR);
        m.world().commit();
        for (Combatant c : m.combatants()) { c.armor = 0; c.health = 100; c.velocity.set(0, 0, 0); }
    }

    @Test public void vampireHealsOnlyActualEnemyHealthLossAndNeverOverkillOrArmor() {
        Match m = match(GameMode.RANGE, MatchModifier.VAMPIRE.bit);
        try {
            Combatant p = m.player(), target = m.combatants().get(1);
            p.health = 40; target.health = 100; target.armor = 100;
            m.applyDamage(target, p, 40, false, WeaponType.AK15);
            assertEquals(44.5f, p.health, 0.001f); // 18 HP after armour, 25% restored
            target.armor = 0; target.health = 10;
            m.applyDamage(target, p, 1000, false, WeaponType.AK15);
            assertEquals(47f, p.health, 0.001f);
            target.alive = true; target.health = 100; p.health = 99;
            m.applyDamage(target, p, 20, false, WeaponType.AK15);
            assertEquals(100f, p.health, 0.001f);
            p.health = 50;
            m.applyDamage(p, p, 20, false, WeaponType.FRAG);
            assertTrue(p.health <= 50);
        } finally { m.release(); }
    }

    @Test public void vampireDoesNotHealFriendlyFire() {
        MatchConfig config = new MatchConfig(); config.mode = GameMode.BATTLE_FIELD;
        config.totalPlayers = 2; config.hardcore = true; config.modifiers = MatchModifier.VAMPIRE.bit;
        Match m = new Match(config);
        try {
            Combatant p = m.player(), target = m.combatants().get(1);
            p.health = 40; target.team = p.team; target.armor = 0;
            m.applyDamage(target, p, 20, false, WeaponType.AK15);
            assertEquals(40f, p.health, 0.001f);
            assertEquals(80f, target.health, 0.001f);
        } finally { m.release(); }
    }

    @Test public void explosiveDeathChainsIterativelyWithKillCredit() {
        Match m = match(GameMode.RANGE, MatchModifier.EXPLOSIVE_DEATH.bit);
        try {
            flatPatch(m);
            m.player().position.set(78, 1, 25);
            Combatant first = m.combatants().get(1);
            for (int i = 1; i < m.combatants().size(); i++) {
                Combatant c = m.combatants().get(i);
                c.position.set(63 + i, 1, 25); c.health = 1;
            }
            m.applyDamage(first, m.player(), 1000, false, WeaponType.AK15);
            m.update(Match.STEP, new InputState());
            for (int i = 1; i < m.combatants().size(); i++) assertFalse(m.combatants().get(i).alive);
            assertEquals(1, m.player().kills);
            assertEquals(2, first.kills);
            assertEquals(100f, m.player().health, 0.001f);
        } finally { m.release(); }
    }

    @Test public void deathBlastRespectsSolidCoverAndDoesNotHurtTeammates() {
        Match m = match(GameMode.RANGE, MatchModifier.EXPLOSIVE_DEATH.bit);
        try {
            flatPatch(m);
            m.player().position.set(78, 1, 25);
            Combatant owner = m.combatants().get(1), covered = m.combatants().get(2), friend = m.combatants().get(3);
            owner.position.set(64, 1, 25); covered.position.set(66, 1, 25); friend.position.set(63, 1, 25);
            owner.team = friend.team = Team.BLUE; covered.team = Team.RED;
            for (int y = 1; y < 6; y++) for (int z = 20; z < 31; z++) m.world().set(65, y, z, Materials.CONCRETE);
            m.world().commit();
            m.applyDamage(owner, null, 1000, false, null);
            m.update(Match.STEP, new InputState());
            assertEquals(100f, covered.health, 0.001f);
            assertEquals(100f, friend.health, 0.001f);
        } finally { m.release(); }
    }

    @Test public void noFallDamagePreventsImpactDamageButNotVoidDeath() {
        for (int mask : new int[]{0, MatchModifier.NO_FALL_DAMAGE.bit}) {
            Match m = match(GameMode.MOVEMENT_TRAINING, mask);
            try {
                m.player().armor = 0;
                m.player().position.set(8.5f, 25, 32.5f);
                m.player().grounded = false;
                for (int i = 0; i < 150; i++) m.update(Match.STEP, new InputState());
                assertTrue(m.player().grounded);
                if (mask == 0) assertTrue(m.player().health < 100);
                else assertEquals(100f, m.player().health, 0.001f);
                m.player().position.y = -8;
                m.update(Match.STEP, new InputState());
                assertFalse(m.player().alive);
            } finally { m.release(); }
        }
    }

    @Test public void doubleJumpNeedsNewPressAndLandingRestoresItsBudget() {
        Match m = match(GameMode.MOVEMENT_TRAINING, MatchModifier.DOUBLE_JUMP.bit | MatchModifier.BUNNY_HOP.bit);
        try {
            InputState input = new InputState(); input.setDeviceJump(true); input.queueJump();
            m.update(Match.STEP, input);
            for (int i = 0; i < 25; i++) m.update(Match.STEP, input);
            assertFalse("holding bhop must not spend the airborne jump", m.player().airJumpUsed);
            input.queueJump(); m.update(Match.STEP, input);
            assertTrue(m.player().airJumpUsed);
            assertTrue(m.player().velocity.y > 6);
            for (int i = 0; i < 10; i++) m.update(Match.STEP, input);
            float before = m.player().velocity.y;
            input.queueJump(); m.update(Match.STEP, input);
            assertTrue("third jump must not reset vertical speed", m.player().velocity.y < before);
            input.setDeviceJump(false);
            for (int i = 0; i < 90; i++) m.update(Match.STEP, input);
            assertTrue(m.player().grounded);
            assertFalse(m.player().airJumpUsed);
        } finally { m.release(); }
    }

    @Test public void noModifierDoesNotPermitAnAirJump() {
        Combatant p = new Combatant(0, "test"); p.grounded = true;
        assertTrue(JumpRules.jump(p, 7.4f, false, true));
        assertFalse(JumpRules.jump(p, 7.4f, false, true));
        assertFalse(p.airJumpUsed);
    }

    @Test public void mirrorLoadoutCopiesPlayerKitWithoutSharingWeaponInstances() {
        MatchConfig config = new MatchConfig(); config.mode = GameMode.RANGE; config.botCount = 3;
        config.primary = WeaponType.FAMAS_B; config.secondary = WeaponType.P9;
        config.modifiers = MatchModifier.MIRROR_LOADOUT.bit;
        Match m = new Match(config);
        try {
            for (Combatant c : m.combatants()) {
                assertArrayEquals(m.player().kit, c.kit);
                if (c != m.player()) assertNotSame(m.player().weapon(), c.weapon());
            }
        } finally { m.release(); }
    }

    @Test public void hardcoreHudDoesNotEnableFriendlyFireOrDisableRegeneration() {
        Match m = match(GameMode.RANGE, MatchModifier.HARDCORE_HUD.bit);
        try {
            assertTrue(m.hideTeamIndicators()); assertFalse(m.hardcore());
            Combatant target = m.combatants().get(1);
            target.team = m.player().team = Team.BLUE;
            target.armor = 0;
            m.applyDamage(target, m.player(), 50, false, WeaponType.AK15);
            assertEquals(100f, target.health, 0.001f);
            snapshot(m);
            assertTrue(m.snapshot().hardcoreHud);
            assertEquals(0, m.snapshot().blipCount);
        } finally { m.release(); }
    }

    @Test public void allNewBitsAreStableAndMirrorWinsOverRandomLoadouts() {
        assertEquals(512, MatchModifier.NO_FALL_DAMAGE.bit);
        assertEquals(65536, MatchModifier.HARDCORE_HUD.bit);
        int mask = MatchModifier.MIRROR_LOADOUT.bit | MatchModifier.RANDOM_WEAPONS.bit;
        assertEquals(MatchModifier.MIRROR_LOADOUT.bit, MatchModifier.sanitize(mask, GameMode.DEATHMATCH));
        assertEquals(0, MatchModifier.sanitize(MatchModifier.MIRROR_LOADOUT.bit, GameMode.ZOMBIE));
        assertEquals(0, MatchModifier.sanitize(MatchModifier.MIRROR_LOADOUT.bit, GameMode.GUN_GAME));
    }

    @Test public void ammoRulesSurvivePurchasesAndRespawnAndApplyToBots() {
        Match m = match(GameMode.RANGE, MatchModifier.NO_RELOAD.bit | MatchModifier.INFINITE_AMMO.bit);
        try {
            for (Combatant c : m.combatants()) {
                assertTrue(c.weapon().noReload()); assertTrue(c.weapon().infiniteReserve());
            }
            InputState input = new InputState(); input.paused = true;
            input.queuePurchase(WeaponType.FAMAS_B.ordinal());
            m.update(0.04f, input);
            assertEquals(WeaponType.FAMAS_B, m.player().weaponType());
            assertTrue(m.player().weapon().noReload()); assertTrue(m.snapshot().noReload);
            m.applyDamage(m.player(), null, 1000, false, null);
            input.paused = false;
            for (int i = 0; i < 250; i++) m.update(Match.STEP, input);
            assertTrue(m.player().alive);
            for (var w : m.player().weapons) if (w != null && !w.type().melee()) {
                assertTrue(w.noReload()); assertTrue(w.infiniteReserve());
            }
        } finally { m.release(); }
    }

    @Test public void battlefieldMirrorUsesSelectedHumanGunAcrossEveryBotClass() {
        MatchConfig config = new MatchConfig(); config.mode = GameMode.BATTLE_FIELD;
        config.totalPlayers = 8; config.battlefieldClass = BattlefieldClass.RIFLEMAN;
        config.battlefieldPrimary = WeaponType.FAMAS_B; config.modifiers = MatchModifier.MIRROR_LOADOUT.bit;
        Match m = new Match(config);
        try {
            assertEquals(WeaponType.FAMAS_B, m.player().weaponType());
            for (Combatant c : m.combatants()) assertArrayEquals(m.player().kit, c.kit);
        } finally { m.release(); }
    }
}
