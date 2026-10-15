package com.ccs.voxelgun.game;

import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.voxel.VoxelWorld;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/** Formation targets must still be real occupants of the underlying objective. */
public class ObjectiveFormationTest {
    @Test public void friendlyBotsGetStableDistributedSlotsInsideTheCaptureRadius() {
        VoxelWorld world = floor();
        try {
            NavGrid nav = new NavGrid(world);
            int center = nav.nearestNode(12.5f, 3f, 12.5f);
            Set<Integer> slots = new HashSet<>();
            for (int id = 2; id <= 16; id += 2) {
                Combatant c = bot(id);
                int slot = ObjectiveFormation.slot(nav, world, center, c, 4f);
                assertUsableSlot(nav, world, center, slot, 4f);
                slots.add(slot);
                c.position.set(20.5f, 3f, 20.5f);
                assertEquals("movement must not churn a bot's holding position", slot,
                        ObjectiveFormation.slot(nav, world, center, c, 4f));
            }
            assertTrue("friendly bots must not all target one shared approach node", slots.size() >= 6);
        } finally { world.release(); }
    }

    @Test public void humanObjectiveTargetRemainsItsCenter() {
        VoxelWorld world = floor();
        try {
            NavGrid nav = new NavGrid(world);
            int center = nav.nearestNode(12.5f, 3f, 12.5f);
            Combatant human = new Combatant(0, "player");
            human.team = Team.BLUE;
            assertEquals(center, ObjectiveFormation.slot(nav, world, center, human, 4f));
        } finally { world.release(); }
    }

    @Test public void blockedPreferredSlotGetsAClearAlternativeWithoutChangingTheObjective() {
        VoxelWorld world = floor();
        try {
            NavGrid nav = new NavGrid(world);
            int center = nav.nearestNode(12.5f, 3f, 12.5f);
            Combatant c = bot(2);
            int first = ObjectiveFormation.slot(nav, world, center, c, 4f);
            assertNotEquals("a clear field should offer a formation position", center, first);
            for (int y = 3; y <= 5; y++) {
                world.set(nav.cellX(first), y, nav.cellZ(first), Materials.CONCRETE);
            }
            world.commit();
            int alternative = ObjectiveFormation.slot(nav, world, center, c, 4f);
            assertNotEquals(first, alternative);
            assertUsableSlot(nav, world, center, alternative, 4f);
            assertEquals("selecting a slot must not rewrite the objective", 12, nav.cellX(center));
            assertEquals(12, nav.cellZ(center));
        } finally { world.release(); }
    }

    @Test public void smallInteractionRadiusNeverPushesABotOutsideItsObjective() {
        VoxelWorld world = floor();
        try {
            NavGrid nav = new NavGrid(world);
            int center = nav.nearestNode(12.5f, 3f, 12.5f);
            for (int id = 1; id <= 12; id++) {
                int slot = ObjectiveFormation.slot(nav, world, center, bot(id), 2.1f);
                assertUsableSlot(nav, world, center, slot, 2.1f);
            }
        } finally { world.release(); }
    }

    private static void assertUsableSlot(NavGrid nav, VoxelWorld world,
                                         int center, int slot, float radius) {
        assertTrue(nav.valid(slot));
        float x = nav.cellX(slot) + .5f;
        float z = nav.cellZ(slot) + .5f;
        float cx = nav.cellX(center) + .5f;
        float cz = nav.cellZ(center) + .5f;
        assertTrue("slot must count toward capture",
                Math.hypot(x - cx, z - cz) < radius);
        assertTrue(Math.abs(nav.nodeY(slot) - nav.nodeY(center)) < 2);
        assertFalse("slot must fit a standing fighter",
                world.boxCollides(x, nav.nodeY(slot), z, Combatant.RADIUS, Combatant.HEIGHT));
        assertTrue("slot must have capture line of sight", world.lineOfSight(cx,
                nav.nodeY(center) + 1f, cz, x, nav.nodeY(slot) + Combatant.EYE_HEIGHT, z));
    }

    private static Combatant bot(int id) {
        Combatant c = new Combatant(id, "bot-" + id);
        c.bot = true;
        c.team = Team.BLUE;
        c.position.set(3.5f, 3f, 3.5f);
        return c;
    }

    private static VoxelWorld floor() {
        VoxelWorld world = new VoxelWorld(26, 8, 26);
        for (int z = 0; z < 26; z++) for (int x = 0; x < 26; x++) {
            for (int y = 0; y < 3; y++) world.set(x, y, z, Materials.CONCRETE);
        }
        world.commit();
        return world;
    }
}
