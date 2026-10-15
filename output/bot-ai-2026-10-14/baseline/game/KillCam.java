package com.ccs.voxelgun.game;

/**
 * A few seconds of every combatant's eye, kept so a death can be replayed from the killer's side.
 *
 * <p>"Where did that come from?" is the most common complaint a player has about dying, and the
 * answer is always in the seconds before it. Samples are taken at a fixed rate into a ring buffer
 * — position, yaw and pitch only — which is small enough to keep for everyone without noticing:
 * ten combatants at 20&nbsp;Hz for three seconds is six hundred samples in total.
 */
public final class KillCam {
    /** How far back the replay reaches. */
    public static final float DURATION_SECONDS = 3f;
    private static final float SAMPLE_INTERVAL = 1f / 20f;
    private static final int SAMPLES = (int) (DURATION_SECONDS / SAMPLE_INTERVAL) + 1;
    private static final int STRIDE = 5;  // x, y, z, yaw, pitch

    private final int capacity;
    private final float[] samples;
    private final int[] writeIndex;
    private final int[] written;
    private float sampleTimer;

    /** Replay state: who is being watched and how far into the replay we are. */
    private int replaySlot = -1;
    private float replayTime;

    public KillCam(int combatants) {
        capacity = Math.max(1, combatants);
        samples = new float[capacity * SAMPLES * STRIDE];
        writeIndex = new int[capacity];
        written = new int[capacity];
    }

    /** Records one sample per combatant when enough time has passed. */
    public void record(float dt, java.util.List<Combatant> combatants) {
        sampleTimer += dt;
        if (sampleTimer < SAMPLE_INTERVAL) {
            return;
        }
        sampleTimer = 0f;
        for (int slot = 0; slot < combatants.size() && slot < capacity; slot++) {
            Combatant combatant = combatants.get(slot);
            int index = writeIndex[slot];
            int base = (slot * SAMPLES + index) * STRIDE;
            samples[base] = combatant.eyeX();
            samples[base + 1] = combatant.eyeY();
            samples[base + 2] = combatant.eyeZ();
            samples[base + 3] = combatant.yaw;
            samples[base + 4] = combatant.pitch;
            writeIndex[slot] = (index + 1) % SAMPLES;
            written[slot] = Math.min(written[slot] + 1, SAMPLES);
        }
    }

    /** Starts a replay of {@code slot}'s last seconds. Returns false when nothing was recorded. */
    public boolean start(int slot) {
        if (slot < 0 || slot >= capacity || written[slot] < 4) {
            return false;
        }
        replaySlot = slot;
        replayTime = 0f;
        return true;
    }

    public void stop() {
        replaySlot = -1;
    }

    public boolean playing() {
        return replaySlot >= 0 && replayTime <= DURATION_SECONDS;
    }

    /**
     * Advances the replay and writes the camera for this moment into {@code out} as
     * {x, y, z, yaw, pitch}. Samples are interpolated so the playback is smooth at any frame rate.
     */
    public boolean advance(float dt, float[] out) {
        if (!playing()) {
            return false;
        }
        replayTime += dt;
        int available = written[replaySlot];
        float span = (available - 1) * SAMPLE_INTERVAL;
        float position = Math.min(span, Math.max(0f, span - DURATION_SECONDS) + replayTime);
        float exact = position / SAMPLE_INTERVAL;
        int first = Math.min(available - 1, (int) exact);
        int second = Math.min(available - 1, first + 1);
        float blend = exact - first;

        int oldest = (writeIndex[replaySlot] - available + SAMPLES) % SAMPLES;
        int baseA = (replaySlot * SAMPLES + (oldest + first) % SAMPLES) * STRIDE;
        int baseB = (replaySlot * SAMPLES + (oldest + second) % SAMPLES) * STRIDE;
        for (int i = 0; i < STRIDE; i++) {
            float a = samples[baseA + i];
            float b = samples[baseB + i];
            // Yaw wraps, so it is blended the short way round rather than through a full turn.
            out[i] = i == 3 ? a + com.ccs.voxelgun.math.MathUtil.angleDelta(a, b) * blend
                    : a + (b - a) * blend;
        }
        return true;
    }

    /** Forgets every trail; used when a round restarts and old positions would be nonsense. */
    public void clear() {
        java.util.Arrays.fill(writeIndex, 0);
        java.util.Arrays.fill(written, 0);
        replaySlot = -1;
        sampleTimer = 0f;
    }
}
