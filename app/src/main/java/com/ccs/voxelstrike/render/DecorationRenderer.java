package com.ccs.voxelstrike.render;

import com.ccs.voxelstrike.map.scenery.DecorationBatch;
import com.ccs.voxelstrike.map.scenery.DecorationMesher;
import com.ccs.voxelstrike.map.scenery.MapScenery;
import com.ccs.voxelstrike.math.Frustum;
import com.ccs.voxelstrike.math.Mat4;

/** Static render-only scenery. G-buffer lighting/TAA, spatial batching and zero frame allocations. */
public final class DecorationRenderer {
    private final DecorationBatch[] batches;
    private final GpuMesh[] meshes;
    private final float[] identity = Mat4.create();
    public DecorationRenderer(MapScenery scenery) {
        batches = DecorationMesher.build(scenery);
        meshes = new GpuMesh[batches.length];
        Mat4.identity(identity);
        for (int i = 0; i < batches.length; i++) meshes[i] = new GpuMesh(batches[i].mesh);
    }
    public void draw(Shader shader, Frustum frustum, float x, float y, float z, float distance) {
        shader.setMatrix("uModel", identity);
        shader.setMatrix("uPreviousModel", identity);
        shader.set("uCharacterTeam", 0f); shader.set("uViewModel", 0f);
        float limit = Math.min(distance, 160f); // tiny lettering/flowers do not need battlefield-scale LOD
        for (int i = 0; i < batches.length; i++) {
            DecorationBatch b = batches[i];
            float dx = Math.max(b.minX - x, Math.max(0, x - b.maxX));
            float dy = Math.max(b.minY - y, Math.max(0, y - b.maxY));
            float dz = Math.max(b.minZ - z, Math.max(0, z - b.maxZ));
            if (dx * dx + dy * dy + dz * dz > limit * limit) continue;
            if (frustum.containsBox(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ)) meshes[i].draw();
        }
    }
    public void release() { for (GpuMesh mesh : meshes) mesh.release(); }
}
