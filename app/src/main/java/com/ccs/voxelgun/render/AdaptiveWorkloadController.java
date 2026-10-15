package com.ccs.voxelgun.render;

/** Reduces cosmetic work and visibility, never simulation rules, hit detection or bot count. */
public final class AdaptiveWorkloadController {
    private float configuredDistance = 120f, distance = 120f;
    private float seconds;
    private int frames, slowWindows, fastWindows, level;
    private int thermal;

    public void reset(float viewDistance) {
        configuredDistance = Float.isFinite(viewDistance) ? Math.max(48f, viewDistance) : 120f;
        level = slowWindows = fastWindows = frames = thermal = 0;
        seconds = 0f;
        apply();
    }

    public void pauseSampling() { frames = slowWindows = fastWindows = 0; seconds = 0f; }

    public void addFrame(float dt, int targetFps, int thermalStatus) {
        thermal = Math.max(0, Math.min(6, thermalStatus));
        level = Math.max(level, thermal >= 3 ? 3 : thermal >= 2 ? 1 : 0);
        apply(); // Thermal mitigation is immediate; recovery still follows the slow cadence below.
        if (!Float.isFinite(dt) || dt <= 0f || dt > 0.25f || targetFps <= 0) return;
        seconds += dt;
        frames++;
        if (seconds < 2f) return;
        float fps = frames / seconds;
        frames = 0;
        seconds = 0f;
        if (fps < targetFps * .88f) {
            fastWindows = 0;
            if (++slowWindows >= 2) { level = Math.min(3, level + 1); slowWindows = 0; }
        } else if (fps >= targetFps * .97f && thermal < 2) {
            slowWindows = 0;
            if (++fastWindows >= 6) { level = Math.max(0, level - 1); fastWindows = 0; }
        } else { slowWindows = fastWindows = 0; }
        apply();
    }

    private int effectiveLevel() { return Math.max(level, thermal >= 3 ? 3 : thermal >= 2 ? 1 : 0); }
    private void apply() {
        // A large battlefield retains at least 144 m, normal arenas at least 72 m (unless the
        // player deliberately selected less). Never exceed their configured visibility.
        float floor = Math.min(configuredDistance, configuredDistance >= 240f ? 144f : 72f);
        distance = Math.max(floor, configuredDistance * (1f - effectiveLevel() * .14f));
    }
    public float viewDistance() { return distance; }
    public float effectFraction() { return 1f - effectiveLevel() * .22f; }
    public int recommendedFrameCap() { return thermal >= 3 ? 30 : 0; }
}
