package com.ccs.voxelstrike.weapon;

import com.ccs.voxelstrike.math.MathUtil;

/** Runtime state of one equipped weapon: ammo, timers, burst counter and accuracy bloom. */
public final class Weapon {
    private final WeaponType type;
    private int ammo;
    private int reserve;
    private float cooldown;
    private float reloadTimer;
    private int burstRemaining;
    private float burstDelay;
    /** Grows while firing and decays while idle, so held triggers get less accurate. */
    private float bloom;
    /** How much of the optics-grade cone a perfectly settled stance is allowed to reach. */
    private static final float STEADY_AUTHORITY = 0.85f;
    private boolean triggerReleased = true;
    private boolean infiniteReserve, noReload;

    /** Match policy, applied to all equipped/purchased weapons without changing weapon stats. */
    public void setAmmoRules(boolean infiniteReserve, boolean noReload) {
        this.infiniteReserve = infiniteReserve && !type.melee();
        this.noReload = noReload && !type.melee();
        if (this.noReload) { reloadTimer = 0f; ammo = type.magazine(); }
    }

    public boolean infiniteReserve() { return infiniteReserve; }
    public boolean noReload() { return noReload; }

    public Weapon(WeaponType type) {
        this.type = type;
        this.ammo = type.magazine();
        this.reserve = type.reserve();
    }

    public WeaponType type() {
        return type;
    }

    public int ammo() {
        return ammo;
    }

    public int reserve() {
        return reserve;
    }

    public boolean reloading() {
        return reloadTimer > 0f;
    }

    public float reloadProgress() {
        if (type.reloadSeconds() <= 0f) {
            return 1f;
        }
        return MathUtil.clamp(1f - reloadTimer / type.reloadSeconds(), 0f, 1f);
    }

    public float bloom() {
        return bloom;
    }

    public boolean empty() {
        return ammo <= 0 && !type.melee();
    }

    public boolean depleted() {
        return ammo <= 0 && reserve <= 0 && !type.melee() && !infiniteReserve && !noReload;
    }

    public void update(float dt) {
        cooldown = Math.max(0f, cooldown - dt);
        burstDelay = Math.max(0f, burstDelay - dt);
        bloom = Math.max(0f, bloom - dt * type.bloomRecovery());
        if (reloadTimer > 0f) {
            reloadTimer -= dt;
            if (reloadTimer <= 0f) {
                reloadTimer = 0f;
                finishReload();
            }
        }
        if (burstRemaining > 0 && burstDelay <= 0f && cooldown <= 0f) {
            // The burst keeps cycling on its own once the first round leaves the barrel.
            burstDelay = type.shotInterval();
        }
    }

    public void releaseTrigger() {
        triggerReleased = true;
    }

    /** True when a shot may leave the barrel this frame. */
    public boolean canFire(boolean triggerHeld) {
        if (reloading() || cooldown > 0f) {
            return false;
        }
        if (!type.melee() && ammo <= 0) {
            return false;
        }
        if (type.burst() > 0) {
            return burstRemaining > 0 || triggerReleased;
        }
        return type.melee() || type.automatic() || triggerReleased;
    }

    /** Consumes one round and starts the cycle timers. */
    public void consume() {
        if (!type.melee() && !noReload) {
            ammo = Math.max(0, ammo - 1);
        }
        cooldown = type.shotInterval();
        triggerReleased = false;
        bloom = Math.min(1f, bloom + (type.melee() ? 0f : type.bloomPerShot()));
        if (type.burst() > 0) {
            if (burstRemaining <= 0) {
                burstRemaining = type.burst();
            }
            burstRemaining--;
            if (burstRemaining <= 0) {
                cooldown = Math.max(cooldown, type.shotInterval() * 3f);
            }
        }
    }

    /** True while a burst is mid-cycle and should keep firing without trigger input. */
    public boolean burstPending() {
        return burstRemaining > 0;
    }

    public boolean startReload() {
        if (noReload || reloading() || type.melee() || ammo >= type.magazine()
                || (reserve <= 0 && !infiniteReserve)) {
            return false;
        }
        reloadTimer = type.reloadSeconds();
        burstRemaining = 0;
        return true;
    }

    public void cancelReload() {
        reloadTimer = 0f;
    }

    private void finishReload() {
        int needed = type.magazine() - ammo;
        int moved = infiniteReserve ? needed : Math.min(needed, reserve);
        ammo += moved;
        if (!infiniteReserve) reserve -= moved;
    }

    /** Cone half-angle in degrees for the current stance. */
    /**
     * The cone half-angle for the next shot.
     *
     * <p>Only snipers can raise optics, so for everything else the tuned {@code adsSpread} is
     * reached by standing still instead: {@code steady} walks the cone from the hip value toward
     * it, stopping just short so a settled stance is worth a lot but never quite free.
     */
    public float spread(boolean aiming, float movementFactor, boolean crouching, boolean airborne,
                        float steady) {
        float settled = Math.max(0f, Math.min(1f, steady)) * STEADY_AUTHORITY;
        float base = aiming ? type.adsSpread()
                : type.hipSpread() + (type.adsSpread() - type.hipSpread()) * settled;
        float value = base * (1f + bloom * 1.1f);
        value *= 1f + movementFactor * type.movementSpreadPenalty()
                * (aiming ? 0.58f : 1f);
        if (crouching) {
            value *= 0.58f;
        }
        if (airborne) {
            value *= 2.4f;
        }
        return value;
    }

    /** Restores the weapon to a fresh state on respawn. */
    public void refill() {
        ammo = type.magazine();
        reserve = type.reserve();
        cooldown = 0f;
        reloadTimer = 0f;
        burstRemaining = 0;
        bloom = 0f;
        triggerReleased = true;
    }

    /** Moves a single round from the reserve into the chamber; used for thrown equipment. */
    public boolean loadOne() {
        if ((!infiniteReserve && reserve <= 0) || ammo >= type.magazine()) {
            return false;
        }
        if (!infiniteReserve) reserve--;
        ammo++;
        return true;
    }

    public void addReserve(int rounds) {
        reserve = Math.min(type.reserve() * 2, reserve + rounds);
    }
}
