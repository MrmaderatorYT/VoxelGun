package com.ccs.voxelgun.game;

import com.ccs.voxelgun.map.SpawnPoint;
import com.ccs.voxelgun.math.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Objective rules live on the simulation thread, including bots' next destination. */
public final class Objectives {
    public static final float RADIUS = 4f;
    private final Match match;
    private final ObjectiveBotTactics tactics;
    private final int[] assignments, tacticalGoals;
    private final ObjectiveBotTactics.Role[] roles;
    private final PathFinder.Path carrierRoute = new PathFinder.Path(8192);
    private float planningTime;
    private boolean planned;
    private int designatedInteractor = -1;
    private int plannedCarrier = -2;
    private boolean plannedPlanted;
    private int plannedRevision = -1;
    private final List<Integer> sites = new ArrayList<>();
    private final int[] nodes = new int[3];
    private final Team[] owners = {Team.NONE, Team.NONE, Team.NONE};
    private final float[] capture = new float[3];
    private final boolean[] contested = new boolean[3];
    private final int blueBase;
    private final int redBase;
    private final java.util.Set<SpawnPoint> playableSpawns = new java.util.HashSet<>();
    private float tick;
    private float rotation = 60f;
    private int hill;
    private int carrier = -1;
    private final Vec3 core = new Vec3();
    private float droppedTime;
    private boolean planted;
    private float roundTime = 90f;
    private float plantTime;
    private float defuseTime;
    private float roundBreak;
    private int interacting = -1;

    public Objectives(Match match) {
        this.match = match;
        tactics = new ObjectiveBotTactics(match.nav(), match.world());
        int actors = match.combatants().size();
        assignments = new int[actors];
        tacticalGoals = new int[actors];
        roles = new ObjectiveBotTactics.Role[actors];
        java.util.Arrays.fill(tacticalGoals, -1);
        for (Combatant c : match.combatants()) assignments[c.id] = Math.floorMod(c.id, 3);
        PathFinder.Path path = new PathFinder.Path(8192);
        int chosenBlue = -1, chosenRed = -1;
        bases: for (SpawnPoint blue : match.map().blueSpawns()) {
            for (SpawnPoint red : match.map().redSpawns()) {
                int b = spawnNode(blue), r = spawnNode(red);
                if (reachable(b, r, path) && reachable(r, b, path)) {
                    chosenBlue = b; chosenRed = r; break bases;
                }
            }
        }
        if (chosenBlue < 0) throw new IllegalStateException("No objective bases on " + match.map().id());
        blueBase = chosenBlue; redBase = chosenRed;
        // Require return paths as well: a one-way drop is unsuitable for a delivery objective.
        for (SpawnPoint spawn : match.map().freeSpawns()) {
            int n = spawnNode(spawn);
            if (!sites.contains(n) && reachable(blueBase, n, path) && reachable(redBase, n, path)
                    && reachable(n, blueBase, path) && reachable(n, redBase, path)) sites.add(n);
        }
        if (sites.isEmpty()) {
            if (!reachable(blueBase, redBase, path)) throw new IllegalStateException("No objective route on " + match.map().id());
            sites.add(path.node(path.length() / 2));
        }
        // Prefer central, separated locations over a team's home spawn.
        sites.sort((a, b) -> Float.compare(centerDistance(a), centerDistance(b)));
        nodes[0] = sites.get(0);
        for (int i = 1; i < nodes.length; i++) {
            int best = nodes[0];
            float distance = -1f;
            for (int n : sites) {
                float nearest = Float.MAX_VALUE;
                for (int j = 0; j < i; j++) nearest = Math.min(nearest, nodeDistance(n, nodes[j]));
                if (nearest > distance) { distance = nearest; best = n; }
            }
            nodes[i] = best;
        }
        for (SpawnPoint s : match.map().blueSpawns())
            if (reachable(spawnNode(s), nodes[0], path)) playableSpawns.add(s);
        for (SpawnPoint s : match.map().redSpawns())
            if (reachable(spawnNode(s), nodes[0], path)) playableSpawns.add(s);
        resetCore();
    }

    private int spawnNode(SpawnPoint s) {
        int y = match.world().standableLevel((int) s.x, (int) s.z, (int) s.y, 2);
        return match.nav().nearestNode(s.x, y >= 0 ? y : s.y, s.z);
    }
    private boolean reachable(int a, int b, PathFinder.Path path) {
        return match.pathFinder().find(a, b, path);
    }
    private float centerDistance(int n) {
        float x = match.nav().cellX(n) - match.world().sizeX() * 0.5f;
        float z = match.nav().cellZ(n) - match.world().sizeZ() * 0.5f;
        return x * x + z * z;
    }
    private float nodeDistance(int a, int b) {
        float x = match.nav().cellX(a) - match.nav().cellX(b);
        float z = match.nav().cellZ(a) - match.nav().cellZ(b);
        return x * x + z * z;
    }
    public int count() { return match.mode() == GameMode.CAPTURE_POINT ? 3 : 1; }
    public int node(int index) { return nodes[index]; }
    public boolean roundPaused() { return roundBreak > 0f; }
    public boolean planted() { return planted; }
    public int carrierId() { return carrier; }
    public boolean canSpawn(SpawnPoint spawn) { return playableSpawns.contains(spawn); }
    public int base(Team team) { return team == Team.BLUE ? blueBase : redBase; }

    public void update(float dt) {
        planningTime -= dt;
        if (match.mode() == GameMode.BOMB_PLANT) { updateBomb(dt); ensurePlan(); return; }
        if (match.mode() == GameMode.CRYSTAL_HEIST) { updateCore(dt); ensurePlan(); return; }
        if (match.mode() == GameMode.KING_OF_THE_HILL) {
            rotation -= dt;
            if (rotation <= 0f) {
                rotation += 60f;
                hill = (hill + 1) % sites.size();
                nodes[0] = sites.get(hill);
                planned = false;
                owners[0] = Team.NONE;
                match.objectiveBanner("HILL MOVED");
            }
        }
        tick += dt;
        int seconds = (int) tick;
        tick -= seconds;
        for (int i = 0; i < count(); i++) {
            int blue = occupants(nodes[i], Team.BLUE), red = occupants(nodes[i], Team.RED);
            contested[i] = blue > 0 && red > 0;
            if (match.mode() == GameMode.KING_OF_THE_HILL) {
                owners[i] = contested[i] || blue + red == 0 ? Team.NONE : blue > 0 ? Team.BLUE : Team.RED;
            } else if (!contested[i] && blue + red > 0) {
                capture[i] = Math.max(-1f, Math.min(1f, capture[i] + (blue > 0 ? 1 : -1) * dt / 5f));
                if (capture[i] >= 1f) owners[i] = Team.BLUE;
                else if (capture[i] <= -1f) owners[i] = Team.RED;
                else if ((owners[i] == Team.BLUE && capture[i] <= 0f)
                        || (owners[i] == Team.RED && capture[i] >= 0f)) owners[i] = Team.NONE;
            }
            if (!contested[i] && owners[i] != Team.NONE && seconds > 0) {
                match.awardObjective(owners[i], seconds);
                for (Combatant c : match.combatants()) if (c.alive && c.team == owners[i]
                        && near(c, nodes[i], RADIUS)) c.score += seconds * 5;
            }
        }
        ensurePlan();
    }

    private int occupants(int node, Team team) {
        int count = 0;
        for (Combatant c : match.combatants()) if (c.alive && c.team == team && near(c, node, RADIUS)) count++;
        return count;
    }
    private boolean near(Combatant c, int node, float radius) {
        float x = match.nav().cellX(node) + 0.5f, y = match.nav().nodeY(node);
        float z = match.nav().cellZ(node) + 0.5f;
        float dx = c.position.x - x, dz = c.position.z - z;
        return Math.abs(c.position.y - y) < 2f && dx * dx + dz * dz < radius * radius
                && match.world().lineOfSight(x, y + 1f, z, c.eyeX(), c.eyeY(), c.eyeZ());
    }

    private void updateBomb(float dt) {
        if (roundBreak > 0f) {
            roundBreak -= dt;
            if (roundBreak <= 0f) {
                planted = false; roundTime = 90f; plantTime = defuseTime = 0f; interacting = -1;
                designatedInteractor = -1; planned = false;
                match.restartObjectiveRound();
            }
            return;
        }
        roundTime -= dt;
        int blueAlive = 0, redAlive = 0;
        ensurePlan();
        Combatant actor = null;
        for (Combatant c : match.combatants()) {
            if (!c.alive) continue;
            if (c.team == Team.BLUE) blueAlive++; else redAlive++;
            if (c.team == (planted ? Team.RED : Team.BLUE)
                    && (!c.bot || c.id == designatedInteractor)
                    && near(c, nodes[0], 2.8f) && !c.firing
                    && c.velocity.x * c.velocity.x + c.velocity.z * c.velocity.z < 1f
                    && (actor == null || !c.bot || c.id == interacting && actor.bot)) actor = c;
        }
        if (actor == null || actor.id != interacting) { plantTime = defuseTime = 0f; }
        interacting = actor == null ? -1 : actor.id;
        if (actor != null) {
            if (planted) defuseTime += dt; else plantTime += dt;
            if (!planted && plantTime >= 3f) {
                planted = true; roundTime = 35f; actor.score += 100; plantTime = 0f;
                match.objectiveBanner("BOMB PLANTED");
            } else if (planted && defuseTime >= 5f) { actor.score += 150; endRound(Team.RED); return; }
        }
        if (planted && roundTime <= 0f) endRound(Team.BLUE);
        else if (redAlive == 0 && blueAlive > 0) endRound(Team.BLUE);
        else if (!planted && (blueAlive == 0 || roundTime <= 0f)) endRound(Team.RED);
    }
    private void endRound(Team winner) {
        roundBreak = 4f;
        match.awardObjective(winner, 1);
        if (match.state() != Match.State.FINISHED) match.objectiveBanner(winner.name() + " WINS ROUND");
        for (Combatant c : match.combatants()) c.money = Economy.clamp(c.money + (c.team == winner ? 2400 : 1900));
    }

    private void resetCore() {
        carrier = -1; droppedTime = 0f;
        core.set(match.nav().cellX(nodes[0]) + 0.5f, match.nav().nodeY(nodes[0]), match.nav().cellZ(nodes[0]) + 0.5f);
    }
    private void updateCore(float dt) {
        Combatant holder = match.combatantById(carrier);
        if (holder != null) {
            if (!holder.alive) {
                carrier = -1; droppedTime = 0f;
                if (holder.position.y < 0f) resetCore();
            } else {
                core.set(holder.position);
                if (near(holder, holder.team == Team.BLUE ? blueBase : redBase, 4f)) {
                    holder.score += 250; match.awardObjective(holder.team, 1); resetCore();
                    if (match.state() != Match.State.FINISHED) match.objectiveBanner("CORE DELIVERED");
                }
                return;
            }
        }
        droppedTime += dt;
        if (droppedTime >= 20f) resetCore();
        for (Combatant c : match.combatants()) if (c.alive && Vec3.distance(c.position, core) < 2f
                && match.world().lineOfSight(c.eyeX(), c.eyeY(), c.eyeZ(), core.x, core.y + 1f, core.z)) {
            carrier = c.id; c.score += 25; match.objectiveBanner(c.name + " HAS THE CORE"); break;
        }
    }

    /** Tactical planning uses public objective state and allies, never hidden enemy positions. */
    private void ensurePlan() {
        Combatant worker = match.combatantById(designatedInteractor);
        boolean lostWorker = match.mode() == GameMode.BOMB_PLANT && designatedInteractor >= 0
                && (worker == null || !worker.alive || worker.team != (planted ? Team.RED : Team.BLUE));
        if (planned && planningTime > 0f && plannedCarrier == carrier
                && plannedPlanted == planted && !lostWorker) return;
        planningTime = match.mode() == GameMode.CRYSTAL_HEIST ? .85f : 1.25f;
        if (plannedCarrier != carrier) carrierRoute.clear();
        plannedCarrier = carrier;
        plannedPlanted = planted;
        planned = true;
        if (plannedRevision != match.world().revision()) {
            plannedRevision = match.world().revision();
            carrierRoute.clear();
        }
        if (match.mode() == GameMode.CAPTURE_POINT) planCapture();
        else if (match.mode() == GameMode.KING_OF_THE_HILL) planHill();
        else if (match.mode() == GameMode.BOMB_PLANT) planBomb();
        else planCore();
    }

    private int enemyBase(Team team) { return team == Team.BLUE ? redBase : blueBase; }

    private void assign(Combatant c, int center, ObjectiveBotTactics.Role role, float ring, float radius) {
        roles[c.id] = role;
        tacticalGoals[c.id] = ring > 0f
                ? tactics.slot(center, c, ring, radius, enemyBase(c.team)) : center;
    }

    private void planCapture() {
        for (Team team : new Team[]{Team.BLUE, Team.RED}) {
            int[] assigned = new int[3];
            int teamSize = 0;
            for (Combatant c : match.combatants()) if (c.alive && c.team == team) {
                if (c.bot) teamSize++;
                else for (int i = 0; i < 3; i++) if (near(c, nodes[i], RADIUS)) assigned[i]++;
            }
            for (Combatant c : match.combatants()) if (c.alive && c.bot && c.team == team) {
                int point = ObjectiveBotTactics.choosePoint(team, owners, contested, assigned,
                        assignments[c.id], teamSize);
                assignments[c.id] = point;
                assigned[point]++;
                assign(c, nodes[point], owners[point] == team && !contested[point]
                        ? ObjectiveBotTactics.Role.DEFEND : ObjectiveBotTactics.Role.CAPTURE,
                        2.2f, RADIUS);
            }
        }
    }

    private void planHill() {
        for (Team team : new Team[]{Team.BLUE, Team.RED}) {
            int index = 0;
            for (Combatant c : match.combatants()) if (c.alive && c.bot && c.team == team) {
                boolean support = index++ > 0 && BotCombatStyle.forCombatant(c) != BotCombatStyle.RUSHER;
                assign(c, nodes[0], support ? ObjectiveBotTactics.Role.HILL_SUPPORT
                        : ObjectiveBotTactics.Role.HILL_CAPTURE, support ? 3.1f : 1.8f, RADIUS);
            }
        }
    }

    private void planBomb() {
        Team actingTeam = planted ? Team.RED : Team.BLUE;
        Combatant current = match.combatantById(interacting);
        Combatant designated;
        // Preserve an uninterrupted plant/defuse. Otherwise let the closest teammate take it,
        // including a human who has already entered the interaction area.
        if (current != null && current.alive && current.team == actingTeam
                && !current.firing && near(current, nodes[0], 2.8f)) designated = current;
        else {
            float best = Float.MAX_VALUE;
            designated = null;
            for (Combatant c : match.combatants()) if (c.alive && c.team == actingTeam) {
                if (!c.bot && near(c, nodes[0], 2.8f) && !c.firing
                        && c.velocity.x * c.velocity.x + c.velocity.z * c.velocity.z < 1f) {
                    designated = c; break;
                }
                if (!c.bot) continue;
                float dx = c.position.x - match.nav().cellX(nodes[0]) - .5f;
                float dz = c.position.z - match.nav().cellZ(nodes[0]) - .5f;
                float score = dx * dx + dz * dz;
                // A small tie margin keeps en-route teammates from swapping jobs each second.
                if (c.id == designatedInteractor) score *= .8f;
                if (score < best) { best = score; designated = c; }
            }
        }
        designatedInteractor = designated == null ? -1 : designated.id;
        for (Combatant c : match.combatants()) if (c.alive && c.bot) {
            boolean interact = c.id == designatedInteractor;
            float ring = BotCombatStyle.forCombatant(c) == BotCombatStyle.SNIPER ? 6f : 4.5f;
            assign(c, nodes[0], interact ? ObjectiveBotTactics.Role.INTERACT
                    : ObjectiveBotTactics.Role.COVER, interact ? 0f : ring, 7.5f);
        }
    }

    private void planCore() {
        int coreNode = match.nav().nearestNode(core.x, core.y, core.z);
        if (!match.nav().valid(coreNode)) coreNode = nodes[0];
        Combatant holder = match.combatantById(carrier);
        if (holder != null && !holder.alive) holder = null;
        if (holder == null) carrierRoute.clear();
        else if (match.pathFinder().canSearch()) {
            // One shared route supplies all escort/interception waypoints, within the same
            // per-frame path budget as movement. A refused search is simply retried next plan.
            match.pathFinder().find(coreNode, base(holder.team), carrierRoute);
        }
        int blue = 0, red = 0;
        for (Combatant c : match.combatants()) if (c.alive && c.bot) {
            int index = c.team == Team.BLUE ? blue++ : red++;
            if (holder == null) {
                // One collector per team reaches pickup distance; the others secure its edge.
                assign(c, coreNode, index == 0 ? ObjectiveBotTactics.Role.RECOVER
                        : ObjectiveBotTactics.Role.COVER, index == 0 ? 0f : 3f, 4.5f);
            } else if (holder.id == c.id) {
                assign(c, base(c.team), ObjectiveBotTactics.Role.CARRY, 0f, 0f);
            } else if (holder.team == c.team) {
                int anchor = index % 2 == 0 ? routeAhead(coreNode, 5) : coreNode;
                assign(c, anchor, ObjectiveBotTactics.Role.ESCORT, 2.6f, 4f);
            } else {
                // The carried core is shown on everyone's HUD. Intercept its route home;
                // no position of an unmarked opponent is consulted here.
                int anchor = index == 0 ? coreNode : routeAhead(coreNode, 7 + index % 3 * 4);
                assign(c, anchor, ObjectiveBotTactics.Role.INTERCEPT, index == 0 ? 0f : 2f, 3.5f);
            }
        }
    }

    private int routeAhead(int fallback, int steps) {
        if (carrierRoute.length() == 0) return fallback;
        // A budget-deferred plan may reuse a previous route: skip its already traversed prefix.
        int nearest = 0;
        float distance = Float.MAX_VALUE;
        for (int i = 0; i < carrierRoute.length(); i++) {
            int n = carrierRoute.node(i);
            if (!match.nav().valid(n)) continue;
            float d = nodeDistance(n, fallback);
            if (d < distance) { distance = d; nearest = i; }
        }
        int ahead = carrierRoute.node(Math.min(carrierRoute.length() - 1, nearest + steps));
        return match.nav().valid(ahead) ? ahead : fallback;
    }

    /** A designated bot enters the interaction early enough to finish inside the rules radius. */
    public boolean shouldInteract(Combatant c) {
        if (match.mode() != GameMode.BOMB_PLANT || roundPaused() || !c.alive || !c.bot) return false;
        ensurePlan();
        return c.id == designatedInteractor && near(c, nodes[0], 2.4f);
    }

    public boolean isInteractor(Combatant c) {
        if (match.mode() != GameMode.BOMB_PLANT || !c.alive) return false;
        ensurePlan();
        return c.id == designatedInteractor;
    }

    ObjectiveBotTactics.Role roleFor(Combatant c) {
        ensurePlan();
        return roles[c.id];
    }

    int assignedPoint(Combatant c) { ensurePlan(); return assignments[c.id]; }

    private int centerFor(Combatant c) {
        if (match.mode() == GameMode.CRYSTAL_HEIST) {
            if (carrier == c.id) return base(c.team);
            int n = match.nav().nearestNode(core.x, core.y, core.z);
            return match.nav().valid(n) ? n : nodes[0];
        }
        if (match.mode() == GameMode.CAPTURE_POINT) {
            if (c.bot) { ensurePlan(); return nodes[assignments[c.id]]; }
            for (int offset = 0; offset < 3; offset++) {
                int i = Math.floorMod(c.id + offset, 3);
                if (owners[i] != c.team || contested[i]) return nodes[i];
            }
            return nodes[Math.floorMod(c.id, 3)];
        }
        return nodes[0];
    }

    public int goalFor(Combatant c) {
        if (!c.bot) return centerFor(c);
        ensurePlan();
        int goal = tacticalGoals[c.id];
        return match.nav().valid(goal) ? goal : centerFor(c);
    }

    public boolean shouldHold(Combatant c) {
        int goal = goalFor(c);
        if (c.bot && (match.mode() == GameMode.CAPTURE_POINT || match.mode() == GameMode.KING_OF_THE_HILL)) {
            return near(c, centerFor(c), RADIUS) && near(c, goal, .8f);
        }
        if (c.bot && match.mode() == GameMode.BOMB_PLANT) {
            return isInteractor(c) ? shouldInteract(c) : near(c, goal, .9f);
        }
        return near(c, goal, c.bot ? .9f : match.mode() == GameMode.BOMB_PLANT ? 2.1f : 2.6f);
    }

    public void snapshot(HudSnapshot out) {
        out.objectiveCount = count();
        out.objectiveSeconds = match.mode() == GameMode.BOMB_PLANT ? Math.max(0f, roundTime) : rotation;
        out.objectiveStatus = match.mode() == GameMode.BOMB_PLANT
                ? roundPaused() ? "NEXT ROUND" : planted ? "DEFUSE" : "PLANT"
                : match.mode() == GameMode.CRYSTAL_HEIST ? carrier >= 0 ? "CORE CARRIED" : "TAKE CORE"
                : match.mode() == GameMode.CAPTURE_POINT ? "CAPTURE" : "HILL";
        out.interactProgress = planted ? defuseTime / 5f : plantTime / 3f;
        for (int i = 0; i < count(); i++) {
            out.objectiveX[i] = match.nav().cellX(nodes[i]) + 0.5f;
            out.objectiveY[i] = match.nav().nodeY(nodes[i]);
            out.objectiveZ[i] = match.nav().cellZ(nodes[i]) + 0.5f;
            out.objectiveColor[i] = owners[i] == Team.NONE ? 0xFFFFD65A : owners[i].color();
            out.objectiveContested[i] = contested[i];
            out.objectiveProgress[i] = capture[i];
        }
        if (match.mode() == GameMode.CRYSTAL_HEIST) {
            out.objectiveCount = 3;
            out.objectiveX[0] = core.x; out.objectiveZ[0] = core.z;
            out.objectiveY[0] = core.y;
            out.objectiveY[1] = match.nav().nodeY(blueBase);
            out.objectiveY[2] = match.nav().nodeY(redBase);
            out.objectiveX[1] = match.nav().cellX(blueBase) + 0.5f;
            out.objectiveZ[1] = match.nav().cellZ(blueBase) + 0.5f;
            out.objectiveColor[1] = Team.BLUE.color();
            out.objectiveX[2] = match.nav().cellX(redBase) + 0.5f;
            out.objectiveZ[2] = match.nav().cellZ(redBase) + 0.5f;
            out.objectiveColor[2] = Team.RED.color();
        }
        int goal = goalFor(match.player());
        out.objectiveDistance = (float) Math.hypot(match.nav().cellX(goal) + 0.5f - out.playerX,
                match.nav().cellZ(goal) + 0.5f - out.playerZ);
        out.objectiveBearing = (float) Math.atan2(match.nav().cellX(goal) + 0.5f - out.playerX,
                -(match.nav().cellZ(goal) + 0.5f - out.playerZ)) - out.playerYaw;
    }
}
