package com.ccs.voxelstrike.map;

/**
 * Per-map lighting. The ray tracer reads these values directly: the sun drives shadow and
 * bounce rays, the sky gradient is the miss shader, and the exposure feeds the tone mapper.
 */
public final class MapEnvironment {
    public final float sunX;
    public final float sunY;
    public final float sunZ;
    public final float sunR;
    public final float sunG;
    public final float sunB;
    public final float sunIntensity;
    /** Angular radius of the sun disc in radians; larger values give softer traced shadows. */
    public final float sunAngle;
    public final float zenithR;
    public final float zenithG;
    public final float zenithB;
    public final float horizonR;
    public final float horizonG;
    public final float horizonB;
    public final float skyIntensity;
    public final float fogDensity;
    public final float exposure;
    public final boolean indoor;

    private MapEnvironment(Builder builder) {
        float length = (float) Math.sqrt(builder.sunX * builder.sunX + builder.sunY * builder.sunY
                + builder.sunZ * builder.sunZ);
        if (length < 1e-5f) {
            length = 1f;
        }
        this.sunX = builder.sunX / length;
        this.sunY = builder.sunY / length;
        this.sunZ = builder.sunZ / length;
        this.sunR = builder.sunR;
        this.sunG = builder.sunG;
        this.sunB = builder.sunB;
        this.sunIntensity = builder.sunIntensity;
        this.sunAngle = builder.sunAngle;
        this.zenithR = builder.zenithR;
        this.zenithG = builder.zenithG;
        this.zenithB = builder.zenithB;
        this.horizonR = builder.horizonR;
        this.horizonG = builder.horizonG;
        this.horizonB = builder.horizonB;
        this.skyIntensity = builder.skyIntensity;
        this.fogDensity = builder.fogDensity;
        this.exposure = builder.exposure;
        this.indoor = builder.indoor;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Mutable configuration for {@link MapEnvironment}. */
    public static final class Builder {
        private float sunX = -0.42f;
        private float sunY = 0.82f;
        private float sunZ = 0.38f;
        private float sunR = 1.0f;
        private float sunG = 0.94f;
        private float sunB = 0.82f;
        private float sunIntensity = 4.2f;
        private float sunAngle = 0.03f;
        private float zenithR = 0.20f;
        private float zenithG = 0.36f;
        private float zenithB = 0.62f;
        private float horizonR = 0.62f;
        private float horizonG = 0.72f;
        private float horizonB = 0.86f;
        private float skyIntensity = 1.0f;
        private float fogDensity = 0.010f;
        private float exposure = 1.0f;
        private boolean indoor;

        public Builder sun(float x, float y, float z) {
            sunX = x;
            sunY = y;
            sunZ = z;
            return this;
        }

        public Builder sunColor(float r, float g, float b, float intensity) {
            sunR = r;
            sunG = g;
            sunB = b;
            sunIntensity = intensity;
            return this;
        }

        public Builder sunAngle(float radians) {
            sunAngle = radians;
            return this;
        }

        public Builder sky(float zr, float zg, float zb, float hr, float hg, float hb,
                           float intensity) {
            zenithR = zr;
            zenithG = zg;
            zenithB = zb;
            horizonR = hr;
            horizonG = hg;
            horizonB = hb;
            skyIntensity = intensity;
            return this;
        }

        public Builder fog(float density) {
            fogDensity = density;
            return this;
        }

        public Builder exposure(float value) {
            exposure = value;
            return this;
        }

        public Builder indoor(boolean value) {
            indoor = value;
            return this;
        }

        public MapEnvironment build() {
            return new MapEnvironment(this);
        }
    }
}
