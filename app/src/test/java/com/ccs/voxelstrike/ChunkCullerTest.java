package com.ccs.voxelstrike;

import com.ccs.voxelstrike.math.Frustum;
import com.ccs.voxelstrike.render.ChunkCuller;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChunkCullerTest {
    @Test public void localGridMatchesFullScanAndKeepsOpaqueAndGlassOrder() {
        int nx = 40, ny = 3, nz = 36;
        ChunkCuller culler = new ChunkCuller(nx, ny, nz);
        Random random = new Random(4242);
        byte[] flags = new byte[nx * ny * nz];
        for (int i = 0; i < flags.length; i++) {
            flags[i] = (byte) random.nextInt(4);
            culler.setMeshes(i, (flags[i] & 1) != 0, (flags[i] & 2) != 0);
        }
        Frustum frustum = new Frustum();
        for (int test = 0; test < 500; test++) {
            float x = random.nextFloat() * 840 - 100;
            float y = random.nextFloat() * 100 - 25;
            float z = random.nextFloat() * 776 - 100;
            float distance = random.nextFloat() * 300;
            if (test % 5 == 0) {
                float[] clip = new float[16];
                clip[0] = .01f; clip[5] = .04f; clip[10] = .02f; clip[15] = 1f;
                clip[12] = -x * clip[0]; clip[13] = -y * clip[5]; clip[14] = -z * clip[10];
                frustum.update(clip);
            }
            culler.cull(frustum, x, y, z, distance);
            int opaque = 0, glass = 0;
            for (int i = 0; i < flags.length; i++) {
                float mx = i % nx * 16f, my = i / nx % ny * 16f, mz = i / (nx * ny) * 16f;
                float dx = mx + 8 - x, dy = my + 8 - y, dz = mz + 8 - z;
                if (flags[i] == 0 || dx * dx + dy * dy + dz * dz > distance * distance
                        || !frustum.containsBox(mx, my, mz, mx + 16, my + 16, mz + 16)) continue;
                if ((flags[i] & 1) != 0) assertEquals(i, culler.opaqueIndices()[opaque++]);
                if ((flags[i] & 2) != 0) assertEquals(i, culler.translucentIndices()[glass++]);
            }
            assertEquals(opaque, culler.opaqueCount());
            assertEquals(glass, culler.translucentCount());
        }
    }

    @Test public void editsClearOrChangeAPassAndLocalWorkIsBounded() {
        ChunkCuller culler = new ChunkCuller(40, 3, 36);
        Frustum unbounded = new Frustum();
        culler.setMeshes(0, true, true);
        culler.cull(unbounded, 8, 8, 8, 0);
        assertEquals(1, culler.opaqueCount()); assertEquals(1, culler.translucentCount());
        culler.setMeshes(0, false, true);
        culler.cull(unbounded, 8, 8, 8, 0);
        assertEquals(0, culler.opaqueCount()); assertEquals(1, culler.translucentCount());
        culler.setMeshes(0, false, false);
        culler.cull(unbounded, 8, 8, 8, 0);
        assertEquals(0, culler.translucentCount());
        culler.cull(unbounded, 320, 16, 288, 120);
        assertTrue(culler.candidateCount() < 40 * 3 * 36 / 4);
        culler.cull(unbounded, -500, -500, -500, 100);
        assertEquals(0, culler.candidateCount());
    }
}
