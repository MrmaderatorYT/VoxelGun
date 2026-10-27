package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.voxel.VoxelWorld;
import com.ccs.voxelstrike.weapon.WeaponSlot;
import com.ccs.voxelstrike.weapon.WeaponType;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class SafeForwardSpawnTest {
    private VoxelWorld world;
    private NavGrid nav;
    private List<Combatant> actors;
    private Combatant player;
    private Field field;

    @Before public void makeWorld() {
        world = new VoxelWorld(192, 7, 160);
        for (int z = 0; z < world.sizeZ(); z++) for (int x = 0; x < world.sizeX(); x++)
            for (int y = 0; y < 3; y++) world.set(x, y, z, Materials.CONCRETE);
        world.commit(); nav = new NavGrid(world);
        actors = new ArrayList<>(); player = actor(0, Team.BLUE, 16, 80); actors.add(player);
        field = new Field();
    }

    @After public void release() { world.release(); }

    @Test public void controlledSectorUsesStandableRingAndSharedScratchCoordinates() {
        SafeForwardSpawn selector = selector();
        float[] first = selector.deploymentFor(player, field.node(0));
        assertNotNull(first);
        assertEquals(3f, first[1], 0f);
        assertTrue(Math.hypot(first[0] - 96.5f, first[2] - 96.5f) < 11);
        assertSame(first, selector.deploymentFor(player, field.node(0)));
        assertFalse(world.boxCollides(first[0], first[1], first[2], Combatant.RADIUS, Combatant.HEIGHT));
    }

    @Test public void neutralAndContestedSectorsCannotBeDeploymentAnchors() {
        SafeForwardSpawn selector = selector();
        field.owner = Team.NONE;
        assertNull(selector.deploymentFor(player, field.node(0)));
        field.owner = Team.BLUE; field.contested = true;
        assertNull(selector.deploymentFor(player, field.node(0)));
    }

    @Test public void nearbyEnemyAndDistantVisibleEnemyBothRejectForwardDeployment() {
        Combatant enemy = actor(1, Team.RED, 110, 96); actors.add(enemy);
        SafeForwardSpawn selector = selector();
        assertNull(selector.deploymentFor(player, field.node(0)));
        assertEquals("nearby rejection should not waste sight queries", 0, selector.sightQueries());
        enemy.position.set(185, 3, 96);
        assertNull(selector.deploymentFor(player, field.node(0)));
        assertTrue(selector.sightQueries() > 0);
    }

    @Test public void actualCoverCanMakeADistantEnemySafeWithoutIgnoringCollision() {
        actors.add(actor(1, Team.RED, 185, 96));
        for (int z = 0; z < world.sizeZ(); z++) for (int y = 3; y < world.sizeY(); y++)
            world.set(150, y, z, Materials.CONCRETE);
        world.set(104, 3, 96, Materials.CONCRETE); world.set(104, 4, 96, Materials.CONCRETE);
        world.commit();
        float[] spawn = selector().deploymentFor(player, field.node(0));
        assertNotNull(spawn);
        assertFalse(world.boxCollides(spawn[0], spawn[1], spawn[2], Combatant.RADIUS, Combatant.HEIGHT));
        assertFalse(world.lineOfSight(spawn[0], spawn[1] + Combatant.EYE_HEIGHT, spawn[2],
                185, 3 + Combatant.EYE_HEIGHT, 96));
    }

    @Test public void longRangeSniperExposureIsRejectedEvenWhileTheSniperHoldsAKnife() {
        Combatant enemy = actor(1, Team.RED, 190, 1);
        enemy.setLoadout(WeaponType.AWM_X, WeaponType.P9, WeaponType.KNIFE, null);
        enemy.slot = WeaponSlot.MELEE.ordinal(); actors.add(enemy);
        SafeForwardSpawn selector = selector();
        assertNull(selector.deploymentFor(player, field.node(0)));
        assertTrue(selector.sightQueries() > 0);
    }

    @Test public void visibilityBudgetIsBoundedAndExhaustionFailsClosed() {
        for (int z = 0; z < world.sizeZ(); z++) for (int y = 3; y < world.sizeY(); y++)
            world.set(140, y, z, Materials.CONCRETE);
        world.commit();
        for (int i = 0; i < 40; i++) actors.add(actor(i * 2 + 1, Team.RED, 175, 95 + i % 3));
        SafeForwardSpawn selector = selector();
        assertNull("an incompletely checked candidate is not safe", selector.deploymentFor(player, field.node(0)));
        assertEquals(32, selector.sightQueries());
        assertTrue(selector.candidateQueries() <= 24);
    }

    @Test public void quietSquadMateWorksButUnderFireJumpingAndHomeCampingDoNot() {
        SafeForwardSpawn selector = new SafeForwardSpawn(nav, world, actors, null);
        Combatant mate = actor(2, Team.BLUE, 96, 96); actors.add(mate);
        mate.lastDamageTime = 20;
        assertNotNull(selector.deploymentFor(player, -1));
        mate.lastDamageTime = 0;
        assertNull(selector.deploymentFor(player, -1));
        mate.lastDamageTime = 20; mate.position.y = 4;
        assertNull(selector.deploymentFor(player, -1));
        mate.position.y = 3; mate.velocity.y = 3;
        assertNull(selector.deploymentFor(player, -1));
        mate.velocity.y = 0; mate.position.x = 20;
        assertNull(selector.deploymentFor(player, -1));
        mate.position.x = 96; mate.firing = true;
        assertNull(selector.deploymentFor(player, -1));
    }

    private SafeForwardSpawn selector() { return new SafeForwardSpawn(nav, world, actors, field); }

    private static Combatant actor(int id, Team team, float x, float z) {
        Combatant c = new Combatant(id, "soldier"); c.team = team; c.bot = id != 0;
        c.position.set(x, 3, z); c.lastDamageTime = 20;
        return c;
    }

    private final class Field implements SquadCoordinator.ObjectiveField {
        private final int node = nav.nearestNode(96, 3, 96);
        Team owner = Team.BLUE;
        boolean contested;
        public int count() { return 1; }
        public int node(int i) { return node; }
        public Team owner(int i) { return owner; }
        public boolean contested(int i) { return contested; }
    }
}
