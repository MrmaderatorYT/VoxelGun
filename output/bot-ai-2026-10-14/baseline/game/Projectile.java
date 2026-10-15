package com.ccs.voxelgun.game;

import com.ccs.voxelgun.weapon.WeaponType;

/** A thrown grenade or a fired rocket. Pooled by {@link Match}. */
public final class Projectile {
    public boolean active;
    public WeaponType type;
    public int ownerId;
    public Team team;
    public float x;
    public float y;
    public float z;
    public float vx;
    public float vy;
    public float vz;
    public float fuse;
    public float age;
    public int bounces;
    public boolean rocket;
    /** Spin used purely for rendering the tumbling model. */
    public float spin;
    /** Captured once at launch; the owner may land, switch weapons or recover before impact. */
    public int shotFlags;

    public void reset() {
        active = false;
        type = null;
        ownerId = -1;
        team = Team.NONE;
        bounces = 0;
        age = 0f;
        spin = 0f;
        shotFlags = 0;
    }
}
