package com.ccs.voxelgun;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ccs.voxelgun.render.AdaptivePerformanceController;

import org.junit.Test;

public final class AdaptivePerformanceControllerTest {
    @Test
    public void missedBudgetLowersResolution() {
        AdaptivePerformanceController controller = new AdaptivePerformanceController();
        controller.reset(1f);

        boolean resized = feed(controller, 45, 3f, 60);

        assertTrue(resized);
        assertTrue(controller.renderScale() < 1f);
    }

    @Test
    public void stableCadenceKeepsConfiguredResolution() {
        AdaptivePerformanceController controller = new AdaptivePerformanceController();
        controller.reset(0.8f);

        boolean resized = feed(controller, 60, 4f, 60);

        assertFalse(resized);
        assertEquals(0.8f, controller.renderScale(), 0.0001f);
        assertFalse(controller.consumeQualityReductionRequest());
    }

    @Test
    public void severeMissRequestsCheaperEffects() {
        AdaptivePerformanceController controller = new AdaptivePerformanceController();
        controller.reset(AdaptivePerformanceController.MIN_RENDER_SCALE);

        feed(controller, 30, 3f, 60);

        assertTrue(controller.consumeQualityReductionRequest());
    }

    @Test public void verySlowFramesAreMeasuredHonestly() {
        AdaptivePerformanceController controller = new AdaptivePerformanceController();
        controller.reset(.75f);
        feed(controller, 2, 8f, 60);
        assertEquals(2f, controller.measuredFps(), .001f);
        assertTrue(controller.consumeQualityReductionRequest());
    }

    @Test public void invalidConfiguredScaleUsesSafeDefault() {
        AdaptivePerformanceController controller = new AdaptivePerformanceController();
        controller.reset(Float.NaN);
        assertEquals(.75f, controller.renderScale(), .001f);
    }

    @Test public void hotDeviceDoesNotRegainResolutionAtLowerThermalFrameCap() {
        AdaptivePerformanceController controller = new AdaptivePerformanceController();
        controller.reset(1f);
        feed(controller, 25, 5f, 60);
        float hotScale = controller.renderScale();
        controller.pauseSampling(); // Renderer drops the old window when the thermal target changes.
        for (int i = 0; i < 30 * 30; i++) controller.addFrame(1f / 30, 30, false);
        assertEquals(hotScale, controller.renderScale(), .0001f);
        controller.pauseSampling();
        feed(controller, 60, 12f, 60);
        assertTrue(controller.renderScale() > hotScale);
    }

    private static boolean feed(AdaptivePerformanceController controller, int actualFps,
                                float seconds, int targetFps) {
        int frames = Math.round(actualFps * seconds);
        boolean changed = false;
        for (int i = 0; i < frames; i++) {
            changed |= controller.addFrame(1f / actualFps, targetFps);
        }
        return changed;
    }
}
