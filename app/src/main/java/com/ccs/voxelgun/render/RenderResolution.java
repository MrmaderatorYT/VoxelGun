package com.ccs.voxelgun.render;

/** Pixel work must scale with the quality budget, not an arbitrarily large tablet panel. */
public final class RenderResolution {
    public static final int LOW_BASE_PIXELS = 1280 * 800;
    private RenderResolution() {}

    public static float surfaceScale(int width, int height, RenderSettings.Quality quality,
                                     float selectedScale) {
        float scale = Math.max(.1f, Math.min(1f, selectedScale));
        if (quality != RenderSettings.Quality.LOW) return scale;
        long pixels = (long) Math.max(1, width) * Math.max(1, height);
        // At 100% Low budgets ~1MP; at 75% it uses ~576k pixels, regardless of panel density.
        // A small panel is never enlarged. HUD/UI remain in the full-resolution overlay.
        float base = Math.min(1f, (float) Math.sqrt((double) LOW_BASE_PIXELS / pixels));
        return scale * base;
    }
}
