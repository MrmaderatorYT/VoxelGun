package com.ccs.voxelgun;

import com.ccs.voxelgun.render.RenderResolution;
import com.ccs.voxelgun.render.RenderSettings;
import org.junit.Test;
import static org.junit.Assert.*;

public class RenderResolutionTest {
    @Test public void lowTabletAtSeventyFivePercentIs960By600() {
        float scale = RenderResolution.surfaceScale(2560, 1600, RenderSettings.Quality.LOW, .75f);
        assertEquals(960, Math.round(2560 * scale));
        assertEquals(600, Math.round(1600 * scale));
    }
    @Test public void lowBudgetRemainsConstantOnVeryLargePanels() {
        for (int width : new int[]{2560, 3840, 7680}) {
            int height = width * 10 / 16;
            float scale = RenderResolution.surfaceScale(width, height, RenderSettings.Quality.LOW, 1f);
            assertEquals(1280, Math.round(width * scale));
            assertEquals(800, Math.round(height * scale));
        }
    }
    @Test public void smallPanelsAndHigherQualitiesKeepSelectedScale() {
        assertEquals(.75f, RenderResolution.surfaceScale(800, 480, RenderSettings.Quality.LOW, .75f), .0001f);
        for (RenderSettings.Quality quality : new RenderSettings.Quality[]{RenderSettings.Quality.MEDIUM,
                RenderSettings.Quality.HIGH, RenderSettings.Quality.ULTRA}) {
            assertEquals(.75f, RenderResolution.surfaceScale(2560, 1600, quality, .75f), .0001f);
        }
    }
}
