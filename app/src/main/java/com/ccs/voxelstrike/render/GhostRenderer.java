package com.ccs.voxelstrike.render;

import com.ccs.voxelstrike.game.GhostPose;
import com.ccs.voxelstrike.math.Frustum;
import com.ccs.voxelstrike.math.Mat4;
import com.ccs.voxelstrike.model.VoxelModel;
import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.voxel.VoxelMesher;

/** One reusable, non-colliding translucent procedural runner, only created in movement practice. */
final class GhostRenderer {
    private GpuMesh mesh;
    private Shader shader;
    private final float[] model = new float[16];
    void create(ShaderLibrary library) {
        release();
        shader = library.create("movement_ghost", "ghost.vert", "ghost.frag");
        int material = Materials.NEON_CYAN;
        VoxelModel runner = new VoxelModel(20, 32, 8)
                .box(6, 0, 2, 8, 11, 5, material).box(11, 0, 2, 13, 11, 5, material)
                .box(6, 12, 2, 13, 23, 5, material)
                .box(2, 13, 2, 5, 23, 5, material).box(14, 13, 2, 17, 23, 5, material)
                .box(6, 24, 0, 13, 31, 7, material);
        mesh = new GpuMesh(VoxelMesher.meshModel(runner.voxels, runner.sizeX, runner.sizeY,
                runner.sizeZ, 1f, VoxelMesher.PASS_OPAQUE));
    }
    void draw(GhostPose pose, float[] viewProjection, Frustum frustum) {
        if (mesh == null || !pose.visible || !frustum.containsBox(pose.x - .6f, pose.y,
                pose.z - .6f, pose.x + .6f, pose.y + 1.8f, pose.z + .6f)) return;
        Mat4.model(model, pose.x, pose.y, pose.z, pose.yaw, .056f);
        // Baked runner is centered around voxel (10,0,4); crouch scales height, not feet.
        if (pose.crouching) { model[4] *= .7f; model[5] *= .7f; model[6] *= .7f; }
        Mat4.translate(model, -10f, 0f, -4f);
        shader.use();
        shader.setMatrix("uViewProjection", viewProjection);
        shader.setMatrix("uModel", model);
        mesh.draw();
    }
    void release() { if (mesh != null) { mesh.release(); mesh = null; } shader = null; }
}
