package com.ccs.voxelgun.render;

/**
 * Allocation-free dynamic-resolution controller driven by the cadence of presented frames.
 *
 * <p>It reacts quickly to missed frame budgets, but restores resolution only after several stable
 * windows. That asymmetry avoids oscillation around a device's sustainable GPU load.
 */
public final class AdaptivePerformanceController {
    public static final float MIN_RENDER_SCALE = 0.55f;

    private static final float SAMPLE_SECONDS = 1f;
    private static final float CHANGE_COOLDOWN_SECONDS = 0.75f;
    private static final int FAST_WINDOWS_TO_RAISE = 5;
    /** Windows spent scraping the bottom before the frame target itself is halved. */
    private static final int WINDOWS_BEFORE_HALVING = 3;
    /** The fallback cadence: every 60Hz panel divides evenly into it. */
    private static final int FALLBACK_FPS = 30;

    private float configuredScale = 1f;
    private float renderScale = 1f;
    private float sampleSeconds;
    private float cooldownSeconds;
    private int sampleFrames;
    private int fastWindows;
    private boolean qualityReductionRequested;
    private float measuredFps;
    private int bottomedWindows;
    private int recoveredWindows;
    private int recommendedCap;

    /** Starts a fresh session at the player's configured resolution. */
    public void reset(float scale) {
        configuredScale = clampScale(scale);
        renderScale = configuredScale;
        resetSample();
        cooldownSeconds = CHANGE_COOLDOWN_SECONDS;
        fastWindows = 0;
        qualityReductionRequested = false;
        measuredFps = 0f;
        bottomedWindows = 0;
        recoveredWindows = 0;
        recommendedCap = 0;
    }

    /** Drops the current measurement window without undoing an already selected scale. */
    public void pauseSampling() {
        resetSample();
        cooldownSeconds = Math.max(cooldownSeconds, 0.25f);
        fastWindows = 0;
    }

    /**
     * Adds one presented frame.
     *
     * @return true when render targets should be resized to {@link #renderScale()}
     */
    public boolean addFrame(float frameSeconds, int targetFps) {
        if (!Float.isFinite(frameSeconds) || frameSeconds <= 0f || targetFps <= 0) {
            return false;
        }
        if (cooldownSeconds > 0f) {
            cooldownSeconds -= Math.min(frameSeconds, 0.1f);
            return false;
        }

        sampleSeconds += Math.min(frameSeconds, 0.1f);
        sampleFrames++;
        if (sampleSeconds < SAMPLE_SECONDS) {
            return false;
        }

        measuredFps = sampleFrames / sampleSeconds;
        resetSample();

        // Once resolution is at the floor and the device still cannot hold the target, chasing
        // it produces an uneven 30-to-45ms cadence. Halving the target instead lets the pacer
        // hold a steady beat, which reads as smoother than the same average frame rate does.
        boolean bottomed = renderScale <= MIN_RENDER_SCALE + 0.001f;
        if (bottomed && measuredFps < targetFps * 0.80f) {
            if (++bottomedWindows >= WINDOWS_BEFORE_HALVING && recommendedCap == 0
                    && targetFps > FALLBACK_FPS && measuredFps > FALLBACK_FPS * 0.9f) {
                recommendedCap = FALLBACK_FPS;
            }
        } else if (measuredFps > targetFps * 0.97f) {
            bottomedWindows = 0;
            // Conditions can improve — a quiet corridor after a firefight, or a scene the GPU
            // handles easily — so a halved target is given back rather than kept for the match.
            if (recommendedCap > 0 && ++recoveredWindows >= FAST_WINDOWS_TO_RAISE
                    && renderScale >= configuredScale - 0.001f) {
                recommendedCap = 0;
                recoveredWindows = 0;
            }
        } else {
            recoveredWindows = 0;
        }

        float severeThreshold = targetFps * 0.70f;
        float slowThreshold = targetFps * 0.92f;
        if (measuredFps < slowThreshold) {
            fastWindows = 0;
            if (measuredFps < severeThreshold) {
                qualityReductionRequested = true;
            }
            if (renderScale > MIN_RENDER_SCALE + 0.001f) {
                float step = measuredFps < severeThreshold ? 0.15f
                        : measuredFps < targetFps * 0.85f ? 0.10f : 0.05f;
                renderScale = Math.max(MIN_RENDER_SCALE, renderScale - step);
                cooldownSeconds = CHANGE_COOLDOWN_SECONDS;
                return true;
            }
            qualityReductionRequested = true;
            cooldownSeconds = CHANGE_COOLDOWN_SECONDS;
            return false;
        }

        if (measuredFps >= targetFps * 0.985f
                && renderScale < configuredScale - 0.001f) {
            fastWindows++;
            if (fastWindows >= FAST_WINDOWS_TO_RAISE) {
                renderScale = Math.min(configuredScale, renderScale + 0.05f);
                fastWindows = 0;
                cooldownSeconds = CHANGE_COOLDOWN_SECONDS;
                return true;
            }
        } else {
            fastWindows = 0;
        }
        return false;
    }

    public float renderScale() {
        return renderScale;
    }

    public float measuredFps() {
        return measuredFps;
    }

    /**
     * A frame cap the device can actually sustain, or zero while the configured one is fine.
     *
     * <p>Only ever suggested after resolution has bottomed out, so it is a last resort rather
     * than an early surrender.
     */
    public int recommendedFrameCap() {
        return recommendedCap;
    }

    /** Returns and clears the request to shed one optional graphics feature. */
    public boolean consumeQualityReductionRequest() {
        boolean requested = qualityReductionRequested;
        qualityReductionRequested = false;
        return requested;
    }

    private void resetSample() {
        sampleSeconds = 0f;
        sampleFrames = 0;
    }

    private static float clampScale(float scale) {
        return Math.max(MIN_RENDER_SCALE, Math.min(1f, scale));
    }
}
