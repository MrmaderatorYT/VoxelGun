package com.ccs.voxelgun.game;

/** Hierarchical routing over the authored road lattice, then local A* to each waypoint. */
public final class BattlefieldRoutes {
    private static final int[] NORTH_SOUTH = {16, 160, 320, 480, 623};
    private static final int[] EAST_WEST = {96, 288, 480};
    private final NavGrid nav;

    public BattlefieldRoutes(NavGrid nav) { this.nav = nav; }

    public int waypoint(Combatant c, int destination) {
        float gx = nav.cellX(destination) + .5f, gz = nav.cellZ(destination) + .5f;
        float x = c.position.x, z = c.position.z;
        if (Math.abs(gx - x) + Math.abs(gz - z) < 70) return destination;
        int roadX = closest(NORTH_SOUTH, x), roadZ = closest(EAST_WEST, z);
        float tx, tz;
        if (Math.abs(x - roadX) > 4 && Math.abs(z - roadZ) > 6) {
            // Leave an interior district through its nearest through-road.
            if (Math.abs(x - roadX) < Math.abs(z - roadZ)) { tx = roadX; tz = z; }
            else { tx = x; tz = roadZ; }
        } else if (Math.abs(z - roadZ) <= 6 && Math.abs(gx - x) > 10) {
            // Cross the map at a flat avenue before heading north/south.
            tx = closest(NORTH_SOUTH, gx); tz = roadZ;
            if (Math.abs(tx - x) < 6) { tx = x; tz = gz; }
        } else {
            tx = roadX; tz = closest(EAST_WEST, gz);
            if (Math.abs(tz - z) < 6) { tx = gx; tz = z; }
        }
        // Bound every local query; avoid one A* across the entire 640-block map.
        float dx = tx - x, dz = tz - z;
        float distance = (float) Math.sqrt(dx * dx + dz * dz);
        float scale = distance > 64 ? 64 / distance : 1;
        int node = nav.nearestNode(x + dx * scale, c.position.y, z + dz * scale);
        return node >= 0 ? node : destination;
    }

    private static int closest(int[] roads, float coordinate) {
        int best = roads[0];
        for (int road : roads) if (Math.abs(road - coordinate) < Math.abs(best - coordinate)) best = road;
        return best;
    }
}
