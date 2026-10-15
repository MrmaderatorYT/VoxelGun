package com.ccs.voxelgun.game;

/**
 * Immutable-by-convention view of the match for the HUD.
 *
 * <p>The simulation fills one of two instances each frame and publishes it through a volatile
 * reference, so the UI thread always reads a complete frame without locking.
 */
public final class HudSnapshot {
    /** Authored callout; reference reused from map scenery, no per-frame formatting. */
    public String areaName = "";
    public static final int MAX_FEED = 5;
    /** Battle Field supports the local player plus as many as 99 bots. */
    public static final int MAX_BLIPS = 100;
    public static final int MAX_SCOREBOARD = 100;

    public boolean alive;
    public float speed;
    public boolean sliding, bhop, movementTrial, courseRunning, courseFinished;
    public int modifiers, courseMillis, courseBestMillis, courseGate;
    public int courseMistakes;
    public boolean courseRetryRecommended, courseGhost;
    public boolean infiniteAmmo, noReload, hardcoreHud;
    public float health;
    public float armor;
    public int ammo;
    public int reserve;
    public String weaponName = "";
    public int slot;
    public boolean reloading;
    public float reloadProgress;
    public boolean melee;
    public boolean grenade;
    public int grenadeCount;

    /** Round money and whether the buy menu will accept a purchase right now. */
    public int money;
    public boolean buyOpen;
    /** True when the mode gives every weapon away, so there is no wallet to show. */
    public boolean freeArsenal;
    public float buyTimeLeft;
    /** {@code WeaponType} ordinal carried in each slot, or -1 when the slot is empty. */
    public final int[] kit = new int[com.ccs.voxelgun.weapon.WeaponSlot.COUNT];
    /** Equipped skin value for each entry in {@link #kit}. */
    public final int[] kitSkin = new int[com.ccs.voxelgun.weapon.WeaponSlot.COUNT];

    public GameMode mode = GameMode.TEAM_DEATHMATCH;
    public int blueScore;
    public int redScore;
    public int scoreLimit;
    public float timeLeft;
    public boolean timed;
    public int wave;
    public int enemiesAlive;

    public int kills;
    public int deaths;
    public int streak;
    public int ladderStep;
    public int ladderTotal;

    public float crosshairSpread;
    public float hitMarker;
    public boolean hitMarkerLethal;
    public int hitKind;
    public int crosshairPreset;
    public int objectiveCount;
    public boolean conquest;
    public final float[] objectiveX = new float[5];
    public final float[] objectiveY = new float[5];
    public final float[] objectiveZ = new float[5];
    public final int[] objectiveColor = new int[5];
    public final boolean[] objectiveContested = new boolean[5];
    public final float[] objectiveProgress = new float[5];
    public String objectiveStatus = "";
    public float objectiveSeconds, objectiveDistance, objectiveBearing, interactProgress;
    public float damageDirection;
    public float damageAmount;
    public float flash;
    public float respawnTimer;
    public boolean aiming;
    public boolean scoped;
    /** True when the weapon in hand carries optics, whether or not it is raised right now. */
    public boolean hasScope;
    /** Session totals for the practice range readout: shots taken, shots that connected. */
    public int rangeShots;
    public int rangeHits;
    public int rangeHeadshots;
    /** True while the death is being replayed from the killer's point of view. */
    public boolean killCam;
    /** 0 hip, 1 fully behind the optic; drives the scope overlay. */
    public float scopeBlend;
    public float zoom;

    public float playerX;
    public float playerZ;
    public float playerYaw;

    public int blipCount;
    public final float[] blipX = new float[MAX_BLIPS];
    public final float[] blipZ = new float[MAX_BLIPS];
    public final int[] blipColor = new int[MAX_BLIPS];
    public final boolean[] blipEnemy = new boolean[MAX_BLIPS];

    public int feedCount;
    public final String[] feedAttacker = new String[MAX_FEED];
    public final String[] feedVictim = new String[MAX_FEED];
    public final String[] feedWeapon = new String[MAX_FEED];
    public final int[] feedAttackerColor = new int[MAX_FEED];
    public final int[] feedVictimColor = new int[MAX_FEED];
    public final boolean[] feedHeadshot = new boolean[MAX_FEED];
    public final float[] feedLife = new float[MAX_FEED];
    public final int[] feedWeaponOrdinal = new int[MAX_FEED];
    public final int[] feedFlags = new int[MAX_FEED];
    public final boolean[] feedInvolvesPlayer = new boolean[MAX_FEED];

    public int scoreboardCount;
    public final String[] rowName = new String[MAX_SCOREBOARD];
    public final int[] rowKills = new int[MAX_SCOREBOARD];
    public final int[] rowDeaths = new int[MAX_SCOREBOARD];
    public final int[] rowScore = new int[MAX_SCOREBOARD];
    public final int[] rowColor = new int[MAX_SCOREBOARD];
    public final boolean[] rowIsPlayer = new boolean[MAX_SCOREBOARD];

    public boolean finished;
    public String banner = "";
    public float bannerLife;
    public int fps;
    public int frameP95;
    /** Rolling CPU section timings; rendering excludes GPU execution/present waits. */
    public final int[] cpuMicros = new int[com.ccs.voxelgun.performance.SectionMetrics.COUNT];
    public int meshingMicros, thermalStatus, effectPercent;
    public float viewDistance;

    public HudSnapshot() {
        java.util.Arrays.fill(feedWeaponOrdinal, -1);
    }
}
