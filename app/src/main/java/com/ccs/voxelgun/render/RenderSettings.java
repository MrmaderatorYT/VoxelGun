package com.ccs.voxelgun.render;

/**
 * Graphics configuration. The presets map onto how much ray tracing work each frame can afford;
 * every field can also be overridden individually from the settings screen.
 */
public final class RenderSettings {
    /** Quality tiers, from raster-only to the full traced pipeline. */
    public enum Quality {
        LOW("Low", "Fast raster, 1 MP base resolution, no ray tracing"),
        MEDIUM("Medium", "Ray traced shadows"),
        HIGH("High", "Traced shadows and bounce lighting"),
        ULTRA("Ultra RT", "Full path traced lighting and reflections");

        private final String label;
        private final String description;

        Quality(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String label() {
            return label;
        }

        public String description() {
            return description;
        }
    }

    public Quality quality = Quality.LOW;
    public boolean rayTracing;
    public boolean rtShadows;
    public boolean rtGlobalIllumination;
    public boolean rtReflections;
    public boolean denoiser;
    public boolean temporalAntiAliasing;
    public boolean bloom;
    public boolean filmGrain;
    /** Optional blue/red rim used to identify friendly and enemy combatants. */
    public boolean playerHighlights = true;
    /** Dynamically trades optional effects and resolution for the selected frame-rate target. */
    public boolean adaptivePerformance = true;
    /** Fraction of the surface resolution the scene is rendered at. */
    public float renderScale = 0.75f;
    /** Fraction of the render resolution the ray tracing passes run at. */
    public float traceScale = 0.5f;
    public int giSamples;
    public int shadowSteps;
    public int giSteps;
    public float giRange = 34f;
    public int denoisePasses = 3;
    /** How far a shaded point looks for emissive blocks, in world units. */
    public float lightRange = 42f;
    /** Log contrast around middle grey applied in the tone mapper; 1 is the untouched image. */
    public float contrast = 1f;
    /** Exposure trim on top of the map's own exposure; 1 is the untouched image. */
    public float brightness = 1f;
    public float gamma = 1f;
    public float fieldOfView = 78f;
    public float viewDistance = 120f;
    public boolean showFps;
    /** Frames per second to pace to; 0 leaves the display's own rate in charge. */
    public int frameRateCap = 60;

    public RenderSettings() {
        applyPreset(Quality.LOW);
    }

    public void applyPreset(Quality preset) {
        quality = preset;
        switch (preset) {
            case LOW:
                rayTracing = false;
                rtShadows = false;
                rtGlobalIllumination = false;
                rtReflections = false;
                denoiser = false;
                temporalAntiAliasing = false;
                bloom = false;
                filmGrain = false;
                renderScale = 0.75f;
                traceScale = 0.5f;
                giSamples = 0;
                shadowSteps = 0;
                giSteps = 0;
                denoisePasses = 0;
                viewDistance = 120f;
                break;
            case MEDIUM:
                rayTracing = true;
                rtShadows = true;
                rtGlobalIllumination = false;
                rtReflections = false;
                denoiser = true;
                temporalAntiAliasing = true;
                bloom = false;
                filmGrain = false;
                renderScale = 0.85f;
                traceScale = 0.5f;
                giSamples = 0;
                shadowSteps = 64;
                giSteps = 48;
                giRange = 24f;
                lightRange = 34f;
                denoisePasses = 2;
                viewDistance = 150f;
                break;
            case ULTRA:
                rayTracing = true;
                rtShadows = true;
                rtGlobalIllumination = true;
                rtReflections = true;
                denoiser = true;
                temporalAntiAliasing = true;
                bloom = true;
                filmGrain = true;
                renderScale = 1f;
                traceScale = 0.75f;
                giSamples = 2;
                shadowSteps = 128;
                giSteps = 96;
                giRange = 48f;
                lightRange = 56f;
                denoisePasses = 3;
                viewDistance = 220f;
                break;
            case HIGH:
            default:
                rayTracing = true;
                rtShadows = true;
                rtGlobalIllumination = true;
                rtReflections = false;
                denoiser = true;
                temporalAntiAliasing = true;
                bloom = true;
                filmGrain = true;
                renderScale = 1f;
                traceScale = 0.5f;
                giSamples = 1;
                shadowSteps = 96;
                giSteps = 72;
                giRange = 34f;
                lightRange = 42f;
                denoisePasses = 3;
                viewDistance = 190f;
                break;
        }
    }

    /** True when the ray tracing passes need to run at all. */
    public boolean tracingActive() {
        return rayTracing && (rtShadows || rtGlobalIllumination || rtReflections);
    }

    public RenderSettings copy() {
        RenderSettings other = new RenderSettings();
        other.quality = quality;
        other.rayTracing = rayTracing;
        other.rtShadows = rtShadows;
        other.rtGlobalIllumination = rtGlobalIllumination;
        other.rtReflections = rtReflections;
        other.denoiser = denoiser;
        other.temporalAntiAliasing = temporalAntiAliasing;
        other.bloom = bloom;
        other.filmGrain = filmGrain;
        other.playerHighlights = playerHighlights;
        other.adaptivePerformance = adaptivePerformance;
        other.renderScale = renderScale;
        other.traceScale = traceScale;
        other.giSamples = giSamples;
        other.shadowSteps = shadowSteps;
        other.giSteps = giSteps;
        other.giRange = giRange;
        other.lightRange = lightRange;
        other.denoisePasses = denoisePasses;
        other.contrast = contrast;
        other.brightness = brightness;
        other.gamma = gamma;
        other.fieldOfView = fieldOfView;
        other.viewDistance = viewDistance;
        other.showFps = showFps;
        other.frameRateCap = frameRateCap;
        return other;
    }
}
