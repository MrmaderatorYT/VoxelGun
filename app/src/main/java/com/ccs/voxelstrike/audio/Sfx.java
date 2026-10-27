package com.ccs.voxelstrike.audio;

/** Indices into the procedural sound bank rendered by {@link NativeSynth}. */
public final class Sfx {
    public static final int RIFLE = 0;
    public static final int PISTOL = 1;
    public static final int SHOTGUN = 2;
    public static final int SNIPER = 3;
    public static final int SMG = 4;
    public static final int EXPLOSION = 5;
    public static final int STEP = 6;
    public static final int CLICK = 7;
    public static final int MAGAZINE = 8;
    public static final int HIT_MARKER = 9;
    public static final int HEADSHOT = 10;
    public static final int SWING = 11;
    public static final int STAB = 12;
    public static final int LAND = 13;
    public static final int WHIZZ = 14;
    public static final int DRY_FIRE = 15;
    public static final int UI = 16;
    public static final int ROUND_START = 17;
    public static final int DEATH = 18;
    public static final int PIN = 19;
    public static final int LMG = 20;

    public static final int ARMOR_HIT = 21;
    public static final int STEP_METAL = 22;
    public static final int STEP_WOOD = 23;
    public static final int STEP_SOFT = 24;
    public static final int MAG_INSERT = 25;
    public static final int BOLT = 26;
    public static final int HEARTBEAT = 27;
    public static final int ROUND_WIN = 28;
    public static final int ROUND_LOSS = 29;

    // Per-model reports. Keeping these separate from the legacy family sounds lets every weapon
    // have a recognisable acoustic signature without changing the positional-audio protocol.
    public static final int SHOT_P9 = 30;
    public static final int SHOT_TALON = 31;
    public static final int SHOT_AUTO9 = 32;
    public static final int SHOT_MAG5 = 33;
    public static final int SHOT_RANGER = 34;
    public static final int SHOT_SIDEWINDER = 35;

    public static final int SHOT_SPECTRE = 36;
    public static final int SHOT_HORNET = 37;
    public static final int SHOT_UZIK = 38;
    public static final int SHOT_MP7K = 39;
    public static final int SHOT_VECTOR9 = 40;

    public static final int SHOT_PUMPER = 41;
    public static final int SHOT_BREAKER12 = 42;
    public static final int SHOT_AUTOSLUG = 43;
    public static final int SHOT_DRUM12 = 44;

    public static final int SHOT_FAMAS_B = 45;
    public static final int SHOT_BULLPUP = 46;
    public static final int SHOT_AK15 = 47;
    public static final int SHOT_VEKTOR_AR = 48;
    public static final int SHOT_FALCON = 49;
    public static final int SHOT_SCAR_H = 50;

    public static final int SHOT_SCOUT_V = 51;
    public static final int SHOT_DRAGUNOV_S = 52;
    public static final int SHOT_AWM_X = 53;
    public static final int SHOT_BARRETT_M = 54;

    public static final int SHOT_BULLDOG_LMG = 55;
    public static final int SHOT_NEGEV_M = 56;
    public static final int SHOT_RPG_V = 57;

    public static final int FIRST_WEAPON_SHOT = SHOT_P9;
    public static final int LAST_WEAPON_SHOT = SHOT_RPG_V;
    public static final int COUNT = LAST_WEAPON_SHOT + 1;

    public static boolean weaponShot(int kind) {
        return kind >= FIRST_WEAPON_SHOT && kind <= LAST_WEAPON_SHOT;
    }

    public static boolean gunshot(int kind) {
        return kind == RIFLE || kind == PISTOL || kind == SHOTGUN || kind == SNIPER
                || kind == SMG || kind == LMG || kind == EXPLOSION || weaponShot(kind);
    }

    public static int footstep(int material) {
        switch (material) {
            case com.ccs.voxelstrike.voxel.Materials.STEEL:
            case com.ccs.voxelstrike.voxel.Materials.GRATE: return STEP_METAL;
            case com.ccs.voxelstrike.voxel.Materials.WOOD:
            case com.ccs.voxelstrike.voxel.Materials.WOOD_DARK:
            case com.ccs.voxelstrike.voxel.Materials.WOOD_LIGHT:
            case com.ccs.voxelstrike.voxel.Materials.DECK_WOOD:
            case com.ccs.voxelstrike.voxel.Materials.DECK_WOOD_ALT: return STEP_WOOD;
            case com.ccs.voxelstrike.voxel.Materials.SAND:
            case com.ccs.voxelstrike.voxel.Materials.SAND_LIGHT:
            case com.ccs.voxelstrike.voxel.Materials.GRASS:
            case com.ccs.voxelstrike.voxel.Materials.GRASS_BRIGHT: return STEP_SOFT;
            default: return STEP;
        }
    }

    private Sfx() {}
}
