package com.ccs.voxelstrike.render;

/** Absolute pixel budgets keep work independent of phone/tablet display density. */
public final class RenderResolution {
    public static final int LOW_BASE_PIXELS = 1280 * 800;
    public static final int MAX_TRACE_PIXELS = 640 * 360;
    private RenderResolution() {}

    public static int scenePixelBudget(RenderSettings.Quality quality) {
        if (quality == RenderSettings.Quality.ULTRA) return 1920 * 1080;
        if (quality == RenderSettings.Quality.HIGH) return 1600 * 900;
        return LOW_BASE_PIXELS;
    }

    public static float surfaceScale(int width, int height, RenderSettings.Quality quality,
                                     float selectedScale) {
        return boundedScale(width, height, selectedScale, scenePixelBudget(quality));
    }

    public static float traceScale(int width, int height, float selectedScale) {
        // A slider value is a ceiling. Even 100% cannot turn RT into millions of rays/pixel work.
        float selected = Float.isFinite(selectedScale)
                ? Math.max(.25f, Math.min(1f, selectedScale)) : .35f;
        long pixels = (long) Math.max(1, width) * Math.max(1, height);
        return Math.min(selected, (float) Math.sqrt((double) MAX_TRACE_PIXELS / pixels));
    }

    private static float boundedScale(int width, int height, float selectedScale, int budget) {
        float scale = Float.isFinite(selectedScale)
                ? Math.max(.1f, Math.min(1f, selectedScale)) : .75f;
        long pixels = (long) Math.max(1, width) * Math.max(1, height);
        return scale * Math.min(1f, (float) Math.sqrt((double) budget / pixels));
    }
}
