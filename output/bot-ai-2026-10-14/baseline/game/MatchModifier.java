package com.ccs.voxelgun.game;

/** Stable bit values persisted independently of menu ordering. */
public enum MatchModifier {
    LOW_GRAVITY(1, "Low gravity"), HEADSHOTS_ONLY(2, "Headshots only"),
    EXPLOSIVE_ROUNDS(4, "Explosive rounds"), DOUBLE_SPEED(8, "Double speed"),
    FOG(16, "Heavy fog"), NIGHT(32, "Night"), RANDOM_WEAPONS(64, "Random weapon on respawn"),
    ONE_SHOT_RAIL(128, "One-shot rail"), BUNNY_HOP(256, "Bhop / Slide hop"),
    NO_FALL_DAMAGE(512, "No Fall Damage"), DOUBLE_JUMP(1024, "Double Jump"),
    VAMPIRE(2048, "Vampire"), INFINITE_AMMO(4096, "Infinite Ammo"),
    NO_RELOAD(8192, "No Reload"), EXPLOSIVE_DEATH(16384, "Explosive Death"),
    MIRROR_LOADOUT(32768, "Mirror Loadout"), HARDCORE_HUD(65536, "Hardcore HUD"),
    NO_BOTS(131072, "No Bots / Solo exploration");

    public final int bit;
    public final String label;
    MatchModifier(int bit, String label) { this.bit = bit; this.label = label; }

    public boolean supports(GameMode mode) {
        if (this == MIRROR_LOADOUT) {
            return !mode.knifeOnly() && !mode.survival() && mode != GameMode.GUN_GAME;
        }
        if (this == INFINITE_AMMO || this == NO_RELOAD) return !mode.knifeOnly();
        if (mode.battlefield() && (this == RANDOM_WEAPONS || this == ONE_SHOT_RAIL)) {
            return false;  // the three player-selected weapon slots define this mode
        }
        if (this == HEADSHOTS_ONLY || this == EXPLOSIVE_ROUNDS || this == ONE_SHOT_RAIL) {
            return !mode.knifeOnly() && !mode.survival() && mode != GameMode.GUN_GAME;
        }
        return this != RANDOM_WEAPONS || (!mode.knifeOnly() && !mode.survival()
                && mode != GameMode.GUN_GAME && mode != GameMode.BOMB_PLANT);
    }

    public static int sanitize(int mask, GameMode mode) {
        int result = 0;
        for (MatchModifier value : values()) if (value.supports(mode)) result |= mask & value.bit;
        if ((result & ONE_SHOT_RAIL.bit) != 0) result &= ~RANDOM_WEAPONS.bit;
        if ((result & MIRROR_LOADOUT.bit) != 0) result &= ~RANDOM_WEAPONS.bit;
        return result;
    }
}
