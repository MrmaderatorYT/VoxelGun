package com.ccs.voxelstrike.game;

/** Squad lanes pull both teams across the map; perception still decides who is an enemy. */
public final class BattlefrontPlanner {
    private final NavGrid nav;
    private final BattlefieldRoutes routes;
    private final SquadCoordinator squads;
    private final SafeForwardSpawn forwardSpawn;

    public BattlefrontPlanner(NavGrid nav) {
        this.nav = nav;
        routes = new BattlefieldRoutes(nav);
        squads = null;
        forwardSpawn = null;
    }

    /** Live matches use coordinated tactics; the grid-only constructor remains a route utility. */
    public BattlefrontPlanner(Match match) {
        nav = match.nav();
        routes = new BattlefieldRoutes(nav);
        squads = new SquadCoordinator(nav, match.world(), match.combatants(), null);
        forwardSpawn = new SafeForwardSpawn(nav, match.world(), match.combatants(), null);
    }

    public int destinationFor(Combatant c) {
        if (squads != null) return squads.destinationFor(c);
        int lane = Math.floorMod((c.id / 2) / 5, 3);
        float z = (lane + .5f) * nav.depth() / 3f;
        float x = c.team == Team.RED ? 32f : nav.width() - 32f;
        return nav.nearestNode(x, c.position.y, z);
    }

    public int waypointFor(Combatant c) {
        int destination = destinationFor(c);
        return destination < 0 ? -1 : routes.waypoint(c, destination);
    }

    public void update(float dt) { if (squads != null) squads.update(dt); }
    public void onDeath(Combatant c) { if (squads != null) squads.onDeath(c); }
    public void onDeployment() { if (squads != null) squads.onDeployment(); }
    public boolean shouldHold(Combatant c) { return squads != null && squads.shouldHold(c); }
    public boolean regrouping(Combatant c) { return squads != null && squads.regrouping(c); }
    public SquadCoordinator.Role roleFor(Combatant c) {
        return squads == null ? SquadCoordinator.Role.ATTACK : squads.roleFor(c);
    }
    public SquadCoordinator.MemberTactic memberTacticFor(Combatant c) {
        return squads == null ? SquadCoordinator.MemberTactic.ADVANCE : squads.memberTacticFor(c);
    }
    public int supportLeaderFor(Combatant c) { return squads == null ? -1 : squads.supportLeaderFor(c); }
    public float[] deploymentFor(Combatant c) {
        return forwardSpawn == null ? null : forwardSpawn.deploymentFor(c, destinationFor(c));
    }
}
