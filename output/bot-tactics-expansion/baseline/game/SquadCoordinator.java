package com.ccs.voxelgun.game;

import com.ccs.voxelgun.voxel.VoxelWorld;

import java.util.Arrays;
import java.util.List;

/**
 * Small, simulation-owned squad planner. It reasons about public objectives and allies only;
 * enemy perception remains in BotBrain. Fixed arrays and infrequent planning avoid tick garbage.
 */
public final class SquadCoordinator {
    public enum Role { ATTACK, DEFEND, FLANK, REGROUP }
    /** A member's own local choice, distinct from the squad's shared strategic assignment. */
    public enum MemberTactic { ADVANCE, SUPPORT, FLANK, HOLD, REGROUP, OVERWATCH }

    /** Capture ownership is public match information, not wall-hack enemy tracking. */
    public interface ObjectiveField {
        int count();
        int node(int index);
        Team owner(int index);
        boolean contested(int index);
    }

    private static final float PLAN_INTERVAL = 1.2f;
    private static final float LOSS_WINDOW = 12f;
    private static final float REGROUP_SECONDS = 5.5f;
    private final NavGrid nav;
    private final List<Combatant> actors;
    private final ObjectiveField objectives;
    private final ObjectiveFormation formation;
    private final AutonomousBotTactics memberTactics;
    private final int squadsPerTeam;
    private final Role[] roles;
    private final int[] centers, points, failedPoints, losses, laneShift, flankStages;
    private final int[] passedFlankStage;
    private final float[] lossAge, regroupTime, avoidTime;
    private final float[] averageX, averageZ;
    private final int[] aliveCount;
    private final int[] openSectorNodes = new int[3];
    private float planTimer;

    public SquadCoordinator(NavGrid nav, VoxelWorld world, List<Combatant> actors,
                            ObjectiveField objectives) {
        this.nav = nav;
        this.actors = actors;
        this.objectives = objectives;
        int maxId = 0;
        for (int i = 0; i < actors.size(); i++) maxId = Math.max(maxId, actors.get(i).id);
        squadsPerTeam = Math.max(1, maxId / 10 + 1);
        int size = squadsPerTeam * 2;
        roles = new Role[size];
        centers = new int[size]; points = new int[size]; failedPoints = new int[size];
        losses = new int[size]; laneShift = new int[size]; flankStages = new int[size];
        lossAge = new float[size]; regroupTime = new float[size]; avoidTime = new float[size];
        averageX = new float[size]; averageZ = new float[size]; aliveCount = new int[size];
        Arrays.fill(centers, -1); Arrays.fill(points, -1); Arrays.fill(failedPoints, -1);
        Arrays.fill(flankStages, -1);
        passedFlankStage = new int[maxId + 1];
        Arrays.fill(passedFlankStage, -1);
        formation = new ObjectiveFormation(nav, world, maxId + 1);
        memberTactics = new AutonomousBotTactics(nav, world, actors, maxId + 1);
        for (int lane = 0; lane < 3; lane++) {
            openSectorNodes[lane] = nav.nearestNode(nav.width() * .5f, 3,
                    (lane + .5f) * nav.depth() / 3f);
        }
        plan();
    }

    /** Twenty tiny squad decisions at most, performed less than once a second. */
    public void update(float dt) {
        if (!Float.isFinite(dt) || dt <= 0f) return;
        for (int i = 0; i < centers.length; i++) {
            if (losses[i] > 0) {
                lossAge[i] += dt;
                if (lossAge[i] > LOSS_WINDOW) { losses[i] = 0; lossAge[i] = 0f; }
            }
            regroupTime[i] = Math.max(0f, regroupTime[i] - dt);
            avoidTime[i] = Math.max(0f, avoidTime[i] - dt);
        }
        planTimer -= dt;
        if (planTimer <= 0f) { planTimer = PLAN_INTERVAL; plan(); }
    }

    /** Match constructs its rules before initial spawning; use the actual deployment positions. */
    public void onDeployment() {
        Arrays.fill(centers, -1); Arrays.fill(flankStages, -1);
        Arrays.fill(passedFlankStage, -1);
        planTimer = PLAN_INTERVAL;
        plan();
    }

    /** Three losses on a forward push cause a short rally and a different next approach. */
    public void onDeath(Combatant victim) {
        if (victim == null || victim.team == Team.NONE) return;
        if (victim.id >= 0 && victim.id < passedFlankStage.length) passedFlankStage[victim.id] = -1;
        int squad = squadIndex(victim);
        float advance = victim.team == Team.BLUE ? victim.position.x
                : nav.width() - victim.position.x;
        if (advance < nav.width() * .18f || regroupTime[squad] > 0f) return;
        if (losses[squad] == 0) lossAge[squad] = 0f;
        if (++losses[squad] < 3) return;
        losses[squad] = 0;
        failedPoints[squad] = points[squad];
        avoidTime[squad] = 18f;
        laneShift[squad] = (laneShift[squad] + 1) % 3;
        regroupTime[squad] = REGROUP_SECONDS;
        planTimer = PLAN_INTERVAL;
        plan();
    }

    public Role roleFor(Combatant c) { return roles[squadIndex(c)]; }
    public boolean regrouping(Combatant c) { return regroupTime[squadIndex(c)] > 0f; }
    public int objectiveFor(Combatant c) { return points[squadIndex(c)]; }
    public MemberTactic memberTacticFor(Combatant c) { return memberTactics.tacticFor(c); }
    /** Ally id, or -1: the human has no special authority or preference. */
    public int supportLeaderFor(Combatant c) { return memberTactics.leaderFor(c); }
    public int supportSightQueries() { return memberTactics.sightQueries(); }

    public int destinationFor(Combatant c) {
        return memberTactics.destinationFor(c);
    }

    /** Only the local tactic planner consumes the unmodified strategic goal. */
    int strategicDestinationFor(Combatant c) {
        int squad = squadIndex(c), center = centers[squad];
        if (roles[squad] == Role.FLANK && flankStages[squad] >= 0) {
            int stage = flankStages[squad];
            float beyondStage = (c.position.x - nav.cellX(stage) - .5f)
                    * (c.team == Team.BLUE ? 1f : -1f);
            // Forward deployment beyond the initial turn must not send a soldier back home.
            if (c.alive && (near(c, stage, 10f) || beyondStage > 64f))
                passedFlankStage[c.id] = stage;
            if (c.alive && BotCombatStyle.forCombatant(c) == BotCombatStyle.SNIPER) {
                boolean infantryAlive = false, infantryPassed = false;
                for (int i = 0; i < actors.size(); i++) {
                    Combatant ally = actors.get(i);
                    if (!ally.alive || !sameSquad(c, ally)
                            || BotCombatStyle.forCombatant(ally) == BotCombatStyle.SNIPER) continue;
                    infantryAlive = true;
                    if (passedFlankStage[ally.id] == stage || near(ally, stage, 10f))
                        infantryPassed = true;
                }
                // Overwatch stays behind the turn; infantry reaching it releases the marksman.
                if (!infantryAlive || infantryPassed) passedFlankStage[c.id] = stage;
            }
            if (passedFlankStage[c.id] != stage) return formation.forBot(stage, c, 8f);
        }
        return formation.forBot(center, c, objectives == null ? 10f : 12f);
    }

    public boolean shouldHold(Combatant c) {
        int squad = squadIndex(c);
        Role role = roles[squad];
        MemberTactic tactic = memberTacticFor(c);
        if (tactic == MemberTactic.SUPPORT) return false;
        if (objectives == null && role != Role.DEFEND && role != Role.REGROUP
                && tactic != MemberTactic.HOLD && tactic != MemberTactic.OVERWATCH) return false;
        return near(c, destinationFor(c), c.bot ? 1.1f : 4f);
    }

    public static boolean sameSquad(Combatant a, Combatant b) {
        return a.team != Team.NONE && a.team == b.team && a.id / 10 == b.id / 10;
    }

    private int squadIndex(Combatant c) {
        int local = Math.floorMod(c.id / 10, squadsPerTeam);
        return (c.team == Team.RED ? squadsPerTeam : 0) + local;
    }

    private boolean near(Combatant c, int node, float radius) {
        if (!nav.valid(node)) return false;
        float dx = nav.cellX(node) + .5f - c.position.x;
        float dz = nav.cellZ(node) + .5f - c.position.z;
        return Math.abs(nav.nodeY(node) - c.position.y) < 2f
                && dx * dx + dz * dz < radius * radius;
    }

    private void plan() {
        Arrays.fill(averageX, 0f); Arrays.fill(averageZ, 0f); Arrays.fill(aliveCount, 0);
        for (int i = 0; i < actors.size(); i++) {
            Combatant c = actors.get(i);
            if (!c.alive || c.team == Team.NONE) continue;
            int squad = squadIndex(c);
            averageX[squad] += c.position.x; averageZ[squad] += c.position.z;
            aliveCount[squad]++;
        }
        for (int squad = 0; squad < centers.length; squad++) {
            Team team = squad < squadsPerTeam ? Team.BLUE : Team.RED;
            int local = squad % squadsPerTeam;
            if (aliveCount[squad] > 0) {
                averageX[squad] /= aliveCount[squad]; averageZ[squad] /= aliveCount[squad];
            } else {
                averageX[squad] = team == Team.BLUE ? 16 : nav.width() - 16;
                averageZ[squad] = nav.depth() * .5f;
            }
            Role role = local % 4 == 1 ? Role.DEFEND : local % 4 == 3 ? Role.FLANK : Role.ATTACK;
            if (regroupTime[squad] > 0f) role = Role.REGROUP;
            roles[squad] = role;
            int beforePoint = points[squad], beforeCenter = centers[squad];
            if (role == Role.REGROUP) {
                centers[squad] = rallyNode(squad, team);
                points[squad] = -1;
            } else if (objectives != null) {
                int point = chooseObjective(squad, team, role);
                points[squad] = point;
                centers[squad] = point < 0 ? rallyNode(squad, team) : objectives.node(point);
            } else {
                points[squad] = -1;
                int lane = (local + laneShift[squad]) % 3;
                float z = nav.valid(openSectorNodes[lane]) ? nav.cellZ(openSectorNodes[lane]) + .5f
                        : (lane + .5f) * nav.depth() / 3f;
                float x = team == Team.BLUE ? nav.width() * .78f : nav.width() * .22f;
                if (role == Role.DEFEND) {
                    float front = friendlyFront(team);
                    x = team == Team.BLUE ? Math.max(nav.width() * .38f, front - 28f)
                            : Math.min(nav.width() * .62f, front + 28f);
                }
                centers[squad] = nav.nearestNode(x, 3, z);
            }
            if (role == Role.FLANK && (beforePoint != points[squad] || beforeCenter != centers[squad])) {
                int center = centers[squad];
                if (nav.valid(center)) {
                    float side = ((local / 4 + laneShift[squad]) & 1) == 0 ? -1f : 1f;
                    // Change avenue near deployment, before advancing along the flank.
                    float x = averageX[squad] + (team == Team.BLUE ? 40f : -40f);
                    x = Math.max(16f, Math.min(nav.width() - 16f, x));
                    int currentLane = Math.max(0, Math.min(2,
                            (int) (averageZ[squad] * 3f / nav.depth())));
                    int flankLane = currentLane == 1 ? side < 0f ? 0 : 2 : 1;
                    float z = (flankLane + .5f) * nav.depth() / 3f;
                    flankStages[squad] = nav.nearestNode(x, nav.nodeY(center), z);
                    for (int i = 0; i < actors.size(); i++) {
                        Combatant c = actors.get(i);
                        if (squadIndex(c) == squad) passedFlankStage[c.id] = -1;
                    }
                }
            } else if (role != Role.FLANK) flankStages[squad] = -1;
        }
        memberTactics.plan(this);
    }

    private float friendlyFront(Team team) {
        float front = team == Team.BLUE ? nav.width() * .38f : nav.width() * .62f;
        for (int i = 0; i < actors.size(); i++) {
            Combatant c = actors.get(i);
            if (!c.alive || c.team != team) continue;
            front = team == Team.BLUE ? Math.max(front, c.position.x) : Math.min(front, c.position.x);
        }
        return Math.max(nav.width() * .22f, Math.min(nav.width() * .78f, front));
    }

    private int chooseObjective(int squad, Team team, Role role) {
        int best = -1;
        float bestScore = -Float.MAX_VALUE;
        int count = objectives.count();
        boolean hasOwn = false;
        for (int i = 0; i < count; i++) if (objectives.owner(i) == team) hasOwn = true;
        for (int i = 0; i < count; i++) {
            int node = objectives.node(i);
            if (!nav.valid(node)) continue;
            boolean own = objectives.owner(i) == team, contested = objectives.contested(i);
            float score;
            if (role == Role.DEFEND && hasOwn) score = own ? contested ? 240f : 140f : -150f;
            else score = own ? contested ? 180f : -110f
                    : objectives.owner(i) == Team.NONE ? 150f : 165f;
            // Several squads may reinforce a contested point, but quiet sectors stay distributed.
            int assigned = 0;
            int start = team == Team.BLUE ? 0 : squadsPerTeam;
            for (int s = start; s < start + squadsPerTeam; s++)
                if (s != squad && points[s] == i && roles[s] != Role.REGROUP) assigned++;
            score -= assigned * (contested ? 22f : 48f);
            float dx = nav.cellX(node) - averageX[squad], dz = nav.cellZ(node) - averageZ[squad];
            score -= (float) Math.sqrt(dx * dx + dz * dz) * .12f;
            if (avoidTime[squad] > 0f && failedPoints[squad] == i) score -= 120f;
            int preferred = Math.floorMod(squad % squadsPerTeam + laneShift[squad]
                    + (team == Team.RED ? 2 : 0), count);
            if (preferred == i) score += 26f;
            if (points[squad] == i) score += 14f; // Hysteresis: no goal flicker on occupancy ties.
            if (score > bestScore) { best = i; bestScore = score; }
        }
        return best;
    }

    private int rallyNode(int squad, Team team) {
        int best = -1;
        float bestScore = Float.MAX_VALUE;
        if (objectives != null) for (int i = 0; i < objectives.count(); i++) {
            if (objectives.owner(i) != team || objectives.contested(i)) continue;
            int n = objectives.node(i);
            float dx = nav.cellX(n) - averageX[squad], dz = nav.cellZ(n) - averageZ[squad];
            float score = dx * dx + dz * dz;
            if (score < bestScore) { best = n; bestScore = score; }
        }
        if (best >= 0) return best;
        float x = averageX[squad] + (team == Team.BLUE ? -36f : 36f);
        x = Math.max(nav.width() * .15f, Math.min(nav.width() * .85f, x));
        return nav.nearestNode(x, 3, averageZ[squad]);
    }
}
