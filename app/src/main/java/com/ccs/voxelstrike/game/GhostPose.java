package com.ccs.voxelstrike.game;

/** Reusable output from a best-run replay. Ghosts are render-only, never collision bodies. */
public final class GhostPose {
    public boolean visible;
    public float x, y, z, yaw;
    public boolean crouching;

    public void hide() { visible = false; }
}
