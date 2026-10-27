package com.ccs.voxelstrike.app;

import android.content.SharedPreferences;
import com.ccs.voxelstrike.game.GameMode;
import com.ccs.voxelstrike.game.MatchResult;
import com.ccs.voxelstrike.weapon.WeaponType;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

/** Exercise the actual profile transaction and a fresh load, without touching a real player. */
public class CareerProgressionTest {
    private final Map<String, Object> saved = new HashMap<>();
    private final SharedPreferences prefs = (SharedPreferences) Proxy.newProxyInstance(
            SharedPreferences.class.getClassLoader(), new Class<?>[]{SharedPreferences.class},
            (proxy, method, args) -> {
                if (method.getName().startsWith("get") && args != null && args.length == 2)
                    return saved.getOrDefault(args[0], args[1]);
                if (method.getName().equals("edit")) return editor();
                if (method.getName().equals("getAll")) return new HashMap<>(saved);
                if (method.getName().equals("contains")) return saved.containsKey(args[0]);
                throw new UnsupportedOperationException(method.getName());
            });

    private SharedPreferences.Editor editor() {
        Map<String, Object> pending = new HashMap<>();
        boolean[] clear = {false};
        return (SharedPreferences.Editor) Proxy.newProxyInstance(
                SharedPreferences.Editor.class.getClassLoader(), new Class<?>[]{SharedPreferences.Editor.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.startsWith("put")) { pending.put((String) args[0], args[1]); return proxy; }
                    if (name.equals("clear")) { clear[0] = true; return proxy; }
                    if (name.equals("apply") || name.equals("commit")) {
                        if (clear[0]) saved.clear();
                        saved.putAll(pending);
                        return name.equals("commit") ? true : null;
                    }
                    throw new UnsupportedOperationException(name);
                });
    }

    private MatchResult match(GameMode mode, int kills) {
        MatchResult result = new MatchResult();
        result.mode = mode;
        result.playerKills = kills;
        result.weaponKills = new int[WeaponType.values().length];
        result.weaponKills[WeaponType.AK15.ordinal()] = kills;
        return result;
    }

    @Test public void crossingGoalAndMasteryGrantsBothExactlyOnceAndSurvivesReload() {
        Profile profile = new Profile(prefs);
        CareerProgression.Update first = profile.completeMatch(match(GameMode.DEATHMATCH, 25));
        assertEquals(200, first.coins());
        assertEquals(300, first.xp());
        assertEquals(2, first.awards().size());
        Profile loaded = new Profile(prefs);
        assertEquals(1400, loaded.coins());
        assertEquals(25, loaded.weaponStats(WeaponType.AK15)[2]);
        assertEquals(25, loaded.goalProgress(CareerProgression.GOALS.get(0)));
        assertEquals(0, loaded.completeMatch(match(GameMode.DEATHMATCH, 1)).coins());
        assertEquals(26, new Profile(prefs).weaponStats(WeaponType.AK15)[2]);
    }

    @Test public void existingWeaponHistoryAwardsAllEarnedTiersOnceAndUnlocksPaint() {
        saved.put("wpn_AK15_kills", 249);
        Profile profile = new Profile(prefs);
        CareerProgression.Update update = profile.completeMatch(match(GameMode.DEATHMATCH, 1));
        assertEquals(850, update.coins());
        assertEquals(1350, update.xp());
        assertEquals(3, update.awards().size());
        assertEquals(Boolean.TRUE, saved.get("owned_skin_gold"));
        assertTrue(profile.owns(java.util.Arrays.stream(Cosmetics.ITEMS)
                .filter(item -> item.id().equals("skin_gold")).findFirst().orElseThrow()));
        assertEquals(0, new Profile(prefs).completeMatch(match(GameMode.DEATHMATCH, 0)).coins());
        assertEquals(2, update.levelsGained());
    }

    @Test public void practiceCannotChangeCareerOrClaimHistoricalMastery() {
        saved.put("wpn_AK15_kills", 100);
        Profile profile = new Profile(prefs);
        Map<String, Object> before = new HashMap<>(saved);
        for (GameMode mode : new GameMode[]{GameMode.RANGE, GameMode.MOVEMENT_TRAINING}) {
            MatchResult result = match(mode, 250);
            result.victory = true;
            result.coinsEarned = 500;
            result.experienceEarned = 500;
            assertEquals(0, profile.completeMatch(result).coins());
        }
        assertEquals(before, saved);
        assertEquals(0, profile.matches());
        assertEquals(1200, profile.coins());
    }

    @Test public void drawsAndDeathmatchVictoriesDoNotCountAsObjectiveWins() {
        Profile profile = new Profile(prefs);
        MatchResult draw = match(GameMode.BOMB_PLANT, 0);
        draw.draw = draw.victory = true;
        profile.completeMatch(draw);
        assertEquals(0, profile.wins());
        MatchResult win = match(GameMode.DEATHMATCH, 0);
        win.victory = true;
        profile.completeMatch(win);
        assertEquals(0, profile.goalProgress(CareerProgression.GOALS.get(3)));
        for (int i = 0; i < 3; i++) {
            MatchResult objective = match(GameMode.CAPTURE_POINT, 0);
            objective.victory = true;
            profile.completeMatch(objective);
        }
        assertEquals(3, profile.goalProgress(CareerProgression.GOALS.get(3)));
        assertEquals(Boolean.TRUE, saved.get("owned_glove_mint"));
    }

    @Test public void explorerNeedsDistinctCombatModes() {
        Profile profile = new Profile(prefs);
        for (int i = 0; i < 5; i++) profile.completeMatch(match(GameMode.DEATHMATCH, 0));
        assertEquals(1, profile.goalProgress(CareerProgression.GOALS.get(4)));
        profile.completeMatch(match(GameMode.TEAM_DEATHMATCH, 0));
        profile.completeMatch(match(GameMode.GUN_GAME, 0));
        assertFalse(saved.containsKey("owned_charm_core"));
        assertEquals(200, profile.completeMatch(match(GameMode.KNIFE_FIGHT, 0)).coins());
        assertEquals(Boolean.TRUE, saved.get("owned_charm_core"));
    }

    @Test public void survivalUsesHighestZombieWaveInsteadOfAddingWaves() {
        Profile profile = new Profile(prefs);
        MatchResult result = match(GameMode.ZOMBIE, 0);
        result.wave = 6;
        profile.completeMatch(result);
        profile.completeMatch(result);
        assertEquals(6, profile.goalProgress(CareerProgression.GOALS.get(5)));
        result.mode = GameMode.DEATHMATCH;
        result.wave = 99;
        profile.completeMatch(result);
        assertEquals(6, profile.goalProgress(CareerProgression.GOALS.get(5)));
        result.mode = GameMode.ZOMBIE;
        result.wave = 10;
        assertEquals(300, profile.completeMatch(result).coins());
        assertEquals(Boolean.TRUE, saved.get("owned_skin_arctic"));
    }

    @Test public void absentOrShortWeaponTalliesDoNotBreakGoalsOrLoseKills() {
        Profile profile = new Profile(prefs);
        MatchResult result = match(GameMode.DEATHMATCH, 25);
        result.weaponKills = new int[0];
        result.weaponShots = null;
        result.headshots = 20;
        CareerProgression.Update update = profile.completeMatch(result);
        assertEquals(250, update.coins());
        assertEquals(25, new Profile(prefs).kills());
        assertEquals(20, new Profile(prefs).headshots());
    }

    @Test public void resetClearsGoalsAndUnlocksAlongWithCareer() {
        Profile profile = new Profile(prefs);
        profile.completeMatch(match(GameMode.DEATHMATCH, 250));
        profile.reset();
        assertEquals(0, profile.goalProgress(CareerProgression.GOALS.get(0)));
        assertFalse(saved.containsKey("owned_skin_gold"));
        assertEquals(0, profile.weaponStats(WeaponType.AK15)[2]);
        assertEquals(1200, profile.coins());
    }
}
