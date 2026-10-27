package com.ccs.voxelstrike;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.voxel.MeshData;
import com.ccs.voxelstrike.voxel.VoxelMesher;
import com.ccs.voxelstrike.voxel.VoxelWorld;

import org.junit.Test;

/** Covers the pure-Java meshing fallback; the native greedy mesher produces the same faces. */
public class VoxelMesherTest {
    @Test
    public void singleVoxelProducesSixFaces() {
        byte[] voxels = new byte[27];
        voxels[1 + 3 * (1 + 3 * 1)] = Materials.CONCRETE;
        MeshData mesh = VoxelMesher.meshModel(voxels, 3, 3, 3, 1f, VoxelMesher.PASS_OPAQUE);
        assertEquals(6 * 6, mesh.indexCount());
        assertEquals(6 * 4, mesh.vertexCount());
    }

    @Test
    public void enclosedVoxelsProduceNoInteriorFaces() {
        int size = 4;
        byte[] voxels = new byte[size * size * size];
        for (int i = 0; i < voxels.length; i++) {
            voxels[i] = Materials.STONE;
        }
        MeshData mesh = VoxelMesher.meshModel(voxels, size, size, size, 1f,
                VoxelMesher.PASS_OPAQUE);
        // Only the outer shell is visible: six faces of 4x4 quads.
        assertEquals(6 * 16 * 6, mesh.indexCount());
    }

    @Test
    public void translucentPassSkipsOpaqueGeometry() {
        byte[] voxels = new byte[27];
        voxels[1 + 3 * (1 + 3 * 1)] = Materials.CONCRETE;
        MeshData opaque = VoxelMesher.meshModel(voxels, 3, 3, 3, 1f, VoxelMesher.PASS_OPAQUE);
        MeshData glass = VoxelMesher.meshModel(voxels, 3, 3, 3, 1f, VoxelMesher.PASS_TRANSLUCENT);
        assertTrue(opaque.indexCount() > 0);
        assertTrue(glass.isEmpty());
    }

    @Test
    public void chunkMeshingCoversTheWorldFloor() {
        VoxelWorld world = new VoxelWorld(16, 16, 16);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                world.set(x, 0, z, Materials.CONCRETE);
            }
        }
        world.commit();
        MeshData mesh = VoxelMesher.meshChunk(world, 0, 0, 0, VoxelMesher.PASS_OPAQUE);
        assertTrue(mesh.indexCount() > 0);
        world.release();
    }

    @Test public void reusableSnapshotMatchesPaddedReferenceAtPartialWorldEdges() {
        VoxelWorld world = new VoxelWorld(19, 21, 23);
        try {
            java.util.Random random = new java.util.Random(24);
            for (int z = 0; z < world.sizeZ(); z++) for (int y = 0; y < world.sizeY(); y++)
                for (int x = 0; x < world.sizeX(); x++) {
                    int r = random.nextInt(12);
                    world.set(x, y, z, r == 0 ? Materials.GLASS : r < 5 ? Materials.CONCRETE : Materials.AIR);
                }
            world.commit();
            VoxelMesher.ChunkSnapshot snapshot = new VoxelMesher.ChunkSnapshot();
            for (int cz = 0; cz < 2; cz++) for (int cy = 0; cy < 2; cy++) for (int cx = 0; cx < 2; cx++) {
                snapshot.capture(world, cx, cy, cz);
                // Independently copy the neighbourhood into a model with an air border. The
                // chunk reference below emits only its core cells, exactly like the old code.
                byte[] coreAndBorder = new byte[18 * 18 * 18];
                for (int z = 0; z < 18; z++) for (int y = 0; y < 18; y++) for (int x = 0; x < 18; x++)
                    coreAndBorder[x + 18 * (y + 18 * z)] = world.get(cx * 16 + x - 1,
                            cy * 16 + y - 1, cz * 16 + z - 1);
                for (int pass = 0; pass < 2; pass++) {
                    MeshData optimized = VoxelMesher.meshChunk(snapshot, pass);
                    assertFaceReference(optimized, coreAndBorder, pass);
                }
            }
        } finally { world.release(); }
    }

    private void assertFaceReference(MeshData mesh, byte[] padded, int pass) {
        int faces = 0;
        int[] offsets = {1, -1, 18, -18, 18 * 18, -18 * 18};
        for (int z = 1; z <= 16; z++) for (int y = 1; y <= 16; y++) for (int x = 1; x <= 16; x++) {
            int index = x + 18 * (y + 18 * z), material = padded[index] & 255;
            boolean wanted = pass == 0 ? Materials.opaque(material) : Materials.translucent(material);
            if (!wanted) continue;
            for (int offset : offsets) {
                int neighbour = padded[index + offset] & 255;
                boolean hidden = pass == 0 ? Materials.opaque(neighbour)
                        : Materials.opaque(neighbour) || neighbour == material;
                if (!hidden) faces++;
            }
        }
        assertEquals(faces * 6, mesh.indexCount());
        assertEquals(faces * 4, mesh.vertexCount());
        // Every emitted vertex must still lie in this chunk's local coordinates.
        for (int i = 0; i < mesh.vertexCount(); i++) for (int axis = 0; axis < 3; axis++) {
            float v = mesh.vertices().get(i * MeshData.FLOATS_PER_VERTEX + axis);
            assertTrue(v >= 0 && v <= 16);
        }
    }

    @Test public void snapshotReuseClearsBordersPassesAndTracksEdits() {
        VoxelWorld world = new VoxelWorld(32, 17, 32);
        try {
            world.set(15, 4, 15, Materials.CRATE);
            world.set(16, 4, 15, Materials.GLASS);
            world.commit();
            VoxelMesher.ChunkSnapshot snapshot = new VoxelMesher.ChunkSnapshot();
            snapshot.capture(world, 0, 0, 0);
            assertTrue(snapshot.hasOpaque());
            assertTrue("native boundary slices also consider halo glass", snapshot.hasTranslucent());
            assertTrue(VoxelMesher.meshChunk(snapshot, 0).indexCount() > 0);
            assertSame(MeshData.empty(), VoxelMesher.meshChunk(snapshot, 1));
            assertTrue(world.removeDestructible(15, 4, 15));
            snapshot.capture(world, 0, 0, 0);
            assertTrue("halo passes are conservative even when the Java fallback emits no core faces",
                    snapshot.hasTranslucent());
            assertEquals(world.chunkVersion(0), snapshot.version());
            assertSame(MeshData.empty(), VoxelMesher.meshChunk(snapshot, 0));
            snapshot.capture(world, 1, 0, 0);
            assertTrue(snapshot.hasTranslucent());
            snapshot.capture(world, 1, 1, 1);
            assertFalse(snapshot.hasOpaque()); assertFalse(snapshot.hasTranslucent());
            assertSame(MeshData.empty(), VoxelMesher.meshChunk(snapshot, 1));
        } finally { world.release(); }
    }
}
