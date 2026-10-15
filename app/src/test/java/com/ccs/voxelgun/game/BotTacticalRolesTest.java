package com.ccs.voxelgun.game;

import com.ccs.voxelgun.input.InputState;
import com.ccs.voxelgun.map.Maps;
import com.ccs.voxelgun.math.MathUtil;
import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.voxel.VoxelWorld;
import com.ccs.voxelgun.weapon.WeaponSlot;
import com.ccs.voxelgun.weapon.WeaponType;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** Public squad decisions plus real perception, weapon selection and voxel movement. */
public class BotTacticalRolesTest {
    private static final float DT = Match.STEP;
    private static final Team[] TEAMS = {Team.BLUE, Team.RED};
    private static VoxelWorld flat;
    private static NavGrid nav;
    private static Match match;
    private static int wallX = -1;

    @BeforeClass public static void prepareSharedTerrain() {
        flat = new VoxelWorld(640, 8, 576);
        for (int z = 0; z < flat.sizeZ(); z++) for (int x = 0; x < flat.sizeX(); x++)
            for (int y = 0; y < 3; y++) flat.set(x, y, z, Materials.CONCRETE);
        flat.commit();
        nav = new NavGrid(flat);

        MatchConfig config = new MatchConfig();
        config.mode = GameMode.BATTLE_FIELD;
        config.mapIndex = Maps.firstBattlefieldIndex();
        config.totalPlayers = 6;
        config.difficulty = Difficulty.ELITE;
        config.modifiers = MatchModifier.BUNNY_HOP.bit;
        config.seed = 721549L;
        match = new Match(config);
        // One reusable lane removes authored terrain and elevation as confounders. Every
        // scenario starts a fresh brain; the match's original brains are never advanced below.
        for (int z = 48; z <= 144; z++) for (int x = 100; x <= 540; x++)
            for (int y = 0; y < match.world().sizeY(); y++)
                match.world().set(x, y, z, y < 3 ? Materials.CONCRETE : Materials.AIR);
        match.world().commit();
        match.nav().refreshArea(match.world(), 100, 48, 540, 144);
        for (Combatant c : match.combatants()) c.alive = false;
        InputState idle = new InputState();
        for (int tick = 0; tick < 300 && match.state() == Match.State.COUNTDOWN; tick++)
            match.update(DT, idle);
        assertTrue(match.combatActive());
    }

    @AfterClass public static void releaseSharedTerrain() {
        if (match != null) match.release();
        if (flat != null) flat.release();
    }

    @Test public void riflePersonalitiesAreMixedEquallyOnBothAlternatingTeams() {
        int[] rushers = new int[2], cautious = new int[2];
        for (int pair = 0; pair < 20; pair++) {
            Combatant blue = soldier(pair * 2, Team.BLUE, WeaponType.AK15, 16f, 96f);
            Combatant red = soldier(pair * 2 + 1, Team.RED, WeaponType.AK15, 624f, 96f);
            assertEquals("opposing rifle pairs deserve the same personality mix",
                    BotCombatStyle.forCombatant(blue), BotCombatStyle.forCombatant(red));
            Combatant[] pairActors = {blue, red};
            for (int team = 0; team < pairActors.length; team++) {
                if (BotCombatStyle.forCombatant(pairActors[team]) == BotCombatStyle.RUSHER)
                    rushers[team]++;
                else if (BotCombatStyle.forCombatant(pairActors[team]) == BotCombatStyle.CAUTIOUS)
                    cautious[team]++;
            }
        }
        assertArrayEquals(rushers, cautious);
        assertTrue("both teams need aggressive riflemen", rushers[0] > 0 && rushers[1] > 0);
        assertTrue("both teams need cautious riflemen", cautious[0] > 0 && cautious[1] > 0);
    }

    @Test public void bothTeamsChooseUsableRearPerchesBehindTheirLivingInfantry() {
        for (Team team : TEAMS) {
            float direction = direction(team);
            Combatant sniper = soldier(team == Team.BLUE ? 2 : 5, team,
                    WeaponType.AWM_X, 320f - direction * 60f, 288f);
            Combatant infantry = soldier(team == Team.BLUE ? 4 : 1, team,
                    WeaponType.AK15, 320f - direction * 30f, 288f);
            List<Combatant> actors = new ArrayList<>();
            actors.add(sniper); actors.add(infantry);
            SquadCoordinator planner = new SquadCoordinator(nav, flat, actors, centralSector());
            int goal = planner.destinationFor(sniper);
            assertEquals(team + " keeps the marksman in overwatch",
                    SquadCoordinator.MemberTactic.OVERWATCH, planner.memberTacticFor(sniper));
            assertTrue(nav.valid(goal));
            float goalX = nav.cellX(goal) + .5f;
            assertTrue(team + " marksman perch crossed the infantry front: " + goalX,
                    direction * (infantry.position.x - goalX) >= 9f);
            assertTrue(team + " marksman perch is too close to the capture sector: " + goalX,
                    direction * (320.5f - goalX) >= 24f);
            assertTrue("overwatch must still belong to the active sector",
                    Math.abs(goalX - 320.5f) < 80f);
            assertFalse(flat.boxCollides(goalX, nav.nodeY(goal), nav.cellZ(goal) + .5f,
                    Combatant.RADIUS, Combatant.HEIGHT));
            assertTrue(flat.solid(nav.cellX(goal), nav.nodeY(goal) - 1, nav.cellZ(goal)));
            assertTrue(flat.lineOfSight(goalX, nav.nodeY(goal) + Combatant.EYE_HEIGHT,
                    nav.cellZ(goal) + .5f, 320.5f, 3f + Combatant.EYE_HEIGHT, 288.5f));
            int original = goal;
            for (int pass = 0; pass < 4; pass++) {
                planner.update(1.3f);
                assertEquals("stationary infantry must not make the perch oscillate",
                        original, planner.destinationFor(sniper));
                assertTrue(planner.supportSightQueries() <= 24);
            }
        }
    }

    @Test public void deadInfantryDoesNotPinTheOnlySurvivingSniperAtDeployment() {
        for (Team team : TEAMS) {
            float direction = direction(team);
            Combatant sniper = soldier(team == Team.BLUE ? 2 : 5, team,
                    WeaponType.AWM_X, 320f - direction * 40f, 288f);
            Combatant fallen = soldier(team == Team.BLUE ? 4 : 1, team,
                    WeaponType.AK15, team == Team.BLUE ? 16f : 624f, 288f);
            fallen.alive = false;
            List<Combatant> actors = new ArrayList<>();
            actors.add(sniper); actors.add(fallen);
            SquadCoordinator planner = new SquadCoordinator(nav, flat, actors, centralSector());
            float goalX = nav.cellX(planner.destinationFor(sniper)) + .5f;
            assertTrue(team + " failed to use the public sector when no infantry survived",
                    Math.abs(goalX - 320.5f) < 40f);
            assertTrue(team + " fallback crossed the capture sector",
                    direction * (320.5f - goalX) >= 20f);
        }
    }

    @Test public void movingSnipersDoNotBecomeInfantryLeadersOrFollowers() {
        for (Team team : TEAMS) {
            float direction = direction(team);
            Combatant sniper = soldier(team == Team.BLUE ? 2 : 5, team,
                    WeaponType.AWM_X, 320f - direction * 22f, 96f);
            sniper.velocity.x = direction * 8f;
            Combatant infantry = soldier(team == Team.BLUE ? 4 : 1, team,
                    WeaponType.BULLDOG_LMG, 320f - direction * 40f, 96f);
            Combatant follower = soldier(team == Team.BLUE ? 6 : 3, team,
                    WeaponType.BULLDOG_LMG, 320f - direction * 44f, 104f);
            List<Combatant> actors = new ArrayList<>();
            actors.add(sniper); actors.add(infantry); actors.add(follower);
            SquadCoordinator planner = new SquadCoordinator(nav, flat, actors, null);
            assertEquals(-1, planner.supportLeaderFor(sniper));
            assertNotEquals(team + " infantry followed the sniper into a marksman position",
                    sniper.id, planner.supportLeaderFor(infantry));
            assertNotEquals(team + " second infantry member followed the sniper",
                    sniper.id, planner.supportLeaderFor(follower));
            assertEquals(SquadCoordinator.MemberTactic.OVERWATCH, planner.memberTacticFor(sniper));
        }
    }

    @Test public void bothTeamsPatrolToTheirRearPositionAndHoldWithoutTravelBhop() {
        for (Team team : TEAMS) {
            Scenario s = scenario(team);
            int perch = match.battlefront().destinationFor(s.sniper);
            place(s.sniper, match.nav().cellX(perch) + .5f - direction(team) * 24f,
                    match.nav().cellZ(perch) + .5f);
            float initialX = s.sniper.position.x;
            run(s, 720);
            assertTrue(team + " sniper never joined its infantry's rear line",
                    direction(team) * (s.sniper.position.x - initialX) > 20f);
            assertTrue(team + " sniper did not settle at its assigned perch",
                    distanceToPerch(s.sniper) < 1.5f);
            assertTrue(match.battlefront().shouldHold(s.sniper));
            assertTrue(team + " sniper moved past its own infantry",
                    direction(team) * (s.infantry.position.x - s.sniper.position.x) >= 9f);
            assertEquals(0f, (float) Math.hypot(s.sniper.velocity.x, s.sniper.velocity.z), .02f);
        }
    }

    @Test public void bothTeamsKeepMarksmanRetreatDistanceWithTheirPistolDrawn() {
        for (Team team : TEAMS) {
            Scenario s = scenario(team);
            placeAtPerch(s.sniper);
            place(s.enemy, s.sniper.position.x + direction(team) * 20f, s.sniper.position.z);
            s.sniper.selectSlot(WeaponSlot.SECONDARY.ordinal());
            // The sidearm remains selected long enough to observe locomotion independently of
            // the range-based weapon selector switching back to the rifle.
            s.sniper.switchTimer = 2f;
            float initial = horizontalDistance(s.sniper, s.enemy);
            run(s, 48);
            assertEquals(WeaponType.P9, s.sniper.weaponType());
            assertEquals(BotCombatStyle.SNIPER, s.brain.combatStyle());
            assertTrue(team + " sniper approached a 20m enemy as if it were a pistol rusher",
                    horizontalDistance(s.sniper, s.enemy) > initial + 2f);
            assertFalse(s.sniper.sprinting);
        }
    }

    @Test public void bothTeamsReturnToOverwatchInsteadOfChasingAVisibleEnemyBehindCover() {
        for (Team team : TEAMS) {
            Scenario s = scenario(team);
            placeAtPerch(s.sniper);
            place(s.enemy, s.sniper.position.x + direction(team) * 35f, s.sniper.position.z);
            int ammo = s.sniper.weapon().ammo();
            run(s, 120);
            assertTrue(team + " test never established a real visible target",
                    s.sniper.weapon().ammo() < ammo);
            float beforeX = s.sniper.position.x;
            wallX = (int) ((s.sniper.position.x + s.enemy.position.x) * .5f);
            wall(true);
            assertFalse(match.world().lineOfSight(s.sniper.eyeX(), s.sniper.eyeY(), s.sniper.eyeZ(),
                    s.enemy.eyeX(), s.enemy.eyeY(), s.enemy.eyeZ()));
            run(s, 240);
            assertTrue(team + " marksman chased the target's last known position through cover",
                    direction(team) * (s.sniper.position.x - beforeX) < 1.5f);
            assertTrue(team + " loss of sight abandoned the rear firing position",
                    distanceToPerch(s.sniper) < 1.5f);
            assertTrue(match.battlefront().shouldHold(s.sniper));
        }
    }

    @Test public void bothTeamsWatchNearbyGunfireWithoutAbandoningOverwatch() {
        for (Team team : TEAMS) {
            Scenario s = scenario(team);
            placeAtPerch(s.sniper);
            s.brain.hearGunshot(s.sniper.position.x + direction(team) * 30f,
                    s.sniper.position.y, s.sniper.position.z, 1f);
            run(s, 180);
            assertTrue(team + " marksman walked toward noise instead of covering its squad",
                    distanceToPerch(s.sniper) < 1.5f);
            assertTrue(match.battlefront().shouldHold(s.sniper));
        }
    }

    private static Combatant soldier(int id, Team team, WeaponType primary, float x, float z) {
        Combatant c = new Combatant(id, "Tactical test " + id);
        c.bot = true; c.team = team;
        c.setKit(primary, WeaponType.P9, WeaponType.KNIFE, null);
        c.applyKit();
        place(c, x, z);
        return c;
    }

    private static SquadCoordinator.ObjectiveField centralSector() {
        final int center = nav.nearestNode(320.5f, 3f, 288.5f);
        return new SquadCoordinator.ObjectiveField() {
            @Override public int count() { return 1; }
            @Override public int node(int index) { return center; }
            @Override public Team owner(int index) { return Team.NONE; }
            @Override public boolean contested(int index) { return false; }
        };
    }

    private static Scenario scenario(Team team) {
        if (wallX >= 0) { wall(false); wallX = -1; }
        for (Combatant c : match.combatants()) {
            c.alive = false;
            c.team = (c.id & 1) == 0 ? Team.BLUE : Team.RED;
            c.velocity.set(0f, 0f, 0f);
            c.health = c.armor = 100000f;
            c.lastDamageTime = 100f;
        }
        Combatant sniper = match.combatants().get(team == Team.BLUE ? 2 : 5);
        Combatant infantry = match.combatants().get(team == Team.BLUE ? 4 : 1);
        Combatant enemy = match.combatants().get(team == Team.BLUE ? 1 : 4);
        sniper.setKit(WeaponType.AWM_X, WeaponType.P9, WeaponType.KNIFE, null);
        sniper.applyKit();
        infantry.setKit(WeaponType.AK15, WeaponType.P9, WeaponType.KNIFE, null);
        infantry.applyKit();
        place(sniper, 320f - direction(team) * 80f, 96.5f);
        place(infantry, 320f - direction(team) * 20f, 96.5f);
        match.battlefront().update(1.3f);
        BotBrain brain = new BotBrain(sniper, Difficulty.ELITE, match, 156240L + team.ordinal());
        return new Scenario(sniper, infantry, enemy, brain);
    }

    private static void place(Combatant c, float x, float z) {
        c.position.set(x, 3f, z);
        c.velocity.set(0f, 0f, 0f);
        c.yaw = direction(c.team) * (float) Math.PI * .5f;
        c.pitch = 0f;
        c.grounded = true; c.alive = true;
        c.aiming = c.firing = c.crouching = c.sprinting = false;
        c.crouchBlend = c.switchTimer = 0f;
        c.airJumpUsed = false;
    }

    private static void placeAtPerch(Combatant c) {
        int n = match.battlefront().destinationFor(c);
        assertTrue(match.nav().valid(n));
        place(c, match.nav().cellX(n) + .5f, match.nav().cellZ(n) + .5f);
    }

    private static float distanceToPerch(Combatant c) {
        int n = match.battlefront().destinationFor(c);
        return (float) Math.hypot(c.position.x - match.nav().cellX(n) - .5f,
                c.position.z - match.nav().cellZ(n) - .5f);
    }

    private static float horizontalDistance(Combatant a, Combatant b) {
        return (float) Math.hypot(a.position.x - b.position.x, a.position.z - b.position.z);
    }

    private static float direction(Team team) { return team == Team.BLUE ? 1f : -1f; }

    private static void wall(boolean solid) {
        for (int z = 60; z <= 132; z++) for (int y = 3; y <= 8; y++)
            match.world().set(wallX, y, z, solid ? Materials.CONCRETE : Materials.AIR);
        match.world().commit();
        match.nav().refreshArea(match.world(), wallX - 1, 60, wallX + 1, 132);
    }

    /** Same stance, gravity and collision integration as Match, without advancing other brains. */
    private static void run(Scenario s, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            s.sniper.updateTimers(DT);
            match.battlefront().update(DT);
            match.pathFinder().beginStep(4);
            s.brain.update(DT);
            Combatant c = s.sniper;
            c.crouchBlend = MathUtil.clamp(
                    MathUtil.damp(c.crouchBlend, c.crouching ? 1f : 0f, 13f, DT), 0f, 1f);
            c.velocity.y = Math.max(Physics.TERMINAL_VELOCITY, c.velocity.y + Physics.GRAVITY * DT);
            c.grounded = Physics.move(match.world(), c.position, c.velocity, DT,
                    Combatant.RADIUS, c.height(), true);
            if (c.grounded) c.airJumpUsed = false;
            assertTrue(c.team + " marksman started travel bhop on an unobstructed flat lane", c.grounded);
            assertEquals(3f, c.position.y, .001f);
        }
    }

    private static final class Scenario {
        final Combatant sniper, infantry, enemy;
        final BotBrain brain;
        Scenario(Combatant sniper, Combatant infantry, Combatant enemy, BotBrain brain) {
            this.sniper = sniper; this.infantry = infantry; this.enemy = enemy; this.brain = brain;
        }
    }
}
