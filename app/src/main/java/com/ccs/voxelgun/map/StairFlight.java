package com.ccs.voxelgun.map;

/** Immutable authoring data retained for final-world traversal validation, not per-frame work. */
public final class StairFlight {
    public final int x, y, z, direction, steps, width;

    public StairFlight(int x, int y, int z, int direction, int steps, int width) {
        if (direction < 0 || direction > 3 || steps < 1 || width < 2)
            throw new IllegalArgumentException("A staircase needs a direction, rise and body-wide treads");
        this.x = x; this.y = y; this.z = z;
        this.direction = direction; this.steps = steps; this.width = width;
    }

    public int xAt(int step) { return x + (direction == 0 ? step : direction == 1 ? -step : 0); }
    public int zAt(int step) { return z + (direction == 2 ? step : direction == 3 ? -step : 0); }
    public int feetAt(int step) { return y + step + 1; }
    public float centerX(int step) { return xAt(step) + (direction > 1 ? width * .5f : .5f); }
    public float centerZ(int step) { return zAt(step) + (direction <= 1 ? width * .5f : .5f); }
}
