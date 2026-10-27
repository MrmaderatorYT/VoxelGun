package com.ccs.voxelstrike.render;

import org.junit.Test;
import static org.junit.Assert.*;

public class GammaSettingsTest {
    @Test public void presetsPreserveUserGamma() {
        RenderSettings settings = new RenderSettings();
        settings.gamma = 1.35f;
        for (RenderSettings.Quality quality : RenderSettings.Quality.values()) {
            settings.applyPreset(quality);
            assertEquals(1.35f, settings.gamma, .0001f);
        }
    }
    @Test public void copiedSettingsRetainGamma() {
        RenderSettings settings = new RenderSettings();
        settings.gamma = .75f;
        assertEquals(.75f, settings.copy().gamma, .0001f);
    }
}
