package com.ccs.voxelgun.game;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Bounded 20 Hz best-run recording. Both recording and interpolation reuse their storage; no
 * vectors, collections, files or strings are created in the simulation/render update.
 */
public final class MovementGhost {
    public static final int MAX_SAMPLES = 6001; // Five minutes, including the finish sample.
    private static final int SAMPLE_INTERVAL_MS = 50;
    private final int[] runTime = new int[MAX_SAMPLES], bestTime = new int[MAX_SAMPLES];
    private final float[] runX = new float[MAX_SAMPLES], runY = new float[MAX_SAMPLES];
    private final float[] runZ = new float[MAX_SAMPLES], runYaw = new float[MAX_SAMPLES];
    private final float[] bestX = new float[MAX_SAMPLES], bestY = new float[MAX_SAMPLES];
    private final float[] bestZ = new float[MAX_SAMPLES], bestYaw = new float[MAX_SAMPLES];
    private final boolean[] runCrouch = new boolean[MAX_SAMPLES], bestCrouch = new boolean[MAX_SAMPLES];
    private int runCount;
    private volatile int bestCount, bestDuration, revision;
    private boolean overflow;

    public int bestRevision() { return revision; }
    public int bestDurationMillis() { return bestDuration; }
    public int sampleCount() { return bestCount; }
    public boolean hasBest() { return bestCount >= 2 && bestDuration > 0; }
    public void resetRun() { runCount = 0; overflow = false; }

    public void record(int timeMillis, float x, float y, float z, float yaw, boolean crouching,
                       boolean force) {
        if (overflow || timeMillis < 0 || !finite(x, y, z, yaw)) return;
        if (runCount > 0) {
            int lastTime = runTime[runCount - 1];
            if (timeMillis < lastTime || (!force && timeMillis - lastTime < SAMPLE_INTERVAL_MS)) return;
            if (timeMillis == lastTime) {
                setRun(runCount - 1, timeMillis, x, y, z, yaw, crouching);
                return;
            }
        }
        if (runCount == MAX_SAMPLES) { overflow = true; return; }
        setRun(runCount++, timeMillis, x, y, z, yaw, crouching);
    }

    private void setRun(int index, int t, float x, float y, float z, float yaw, boolean crouch) {
        runTime[index] = t; runX[index] = x; runY[index] = y; runZ[index] = z;
        runYaw[index] = yaw; runCrouch[index] = crouch;
    }

    /** Only complete, faster runs replace the replay. Oversize runs retain the previous best. */
    public synchronized boolean completeRun(int durationMillis) {
        if (overflow || runCount < 2 || durationMillis <= 0
                || durationMillis > 300_000 || runTime[0] != 0
                || runTime[runCount - 1] != durationMillis
                || (hasBest() && durationMillis >= bestDuration)) return false;
        System.arraycopy(runTime, 0, bestTime, 0, runCount);
        System.arraycopy(runX, 0, bestX, 0, runCount);
        System.arraycopy(runY, 0, bestY, 0, runCount);
        System.arraycopy(runZ, 0, bestZ, 0, runCount);
        System.arraycopy(runYaw, 0, bestYaw, 0, runCount);
        System.arraycopy(runCrouch, 0, bestCrouch, 0, runCount);
        bestCount = runCount; bestDuration = durationMillis; revision++;
        return true;
    }

    /** Binary search permits independent GL/UI consumers without a shared replay cursor. */
    public synchronized boolean sample(int timeMillis, GhostPose out) {
        if (!hasBest() || timeMillis < 0 || timeMillis > bestDuration) { out.hide(); return false; }
        int lo = 0, hi = bestCount - 1;
        while (lo + 1 < hi) {
            int middle = (lo + hi) >>> 1;
            if (bestTime[middle] <= timeMillis) lo = middle;
            else hi = middle;
        }
        int a = timeMillis >= bestTime[hi] ? hi : lo;
        int b = Math.min(a + 1, bestCount - 1);
        float t = a == b ? 0 : Math.max(0f, Math.min(1f,
                (timeMillis - bestTime[a]) / (float) (bestTime[b] - bestTime[a])));
        out.x = bestX[a] + (bestX[b] - bestX[a]) * t;
        out.y = bestY[a] + (bestY[b] - bestY[a]) * t;
        out.z = bestZ[a] + (bestZ[b] - bestZ[a]) * t;
        float angle = (float) Math.IEEEremainder(bestYaw[b] - bestYaw[a], 2 * Math.PI);
        out.yaw = bestYaw[a] + angle * t;
        out.crouching = t < 0.5f ? bestCrouch[a] : bestCrouch[b];
        out.visible = true;
        return true;
    }

    public void write(DataOutput output) throws IOException {
        // Allocate on the persistence worker, then hold the render lock only for array copies.
        // The slow output stream must never hold the lock used by sample()/completeRun().
        Snapshot snapshot = new Snapshot();
        synchronized (this) {
            snapshot.duration = bestDuration; snapshot.count = bestCount;
            copy(bestTime, bestX, bestY, bestZ, bestYaw, bestCrouch, snapshot.time,
                    snapshot.x, snapshot.y, snapshot.z, snapshot.yaw, snapshot.crouch, bestCount);
        }
        output.writeInt(snapshot.duration); output.writeInt(snapshot.count);
        for (int i = 0; i < snapshot.count; i++) {
            output.writeInt(snapshot.time[i]); output.writeFloat(snapshot.x[i]); output.writeFloat(snapshot.y[i]);
            output.writeFloat(snapshot.z[i]); output.writeFloat(snapshot.yaw[i]); output.writeBoolean(snapshot.crouch[i]);
        }
    }

    /** Reads into scratch first: malformed/incomplete files never partially overwrite a best run. */
    public void read(DataInput input) throws IOException {
        int duration = input.readInt(), count = input.readInt();
        if (duration <= 0 || duration > 300_000 || count < 2 || count > MAX_SAMPLES)
            throw new IOException("Invalid ghost length");
        Snapshot snapshot = new Snapshot();
        int previous = -1;
        for (int i = 0; i < count; i++) {
            int time = input.readInt();
            float x = input.readFloat(), y = input.readFloat(), z = input.readFloat(), yaw = input.readFloat();
            boolean crouch = input.readBoolean();
            if (time <= previous || time > duration || !finite(x, y, z, yaw)
                    || x < 0 || x >= 128 || y < -8 || y > 48 || z < 0 || z >= 128)
                throw new IOException("Invalid ghost sample");
            if (i == 0 && time != 0) throw new IOException("Missing ghost start");
            snapshot.time[i] = time; snapshot.x[i] = x; snapshot.y[i] = y;
            snapshot.z[i] = z; snapshot.yaw[i] = yaw; snapshot.crouch[i] = crouch;
            previous = time;
        }
        if (previous != duration) throw new IOException("Missing ghost finish");
        synchronized (this) {
            copy(snapshot.time, snapshot.x, snapshot.y, snapshot.z, snapshot.yaw, snapshot.crouch,
                    bestTime, bestX, bestY, bestZ, bestYaw, bestCrouch, count);
            bestCount = count; bestDuration = duration; revision++;
        }
    }

    private static void copy(int[] st, float[] sx, float[] sy, float[] sz, float[] sw, boolean[] sc,
                             int[] dt, float[] dx, float[] dy, float[] dz, float[] dw, boolean[] dc, int count) {
        System.arraycopy(st, 0, dt, 0, count); System.arraycopy(sx, 0, dx, 0, count);
        System.arraycopy(sy, 0, dy, 0, count); System.arraycopy(sz, 0, dz, 0, count);
        System.arraycopy(sw, 0, dw, 0, count); System.arraycopy(sc, 0, dc, 0, count);
    }

    /** Only instantiated by load/save, never by fixed-step simulation or drawing. */
    private static final class Snapshot {
        int duration, count;
        final int[] time = new int[MAX_SAMPLES];
        final float[] x = new float[MAX_SAMPLES], y = new float[MAX_SAMPLES];
        final float[] z = new float[MAX_SAMPLES], yaw = new float[MAX_SAMPLES];
        final boolean[] crouch = new boolean[MAX_SAMPLES];
    }

    private static boolean finite(float x, float y, float z, float yaw) {
        return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z)
                && Float.isFinite(yaw) && Math.abs(yaw) <= 10_000f;
    }
}
