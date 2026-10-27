package com.ccs.voxelstrike.game;

import org.junit.Test;
import java.io.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class MovementGhostTest {
    private MovementGhost replay(int duration) {
        MovementGhost ghost = new MovementGhost();
        ghost.record(0, 14, 3, 32, (float) Math.toRadians(179), false, true);
        ghost.record(duration, 114, 3.5f, 32, (float) Math.toRadians(-179), true, true);
        assertTrue(ghost.completeRun(duration));
        return ghost;
    }

    @Test public void poseInterpolatesPositionAndShortestYawWithoutCreatingBodies() {
        MovementGhost ghost = replay(1000);
        GhostPose out = new GhostPose();
        assertTrue(ghost.sample(500, out));
        assertEquals(64, out.x, 0.001f);
        assertEquals(3.25f, out.y, 0.001f);
        assertEquals(Math.PI, Math.abs(out.yaw), 0.001f);
        assertTrue(out.crouching);
        assertFalse(ghost.sample(1001, out));
        assertFalse(out.visible);
        assertFalse(ghost.sample(-1, out));
    }

    @Test public void incompleteSlowerOrOversizeRunNeverReplacesBest() {
        MovementGhost ghost = replay(1000);
        int revision = ghost.bestRevision();
        ghost.resetRun();
        ghost.record(0, 14, 3, 32, 0, false, true);
        assertFalse(ghost.completeRun(500));
        ghost.record(2000, 114, 3, 32, 0, false, true);
        assertFalse(ghost.completeRun(2000));
        ghost.resetRun();
        for (int i = 0; i <= MovementGhost.MAX_SAMPLES; i++)
            ghost.record(i, 14, 3, 32, 0, false, true);
        assertFalse(ghost.completeRun(800));
        assertEquals(revision, ghost.bestRevision());
        assertEquals(1000, ghost.bestDurationMillis());
    }

    @Test public void fasterCompleteRunReplacesBestAndRecordingRateIsBounded() {
        MovementGhost ghost = replay(1000);
        int revision = ghost.bestRevision();
        ghost.resetRun();
        for (int i = 0; i <= 800; i++) ghost.record(i, 14 + i / 8f, 3, 32, 0, false, false);
        assertTrue(ghost.completeRun(800));
        assertEquals(revision + 1, ghost.bestRevision());
        assertEquals(17, ghost.sampleCount());
    }

    @Test public void codecRoundTripsEveryPoseAndDoesNotClearCurrentRun() throws Exception {
        MovementGhost source = replay(1000);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        source.write(new DataOutputStream(bytes));
        MovementGhost loaded = new MovementGhost();
        loaded.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        GhostPose a = new GhostPose(), b = new GhostPose();
        for (int t = 0; t <= 1000; t += 17) {
            assertTrue(source.sample(t, a)); assertTrue(loaded.sample(t, b));
            assertEquals(a.x, b.x, 0f); assertEquals(a.yaw, b.yaw, 0f);
            assertEquals(a.crouching, b.crouching);
        }
    }

    @Test public void corruptFileRetainsPreviouslyLoadedBest() throws Exception {
        MovementGhost ghost = replay(1000);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(500); output.writeInt(2);
        output.writeInt(0); output.writeFloat(14); output.writeFloat(3);
        output.writeFloat(32); output.writeFloat(0); output.writeBoolean(false);
        output.writeInt(500); output.writeFloat(Float.NaN); output.writeFloat(3);
        output.writeFloat(32); output.writeFloat(0); output.writeBoolean(false);
        try {
            ghost.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
            fail("corrupt pose accepted");
        } catch (IOException expected) { /* expected */ }
        assertEquals(1000, ghost.bestDurationMillis());
        assertEquals(2, ghost.sampleCount());
    }

    @Test public void hugeCountOrYawAndMissingFinishAreRejected() throws Exception {
        for (int count : new int[]{-1, 1, MovementGhost.MAX_SAMPLES + 1}) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(100); output.writeInt(count);
            try {
                new MovementGhost().read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
                fail("invalid count accepted");
            } catch (IOException expected) { /* expected */ }
        }
        MovementGhost ghost = new MovementGhost();
        ghost.record(0, 14, 3, 32, Float.MAX_VALUE, false, true);
        ghost.record(100, 114, 3, 32, 0, false, true);
        assertFalse(ghost.completeRun(100));
    }

    @Test public void slowPersistenceDoesNotHoldReplayLock() throws Exception {
        MovementGhost ghost = replay(1000);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> save = workers.submit(() -> {
                try {
                    ghost.write(new DataOutputStream(new OutputStream() {
                        @Override public void write(int value) throws IOException {
                            entered.countDown();
                            try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("timeout"); }
                            catch (InterruptedException e) { throw new IOException(e); }
                        }
                    }));
                } catch (IOException e) { throw new RuntimeException(e); }
            });
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            Future<Boolean> draw = workers.submit(() -> ghost.sample(500, new GhostPose()));
            assertTrue("disk I/O blocks the render replay", draw.get(1, TimeUnit.SECONDS));
            release.countDown(); save.get(2, TimeUnit.SECONDS);
        } finally { release.countDown(); workers.shutdownNow(); }
    }
}
