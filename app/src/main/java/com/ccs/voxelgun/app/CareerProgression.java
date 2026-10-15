package com.ccs.voxelgun.app;

import android.content.SharedPreferences;

import com.ccs.voxelgun.R;
import com.ccs.voxelgun.game.GameMode;
import com.ccs.voxelgun.game.MatchResult;
import com.ccs.voxelgun.weapon.WeaponType;

import java.util.ArrayList;
import java.util.List;

/** Permanent offline goals. Stable preference keys keep progress across catalog changes. */
public final class CareerProgression {
    public record Goal(String id, int title, int target, int coins, int xp, String cosmetic) {}
    public static final List<Goal> GOALS = java.util.Collections.unmodifiableList(java.util.Arrays.asList(
            new Goal("kills", R.string.goal_kills, 25, 100, 150, null),
            new Goal("headshots", R.string.goal_headshots, 20, 150, 250, null),
            new Goal("wins", R.string.goal_wins, 5, 250, 400, null),
            new Goal("objectives", R.string.goal_objectives, 3, 300, 500, "glove_mint"),
            new Goal("modes", R.string.goal_modes, 4, 200, 300, "charm_core"),
            new Goal("wave", R.string.goal_wave, 10, 300, 500, "skin_arctic")));
    private static final int[] MASTERY_KILLS = {25, 100, 250};
    private static final int[] MASTERY_COINS = {100, 250, 500};
    private static final int[] MASTERY_XP = {150, 400, 800};

    public record Award(int title, WeaponType weapon, int tier, String cosmetic) {}
    public record Update(int coins, int xp, List<Award> awards, int levelsGained) {}

    private CareerProgression() {}

    public static boolean eligible(MatchResult result) {
        return result.mode != null && result.mode != GameMode.RANGE
                && result.mode != GameMode.MOVEMENT_TRAINING;
    }

    public static int cosmeticName(String id) {
        return switch (id) {
            case "glove_mint" -> R.string.career_reward_mint;
            case "charm_core" -> R.string.career_reward_core;
            case "skin_arctic" -> R.string.career_reward_arctic;
            case "skin_gold" -> R.string.career_reward_gold;
            default -> throw new IllegalArgumentException("Unknown career cosmetic: " + id);
        };
    }

    public static int tally(int[] values, int index) {
        return values == null || index >= values.length ? 0 : Math.max(0, values[index]);
    }

    public static int masteryTier(int kills) {
        int tier = 0;
        for (int threshold : MASTERY_KILLS) if (kills >= threshold) tier++;
        return tier;
    }

    /** Threshold for the next tier, or the final threshold when all tiers are complete. */
    public static int masteryTarget(int kills) {
        return MASTERY_KILLS[Math.min(masteryTier(kills), MASTERY_KILLS.length - 1)];
    }

    public static int progress(SharedPreferences preferences, Goal goal) {
        return Math.min(goal.target(), Math.max(0, preferences.getInt("goal_" + goal.id(), 0)));
    }

    /** Stages counters and one-time reward flags in the same transaction as the match totals. */
    static Update advance(SharedPreferences preferences, SharedPreferences.Editor editor,
                          MatchResult result) {
        if (!eligible(result)) return new Update(0, 0, java.util.Collections.emptyList(), 0);
        int modes = 0;
        for (GameMode mode : GameMode.values()) {
            if (mode == result.mode || preferences.getBoolean("goal_mode_" + mode.name(), false)) modes++;
        }
        editor.putBoolean("goal_mode_" + result.mode.name(), true);
        boolean win = result.victory && !result.draw;
        int[] increments = {Math.max(0, result.playerKills), Math.max(0, result.headshots),
                win ? 1 : 0, win && result.mode.objective() ? 1 : 0, modes, result.mode == GameMode.ZOMBIE ? Math.max(0, result.wave) : 0};
        List<Award> awards = new ArrayList<>();
        int coins = 0;
        int xp = 0;
        for (int i = 0; i < GOALS.size(); i++) {
            Goal goal = GOALS.get(i);
            int before = progress(preferences, goal);
            int after = Math.min(goal.target(), i >= 4 ? Math.max(before, increments[i])
                    : before + Math.min(goal.target(), increments[i]));
            editor.putInt("goal_" + goal.id(), after);
            String claimed = "goal_reward_" + goal.id();
            if (after >= goal.target() && !preferences.getBoolean(claimed, false)) {
                editor.putBoolean(claimed, true);
                if (goal.cosmetic() != null) editor.putBoolean("owned_" + goal.cosmetic(), true);
                coins += goal.coins();
                xp += goal.xp();
                awards.add(new Award(goal.title(), null, 0, goal.cosmetic()));
            }
        }
        for (WeaponType weapon : WeaponType.values()) {
            int kills = preferences.getInt("wpn_" + weapon.name() + "_kills", 0);
            int added = tally(result.weaponKills, weapon.ordinal());
            // Existing career stats count towards mastery, awarded on the next combat match.
            int tier = masteryTier((int) Math.min(Integer.MAX_VALUE, (long) kills + added));
            for (int i = 0; i < tier; i++) {
                String key = "mastery_reward_" + weapon.name() + "_" + (i + 1);
                if (preferences.getBoolean(key, false)) continue;
                editor.putBoolean(key, true);
                coins += MASTERY_COINS[i];
                xp += MASTERY_XP[i];
                String cosmetic = i == 2 ? "skin_gold" : null;
                if (cosmetic != null) editor.putBoolean("owned_" + cosmetic, true);
                awards.add(new Award(R.string.mastery_award, weapon, i + 1, cosmetic));
            }
        }
        return new Update(coins, xp, java.util.Collections.unmodifiableList(awards), 0);
    }
}
