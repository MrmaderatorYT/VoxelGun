package com.ccs.voxelstrike.game;

/** Outcome handed to the results screen and to the progression store. */
public final class MatchResult {
    public GameMode mode;
    public Difficulty difficulty;
    public int modifiers;
    public String mapName = "";
    public boolean victory;
    public boolean draw;
    public int playerKills;
    public int playerDeaths;
    public int playerScore;
    public int blueScore;
    public int redScore;
    public int bestStreak;
    public int headshots;
    public float durationSeconds;
    public int coinsEarned;
    public int experienceEarned;
    public int wave;
    /** Per-weapon tallies for the player, indexed by {@code WeaponType.ordinal()}. */
    public int[] weaponShots;
    public int[] weaponHits;
    public int[] weaponKills;
    public int[] weaponHeadshots;
}
