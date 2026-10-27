package com.ccs.voxelstrike;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ccs.voxelstrike.render.RenderSettings;

import org.junit.Test;

/** Guards the inexpensive first-launch graphics configuration. */
public class RenderSettingsTest {
    @Test
    public void newSettingsDefaultToRasterOnlyLowPreset() {
        RenderSettings settings = new RenderSettings();

        assertEquals(RenderSettings.Quality.LOW, settings.quality);
        assertFalse(settings.rayTracing);
        assertFalse(settings.rtShadows);
        assertFalse(settings.rtGlobalIllumination);
        assertFalse(settings.rtReflections);
        assertFalse(settings.denoiser);
        assertFalse(settings.temporalAntiAliasing);
        assertFalse(settings.bloom);
        assertFalse(settings.tracingActive());
        assertTrue(settings.playerHighlights);
        assertEquals(0.75f, settings.renderScale, 0.0001f);
        assertEquals(120f, settings.viewDistance, 0.0001f);
    }

    @Test public void everyCustomEffectWorksOnEveryPreset() {
        for (RenderSettings.Quality quality : RenderSettings.Quality.values()) {
            for (int effect = 0; effect < 3; effect++) {
                RenderSettings settings = new RenderSettings();
                settings.applyPreset(quality);
                settings.rtShadows = effect == 0;
                settings.rtGlobalIllumination = effect == 1;
                settings.rtReflections = effect == 2;
                settings.normalize();
                assertTrue(settings.tracingActive());
                assertEquals(effect == 1 ? 1 : 0, settings.giSamples);
                assertTrue(settings.giSteps >= 32 && settings.giSteps <= 64);
                if (effect == 0) assertTrue(settings.shadowSteps >= 32);
                else assertEquals(0, settings.shadowSteps);
            }
        }
    }

    @Test public void presetsDoNotInheritRayBudgetsOrChangePicturePreferences() {
        for (RenderSettings.Quality before : RenderSettings.Quality.values()) {
            for (RenderSettings.Quality after : RenderSettings.Quality.values()) {
                RenderSettings reused = new RenderSettings();
                reused.applyPreset(before);
                reused.gamma = 1.25f; reused.frameRateCap = 30;
                reused.applyPreset(after);
                RenderSettings fresh = new RenderSettings(); fresh.applyPreset(after);
                assertEquals(fresh.giRange, reused.giRange, .0001f);
                assertEquals(fresh.lightRange, reused.lightRange, .0001f);
                assertEquals(fresh.giSteps, reused.giSteps);
                assertEquals(fresh.shadowSteps, reused.shadowSteps);
                assertEquals(fresh.denoisePasses, reused.denoisePasses);
                assertEquals(fresh.traceScale, reused.traceScale, .0001f);
                assertEquals(1.25f, reused.gamma, .0001f);
                assertEquals(30, reused.frameRateCap);
                assertFalse(reused.filmGrain);
            }
        }
    }

    @Test public void balancedPresetDoesNotStartRayTracing() {
        RenderSettings settings = new RenderSettings();
        settings.applyPreset(RenderSettings.Quality.MEDIUM);
        settings.normalize();
        assertFalse(settings.tracingActive());
        assertEquals(0, settings.giSamples);
        assertEquals(0, settings.denoisePasses);
        assertTrue(settings.temporalAntiAliasing);
    }

    @Test public void invalidPreferencesCannotPoisonRenderTargets() {
        RenderSettings settings = new RenderSettings();
        settings.renderScale = Float.NaN; settings.traceScale = Float.POSITIVE_INFINITY;
        settings.fieldOfView = -1; settings.gamma = Float.NaN;
        settings.frameRateCap = Integer.MAX_VALUE;
        settings.normalize();
        assertEquals(.75f, settings.renderScale, .0001f);
        assertEquals(.35f, settings.traceScale, .0001f);
        assertEquals(65f, settings.fieldOfView, .0001f);
        assertEquals(1f, settings.gamma, .0001f);
        assertEquals(60, settings.frameRateCap);
    }
}
