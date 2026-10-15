package com.ccs.voxelgun.game;

import com.ccs.voxelgun.weapon.WeaponType;

/**
 * Everything the menu chooses before a match starts.
 *
 * <p>The weapons here are only the spawn defaults. Nothing in the menu picks them any more: in the
 * deathmatch modes the whole rack is free from the buy menu, and elsewhere you buy your way up from
 * the pistol.
 */
public final class MatchConfig {
    public GameMode mode = GameMode.TEAM_DEATHMATCH;
    public int mapIndex;
    public int botCount = 7;
    public Difficulty difficulty = Difficulty.REGULAR;
    public WeaponType primary = WeaponType.AK15;
    public WeaponType secondary = WeaponType.P9;
    public WeaponType melee = WeaponType.KNIFE;
    public WeaponType grenade = WeaponType.FRAG;
    public String playerName = "Player";
    public long seed = 20260726L;
    public int modifiers;
    public int courseBestMillis;
    public int[] weaponSkins;
    public int gloveColor;
    public int charm;
    public int crosshair;

    /** Battle Field has one human plus enough bots to reach this even total. */
    public int totalPlayers = BattlefieldRules.DEFAULT_PLAYERS;
    /** Configurable team-kill target for Battle Field; other modes use their enum default. */
    public int scoreLimit = BattlefieldRules.DEFAULT_SCORE;
    /** Configurable Battle Field clock. Other modes use their enum default. */
    public float timeLimitSeconds = BattlefieldRules.DEFAULT_TIME_MINUTES * 60f;
    /** Disables health regeneration and all team-identifying HUD/render highlights. */
    public boolean hardcore;
    /** Optional territory rules; the original Battle Field deathmatch remains available. */
    public boolean conquest;
    public BattlefieldClass battlefieldClass = BattlefieldClass.RIFLEMAN;
    public WeaponType battlefieldPrimary = WeaponType.AK15;
    public WeaponType battlefieldSniper = WeaponType.AWM_X;
    public WeaponType battlefieldLmg = WeaponType.NEGEV_M;

    public MatchConfig copy() {
        MatchConfig other = new MatchConfig();
        other.mode = mode;
        other.mapIndex = mapIndex;
        other.botCount = botCount;
        other.difficulty = difficulty;
        other.primary = primary;
        other.secondary = secondary;
        other.melee = melee;
        other.grenade = grenade;
        other.playerName = playerName;
        other.seed = seed;
        other.modifiers = MatchModifier.sanitize(modifiers, mode);
        other.courseBestMillis = Math.max(0, courseBestMillis);
        other.weaponSkins = weaponSkins;
        other.gloveColor = gloveColor;
        other.charm = charm;
        other.crosshair = crosshair;
        other.totalPlayers = BattlefieldRules.sanitizeTotalPlayers(totalPlayers);
        other.scoreLimit = BattlefieldRules.sanitizeScoreLimit(scoreLimit);
        other.timeLimitSeconds = BattlefieldRules.sanitizeTimeSeconds(timeLimitSeconds);
        other.hardcore = hardcore;
        other.conquest = conquest;
        other.battlefieldClass = BattlefieldRules.sanitizeClass(battlefieldClass);
        other.battlefieldPrimary = BattlefieldRules.sanitizeRifle(battlefieldPrimary);
        other.battlefieldSniper = BattlefieldRules.sanitizeSniper(battlefieldSniper);
        other.battlefieldLmg = BattlefieldRules.sanitizeMachineGun(battlefieldLmg);
        return other;
    }
}
