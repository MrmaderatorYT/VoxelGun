package com.ccs.voxelgun.math;

/** Small helpers shared by gameplay, AI and rendering. */
public final class MathUtil {
    public static final float TAU = (float) (Math.PI * 2.0);

    private MathUtil() {}

    public static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    public static int clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Frame-rate independent exponential smoothing. */
    public static float damp(float current, float target, float rate, float dt) {
        return lerp(current, target, 1f - (float) Math.exp(-rate * dt));
    }

    /** Wraps an angle into (-PI, PI]. */
    public static float wrapAngle(float radians) {
        float wrapped = radians % TAU;
        if (wrapped > Math.PI) {
            wrapped -= TAU;
        } else if (wrapped <= -Math.PI) {
            wrapped += TAU;
        }
        return wrapped;
    }

    /** Shortest signed rotation from {@code from} to {@code to}. */
    public static float angleDelta(float from, float to) {
        return wrapAngle(to - from);
    }

    public static float approachAngle(float current, float target, float maxStep) {
        float delta = angleDelta(current, target);
        if (delta > maxStep) {
            delta = maxStep;
        } else if (delta < -maxStep) {
            delta = -maxStep;
        }
        return wrapAngle(current + delta);
    }

    public static float smoothstep(float edge0, float edge1, float value) {
        float t = clamp((value - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}
