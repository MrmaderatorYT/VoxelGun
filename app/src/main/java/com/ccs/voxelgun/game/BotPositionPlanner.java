package com.ccs.voxelgun.game;

import com.ccs.voxelgun.voxel.VoxelWorld;
import com.ccs.voxelgun.weapon.WeaponClass;
import com.ccs.voxelgun.weapon.WeaponType;

/** Geometry ranks a small set first; only finalists spend shared sight/path budgets. */
public final class BotPositionPlanner {
    private final VoxelWorld world;
    private final NavGrid nav;
    private final PathFinder finder;
    private final BotTacticalBudget budget;
    private final int[] candidates = new int[4];
    private final float[] scores = new float[4];
    private final int[] neighbours = new int[8 * NavGrid.MAX_LEVELS];
    private final PathFinder.Path scratch = new PathFinder.Path(256);
    private int coverNode = -1, peekNode = -1;

    public BotPositionPlanner(VoxelWorld world, NavGrid nav, PathFinder finder, BotTacticalBudget budget) {
        this.world = world; this.nav = nav; this.finder = finder; this.budget = budget;
    }

    public int coverNode() { return coverNode; }
    public int peekNode() { return peekNode; }

    public int firingPosition(Combatant c, WeaponType weapon, float tx, float ty, float tz,
                              int anchor, float tether, BotRouteMemory memory, int rejected) {
        rank(c, weapon, tx, tz, anchor, tether, memory, rejected, false);
        int start = nav.nearestNode(c.position.x, c.position.y, c.position.z);
        for (int n : candidates) {
            if (n < 0 || !finder.canSearch() || !budget.take()) break;
            if (!visible(n, tx, ty, tz)) continue;
            if (finder.find(start, n, scratch, memory)) return n;
        }
        return -1;
    }

    /** A hidden body position with a connected exposed neighbour: no teleporting around cover. */
    public boolean coverPair(Combatant c, float tx, float ty, float tz, int anchor, float tether,
                             BotRouteMemory memory, int rejected, int side) {
        coverNode = peekNode = -1;
        rank(c, c.weaponType(), tx, tz, anchor, tether, memory, rejected, true);
        int start = nav.nearestNode(c.position.x, c.position.y, c.position.z);
        for (int n : candidates) {
            if (n < 0 || !finder.canSearch() || !budget.take()) break;
            if (visible(n, tx, ty, tz)) continue;
            if (!finder.find(start, n, scratch, memory)) continue;
            int count = nav.neighbours(n, neighbours);
            // Alternate which shoulder is checked first after a damaging exposure.
            for (int i = 0; i < count; i++) {
                int p = neighbours[Math.floorMod(i + side * 3, count)];
                if (!usable(p) || !nav.connected(p, n)
                        || !withinAnchor(p, anchor, tether) || memory.cost(p) > 0f) continue;
                if (!budget.take()) return false;
                if (visible(p, tx, ty, tz)) { coverNode = n; peekNode = p; return true; }
            }
        }
        return false;
    }

    private void rank(Combatant c, WeaponType weapon, float tx, float tz, int anchor,
                      float tether, BotRouteMemory memory, int rejected, boolean cover) {
        java.util.Arrays.fill(candidates, -1); java.util.Arrays.fill(scores, -Float.MAX_VALUE);
        float facing = (float) Math.atan2(tz - c.position.z, tx - c.position.x);
        for (int sample = 0; sample < 24; sample++) {
            float angle = facing + (sample % 8) * (float) Math.PI / 4f;
            float radius = 2f + sample / 8 * 3f;
            int n = nav.nearestNode(c.position.x + (float) Math.cos(angle) * radius,
                    c.position.y, c.position.z + (float) Math.sin(angle) * radius);
            if (n < 0 || n == rejected || !usable(n) || !withinAnchor(n, anchor, tether)
                    || memory.cost(n) > 0f || memory.failures(n) > 0) continue;
            float x = nav.cellX(n) + .5f, z = nav.cellZ(n) + .5f;
            float travel = (float) Math.hypot(x - c.position.x, z - c.position.z);
            if (travel < 1.2f || travel > 11f || Math.abs(nav.nodeY(n) - c.position.y) > 3f) continue;
            int exits = nav.neighbours(n, neighbours);
            if (exits < 2) continue;
            float range = (float) Math.hypot(tx - x, tz - z);
            if (range < BotBrain.minimumSafeRangeFor(weapon) + 1f) continue;
            int shelter = shelterSides(n);
            if (cover && shelter == 0) continue;
            float score = cover ? shelter * 3f - travel * .7f + Math.min(30f, range) * .06f
                    : positionScore(weapon, range, shelter, exits,
                            nav.nodeY(n) - c.position.y, travel);
            for (int i = 0; i < candidates.length; i++) {
                if (candidates[i] == n) break;
                if (score > scores[i]) {
                    for (int j = candidates.length - 1; j > i; j--) {
                        candidates[j] = candidates[j - 1]; scores[j] = scores[j - 1];
                    }
                    candidates[i] = n; scores[i] = score; break;
                }
            }
        }
    }

    static float positionScore(WeaponType weapon, float range, int shelter, int exits,
                               float elevation, float travel) {
        WeaponClass type = weapon.weaponClass();
        boolean close = type == WeaponClass.SHOTGUN || type == WeaponClass.SMG;
        float ideal = BotBrain.preferredRangeFor(weapon);
        float score = -Math.abs(range - ideal) * (close ? 1.6f : .8f)
                + shelter * (close ? 3f : 2f) + Math.min(4, exits) * .35f - travel * .4f;
        if (type == WeaponClass.SNIPER) score += Math.max(-2f, Math.min(3f, elevation)) * 1.5f;
        if (type == WeaponClass.LMG && range > 14f && range < 38f) score += 3f;
        return score;
    }

    private boolean visible(int n, float x, float y, float z) {
        return world.lineOfSight(nav.cellX(n) + .5f, nav.nodeY(n) + Combatant.EYE_HEIGHT,
                nav.cellZ(n) + .5f, x, y + Combatant.EYE_HEIGHT * .85f, z);
    }

    private boolean withinAnchor(int n, int anchor, float tether) {
        if (!nav.valid(anchor)) return true;
        float dx = nav.cellX(n) - nav.cellX(anchor), dz = nav.cellZ(n) - nav.cellZ(anchor);
        return dx * dx + dz * dz <= tether * tether
                && Math.abs(nav.nodeY(n) - nav.nodeY(anchor)) < 3f;
    }

    private int shelterSides(int n) {
        float x = nav.cellX(n) + .5f, z = nav.cellZ(n) + .5f, y = nav.nodeY(n) + 1f;
        int result = 0;
        if (world.solidAt(x + 1.4f, y, z)) result++;
        if (world.solidAt(x - 1.4f, y, z)) result++;
        if (world.solidAt(x, y, z + 1.4f)) result++;
        if (world.solidAt(x, y, z - 1.4f)) result++;
        return result;
    }

    public boolean usable(int n) {
        return nav.valid(n) && world.solid(nav.cellX(n), nav.nodeY(n) - 1, nav.cellZ(n))
                && !world.boxCollides(nav.cellX(n) + .5f, nav.nodeY(n), nav.cellZ(n) + .5f,
                Combatant.RADIUS, Combatant.HEIGHT);
    }
}
