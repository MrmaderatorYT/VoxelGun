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
    @Test public void everyTierHasAPixelCeilingOnLargeAndUnusualPanels() {
        for (RenderSettings.Quality quality : RenderSettings.Quality.values()) {
            for (int[] panel : new int[][]{{800,480}, {2560,1600}, {3840,2160}, {7680,4320},
                    {1080,2400}, {5120,1440}}) {
                float scale = RenderResolution.surfaceScale(panel[0], panel[1], quality, 1f);
                long scenePixels = (long) (panel[0] * scale) * (int) (panel[1] * scale);
                assertTrue(scenePixels <= RenderResolution.scenePixelBudget(quality));
                assertTrue(scale > 0f && scale <= 1f);
                float trace = RenderResolution.traceScale(panel[0], panel[1], 1f);
                long tracePixels = (long) (panel[0] * trace) * (int) (panel[1] * trace);
                assertTrue(tracePixels <= RenderResolution.MAX_TRACE_PIXELS);
                assertTrue(trace > 0f && trace <= 1f);
            }
        }
    }
    @Test public void smallPanelsAreNeverUpscaledAndCustomTraceScaleIsACeiling() {
        assertEquals(.75f, RenderResolution.surfaceScale(800, 480, RenderSettings.Quality.LOW, .75f), .0001f);
        assertEquals(.25f, RenderResolution.traceScale(800, 480, .25f), .0001f);
        assertEquals(1f, RenderResolution.traceScale(160, 90, 1f), .0001f);
        assertTrue(Float.isFinite(RenderResolution.surfaceScale(0, 0, null, Float.NaN)));
        assertTrue(Float.isFinite(RenderResolution.traceScale(0, 0, Float.NaN)));
    }
}
