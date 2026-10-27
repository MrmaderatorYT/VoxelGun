package com.ccs.voxelstrike;

import com.ccs.voxelstrike.render.AdaptiveWorkloadController;
import com.ccs.voxelstrike.performance.SectionMetrics;
import org.junit.Test;
import static org.junit.Assert.*;

public final class AdaptiveWorkloadControllerTest {
    @Test public void sustainedLoadReducesVisibleWorkWithoutChangingConfiguredCeiling() {
        AdaptiveWorkloadController c = controller(240);
        feed(c, 20, 16, 60, 0);
        assertEquals(144, c.viewDistance(), .001f);
        assertEquals(.34f, c.effectFraction(), .001f);
        feed(c, 60, 42, 60, 0);
        assertEquals(240, c.viewDistance(), .001f);
        assertEquals(1f, c.effectFraction(), .001f);
    }
    @Test public void oneSpikeDoesNotDropVisibility() {
        AdaptiveWorkloadController c = controller(120);
        feed(c, 20, 2, 60, 0);
        feed(c, 60, 4, 60, 0);
        assertEquals(120, c.viewDistance(), .001f);
    }
    @Test public void severeThermalSignalImmediatelyLimitsWorkAndFrameCap() {
        AdaptiveWorkloadController c = controller(400);
        c.addFrame(1 / 60f, 60, 3);
        assertEquals(232, c.viewDistance(), .001f);
        assertEquals(30, c.recommendedFrameCap());
        assertEquals(.34f, c.effectFraction(), .001f);
    }
    @Test public void deliberatelyShortVisibilityIsNeverIncreased() {
        AdaptiveWorkloadController c = controller(48);
        feed(c, 20, 20, 60, 0);
        assertEquals(48, c.viewDistance(), .001f);
    }
    @Test public void thermalRecoveryRequiresStableCadenceInsteadOfOneCoolCallback() {
        AdaptiveWorkloadController c = controller(240);
        c.addFrame(1 / 60f, 60, 3);
        c.addFrame(1 / 60f, 60, 0);
        assertEquals(144, c.viewDistance(), .001f);
        feed(c, 60, 42, 60, 0);
        assertEquals(240, c.viewDistance(), .001f);
    }
    @Test public void pauseDropsIncompleteWindowsAndInvalidSamplesCannotPoisonState() {
        AdaptiveWorkloadController c = controller(120);
        feed(c, 20, 1, 60, 0);
        c.pauseSampling();
        c.addFrame(Float.NaN, 60, 0); c.addFrame(Float.POSITIVE_INFINITY, 60, 0);
        c.addFrame(-1, 60, 0);
        feed(c, 60, 3, 60, 0);
        assertEquals(120, c.viewDistance(), .001f);
    }
    @Test public void rollingTimingWindowEvictsOldSamples() {
        SectionMetrics m = new SectionMetrics();
        m.record(SectionMetrics.AI, 90_000_000);
        for (int i = 0; i < 120; i++) m.record(SectionMetrics.AI, 100_000);
        assertEquals(100, m.averageMicros(SectionMetrics.AI));
        assertEquals(0, m.averageMicros(SectionMetrics.PHYSICS));
        m.record(SectionMetrics.AI, -1);
        assertEquals(100, m.averageMicros(SectionMetrics.AI));
    }
    private AdaptiveWorkloadController controller(float distance) {
        AdaptiveWorkloadController c = new AdaptiveWorkloadController(); c.reset(distance); return c;
    }
    private void feed(AdaptiveWorkloadController c, int fps, int seconds, int target, int thermal) {
        for (int i = 0; i < fps * seconds; i++) c.addFrame(1f / fps, target, thermal);
    }
}
