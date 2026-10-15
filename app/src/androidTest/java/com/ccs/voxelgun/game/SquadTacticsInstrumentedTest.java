package com.ccs.voxelgun.game;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.voxel.NativeBridge;
import com.ccs.voxelgun.voxel.VoxelWorld;
import com.ccs.voxelgun.weapon.WeaponType;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** Deployment safety must agree with the real JNI collision/visibility implementation. */
@RunWith(AndroidJUnit4.class)
public class SquadTacticsInstrumentedTest {
    @Test public void nativeSightRejectsExposureAndAcceptsCoveredControlledSector() {
        assertTrue(NativeBridge.available());
        VoxelWorld world = flatWorld();
        try {
            NavGrid nav = new NavGrid(world);
            List<Combatant> actors = new ArrayList<>();
            Combatant player = actor(0, Team.BLUE, 16, 80), enemy = actor(1, Team.RED, 185, 96);
            actors.add(player); actors.add(enemy);
            SquadCoordinator.ObjectiveField field = field(nav);
            SafeForwardSpawn selector = new SafeForwardSpawn(nav, world, actors, field);
            assertNull(selector.deploymentFor(player, field.node(0)));
            for (int z = 0; z < world.sizeZ(); z++) for (int y = 3; y < world.sizeY(); y++)
                world.set(150, y, z, Materials.CONCRETE);
            world.commit();
            float[] spawn = selector.deploymentFor(player, field.node(0));
            assertNotNull(spawn);
            assertFalse(world.boxCollides(spawn[0], spawn[1], spawn[2], Combatant.RADIUS, Combatant.HEIGHT));
            assertFalse(world.lineOfSight(spawn[0], spawn[1] + Combatant.EYE_HEIGHT, spawn[2],
                    enemy.eyeX(), enemy.eyeY(), enemy.eyeZ()));
            assertTrue(selector.sightQueries() <= 32);
        } finally { world.release(); }
    }

    @Test public void nativeAutonomousSupportUsesDifferentCollisionFreeSlots() {
        assertTrue(NativeBridge.available());
        VoxelWorld world = flatWorld();
        try {
            NavGrid nav = new NavGrid(world);
            List<Combatant> actors = new ArrayList<>();
            Combatant player = actor(0, Team.BLUE, 16, 140);
            Combatant leader = actor(4, Team.BLUE, 90, 26);
            Combatant left = actor(2, Team.BLUE, 76, 20), right = actor(6, Team.BLUE, 76, 32);
            leader.setKit(WeaponType.SPECTRE, null, null, null);
            leader.velocity.set(8, 0, 0);
            left.setKit(WeaponType.BULLDOG_LMG, null, null, null);
            right.setKit(WeaponType.BULLDOG_LMG, null, null, null);
            actors.add(player); actors.add(left); actors.add(leader); actors.add(right);
            SquadCoordinator squads = new SquadCoordinator(nav, world, actors, null);
            assertEquals("the actual moving bot, not the idle player, earns support", leader.id,
                    squads.supportLeaderFor(left));
            assertEquals(leader.id, squads.supportLeaderFor(right));
            int a = squads.destinationFor(left), b = squads.destinationFor(right);
            assertNotEquals(a, b);
            assertEquals(SquadCoordinator.MemberTactic.SUPPORT, squads.memberTacticFor(left));
            assertEquals(SquadCoordinator.Role.ATTACK, squads.roleFor(left));
            assertFalse(world.boxCollides(nav.cellX(a) + .5f, nav.nodeY(a), nav.cellZ(a) + .5f,
                    Combatant.RADIUS, Combatant.HEIGHT));
            assertFalse(world.boxCollides(nav.cellX(b) + .5f, nav.nodeY(b), nav.cellZ(b) + .5f,
                    Combatant.RADIUS, Combatant.HEIGHT));
            leader.velocity.set(0, 0, 0);
            assertEquals("a stopped ally must not become an eternal body-guard assignment", -1,
                    squads.supportLeaderFor(left));
            assertNotEquals(SquadCoordinator.MemberTactic.SUPPORT, squads.memberTacticFor(left));
            assertTrue(nav.cellX(squads.destinationFor(left)) > 130);
        } finally { world.release(); }
    }

    @Test public void nativeWallBlocksAutonomousSupportAcrossDisconnectedSight() {
        assertTrue(NativeBridge.available());
        VoxelWorld world = flatWorld();
        try {
            NavGrid nav = new NavGrid(world);
            List<Combatant> actors = new ArrayList<>();
            Combatant follower = actor(2, Team.BLUE, 76, 26), leader = actor(4, Team.BLUE, 90, 26);
            follower.setKit(WeaponType.BULLDOG_LMG, null, null, null);
            leader.setKit(WeaponType.SPECTRE, null, null, null);
            leader.velocity.set(8, 0, 0);
            actors.add(actor(0, Team.BLUE, 16, 140));
            actors.add(follower); actors.add(leader);
            SquadCoordinator squads = new SquadCoordinator(nav, world, actors, null);
            assertEquals(leader.id, squads.supportLeaderFor(follower));
            // Real JNI visibility must prevent choosing an ally hidden behind newly built terrain.
            for (int z = 0; z < world.sizeZ(); z++) for (int y = 3; y < world.sizeY(); y++)
                world.set(84, y, z, Materials.CONCRETE);
            world.commit();
            assertFalse(world.lineOfSight(follower.eyeX(), follower.eyeY(), follower.eyeZ(),
                    leader.eyeX(), leader.eyeY(), leader.eyeZ()));
            squads.update(1.3f);
            assertEquals(-1, squads.supportLeaderFor(follower));
            assertNotEquals(SquadCoordinator.MemberTactic.SUPPORT, squads.memberTacticFor(follower));
        } finally { world.release(); }
    }

    private static VoxelWorld flatWorld() {
        VoxelWorld world = new VoxelWorld(192, 7, 160);
        for (int z = 0; z < world.sizeZ(); z++) for (int x = 0; x < world.sizeX(); x++)
            for (int y = 0; y < 3; y++) world.set(x, y, z, Materials.CONCRETE);
        world.commit(); return world;
    }

    private static Combatant actor(int id, Team team, float x, float z) {
        Combatant c = new Combatant(id, "soldier"); c.team = team; c.bot = id != 0;
        c.position.set(x, 3, z); c.lastDamageTime = 20;
        return c;
    }

    private static SquadCoordinator.ObjectiveField field(NavGrid nav) {
        final int center = nav.nearestNode(96, 3, 96);
        return new SquadCoordinator.ObjectiveField() {
            public int count() { return 1; }
            public int node(int i) { return center; }
            public Team owner(int i) { return Team.BLUE; }
            public boolean contested(int i) { return false; }
        };
    }
}
