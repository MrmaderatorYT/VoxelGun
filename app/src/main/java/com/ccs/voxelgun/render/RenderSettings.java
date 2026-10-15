package com.ccs.voxelgun.render;

/**
 * Graphics configuration. The presets map onto how much ray tracing work each frame can afford;
 * every field can also be overridden individually from the settings screen.
 */
public final class RenderSettings {
    /** Quality tiers, from raster-only to the full traced pipeline. */
    public enum Quality {
        LOW("Low", "Battery saver · raster lighting · lowest GPU load"),
        MEDIUM("Medium", "Balanced · sharper raster lighting and anti-aliasing"),
        HIGH("High", "Soft ray traced sun shadows · bounded mobile workload"),
        ULTRA("Ultra RT", "Software ray tracing · GI and reflections · highest GPU load");

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
        quality = preset == null ? Quality.LOW : preset;
        // Define all preset-owned fields so switching presets never inherits old ray budgets.
        rtShadows = quality == Quality.HIGH || quality == Quality.ULTRA;
        rtGlobalIllumination = quality == Quality.ULTRA;
        rtReflections = quality == Quality.ULTRA;
        rayTracing = rtShadows;
        denoiser = rayTracing;
        temporalAntiAliasing = quality != Quality.LOW;
        bloom = quality == Quality.HIGH || quality == Quality.ULTRA;
        filmGrain = false;
        renderScale = quality == Quality.LOW ? .75f : quality == Quality.MEDIUM ? .85f : 1f;
        traceScale = quality == Quality.ULTRA ? .5f : .35f;
        giSamples = rtGlobalIllumination ? 1 : 0;
        shadowSteps = rtShadows ? (quality == Quality.ULTRA ? 64 : 48) : 0;
        giSteps = quality == Quality.ULTRA ? 48 : 32;
        giRange = quality == Quality.ULTRA ? 32f : 24f;
        lightRange = quality == Quality.ULTRA ? 40f : 32f;
        denoisePasses = denoiser ? (quality == Quality.ULTRA ? 2 : 1) : 0;
        viewDistance = quality == Quality.LOW ? 120f : quality == Quality.MEDIUM ? 150f
                : quality == Quality.HIGH ? 180f : 220f;
    }

    /** Validates custom settings and gives enabled effects a usable mobile work budget. */
    public void normalize() {
        if (quality == null) quality = Quality.LOW;
        renderScale = finiteClamp(renderScale, .6f, 1f, .75f);
        traceScale = finiteClamp(traceScale, .25f, 1f, .35f);
        contrast = finiteClamp(contrast, .8f, 1.6f, 1f);
        brightness = finiteClamp(brightness, .6f, 1.3f, 1f);
        gamma = finiteClamp(gamma, .5f, 2f, 1f);
        fieldOfView = finiteClamp(fieldOfView, 65f, 100f, 78f);
        viewDistance = finiteClamp(viewDistance, 48f, 400f, 120f);
        giRange = finiteClamp(giRange, 8f, 48f, 24f);
        lightRange = finiteClamp(lightRange, 8f, 56f, 32f);
        if (frameRateCap != 0 && frameRateCap != 30 && frameRateCap != 60
                && frameRateCap != 90 && frameRateCap != 120) frameRateCap = 60;
        rayTracing = rtShadows || rtGlobalIllumination || rtReflections;
        shadowSteps = rtShadows ? Math.max(32, Math.min(64, shadowSteps)) : 0;
        giSteps = Math.max(32, Math.min(64, giSteps));
        giSamples = rtGlobalIllumination ? 1 : 0;
        denoisePasses = denoiser && rayTracing ? Math.max(1, Math.min(2, denoisePasses)) : 0;
    }

    public void disableTracing() {
        rayTracing = rtShadows = rtGlobalIllumination = rtReflections = false;
        giSamples = shadowSteps = denoisePasses = 0;
    }

    private static float finiteClamp(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
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
