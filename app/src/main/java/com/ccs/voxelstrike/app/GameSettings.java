package com.ccs.voxelstrike.app;

import android.content.Context;
import android.content.SharedPreferences;

import com.ccs.voxelstrike.game.BattlefieldClass;
import com.ccs.voxelstrike.game.BattlefieldRules;
import com.ccs.voxelstrike.game.Difficulty;
import com.ccs.voxelstrike.game.GameMode;
import com.ccs.voxelstrike.map.Maps;
import com.ccs.voxelstrike.render.RenderSettings;
import com.ccs.voxelstrike.weapon.WeaponType;

/** Persisted graphics, control and audio preferences. */
public final class GameSettings {
    private static final String FILE = "voxelstrike_settings";

    private final SharedPreferences preferences;

    public RenderSettings.Quality quality = RenderSettings.Quality.LOW;
    public boolean rtShadows;
    public boolean rtGlobalIllumination;
    public boolean rtReflections;
    public boolean denoiser;
    public boolean temporalAntiAliasing;
    public boolean bloom;
    public boolean adaptivePerformance = true;
    public float renderScale = 0.75f;
    public float traceScale = 0.5f;
    public float fieldOfView = 78f;
    /** Tone mapper contrast; above 1 deepens the shadows, which is the high contrast look. */
    public float contrast = 1f;
    /** Tone mapper exposure trim; below 1 pulls back a map that reads as too bright. */
    public float brightness = 1f;
    /** Relative display gamma; 1 preserves the original picture. */
    public float gamma = 1f;
    public boolean showFps;
    /** 0 means uncapped; otherwise 30, 60, 90 or 120. */
    public int frameRateCap = 60;

    public float lookSensitivity = 1f;
    public float adsSensitivity = 0.6f;
    /**
     * Optics are their own problem: at 4.6x a sniper's sight sweeps the whole map for a thumb
     * movement that barely nudges an iron sight, so scoped weapons get a multiplier of their own
     * on top of the ADS one.
     */
    public float scopeSensitivity = 0.4f;
    /** Off by default: the aim button brings up the sight, it does not pull the trigger. */
    public boolean autoFire;
    /** Tap the aim button to stay behind the sight instead of having to hold it. */
    public boolean aimToggle = true;
    /** Contextual jump over a one-block ledge while moving; off preserves manual movement. */
    public boolean autoJump;
    /** Draw the blue/red silhouette rim around combatants. */
    public boolean playerHighlights = true;
    public boolean southpaw;
    public boolean haptics = true;
    public float effectsVolume = 0.85f;

    public int lastMode = GameMode.TEAM_DEATHMATCH.ordinal();
    /** Active map kept for existing callers; the two pools below remember their own selections. */
    public int lastMap;
    public int lastArenaMap;
    public int lastBattlefieldMap = Maps.firstBattlefieldIndex();
    public int matchModifiers;
    public int lastDifficulty = Difficulty.REGULAR.ordinal();
    public int lastBotCount = 7;

    /** Battle Field keeps its larger match setup separate from the compact arena defaults. */
    public int battlefieldTotalPlayers = BattlefieldRules.DEFAULT_PLAYERS;
    public int battlefieldScoreLimit = BattlefieldRules.DEFAULT_SCORE;
    public float battlefieldTimeLimitSeconds = BattlefieldRules.DEFAULT_TIME_MINUTES * 60f;
    public boolean battlefieldHardcore;
    public boolean battlefieldConquest = true;
    public BattlefieldClass battlefieldClass = BattlefieldClass.RIFLEMAN;
    public WeaponType battlefieldPrimary = WeaponType.AK15;
    public WeaponType battlefieldSniper = WeaponType.AWM_X;
    public WeaponType battlefieldLmg = WeaponType.NEGEV_M;

    public GameSettings(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
        load();
    }

    private void load() {
        quality = RenderSettings.Quality.values()[clampOrdinal(
                preferences.getInt("quality", RenderSettings.Quality.LOW.ordinal()),
                RenderSettings.Quality.values().length)];
        RenderSettings defaults = new RenderSettings();
        defaults.applyPreset(quality);

        rtShadows = preferences.getBoolean("rt_shadows", defaults.rtShadows);
        rtGlobalIllumination = preferences.getBoolean("rt_gi", defaults.rtGlobalIllumination);
        rtReflections = preferences.getBoolean("rt_reflections", defaults.rtReflections);
        denoiser = preferences.getBoolean("denoiser", defaults.denoiser);
        temporalAntiAliasing = preferences.getBoolean("taa", defaults.temporalAntiAliasing);
        bloom = preferences.getBoolean("bloom", defaults.bloom);
        adaptivePerformance = preferences.getBoolean("adaptive_performance", true);
        renderScale = preferences.getFloat("render_scale", defaults.renderScale);
        traceScale = preferences.getFloat("trace_scale", defaults.traceScale);
        fieldOfView = preferences.getFloat("fov", defaults.fieldOfView);
        contrast = preferences.getFloat("contrast", 1f);
        brightness = preferences.getFloat("brightness", 1f);
        gamma = preferences.getFloat("gamma", 1f);
        gamma = Float.isFinite(gamma) ? Math.max(.5f, Math.min(2f, gamma)) : 1f;
        showFps = preferences.getBoolean("show_fps", false);
        frameRateCap = preferences.getInt("fps_cap", 60);
        RenderSettings validated = toRenderSettings();
        renderScale = validated.renderScale;
        traceScale = validated.traceScale;
        fieldOfView = validated.fieldOfView;
        contrast = validated.contrast;
        brightness = validated.brightness;
        gamma = validated.gamma;
        frameRateCap = validated.frameRateCap;

        lookSensitivity = preferences.getFloat("sensitivity", 1f);
        adsSensitivity = preferences.getFloat("ads_sensitivity", 0.6f);
        scopeSensitivity = preferences.getFloat("scope_sensitivity", 0.4f);
        // Versioned key: earlier builds defaulted this to true and saved it, so reading the old
        // key would keep auto firing on for anyone who had already played. Aiming should only aim.
        autoFire = preferences.getBoolean("auto_fire_v2", false);
        aimToggle = preferences.getBoolean("aim_toggle", true);
        autoJump = preferences.getBoolean("auto_jump", false);
        playerHighlights = preferences.getBoolean("player_highlights", true);
        southpaw = preferences.getBoolean("southpaw", false);
        haptics = preferences.getBoolean("haptics", true);
        effectsVolume = preferences.getFloat("volume", 0.85f);

        lastMode = preferences.getInt("last_mode", GameMode.TEAM_DEATHMATCH.ordinal());
        // Migrate the old single-map preference into whichever pool it belongs to. New installs
        // start on Dust Yard for arena modes and the first deployment map for Battle Field.
        int migratedMap = preferences.getInt("last_map", 0);
        int arenaFallback = isArenaMap(migratedMap) ? migratedMap : 0;
        int battlefieldFallback = Maps.isBattlefield(migratedMap) ? migratedMap
                : Maps.firstBattlefieldIndex();
        lastArenaMap = sanitizeArenaMap(
                preferences.getInt("last_arena_map", arenaFallback));
        lastBattlefieldMap = sanitizeBattlefieldMap(
                preferences.getInt("last_battlefield_map", battlefieldFallback));
        lastMap = lastMode == GameMode.BATTLE_FIELD.ordinal()
                ? lastBattlefieldMap : lastArenaMap;
        matchModifiers = preferences.getInt("match_modifiers", 0);
        lastDifficulty = preferences.getInt("last_difficulty", Difficulty.REGULAR.ordinal());
        lastBotCount = preferences.getInt("last_bots", 7);
        battlefieldTotalPlayers = BattlefieldRules.sanitizeTotalPlayers(
                preferences.getInt("battlefield_players", BattlefieldRules.DEFAULT_PLAYERS));
        battlefieldScoreLimit = BattlefieldRules.sanitizeScoreLimit(
                preferences.getInt("battlefield_score", BattlefieldRules.DEFAULT_SCORE));
        battlefieldTimeLimitSeconds = BattlefieldRules.sanitizeTimeSeconds(
                preferences.getFloat("battlefield_time_seconds",
                        BattlefieldRules.DEFAULT_TIME_MINUTES * 60f));
        battlefieldHardcore = preferences.getBoolean("battlefield_hardcore", false);
        battlefieldConquest = preferences.getBoolean("battlefield_conquest", true);
        battlefieldClass = BattlefieldClass.values()[clampOrdinal(
                preferences.getInt("battlefield_class", BattlefieldClass.RIFLEMAN.ordinal()),
                BattlefieldClass.values().length)];
        battlefieldPrimary = battlefieldWeapon("battlefield_primary", WeaponType.AK15,
                BattlefieldRules.RIFLES);
        battlefieldSniper = battlefieldWeapon("battlefield_sniper", WeaponType.AWM_X,
                BattlefieldRules.SNIPERS);
        battlefieldLmg = battlefieldWeapon("battlefield_lmg", WeaponType.NEGEV_M,
                BattlefieldRules.MACHINE_GUNS);
    }

    private static int clampOrdinal(int value, int count) {
        return value < 0 || value >= count ? 0 : value;
    }

    public void save() {
        syncMapSelections();
        battlefieldTotalPlayers = BattlefieldRules.sanitizeTotalPlayers(battlefieldTotalPlayers);
        battlefieldScoreLimit = BattlefieldRules.sanitizeScoreLimit(battlefieldScoreLimit);
        battlefieldTimeLimitSeconds = BattlefieldRules.sanitizeTimeSeconds(
                battlefieldTimeLimitSeconds);
        battlefieldClass = BattlefieldRules.sanitizeClass(battlefieldClass);
        battlefieldPrimary = BattlefieldRules.sanitizeRifle(battlefieldPrimary);
        battlefieldSniper = BattlefieldRules.sanitizeSniper(battlefieldSniper);
        battlefieldLmg = BattlefieldRules.sanitizeMachineGun(battlefieldLmg);
        preferences.edit()
                .putInt("quality", quality.ordinal())
                .putBoolean("rt_shadows", rtShadows)
                .putBoolean("rt_gi", rtGlobalIllumination)
                .putBoolean("rt_reflections", rtReflections)
                .putBoolean("denoiser", denoiser)
                .putBoolean("taa", temporalAntiAliasing)
                .putBoolean("bloom", bloom)
                .putBoolean("adaptive_performance", adaptivePerformance)
                .putFloat("render_scale", renderScale)
                .putFloat("trace_scale", traceScale)
                .putFloat("fov", fieldOfView)
                .putFloat("contrast", contrast)
                .putFloat("brightness", brightness)
                .putFloat("gamma", gamma)
                .putBoolean("show_fps", showFps)
                .putInt("fps_cap", frameRateCap)
                .putFloat("sensitivity", lookSensitivity)
                .putFloat("ads_sensitivity", adsSensitivity)
                .putFloat("scope_sensitivity", scopeSensitivity)
                .remove("auto_fire")
                .putBoolean("auto_fire_v2", autoFire)
                .putBoolean("aim_toggle", aimToggle)
                .putBoolean("auto_jump", autoJump)
                .remove("bunny_hop") // obsolete personal setting; now part of match_modifiers
                .putBoolean("player_highlights", playerHighlights)
                .putBoolean("southpaw", southpaw)
                .putBoolean("haptics", haptics)
                .putFloat("volume", effectsVolume)
                .putInt("last_mode", lastMode)
                .putInt("last_map", lastMap)
                .putInt("last_arena_map", lastArenaMap)
                .putInt("last_battlefield_map", lastBattlefieldMap)
                .putInt("match_modifiers", matchModifiers)
                .putInt("last_difficulty", lastDifficulty)
                .putInt("last_bots", lastBotCount)
                .putInt("battlefield_players", battlefieldTotalPlayers)
                .putInt("battlefield_score", battlefieldScoreLimit)
                .putFloat("battlefield_time_seconds", battlefieldTimeLimitSeconds)
                .putBoolean("battlefield_hardcore", battlefieldHardcore)
                .putBoolean("battlefield_conquest", battlefieldConquest)
                .putInt("battlefield_class", battlefieldClass.ordinal())
                .putInt("battlefield_primary", battlefieldPrimary.ordinal())
                .putInt("battlefield_sniper", battlefieldSniper.ordinal())
                .putInt("battlefield_lmg", battlefieldLmg.ordinal())
                .apply();
    }

    /** Keeps the compatibility field pointed at the selection for the currently active mode. */
    private void syncMapSelections() {
        if (lastMode == GameMode.BATTLE_FIELD.ordinal()) {
            if (Maps.isBattlefield(lastMap)) {
                lastBattlefieldMap = lastMap;
            }
            lastBattlefieldMap = sanitizeBattlefieldMap(lastBattlefieldMap);
            lastArenaMap = sanitizeArenaMap(lastArenaMap);
            lastMap = lastBattlefieldMap;
        } else {
            if (isArenaMap(lastMap)) {
                lastArenaMap = lastMap;
            }
            lastArenaMap = sanitizeArenaMap(lastArenaMap);
            lastBattlefieldMap = sanitizeBattlefieldMap(lastBattlefieldMap);
            lastMap = lastArenaMap;
        }
    }

    private static boolean isArenaMap(int index) {
        return index >= 0 && index < Maps.count() && !Maps.isBattlefield(index);
    }

    private static int sanitizeArenaMap(int index) {
        return isArenaMap(index) ? index : 0;
    }

    private static int sanitizeBattlefieldMap(int index) {
        return Maps.isBattlefield(index) ? index : Maps.firstBattlefieldIndex();
    }

    /** Reads a persisted weapon only when it still belongs to the requested Battle Field family. */
    private WeaponType battlefieldWeapon(String key, WeaponType fallback, WeaponType[] allowed) {
        int ordinal = preferences.getInt(key, fallback.ordinal());
        if (ordinal < 0 || ordinal >= WeaponType.values().length) {
            return fallback;
        }
        WeaponType candidate = WeaponType.values()[ordinal];
        for (WeaponType type : allowed) {
            if (type == candidate) {
                return candidate;
            }
        }
        return fallback;
    }

    /** Switches preset and adopts its defaults for the individual toggles. */
    public void applyPreset(RenderSettings.Quality preset) {
        RenderSettings defaults = new RenderSettings();
        defaults.applyPreset(preset);
        quality = preset;
        rtShadows = defaults.rtShadows;
        rtGlobalIllumination = defaults.rtGlobalIllumination;
        rtReflections = defaults.rtReflections;
        denoiser = defaults.denoiser;
        temporalAntiAliasing = defaults.temporalAntiAliasing;
        bloom = defaults.bloom;
        renderScale = defaults.renderScale;
        traceScale = defaults.traceScale;
        save();
    }

    /** Builds the renderer configuration this settings object describes. */
    public RenderSettings toRenderSettings() {
        RenderSettings render = new RenderSettings();
        render.applyPreset(quality);
        render.rtShadows = rtShadows;
        render.rtGlobalIllumination = rtGlobalIllumination;
        render.rtReflections = rtReflections;
        render.rayTracing = rtShadows || rtGlobalIllumination || rtReflections;
        render.denoiser = denoiser;
        render.temporalAntiAliasing = temporalAntiAliasing;
        render.denoisePasses = denoiser ? Math.max(1, render.denoisePasses) : 0;
        render.bloom = bloom;
        render.adaptivePerformance = adaptivePerformance;
        render.renderScale = renderScale;
        render.traceScale = traceScale;
        render.contrast = contrast;
        render.brightness = brightness;
        render.gamma = gamma;
        render.fieldOfView = fieldOfView;
        render.showFps = showFps;
        render.frameRateCap = frameRateCap;
        render.playerHighlights = playerHighlights;
        render.normalize();
        return render;
    }
}
