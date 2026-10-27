package com.ccs.voxelstrike.weapon;

import com.ccs.voxelstrike.audio.Sfx;

/**
 * The arsenal. Every number that defines how a gun feels lives here, so balancing never means
 * touching gameplay code.
 *
 * <p>Damage is per bullet against 100 HP. Spread is the cone half-angle in degrees. Recoil is the
 * kick applied per shot in degrees, decayed back toward the aim point between shots.
 */
public enum WeaponType {
    // ---------------------------------------------------------------- melee

    /** The blade every loadout starts with: one connected swing kills a target at full health. */
    KNIFE(new Builder("Combat Knife", WeaponClass.MELEE)
            .damage(150).headshot(1.0f).rpm(170).range(2.7f)
            .automatic(false).moveSpeed(1.22f).sound(Sfx.SWING).impactSound(Sfx.STAB)
            .cost(0).model("knife")),

    /**
     * What the horde swings in zombie survival. It keeps the old two-hit numbers so a wave stays
     * survivable, and is mode-only, so it never reaches a buy menu.
     */
    SCRAP_KNIFE(new Builder("Scrap Blade", WeaponClass.MELEE)
            .damage(55).headshot(2.0f).rpm(150).range(2.4f)
            .automatic(false).moveSpeed(1.15f).sound(Sfx.SWING).impactSound(Sfx.STAB)
            .cost(0).modeOnly().model("scrap_knife")),

    // ---------------------------------------------------------------- pistols

    P9(new Builder("P9", WeaponClass.PISTOL)
            .damage(24).headshot(3.4f).rpm(560).magazine(17).reserve(68).reload(1.5f)
            .spread(1.6f, 0.55f).recoil(0.85f, 0.35f).range(48f)
            .handling(0.95f, 0.12f, 2.60f)
            .automatic(false).moveSpeed(1.06f).sound(Sfx.SHOT_P9)
            .cost(0).model("p9")),

    TALON(new Builder("Talon-S", WeaponClass.PISTOL)
            .damage(27).headshot(3.2f).rpm(420).magazine(15).reserve(60).reload(1.6f)
            .spread(1.3f, 0.40f).recoil(0.90f, 0.30f).range(54f)
            .handling(0.90f, 0.10f, 2.80f)
            .automatic(false).moveSpeed(1.07f).sound(Sfx.SHOT_TALON)
            .cost(350).model("talon_s")),

    AUTO9(new Builder("Auto-9", WeaponClass.PISTOL)
            .damage(20).headshot(2.8f).rpm(1050).magazine(24).reserve(96).reload(1.7f)
            .spread(2.6f, 1.1f).recoil(0.75f, 0.55f).range(38f)
            .handling(1.15f, 0.08f, 1.65f)
            .automatic(true).moveSpeed(1.05f).sound(Sfx.SHOT_AUTO9)
            .cost(500).model("auto_9")),

    MAG5(new Builder("Mag-5", WeaponClass.PISTOL)
            .damage(33).headshot(3.0f).rpm(380).magazine(12).reserve(48).reload(1.9f)
            .spread(1.9f, 0.50f).recoil(1.6f, 0.60f).range(52f).penetration(0.25f)
            .handling(1.05f, 0.16f, 2.20f)
            .automatic(false).moveSpeed(1.04f).sound(Sfx.SHOT_MAG5)
            .cost(650).model("mag_5")),

    RANGER(new Builder("Ranger .50", WeaponClass.PISTOL)
            .damage(58).headshot(2.6f).rpm(240).magazine(7).reserve(35).reload(2.1f)
            .spread(2.2f, 0.6f).recoil(3.4f, 1.1f).range(60f).penetration(0.35f)
            .handling(1.20f, 0.22f, 1.90f)
            .automatic(false).moveSpeed(1.02f).sound(Sfx.SHOT_RANGER)
            .cost(800).model("ranger_50")),

    SIDEWINDER(new Builder("Sidewinder", WeaponClass.PISTOL)
            .damage(72).headshot(2.4f).rpm(170).magazine(6).reserve(30).reload(2.6f)
            .spread(1.9f, 0.5f).recoil(4.0f, 1.4f).range(66f).penetration(0.4f)
            .handling(1.25f, 0.28f, 1.60f)
            .automatic(false).moveSpeed(1.00f).sound(Sfx.SHOT_SIDEWINDER)
            .cost(950).model("sidewinder")),

    // ---------------------------------------------------------------- submachine guns

    SPECTRE(new Builder("Spectre", WeaponClass.SMG)
            .damage(23).headshot(2.7f).rpm(750).magazine(30).reserve(120).reload(2.1f)
            .spread(2.2f, 0.8f).recoil(0.85f, 0.5f).range(40f)
            .handling(0.90f, 0.09f, 2.05f)
            .automatic(true).moveSpeed(1.06f).sound(Sfx.SHOT_SPECTRE)
            .cost(1050).model("spectre")),

    HORNET(new Builder("Hornet", WeaponClass.SMG)
            .damage(26).headshot(2.6f).rpm(800).magazine(30).reserve(120).reload(2.2f)
            .spread(2.4f, 0.9f).recoil(1.0f, 0.6f).range(44f)
            .handling(1.00f, 0.10f, 1.85f)
            .automatic(true).moveSpeed(1.03f).sound(Sfx.SHOT_HORNET)
            .cost(1250).model("hornet")),

    UZIK(new Builder("Uzi-K", WeaponClass.SMG)
            .damage(25).headshot(2.4f).rpm(950).magazine(32).reserve(128).reload(2.4f)
            .spread(2.8f, 1.0f).recoil(1.1f, 0.7f).range(42f)
            .handling(1.15f, 0.12f, 1.55f)
            .automatic(true).moveSpeed(1.04f).sound(Sfx.SHOT_UZIK)
            .cost(1400).model("uzi_k")),

    MP7K(new Builder("MP7-K", WeaponClass.SMG)
            .damage(24).headshot(2.9f).rpm(900).magazine(35).reserve(140).reload(2.0f)
            .spread(1.9f, 0.6f).recoil(0.9f, 0.45f).range(48f).penetration(0.3f)
            .handling(0.85f, 0.07f, 2.20f)
            .automatic(true).moveSpeed(1.05f).sound(Sfx.SHOT_MP7K)
            .cost(1550).model("mp7_k")),

    VECTOR9(new Builder("Vector-9", WeaponClass.SMG)
            .damage(22).headshot(2.5f).rpm(1150).magazine(25).reserve(125).reload(2.0f)
            .spread(2.2f, 0.8f).recoil(0.8f, 0.5f).range(40f)
            .handling(1.10f, 0.11f, 1.45f)
            .automatic(true).moveSpeed(1.05f).sound(Sfx.SHOT_VECTOR9)
            .cost(1700).model("vector_9")),

    // ---------------------------------------------------------------- shotguns

    PUMPER(new Builder("Pumper-8", WeaponClass.SHOTGUN)
            .damage(16).headshot(1.8f).rpm(58).magazine(8).reserve(40).reload(3.4f)
            .spread(7.5f, 5.0f).recoil(5.4f, 1.8f).range(19f).pellets(8)
            .handling(0.75f, 0.20f, 1.35f)
            .automatic(false).moveSpeed(0.96f).sound(Sfx.SHOT_PUMPER)
            .cost(1500).model("pumper_8")),

    BREAKER12(new Builder("Breaker-12", WeaponClass.SHOTGUN)
            .damage(17).headshot(1.8f).rpm(72).magazine(7).reserve(35).reload(3.2f)
            .spread(6.5f, 4.5f).recoil(5.0f, 1.6f).range(22f).pellets(8)
            .handling(0.80f, 0.18f, 1.45f)
            .automatic(false).moveSpeed(0.95f).sound(Sfx.SHOT_BREAKER12)
            .cost(1900).model("breaker_12")),

    AUTOSLUG(new Builder("Auto-Slug", WeaponClass.SHOTGUN)
            .damage(14).headshot(1.7f).rpm(200).magazine(8).reserve(40).reload(3.0f)
            .spread(7.0f, 5.0f).recoil(3.6f, 1.4f).range(20f).pellets(6)
            .handling(0.95f, 0.11f, 0.95f)
            .automatic(true).moveSpeed(0.93f).sound(Sfx.SHOT_AUTOSLUG)
            .cost(2500).model("auto_slug")),

    DRUM12(new Builder("Drum-12", WeaponClass.SHOTGUN)
            .damage(15).headshot(1.7f).rpm(260).magazine(12).reserve(48).reload(4.2f)
            .spread(7.4f, 5.4f).recoil(3.2f, 1.5f).range(21f).pellets(7)
            .handling(1.05f, 0.13f, 0.75f)
            .automatic(true).moveSpeed(0.90f).sound(Sfx.SHOT_DRUM12)
            .cost(3000).model("drum_12")),

    // ---------------------------------------------------------------- rifles

    FAMAS_B(new Builder("Famas-B", WeaponClass.RIFLE)
            .damage(30).headshot(3.1f).rpm(900).magazine(25).reserve(100).reload(2.4f)
            .spread(1.5f, 0.25f).recoil(1.3f, 0.5f).range(70f).burst(3)
            .handling(1.15f, 0.12f, 1.85f)
            .automatic(false).moveSpeed(1.00f).sound(Sfx.SHOT_FAMAS_B)
            .cost(2250).model("famas_b")),

    BULLPUP(new Builder("Bullpup-A", WeaponClass.RIFLE)
            .damage(33).headshot(3.0f).rpm(660).magazine(30).reserve(120).reload(2.3f)
            .spread(1.8f, 0.30f).recoil(1.7f, 0.7f).range(76f).penetration(0.40f)
            .handling(1.20f, 0.14f, 1.55f)
            .automatic(true).moveSpeed(0.99f).sound(Sfx.SHOT_BULLPUP)
            .cost(2500).model("bullpup_a")),

    AK15(new Builder("AK-15", WeaponClass.RIFLE)
            .damage(36).headshot(3.0f).rpm(600).magazine(30).reserve(120).reload(2.5f)
            .spread(2.0f, 0.35f).recoil(2.1f, 0.9f).range(80f).penetration(0.45f)
            .handling(1.35f, 0.18f, 1.35f)
            .automatic(true).moveSpeed(0.97f).sound(Sfx.SHOT_AK15)
            .cost(2700).model("ak_15")),

    VEKTOR_AR(new Builder("Vektor-AR", WeaponClass.RIFLE)
            .damage(34).headshot(3.2f).rpm(780).magazine(28).reserve(112).reload(2.2f)
            .spread(1.6f, 0.26f).recoil(1.4f, 0.55f).range(82f).penetration(0.42f)
            .handling(1.05f, 0.11f, 1.95f)
            .automatic(true).moveSpeed(0.98f).sound(Sfx.SHOT_VEKTOR_AR)
            .cost(2900).model("vektor_ar")),

    FALCON(new Builder("M4 Falcon", WeaponClass.RIFLE)
            .damage(31).headshot(3.0f).rpm(720).magazine(30).reserve(120).reload(2.3f)
            .spread(1.7f, 0.28f).recoil(1.5f, 0.6f).range(78f).penetration(0.4f)
            .handling(1.10f, 0.13f, 1.70f)
            .automatic(true).moveSpeed(0.99f).sound(Sfx.SHOT_FALCON)
            .cost(3100).model("m4_falcon")),

    SCAR_H(new Builder("Scar-H", WeaponClass.RIFLE)
            .damage(42).headshot(2.9f).rpm(540).magazine(20).reserve(100).reload(2.7f)
            .spread(2.2f, 0.30f).recoil(2.6f, 1.0f).range(90f).penetration(0.55f)
            .handling(1.45f, 0.22f, 1.25f)
            .automatic(true).moveSpeed(0.94f).sound(Sfx.SHOT_SCAR_H)
            .cost(3400).model("scar_h")),

    // ---------------------------------------------------------------- snipers

    SCOUT_V(new Builder("Scout-V", WeaponClass.SNIPER)
            .damage(70).headshot(2.2f).rpm(85).magazine(10).reserve(40).reload(2.6f)
            .spread(2.6f, 0.05f).recoil(3.0f, 0.6f).range(130f).penetration(0.55f)
            .handling(2.00f, 0.20f, 1.20f)
            .automatic(false).moveSpeed(1.00f).zoom(2.6f).scope(true).sound(Sfx.SHOT_SCOUT_V)
            .cost(1950).model("scout_v")),

    DRAGUNOV_S(new Builder("Dragunov-S", WeaponClass.SNIPER)
            .damage(78).headshot(1.9f).rpm(150).magazine(10).reserve(50).reload(3.0f)
            .spread(3.0f, 0.06f).recoil(4.2f, 0.8f).range(140f).penetration(0.65f)
            .handling(2.20f, 0.16f, 1.35f)
            .automatic(false).moveSpeed(0.90f).zoom(3.0f).scope(true).sound(Sfx.SHOT_DRAGUNOV_S)
            .cost(3800).model("dragunov_s")),

    AWM_X(new Builder("AWM-X", WeaponClass.SNIPER)
            .damage(118).headshot(1.6f).rpm(41).magazine(5).reserve(25).reload(3.4f)
            .spread(4.0f, 0.02f).recoil(7.5f, 1.0f).range(160f).penetration(0.8f)
            .handling(2.80f, 0.28f, 0.90f)
            .automatic(false).moveSpeed(0.86f).zoom(4.2f).scope(true).sound(Sfx.SHOT_AWM_X)
            .cost(4750).model("awm_x")),

    BARRETT_M(new Builder("Barrett-M", WeaponClass.SNIPER)
            .damage(135).headshot(1.4f).rpm(48).magazine(7).reserve(28).reload(4.0f)
            .spread(4.6f, 0.03f).recoil(8.5f, 1.2f).range(170f).penetration(0.9f)
            .handling(3.00f, 0.32f, 0.75f)
            .automatic(false).moveSpeed(0.80f).zoom(4.6f).scope(true).sound(Sfx.SHOT_BARRETT_M)
            .cost(5400).model("barrett_m")),

    // ---------------------------------------------------------------- machine guns

    BULLDOG_LMG(new Builder("Bulldog", WeaponClass.LMG)
            .damage(30).headshot(2.5f).rpm(700).magazine(75).reserve(150).reload(4.6f)
            .spread(3.0f, 0.7f).recoil(1.5f, 0.9f).range(78f).penetration(0.45f)
            .handling(1.55f, 0.075f, 0.62f)
            .automatic(true).moveSpeed(0.88f).sound(Sfx.SHOT_BULLDOG_LMG)
            .cost(4300).model("bulldog_lmg")),

    NEGEV_M(new Builder("Negev-M", WeaponClass.LMG)
            .damage(33).headshot(2.6f).rpm(880).magazine(100).reserve(200).reload(5.2f)
            .spread(3.2f, 0.8f).recoil(1.8f, 1.1f).range(84f).penetration(0.5f)
            .handling(1.70f, 0.070f, 0.48f)
            .automatic(true).moveSpeed(0.84f).sound(Sfx.SHOT_NEGEV_M)
            .cost(5200).model("negev_m")),

    // ---------------------------------------------------------------- launcher

    RPG_V(new Builder("RPG-V", WeaponClass.LAUNCHER)
            .damage(120).headshot(1.0f).rpm(30).magazine(1).reserve(4).reload(4.0f)
            .spread(0.8f, 0.3f).recoil(6.0f, 1.0f).range(120f)
            .handling(1.80f, 0.35f, 0.60f)
            .automatic(false).moveSpeed(0.82f).projectile(true).splash(6.5f, 95f)
            .sound(Sfx.SHOT_RPG_V)
            .cost(6200).model("rpg_v")),

    // ---------------------------------------------------------------- grenades

    FRAG(new Builder("Frag Grenade", WeaponClass.GRENADE)
            .damage(0).rpm(60).magazine(1).reserve(2).reload(1.0f)
            .range(30f).automatic(false).moveSpeed(1.10f)
            .projectile(true).splash(6.0f, 115f).fuse(2.6f).sound(Sfx.PIN)
            .cost(0).model("frag_grenade")),

    FLASH(new Builder("Flash Grenade", WeaponClass.GRENADE)
            .damage(0).rpm(60).magazine(1).reserve(2).reload(1.0f)
            .range(30f).automatic(false).moveSpeed(1.10f)
            .projectile(true).splash(9.0f, 0f).fuse(1.9f).flash(true).sound(Sfx.PIN)
            .cost(250).model("flash_grenade"));

    private final String displayName;
    private final WeaponClass weaponClass;
    private final float damage;
    private final float headshotMultiplier;
    private final float limbMultiplier;
    private final float roundsPerMinute;
    private final int magazine;
    private final int reserve;
    private final float reloadSeconds;
    private final float hipSpread;
    private final float adsSpread;
    /** How strongly horizontal movement widens this weapon's cone. */
    private final float movementSpreadPenalty;
    /** Accuracy bloom added by one shot and recovered per second. */
    private final float bloomPerShot;
    private final float bloomRecovery;
    private final float recoilVertical;
    private final float recoilHorizontal;
    private final float range;
    private final int pellets;
    private final int burst;
    private final boolean automatic;
    private final float moveSpeedMultiplier;
    private final float zoom;
    private final boolean scoped;
    private final float penetration;
    private final boolean projectile;
    private final float splashRadius;
    private final float splashDamage;
    private final float fuseSeconds;
    private final boolean flashbang;
    private final int soundKind;
    private final int impactSoundKind;
    private final int cost;
    private final boolean modeOnly;
    private final String modelKey;

    WeaponType(Builder builder) {
        this.displayName = builder.displayName;
        this.weaponClass = builder.weaponClass;
        this.damage = builder.damage;
        this.headshotMultiplier = builder.headshot;
        this.limbMultiplier = builder.limb;
        this.roundsPerMinute = builder.rpm;
        this.magazine = builder.magazine;
        this.reserve = builder.reserve;
        this.reloadSeconds = builder.reload;
        this.hipSpread = builder.hipSpread;
        this.adsSpread = builder.adsSpread;
        this.movementSpreadPenalty = builder.movementSpreadPenalty;
        this.bloomPerShot = builder.bloomPerShot;
        this.bloomRecovery = builder.bloomRecovery;
        this.recoilVertical = builder.recoilVertical;
        this.recoilHorizontal = builder.recoilHorizontal;
        this.range = builder.range;
        this.pellets = builder.pellets;
        this.burst = builder.burst;
        this.automatic = builder.automatic;
        this.moveSpeedMultiplier = builder.moveSpeed;
        this.zoom = builder.zoom;
        this.scoped = builder.scope;
        this.penetration = builder.penetration;
        this.projectile = builder.projectile;
        this.splashRadius = builder.splashRadius;
        this.splashDamage = builder.splashDamage;
        this.fuseSeconds = builder.fuse;
        this.flashbang = builder.flash;
        this.soundKind = builder.sound;
        this.impactSoundKind = builder.impactSound;
        this.cost = builder.cost;
        this.modeOnly = builder.modeOnly;
        this.modelKey = builder.model;
    }

    public String displayName() {
        return displayName;
    }

    public WeaponClass weaponClass() {
        return weaponClass;
    }

    public WeaponSlot slot() {
        return weaponClass.slot();
    }

    public float damage() {
        return damage;
    }

    public float headshotMultiplier() {
        return headshotMultiplier;
    }

    public float limbMultiplier() {
        return limbMultiplier;
    }

    public float roundsPerMinute() {
        return roundsPerMinute;
    }

    public float shotInterval() {
        return 60f / Math.max(1f, roundsPerMinute);
    }

    public int magazine() {
        return magazine;
    }

    public int reserve() {
        return reserve;
    }

    public float reloadSeconds() {
        return reloadSeconds;
    }

    public float hipSpread() {
        return hipSpread;
    }

    public float adsSpread() {
        return adsSpread;
    }

    public float movementSpreadPenalty() {
        return movementSpreadPenalty;
    }

    public float bloomPerShot() {
        return bloomPerShot;
    }

    public float bloomRecovery() {
        return bloomRecovery;
    }

    public float recoilVertical() {
        return recoilVertical;
    }

    public float recoilHorizontal() {
        return recoilHorizontal;
    }

    public float range() {
        return range;
    }

    public int pellets() {
        return pellets;
    }

    public int burst() {
        return burst;
    }

    public boolean automatic() {
        return automatic;
    }

    public float moveSpeedMultiplier() {
        return moveSpeedMultiplier;
    }

    public float zoom() {
        return zoom;
    }

    public boolean scoped() {
        return scoped;
    }

    /** Fraction of damage that survives shooting through a thin voxel wall. */
    public float penetration() {
        return penetration;
    }

    public boolean projectile() {
        return projectile;
    }

    public float splashRadius() {
        return splashRadius;
    }

    public float splashDamage() {
        return splashDamage;
    }

    public float fuseSeconds() {
        return fuseSeconds;
    }

    public boolean flashbang() {
        return flashbang;
    }

    public int soundKind() {
        return soundKind;
    }

    public int impactSoundKind() {
        return impactSoundKind;
    }

    /** What this weapon costs in round money at the in-match buy menu. */
    public int cost() {
        return cost;
    }

    public String modelKey() {
        return modelKey;
    }

    /** True for weapons a game mode hands out; they never show up in the arsenal or the shop. */
    public boolean modeOnly() {
        return modeOnly;
    }

    public boolean melee() {
        return weaponClass == WeaponClass.MELEE;
    }

    public boolean grenade() {
        return weaponClass == WeaponClass.GRENADE;
    }

    /** Default loadout entries for a fresh profile. */
    public static WeaponType defaultFor(WeaponSlot slot) {
        switch (slot) {
            case SECONDARY:
                return P9;
            case MELEE:
                return KNIFE;
            case GRENADE:
                return FRAG;
            case PRIMARY:
            default:
                return AK15;
        }
    }

    /** Fluent stat sheet; keeps the enum table readable. */
    private static final class Builder {
        private final String displayName;
        private final WeaponClass weaponClass;
        private float damage = 20f;
        private float headshot = 2.5f;
        private float limb = 0.85f;
        private float rpm = 600f;
        private int magazine = 30;
        private int reserve = 90;
        private float reload = 2.2f;
        private float hipSpread = 2f;
        private float adsSpread = 0.5f;
        private float movementSpreadPenalty = 1.4f;
        private float bloomPerShot = 0.22f;
        private float bloomRecovery = 2.4f;
        private float recoilVertical = 1f;
        private float recoilHorizontal = 0.5f;
        private float range = 60f;
        private int pellets = 1;
        private int burst;
        private boolean automatic;
        private float moveSpeed = 1f;
        /**
         * No magnification by default: bringing up the iron sights of a rifle or a pistol tightens
         * the spread, it does not zoom. Only the scoped weapons override this.
         */
        private float zoom = 1f;
        private boolean scope;
        private float penetration;
        private boolean projectile;
        private float splashRadius;
        private float splashDamage;
        private float fuse;
        private boolean flash;
        private int sound;
        private int impactSound = -1;
        private int cost;
        private boolean modeOnly;
        private String model = "rifle";

        Builder(String displayName, WeaponClass weaponClass) {
            this.displayName = displayName;
            this.weaponClass = weaponClass;
        }

        Builder modeOnly() {
            modeOnly = true;
            return this;
        }

        Builder damage(float value) {
            damage = value;
            return this;
        }

        Builder headshot(float value) {
            headshot = value;
            return this;
        }

        Builder rpm(float value) {
            rpm = value;
            return this;
        }

        Builder magazine(int value) {
            magazine = value;
            return this;
        }

        Builder reserve(int value) {
            reserve = value;
            return this;
        }

        Builder reload(float value) {
            reload = value;
            return this;
        }

        Builder spread(float hip, float ads) {
            hipSpread = hip;
            adsSpread = ads;
            return this;
        }

        Builder handling(float movementPenalty, float bloomShot, float bloomRecover) {
            movementSpreadPenalty = movementPenalty;
            bloomPerShot = bloomShot;
            bloomRecovery = bloomRecover;
            return this;
        }

        Builder recoil(float vertical, float horizontal) {
            recoilVertical = vertical;
            recoilHorizontal = horizontal;
            return this;
        }

        Builder range(float value) {
            range = value;
            return this;
        }

        Builder pellets(int value) {
            pellets = value;
            return this;
        }

        Builder burst(int value) {
            burst = value;
            return this;
        }

        Builder automatic(boolean value) {
            automatic = value;
            return this;
        }

        Builder moveSpeed(float value) {
            moveSpeed = value;
            return this;
        }

        Builder zoom(float value) {
            zoom = value;
            return this;
        }

        Builder scope(boolean value) {
            scope = value;
            return this;
        }

        Builder penetration(float value) {
            penetration = value;
            return this;
        }

        Builder projectile(boolean value) {
            projectile = value;
            return this;
        }

        Builder splash(float radius, float damage) {
            splashRadius = radius;
            splashDamage = damage;
            return this;
        }

        Builder fuse(float value) {
            fuse = value;
            return this;
        }

        Builder flash(boolean value) {
            flash = value;
            return this;
        }

        Builder sound(int value) {
            sound = value;
            return this;
        }

        Builder impactSound(int value) {
            impactSound = value;
            return this;
        }

        Builder cost(int value) {
            cost = value;
            return this;
        }

        Builder model(String value) {
            model = value;
            return this;
        }
    }
}
