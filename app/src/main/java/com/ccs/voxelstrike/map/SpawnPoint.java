package com.ccs.voxelstrike.map;

/** A place a combatant can enter the match at, with the yaw it should face. */
public final class SpawnPoint {
    public final float x;
    public final float y;
    public final float z;
    public final float yaw;

    public SpawnPoint(float x, float y, float z, float yawDegrees) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = (float) Math.toRadians(yawDegrees);
    }
}
