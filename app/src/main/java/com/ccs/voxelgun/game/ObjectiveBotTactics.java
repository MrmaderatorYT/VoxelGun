package com.ccs.voxelgun.game;

import com.ccs.voxelgun.voxel.VoxelWorld;

/** Small, bounded terrain search for objective formations. Never reads hidden opponents. */
final class ObjectiveBotTactics {
    enum Role { CAPTURE, DEFEND, HILL_CAPTURE, HILL_SUPPORT, INTERACT, COVER, CARRY, ESCORT, INTERCEPT, RECOVER }

    private static final int MAX_NODES = 320;
    private final NavGrid nav;
    private final VoxelWorld world;
    private final int[] queue = new int[MAX_NODES];
    private final int[] neighbours = new int[8 * NavGrid.MAX_LEVELS];
    private final int[] seen;
    private final int[] finalists = new int[4];
    private final float[] finalistScores = new float[4];
    private int lastSightQueries;
    private int stamp;
    private int center = -1, revision = -1, count;

    ObjectiveBotTactics(NavGrid nav, VoxelWorld world) {
        this.nav = nav;
        this.world = world;
        seen = new int[nav.nodeCapacity()];
    }

    /**
     * Every candidate has a short walkable return route to the center. A nearby disconnected
     * roof or a one-way drop cannot become a holding slot merely because it has line of sight.
     */
    int slot(int anchor, Combatant bot, float ring, float radius, int facingNode) {
        lastSightQueries = 0;
        if (!nav.valid(anchor)) return anchor;
        collect(anchor);
        float ax = nav.cellX(anchor) + .5f, az = nav.cellZ(anchor) + .5f;
        float facing = nav.valid(facingNode)
                ? (float) Math.atan2(nav.cellZ(facingNode) - az, nav.cellX(facingNode) - ax) : 0f;
        float angle = facing + Math.floorMod(bot.id / 2, 8) * (float) Math.PI / 4f;
        float tx = ax + (float) Math.cos(angle) * ring;
        float tz = az + (float) Math.sin(angle) * ring;
        java.util.Arrays.fill(finalists, -1);
        java.util.Arrays.fill(finalistScores, Float.MAX_VALUE);
        for (int i = 0; i < count; i++) {
            int n = queue[i];
            float x = nav.cellX(n) + .5f, z = nav.cellZ(n) + .5f;
            float y = nav.nodeY(n), dx = x - ax, dz = z - az;
            if (Math.abs(y - nav.nodeY(anchor)) >= 2f || dx * dx + dz * dz >= radius * radius) continue;
            float score = (x - tx) * (x - tx) + (z - tz) * (z - tz);
            // Nearby chest-high shelter makes a useful cover/support slot, without requiring
            // knowledge of an enemy position or assuming that shelter is bulletproof.
            if (world.solid(nav.cellX(n) + 1, nav.nodeY(n) + 1, nav.cellZ(n))
                    || world.solid(nav.cellX(n) - 1, nav.nodeY(n) + 1, nav.cellZ(n))
                    || world.solid(nav.cellX(n), nav.nodeY(n) + 1, nav.cellZ(n) + 1)
                    || world.solid(nav.cellX(n), nav.nodeY(n) + 1, nav.cellZ(n) - 1)) score -= 1.5f;
            for (int slot = 0; slot < finalists.length; slot++) if (score < finalistScores[slot]) {
                for (int move = finalists.length - 1; move > slot; move--) {
                    finalists[move] = finalists[move - 1];
                    finalistScores[move] = finalistScores[move - 1];
                }
                finalists[slot] = n;
                finalistScores[slot] = score;
                break;
            }
        }
        // Geometric ranking is cheap; only four finalists may call the world's collision/sight
        // queries. Planning an entire squad must not issue hundreds of native rays per actor.
        for (int n : finalists) {
            if (n < 0) break;
            float x = nav.cellX(n) + .5f, z = nav.cellZ(n) + .5f, y = nav.nodeY(n);
            if (world.boxCollides(x, y, z, Combatant.RADIUS, Combatant.HEIGHT)) continue;
            lastSightQueries++;
            if (world.lineOfSight(ax, nav.nodeY(anchor) + 1f, az,
                    x, y + Combatant.EYE_HEIGHT, z)) return n;
        }
        return anchor;
    }

    int lastSightQueries() { return lastSightQueries; }

    private void collect(int anchor) {
        if (center == anchor && revision == world.revision()) return;
        center = anchor;
        revision = world.revision();
        if (++stamp == 0) { java.util.Arrays.fill(seen, 0); stamp = 1; }
        count = 1;
        queue[0] = anchor;
        seen[anchor] = stamp;
        for (int read = 0; read < count && count < MAX_NODES; read++) {
            int from = queue[read];
            int found = nav.neighbours(from, neighbours);
            for (int i = 0; i < found && count < MAX_NODES; i++) {
                int n = neighbours[i];
                int dx = nav.cellX(n) - nav.cellX(anchor), dz = nav.cellZ(n) - nav.cellZ(anchor);
                if (seen[n] == stamp || dx * dx + dz * dz > 64 || !nav.connected(n, from)) continue;
                seen[n] = stamp;
                queue[count++] = n;
            }
        }
    }

    /** Load-aware dispatch retains one guard per owned point once a team can spare three bots. */
    static int choosePoint(Team team, Team[] owners, boolean[] contested, int[] assigned,
                           int preferred, int teamSize) {
        int best = preferred;
        float bestScore = -Float.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            float priority = contested[i] ? 2.6f : owners[i] == team ? 1f
                    : owners[i] == Team.NONE ? 1.8f : 2.2f;
            float score = priority / (assigned[i] + 1f);
            if (assigned[i] == 0 && teamSize >= 3) score += 2.5f;
            if (i == preferred) score += .08f;
            if (score > bestScore) { bestScore = score; best = i; }
        }
        return best;
    }
}
