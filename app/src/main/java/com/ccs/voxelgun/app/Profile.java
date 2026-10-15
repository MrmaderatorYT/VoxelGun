package com.ccs.voxelgun.app;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persistent player progression: coins, level and lifetime stats.
 *
 * <p>No weapons live here. Guns are taken from the buy menu inside a round and die with it; coins
 * are the persistent currency and are reserved for skins.
 */
public final class Profile {
    private static final String FILE = "voxelgun_profile";
    private static final String KEY_COINS = "coins";
    private static final String KEY_XP = "xp";
    private static final String KEY_KILLS = "kills";
    private static final String KEY_DEATHS = "deaths";
    private static final String KEY_MATCHES = "matches";
    private static final String KEY_WINS = "wins";
    private static final String KEY_HEADSHOTS = "headshots";
    private static final String KEY_PLAYTIME = "playtime";
    private static final String KEY_NAME = "name";

    private final SharedPreferences preferences;

    private int coins;
    private int experience;
    private int kills;
    private int deaths;
    private int matches;
    private int wins;
    private int headshots;
    private long playtimeSeconds;
    private String name;

    public Profile(Context context) {
        this(context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE));
    }

    Profile(SharedPreferences preferences) {
        this.preferences = preferences;
        load();
    }

    private void load() {
        coins = preferences.getInt(KEY_COINS, 1200);
        experience = preferences.getInt(KEY_XP, 0);
        kills = preferences.getInt(KEY_KILLS, 0);
        deaths = preferences.getInt(KEY_DEATHS, 0);
        matches = preferences.getInt(KEY_MATCHES, 0);
        wins = preferences.getInt(KEY_WINS, 0);
        headshots = preferences.getInt(KEY_HEADSHOTS, 0);
        playtimeSeconds = preferences.getLong(KEY_PLAYTIME, 0L);
        name = preferences.getString(KEY_NAME, "Player");
    }

    private void save() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(KEY_COINS, coins);
        editor.putInt(KEY_XP, experience);
        editor.putInt(KEY_KILLS, kills);
        editor.putInt(KEY_DEATHS, deaths);
        editor.putInt(KEY_MATCHES, matches);
        editor.putInt(KEY_WINS, wins);
        editor.putInt(KEY_HEADSHOTS, headshots);
        editor.putLong(KEY_PLAYTIME, playtimeSeconds);
        editor.putString(KEY_NAME, name);
        editor.apply();
    }

    public String name() {
        return name;
    }

    public void setName(String value) {
        name = value == null || value.trim().isEmpty() ? "Player" : value.trim();
        save();
    }

    public int coins() {
        return coins;
    }

    public int experience() {
        return experience;
    }

    public int kills() {
        return kills;
    }

    public int deaths() {
        return deaths;
    }

    public int matches() {
        return matches;
    }

    public int wins() {
        return wins;
    }

    public int headshots() {
        return headshots;
    }

    public long playtimeSeconds() {
        return playtimeSeconds;
    }

    public float killDeathRatio() {
        return deaths == 0 ? kills : (float) kills / deaths;
    }

    /** Levels get progressively more expensive; level 1 starts at zero experience. */
    public int level() {
        int level = 1;
        int required = 0;
        while (level < 60) {
            required += experienceForLevel(level);
            if (experience < required) {
                break;
            }
            level++;
        }
        return level;
    }

    public static int experienceForLevel(int level) {
        return 320 + level * 180;
    }

    /** Progress through the current level in the 0..1 range. */
    public float levelProgress() {
        int level = level();
        int consumed = 0;
        for (int i = 1; i < level; i++) {
            consumed += experienceForLevel(i);
        }
        int needed = experienceForLevel(level);
        return Math.max(0f, Math.min(1f, (experience - consumed) / (float) needed));
    }

    public int experienceIntoLevel() {
        int level = level();
        int consumed = 0;
        for (int i = 1; i < level; i++) {
            consumed += experienceForLevel(i);
        }
        return experience - consumed;
    }

    /** Applies a finished match and returns the number of levels gained. */
    public int applyMatch(int coinsEarned, int experienceEarned, int matchKills, int matchDeaths,
                          int matchHeadshots, boolean victory, float durationSeconds) {
        int before = level();
        coins += Math.max(0, coinsEarned);
        experience += Math.max(0, experienceEarned);
        kills += matchKills;
        deaths += matchDeaths;
        headshots += matchHeadshots;
        matches++;
        if (victory) {
            wins++;
        }
        playtimeSeconds += (long) durationSeconds;
        save();
        return level() - before;
    }

    /**
     * Paint chosen for one weapon. Paints are bought once and can then be applied to any gun, so
     * ownership stays per colour while the choice is per weapon.
     */
    public int equippedSkin(com.ccs.voxelgun.weapon.WeaponType type) {
        int value = preferences.getInt("skin_" + type.name(),
                preferences.getInt("equipped_" + Cosmetics.Category.SKIN.name(), 0));
        return ownsSkin(value) ? value : 0;
    }

    /** Applies a paint to one weapon, buying it first if the player does not own it yet. */
    public boolean equipSkin(com.ccs.voxelgun.weapon.WeaponType type, Cosmetics.Item item) {
        if (!owns(item)) {
            if (coins < item.cost()) {
                return false;
            }
            coins -= item.cost();
        }
        preferences.edit().putBoolean("owned_" + item.id(), true)
                .putInt("skin_" + type.name(), item.value())
                .putInt(KEY_COINS, coins).apply();
        return true;
    }

    /** The paint choice for every weapon, ready to hand to a match. */
    public int[] weaponSkins() {
        com.ccs.voxelgun.weapon.WeaponType[] types = com.ccs.voxelgun.weapon.WeaponType.values();
        int[] skins = new int[types.length];
        for (int i = 0; i < types.length; i++) {
            skins[i] = equippedSkin(types[i]);
        }
        return skins;
    }

    private boolean ownsSkin(int value) {
        for (Cosmetics.Item item : Cosmetics.ITEMS) {
            if (item.category() == Cosmetics.Category.SKIN && item.value() == value) {
                return owns(item);
            }
        }
        return false;
    }

    /**
     * Merges a match's per-weapon tallies into the career totals. Keys are the enum names rather
     * than ordinals, so reordering or inserting a weapon never rewrites someone's history onto
     * the wrong gun.
     */
    public void applyWeaponStats(com.ccs.voxelgun.game.MatchResult result) {
        if (result.weaponShots == null) {
            return;
        }
        SharedPreferences.Editor editor = preferences.edit();
        com.ccs.voxelgun.weapon.WeaponType[] types = com.ccs.voxelgun.weapon.WeaponType.values();
        for (int i = 0; i < types.length; i++) {
            if (result.weaponShots[i] == 0 && result.weaponKills[i] == 0) {
                continue;
            }
            String key = "wpn_" + types[i].name() + "_";
            editor.putInt(key + "shots", preferences.getInt(key + "shots", 0) + result.weaponShots[i]);
            editor.putInt(key + "hits", preferences.getInt(key + "hits", 0) + result.weaponHits[i]);
            editor.putInt(key + "kills", preferences.getInt(key + "kills", 0) + result.weaponKills[i]);
            editor.putInt(key + "hs", preferences.getInt(key + "hs", 0) + result.weaponHeadshots[i]);
        }
        editor.apply();
    }

    /** Career totals for one weapon: shots, hits, kills, headshots. */
    public int[] weaponStats(com.ccs.voxelgun.weapon.WeaponType type) {
        String key = "wpn_" + type.name() + "_";
        return new int[] {
            preferences.getInt(key + "shots", 0),
            preferences.getInt(key + "hits", 0),
            preferences.getInt(key + "kills", 0),
            preferences.getInt(key + "hs", 0)
        };
    }

    /** Completes a combat match with career totals and rewards in one preference transaction. */
    public CareerProgression.Update completeMatch(com.ccs.voxelgun.game.MatchResult result) {
        if (!CareerProgression.eligible(result)) {
            return new CareerProgression.Update(0, 0, java.util.Collections.emptyList(), 0);
        }
        int before = level();
        SharedPreferences.Editor editor = preferences.edit();
        CareerProgression.Update update = CareerProgression.advance(preferences, editor, result);
        for (com.ccs.voxelgun.weapon.WeaponType type : com.ccs.voxelgun.weapon.WeaponType.values()) {
            String key = "wpn_" + type.name() + "_";
            int i = type.ordinal();
            editor.putInt(key + "shots", preferences.getInt(key + "shots", 0)
                    + CareerProgression.tally(result.weaponShots, i));
            editor.putInt(key + "hits", preferences.getInt(key + "hits", 0)
                    + CareerProgression.tally(result.weaponHits, i));
            editor.putInt(key + "kills", preferences.getInt(key + "kills", 0)
                    + CareerProgression.tally(result.weaponKills, i));
            editor.putInt(key + "hs", preferences.getInt(key + "hs", 0)
                    + CareerProgression.tally(result.weaponHeadshots, i));
        }
        coins += Math.max(0, result.coinsEarned) + update.coins();
        experience += Math.max(0, result.experienceEarned) + update.xp();
        kills += Math.max(0, result.playerKills);
        deaths += Math.max(0, result.playerDeaths);
        headshots += Math.max(0, result.headshots);
        matches++;
        if (result.victory && !result.draw) wins++;
        playtimeSeconds += (long) Math.max(0, result.durationSeconds);
        editor.putInt(KEY_COINS, coins).putInt(KEY_XP, experience)
                .putInt(KEY_KILLS, kills).putInt(KEY_DEATHS, deaths)
                .putInt(KEY_HEADSHOTS, headshots).putInt(KEY_MATCHES, matches)
                .putInt(KEY_WINS, wins).putLong(KEY_PLAYTIME, playtimeSeconds).apply();
        recordBadge(result);
        return new CareerProgression.Update(update.coins(), update.xp(), update.awards(), level() - before);
    }

    public int goalProgress(CareerProgression.Goal goal) {
        return CareerProgression.progress(preferences, goal);
    }

    public void reset() {
        preferences.edit().clear().apply();
        load();
    }

    public boolean owns(Cosmetics.Item item) {
        return item.cost() == 0 || preferences.getBoolean("owned_" + item.id(), false);
    }

    public int equipped(Cosmetics.Category category) {
        int value = preferences.getInt("equipped_" + category.name(), 0);
        for (Cosmetics.Item item : Cosmetics.ITEMS)
            if (item.category() == category && item.value() == value && owns(item)) return value;
        return 0;
    }

    public boolean equip(Cosmetics.Item item) {
        if (!owns(item)) {
            if (coins < item.cost()) return false;
            coins -= item.cost();
        }
        preferences.edit().putBoolean("owned_" + item.id(), true)
                .putInt("equipped_" + item.category().name(), item.value())
                .putInt(KEY_COINS, coins).apply();
        return true;
    }

    public void recordBadge(com.ccs.voxelgun.game.MatchResult result) {
        if (result.victory && !result.draw && result.modifiers == 0 && result.difficulty != null)
            preferences.edit().putBoolean("badge_" + result.difficulty.name(), true).apply();
    }

    public boolean hasBadge(com.ccs.voxelgun.game.Difficulty difficulty) {
        return preferences.getBoolean("badge_" + difficulty.name(), false);
    }
}
