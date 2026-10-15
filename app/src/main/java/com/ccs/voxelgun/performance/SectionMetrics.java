package com.ccs.voxelgun.performance;

/** Single-writer, bounded rolling CPU timings. No allocations after construction. */
public final class SectionMetrics {
    public static final int PHYSICS = 0, AI = 1, COMBAT = 2, EFFECTS = 3,
            OBJECTIVES = 4, UPLOAD = 5, RENDER_CPU = 6, COUNT = 7;
    private static final int WINDOW = 120;
    private final long[][] samples = new long[COUNT][WINDOW];
    private final long[] totals = new long[COUNT];
    private final int[] cursor = new int[COUNT], size = new int[COUNT];

    public void record(int section, long nanos) {
        if (section < 0 || section >= COUNT || nanos < 0) return;
        int at = cursor[section];
        totals[section] += nanos - samples[section][at];
        samples[section][at] = nanos;
        cursor[section] = (at + 1) % WINDOW;
        size[section] = Math.min(WINDOW, size[section] + 1);
    }

    public int averageMicros(int section) {
        return size[section] == 0 ? 0 : (int) Math.min(Integer.MAX_VALUE,
                totals[section] / size[section] / 1000L);
    }
}
