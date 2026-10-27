package com.ccs.voxelstrike.map.scenery;

/** Authored callout zone, not a nearest-object label that jumps across the whole map. */
public final class Landmark {
    public final String name;
    public final float x, z, radius;
    public Landmark(String name, float x, float z, float radius) {
        this.name = name; this.x = x; this.z = z; this.radius = radius;
    }
}
