package com.ccs.voxelgun.game;

import com.ccs.voxelgun.weapon.WeaponClass;
import com.ccs.voxelgun.weapon.WeaponType;

/** Validation and the deliberately small weapon pools used by Battle Field setup. */
public final class BattlefieldRules {
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 100;
    public static final int DEFAULT_PLAYERS = 100;

    public static final int MIN_SCORE = 25;
    public static final int MAX_SCORE = 1000;
    public static final int DEFAULT_SCORE = 250;

    public static final int MIN_TIME_MINUTES = 5;
    public static final int MAX_TIME_MINUTES = 60;
    public static final int DEFAULT_TIME_MINUTES = 15;
    public static final float REGENERATION_DELAY_SECONDS = 15f;

    public static final WeaponType[] RIFLES = {
            WeaponType.FAMAS_B, WeaponType.BULLPUP, WeaponType.AK15,
            WeaponType.VEKTOR_AR, WeaponType.FALCON, WeaponType.SCAR_H
    };
    public static final WeaponType[] SNIPERS = {
            WeaponType.SCOUT_V, WeaponType.DRAGUNOV_S, WeaponType.AWM_X, WeaponType.BARRETT_M
    };
    public static final WeaponType[] MACHINE_GUNS = {
            WeaponType.BULLDOG_LMG, WeaponType.NEGEV_M
    };

    private BattlefieldRules() {}

    /** Clamps to 2..100 and always returns an even number so both teams are the same size. */
    public static int sanitizeTotalPlayers(int players) {
        int clamped = Math.max(MIN_PLAYERS, Math.min(MAX_PLAYERS, players));
        if ((clamped & 1) != 0) {
            clamped += clamped == MAX_PLAYERS ? -1 : 1;
        }
        return clamped;
    }

    public static int sanitizeScoreLimit(int score) {
        return Math.max(MIN_SCORE, Math.min(MAX_SCORE, score));
    }

    public static float sanitizeTimeSeconds(float seconds) {
        return Math.max(MIN_TIME_MINUTES * 60f,
                Math.min(MAX_TIME_MINUTES * 60f, seconds));
    }

    public static BattlefieldClass sanitizeClass(BattlefieldClass role) {
        return role == null ? BattlefieldClass.RIFLEMAN : role;
    }

    public static WeaponType sanitizeRifle(WeaponType type) {
        return type != null && type.weaponClass() == WeaponClass.RIFLE ? type : WeaponType.AK15;
    }

    public static WeaponType sanitizeSniper(WeaponType type) {
        return type != null && type.weaponClass() == WeaponClass.SNIPER ? type : WeaponType.AWM_X;
    }

    public static WeaponType sanitizeMachineGun(WeaponType type) {
        return type != null && type.weaponClass() == WeaponClass.LMG
                ? type : WeaponType.NEGEV_M;
    }

    /** Selects the single long gun allowed by the chosen weapon class. */
    public static WeaponType weaponForClass(BattlefieldClass role, WeaponType rifle,
                                            WeaponType sniper, WeaponType machineGun) {
        BattlefieldClass safeRole = sanitizeClass(role);
        return switch (safeRole) {
            case SNIPER -> sanitizeSniper(sniper);
            case MACHINE_GUNNER -> sanitizeMachineGun(machineGun);
            case RIFLEMAN -> sanitizeRifle(rifle);
        };
    }

    public static WeaponType[] weaponsForClass(BattlefieldClass role) {
        return switch (sanitizeClass(role)) {
            case SNIPER -> SNIPERS;
            case MACHINE_GUNNER -> MACHINE_GUNS;
            case RIFLEMAN -> RIFLES;
        };
    }
}
