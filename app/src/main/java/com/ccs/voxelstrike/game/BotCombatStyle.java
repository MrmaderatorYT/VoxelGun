package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.weapon.WeaponSlot;
import com.ccs.voxelstrike.weapon.WeaponType;

/** Stable personalities, independent of the sidearm currently held or a transient movement state. */
public enum BotCombatStyle {
    RUSHER(.84f, .52f, 1.10f, .92f),
    CAUTIOUS(1.10f, 1.22f, .94f, 1.06f),
    SNIPER(1f, 1.08f, .95f, 1.02f);

    private final float rangeFactor, holdFactor, advanceFactor, reactionFactor;

    BotCombatStyle(float rangeFactor, float holdFactor, float advanceFactor, float reactionFactor) {
        this.rangeFactor = rangeFactor;
        this.holdFactor = holdFactor;
        this.advanceFactor = advanceFactor;
        this.reactionFactor = reactionFactor;
    }

    public float rangeFactor() { return rangeFactor; }
    public float holdFactor() { return holdFactor; }
    public float advanceFactor() { return advanceFactor; }
    public float reactionFactor() { return reactionFactor; }

    /** Kit, rather than selected slot, prevents a sniper changing personality when drawing a pistol. */
    public static BotCombatStyle forCombatant(Combatant combatant) {
        WeaponType primary = combatant.kit[WeaponSlot.PRIMARY.ordinal()];
        if (primary == null) primary = combatant.weaponType();
        switch (primary.weaponClass()) {
            case SNIPER: return SNIPER;
            case SHOTGUN:
            case SMG:
            case MELEE: return RUSHER;
            case LMG:
            case LAUNCHER: return CAUTIOUS;
            // Team ids alternate: vary within each team rather than making one side all rushers.
            default: return (combatant.id / 2 & 1) == 0 ? RUSHER : CAUTIOUS;
        }
    }
}
