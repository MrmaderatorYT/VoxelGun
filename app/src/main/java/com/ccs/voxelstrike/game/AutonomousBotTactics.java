package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.voxel.VoxelWorld;

import java.util.Arrays;
import java.util.List;

/**
 * Local, voluntary cooperation. Fighters keep their own objective; a cautious or injured bot may
 * support a useful advancing ally, never an assigned human commander. Arrays are allocated once,
 * decisions run on the squad planning cadence, and perception has a global bounded ray budget.
 */
final class AutonomousBotTactics {
    private static final int MAX_FOLLOWERS = 2;
    private static final int MAX_SIGHT_QUERIES = 24;
    private static final float PICK_RADIUS_SQUARED = 32f * 32f;
    private static final float KEEP_RADIUS_SQUARED = 40f * 40f;
    private final NavGrid nav;
    private final VoxelWorld world;
    private final List<Combatant> actors;
    private final Combatant[] byId;
    private final int[] baseGoals, localGoals, leaders, followers, objectives, pointmen, rejected;
    private final int[] supportSlots, occupiedSlots;
    private final int[] perchCenters, perchGoals, perchRevisions;
    private final int[] perchRetryPasses, perchAttempts;
    private final boolean[] perchVisible;
    private final float[] perchFronts;
    private final float[] pointmanScores;
    private final boolean[] maySupport, roots;
    private final SquadCoordinator.MemberTactic[] tactics, baseline;
    private int sightQueries, planningStart, searchStamp;

    AutonomousBotTactics(NavGrid nav, VoxelWorld world, List<Combatant> actors, int capacity) {
        this.nav = nav; this.world = world; this.actors = actors;
        byId = new Combatant[capacity];
        baseGoals = new int[capacity]; localGoals = new int[capacity]; leaders = new int[capacity];
        followers = new int[capacity]; objectives = new int[capacity]; rejected = new int[capacity];
        supportSlots = new int[capacity]; occupiedSlots = new int[capacity];
        perchCenters = new int[capacity]; perchGoals = new int[capacity];
        perchRevisions = new int[capacity]; perchFronts = new float[capacity];
        perchRetryPasses = new int[capacity]; perchAttempts = new int[capacity];
        perchVisible = new boolean[capacity];
        Arrays.fill(perchCenters, -1); Arrays.fill(perchGoals, -1);
        pointmen = new int[Math.max(2, (capacity / 10 + 1) * 2)];
        pointmanScores = new float[pointmen.length];
        maySupport = new boolean[capacity]; roots = new boolean[capacity];
        tactics = new SquadCoordinator.MemberTactic[capacity];
        baseline = new SquadCoordinator.MemberTactic[capacity];
        Arrays.fill(baseGoals, -1); Arrays.fill(localGoals, -1); Arrays.fill(leaders, -1);
        Arrays.fill(supportSlots, -1);
        Arrays.fill(tactics, SquadCoordinator.MemberTactic.ADVANCE);
        Arrays.fill(baseline, SquadCoordinator.MemberTactic.ADVANCE);
        for (int i = 0; i < actors.size(); i++) {
            Combatant c = actors.get(i);
            if (known(c)) byId[c.id] = c;
        }
    }

    void plan(SquadCoordinator strategy) {
        sightQueries = 0;
        Arrays.fill(followers, 0); Arrays.fill(occupiedSlots, 0); Arrays.fill(pointmen, -1);
        Arrays.fill(pointmanScores, -Float.MAX_VALUE);
        // A group composed entirely of cautious riflemen still needs someone to advance.
        // Useful movement outranks identity. An idle human never occupies this initiative slot;
        // humans remain ordinary potential support anchors, subject to the same motion checks.
        for (int i = 0; i < actors.size(); i++) {
            Combatant c = actors.get(i);
            if (!known(c)) continue;
            baseGoals[c.id] = strategy.strategicDestinationFor(c);
            objectives[c.id] = strategy.objectiveFor(c);
            if (!c.bot || !c.alive || c.team == Team.NONE || c.health < 55f
                    || strategy.regrouping(c) || BotCombatStyle.forCombatant(c)
                    == BotCombatStyle.SNIPER) continue;
            int squad = pointmanIndex(c);
            BotCombatStyle style = BotCombatStyle.forCombatant(c);
            float score = (movingToward(c, baseGoals[c.id]) ? 100f : 0f)
                    + (style == BotCombatStyle.RUSHER ? 12f : 0f)
                    + c.health * .1f - (float) Math.sqrt(remainingSquared(c, baseGoals[c.id])) * .04f;
            if (score > pointmanScores[squad] || score == pointmanScores[squad]
                    && (pointmen[squad] < 0 || c.id < pointmen[squad])) {
                pointmen[squad] = c.id;
                pointmanScores[squad] = score;
            }
        }
        for (int offset = 0; offset < actors.size(); offset++) {
            Combatant c = actors.get((planningStart + offset) % actors.size());
            if (!known(c)) continue;
            int id = c.id;
            localGoals[id] = baseGoals[id];
            SquadCoordinator.Role role = strategy.roleFor(c);
            SquadCoordinator.MemberTactic choice = role == SquadCoordinator.Role.REGROUP
                    ? SquadCoordinator.MemberTactic.REGROUP : role == SquadCoordinator.Role.FLANK
                    ? SquadCoordinator.MemberTactic.FLANK : role == SquadCoordinator.Role.DEFEND
                    || objectives[id] >= 0 && remainingSquared(c, baseGoals[id]) < 14f * 14f
                    ? SquadCoordinator.MemberTactic.HOLD : SquadCoordinator.MemberTactic.ADVANCE;
            BotCombatStyle style = BotCombatStyle.forCombatant(c);
            // Aggressive individuals may choose a final shoulder approach even when the larger
            // squad advances directly. Once near the sector they return to a real capture slot.
            if (c.bot && c.alive && style == BotCombatStyle.RUSHER && c.health >= 70f
                    && role == SquadCoordinator.Role.ATTACK && c.id / 2 % 3 == 2
                    && remainingSquared(c, baseGoals[id]) > 36f * 36f) {
                int flank = flankGoal(c, baseGoals[id]);
                if (remainingSquared(c, flank) > 10f * 10f) {
                    localGoals[id] = flank;
                    choice = SquadCoordinator.MemberTactic.FLANK;
                }
            }
            // Marksmen advance behind infantry; their pistol never turns them into pointmen.
            if (c.bot && c.alive && style == BotCombatStyle.SNIPER
                    && role != SquadCoordinator.Role.REGROUP) {
                localGoals[id] = standOffGoal(c, baseGoals[id]);
                choice = SquadCoordinator.MemberTactic.OVERWATCH;
            }
            baseline[id] = tactics[id] = choice;
            boolean usefulIndependent = c.health >= 55f && (style != BotCombatStyle.CAUTIOUS
                    || pointmen[pointmanIndex(c)] == id);
            roots[id] = c.alive && c.team != Team.NONE && style != BotCombatStyle.SNIPER
                    && (!c.bot || usefulIndependent)
                    && role != SquadCoordinator.Role.REGROUP;
            maySupport[id] = c.bot && c.alive && c.team != Team.NONE && !roots[id]
                    && style != BotCombatStyle.SNIPER
                    && role != SquadCoordinator.Role.REGROUP && nav.valid(baseGoals[id])
                    && remainingSquared(c, baseGoals[id]) >= 14f * 14f;
        }
        int count = actors.size();
        // Rotate the perception budget so late actor ids and the red team do not starve.
        for (int offset = 0; offset < count; offset++) {
            Combatant c = actors.get((planningStart + offset) % count);
            if (!known(c)) continue;
            int previous = leaders[c.id];
            leaders[c.id] = -1;
            if (!maySupport[c.id]) continue;
            int best = chooseLeader(c, previous);
            if (best < 0) continue;
            int slot = previous == best && supportSlots[c.id] >= 0
                    ? supportSlots[c.id] : c.id / 2 & 1;
            if ((occupiedSlots[best] & 1 << slot) != 0) slot ^= 1;
            int goal = supportingGoal(byId[best], slot);
            if (!nav.valid(goal)) continue;
            leaders[c.id] = best;
            supportSlots[c.id] = slot;
            occupiedSlots[best] |= 1 << slot;
            followers[best]++;
            localGoals[c.id] = goal;
            tactics[c.id] = SquadCoordinator.MemberTactic.SUPPORT;
        }
        if (count > 0) planningStart = (planningStart + 17) % count;
    }

    SquadCoordinator.MemberTactic tacticFor(Combatant c) {
        validateSupport(c);
        return known(c) ? tactics[c.id] : SquadCoordinator.MemberTactic.ADVANCE;
    }

    int leaderFor(Combatant c) {
        validateSupport(c);
        return known(c) ? leaders[c.id] : -1;
    }

    int destinationFor(Combatant c) {
        validateSupport(c);
        return known(c) ? localGoals[c.id] : -1;
    }

    int sightQueries() { return sightQueries; }

    private boolean known(Combatant c) {
        return c != null && c.id >= 0 && c.id < byId.length;
    }

    private int pointmanIndex(Combatant c) {
        int perTeam = pointmen.length / 2;
        return Math.floorMod(c.id / 10, perTeam) + (c.team == Team.RED ? perTeam : 0);
    }

    private int chooseLeader(Combatant c, int previous) {
        if (++searchStamp == Integer.MAX_VALUE) { Arrays.fill(rejected, 0); searchStamp = 1; }
        // At most two rays for this member, and twenty-four for the entire planning pass.
        for (int attempt = 0; attempt < 2 && sightQueries < MAX_SIGHT_QUERIES; attempt++) {
            int best = -1;
            float bestScore = -Float.MAX_VALUE;
            for (int i = 0; i < actors.size(); i++) {
                Combatant ally = actors.get(i);
                if (!known(ally) || rejected[ally.id] == searchStamp || !roots[ally.id]
                        || followers[ally.id] >= MAX_FOLLOWERS
                        || !usefulLeader(c, ally, ally.id == previous)) continue;
                float dx = ally.position.x - c.position.x, dz = ally.position.z - c.position.z;
                float score = 60f - (float) Math.sqrt(dx * dx + dz * dz)
                        + ally.health * .08f + (SquadCoordinator.sameSquad(c, ally) ? 7f : 0f)
                        + (ally.id == previous ? 9f : 0f);
                if (score > bestScore) { best = ally.id; bestScore = score; }
            }
            if (best < 0) return -1;
            Combatant ally = byId[best];
            sightQueries++;
            if (world.lineOfSight(c.eyeX(), c.eyeY(), c.eyeZ(),
                    ally.eyeX(), ally.eyeY(), ally.eyeZ())) return best;
            rejected[best] = searchStamp;
        }
        return -1;
    }

    private boolean usefulLeader(Combatant c, Combatant ally, boolean existing) {
        if (ally == null || ally == c || !c.alive || !ally.alive || ally.team != c.team
                || ally.team == Team.NONE || ally.health < 50f || !known(ally)
                || !roots[ally.id] || !nav.valid(baseGoals[c.id])) return false;
        float dx = ally.position.x - c.position.x, dz = ally.position.z - c.position.z;
        float distanceSquared = dx * dx + dz * dz;
        if (distanceSquared < 4f * 4f || distanceSquared > (existing
                ? KEEP_RADIUS_SQUARED : PICK_RADIUS_SQUARED)
                || Math.abs(ally.position.y - c.position.y) > 4f) return false;
        if (objectives[c.id] >= 0 && objectives[c.id] != objectives[ally.id]) return false;
        int myGoal = baseGoals[c.id], allyGoal = baseGoals[ally.id];
        if (!nav.valid(allyGoal)) return false;
        float gx = nav.cellX(myGoal) + .5f, gz = nav.cellZ(myGoal) + .5f;
        float goalDx = nav.cellX(allyGoal) - gx, goalDz = nav.cellZ(allyGoal) - gz;
        if (goalDx * goalDx + goalDz * goalDz > 48f * 48f) return false;
        float toGoalX = gx - ally.position.x, toGoalZ = gz - ally.position.z;
        float allyRemaining = (float) Math.sqrt(toGoalX * toGoalX + toGoalZ * toGoalZ);
        if (allyRemaining < 3f || remainingSquared(c, myGoal)
                < (allyRemaining + 3f) * (allyRemaining + 3f)) return false;
        float speedSquared = ally.velocity.x * ally.velocity.x + ally.velocity.z * ally.velocity.z;
        return speedSquared > 1f && ally.velocity.x * toGoalX + ally.velocity.z * toGoalZ
                > (float) Math.sqrt(speedSquared) * allyRemaining * .35f;
    }

    private void validateSupport(Combatant c) {
        if (!known(c) || leaders[c.id] < 0) return;
        if (!usefulLeader(c, byId[leaders[c.id]], true)) {
            leaders[c.id] = -1;
            localGoals[c.id] = baseGoals[c.id];
            tactics[c.id] = baseline[c.id];
        }
    }

    private int supportingGoal(Combatant ally, int slot) {
        float speed = (float) Math.sqrt(ally.velocity.x * ally.velocity.x + ally.velocity.z * ally.velocity.z);
        float ux = ally.velocity.x / speed, uz = ally.velocity.z / speed;
        // Separate rear shoulders: two allies do not stack into one human-shaped column.
        float side = slot == 0 ? -3.5f : 3.5f;
        int n = nav.nearestNode(ally.position.x - ux * 6f - uz * side, ally.position.y,
                ally.position.z - uz * 6f + ux * side);
        return usableGoal(n) ? n : -1;
    }

    private int standOffGoal(Combatant c, int goal) {
        if (!nav.valid(goal)) return goal;
        float direction = c.team == Team.BLUE ? 1f : -1f;
        float rear = Math.min(28f, nav.width() * .12f);
        float forward = direction * (nav.cellX(goal) + .5f) - rear;
        float first = -Float.MAX_VALUE, second = -Float.MAX_VALUE;
        for (int i = 0; i < actors.size(); i++) {
            Combatant ally = actors.get(i);
            if (!ally.alive || !SquadCoordinator.sameSquad(c, ally)
                    || BotCombatStyle.forCombatant(ally) == BotCombatStyle.SNIPER
                    || !ally.bot && ally.velocity.x * ally.velocity.x
                    + ally.velocity.z * ally.velocity.z < 1f) continue;
            float progress = direction * ally.position.x;
            if (progress > first) { second = first; first = progress; }
            else if (progress > second) second = progress;
        }
        if (first > -Float.MAX_VALUE) {
            float infantryFront = second > -Float.MAX_VALUE ? (first + second) * .5f : first;
            forward = Math.min(forward, infantryFront - Math.min(12f, rear * .5f));
        }
        float x = Math.max(8f, Math.min(nav.width() - 8f, direction * forward));
        int id = c.id;
        if (perchCenters[id] == goal && perchRevisions[id] == world.revision()
                && Math.abs(perchFronts[id] - x) < 6f && usableGoal(perchGoals[id])) {
            if (perchVisible[id] || perchRetryPasses[id]-- > 0) return perchGoals[id];
        }
        float z = nav.cellZ(goal) + .5f;
        float shoulder = (id / 2 % 3 - 1) * 7f + (perchAttempts[id]++ % 3 - 1) * 6f;
        int best = -1, visible = -1;
        float bestScore = -Float.MAX_VALUE;
        int queriesBefore = sightQueries;
        // A small terrain search on the planning cadence, with the shared 24-ray budget.
        for (int sample = 0; sample < 9; sample++) {
            float sx = x - direction * (sample / 3) * 4f;
            float sz = z + shoulder + (sample % 3 - 1) * 6f;
            int n = nav.nearestNode(sx, nav.nodeY(goal) + 1f, sz);
            if (!usableGoal(n) || direction * (nav.cellX(n) + .5f - x) > 2f) continue;
            float score = -Math.abs(nav.cellX(n) + .5f - x)
                    - Math.abs(nav.cellZ(n) + .5f - z - shoulder) * .4f
                    + Math.min(3f, nav.nodeY(n) - nav.nodeY(goal)) * 2f;
            if (score > bestScore) { best = n; bestScore = score; }
        }
        if (best >= 0 && sightQueries < MAX_SIGHT_QUERIES) {
            sightQueries++;
            if (world.lineOfSight(nav.cellX(best) + .5f, nav.nodeY(best) + Combatant.EYE_HEIGHT,
                    nav.cellZ(best) + .5f, nav.cellX(goal) + .5f,
                    nav.nodeY(goal) + Combatant.EYE_HEIGHT, z)) visible = best;
        }
        // If the primary shoulder is obstructed, check one alternate, never all nine rays.
        if (visible < 0 && best >= 0 && sightQueries < MAX_SIGHT_QUERIES) {
            int alternate = nav.nearestNode(x - direction * 4f, nav.nodeY(goal), z - shoulder + 10f);
            if (usableGoal(alternate) && direction * (nav.cellX(alternate) + .5f - x) <= 2f) {
                sightQueries++;
                if (world.lineOfSight(nav.cellX(alternate) + .5f,
                        nav.nodeY(alternate) + Combatant.EYE_HEIGHT, nav.cellZ(alternate) + .5f,
                        nav.cellX(goal) + .5f, nav.nodeY(goal) + Combatant.EYE_HEIGHT, z))
                    visible = alternate;
            }
        }
        int n = visible >= 0 ? visible : best;
        if (n < 0) n = nav.nearestNode(c.position.x, c.position.y, c.position.z);
        perchCenters[id] = goal; perchGoals[id] = n;
        perchRevisions[id] = world.revision(); perchFronts[id] = x;
        perchVisible[id] = visible >= 0;
        perchRetryPasses[id] = sightQueries > queriesBefore ? 2 : 0;
        return n;
    }

    private int flankGoal(Combatant c, int goal) {
        if (!nav.valid(goal)) return goal;
        float side = (c.id / 6 & 1) == 0 ? -18f : 18f;
        int n = nav.nearestNode(nav.cellX(goal) + .5f + (c.team == Team.BLUE ? -24f : 24f),
                nav.nodeY(goal), nav.cellZ(goal) + .5f + side);
        return usableGoal(n) ? n : goal;
    }

    private boolean movingToward(Combatant c, int goal) {
        if (!nav.valid(goal)) return false;
        float dx = nav.cellX(goal) + .5f - c.position.x;
        float dz = nav.cellZ(goal) + .5f - c.position.z;
        float distanceSquared = dx * dx + dz * dz;
        float speedSquared = c.velocity.x * c.velocity.x + c.velocity.z * c.velocity.z;
        return distanceSquared > 9f && speedSquared > 1f
                && c.velocity.x * dx + c.velocity.z * dz
                > (float) Math.sqrt(speedSquared * distanceSquared) * .35f;
    }

    private boolean usableGoal(int n) {
        return nav.valid(n) && !world.boxCollides(nav.cellX(n) + .5f, nav.nodeY(n),
                nav.cellZ(n) + .5f, Combatant.RADIUS, Combatant.HEIGHT)
                && world.solid(nav.cellX(n), nav.nodeY(n) - 1, nav.cellZ(n));
    }

    private float remainingSquared(Combatant c, int goal) {
        if (!nav.valid(goal)) return 0f;
        float dx = nav.cellX(goal) + .5f - c.position.x, dz = nav.cellZ(goal) + .5f - c.position.z;
        return dx * dx + dz * dz;
    }
}
