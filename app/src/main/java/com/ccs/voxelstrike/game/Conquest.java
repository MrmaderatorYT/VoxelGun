package com.ccs.voxelstrike.game;

/** Five-sector battlefield rules. Simulation-thread owned; no per-tick allocations. */
public final class Conquest implements SquadCoordinator.ObjectiveField {
    public static final int POINTS = 5;
    private static final float RADIUS = 12f;
    private final Match match;
    private final BattlefieldRoutes routes;
    private final SquadCoordinator squads;
    private final SafeForwardSpawn forwardSpawn;
    private final int[] nodes = new int[POINTS];
    private final Team[] owners = new Team[POINTS];
    private final float[] progress = new float[POINTS];
    private final boolean[] contested = new boolean[POINTS];
    private int blueTickets, redTickets;
    private float bleedTime;

    public Conquest(Match match, int tickets) {
        this.match = match;
        routes = new BattlefieldRoutes(match.nav());
        blueTickets = redTickets = tickets;
        int[] xs = {160, 320, 320, 320, 480};
        int[] zs = {288, 96, 288, 480, 288};
        for (int i = 0; i < POINTS; i++) {
            nodes[i] = match.nav().nearestNode(xs[i], 3, zs[i]);
            if (nodes[i] < 0) throw new IllegalStateException("Unwalkable conquest point " + i);
            owners[i] = Team.NONE;
        }
        squads = new SquadCoordinator(match.nav(), match.world(), match.combatants(), this);
        forwardSpawn = new SafeForwardSpawn(match.nav(), match.world(), match.combatants(), this);
    }

    public static int squad(Combatant c) { return (c.id / 2) / 5; }
    @Override public int count() { return POINTS; }
    public int tickets(Team team) { return team == Team.BLUE ? blueTickets : redTickets; }
    public int node(int i) { return nodes[i]; }
    public Team owner(int i) { return owners[i]; }
    public boolean contested(int i) { return contested[i]; }

    public void death(Team team) {
        if (team == Team.BLUE) blueTickets = Math.max(0, blueTickets - 1);
        if (team == Team.RED) redTickets = Math.max(0, redTickets - 1);
    }

    public void update(float dt) {
        int blueHeld = 0, redHeld = 0;
        for (int i = 0; i < POINTS; i++) {
            int blue = 0, red = 0;
            float x = match.nav().cellX(nodes[i]) + .5f;
            float y = match.nav().nodeY(nodes[i]);
            float z = match.nav().cellZ(nodes[i]) + .5f;
            for (int actor = 0; actor < match.combatants().size(); actor++) {
                Combatant c = match.combatants().get(actor);
                float dx = c.position.x - x, dz = c.position.z - z;
                if (!c.alive || Math.abs(c.position.y - y) > 2f
                        || dx * dx + dz * dz > RADIUS * RADIUS
                        || !match.world().lineOfSight(x, y + 1f, z, c.eyeX(), c.eyeY(), c.eyeZ())) continue;
                if (c.team == Team.BLUE) blue++; else if (c.team == Team.RED) red++;
            }
            contested[i] = blue > 0 && red > 0;
            if (!contested[i] && blue + red > 0) {
                progress[i] = Math.max(-1f, Math.min(1f,
                        progress[i] + (blue > 0 ? 1f : -1f) * dt / 8f));
                Team before = owners[i];
                if (progress[i] == 1f) owners[i] = Team.BLUE;
                else if (progress[i] == -1f) owners[i] = Team.RED;
                else if ((owners[i] == Team.BLUE && progress[i] <= 0)
                        || (owners[i] == Team.RED && progress[i] >= 0)) owners[i] = Team.NONE;
                if (before != owners[i] && owners[i] != Team.NONE) {
                    match.objectiveBanner(owners[i].name() + " CAPTURED " + (char) ('A' + i));
                }
            }
            if (!contested[i] && owners[i] == Team.BLUE) blueHeld++;
            if (!contested[i] && owners[i] == Team.RED) redHeld++;
        }
        bleedTime += dt;
        while (bleedTime >= 2f) {
            bleedTime -= 2f;
            if (blueHeld > POINTS / 2) redTickets = Math.max(0, redTickets - (blueHeld - redHeld));
            if (redHeld > POINTS / 2) blueTickets = Math.max(0, blueTickets - (redHeld - blueHeld));
        }
        squads.update(dt);
    }

    public int goalFor(Combatant c) { return squads.destinationFor(c); }
    public boolean shouldHold(Combatant c) { return squads.shouldHold(c); }
    public boolean regrouping(Combatant c) { return squads.regrouping(c); }
    public SquadCoordinator.Role roleFor(Combatant c) { return squads.roleFor(c); }
    public int objectiveFor(Combatant c) { return squads.objectiveFor(c); }
    public void onDeath(Combatant c) { squads.onDeath(c); }
    public void onDeployment() { squads.onDeployment(); }
    public SquadCoordinator.MemberTactic memberTacticFor(Combatant c) { return squads.memberTacticFor(c); }
    public int supportLeaderFor(Combatant c) { return squads.supportLeaderFor(c); }

    public int waypointFor(Combatant c) { return routes.waypoint(c, goalFor(c)); }

    /** Returns shared scratch coordinates, consumed immediately by Match, or null for home base. */
    public float[] deploymentFor(Combatant c) {
        return forwardSpawn.deploymentFor(c, goalFor(c));
    }

    public void snapshot(HudSnapshot out) {
        out.conquest = true;
        out.blueScore = blueTickets; out.redScore = redTickets;
        out.objectiveCount = POINTS; out.objectiveStatus = "CONQUEST · TICKETS";
        for (int i = 0; i < POINTS; i++) {
            out.objectiveX[i] = match.nav().cellX(nodes[i]) + .5f;
            out.objectiveY[i] = match.nav().nodeY(nodes[i]);
            out.objectiveZ[i] = match.nav().cellZ(nodes[i]) + .5f;
            out.objectiveColor[i] = owners[i] == Team.NONE ? 0xFFFFD65A : owners[i].color();
            out.objectiveProgress[i] = progress[i]; out.objectiveContested[i] = contested[i];
        }
        int n = goalFor(match.player());
        float dx = match.nav().cellX(n) + .5f - out.playerX;
        float dz = match.nav().cellZ(n) + .5f - out.playerZ;
        out.objectiveDistance = (float) Math.hypot(dx, dz);
        out.objectiveBearing = (float) Math.atan2(dx, -dz) - out.playerYaw;
    }
}
