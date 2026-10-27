package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.weapon.WeaponClass;
import com.ccs.voxelstrike.weapon.WeaponType;
import org.junit.Test;

import static org.junit.Assert.*;

/** Kill badges describe the shot/death event, not whatever a fighter does afterwards. */
public class KillContextTest {
    private Combatant fighter(int id) {
        Combatant c = new Combatant(id, "Fighter " + id);
        c.grounded = true;
        return c;
    }

    @Test public void scopedAndNoScopeAreExclusiveForEveryWeapon() {
        Combatant attacker = fighter(0), victim = fighter(1);
        for (WeaponType type : WeaponType.values()) {
            boolean optic = type.weaponClass() == WeaponClass.SNIPER && type.scoped();
            attacker.aiming = false;
            int hip = KillContext.capture(attacker, victim, type, false);
            assertEquals(type.name(), optic ? KillContext.NO_SCOPE : 0, hip);
            attacker.aiming = true;
            int aimed = KillContext.capture(attacker, victim, type, false);
            assertEquals(type.name(), optic ? KillContext.SCOPED : 0, aimed);
            assertEquals(type.name(), 0, aimed & KillContext.NO_SCOPE);
            assertEquals(type.name(), 0, hip & KillContext.SCOPED);
        }
    }

    @Test public void airborneNoScopeHeadshotAndFlashBadgesCombine() {
        Combatant attacker = fighter(0), victim = fighter(1);
        attacker.grounded = false;
        attacker.flash = 0.9f;
        victim.flash = 0.6f;
        int flags = KillContext.capture(attacker, victim, WeaponType.AWM_X, true);
        assertEquals(KillContext.HEADSHOT | KillContext.NO_SCOPE | KillContext.AIRBORNE
                | KillContext.BLINDED | KillContext.VICTIM_BLINDED, flags);
        assertEquals(0, flags & (KillContext.SCOPED | KillContext.WALLBANG));
    }

    @Test public void flashThresholdIsInclusiveAndDoesNotFlagResidualFade() {
        Combatant attacker = fighter(0), victim = fighter(1);
        attacker.flash = KillContext.FLASH_THRESHOLD - 0.0001f;
        victim.flash = KillContext.FLASH_THRESHOLD - 0.0001f;
        assertEquals(0, KillContext.capture(attacker, victim, WeaponType.AK15, false));
        attacker.flash = KillContext.FLASH_THRESHOLD;
        assertEquals(KillContext.BLINDED,
                KillContext.capture(attacker, victim, WeaponType.AK15, false));
        victim.flash = KillContext.FLASH_THRESHOLD;
        assertEquals(KillContext.BLINDED | KillContext.VICTIM_BLINDED,
                KillContext.capture(attacker, victim, WeaponType.AK15, false));
    }

    @Test public void victimJumpingDoesNotMeanAttackerAirborne() {
        Combatant attacker = fighter(0), victim = fighter(1);
        victim.grounded = false;
        assertEquals(0, KillContext.capture(attacker, victim, WeaponType.KNIFE, false));
        attacker.grounded = false;
        victim.grounded = true;
        assertEquals(KillContext.AIRBORNE,
                KillContext.capture(attacker, victim, WeaponType.KNIFE, false));
    }

    @Test public void headshotDoesNotRequireOptics() {
        Combatant attacker = fighter(0), victim = fighter(1);
        assertEquals(KillContext.HEADSHOT,
                KillContext.capture(attacker, victim, WeaponType.P9, true));
        assertEquals(0, KillContext.capture(attacker, victim, WeaponType.P9, false));
    }

    @Test public void environmentHasNoShooterBadgesAndNullsAreSafe() {
        Combatant victim = fighter(1);
        victim.grounded = false;
        assertEquals(0, KillContext.shooterFlags(null, WeaponType.AWM_X));
        assertEquals(0, KillContext.capture(null, victim, WeaponType.AWM_X, false));
        assertEquals(0, KillContext.capture(null, null, null, false));
        assertEquals(KillContext.HEADSHOT, KillContext.capture(null, null, null, true));
    }

    @Test public void completionPreservesLaunchContextAndUsesVictimFlashAtImpact() {
        Combatant attacker = fighter(0), victim = fighter(1);
        attacker.grounded = false;
        attacker.flash = 1f;
        int shotFlags = KillContext.shooterFlags(attacker, WeaponType.RPG_V);
        assertEquals(KillContext.AIRBORNE | KillContext.BLINDED, shotFlags);
        // A projectile may arrive after its owner has landed, switched weapons, or recovered.
        attacker.grounded = true;
        attacker.aiming = true;
        attacker.flash = 0f;
        victim.flash = 0.8f;
        assertEquals(shotFlags | KillContext.VICTIM_BLINDED | KillContext.HEADSHOT,
                KillContext.complete(shotFlags, victim, true));
        assertEquals(KillContext.AIRBORNE | KillContext.BLINDED, shotFlags);
    }

    @Test public void wallbangAnnotationSurvivesCompletionWithoutInventingOtherBadges() {
        Combatant attacker = fighter(0), victim = fighter(1);
        attacker.aiming = true;
        int shotFlags = KillContext.shooterFlags(attacker, WeaponType.AWM_X)
                | KillContext.WALLBANG;
        assertEquals(KillContext.SCOPED | KillContext.WALLBANG,
                KillContext.complete(shotFlags, victim, false));
        assertEquals(shotFlags | KillContext.HEADSHOT,
                KillContext.complete(shotFlags, null, true));
    }
}
