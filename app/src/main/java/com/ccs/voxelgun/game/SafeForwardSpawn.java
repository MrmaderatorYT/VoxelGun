package com.ccs.voxelgun.game;

import com.ccs.voxelgun.voxel.VoxelWorld;

import java.util.List;

/**
 * Conservative, bounded forward deployment. Candidates are cached terrain nodes; collision and
 * live enemy exposure are rechecked immediately before use. Exhausting the visibility budget
 * falls back to home deployment rather than accepting an unchecked spawn.
 */
public final class SafeForwardSpawn {
    private static final int SLOTS = 8;
    private static final int MAX_CANDIDATES = 24;
    private static final int MAX_SIGHT_QUERIES = 32;
    private static final float ENEMY_EXCLUSION_SQUARED = 24f * 24f;
    private static final float MIN_SIGHT_RADIUS = 110f;
    private final NavGrid nav;
    private final VoxelWorld world;
    private final List<Combatant> actors;
    private final SquadCoordinator.ObjectiveField objectives;
    private final int[] cachedSlots;
    private final boolean[] consideredPoints;
    private final float[] deployment = new float[4];
    private int candidateQueries, sightQueries;

    public SafeForwardSpawn(NavGrid nav, VoxelWorld world, List<Combatant> actors,
                            SquadCoordinator.ObjectiveField objectives) {
        this.nav = nav; this.world = world; this.actors = actors; this.objectives = objectives;
        int count = objectives == null ? 0 : objectives.count();
        cachedSlots = new int[count * SLOTS];
        consideredPoints = new boolean[count];
        for (int point = 0; point < count; point++) {
            int center = objectives.node(point);
            if (!nav.valid(center)) {
                java.util.Arrays.fill(cachedSlots, point * SLOTS, (point + 1) * SLOTS, -1);
                continue;
            }
            float x = nav.cellX(center) + .5f, z = nav.cellZ(center) + .5f;
            for (int slot = 0; slot < SLOTS; slot++) {
                float angle = slot * (float) Math.PI / 4;
                int n = nav.nearestNode(x + (float) Math.cos(angle) * 8f, nav.nodeY(center),
                        z + (float) Math.sin(angle) * 8f);
                float dx = nav.cellX(n) + .5f - x, dz = nav.cellZ(n) + .5f - z;
                cachedSlots[point * SLOTS + slot] = nav.valid(n) && dx * dx + dz * dz < 121f
                        ? n : -1;
            }
        }
    }

    /** Shared scratch coordinates; the simulation caller must consume them immediately. */
    public float[] deploymentFor(Combatant c, int preferredGoal) {
        candidateQueries = sightQueries = 0;
        java.util.Arrays.fill(consideredPoints, false);
        // A controlled sector is the first choice. Try nearby sectors before distant ones.
        if (objectives != null) for (int attempt = 0; attempt < objectives.count(); attempt++) {
            int point = nearestOwned(c, preferredGoal);
            if (point < 0) break;
            consideredPoints[point] = true;
            for (int slot = 0; slot < SLOTS; slot++) {
                int node = cachedSlots[point * SLOTS + Math.floorMod(slot + c.id, SLOTS)];
                if (!nav.valid(node)) continue;
                float x = nav.cellX(node) + .5f, z = nav.cellZ(node) + .5f;
                int y = nav.nodeY(node);
                if (safe(c, x, y, z)) return deploy(x, resolvedY(x, y, z), z,
                        c.team == Team.BLUE ? (float) Math.PI * .5f : -(float) Math.PI * .5f);
                if (candidateQueries >= MAX_CANDIDATES || sightQueries >= MAX_SIGHT_QUERIES) return null;
            }
        }
        // With no safe sector, a quiet living squad mate may be an anchor, never one under fire.
        for (int i = 0; i < actors.size(); i++) {
            Combatant mate = actors.get(i);
            if (mate == c || !mate.alive || !SquadCoordinator.sameSquad(c, mate)
                    || mate.lastDamageTime < 8f || mate.firing || Math.abs(mate.velocity.y) > .2f) continue;
            int anchorY = resolvedY(mate.position.x, mate.position.y, mate.position.z);
            if (anchorY < 0 || Math.abs(anchorY - mate.position.y) > .2f) continue;
            if (objectives == null) {
                float advance = c.team == Team.BLUE ? mate.position.x : nav.width() - mate.position.x;
                if (advance < nav.width() * .20f) continue;
            }
            for (int slot = 0; slot < SLOTS; slot++) {
                float angle = Math.floorMod(slot + c.id, SLOTS) * (float) Math.PI / 4;
                float x = mate.position.x + (float) Math.cos(angle) * 4f;
                float z = mate.position.z + (float) Math.sin(angle) * 4f;
                if (safe(c, x, mate.position.y, z))
                    return deploy(x, resolvedY(x, mate.position.y, z), z, mate.yaw);
                if (candidateQueries >= MAX_CANDIDATES || sightQueries >= MAX_SIGHT_QUERIES) return null;
            }
        }
        return null;
    }

    public int candidateQueries() { return candidateQueries; }
    public int sightQueries() { return sightQueries; }

    private int nearestOwned(Combatant c, int preferredGoal) {
        int best = -1;
        float bestScore = Float.MAX_VALUE;
        float gx = nav.valid(preferredGoal) ? nav.cellX(preferredGoal) : c.position.x;
        float gz = nav.valid(preferredGoal) ? nav.cellZ(preferredGoal) : c.position.z;
        for (int i = 0; i < objectives.count(); i++) {
            if (consideredPoints[i] || objectives.owner(i) != c.team || objectives.contested(i)) continue;
            int node = objectives.node(i);
            if (!nav.valid(node)) continue;
            float dx = nav.cellX(node) - gx, dz = nav.cellZ(node) - gz;
            float score = dx * dx + dz * dz;
            if (score < bestScore) { best = i; bestScore = score; }
        }
        return best;
    }

    private int resolvedY(float x, float y, float z) {
        return world.standableLevel(VoxelWorld.floor(x), VoxelWorld.floor(z), (int) y, 2);
    }

    private boolean safe(Combatant c, float x, float preferredY, float z) {
        if (++candidateQueries > MAX_CANDIDATES) return false;
        if (!world.inside(VoxelWorld.floor(x), (int) preferredY, VoxelWorld.floor(z))) return false;
        int y = resolvedY(x, preferredY, z);
        if (y < 0 || world.boxCollides(x, y, z, Combatant.RADIUS, Combatant.HEIGHT)) return false;
        // Reject close enemies/body overlap before any expensive visibility queries.
        for (int i = 0; i < actors.size(); i++) {
            Combatant other = actors.get(i);
            if (other == c || !other.alive) continue;
            float dx = other.position.x - x, dz = other.position.z - z;
            float d = dx * dx + dz * dz;
            if (d < 9f && Math.abs(other.position.y - y) < 3f) return false;
            if (other.team != c.team && d < ENEMY_EXCLUSION_SQUARED) return false;
        }
        for (int i = 0; i < actors.size(); i++) {
            Combatant other = actors.get(i);
            if (other == c || !other.alive || other.team == c.team) continue;
            float dx = other.position.x - x, dz = other.position.z - z;
            float sightRadius = MIN_SIGHT_RADIUS;
            // A sniper holding a knife still has a long gun available after deployment.
            for (int slot = 0; slot < other.weapons.length; slot++)
                if (other.weapons[slot] != null)
                    sightRadius = Math.max(sightRadius, other.weapons[slot].type().range());
            if (dx * dx + dz * dz >= sightRadius * sightRadius) continue;
            if (sightQueries >= MAX_SIGHT_QUERIES) return false;
            sightQueries++;
            if (world.lineOfSight(x, y + Combatant.EYE_HEIGHT, z,
                    other.eyeX(), other.eyeY(), other.eyeZ())) return false;
        }
        return true;
    }

    private float[] deploy(float x, float y, float z, float yaw) {
        deployment[0] = x; deployment[1] = y; deployment[2] = z; deployment[3] = yaw;
        return deployment;
    }
}
