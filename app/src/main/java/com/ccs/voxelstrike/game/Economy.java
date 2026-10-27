package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.weapon.WeaponClass;
import com.ccs.voxelstrike.weapon.WeaponType;

/**
 * Round money: what a kill pays and what the buy menu costs against it.
 *
 * <p>This is the currency of a single match and is thrown away with it. The profile's coins are a
 * separate, persistent thing and buy nothing here.
 *
 * <p>Rewards go up as the weapon gets harder to kill with, so pushing with a knife or a shotgun
 * funds the rifle you actually want.
 */
public final class Economy {
    /** What everyone has in hand at the first spawn: a pistol plus change toward an SMG. */
    public static final int STARTING_MONEY = 1600;
    public static final int MAX_MONEY = 16000;
    /** Paid on death so a losing player is never stuck on a pistol with nothing to buy. */
    public static final int DEATH_CONSOLATION = 350;
    /** Seconds after a spawn during which buying stays open; it is always open while dead. */
    public static final float BUY_WINDOW_SECONDS = 20f;
    public static final int HEADSHOT_BONUS = 50;

    private Economy() {}

    /** Kill reward for the weapon that landed it. */
    public static int killReward(WeaponType weapon) {
        if (weapon == null) {
            return 0;
        }
        WeaponClass family = weapon.weaponClass();
        switch (family) {
            case MELEE:
                return 900;
            case SHOTGUN:
                return 550;
            case SMG:
                return 450;
            case GRENADE:
                return 400;
            case PISTOL:
                return 350;
            case LMG:
            case LAUNCHER:
                return 250;
            case RIFLE:
            case SNIPER:
            default:
                return 300;
        }
    }

    public static int clamp(int money) {
        return Math.max(0, Math.min(MAX_MONEY, money));
    }
}
