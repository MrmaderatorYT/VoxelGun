package com.ccs.voxelstrike;

import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.ccs.voxelstrike.map.Maps;
import com.ccs.voxelstrike.math.Frustum;
import com.ccs.voxelstrike.render.ChunkCuller;
import com.ccs.voxelstrike.voxel.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Deterministic native meshing workload; timings are evidence, not flaky pass/fail thresholds. */
@RunWith(AndroidJUnit4.class)
public class PerformanceInstrumentedTest {
    @Test public void battlefieldMeshingWorkload() {
        assertTrue(NativeBridge.available());
        VoxelWorld world = Maps.create(Maps.indexOf("battlefield_alps")).build(4242L);
        try {
            int count = world.chunksX() * world.chunksY() * world.chunksZ();
            // Fixed sample across all heights and map districts. Warm JNI/JIT first.
            runMeshes(world, count, 16, false);
            runMeshes(world, count, 16, true);
            long[] legacy = new long[5], optimized = new long[5];
            long indices = 0;
            for (int run = 0; run < legacy.length; run++) {
                // Alternate first runner so one path does not always benefit from a warm CPU.
                for (int turn = 0; turn < 2; turn++) {
                    boolean fast = ((run + turn) & 1) == 0;
                    long start = System.nanoTime();
                    long result = runMeshes(world, count, 256, fast);
                    (fast ? optimized : legacy)[run] = System.nanoTime() - start;
                    if (indices != 0) assertEquals(indices, result);
                    indices = result;
                }
            }
            java.util.Arrays.sort(legacy); java.util.Arrays.sort(optimized);
            assertTrue(indices > 0);
            assertEquals(42984, indices); // Baseline captured on this deterministic map/seed.
            Log.i("VoxelPerf", "alps_meshing sample=256 passes=2 legacy_ms="
                    + legacy[2] / 1_000_000.0 + " optimized_ms=" + optimized[2] / 1_000_000.0
                    + " indices=" + indices);
            VoxelMesher.ChunkSnapshot snapshot = new VoxelMesher.ChunkSnapshot();
            for (int i = 0; i < 256; i++) {
                int index = (i * 1543 + 37) % count;
                int x = index % world.chunksX(), y = index / world.chunksX() % world.chunksY();
                int z = index / (world.chunksX() * world.chunksY());
                snapshot.capture(world, x, y, z);
                for (int pass = 0; pass < 2; pass++) {
                    MeshData expected = legacyMesh(world, x, y, z, pass);
                    MeshData actual = VoxelMesher.meshChunk(snapshot, pass);
                    assertEquals(expected.vertexCount(), actual.vertexCount());
                    assertEquals(expected.indexCount(), actual.indexCount());
                    // Include AO, normals and material IDs, not just triangle count.
                    for (int f = 0; f < expected.vertexCount() * MeshData.FLOATS_PER_VERTEX; f++)
                        assertEquals(expected.vertices().get(f), actual.vertices().get(f), 0f);
                    for (int n = 0; n < expected.indexCount(); n++)
                        assertEquals(expected.indices().get(n), actual.indices().get(n));
                }
            }
        } finally { world.release(); }
    }

    private long runMeshes(VoxelWorld world, int count, int samples, boolean optimized) {
        long indices = 0;
        VoxelMesher.ChunkSnapshot snapshot = new VoxelMesher.ChunkSnapshot();
        for (int i = 0; i < samples; i++) {
            int index = (i * 1543 + 37) % count;
            int x = index % world.chunksX();
            int y = index / world.chunksX() % world.chunksY();
            int z = index / (world.chunksX() * world.chunksY());
            if (optimized) snapshot.capture(world, x, y, z);
            for (int pass = 0; pass < 2; pass++) indices += (optimized
                    ? VoxelMesher.meshChunk(snapshot, pass) : legacyMesh(world, x, y, z, pass)).indexCount();
        }
        return indices;
    }

    /** The pre-optimization path is retained only in the benchmark as an independent reference. */
    private MeshData legacyMesh(VoxelWorld world, int cx, int cy, int cz, int pass) {
        byte[] padded = new byte[18 * 18 * 18];
        synchronized (world) {
            for (int z = -1; z <= 16; z++) for (int y = -1; y <= 16; y++) for (int x = -1; x <= 16; x++)
                padded[x + 1 + 18 * (y + 1 + 18 * (z + 1))] = world.get(cx * 16 + x, cy * 16 + y, cz * 16 + z);
        }
        return MeshData.fromPacked(NativeBridge.greedyMesh(padded, 16, 16, 16, 1f, pass));
    }

    @Test public void battlefieldCullingWorkload() {
        ChunkCuller culler = new ChunkCuller(40, 3, 36);
        byte[] flags = new byte[4320];
        for (int i = 0; i < flags.length; i++) {
            flags[i] = (byte) ((i % 5 != 0 ? 1 : 0) | (i % 7 == 0 ? 2 : 0));
            culler.setMeshes(i, (flags[i] & 1) != 0, (flags[i] & 2) != 0);
        }
        Frustum frustum = new Frustum();
        long[] legacy = new long[5], optimized = new long[5];
        int[] expected = new int[4320];
        int[] glass = new int[4320];
        for (int run = -1; run < 5; run++) {
            for (int turn = 0; turn < 2; turn++) {
                boolean fast = ((run + turn) & 1) == 0;
                long start = System.nanoTime();
                for (int frame = 0; frame < 2000; frame++) {
                    float x = 160 + frame % 320, z = 130 + frame % 300;
                    if (fast) culler.cull(frustum, x, 16, z, 240);
                    else fullScan(frustum, x, z, flags, expected, glass);
                }
                long nanos = System.nanoTime() - start;
                if (run >= 0) (fast ? optimized : legacy)[run] = nanos;
            }
        }
        culler.cull(frustum, 320, 16, 288, 240);
        int count = fullScan(frustum, 320, 288, flags, expected, glass);
        assertEquals(count, culler.opaqueCount());
        for (int i = 0; i < count; i++) assertEquals(expected[i], culler.opaqueIndices()[i]);
        java.util.Arrays.sort(legacy); java.util.Arrays.sort(optimized);
        Log.i("VoxelPerf", "battlefield_cull frames=2000 legacy_ms=" + legacy[2] / 1_000_000.0
                + " optimized_ms=" + optimized[2] / 1_000_000.0 + " candidates="
                + culler.candidateCount() + "/4320 opaque=" + count);
    }

    private int fullScan(Frustum frustum, float x, float z, byte[] flags, int[] out, int[] glass) {
        int count = 0, translucent = 0;
        for (int i = 0; i < 4320; i++) {
            if (flags[i] == 0) continue;
            float mx = i % 40 * 16f, my = i / 40 % 3 * 16f, mz = i / 120 * 16f;
            float dx = mx + 8 - x, dy = my + 8 - 16, dz = mz + 8 - z;
            if (dx * dx + dy * dy + dz * dz > 240f * 240f
                    || !frustum.containsBox(mx, my, mz, mx + 16, my + 16, mz + 16)) continue;
            if ((flags[i] & 1) != 0) out[count++] = i;
            if ((flags[i] & 2) != 0) glass[translucent++] = i;
        }
        return count;
    }
}
