package com.ccs.voxelstrike.render;

/** Rolling 300-frame histogram; no allocation or sorting when recording frames. */
public final class FrameMetrics {
    private final int[] samples = new int[300];
    private final int[] histogram = new int[1001];
    private int cursor, count;
    private long sum;
    public void record(float seconds) {
        if (!Float.isFinite(seconds) || seconds <= 0) return;
        int ms = Math.max(1, Math.min(1000, Math.round(seconds * 1000)));
        if (count == samples.length) {
            int old = samples[cursor]; histogram[old]--; sum -= old;
        } else count++;
        samples[cursor] = ms; histogram[ms]++; sum += ms;
        cursor = (cursor + 1) % samples.length;
    }
    public float averageMs() { return count == 0 ? 0 : (float) sum / count; }
    public int p95Ms() {
        int needed = (int) Math.ceil(count * .95);
        if (needed == 0) return 0;
        for (int ms = 1, seen = 0; ms < histogram.length; ms++) {
            seen += histogram[ms]; if (seen >= needed) return ms;
        }
        return 1000;
    }
}
