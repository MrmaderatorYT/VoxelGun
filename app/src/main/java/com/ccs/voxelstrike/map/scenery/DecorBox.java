package com.ccs.voxelstrike.map.scenery;

/** Render-only cuboid in world metres. It is never inserted into the collision/LOS volume. */
public final class DecorBox {
    public final float x, y, z, width, height, depth;
    public final int material;
    public DecorBox(float x, float y, float z, float width, float height, float depth, int material) {
        if (!(width > 0 && height > 0 && depth > 0)) throw new IllegalArgumentException("Empty decor");
        this.x = x; this.y = y; this.z = z; this.width = width; this.height = height; this.depth = depth;
        this.material = material & 255;
    }
}
