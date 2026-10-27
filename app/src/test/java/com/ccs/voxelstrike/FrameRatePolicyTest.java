package com.ccs.voxelstrike;

import com.ccs.voxelstrike.render.FrameRatePolicy;
import org.junit.Test;
import static org.junit.Assert.*;

public class FrameRatePolicyTest {
    @Test public void targetNeverExceedsActualDisplayRate() {
        assertEquals(60, FrameRatePolicy.resolve(120, 59.94f, 0, 0, false));
        assertEquals(60, FrameRatePolicy.resolve(60, 120f, 0, 0, false));
        assertEquals(120, FrameRatePolicy.resolve(0, 119.88f, 0, 0, false));
        assertEquals(60, FrameRatePolicy.resolve(120, Float.NaN, 0, 0, false));
    }
    @Test public void displayModeStillHonorsThermalAndAdaptiveCaps() {
        assertEquals(30, FrameRatePolicy.resolve(0, 120f, 0, 30, false));
        assertEquals(30, FrameRatePolicy.resolve(0, 120f, 30, 0, false));
        assertEquals(30, FrameRatePolicy.resolve(120, 120f, 60, 30, false));
    }
    @Test public void pauseHasALowCadenceAndResumeRestoresSelection() {
        assertEquals(20, FrameRatePolicy.resolve(120, 120f, 0, 0, true));
        assertEquals(120, FrameRatePolicy.resolve(120, 120f, 0, 0, false));
    }
}
