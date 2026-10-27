package com.ccs.voxelstrike.game;

/** The offline match rulesets. */
public enum GameMode {
    TEAM_DEATHMATCH("Team Deathmatch", "Two squads, first to the kill limit", true, 40, 420f),
    DEATHMATCH("Deathmatch", "Everyone for themselves", false, 25, 360f),
    GUN_GAME("Gun Game", "Every kill upgrades your weapon", false, 0, 480f),
    ZOMBIE("Zombie Survival", "Hold out against the horde", false, 0, 0f),
    KNIFE_FIGHT("Knife Fight", "Blades only, and one hit is lethal", false, 20, 300f),
    KING_OF_THE_HILL("King of the Hill", "Hold the hill; it moves every 60 seconds", true, 150, 600f),
    CAPTURE_POINT("Capture Point", "Capture three points and defend them", true, 200, 600f),
    BOMB_PLANT("Bomb / Plant", "Blue plants, Red defuses. First to five rounds", true, 5, 0f),
    CRYSTAL_HEIST("Crystal Heist", "Carry the core home; first to five deliveries", true, 5, 600f),

    /**
     * A practice range: every weapon is free, the targets stand there and pop straight back up,
     * and nothing ends the session. It is where a new gun or a new skin gets tried out.
     */
    RANGE("Shooting Range", "Free weapons and targets that pop back up. No clock", false, 0, 0f),

    /** Large two-team battle with a pre-selected rifle, sniper rifle and machine gun. */
    BATTLE_FIELD("Battle Field", "Large-scale combined-arms fight for up to 100 soldiers", true,
            250, 900f),
    MOVEMENT_TRAINING("Movement Training", "Timed sprint, jump and slide course. Personal bests", false, 0, 0f);

    private final String label;
    private final String description;
    private final boolean teamBased;
    private final int scoreLimit;
    private final float timeLimitSeconds;

    GameMode(String label, String description, boolean teamBased, int scoreLimit,
             float timeLimitSeconds) {
        this.label = label;
        this.description = description;
        this.teamBased = teamBased;
        this.scoreLimit = scoreLimit;
        this.timeLimitSeconds = timeLimitSeconds;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public boolean teamBased() {
        return teamBased;
    }

    public int scoreLimit() {
        return scoreLimit;
    }

    public float timeLimitSeconds() {
        return timeLimitSeconds;
    }

    /** Zombie survival has no respawns for the player and no round clock. */
    public boolean survival() {
        return this == ZOMBIE;
    }

    /**
     * The two deathmatch modes are deliberately unbalanced: every weapon in the game is available
     * from the first second, for nothing. There is no economy to play around, so the buy menu
     * becomes a plain inventory you can open at any point in the match.
     */
    public boolean freeArsenal() {
        return this == TEAM_DEATHMATCH || this == DEATHMATCH || this == RANGE
                || (objective() && this != BOMB_PLANT);
    }

    /** True when the mode lets you spend round money on weapons mid-match. */
    public boolean buyMenu() {
        return freeArsenal() || this == ZOMBIE || this == BOMB_PLANT;
    }

    public boolean objective() {
        // Spelled out rather than an ordinal cut-off, so a mode added at the end of the enum does
        // not silently inherit objective handling.
        return this == KING_OF_THE_HILL || this == CAPTURE_POINT || this == BOMB_PLANT
                || this == CRYSTAL_HEIST;
    }

    /**
     * The range's targets never shoot back and never hunt: they hold their ground so the player
     * can work on recoil and headshots instead of fighting for their life.
     */
    public boolean passiveBots() {
        return this == RANGE;
    }

    /** Knife fight replaces every loadout with the one lethal blade, on spawn and on respawn. */
    public boolean knifeOnly() {
        return this == KNIFE_FIGHT || this == MOVEMENT_TRAINING;
    }

    /** True for the large 2-100 player, two-team battlefield ruleset. */
    public boolean battlefield() {
        return this == BATTLE_FIELD;
    }
}
