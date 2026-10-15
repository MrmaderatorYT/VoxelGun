package com.ccs.voxelgun;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ccs.voxelgun.render.RenderSettings;

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
}
