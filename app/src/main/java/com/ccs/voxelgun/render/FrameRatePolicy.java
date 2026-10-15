package com.ccs.voxelgun.render;

/** Shared pacing/adaptation target, including display and thermal limits. */
public final class FrameRatePolicy {
    private FrameRatePolicy() {}

    public static int resolve(int requested, float displayHz, int adaptiveCap, int thermalCap,
                              boolean paused) {
        int display = Float.isFinite(displayHz) && displayHz >= 24f
                ? Math.max(24, Math.min(240, Math.round(displayHz))) : 60;
        int cap = requested > 0 ? Math.min(requested, display) : display;
        if (adaptiveCap > 0) cap = Math.min(cap, adaptiveCap);
        if (thermalCap > 0) cap = Math.min(cap, thermalCap);
        return paused ? Math.min(cap, 20) : cap;
    }
}
