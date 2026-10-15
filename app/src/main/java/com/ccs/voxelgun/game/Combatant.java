package com.ccs.voxelgun.game;

import com.ccs.voxelgun.math.MathUtil;
import com.ccs.voxelgun.math.Vec3;
import com.ccs.voxelgun.weapon.Weapon;
import com.ccs.voxelgun.weapon.WeaponSlot;
import com.ccs.voxelgun.weapon.WeaponType;

/** Everything a fighter is, whether it is driven by touch input or by the bot brain. */
public class Combatant {
    public static final float RADIUS = 0.34f;
    public static final float HEIGHT = 1.80f;
    public static final float CROUCH_HEIGHT = 1.25f;
    public static final float EYE_HEIGHT = 1.62f;
    public static final float CROUCH_EYE_HEIGHT = 1.08f;
    public static final float HEAD_RADIUS = 0.24f;
    public static final float MAX_HEALTH = 100f;
    public static final float MAX_ARMOR = 100f;

    public final int id;
    public String name;
    public Team team = Team.NONE;
    public boolean bot;
    /** Zombies use melee only and take a different damage profile. */
    public boolean zombie;
    /** Battle Field weapon class; ignored by the arena modes. */
    public BattlefieldClass battlefieldClass = BattlefieldClass.RIFLEMAN;

    public final Vec3 position = new Vec3();
    public final Vec3 velocity = new Vec3();
    public float yaw;
    public float pitch;

    public float health = MAX_HEALTH;
    public float armor;
    public boolean alive = true;
    public float respawnTimer;

    public boolean crouching;
    /** 0 standing, 1 fully crouched; drives the camera dip, the hitbox and the rig pose. */
    public float crouchBlend;
    /**
     * How settled the stance is, 0..1. It climbs while the player holds still and collapses the
     * moment they move, and it is what tightens the cone now that only snipers have optics.
     */
    public float steady;
    public boolean sprinting;
    public boolean grounded;
    /** One additional airborne jump; restored only on landing/respawn. */
    public boolean airJumpUsed;
    public boolean aiming;
    public boolean firing;
    public boolean wantsJump;

    public final Weapon[] weapons = new Weapon[WeaponSlot.COUNT];
    /**
     * The kit this combatant respawns with. Buying writes here as well as into {@link #weapons},
     * so a rifle bought once is still in hand after the next death — losing it would mean buying
     * the same gun every twenty seconds.
     */
    public final WeaponType[] kit = new WeaponType[WeaponSlot.COUNT];
    /** Round money. Spent at the buy menu, never persisted. */
    public int money = Economy.STARTING_MONEY;
    /** Seconds of buy window left after the last spawn; buying is always open while dead. */
    public float buyTimer;
    public int slot;
    public float switchTimer;

    public int kills;
    public int deaths;
    public int assists;
    public int score;
    public int streak;
    /** Gun Game ladder position. */
    public int ladderStep;

    public float recoilPitch;
    public float recoilYaw;
    public float recoilVelocityPitch;
    public float recoilVelocityYaw;

    /** Transform this body was drawn at last frame; the renderer uses it for motion vectors. */
    public float renderPreviousX;
    public float renderPreviousY;
    public float renderPreviousZ;
    public float renderPreviousYaw;
    public boolean renderPreviousValid;

    public float walkCycle;
    public float viewBob;
    public float stepAccumulator;
    public float flash;
    public float damageFlash;
    public float lastDamageYaw;
    public float lastDamageTime;
    public int lastAttackerId = -1;
    public float meleeSwing;
    public float muzzleFlash;
    public float landImpact;
    public int gloveColor, charm;
    /**
     * Paint per weapon, indexed by {@code WeaponType.ordinal()}. Null for bots, who carry stock
     * guns, so the renderer falls back to the unpainted mesh without allocating an array each.
     */
    public int[] weaponSkins;
    public int reloadSoundStage;
    public float screenKick;

    public Combatant(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public Weapon weapon() {
        return weapons[slot];
    }

    /** The paint applied to whatever is currently in the hands. */
    public int weaponSkin() {
        return weaponSkin(weaponType());
    }

    /** The configured paint for a carried weapon, also used by the HUD slot previews. */
    public int weaponSkin(WeaponType type) {
        if (weaponSkins == null) {
            return 0;
        }
        int index = type.ordinal();
        return index < weaponSkins.length ? weaponSkins[index] : 0;
    }

    public WeaponType weaponType() {
        Weapon weapon = weapons[slot];
        return weapon == null ? WeaponType.KNIFE : weapon.type();
    }

    public float eyeHeight() {
        return MathUtil.lerp(EYE_HEIGHT, CROUCH_EYE_HEIGHT, crouchBlend);
    }

    public float height() {
        return MathUtil.lerp(HEIGHT, CROUCH_HEIGHT, crouchBlend);
    }

    public float eyeX() {
        return position.x;
    }

    public float eyeY() {
        return position.y + eyeHeight();
    }

    public float eyeZ() {
        return position.z;
    }

    /** Aim direction including recoil kick, written into {@code out}. */
    public void aimDirection(Vec3 out) {
        float p = MathUtil.clamp(pitch + recoilPitch, -1.55f, 1.55f);
        float y = yaw + recoilYaw;
        float cp = (float) Math.cos(p);
        out.set((float) Math.sin(y) * cp, (float) Math.sin(p), -(float) Math.cos(y) * cp);
    }

    public void setLoadout(WeaponType primary, WeaponType secondary, WeaponType melee,
                           WeaponType grenade) {
        setKit(primary, secondary, melee, grenade);
        applyKit();
    }

    /** One class-specific long gun plus the pistol and knife shared by every Battle Field class. */
    public void setBattlefieldLoadout(WeaponType longGun) {
        WeaponType safeLongGun = BattlefieldRules.weaponForClass(battlefieldClass,
                longGun, longGun, longGun);
        setLoadout(safeLongGun, WeaponType.P9, WeaponType.KNIFE, null);
    }

    /** Records what to respawn with, without touching what is currently in hand. */
    public void setKit(WeaponType primary, WeaponType secondary, WeaponType melee,
                       WeaponType grenade) {
        kit[WeaponSlot.PRIMARY.ordinal()] = primary;
        kit[WeaponSlot.SECONDARY.ordinal()] = secondary;
        kit[WeaponSlot.MELEE.ordinal()] = melee;
        kit[WeaponSlot.GRENADE.ordinal()] = grenade;
    }

    /** Hands over a fresh copy of the kit; a null entry leaves that slot empty. */
    public void applyKit() {
        int first = -1;
        for (int i = 0; i < weapons.length; i++) {
            weapons[i] = kit[i] == null ? null : new Weapon(kit[i]);
            if (weapons[i] != null && first < 0 && i != WeaponSlot.GRENADE.ordinal()) {
                first = i;
            }
        }
        slot = first < 0 ? WeaponSlot.MELEE.ordinal() : first;
    }

    public void setSingleWeapon(WeaponType type) {
        for (int i = 0; i < weapons.length; i++) {
            weapons[i] = null;
            kit[i] = null;
        }
        kit[type.slot().ordinal()] = type;
        int index = type.slot().ordinal();
        weapons[index] = new Weapon(type);
        slot = index;
    }

    public boolean selectSlot(int index) {
        if (index < 0 || index >= weapons.length || weapons[index] == null || index == slot) {
            return false;
        }
        Weapon current = weapons[slot];
        if (current != null) {
            current.cancelReload();
        }
        slot = index;
        switchTimer = 0.32f;
        return true;
    }

    /** Cycles to the next non-empty slot; used by bots and by the swap button. */
    public int nextSlot() {
        for (int step = 1; step <= weapons.length; step++) {
            int candidate = (slot + step) % weapons.length;
            if (weapons[candidate] != null) {
                return candidate;
            }
        }
        return slot;
    }

    public void resetForSpawn(float x, float y, float z, float spawnYaw) {
        position.set(x, y, z);
        velocity.set(0f, 0f, 0f);
        yaw = spawnYaw;
        pitch = 0f;
        health = MAX_HEALTH;
        armor = zombie ? 0f : battlefieldClass.spawnArmor();
        alive = true;
        airJumpUsed = false;
        crouching = false;
        crouchBlend = 0f;
        aiming = false;
        firing = false;
        recoilPitch = 0f;
        recoilYaw = 0f;
        recoilVelocityPitch = 0f;
        recoilVelocityYaw = 0f;
        flash = 0f;
        damageFlash = 0f;
        meleeSwing = 0f;
        muzzleFlash = 0f;
        respawnTimer = 0f;
        renderPreviousValid = false;
        lastAttackerId = -1;
        lastDamageTime = 100f;
        screenKick = 0f;
        reloadSoundStage = 0;
        switchTimer = 0.2f;
        for (Weapon weapon : weapons) {
            if (weapon != null) {
                weapon.refill();
            }
        }
        for (int i = 0; i < weapons.length; i++) {
            if (weapons[i] != null) {
                slot = i;
                break;
            }
        }
    }

    /** Applies damage through armour and reports whether it was lethal. */
    public boolean applyDamage(float amount, int attackerId, float fromYaw) {
        if (!alive) {
            return false;
        }
        float remaining = amount;
        if (armor > 0f) {
            float absorbed = Math.min(armor, remaining * 0.55f);
            armor -= absorbed;
            remaining -= absorbed;
        }
        health -= remaining;
        lastAttackerId = attackerId;
        lastDamageTime = 0f;
        lastDamageYaw = fromYaw;
        damageFlash = 1f;
        if (health <= 0f) {
            health = 0f;
            alive = false;
            deaths++;
            streak = 0;
            return true;
        }
        return false;
    }

    public void updateTimers(float dt) {
        switchTimer = Math.max(0f, switchTimer - dt);
        screenKick = Math.max(0f, screenKick - dt * 7f);
        damageFlash = Math.max(0f, damageFlash - dt * 2.2f);
        muzzleFlash = Math.max(0f, muzzleFlash - dt * 14f);
        meleeSwing = Math.max(0f, meleeSwing - dt * 4.5f);
        landImpact = Math.max(0f, landImpact - dt * 3.5f);
        flash = Math.max(0f, flash - dt * 0.42f);
        lastDamageTime += dt;

        // Recoil is a spring: kick up fast, settle back toward the aim point.
        recoilVelocityPitch -= recoilPitch * 42f * dt;
        recoilVelocityYaw -= recoilYaw * 42f * dt;
        recoilVelocityPitch *= Math.max(0f, 1f - 9f * dt);
        recoilVelocityYaw *= Math.max(0f, 1f - 9f * dt);
        recoilPitch += recoilVelocityPitch * dt;
        recoilYaw += recoilVelocityYaw * dt;

        for (Weapon weapon : weapons) {
            if (weapon != null) {
                weapon.update(dt);
            }
        }
    }

    public void addRecoil(float vertical, float horizontal) {
        recoilVelocityPitch += (float) Math.toRadians(vertical) * 12f;
        recoilVelocityYaw += (float) Math.toRadians(horizontal) * 12f;
    }
}
