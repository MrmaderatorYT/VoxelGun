package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.weapon.WeaponClass;
import com.ccs.voxelstrike.weapon.WeaponType;

/** Allocation-free event metadata. Shooter traits belong to launch time, victim traits to impact. */
public final class KillContext {
    public static final int HEADSHOT = 1;
    public static final int SCOPED = 1 << 1;
    public static final int NO_SCOPE = 1 << 2;
    public static final int AIRBORNE = 1 << 3;
    public static final int BLINDED = 1 << 4;
    public static final int VICTIM_BLINDED = 1 << 5;
    public static final int WALLBANG = 1 << 6;
    /** Ignore the almost-transparent tail of a flash, but count a visible flash overlay. */
    public static final float FLASH_THRESHOLD = 0.2f;

    private KillContext() { }

    public static int shooterFlags(Combatant attacker, WeaponType weapon) {
        if (attacker == null || weapon == null) return 0;
        int flags = 0;
        // Rifles without sniper optics must never award a "no-scope" badge.
        if (weapon.weaponClass() == WeaponClass.SNIPER && weapon.scoped()) {
            flags |= attacker.aiming ? SCOPED : NO_SCOPE;
        }
        if (!attacker.grounded) flags |= AIRBORNE;
        if (attacker.flash >= FLASH_THRESHOLD) flags |= BLINDED;
        return flags;
    }

    public static int complete(int shotFlags, Combatant victim, boolean headshot) {
        // Clear impact-only traits when a captured launch mask is reused for multiple victims.
        int flags = shotFlags & ~(HEADSHOT | VICTIM_BLINDED);
        if (headshot) flags |= HEADSHOT;
        if (victim != null && victim.flash >= FLASH_THRESHOLD) flags |= VICTIM_BLINDED;
        return flags;
    }

    public static int capture(Combatant attacker, Combatant victim, WeaponType weapon,
                              boolean headshot) {
        return complete(shooterFlags(attacker, weapon), victim, headshot);
    }
}
