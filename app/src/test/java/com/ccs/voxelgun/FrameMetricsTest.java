package com.ccs.voxelgun;
import com.ccs.voxelgun.render.FrameMetrics;
import org.junit.Test;
import static org.junit.Assert.*;
public class FrameMetricsTest {
    @Test public void rollingWindowEvictsOldSamplesAndIgnoresInvalidValues() {
        FrameMetrics metrics = new FrameMetrics();
        assertEquals(0, metrics.p95Ms());
        for (int i = 0; i < 280; i++) metrics.record(.016f);
        for (int i = 0; i < 20; i++) metrics.record(.100f);
        assertEquals(100, metrics.p95Ms());
        assertEquals(21.6f, metrics.averageMs(), .01f);
        for (int i = 0; i < 300; i++) metrics.record(.020f);
        metrics.record(Float.NaN); metrics.record(-1);
        assertEquals(20, metrics.p95Ms());
        assertEquals(20, metrics.averageMs(), .01f);
    }
}
