package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.audio.Sfx;
import com.ccs.voxelstrike.input.InputState;
import com.ccs.voxelstrike.map.GameMap;
import com.ccs.voxelstrike.map.MapEnvironment;
import com.ccs.voxelstrike.map.Maps;
import com.ccs.voxelstrike.map.SpawnPoint;
import com.ccs.voxelstrike.math.MathUtil;
import com.ccs.voxelstrike.math.Vec3;
import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.voxel.RayHit;
import com.ccs.voxelstrike.voxel.VoxelWorld;
import com.ccs.voxelstrike.weapon.Weapon;
import com.ccs.voxelstrike.weapon.WeaponSlot;
import com.ccs.voxelstrike.weapon.WeaponType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The whole offline simulation: player control, ballistics, bots, projectiles, scoring and
 * mode rules. It runs on the render thread at a fixed step and publishes a {@link HudSnapshot}
 * for the UI thread.
 */
public final class Match {
    public static final float STEP = 1f / 60f;
    /** The renderer and simulation stay at 60 Hz; HUD-only data is ample at 30 Hz. */
    private static final float HUD_STEP = 1f / 30f;
    private static final float RESPAWN_SECONDS = 3.4f;
    /** Range targets come back almost at once; waiting is not the exercise. */
    private static final float RANGE_RESPAWN_SECONDS = 1.2f;
    private static final float WALK_SPEED = 5.4f;
    private static final float SPRINT_SPEED = 7.7f;
    private static final float CROUCH_SPEED = 2.7f;
    private static final float JUMP_SPEED = 7.4f;
    private final ArenaMovement arenaMovement = new ArenaMovement();
    private final MovementTrial movementTrial;
    private final DeathBlastQueue deathBlasts = new DeathBlastQueue();
    private final WeaponType[] mirroredKit = new WeaponType[WeaponSlot.COUNT];
    private static final int MAX_PROJECTILES = 48;
    /** Below this height a body has left the map for good. */
    private static final float VOID_LEVEL = -6f;
    private static final float JUMP_PAD_SPEED = 15.5f;
    private static final float SPAWN_CLEAR_RADIUS_SQUARED = 9f;
    private static final float SPAWN_VISIBLE_ENEMY_PENALTY = 900f;
    private static final int FEED_SIZE = HudSnapshot.MAX_FEED;
    private static final int ZOMBIE_BUY_WAVE_INTERVAL = 5;

    private static final WeaponType[] GUN_GAME_LADDER = {
            WeaponType.P9, WeaponType.AUTO9, WeaponType.HORNET, WeaponType.VECTOR9,
            WeaponType.UZIK, WeaponType.FALCON, WeaponType.AK15, WeaponType.FAMAS_B,
            WeaponType.SCAR_H, WeaponType.BREAKER12, WeaponType.DRAGUNOV_S, WeaponType.KNIFE
    };

    private static final String[] BOT_NAMES = {
            "Kobra", "Vector", "Slate", "Nomad", "Vulcan", "Reaper", "Echo", "Fang", "Hydra",
            "Onyx", "Pilot", "Quartz", "Rogue", "Saber", "Titan", "Ursa", "Viper", "Wraith"
    };

    /** Match phases. */
    public enum State {
        COUNTDOWN,
        PLAYING,
        FINISHED
    }

    private final MatchConfig config;
    private final GameMap map;
    private final VoxelWorld world;
    private final NavGrid nav;
    private final PathFinder pathFinder;
    private final Effects effects = new Effects();
    private final Random random;
    private final List<Combatant> combatants = new ArrayList<>();
    private final List<BotBrain> brains = new ArrayList<>();
    private BotDiagnostics botDiagnostics;
    public void enableBotDiagnostics() { if (botDiagnostics == null) botDiagnostics = new BotDiagnostics(combatants.size()); }
    public BotDiagnostics botDiagnostics() { return botDiagnostics; }
    private final BotTacticalBudget tacticalBudget = new BotTacticalBudget();
    BotTacticalBudget tacticalBudget() { return tacticalBudget; }
    /** Brain lookup by combatant id; index 0 (the player) is always null. */
    private BotBrain[] brainById = new BotBrain[1];
    private final Projectile[] projectiles = new Projectile[MAX_PROJECTILES];
    private final KillFeedEntry[] feed = new KillFeedEntry[FEED_SIZE];
    private final Combatant player;
    private final MatchResult result = new MatchResult();
    private final Objectives objectives;
    private final Conquest conquest;
    private final BattlefrontPlanner battlefront;
    private final Destruction destruction;
    private final MapEnvironment matchEnvironment;
    private int hitKind;
    private float heartbeatTimer;
    private static final WeaponType[] RANDOM_GUNS = {WeaponType.AK15, WeaponType.P9,
            WeaponType.BREAKER12, WeaponType.DRAGUNOV_S, WeaponType.VECTOR9, WeaponType.NEGEV_M};
    /**
     * One primary family per combat role. Rotating the role by seed keeps matches varied while
     * guaranteeing that a normal six-plus-bot match contains more than a pile of random rifles.
     */
    private static final WeaponType[][] BOT_PRIMARY_POOLS = {
            {WeaponType.FAMAS_B, WeaponType.BULLPUP, WeaponType.AK15,
                    WeaponType.VEKTOR_AR, WeaponType.FALCON, WeaponType.SCAR_H},
            {WeaponType.SPECTRE, WeaponType.HORNET, WeaponType.UZIK,
                    WeaponType.MP7K, WeaponType.VECTOR9},
            {WeaponType.PUMPER, WeaponType.BREAKER12, WeaponType.AUTOSLUG,
                    WeaponType.DRUM12},
            {WeaponType.SCOUT_V, WeaponType.DRAGUNOV_S, WeaponType.AWM_X,
                    WeaponType.BARRETT_M},
            {WeaponType.BULLDOG_LMG, WeaponType.NEGEV_M},
            {WeaponType.RPG_V}
    };
    private static final WeaponType[][] BOT_SECONDARY_POOLS = {
            {WeaponType.P9, WeaponType.TALON, WeaponType.MAG5},
            {WeaponType.RANGER, WeaponType.MAG5, WeaponType.SIDEWINDER},
            {WeaponType.TALON, WeaponType.MAG5, WeaponType.RANGER},
            {WeaponType.AUTO9, WeaponType.P9, WeaponType.TALON},
            {WeaponType.P9, WeaponType.MAG5, WeaponType.RANGER},
            {WeaponType.AUTO9, WeaponType.P9, WeaponType.TALON}
    };

    private final HudSnapshot[] snapshots = {new HudSnapshot(), new HudSnapshot()};
    private volatile HudSnapshot published = snapshots[0];
    private int snapshotIndex;

    private final float[] lookScratch = new float[2];
    private final Vec3 aimScratch = new Vec3();
    private final Vec3 tempVec = new Vec3();
    private final RayHit rayHit = new RayHit();
    private final HitProbe probe = new HitProbe();

    private MatchListener listener = NULL_LISTENER;
    private State state = State.COUNTDOWN;
    private float clock;
    private float countdown = 3.2f;
    private int blueScore;
    private int redScore;
    private int wave = 1;
    private float waveTimer = 45f;
    private float hitMarker;
    private boolean hitMarkerLethal;
    private float bannerLife;
    private String banner = "";
    private int bestStreak;
    private int headshots;
    /** Short movement trails, replayed from the killer's eyes when the player goes down. */
    private KillCam killCam;
    private final float[] killCamCamera = new float[5];
    /**
     * Per-weapon tallies for the local player only, indexed by {@link WeaponType#ordinal()}.
     * A hit is counted once per trigger pull rather than once per pellet, so a shotgun cannot
     * report better than 100% accuracy.
     */
    private final int[] weaponShots = new int[WeaponType.values().length];
    private final int[] weaponHits = new int[WeaponType.values().length];
    private final int[] weaponKills = new int[WeaponType.values().length];
    private final int[] weaponHeadshots = new int[WeaponType.values().length];
    private boolean shotConnected;
    private int fps;
    private int frameP95;
    private final com.ccs.voxelstrike.performance.SectionMetrics metrics =
            new com.ccs.voxelstrike.performance.SectionMetrics();
    private int meshingMicros, thermalStatus;
    private long physicsNanos, firingNanos;
    private float liveViewDistance, effectFraction = 1f;
    public com.ccs.voxelstrike.performance.SectionMetrics metrics() { return metrics; }
    public MovementTrial movementTrial() { return movementTrial; }
    public void setRenderTelemetry(int meshingMicros, int thermalStatus, float distance, float effects) {
        this.meshingMicros = meshingMicros; this.thermalStatus = thermalStatus;
        liveViewDistance = distance; effectFraction = effects;
    }
    public void setFrameP95(int milliseconds) { frameP95 = milliseconds; }
    private float hudAccumulator;

    // Camera state consumed by the renderer.
    public final Vec3 cameraPosition = new Vec3();
    public float cameraYaw;
    public float cameraPitch;
    public float cameraRoll;
    public float cameraFovScale = 1f;
    /** How far the optic has been raised, 0..1. */
    public float scopeBlend;
    private float stepViewOffset, landingViewOffset;
    /** Small first-person weapon follow-through during takeoff and landing. */
    public float weaponJumpOffset;

    public Match(MatchConfig config) {
        this.config = config.copy();
        movementTrial = this.config.mode == GameMode.MOVEMENT_TRAINING
                ? new MovementTrial(this.config.courseBestMillis) : null;
        if (this.config.mode.battlefield() && !Maps.isBattlefield(this.config.mapIndex)) {
            this.config.mapIndex = Maps.firstBattlefieldIndex();
        } else if (!this.config.mode.battlefield() && Maps.isBattlefield(this.config.mapIndex)) {
            // The 192x64 deployment maps are balanced and budgeted for Battle Field's rules.
            // Arena/survival modes stay on the classic catalog instead of loading one by accident.
            this.config.mapIndex = 0;
        }
        this.random = new Random(this.config.seed);
        this.map = this.config.mode == GameMode.MOVEMENT_TRAINING
                ? new com.ccs.voxelstrike.map.maps.MovementCourse() : Maps.create(this.config.mapIndex);
        this.world = map.build(this.config.seed);
        MapEnvironment base = map.environment();
        boolean night = modifier(MatchModifier.NIGHT);
        matchEnvironment = MapEnvironment.builder().sun(base.sunX, base.sunY, base.sunZ)
                .sunColor(night ? 0.55f : base.sunR, night ? 0.7f : base.sunG,
                        night ? 1f : base.sunB, night ? 0.45f : base.sunIntensity)
                .sunAngle(base.sunAngle).sky(night ? 0.035f : base.zenithR,
                        night ? 0.055f : base.zenithG, night ? 0.12f : base.zenithB,
                        night ? 0.10f : base.horizonR, night ? 0.14f : base.horizonG,
                        night ? 0.23f : base.horizonB, night ? 0.65f : base.skyIntensity)
                .fog(modifier(MatchModifier.FOG) ? 0.045f : base.fogDensity)
                .exposure(base.exposure).indoor(base.indoor).build();
        this.nav = new NavGrid(world);
        this.pathFinder = new PathFinder(nav);
        destruction = new Destruction(world, nav);
        for (int i = 0; i < projectiles.length; i++) {
            projectiles[i] = new Projectile();
        }
        for (int i = 0; i < feed.length; i++) {
            feed[i] = new KillFeedEntry();
        }

        player = new Combatant(0, this.config.playerName);
        player.bot = false;
        combatants.add(player);
        setupTeamsAndLoadouts();
        if (isBattlefield() && this.config.conquest) {
            for (Combatant c : combatants) {
                c.kit[WeaponSlot.GRENADE.ordinal()] = WeaponType.FRAG;
                c.applyKit();
            }
        }
        player.weaponSkins = this.config.weaponSkins;
        System.arraycopy(player.kit, 0, mirroredKit, 0, mirroredKit.length);
        player.gloveColor = this.config.gloveColor;
        player.charm = this.config.charm;
        objectives = this.config.mode.objective() ? new Objectives(this) : null;
        conquest = isBattlefield() && this.config.conquest ? new Conquest(this, scoreLimit()) : null;
        battlefront = isBattlefield() && conquest == null ? new BattlefrontPlanner(this) : null;
        killCam = new KillCam(combatants.size());
        spawnAll();
        if (conquest != null) conquest.onDeployment();
        if (battlefront != null) battlefront.onDeployment();
        publishSnapshot(0f);
    }

    public void setListener(MatchListener listener) {
        this.listener = listener == null ? NULL_LISTENER : listener;
    }

    public VoxelWorld world() {
        return world;
    }

    public GameMap map() {
        return map;
    }

    public MapEnvironment environment() {
        return matchEnvironment;
    }

    public boolean modifier(MatchModifier modifier) { return (config.modifiers & modifier.bit) != 0; }
    public float movementMultiplier() { return modifier(MatchModifier.DOUBLE_SPEED) ? 2f : 1f; }
    public Objectives objectives() { return objectives; }
    public Conquest conquest() { return conquest; }
    BattlefrontPlanner battlefront() { return battlefront; }
    public boolean soloExploration() { return modifier(MatchModifier.NO_BOTS); }
    public boolean combatActive() { return state == State.PLAYING && (objectives == null || !objectives.roundPaused()); }

    void objectiveBanner(String text) {
        showBanner(text, 2.2f);
        listener.onSound(Sfx.ROUND_START, player.eyeX(), player.eyeY(), player.eyeZ(), 0.5f, 1.25f);
    }

    void awardObjective(Team team, int points) {
        if (state != State.PLAYING || soloExploration()) return;
        if (team == Team.BLUE) blueScore += points;
        if (team == Team.RED) redScore += points;
        if (blueScore >= scoreLimit() || redScore >= scoreLimit())
            finish(blueScore > redScore, blueScore == redScore);
    }

    void restartObjectiveRound() {
        if (killCam != null) {
            killCam.clear();
        }
        for (Projectile projectile : projectiles) projectile.active = false;
        for (Combatant c : combatants) respawn(c, false);
        objectiveBanner("ROUND START");
    }

    public Effects effects() {
        return effects;
    }

    public List<Combatant> combatants() {
        return combatants;
    }

    public Combatant player() {
        return player;
    }

    public Projectile[] projectiles() {
        return projectiles;
    }

    public NavGrid nav() {
        return nav;
    }

    public PathFinder pathFinder() {
        return pathFinder;
    }

    public Random random() {
        return random;
    }

    public State state() {
        return state;
    }

    public GameMode mode() {
        return config.mode;
    }

    public boolean isBattlefield() {
        return config.mode.battlefield();
    }

    public boolean hardcore() {
        return isBattlefield() && config.hardcore;
    }

    /** Hardcore removes persistent friend markers and the silhouette rendering pass. */
    public boolean hideTeamIndicators() {
        return hardcore() || modifier(MatchModifier.HARDCORE_HUD);
    }

    /** Bot requests obey the same airborne jump budget as a fresh human jump press. */
    boolean jump(Combatant c, float speed) {
        return JumpRules.jump(c, speed, modifier(MatchModifier.DOUBLE_JUMP), true);
    }

    private void configureWeapons(Combatant c) {
        for (Weapon w : c.weapons) if (w != null) w.setAmmoRules(
                modifier(MatchModifier.INFINITE_AMMO), modifier(MatchModifier.NO_RELOAD));
    }

    public int scoreLimit() {
        return isBattlefield() ? config.scoreLimit : config.mode.scoreLimit();
    }

    public float timeLimitSeconds() {
        if (soloExploration()) return 0f;
        return isBattlefield() ? config.timeLimitSeconds : config.mode.timeLimitSeconds();
    }

    /** Latest training result, independent of the HUD's 30 Hz publication cadence. */
    public int courseBestMillis() {
        return movementTrial == null ? 0 : movementTrial.bestMillis();
    }

    public float classMovementMultiplier(Combatant combatant) {
        return isBattlefield() && combatant != null
                ? combatant.battlefieldClass.movementMultiplier() : 1f;
    }

    /** Live per-weapon tallies for the player: shots, hits, kills, headshots. */
    public int[] weaponTally(WeaponType type) {
        int i = type.ordinal();
        return new int[] {weaponShots[i], weaponHits[i], weaponKills[i], weaponHeadshots[i]};
    }

    public MatchResult result() {
        return result;
    }

    public HudSnapshot snapshot() {
        return published;
    }

    public void setFps(int value) {
        fps = value;
    }

    public void release() {
        world.release();
    }

    // ------------------------------------------------------------------ setup

    private void setupTeamsAndLoadouts() {
        boolean teamMode = config.mode.teamBased();
        boolean zombieMode = config.mode == GameMode.ZOMBIE;

        player.team = teamMode || zombieMode ? Team.BLUE : Team.NONE;
        if (isBattlefield()) {
            player.battlefieldClass = config.battlefieldClass;
            player.setBattlefieldLoadout(BattlefieldRules.weaponForClass(
                    config.battlefieldClass, config.battlefieldPrimary,
                    config.battlefieldSniper, config.battlefieldLmg));
        } else if (config.mode.knifeOnly()) {
            player.setSingleWeapon(WeaponType.KNIFE);
        } else if (config.mode == GameMode.GUN_GAME) {
            player.ladderStep = 0;
            player.setSingleWeapon(GUN_GAME_LADDER[0]);
            player.weapons[WeaponSlot.MELEE.ordinal()] = new Weapon(WeaponType.KNIFE);
        } else if (config.mode.freeArsenal()) {
            // Nothing is earned here, so the menu pick is simply handed over.
            player.setLoadout(config.primary, config.secondary, config.melee, config.grenade);
        } else if (config.mode.buyMenu()) {
            // No primary at the first spawn: that is what the buy menu is for.
            player.setLoadout(null, config.secondary, config.melee, config.grenade);
            player.money = Economy.STARTING_MONEY;
        } else {
            player.setLoadout(config.primary, config.secondary, config.melee, config.grenade);
        }

        int botCount = soloExploration() || config.mode == GameMode.MOVEMENT_TRAINING ? 0 : isBattlefield()
                ? BattlefieldRules.sanitizeTotalPlayers(config.totalPlayers) - 1
                : Math.max(1, config.botCount);
        for (int i = 0; i < botCount; i++) {
            Combatant bot = new Combatant(i + 1, BOT_NAMES[i % BOT_NAMES.length]
                    + (i >= BOT_NAMES.length ? String.valueOf(i / BOT_NAMES.length + 1) : ""));
            bot.bot = true;
            if (isBattlefield()) {
                bot.team = (i % 2 == 0) ? Team.RED : Team.BLUE;
                BattlefieldClass[] roles = BattlefieldClass.values();
                bot.battlefieldClass = roles[Math.floorMod(i, roles.length)];
                giveBattlefieldBotLoadout(bot);
            } else if (config.mode.knifeOnly()) {
                bot.team = Team.NONE;
                bot.setSingleWeapon(WeaponType.KNIFE);
            } else if (zombieMode) {
                bot.team = Team.RED;
                bot.zombie = true;
                bot.setSingleWeapon(WeaponType.SCRAP_KNIFE);
            } else if (teamMode) {
                bot.team = (i % 2 == 0) ? Team.RED : Team.BLUE;
                giveBotLoadout(bot);
            } else {
                bot.team = Team.NONE;
                if (config.mode == GameMode.GUN_GAME) {
                    bot.setSingleWeapon(GUN_GAME_LADDER[0]);
                    bot.weapons[WeaponSlot.MELEE.ordinal()] = new Weapon(WeaponType.KNIFE);
                } else {
                    giveBotLoadout(bot);
                }
            }
            combatants.add(bot);
            brains.add(new BotBrain(bot, config.difficulty, this, random.nextLong()));
        }

        brainById = new BotBrain[combatants.size()];
        for (BotBrain brain : brains) {
            for (Combatant combatant : combatants) {
                if (brain.owns(combatant)) {
                    brainById[combatant.id] = brain;
                }
            }
        }
    }

    // ------------------------------------------------------------------ buy menu

    /** True while the player may spend round money: always when dead, briefly after a spawn. */
    public boolean buyOpen() {
        if (!config.mode.buyMenu() || state == State.FINISHED
                || modifier(MatchModifier.ONE_SHOT_RAIL) || modifier(MatchModifier.RANDOM_WEAPONS)) {
            return false;
        }
        // With a free arsenal there is nothing to ration, so the list is always open.
        // Otherwise the opening countdown counts as buy time: that is when you kit up.
        return config.mode.freeArsenal() || state == State.COUNTDOWN || !player.alive
                || player.buyTimer > 0f;
    }

    /**
     * Spends round money on one weapon. It goes into the kit as well as into the player's hands,
     * so the next respawn still has it.
     */
    private void tryBuy(int weaponOrdinal) {
        WeaponType[] all = WeaponType.values();
        if (weaponOrdinal < 0 || weaponOrdinal >= all.length) {
            return;
        }
        WeaponType type = all[weaponOrdinal];
        int cost = config.mode.freeArsenal() ? 0 : type.cost();
        if (type.modeOnly() || !buyOpen() || cost > player.money) {
            listener.onSound(Sfx.DRY_FIRE, player.eyeX(), player.eyeY(), player.eyeZ(), 0.5f, 0.8f);
            return;
        }
        int index = type.slot().ordinal();
        if (player.kit[index] == type) {
            return;  // already carrying it; do not charge twice
        }
        player.money = Economy.clamp(player.money - cost);
        player.kit[index] = type;
        if (player.alive) {
            player.weapons[index] = new Weapon(type);
            configureWeapons(player);
            player.slot = index;
            player.switchTimer = 0.32f;
        }
        listener.onSound(Sfx.CLICK, player.eyeX(), player.eyeY(), player.eyeZ(), 0.7f, 1.35f);
    }

    private void giveBotLoadout(Combatant bot) {
        int seedRotation = (int) Math.floorMod(config.seed, (long) BOT_PRIMARY_POOLS.length);
        int ordinal = config.mode.teamBased() ? (bot.id - 1) / 2 : bot.id - 1;
        int roleCount = modifier(MatchModifier.HEADSHOTS_ONLY)
                ? BOT_PRIMARY_POOLS.length - 1 : BOT_PRIMARY_POOLS.length;
        int role = Math.floorMod(ordinal + seedRotation, roleCount);
        WeaponType[] primaries = BOT_PRIMARY_POOLS[role];
        WeaponType[] secondaries = BOT_SECONDARY_POOLS[role];
        WeaponType primary = primaries[random.nextInt(primaries.length)];
        WeaponType secondary = secondaries[random.nextInt(secondaries.length)];
        WeaponType grenade = !modifier(MatchModifier.HEADSHOTS_ONLY) && random.nextFloat() < 0.55f
                ? WeaponType.FRAG : WeaponType.FLASH;
        bot.setLoadout(primary, secondary, WeaponType.KNIFE, grenade);
    }

    /** Every Battle Field bot receives one class weapon, the common P9 and the common knife. */
    private void giveBattlefieldBotLoadout(Combatant bot) {
        WeaponType[] pool = BattlefieldRules.weaponsForClass(bot.battlefieldClass);
        // The same class/team repeats every six ids; indexing by id aliases with pool sizes.
        int ordinal = (bot.id - 1) / (BattlefieldClass.values().length * 2);
        WeaponType longGun = pool[(int) Math.floorMod(config.seed + ordinal, (long) pool.length)];
        bot.setBattlefieldLoadout(longGun);
    }

    private void spawnAll() {
        for (Combatant combatant : combatants) combatant.alive = false;
        for (Combatant combatant : combatants) {
            respawn(combatant, true);
        }
        updateCamera(0f);
    }

    // ------------------------------------------------------------------ update

    public void update(float dt, InputState input) {
        physicsNanos = firingNanos = 0L;
        if (input.consumeCourseRestart() && movementTrial != null) {
            movementTrial.restart(player);
            arenaMovement.reset();
            resetMovementView();
            input.releaseAll();
            updateCamera(STEP);
        }
        if (input.paused) {
            arenaMovement.reset(); // A pre-pause jump/landing buffer must not fire on resume.
            for (int purchase = input.consumePurchase(); purchase >= 0; purchase = input.consumePurchase()) tryBuy(purchase);
            input.consumeLook(lookScratch);
            publishHudIfNeeded(dt);
            return;
        }
        if (state == State.FINISHED) {
            effects.update(dt, world);
            updateCamera(dt);
            publishHudIfNeeded(dt);
            return;
        }
        if (state == State.COUNTDOWN) {
            countdown -= dt;
            if (countdown <= 0f) {
                state = State.PLAYING;
                showBanner(hardcore() ? config.mode.label() + " · HARDCORE"
                        : config.mode.label(), 2.4f);
                listener.onSound(Sfx.ROUND_START, player.eyeX(), player.eyeY(), player.eyeZ(), 0.8f,
                        1f);
            }
        }

        if (state == State.PLAYING) clock += dt;
        bannerLife = Math.max(0f, bannerLife - dt);
        hitMarker = Math.max(0f, hitMarker - dt * 2.6f);

        // Large matches need a little more path throughput, but keep a hard ceiling so one frame
        // cannot turn into a burst of dozens of A* searches.
        pathFinder.beginStep(Math.min(10, Math.max(4, combatants.size() / 12)));
        tacticalBudget.beginStep(isBattlefield() ? 8 : 6);

        float oldX = player.position.x, oldY = player.position.y, oldZ = player.position.z;
        for (int i = 0; i < combatants.size(); i++) configureWeapons(combatants.get(i));
        long timing = System.nanoTime();
        updatePlayer(dt, input);
        if (movementTrial != null && state == State.PLAYING) {
            movementTrial.update(dt, oldX, oldY, oldZ, player, arenaMovement.sliding());
        }
        timing = System.nanoTime();
        if (battlefront != null && combatActive()) battlefront.update(dt);
        for (int i = 0; i < brains.size(); i++) {
            brains.get(i).update(dt);
        }
        metrics.record(com.ccs.voxelstrike.performance.SectionMetrics.AI, System.nanoTime() - timing);
        if (killCam != null) {
            killCam.record(dt, combatants);
        }
        timing = System.nanoTime();
        for (Combatant combatant : combatants) {
            if (combatant.bot && combatant.alive) {
                updateStance(combatant, combatant.crouching, dt);
                updateSteady(combatant, dt);
                integrateBody(combatant, dt, true);
            }
            if (botDiagnostics != null && combatant.bot) {
                BotBrain brain = brainFor(combatant);
                botDiagnostics.sample(combatant, dt, combatActive(),
                        brain != null && brain.diagnosticWantsMovement(),
                        brain == null ? BotDiagnostics.NONE : brain.diagnosticHoldReason());
            }
            combatant.updateTimers(dt);
            updateHealthRegeneration(combatant, dt);
            updateReloadAudio(combatant);
            if (!combatant.alive) {
                updateRespawn(combatant, dt);
            }
        }
        metrics.record(com.ccs.voxelstrike.performance.SectionMetrics.PHYSICS,
                physicsNanos);
        timing = System.nanoTime();
        updateProjectiles(dt);
        processDeathBlasts();
        metrics.record(com.ccs.voxelstrike.performance.SectionMetrics.COMBAT,
                firingNanos + System.nanoTime() - timing);
        timing = System.nanoTime();
        effects.update(dt, world);
        metrics.record(com.ccs.voxelstrike.performance.SectionMetrics.EFFECTS, System.nanoTime() - timing);
        timing = System.nanoTime();
        updateRules(dt);
        metrics.record(com.ccs.voxelstrike.performance.SectionMetrics.OBJECTIVES, System.nanoTime() - timing);
        heartbeatTimer -= dt;
        if (player.alive && player.health <= 30f && combatActive() && heartbeatTimer <= 0f) {
            heartbeatTimer = 0.55f + player.health / 70f;
            listener.onSound(Sfx.HEARTBEAT, player.eyeX(), player.eyeY(), player.eyeZ(), 0.42f, 1f);
        }
        updateCamera(dt);
        publishHudIfNeeded(dt);
    }

    private void updateRules(float dt) {
        if (state != State.PLAYING) {
            return;
        }
        // Exploration has no opposing team, automatic round win, ticket bleed or wave clock.
        if (soloExploration()) return;
        if (objectives != null) objectives.update(dt);
        if (conquest != null) {
            conquest.update(dt);
            blueScore = conquest.tickets(Team.BLUE);
            redScore = conquest.tickets(Team.RED);
            if (blueScore == 0 || redScore == 0) {
                finish(blueScore > redScore, blueScore == redScore);
                return;
            }
        }
        if (config.mode == GameMode.ZOMBIE) {
            waveTimer -= dt;
            if (waveTimer <= 0f) {
                wave++;
                waveTimer = 45f;
                boolean shopWave = wave % ZOMBIE_BUY_WAVE_INTERVAL == 0;
                if (shopWave) {
                    player.buyTimer = Math.max(player.buyTimer, Economy.BUY_WINDOW_SECONDS);
                }
                showBanner("WAVE " + wave + (shopWave ? " · SHOP OPEN" : ""),
                        shopWave ? 3f : 2.2f);
                listener.onSound(Sfx.ROUND_START, player.eyeX(), player.eyeY(), player.eyeZ(), 0.7f,
                        0.8f);
            }
            if (!player.alive && player.respawnTimer <= 0f) {
                finish(false, false);
            }
            return;
        }

        float limit = timeLimitSeconds();
        if (limit > 0f && clock >= limit) {
            if (config.mode.teamBased()) {
                finish(blueScore > redScore, blueScore == redScore);
            } else {
                finish(isPlayerLeading(), false);
            }
        }
    }

    /** Normal Battle Field slowly heals health after fifteen uninterrupted seconds out of fire. */
    private void updateHealthRegeneration(Combatant combatant, float dt) {
        if (!isBattlefield() || hardcore() || state != State.PLAYING || !combatant.alive
                || combatant.lastDamageTime < BattlefieldRules.REGENERATION_DELAY_SECONDS
                || combatant.health >= Combatant.MAX_HEALTH) {
            return;
        }
        combatant.health = Math.min(Combatant.MAX_HEALTH, combatant.health
                + combatant.battlefieldClass.regenerationPerSecond() * dt);
    }

    private boolean isPlayerLeading() {
        for (Combatant combatant : combatants) {
            if (combatant != player && combatant.kills > player.kills) {
                return false;
            }
        }
        return true;
    }

    private void finish(boolean victory, boolean draw) {
        if (state == State.FINISHED) {
            return;
        }
        state = State.FINISHED;
        result.mode = config.mode;
        result.difficulty = config.difficulty;
        result.modifiers = config.modifiers;
        result.mapName = map.displayName();
        result.victory = victory;
        result.draw = draw;
        result.playerKills = player.kills;
        result.playerDeaths = player.deaths;
        result.playerScore = player.score;
        result.blueScore = blueScore;
        result.redScore = redScore;
        result.bestStreak = bestStreak;
        result.headshots = headshots;
        result.weaponShots = weaponShots.clone();
        result.weaponHits = weaponHits.clone();
        result.weaponKills = weaponKills.clone();
        result.weaponHeadshots = weaponHeadshots.clone();
        result.durationSeconds = clock;
        result.wave = wave;

        float difficultyBonus = config.difficulty.rewardMultiplier();
        int base = player.kills * 45 + player.score / 4 + (victory ? 350 : 90);
        result.coinsEarned = Math.round(base * difficultyBonus);
        result.experienceEarned = Math.round((player.kills * 24 + player.score / 6
                + (victory ? 180 : 60)) * difficultyBonus);
        showBanner(draw ? "DRAW" : victory ? "VICTORY" : "DEFEAT", 6f);
        listener.onSound(victory ? Sfx.ROUND_WIN : Sfx.ROUND_LOSS,
                player.eyeX(), player.eyeY(), player.eyeZ(), 0.8f, 1f);
        listener.onMatchEnd(result);
    }

    // ------------------------------------------------------------------ player

    private void updatePlayer(float dt, InputState input) {
        if (player.alive && state == State.PLAYING) {
            player.buyTimer = Math.max(0f, player.buyTimer - dt);
        }
        // Purchases are handled before the pause check: the buy menu is a modal dialog, so the
        // player is always "paused" while spending.
        for (int purchase = input.consumePurchase(); purchase >= 0;
                purchase = input.consumePurchase()) {
            tryBuy(purchase);
        }

        if (input.paused) {
            input.consumeLook(lookScratch);
            player.firing = false;
            return;
        }

        input.consumeLook(lookScratch);
        if (player.alive) {
            float aimMultiplier = !player.aiming ? 1f
                    : player.weaponType().scoped() ? input.scopeSensitivity : input.adsSensitivity;
            float sensitivity = input.lookSensitivity * aimMultiplier;
            player.yaw = MathUtil.wrapAngle(player.yaw + lookScratch[0] * 0.0031f * sensitivity);
            player.pitch = MathUtil.clamp(player.pitch - lookScratch[1] * 0.0031f * sensitivity,
                    -1.52f, 1.52f);
        }

        if (!player.alive) {
            player.firing = false;
            return;
        }
        if (!combatActive()) {
            player.firing = false;
            player.velocity.set(0f, 0f, 0f);
            return;
        }

        int slotRequest = input.consumeSlotRequest();
        if (slotRequest >= 0) {
            selectPlayerSlot(slotRequest);
        }
        if (input.consumeMelee()) {
            quickMelee(player);
        }
        if (input.consumeGrenade()) {
            throwGrenadeFromSlot(player);
        }
        if (input.consumeReload()) {
            startReload(player);
        }

        Weapon weapon = player.weapon();
        WeaponType type = player.weaponType();
        // Aiming is an optics-only action now: iron sights neither zoom nor change the view, so
        // holding aim on a rifle did nothing visible. Enforced here rather than at each input
        // source, so touch, keyboard and mouse all obey the same rule.
        player.aiming = input.aimHeld && type.scoped();
        boolean bhop = modifier(MatchModifier.BUNNY_HOP);
        boolean wantsCrouch = input.crouching || (bhop && input.slideHeld);
        updateStance(player, wantsCrouch, dt);
        updateSteady(player, dt);
        boolean wantsSprint = input.sprinting && !player.aiming && input.moveY > 0.35f
                && player.crouchBlend < 0.35f;

        // Movement.
        float moveX = input.moveX;
        float moveY = input.moveY;
        float magnitude = (float) Math.sqrt(moveX * moveX + moveY * moveY);
        if (magnitude > 1f) {
            moveX /= magnitude;
            moveY /= magnitude;
            magnitude = 1f;
        }
        player.sprinting = wantsSprint && magnitude > 0.5f;

        float speed = MathUtil.lerp(player.sprinting ? SPRINT_SPEED : WALK_SPEED, CROUCH_SPEED,
                player.crouchBlend);
        speed *= type.moveSpeedMultiplier();
        speed *= movementMultiplier();
        speed *= classMovementMultiplier(player);
        if (objectives != null && objectives.carrierId() == player.id) speed *= 0.85f;
        if (player.aiming) {
            speed *= 0.52f;
        }

        float sin = (float) Math.sin(player.yaw);
        float cos = (float) Math.cos(player.yaw);
        float desiredX = (sin * moveY + cos * moveX) * speed;
        float desiredZ = (-cos * moveY + sin * moveX) * speed;

        float referenceSpeed = SPRINT_SPEED * type.moveSpeedMultiplier() * movementMultiplier()
                * classMovementMultiplier(player);
        if (objectives != null && objectives.carrierId() == player.id) referenceSpeed *= 0.85f;
        if (player.aiming) referenceSpeed *= 0.52f;
        boolean swimming = Swimming.inWater(world, player);
        if (swimming) {
            arenaMovement.reset(); // Water is not a timed landing and cannot award a bhop.
            Swimming.steer(player, desiredX, desiredZ, dt);
        } else {
            arenaMovement.update(player, input, desiredX, desiredZ, referenceSpeed, bhop, dt);
        }

        boolean jumpPressed = input.consumeJump();
        if (!swimming && bhop && jumpPressed) arenaMovement.bufferJump();
        boolean timedJumpPress = jumpPressed || (bhop && arenaMovement.bufferedJump());
        boolean jumpRequested = jumpPressed
                || (bhop && player.grounded && arenaMovement.bufferedJump());
        if (!jumpRequested && input.autoJump && !wantsCrouch && player.grounded
                && magnitude > 0.15f && shouldAutoJump(world, player, desiredX, desiredZ)) {
            jumpRequested = true;
        }
        if (!swimming && jumpRequested) {
            if (wantsCrouch && !(bhop && (arenaMovement.sliding()
                    || arenaMovement.hopReady() || !player.grounded))) {
                // Jumping out of a crouch stands you up rather than hopping; the crouch toggle is
                // released here so the button and the stance agree.
                input.crouching = false;
                updateStance(player, false, dt);
            } else if (player.grounded || (modifier(MatchModifier.DOUBLE_JUMP)
                    && jumpPressed && !player.airJumpUsed)) {
                if (wantsCrouch) {
                    input.crouching = false;
                    updateStance(player, false, dt);
                }
                // A low ceiling must not turn a slide hop into a standing jump through voxels.
                if ((!wantsCrouch || !player.crouching)
                        && JumpRules.jump(player, JUMP_SPEED, modifier(MatchModifier.DOUBLE_JUMP), jumpPressed)) {
                    arenaMovement.onJump(player, bhop && timedJumpPress);
                }
            }
        }

        integrateBody(player, dt, input.jumpHeld);
        updateMovementView(dt);

        // Firing.
        // Movement is never allowed to swallow the trigger. Sprinting already widens the cone and
        // adds camera motion, but the player can keep moving at full speed while firing.
        boolean wantsFire = input.fireHeld && player.switchTimer <= 0f;
        player.firing = wantsFire;
        if (weapon != null) {
            if (wantsFire || weapon.burstPending()) {
                tryFire(player, wantsFire);
            } else {
                weapon.releaseTrigger();
            }
            if (wantsFire && weapon.empty() && (weapon.reserve() > 0 || weapon.infiniteReserve())) {
                startReload(player);
            } else if (wantsFire && weapon.depleted() && !weapon.type().melee()) {
                switchToBackup(player);
            }
        }
    }

    /**
     * Detects a climbable ledge in the actual movement direction.
     *
     * <p>The low probe must hit while the space above it stays clear. A two-block wall therefore
     * remains a wall, and merely holding the stick on flat ground never turns into bunny hopping.
     */
    static boolean shouldAutoJump(VoxelWorld world, Combatant combatant,
                                  float moveX, float moveZ) {
        float length = (float) Math.sqrt(moveX * moveX + moveZ * moveZ);
        if (length < 0.1f) {
            return false;
        }
        float probeX = combatant.position.x + moveX / length * 0.8f;
        float probeZ = combatant.position.z + moveZ / length * 0.8f;
        boolean blockedLow = world.boxCollides(probeX, combatant.position.y + 0.1f, probeZ,
                Combatant.RADIUS, 0.6f);
        boolean clearAbove = !world.boxCollides(probeX, combatant.position.y + 1.2f, probeZ,
                Combatant.RADIUS, 1.0f);
        return blockedLow && clearAbove;
    }

    /** Bounces a body that is standing on a jump pad. */
    private void launchFromJumpPad(Combatant combatant) {
        int y = VoxelWorld.floor(combatant.position.y - 0.1f);
        int x = VoxelWorld.floor(combatant.position.x);
        int z = VoxelWorld.floor(combatant.position.z);
        if (world.get(x, y, z) != Materials.JUMP_PAD) {
            return;
        }
        combatant.velocity.y = JUMP_PAD_SPEED;
        combatant.grounded = false;
        listener.onSound(Sfx.LAND, combatant.position.x, combatant.position.y,
                combatant.position.z, 0.7f, 1.6f);
    }

    /**
     * Blends the crouch pose. Standing back up is refused while something is directly overhead,
     * so nobody pops through a container roof or a low corridor.
     */
    /**
     * Settles or unsettles a stance. Standing still is what earns accuracy now, so movement and
     * jumping wipe it at once while holding position rewards a couple of beats of patience;
     * crouching both settles faster and is worth holding.
     */
    private static void updateSteady(Combatant combatant, float dt) {
        float speed = (float) Math.sqrt(combatant.velocity.x * combatant.velocity.x
                + combatant.velocity.z * combatant.velocity.z);
        boolean planted = combatant.grounded && !combatant.sprinting && speed < 0.35f;
        float target = planted ? 1f : 0f;
        // Settling takes about half a second from a standstill, crouched a little quicker;
        // breaking it is immediate, which is what keeps run-and-gun honest.
        float rate = planted ? (combatant.crouching ? 3.2f : 2.4f) : 14f;
        combatant.steady = MathUtil.damp(combatant.steady, target, rate, Math.max(dt, 1e-4f));
    }

    private void updateStance(Combatant combatant, boolean wantsCrouch, float dt) {
        if (!wantsCrouch && combatant.crouchBlend > 0.01f
                && world.boxCollides(combatant.position.x, combatant.position.y,
                combatant.position.z, Combatant.RADIUS, Combatant.HEIGHT)) {
            wantsCrouch = true;
        }
        combatant.crouching = wantsCrouch;
        combatant.crouchBlend = MathUtil.clamp(
                MathUtil.damp(combatant.crouchBlend, wantsCrouch ? 1f : 0f, 13f, dt), 0f, 1f);
    }

    /** Gravity, collision and footstep bookkeeping for any combatant. */
    private void integrateBody(Combatant combatant, float dt, boolean swimUp) {
        float previousX = combatant.position.x, previousZ = combatant.position.z;
        boolean swimming = Swimming.applyVertical(world, combatant, swimUp, dt);
        if (!swimming) combatant.velocity.y = Math.max(Physics.TERMINAL_VELOCITY,
                combatant.velocity.y + Physics.GRAVITY * dt * (modifier(MatchModifier.LOW_GRAVITY) ? 0.4f : 1f));
        float previousY = combatant.velocity.y;
        float previousFeetY = combatant.position.y;
        boolean wasGrounded = combatant.grounded;

        long collisionStarted = System.nanoTime();
        combatant.grounded = Physics.move(world, combatant.position, combatant.velocity, dt,
                Combatant.RADIUS, combatant.height(), true);
        physicsNanos += System.nanoTime() - collisionStarted;

        if (combatant == player && wasGrounded && combatant.grounded && previousY <= 0f) {
            float rise = combatant.position.y - previousFeetY;
            // A diagonal corner can resolve one supported step on each horizontal axis.
            if (rise > 0.25f && rise <= Physics.STEP_HEIGHT * 2f) {
                stepViewOffset = Math.max(-Physics.STEP_HEIGHT * 2f, stepViewOffset - rise);
            }
        }

        if (combatant.position.y < VOID_LEVEL) {
            // Falling off a floating island is a death, not a quiet teleport.
            combatant.velocity.set(0f, 0f, 0f);
            applyDamage(combatant, null, Combatant.MAX_HEALTH * 4f, false, null);
            if (combatant.alive) {
                respawn(combatant, false);
            }
            return;
        }

        if (combatant.grounded) {
            combatant.airJumpUsed = false;
            launchFromJumpPad(combatant);
        }

        if (combatant == player && !wasGrounded && combatant.grounded && previousY < -1.5f
                && !swimming && !Swimming.inWater(world, combatant)) {
            combatant.landImpact = MathUtil.clamp(-previousY / 22f, 0f, 1f);
        }
        if (!wasGrounded && combatant.grounded && previousY < -6f
                && !swimming && !Swimming.inWater(world, combatant)) {
            combatant.landImpact = MathUtil.clamp(-previousY / 22f, 0f, 1f);
            listener.onSound(Sfx.LAND, combatant.position.x, combatant.position.y,
                    combatant.position.z, combatant.landImpact * 0.7f, 1f);
            if (previousY < -19f && !modifier(MatchModifier.NO_FALL_DAMAGE)) {
                float fallDamage = (-previousY - 19f) * 4.2f;
                applyDamage(combatant, null, fallDamage, false, null);
            }
        }

        float travelled = (float) Math.hypot(combatant.position.x - previousX,
                combatant.position.z - previousZ);
        combatant.walkCycle += travelled * 1.9f;
        if (!swimming && combatant.grounded && travelled > 0.6f * dt) {
            combatant.stepAccumulator += travelled;
            float stride = combatant.crouching ? 3.1f : combatant.sprinting ? 1.9f : 2.4f;
            if (combatant.stepAccumulator >= stride) {
                combatant.stepAccumulator = 0f;
                int surface = world.get(VoxelWorld.floor(combatant.position.x),
                        VoxelWorld.floor(combatant.position.y - 0.1f), VoxelWorld.floor(combatant.position.z));
                listener.onSound(Sfx.footstep(surface), combatant.position.x, combatant.position.y,
                        combatant.position.z, combatant.crouching ? 0.25f : 0.6f,
                        0.9f + random.nextFloat() * 0.25f);
                alertFootsteps(combatant);
            }
        }
    }

    // ------------------------------------------------------------------ weapons

    /** Shared entry point: validates the weapon state and dispatches to the right attack. */
    boolean tryFire(Combatant shooter, boolean triggerPressed) {
        long start = System.nanoTime();
        try { return tryFireInternal(shooter, triggerPressed); }
        finally { firingNanos += System.nanoTime() - start; }
    }

    private boolean tryFireInternal(Combatant shooter, boolean triggerPressed) {
        if (!combatActive() || !shooter.alive) return false;
        if (shooter.bot && config.mode.passiveBots()) return false;
        Weapon weapon = shooter.weapon();
        if (weapon == null || shooter.switchTimer > 0f) {
            return false;
        }
        WeaponType type = weapon.type();
        weapon.setAmmoRules(modifier(MatchModifier.INFINITE_AMMO), modifier(MatchModifier.NO_RELOAD));

        if (!weapon.canFire(triggerPressed)) {
            if (triggerPressed && weapon.empty() && !weapon.reloading() && !type.melee()) {
                if (weapon.reserve() <= 0 && !weapon.infiniteReserve()) {
                    if (shooter == player) {
                        listener.onSound(Sfx.DRY_FIRE, shooter.eyeX(), shooter.eyeY(),
                                shooter.eyeZ(), 0.5f, 1f);
                    }
                } else {
                    startReload(shooter);
                }
            }
            return false;
        }

        weapon.consume();
        shooter.muzzleFlash = 1f;

        if (type.melee()) {
            if (shooter == player) {
                weaponShots[type.ordinal()]++;
                shotConnected = false;
            }
            meleeAttack(shooter, type);
            if (shooter == player && shotConnected) {
                weaponHits[type.ordinal()]++;
            }
            return true;
        }
        if (type.grenade()) {
            spawnGrenade(shooter, type);
            weapon.loadOne();  // chamber the next grenade from the pouch
            return true;
        }
        if (shooter == player) {
            weaponShots[type.ordinal()]++;
            shotConnected = false;
        }
        if (type.projectile()) {
            spawnRocket(shooter, type, weapon);
        } else {
            hitscan(shooter, type, weapon);
        }
        if (shooter == player && shotConnected) {
            weaponHits[type.ordinal()]++;
        }

        shooter.addRecoil(type.recoilVertical(), (random.nextFloat() - 0.5f)
                * type.recoilHorizontal() * 2f);
        switch (type.weaponClass()) {
            case SNIPER: case SHOTGUN: case LAUNCHER: case LMG:
                shooter.screenKick = Math.min(1f, shooter.screenKick + 0.65f); break;
            default: break;
        }
        shooter.aimDirection(aimScratch);
        effects.muzzleSmoke(muzzleX(shooter), muzzleY(shooter), muzzleZ(shooter), aimScratch.x,
                aimScratch.y, aimScratch.z);
        effects.ejectShell(muzzleX(shooter), muzzleY(shooter) - 0.05f, muzzleZ(shooter),
                (float) Math.cos(shooter.yaw), (float) Math.sin(shooter.yaw));
        listener.onSound(type.soundKind(), shooter.eyeX(), shooter.eyeY(), shooter.eyeZ(),
                shooter == player ? 1f : 0.9f, 0.96f + random.nextFloat() * 0.08f);
        alertBots(shooter, type.weaponClass() == com.ccs.voxelstrike.weapon.WeaponClass.SNIPER ? 1.3f
                : 1f);
        return true;
    }

    private void hitscan(Combatant shooter, WeaponType type, Weapon weapon) {
        float spread = shotSpread(shooter, type, weapon);
        int shotFlags = KillContext.shooterFlags(shooter, type);

        for (int pellet = 0; pellet < type.pellets(); pellet++) {
            shooter.aimDirection(aimScratch);
            applyCone(aimScratch, spread);
            fireRay(shooter, type, aimScratch.x, aimScratch.y, aimScratch.z, type.range(),
                    modifier(MatchModifier.ONE_SHOT_RAIL) ? 500f : type.damage(), true, shotFlags);
        }
    }

    private float shotSpread(Combatant shooter, WeaponType type, Weapon weapon) {
        float movement = (float) Math.sqrt(shooter.velocity.x * shooter.velocity.x
                + shooter.velocity.z * shooter.velocity.z) / WALK_SPEED;
        float spread = weapon.spread(shooter.aiming, movement, shooter.crouching,
                !shooter.grounded, shooter.steady);
        BotBrain brain = shooter.bot ? brainFor(shooter) : null;
        if (brain != null) {
            spread = brain.shotSpread(type, spread);
        }
        if (modifier(MatchModifier.ONE_SHOT_RAIL)) spread = 0f;
        return spread;
    }

    /** Traces one bullet, optionally continuing through a thin wall. */
    private void fireRay(Combatant shooter, WeaponType type, float dx, float dy, float dz,
                         float maxDistance, float damage, boolean allowPenetration) {
        fireRay(shooter, type, dx, dy, dz, maxDistance, damage, allowPenetration,
                KillContext.shooterFlags(shooter, type));
    }

    private void fireRay(Combatant shooter, WeaponType type, float dx, float dy, float dz,
                         float maxDistance, float damage, boolean allowPenetration, int shotFlags) {
        float ox = shooter.eyeX();
        float oy = aimingEyeY(shooter);
        float oz = shooter.eyeZ();

        boolean wallHit = world.raycast(ox, oy, oz, dx, dy, dz, maxDistance, false, rayHit);
        float wallDistance = wallHit ? rayHit.distance : maxDistance;

        Combatant victim = null;
        float bestDistance = wallDistance;
        int region = HitProbe.REGION_BODY;
        for (int i = 0; i < combatants.size(); i++) {
            Combatant candidate = combatants.get(i);
            if (candidate == shooter || !candidate.alive || !canDamage(shooter, candidate)) {
                continue;
            }
            if (!probe.intersect(candidate, ox, oy, oz, dx, dy, dz, bestDistance)) {
                continue;
            }
            victim = candidate;
            bestDistance = probe.distance;
            region = probe.region;
        }

        if (victim != null) {
            float falloff = damageFalloff(bestDistance, type.range());
            float multiplier = region == HitProbe.REGION_HEAD ? type.headshotMultiplier()
                    : region == HitProbe.REGION_LEGS ? type.limbMultiplier() : 1f;
            float applied = damage * falloff * multiplier;
            boolean headshot = region == HitProbe.REGION_HEAD;
            effects.bloodBurst(ox + dx * bestDistance, oy + dy * bestDistance,
                    oz + dz * bestDistance, dx, dy, dz, headshot ? 1.4f : 0.8f);
            effects.tracer(muzzleX(shooter), muzzleY(shooter), muzzleZ(shooter),
                    ox + dx * bestDistance, oy + dy * bestDistance, oz + dz * bestDistance, 1f);
            applyDamage(victim, shooter, applied, headshot, type, shotFlags);
            explosiveBullet(shooter, type, ox + dx * bestDistance, oy + dy * bestDistance,
                    oz + dz * bestDistance, shotFlags);
            return;
        }

        if (wallHit) {
            float impactX = rayHit.x, impactY = rayHit.y, impactZ = rayHit.z;
            effects.tracer(muzzleX(shooter), muzzleY(shooter), muzzleZ(shooter), rayHit.x, rayHit.y,
                    rayHit.z, 1f);
            effects.bulletImpact(rayHit.x, rayHit.y, rayHit.z, rayHit.normalX, rayHit.normalY,
                    rayHit.normalZ, rayHit.material);
            listener.onSound(Sfx.WHIZZ, rayHit.x, rayHit.y, rayHit.z, 0.35f,
                    0.9f + random.nextFloat() * 0.3f);

            if (allowPenetration && type.penetration() > 0f) {
                penetrate(shooter, type, dx, dy, dz, maxDistance, damage, shotFlags);
            }
            explosiveBullet(shooter, type, impactX - dx * 0.05f, impactY - dy * 0.05f,
                    impactZ - dz * 0.05f, shotFlags);
            return;
        }

        effects.tracer(muzzleX(shooter), muzzleY(shooter), muzzleZ(shooter), ox + dx * maxDistance,
                oy + dy * maxDistance, oz + dz * maxDistance, 0.6f);
    }

    /** Continues a bullet past a thin wall at reduced damage. */
    private void penetrate(Combatant shooter, WeaponType type, float dx, float dy, float dz,
                           float maxDistance, float damage, int shotFlags) {
        float thickness = 0f;
        float step = 0.25f;
        float x = rayHit.x + dx * 0.05f;
        float y = rayHit.y + dy * 0.05f;
        float z = rayHit.z + dz * 0.05f;
        while (thickness < 1.6f) {
            x += dx * step;
            y += dy * step;
            z += dz * step;
            thickness += step;
            if (!Materials.opaque(world.get(VoxelWorld.floor(x), VoxelWorld.floor(y),
                    VoxelWorld.floor(z)))) {
                break;
            }
        }
        if (thickness >= 1.6f) {
            return;
        }
        float remaining = maxDistance - rayHit.distance - thickness;
        if (remaining <= 0.5f) {
            return;
        }

        float ox = shooter.eyeX();
        float oy = aimingEyeY(shooter);
        float oz = shooter.eyeZ();
        float travelled = rayHit.distance + thickness;
        float exitX = ox + dx * travelled;
        float exitY = oy + dy * travelled;
        float exitZ = oz + dz * travelled;

        Combatant victim = null;
        float bestDistance = remaining;
        int region = HitProbe.REGION_BODY;
        for (int i = 0; i < combatants.size(); i++) {
            Combatant candidate = combatants.get(i);
            if (candidate == shooter || !candidate.alive || !canDamage(shooter, candidate)) {
                continue;
            }
            if (!probe.intersect(candidate, exitX, exitY, exitZ, dx, dy, dz, bestDistance)) {
                continue;
            }
            victim = candidate;
            bestDistance = probe.distance;
            region = probe.region;
        }
        if (victim == null) {
            return;
        }
        float multiplier = region == HitProbe.REGION_HEAD ? type.headshotMultiplier()
                : region == HitProbe.REGION_LEGS ? type.limbMultiplier() : 1f;
        float applied = damage * type.penetration() * multiplier
                * damageFalloff(travelled + bestDistance, type.range());
        effects.bloodBurst(exitX + dx * bestDistance, exitY + dy * bestDistance,
                exitZ + dz * bestDistance, dx, dy, dz, 0.6f);
        applyDamage(victim, shooter, applied, region == HitProbe.REGION_HEAD, type,
                shotFlags | KillContext.WALLBANG);
    }

    private static float damageFalloff(float distance, float range) {
        if (distance <= range * 0.5f) {
            return 1f;
        }
        if (distance <= range) {
            return MathUtil.lerp(1f, 0.55f, (distance - range * 0.5f) / (range * 0.5f));
        }
        return 0.28f;
    }

    private void meleeAttack(Combatant shooter, WeaponType type) {
        int shotFlags = KillContext.shooterFlags(shooter, type);
        shooter.meleeSwing = 1f;
        listener.onSound(Sfx.SWING, shooter.eyeX(), shooter.eyeY(), shooter.eyeZ(),
                shooter == player ? 0.7f : 0.5f, 1f);
        shooter.aimDirection(aimScratch);

        Combatant victim = null;
        float originX = shooter.eyeX();
        float originY = aimingEyeY(shooter);
        float originZ = shooter.eyeZ();
        boolean wallHit = world.raycast(originX, originY, originZ, aimScratch.x, aimScratch.y,
                aimScratch.z, type.range(), false, rayHit);
        float best = wallHit ? rayHit.distance : type.range();
        for (int i = 0; i < combatants.size(); i++) {
            Combatant candidate = combatants.get(i);
            if (candidate == shooter || !candidate.alive || !canDamage(shooter, candidate)) {
                continue;
            }
            if (!probe.intersect(candidate, originX, originY, originZ,
                    aimScratch.x, aimScratch.y, aimScratch.z, best)) {
                continue;
            }
            victim = candidate;
            best = probe.distance;
        }
        if (victim == null) {
            return;
        }
        boolean backstab = Math.abs(MathUtil.angleDelta(victim.yaw, shooter.yaw)) < 1.05f;
        float damage = type.damage() * (backstab ? 1.9f : 1f);
        effects.bloodBurst(victim.position.x, victim.position.y + 1.1f, victim.position.z,
                aimScratch.x, aimScratch.y, aimScratch.z, 1f);
        listener.onSound(Sfx.STAB, victim.position.x, victim.position.y + 1f, victim.position.z,
                0.8f, 1f);
        applyDamage(victim, shooter, damage, false, type, shotFlags);
    }

    /** Selects a carried player weapon and provides the same feedback for every input source. */
    private void selectPlayerSlot(int slot) {
        if (player.selectSlot(slot)) {
            listener.onSound(Sfx.CLICK, player.eyeX(), player.eyeY(), player.eyeZ(), 0.5f, 1.2f);
        }
    }

    /** Melee bash with the equipped blade, without switching weapons. */
    private void quickMelee(Combatant shooter) {
        if (!combatActive() || modifier(MatchModifier.ONE_SHOT_RAIL)) return;
        if (shooter.switchTimer > 0f) {
            return;
        }
        Weapon melee = shooter.weapons[WeaponSlot.MELEE.ordinal()];
        shooter.switchTimer = 0.45f;
        meleeAttack(shooter, melee == null ? WeaponType.KNIFE : melee.type());
    }

    private void throwGrenadeFromSlot(Combatant shooter) {
        if (!combatActive()) return;
        Weapon grenade = shooter.weapons[WeaponSlot.GRENADE.ordinal()];
        if (grenade == null || grenade.depleted() || shooter.switchTimer > 0f) {
            return;
        }
        if (grenade.empty() && !grenade.loadOne()) {
            return;
        }
        grenade.consume();
        shooter.switchTimer = 0.6f;
        spawnGrenade(shooter, grenade.type());
    }

    private void spawnGrenade(Combatant shooter, WeaponType type) {
        Projectile projectile = allocateProjectile();
        if (projectile == null) {
            return;
        }
        shooter.aimDirection(aimScratch);
        float speed = 17f;
        projectile.active = true;
        projectile.type = type;
        projectile.ownerId = shooter.id;
        projectile.team = shooter.team;
        projectile.shotFlags = KillContext.shooterFlags(shooter, type);
        projectile.rocket = false;
        projectile.fuse = type.fuseSeconds();
        projectile.x = shooter.eyeX() + aimScratch.x * 0.6f;
        projectile.y = aimingEyeY(shooter) + aimScratch.y * 0.6f;
        projectile.z = shooter.eyeZ() + aimScratch.z * 0.6f;
        projectile.vx = aimScratch.x * speed + shooter.velocity.x * 0.5f;
        projectile.vy = aimScratch.y * speed + 3.4f;
        projectile.vz = aimScratch.z * speed + shooter.velocity.z * 0.5f;
        listener.onSound(Sfx.PIN, shooter.eyeX(), shooter.eyeY(), shooter.eyeZ(), 0.6f, 1f);
    }

    private void spawnRocket(Combatant shooter, WeaponType type, Weapon weapon) {
        Projectile projectile = allocateProjectile();
        if (projectile == null) {
            return;
        }
        shooter.aimDirection(aimScratch);
        applyCone(aimScratch, shotSpread(shooter, type, weapon));
        projectile.active = true;
        projectile.type = type;
        projectile.ownerId = shooter.id;
        projectile.team = shooter.team;
        projectile.shotFlags = KillContext.shooterFlags(shooter, type);
        projectile.rocket = true;
        projectile.fuse = 6f;
        projectile.x = shooter.eyeX() + aimScratch.x * 0.8f;
        projectile.y = aimingEyeY(shooter) + aimScratch.y * 0.8f;
        projectile.z = shooter.eyeZ() + aimScratch.z * 0.8f;
        projectile.vx = aimScratch.x * 42f;
        projectile.vy = aimScratch.y * 42f;
        projectile.vz = aimScratch.z * 42f;
    }

    private Projectile allocateProjectile() {
        for (Projectile projectile : projectiles) {
            if (!projectile.active) {
                projectile.reset();
                return projectile;
            }
        }
        return null;
    }

    private void updateProjectiles(float dt) {
        for (Projectile projectile : projectiles) {
            if (!projectile.active) {
                continue;
            }
            projectile.age += dt;
            projectile.spin += dt * 9f;
            if (!projectile.rocket) {
                projectile.vy += Physics.GRAVITY * dt * (modifier(MatchModifier.LOW_GRAVITY) ? 0.4f : 1f);
                projectile.vx *= Math.max(0f, 1f - 0.35f * dt);
                projectile.vz *= Math.max(0f, 1f - 0.35f * dt);
            }

            float nx = projectile.x + projectile.vx * dt;
            float ny = projectile.y + projectile.vy * dt;
            float nz = projectile.z + projectile.vz * dt;

            if (projectile.rocket) {
                effects.spawnParticle(Effects.KIND_SMOKE, projectile.x, projectile.y, projectile.z,
                        0f, 0.4f, 0f, 0.7f, 0.22f, 0.6f, 0.58f, 0.56f, 0f);
                Combatant struck = firstCombatantOnSegment(projectile, nx, ny, nz);
                if (struck != null) {
                    detonate(projectile, projectile.x, projectile.y, projectile.z);
                    continue;
                }
            }

            if (world.solidAt(nx, ny, nz)) {
                if (projectile.rocket) {
                    detonate(projectile, projectile.x, projectile.y, projectile.z);
                    continue;
                }
                // Bounce the grenade off the surface it touched.
                if (!world.solidAt(nx, projectile.y, projectile.z)) {
                    projectile.vx *= -0.42f;
                } else if (!world.solidAt(projectile.x, ny, projectile.z)) {
                    projectile.vy *= -0.36f;
                } else {
                    projectile.vz *= -0.42f;
                }
                projectile.vx *= 0.72f;
                projectile.vz *= 0.72f;
                projectile.bounces++;
                if (projectile.bounces == 1 || projectile.bounces == 3) {
                    listener.onSound(Sfx.CLICK, projectile.x, projectile.y, projectile.z, 0.45f,
                            0.7f);
                }
            } else {
                projectile.x = nx;
                projectile.y = ny;
                projectile.z = nz;
            }

            projectile.fuse -= dt;
            if (projectile.fuse <= 0f) {
                detonate(projectile, projectile.x, projectile.y, projectile.z);
            }
        }
    }

    private Combatant firstCombatantOnSegment(Projectile projectile, float nx, float ny, float nz) {
        float dx = nx - projectile.x;
        float dy = ny - projectile.y;
        float dz = nz - projectile.z;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-4f) {
            return null;
        }
        dx /= length;
        dy /= length;
        dz /= length;
        for (int i = 0; i < combatants.size(); i++) {
            Combatant candidate = combatants.get(i);
            if (!candidate.alive || candidate.id == projectile.ownerId) {
                continue;
            }
            if (probe.intersect(candidate, projectile.x, projectile.y, projectile.z, dx, dy, dz,
                    length)) {
                return candidate;
            }
        }
        return null;
    }

    private void detonate(Projectile projectile, float x, float y, float z) {
        WeaponType type = projectile.type;
        projectile.active = false;
        if (type == null) {
            return;
        }

        if (type.flashbang()) {
            effects.spawnParticle(Effects.KIND_FIRE, x, y, z, 0f, 0f, 0f, 0.2f, 2.4f, 1f, 1f, 1f,
                    30f);
            listener.onSound(Sfx.EXPLOSION, x, y, z, 0.7f, 1.8f);
            for (Combatant combatant : combatants) {
                if (!combatant.alive) {
                    continue;
                }
                float distance = distanceTo(combatant, x, y, z);
                if (distance > type.splashRadius() * 2.4f) {
                    continue;
                }
                if (!world.lineOfSight(x, y, z, combatant.eyeX(), combatant.eyeY(),
                        combatant.eyeZ())) {
                    continue;
                }
                combatant.aimDirection(tempVec);
                float toX = x - combatant.eyeX();
                float toY = y - combatant.eyeY();
                float toZ = z - combatant.eyeZ();
                float length = (float) Math.sqrt(toX * toX + toY * toY + toZ * toZ) + 1e-4f;
                float facing = (tempVec.x * toX + tempVec.y * toY + tempVec.z * toZ) / length;
                float strength = MathUtil.clamp(1f - distance / (type.splashRadius() * 2.4f), 0f, 1f);
                combatant.flash = Math.max(combatant.flash,
                        strength * MathUtil.clamp(facing, 0.15f, 1f) * 3.4f);
            }
            return;
        }

        effects.explosion(x, y, z, type.splashRadius());
        listener.onSound(Sfx.EXPLOSION, x, y, z, 1f, 0.95f + random.nextFloat() * 0.1f);

        Combatant owner = combatantById(projectile.ownerId);
        for (Combatant combatant : combatants) {
            if (!combatant.alive) {
                continue;
            }
            float distance = distanceTo(combatant, x, y, z);
            if (distance > type.splashRadius()) {
                continue;
            }
            if (!world.lineOfSight(x, y, z, combatant.eyeX(), combatant.eyeY(), combatant.eyeZ())
                    && distance > 1.5f) {
                continue;
            }
            float falloff = 1f - distance / type.splashRadius();
            float damage = type.splashDamage() * falloff * falloff;
            if (owner == combatant) {
                damage *= 0.55f;  // self-damage still hurts, but does not instantly delete you
            }
            // Blast impulse.
            float px = combatant.position.x - x;
            float py = combatant.position.y + 0.9f - y;
            float pz = combatant.position.z - z;
            float length = (float) Math.sqrt(px * px + py * py + pz * pz) + 1e-3f;
            float impulse = 12f * falloff;
            combatant.velocity.x += px / length * impulse;
            combatant.velocity.y += Math.max(2.5f, py / length * impulse);
            combatant.velocity.z += pz / length * impulse;
            combatant.grounded = false;
            applyDamage(combatant, owner, damage, false, type, projectile.shotFlags);
        }
        if (isBattlefield()) destruction.blast(x, y, z, type.splashRadius());
    }

    private float distanceTo(Combatant combatant, float x, float y, float z) {
        float dx = combatant.position.x - x;
        float dy = combatant.position.y + combatant.height() * 0.5f - y;
        float dz = combatant.position.z - z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Iterative, bounded chain reactions. Death positions are captured before any respawn. */
    private void processDeathBlasts() {
        for (int budget = 0; budget < 128 && deathBlasts.poll(); budget++) {
            Combatant owner = deathBlasts.owner;
            float x = deathBlasts.x, y = deathBlasts.y, z = deathBlasts.z;
            effects.explosion(x, y, z, 5f);
            listener.onSound(Sfx.EXPLOSION, x, y, z, 0.85f, 1f);
            if (!combatActive()) continue;
            for (int i = 0; i < combatants.size(); i++) {
                Combatant target = combatants.get(i);
                if (!target.alive || target == owner || !canDamage(owner, target)) continue;
                float distance = distanceTo(target, x, y, z);
                if (distance >= 5f || !world.lineOfSight(x, y, z, target.eyeX(), target.eyeY(), target.eyeZ())) continue;
                float falloff = 1f - distance / 5f;
            // An automatic death blast is not a new airborne/scoped shot by the dead owner.
            applyDamage(target, owner, 100f * falloff * falloff, false, WeaponType.FRAG, 0);
            }
        }
    }

    void startReload(Combatant combatant) {
        Weapon weapon = combatant.weapon();
        if (weapon != null) weapon.setAmmoRules(modifier(MatchModifier.INFINITE_AMMO), modifier(MatchModifier.NO_RELOAD));
        if (weapon == null || !weapon.startReload()) {
            return;
        }
        listener.onSound(Sfx.MAGAZINE, combatant.eyeX(), combatant.eyeY(), combatant.eyeZ(),
                combatant == player ? 0.75f : 0.45f, 1f);
        combatant.reloadSoundStage = 1;
    }

    private void updateReloadAudio(Combatant c) {
        Weapon w = c.weapon();
        if (!c.alive || w == null || !w.reloading()) { c.reloadSoundStage = 0; return; }
        if (c.reloadSoundStage == 1 && w.reloadProgress() >= 0.52f) {
            c.reloadSoundStage = 2;
            listener.onSound(Sfx.MAG_INSERT, c.eyeX(), c.eyeY(), c.eyeZ(), 0.5f, 1f);
        }
        if (c.reloadSoundStage == 2 && w.reloadProgress() >= 0.88f) {
            c.reloadSoundStage = 3;
            listener.onSound(Sfx.BOLT, c.eyeX(), c.eyeY(), c.eyeZ(), 0.55f, 1f);
        }
    }

    private void explosiveBullet(Combatant shooter, WeaponType type, float x, float y, float z,
                                 int shotFlags) {
        if (!modifier(MatchModifier.EXPLOSIVE_ROUNDS) || modifier(MatchModifier.HEADSHOTS_ONLY)) return;
        effects.explosion(x, y, z, 1.2f);
        listener.onSound(Sfx.EXPLOSION, x, y, z, 0.3f, 1.8f);
        for (Combatant c : combatants) {
            if (!c.alive || !canDamage(shooter, c)) continue;
            float distance = distanceTo(c, x, y, z);
            if (distance < 2.5f && world.lineOfSight(x, y, z, c.eyeX(), c.eyeY(), c.eyeZ()))
                applyDamage(c, shooter, 24f * (1f - distance / 2.5f), false, type, shotFlags);
        }
    }

    private void switchToBackup(Combatant combatant) {
        for (int i = 0; i < combatant.weapons.length; i++) {
            Weapon candidate = combatant.weapons[i];
            if (candidate != null && i != combatant.slot && !candidate.depleted()) {
                combatant.selectSlot(i);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ damage and scoring

    void applyDamage(Combatant victim, Combatant attacker, float amount, boolean headshot,
                     WeaponType weapon) {
        applyDamage(victim, attacker, amount, headshot, weapon,
                KillContext.shooterFlags(attacker, weapon));
    }

    void applyDamage(Combatant victim, Combatant attacker, float amount, boolean headshot,
                     WeaponType weapon, int shotFlags) {
        if (!victim.alive || amount <= 0f) {
            return;
        }
        if (attacker != null && attacker != victim && !canDamage(attacker, victim)) return;
        if (attacker != null && !headshot && modifier(MatchModifier.HEADSHOTS_ONLY)) return;
        boolean armored = victim.armor > 0f;
        float fromYaw = attacker == null ? victim.yaw
                : (float) Math.atan2(attacker.position.x - victim.position.x,
                        -(attacker.position.z - victim.position.z));
        float oldHealth = victim.health;
        boolean lethal = victim.applyDamage(amount, attacker == null ? -1 : attacker.id, fromYaw);
        if (modifier(MatchModifier.VAMPIRE) && attacker != null && attacker.alive
                && attacker != victim && hostile(attacker, victim)) {
            // Actual health lost, not incoming damage, armour absorption or lethal overkill.
            float maximum = attacker.zombie ? 130f + wave * 18f : Combatant.MAX_HEALTH;
            if (attacker.health < maximum) attacker.health = Math.min(maximum,
                    attacker.health + (oldHealth - victim.health) * 0.25f);
        }

        if (victim == player) {
            listener.onLocalDamage(amount, fromYaw);
        }
        if (attacker == player && victim != player) {
            boolean friendlyFireHit = hardcore() && friendly(attacker, victim);
            shotConnected = !friendlyFireHit;
            hitMarker = 1f;
            hitMarkerLethal = lethal;
            hitKind = headshot ? 2 : armored ? 1 : 0;
            listener.onSound(headshot ? Sfx.HEADSHOT : armored ? Sfx.ARMOR_HIT : Sfx.HIT_MARKER,
                    player.eyeX(), player.eyeY(), player.eyeZ(), 0.55f, lethal ? 1.15f : 1f);
            listener.onLocalHit(headshot, lethal);
            if (!friendlyFireHit) {
                player.score += headshot ? 18 : 10;
            }
            if (headshot && !friendlyFireHit) {
                headshots++;
            }
        }
        if (lethal) {
            registerKill(attacker, victim, weapon, headshot,
                    KillContext.complete(shotFlags, victim, headshot));
            if (victim == player && attacker != null && attacker != player && killCam != null) {
                killCam.start(combatants.indexOf(attacker));
            }
        }
    }

    private void registerKill(Combatant attacker, Combatant victim, WeaponType weapon,
                              boolean headshot, int flags) {
        if (conquest != null) conquest.onDeath(victim);
        else if (battlefront != null) battlefront.onDeath(victim);
        victim.respawnTimer = config.mode.passiveBots() && victim != player ? RANGE_RESPAWN_SECONDS
                : config.mode.survival() && victim == player ? 2.0f : RESPAWN_SECONDS;
        victim.velocity.set(0f, 0f, 0f);
        if (modifier(MatchModifier.EXPLOSIVE_DEATH)) deathBlasts.add(victim);
        if (conquest != null && state == State.PLAYING && !soloExploration()) conquest.death(victim.team);
        listener.onSound(Sfx.DEATH, victim.position.x, victim.position.y + 1f, victim.position.z,
                0.7f, 0.9f + random.nextFloat() * 0.2f);

        String attackerName = attacker == null ? "World" : attacker.name;
        int attackerColor = attacker == null ? Team.NONE.color() : attacker.team.color();
        boolean involvesPlayer = attacker == player || victim == player;
        // Legacy environment damage used KNIFE as a placeholder. Never render it as melee.
        WeaponType feedWeapon = attacker == null && weapon == WeaponType.KNIFE ? null : weapon;
        pushFeed(attackerName, attackerColor, victim.name, victim.team.color(),
                feedWeapon == null ? "Fall" : feedWeapon.displayName(), headshot, involvesPlayer,
                feedWeapon == null ? -1 : feedWeapon.ordinal(), flags);

        if (attacker == null || attacker == victim) {
            return;
        }
        if (hardcore() && friendly(attacker, victim)) {
            // Friendly fire is a risk, never a source of progress. The point penalty also means
            // repeated careless fire can cost a close match without producing negative scores.
            attacker.score = Math.max(0, attacker.score - 100);
            if (attacker.team == Team.BLUE) {
                blueScore = Math.max(0, blueScore - 1);
            } else if (attacker.team == Team.RED) {
                redScore = Math.max(0, redScore - 1);
            }
            return;
        }
        attacker.kills++;
        attacker.streak++;
        attacker.score += headshot ? 150 : 100;
        if (config.mode.buyMenu() && !config.mode.freeArsenal()) {
            attacker.money = Economy.clamp(attacker.money + Economy.killReward(weapon)
                    + (headshot ? Economy.HEADSHOT_BONUS : 0));
            victim.money = Economy.clamp(victim.money + Economy.DEATH_CONSOLATION);
        }
        if (attacker == player) {
            bestStreak = Math.max(bestStreak, player.streak);
            if (weapon != null) {
                weaponKills[weapon.ordinal()]++;
                if (headshot) {
                    weaponHeadshots[weapon.ordinal()]++;
                }
            }
        }

        if (conquest != null) {
            // Ticket loss is charged to the victim, including falls and friendly fire.
        } else if (config.mode.teamBased() && !config.mode.objective()) {
            if (attacker.team == Team.BLUE) {
                blueScore++;
            } else if (attacker.team == Team.RED) {
                redScore++;
            }
            int limit = scoreLimit();
            if (blueScore >= limit || redScore >= limit) {
                finish(blueScore > redScore, false);
            }
        } else if (config.mode == GameMode.GUN_GAME) {
            advanceLadder(attacker);
        } else if (!config.mode.objective() && scoreLimit() > 0) {
            if (attacker.kills >= scoreLimit()) {
                finish(attacker == player, false);
            }
        }
    }

    private void advanceLadder(Combatant combatant) {
        combatant.ladderStep++;
        if (combatant.ladderStep >= GUN_GAME_LADDER.length) {
            finish(combatant == player, false);
            return;
        }
        WeaponType next = GUN_GAME_LADDER[combatant.ladderStep];
        combatant.setSingleWeapon(next);
        combatant.weapons[WeaponSlot.MELEE.ordinal()] = new Weapon(WeaponType.KNIFE);
        combatant.slot = next.slot().ordinal();
        combatant.switchTimer = 0.35f;
        if (combatant == player) {
            showBanner("LEVEL " + (combatant.ladderStep + 1) + " · " + next.displayName(), 2f);
            listener.onSound(Sfx.UI, player.eyeX(), player.eyeY(), player.eyeZ(), 0.7f, 1.3f);
        }
    }

    private void pushFeed(String attacker, int attackerColor, String victim, int victimColor,
                          String weapon, boolean headshot, boolean involvesPlayer,
                          int weaponOrdinal, int flags) {
        for (int i = feed.length - 1; i > 0; i--) {
            KillFeedEntry target = feed[i];
            KillFeedEntry source = feed[i - 1];
            target.copyFrom(source);
        }
        feed[0].set(attacker, attackerColor, victim, victimColor, weapon, headshot, involvesPlayer,
                weaponOrdinal, flags);
        listener.onKillFeed(feed[0]);
    }

    private void updateRespawn(Combatant combatant, float dt) {
        if (config.mode == GameMode.BOMB_PLANT && !soloExploration()) { combatant.respawnTimer = 0f; return; }
        combatant.respawnTimer -= dt;
        if (combatant.respawnTimer > 0f) {
            return;
        }
        if (config.mode.survival() && combatant == player && !soloExploration()) {
            return;  // survival is one life; the rules check ends the match
        }
        respawn(combatant, false);
    }

    private void respawn(Combatant combatant, boolean initial) {
        if (combatant == player) {
            arenaMovement.reset();
            resetMovementView();
            if (movementTrial != null) movementTrial.reset();
        }
        int equippedSlot = combatant.slot;
        SpawnPoint spawn = pickSpawn(combatant);
        float x = spawn == null ? world.sizeX() * 0.5f : spawn.x;
        float z = spawn == null ? world.sizeZ() * 0.5f : spawn.z;
        // Resolve to the first level a body actually fits on, so a spawn inside a building puts
        // you on its floor rather than on its roof.
        float preferred = spawn == null ? 4f : spawn.y;
        int level = world.standableLevel(VoxelWorld.floor(x), VoxelWorld.floor(z),
                (int) preferred, 2);
        float y = level >= 0 ? level : preferred;
        float yaw = spawn == null ? 0f : spawn.yaw;
        if (!initial && conquest != null) {
            float[] forward = conquest.deploymentFor(combatant);
            if (forward != null) { x = forward[0]; y = forward[1]; z = forward[2]; yaw = forward[3]; }
        } else if (!initial && battlefront != null) {
            float[] forward = battlefront.deploymentFor(combatant);
            if (forward != null) { x = forward[0]; y = forward[1]; z = forward[2]; yaw = forward[3]; }
        }
        combatant.resetForSpawn(x, y, z, yaw);
        if (initial && modifier(MatchModifier.MIRROR_LOADOUT)) {
            System.arraycopy(mirroredKit, 0, combatant.kit, 0, mirroredKit.length);
            combatant.applyKit();
        }
        Physics.resolveSpawn(world, combatant.position, Combatant.RADIUS, Combatant.HEIGHT);

        if (combatant.zombie) {
            combatant.health = 130f + wave * 18f;
        }
        if (!initial) {
            for (int i = 0; i < brains.size(); i++) {
                if (brains.get(i).owns(combatant)) {
                    brains.get(i).onRespawn();
                }
            }
        }
        combatant.buyTimer = Economy.BUY_WINDOW_SECONDS;
        if (!initial && !config.mode.knifeOnly() && config.mode != GameMode.GUN_GAME) {
            combatant.applyKit();
        }
        if (config.mode.knifeOnly() && !initial) {
            combatant.setSingleWeapon(WeaponType.KNIFE);
        }
        if (config.mode == GameMode.GUN_GAME && !initial) {
            int step = MathUtil.clamp(combatant.ladderStep, 0, GUN_GAME_LADDER.length - 1);
            combatant.setSingleWeapon(GUN_GAME_LADDER[step]);
            combatant.weapons[WeaponSlot.MELEE.ordinal()] = new Weapon(WeaponType.KNIFE);
        }
        if (modifier(MatchModifier.RANDOM_WEAPONS)) {
            combatant.setLoadout(RANDOM_GUNS[random.nextInt(RANDOM_GUNS.length)],
                    WeaponType.P9, WeaponType.KNIFE,
                    combatant.bot && modifier(MatchModifier.HEADSHOTS_ONLY)
                            ? WeaponType.FLASH : WeaponType.FRAG);
        }
        if (modifier(MatchModifier.ONE_SHOT_RAIL)) combatant.setSingleWeapon(WeaponType.SCOUT_V);
        configureWeapons(combatant);
        if (!initial && combatant == player && keepsEquippedWeaponAfterRespawn()
                && equippedSlot >= 0 && equippedSlot < combatant.weapons.length
                && combatant.weapons[equippedSlot] != null) {
            combatant.slot = equippedSlot;
        }
    }

    private boolean keepsEquippedWeaponAfterRespawn() {
        return config.mode == GameMode.TEAM_DEATHMATCH || config.mode == GameMode.DEATHMATCH
                || config.mode == GameMode.KING_OF_THE_HILL
                || config.mode == GameMode.CAPTURE_POINT || isBattlefield();
    }

    /** Picks a clear friendly spawn away from enemies and their direct line of sight. */
    private SpawnPoint pickSpawn(Combatant combatant) {
        List<SpawnPoint> pool;
        if (config.mode.teamBased() || config.mode == GameMode.ZOMBIE) {
            pool = combatant.team == Team.RED ? map.redSpawns() : map.blueSpawns();
        } else {
            pool = map.freeSpawns();
        }
        if (pool.isEmpty()) {
            return null;
        }

        SpawnPoint best = null;
        float bestScore = -Float.MAX_VALUE;
        boolean bestOccupied = true;
        int start = random.nextInt(pool.size());
        for (int attempt = 0; attempt < pool.size(); attempt++) {
            SpawnPoint candidate = pool.get((start + attempt) % pool.size());
            if (objectives != null && !objectives.canSpawn(candidate)) continue;
            int level = world.standableLevel(VoxelWorld.floor(candidate.x),
                    VoxelWorld.floor(candidate.z), (int) candidate.y, 2);
            if (level < 0 || world.boxCollides(candidate.x, level, candidate.z,
                    Combatant.RADIUS, Combatant.HEIGHT)) continue;

            float nearestHostile = 1600f;
            float nearestCombatant = 100f;
            int visibleHostiles = 0;
            for (Combatant other : combatants) {
                if (other == combatant || !other.alive) continue;
                float dx = other.position.x - candidate.x;
                float dz = other.position.z - candidate.z;
                float distanceSquared = dx * dx + dz * dz;
                nearestCombatant = Math.min(nearestCombatant, distanceSquared);
                if (!hostile(combatant, other)) continue;
                nearestHostile = Math.min(nearestHostile, distanceSquared);
                if (distanceSquared < 1600f && world.lineOfSight(candidate.x,
                        level + Combatant.EYE_HEIGHT, candidate.z, other.eyeX(), other.eyeY(),
                        other.eyeZ())) {
                    visibleHostiles++;
                }
            }
            boolean occupied = nearestCombatant < SPAWN_CLEAR_RADIUS_SQUARED;
            float score = nearestHostile + nearestCombatant * 0.15f
                    - visibleHostiles * SPAWN_VISIBLE_ENEMY_PENALTY;
            if (best == null || bestOccupied && !occupied
                    || bestOccupied == occupied && score > bestScore) {
                bestOccupied = occupied;
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ helpers

    /** Team rules: in free-for-all everyone is hostile, in team modes only the other side is. */
    public boolean hostile(Combatant a, Combatant b) {
        if (a == b) {
            return false;
        }
        if (a.team == Team.NONE || b.team == Team.NONE) {
            return true;
        }
        return a.team != b.team;
    }

    /**
     * Damage eligibility is intentionally separate from hostility: hardcore bullets can strike a
     * teammate who crosses the line of fire, but bots must never select that teammate as a target.
     */
    public boolean canDamage(Combatant attacker, Combatant victim) {
        if (attacker == victim) {
            return true;
        }
        return hostile(attacker, victim) || hardcore() && friendly(attacker, victim);
    }

    private static boolean friendly(Combatant a, Combatant b) {
        return a != null && b != null && a != b && a.team != Team.NONE && a.team == b.team;
    }

    public Combatant combatantById(int id) {
        for (Combatant combatant : combatants) {
            if (combatant.id == id) {
                return combatant;
            }
        }
        return null;
    }

    private BotBrain brainFor(Combatant combatant) {
        int id = combatant.id;
        return id >= 0 && id < brainById.length ? brainById[id] : null;
    }

    /** Lets every nearby bot know where a shot came from. */
    private void alertBots(Combatant shooter, float loudness) {
        for (Combatant listener : combatants) {
            BotBrain brain = brainFor(listener);
            if (brain != null && listener.alive && hostile(listener, shooter)) {
                brain.hearGunshot(shooter.position.x, shooter.position.y, shooter.position.z,
                        loudness);
            }
        }
    }

    /** Short-range cues only: walking and crouching are quieter than running. */
    private void alertFootsteps(Combatant walker) {
        float range = walker.crouching ? 2.5f : walker.sprinting ? 14f : 8f;
        for (Combatant listener : combatants) {
            BotBrain brain = brainFor(listener);
            if (brain != null && listener.alive && hostile(listener, walker)) {
                brain.hearFootstep(walker.position.x, walker.position.y, walker.position.z, range);
            }
        }
    }

    /** Randomly deflects a direction inside a cone of the given half-angle in degrees. */
    void applyCone(Vec3 direction, float degrees) {
        if (degrees <= 0f) {
            return;
        }
        float radians = (float) Math.toRadians(degrees);
        float angle = random.nextFloat() * MathUtil.TAU;
        float radius = (float) Math.sqrt(random.nextFloat()) * radians;

        // Build an orthonormal basis around the direction and tilt inside it.
        float ux;
        float uy;
        float uz;
        if (Math.abs(direction.y) < 0.9f) {
            ux = -direction.z;
            uy = 0f;
            uz = direction.x;
        } else {
            ux = 1f;
            uy = 0f;
            uz = 0f;
        }
        float length = (float) Math.sqrt(ux * ux + uy * uy + uz * uz) + 1e-6f;
        ux /= length;
        uy /= length;
        uz /= length;
        float vx = direction.y * uz - direction.z * uy;
        float vy = direction.z * ux - direction.x * uz;
        float vz = direction.x * uy - direction.y * ux;

        float offsetX = (float) (Math.cos(angle) * Math.tan(radius));
        float offsetY = (float) (Math.sin(angle) * Math.tan(radius));
        direction.x += ux * offsetX + vx * offsetY;
        direction.y += uy * offsetX + vy * offsetY;
        direction.z += uz * offsetX + vz * offsetY;
        direction.normalize();
    }

    float muzzleX(Combatant combatant) {
        combatant.aimDirection(tempVec);
        return combatant.eyeX() + tempVec.x * 0.55f + (float) Math.cos(combatant.yaw) * 0.18f;
    }

    float muzzleY(Combatant combatant) {
        combatant.aimDirection(tempVec);
        return aimingEyeY(combatant) + tempVec.y * 0.55f - 0.12f;
    }

    float muzzleZ(Combatant combatant) {
        combatant.aimDirection(tempVec);
        return combatant.eyeZ() + tempVec.z * 0.55f + (float) Math.sin(combatant.yaw) * 0.18f;
    }

    private void showBanner(String text, float seconds) {
        banner = text;
        bannerLife = seconds;
    }

    // ------------------------------------------------------------------ camera and HUD

    private void resetMovementView() {
        stepViewOffset = landingViewOffset = weaponJumpOffset = 0f;
        player.landImpact = player.viewBob = 0f;
    }

    private void updateMovementView(float dt) {
        float bobTarget = 0f;
        float planarSpeed = (float) Math.sqrt(player.velocity.x * player.velocity.x
                + player.velocity.z * player.velocity.z);
        if (player.alive && player.grounded && planarSpeed > 0.4f) {
            bobTarget = (float) Math.sin(player.walkCycle * 2f) * 0.035f * (planarSpeed / WALK_SPEED);
        }
        player.viewBob = MathUtil.damp(player.viewBob, bobTarget, 14f, Math.max(dt, 1e-4f));

        stepViewOffset = MathUtil.damp(stepViewOffset, 0f, 12f, dt);
        landingViewOffset = MathUtil.damp(landingViewOffset, player.landImpact * 0.22f, 18f, dt);
        float weaponTarget = MathUtil.clamp(-player.velocity.y * 0.008f
                - player.landImpact * 0.08f, -0.08f, 0.06f);
        weaponJumpOffset = MathUtil.damp(weaponJumpOffset, weaponTarget, 14f, dt);
    }

    /** Local attacks use the same bounded viewpoint as the first-person camera. */
    private float aimingEyeY(Combatant combatant) {
        if (combatant != player) return combatant.eyeY();
        return MathUtil.clamp(player.eyeY() + player.viewBob + stepViewOffset
                - landingViewOffset, player.position.y + 0.18f,
                player.position.y + player.height() - 0.1f);
    }

    private void updateCamera(float dt) {
        if (!player.alive) resetMovementView();
        if (player.alive) {
            cameraPosition.set(player.eyeX(), aimingEyeY(player), player.eyeZ());
            cameraYaw = player.yaw + player.recoilYaw;
            cameraPitch = MathUtil.clamp(player.pitch + player.recoilPitch, -1.55f, 1.55f);
            cameraPitch = MathUtil.clamp(cameraPitch + player.screenKick * 0.012f, -1.55f, 1.55f);
            cameraRoll = player.screenKick * 0.008f;
        } else if (killCam != null && killCam.playing() && killCam.advance(dt, killCamCamera)) {
            // Killcam: the last seconds from the killer's eyes, so the angle that got you is
            // visible rather than guessed at.
            cameraPosition.set(killCamCamera[0], killCamCamera[1], killCamCamera[2]);
            cameraYaw = killCamCamera[3];
            cameraPitch = MathUtil.clamp(killCamCamera[4], -1.55f, 1.55f);
            cameraRoll = 0f;
        } else {
            // Death camera: drift up and look toward the killer.
            cameraPosition.y = MathUtil.damp(cameraPosition.y, player.position.y + 2.4f, 3f,
                    Math.max(dt, 1e-4f));
            Combatant killer = combatantById(player.lastAttackerId);
            if (killer != null && killer.alive) {
                float targetYaw = (float) Math.atan2(killer.position.x - cameraPosition.x,
                        -(killer.position.z - cameraPosition.z));
                cameraYaw = MathUtil.approachAngle(cameraYaw, targetYaw, 3f * dt);
                float dy = killer.position.y + 1.2f - cameraPosition.y;
                float horizontal = Vec3.horizontalDistance(killer.position, cameraPosition) + 1e-3f;
                cameraPitch = MathUtil.damp(cameraPitch, (float) Math.atan2(dy, horizontal), 4f,
                        Math.max(dt, 1e-4f));
            }
        }

        WeaponType type = player.weaponType();
        if (type.melee()) {
            cameraFovScale = 1f;
            scopeBlend = 0f;
        } else {
            float target = player.aiming ? 1f / type.zoom() : 1f;
            cameraFovScale = MathUtil.damp(cameraFovScale, target, 16f, Math.max(dt, 1e-4f));

            boolean scoped = player.alive && player.aiming && type.scoped();
            scopeBlend = MathUtil.clamp(
                    MathUtil.damp(scopeBlend, scoped ? 1f : 0f, 18f, Math.max(dt, 1e-4f)), 0f, 1f);
        }
    }

    private void publishHudIfNeeded(float dt) {
        hudAccumulator += dt;
        if (hudAccumulator + 1e-6f < HUD_STEP) {
            return;
        }
        float elapsed = hudAccumulator;
        hudAccumulator = 0f;
        publishSnapshot(elapsed);
    }

    private void publishSnapshot(float elapsed) {
        snapshotIndex ^= 1;
        HudSnapshot out = snapshots[snapshotIndex];
        Weapon weapon = player.weapon();
        WeaponType type = player.weaponType();

        out.alive = player.alive;
        out.speed = (float) Math.sqrt(player.velocity.x * player.velocity.x + player.velocity.z * player.velocity.z);
        out.sliding = arenaMovement.sliding();
        out.bhop = modifier(MatchModifier.BUNNY_HOP);
        out.movementTrial = movementTrial != null;
        out.modifiers = config.modifiers;
        out.courseMillis = movementTrial == null ? 0 : movementTrial.millis();
        out.courseBestMillis = movementTrial == null ? 0 : movementTrial.bestMillis();
        out.courseGate = movementTrial == null ? 0 : movementTrial.nextGate();
        out.courseRunning = movementTrial != null && movementTrial.running();
        out.courseFinished = movementTrial != null && movementTrial.finished();
        out.courseMistakes = movementTrial == null ? 0 : movementTrial.mistakes();
        out.courseRetryRecommended = movementTrial != null && movementTrial.retryRecommended();
        out.courseGhost = movementTrial != null && movementTrial.ghost().hasBest();
        out.health = player.health;
        out.armor = player.armor;
        out.ammo = weapon == null ? 0 : weapon.ammo();
        out.reserve = weapon == null ? 0 : weapon.reserve();
        out.infiniteAmmo = weapon != null && weapon.infiniteReserve();
        out.noReload = weapon != null && weapon.noReload();
        out.hardcoreHud = hideTeamIndicators();
        out.areaName = map.scenery().area(player.position.x, player.position.z);
        out.weaponName = type.displayName();
        out.slot = player.slot;
        out.reloading = weapon != null && weapon.reloading();
        out.reloadProgress = weapon == null ? 1f : weapon.reloadProgress();
        out.melee = type.melee();
        out.grenade = type.grenade();
        Weapon grenadeSlot = player.weapons[WeaponSlot.GRENADE.ordinal()];
        out.grenadeCount = grenadeSlot == null ? 0 : grenadeSlot.ammo() + grenadeSlot.reserve();
        out.money = player.money;
        out.buyOpen = buyOpen();
        out.freeArsenal = config.mode.freeArsenal();
        out.buyTimeLeft = player.alive ? player.buyTimer : 0f;
        for (int i = 0; i < out.kit.length; i++) {
            WeaponType carried = player.kit[i];
            out.kit[i] = carried == null ? -1 : carried.ordinal();
            out.kitSkin[i] = carried == null ? 0 : player.weaponSkin(carried);
        }

        out.mode = config.mode;
        out.blueScore = blueScore;
        out.redScore = redScore;
        out.scoreLimit = scoreLimit();
        out.timed = timeLimitSeconds() > 0f;
        out.timeLeft = Math.max(0f, timeLimitSeconds() - clock);
        out.wave = wave;

        int enemiesAlive = 0;
        for (Combatant combatant : combatants) {
            if (combatant != player && combatant.alive && hostile(player, combatant)) {
                enemiesAlive++;
            }
        }
        out.enemiesAlive = enemiesAlive;

        out.kills = player.kills;
        out.deaths = player.deaths;
        out.streak = player.streak;
        out.ladderStep = player.ladderStep;
        out.ladderTotal = GUN_GAME_LADDER.length;

        float movement = (float) Math.sqrt(player.velocity.x * player.velocity.x
                + player.velocity.z * player.velocity.z) / WALK_SPEED;
        out.crosshairSpread = weapon == null ? 1f
                : weapon.spread(player.aiming, movement, player.crouching, !player.grounded,
                        player.steady);
        out.hitMarker = hitMarker;
        out.hitMarkerLethal = hitMarkerLethal;
        out.hitKind = hitKind;
        out.crosshairPreset = config.crosshair;
        out.damageDirection = player.lastDamageYaw - player.yaw;
        out.damageAmount = player.damageFlash;
        out.flash = MathUtil.clamp(player.flash, 0f, 1f);
        out.respawnTimer = player.alive ? 0f : Math.max(0f, player.respawnTimer);
        out.killCam = killCam != null && killCam.playing() && !player.alive;
        out.aiming = player.aiming;
        out.scoped = player.aiming && type.scoped();
        out.hasScope = type.scoped();
        if (config.mode.passiveBots()) {
            // The range keeps its own score: the career tallies deliberately ignore practice.
            int shots = 0, hits = 0, headshots = 0;
            for (int i = 0; i < weaponShots.length; i++) {
                shots += weaponShots[i];
                hits += weaponHits[i];
                headshots += weaponHeadshots[i];
            }
            out.rangeShots = shots;
            out.rangeHits = hits;
            out.rangeHeadshots = headshots;
        }
        out.scopeBlend = scopeBlend;
        out.zoom = type.zoom();

        out.playerX = player.position.x;
        out.playerZ = player.position.z;
        out.playerYaw = player.yaw;
        if (objectives != null) objectives.snapshot(out);
        out.conquest = false;
        if (conquest != null) conquest.snapshot(out);
        if (soloExploration() && out.objectiveCount > 0) {
            out.objectiveStatus = "SOLO EXPLORATION";
            out.objectiveSeconds = 0f;
        }

        int blips = 0;
        for (Combatant combatant : combatants) {
            if (modifier(MatchModifier.HARDCORE_HUD) || blips >= HudSnapshot.MAX_BLIPS
                    || combatant == player || !combatant.alive) {
                continue;
            }
            boolean enemy = hostile(player, combatant);
            if (hardcore() && !enemy) {
                continue;  // no persistent friendly tracking in hardcore
            }
            if (enemy) {
                float dx = combatant.position.x - player.position.x;
                float dy = combatant.position.y - player.position.y;
                float dz = combatant.position.z - player.position.z;
                if (dx * dx + dy * dy + dz * dz >= 55f * 55f) {
                    continue;
                }
                boolean visible = world.lineOfSight(player.eyeX(), player.eyeY(), player.eyeZ(),
                        combatant.eyeX(), combatant.eyeY(), combatant.eyeZ());
                if (!visible) {
                    continue;
                }
            }
            out.blipX[blips] = combatant.position.x;
            out.blipZ[blips] = combatant.position.z;
            out.blipColor[blips] = enemy ? Team.RED.color() : Team.BLUE.color();
            out.blipEnemy[blips] = enemy;
            blips++;
        }
        out.blipCount = blips;

        int feedCount = 0;
        for (KillFeedEntry entry : feed) {
            entry.life = Math.max(0f, entry.life - elapsed);
            if (entry.life <= 0f || feedCount >= HudSnapshot.MAX_FEED) {
                continue;
            }
            out.feedAttacker[feedCount] = entry.attacker;
            out.feedVictim[feedCount] = entry.victim;
            out.feedWeapon[feedCount] = entry.weapon;
            out.feedAttackerColor[feedCount] = entry.attackerColor;
            out.feedVictimColor[feedCount] = entry.victimColor;
            out.feedHeadshot[feedCount] = entry.headshot;
            out.feedLife[feedCount] = entry.life;
            out.feedWeaponOrdinal[feedCount] = entry.weaponOrdinal;
            out.feedFlags[feedCount] = entry.flags;
            out.feedInvolvesPlayer[feedCount] = entry.involvesPlayer;
            feedCount++;
        }
        out.feedCount = feedCount;

        int rows = 0;
        for (Combatant combatant : combatants) {
            if (rows >= HudSnapshot.MAX_SCOREBOARD) {
                break;
            }
            out.rowName[rows] = combatant.name;
            out.rowKills[rows] = combatant.kills;
            out.rowDeaths[rows] = combatant.deaths;
            out.rowScore[rows] = combatant.score;
            out.rowColor[rows] = combatant.team.color();
            out.rowIsPlayer[rows] = combatant == player;
            rows++;
        }
        out.scoreboardCount = rows;

        out.finished = state == State.FINISHED;
        out.banner = bannerLife > 0f ? banner : "";
        out.bannerLife = bannerLife;
        out.fps = fps;
        out.frameP95 = frameP95;
        for (int i = 0; i < out.cpuMicros.length; i++) out.cpuMicros[i] = metrics.averageMicros(i);
        out.meshingMicros = meshingMicros;
        out.thermalStatus = thermalStatus;
        out.viewDistance = liveViewDistance;
        out.effectPercent = Math.round(effectFraction * 100f);
        published = out;
    }

    /** Ray versus combatant test, reused to avoid allocation. */
    static final class HitProbe {
        static final int REGION_BODY = 0;
        static final int REGION_HEAD = 1;
        static final int REGION_LEGS = 2;

        float distance;
        int region;

        boolean intersect(Combatant target, float ox, float oy, float oz, float dx, float dy,
                          float dz, float maxDistance) {
            float minX = target.position.x - Combatant.RADIUS;
            float maxX = target.position.x + Combatant.RADIUS;
            float minY = target.position.y;
            float maxY = target.position.y + target.height();
            float minZ = target.position.z - Combatant.RADIUS;
            float maxZ = target.position.z + Combatant.RADIUS;

            float tMin = 0f;
            float tMax = maxDistance;

            float inverse = dx != 0f ? 1f / dx : 1e30f;
            float t1 = (minX - ox) * inverse;
            float t2 = (maxX - ox) * inverse;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));

            inverse = dy != 0f ? 1f / dy : 1e30f;
            t1 = (minY - oy) * inverse;
            t2 = (maxY - oy) * inverse;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));

            inverse = dz != 0f ? 1f / dz : 1e30f;
            t1 = (minZ - oz) * inverse;
            t2 = (maxZ - oz) * inverse;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));

            if (tMax < tMin || tMax < 0f) {
                return false;
            }
            distance = Math.max(tMin, 0f);
            float hitY = oy + dy * distance;
            float headStart = maxY - Combatant.HEAD_RADIUS * 1.35f;
            if (hitY >= headStart) {
                region = REGION_HEAD;
            } else if (hitY <= minY + 0.62f) {
                region = REGION_LEGS;
            } else {
                region = REGION_BODY;
            }
            return true;
        }
    }

    private static final MatchListener NULL_LISTENER = new MatchListener() {
        @Override
        public void onSound(int kind, float x, float y, float z, float volume, float pitch) {}

        @Override
        public void onLocalHit(boolean headshot, boolean lethal) {}

        @Override
        public void onLocalDamage(float amount, float fromYaw) {}

        @Override
        public void onKillFeed(KillFeedEntry entry) {}

        @Override
        public void onMatchEnd(MatchResult result) {}
    };
}
