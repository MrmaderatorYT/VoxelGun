package com.ccs.voxelgun.game;

import com.ccs.voxelgun.input.InputState;
import com.ccs.voxelgun.map.MapBuilder;
import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.voxel.VoxelWorld;
import org.junit.Test;
import static org.junit.Assert.*;

public class SwimmingTest {
    private static final float DT = Match.STEP;

    private VoxelWorld pool() {
        VoxelWorld w = new VoxelWorld(24, 16, 24);
        MapBuilder b = new MapBuilder(w, 1L);
        b.fill(0, 0, 0, 23, 6, 23, Materials.CONCRETE);
        b.pool(4, 4, 18, 18, 6, 6); // Floor1, surface7, raised rim8.
        w.commit();
        return w;
    }

    private Combatant swimmer() {
        Combatant c = new Combatant(1, "Swimmer");
        c.position.set(12.5f, 1f, 12.5f);
        c.grounded = true;
        return c;
    }

    private void step(VoxelWorld w, Combatant c, boolean rise) {
        if (!Swimming.applyVertical(w, c, rise, DT))
            c.velocity.y = Math.max(Physics.TERMINAL_VELOCITY, c.velocity.y + Physics.GRAVITY * DT);
        c.grounded = Physics.move(w, c.position, c.velocity, DT,
                Combatant.RADIUS, c.height(), true);
        assertFalse("Swimming must never bypass body collision",
                w.boxCollides(c.position.x, c.position.y, c.position.z, Combatant.RADIUS, c.height()));
    }

    @Test public void heldJumpRisesFromThePoolFloorAndReleaseStopsPoweredAscent() {
        VoxelWorld w = pool();
        try {
            Combatant c = swimmer(); InputState input = new InputState();
            input.setDeviceJump(true);
            for (int i = 0; i < 30; i++) step(w, c, input.jumpHeld);
            assertTrue(c.position.y > 2.8f); assertTrue(Swimming.inWater(w, c));
            float y = c.position.y;
            input.setDeviceJump(false);
            for (int i = 0; i < 20; i++) step(w, c, input.jumpHeld);
            assertTrue("Release must not keep applying lift", c.position.y < y);
            assertTrue(c.velocity.y < 0);
        } finally { w.release(); }
    }

    @Test public void touchHeldJumpUsesTheSameWaterControl() {
        VoxelWorld w = pool();
        try {
            Combatant c = swimmer(); InputState input = new InputState();
            input.setTouchJump(true);
            for (int i = 0; i < 30; i++) step(w, c, input.jumpHeld);
            assertTrue(c.position.y > 2.8f);
            input.releaseAll();
            step(w, c, input.jumpHeld);
            assertTrue(c.velocity.y <= 0);
        } finally { w.release(); }
    }

    @Test public void dryGroundAndWaterBelowFeetDoNotEnableSwimming() {
        VoxelWorld w = pool();
        try {
            Combatant c = swimmer();
            c.position.set(19.5f, 8, 12.5f);
            assertFalse(Swimming.inWater(w, c));
            assertFalse(Swimming.applyVertical(w, c, true, DT));
            c.position.set(12.5f, 7.1f, 12.5f);
            assertFalse("Do not turn the air above a pool into water", Swimming.inWater(w, c));
            c.position.y = 6.9f;
            assertTrue("Partially immersed feet are enough", Swimming.inWater(w, c));
        } finally { w.release(); }
    }

    @Test public void ceilingBlocksAscentWithoutPenetration() {
        VoxelWorld w = pool();
        try {
            for (int x = 4; x <= 18; x++) for (int z = 4; z <= 18; z++)
                w.set(x, 5, z, Materials.CONCRETE);
            w.commit(); Combatant c = swimmer();
            for (int i = 0; i < 100; i++) step(w, c, true);
            assertTrue(c.position.y + c.height() <= 5);
        } finally { w.release(); }
    }

    @Test public void heldJumpAndMovementClearTheRaisedPoolRim() {
        VoxelWorld w = pool();
        try {
            Combatant c = swimmer(); c.position.x = 16.5f;
            for (int i = 0; i < 240 && (c.position.x < 20.5f || !c.grounded); i++) {
                Swimming.steer(c, Swimming.MOVE_SPEED, 0, DT);
                step(w, c, true);
            }
            assertTrue("Swim must get over a rim higher than the surface", c.position.x > 20);
            assertTrue(c.grounded); assertFalse(Swimming.inWater(w, c));
            assertTrue(c.position.y >= 7f);
        } finally { w.release(); }
    }

    @Test public void botEscapesDeepWaterWithoutAnyNavigationPath() {
        VoxelWorld w = pool();
        try {
            Combatant c = swimmer(); c.bot = true;
            BotSwimmingController controller = new BotSwimmingController();
            boolean escaped = false;
            for (int i = 0; i < 600; i++) {
                controller.update(c, w, DT); step(w, c, true);
                if (c.grounded && !Swimming.inWater(w, c)) { escaped = true; break; }
            }
            assertTrue("A pool floor six voxels below shore must not trap the bot", escaped);
            assertTrue(c.position.y >= 7f);
            assertFalse("Normal tactics resume on dry ground", controller.update(c, w, DT));
        } finally { w.release(); }
    }

    @Test public void tallWallsRemainImpassableForBotSwimmers() {
        VoxelWorld w = pool();
        try {
            for (int x = 3; x <= 19; x++) for (int z = 3; z <= 19; z++)
                if (x == 3 || x == 19 || z == 3 || z == 19)
                    for (int y = 7; y <= 11; y++) w.set(x, y, z, Materials.CONCRETE);
            w.commit(); Combatant c = swimmer();
            BotSwimmingController controller = new BotSwimmingController();
            for (int i = 0; i < 300; i++) { controller.update(c, w, DT); step(w, c, true); }
            assertTrue("Exploration must stay inside the sealed basin",
                    c.position.x > 4 && c.position.x < 19
                            && c.position.z > 4 && c.position.z < 19);
            assertTrue(Swimming.inWater(w, c) || !c.grounded);
            assertTrue(c.position.y < 9);
        } finally { w.release(); }
    }

    @Test public void botFindsAndEscapesAOneBlockBankOpening() {
        VoxelWorld w = pool();
        try {
            for (int x = 3; x <= 19; x++) for (int z = 3; z <= 19; z++)
                if (x == 3 || x == 19 || z == 3 || z == 19)
                    for (int y = 7; y <= 11; y++) w.set(x, y, z, Materials.CONCRETE);
            // Off-axis opening that a sparse directional shore search cannot see.
            for (int y = 8; y <= 11; y++) w.set(11, y, 19, Materials.AIR);
            w.commit(); Combatant c = swimmer();
            BotSwimmingController controller = new BotSwimmingController();
            boolean escaped = false;
            for (int i = 0; i < 600; i++) {
                controller.update(c, w, DT); step(w, c, true);
                if (c.grounded && !Swimming.inWater(w, c)) { escaped = true; break; }
            }
            assertTrue("A one-block opening must be found without an underwater A* route", escaped);
            assertEquals(11.5f, c.position.x, .4f);
            assertTrue(c.position.z > 19f);
        } finally { w.release(); }
    }

    @Test public void dryExitGraceEndsWithAnObservableTransitionBeforeLanding() {
        VoxelWorld w = pool();
        try {
            Combatant c = swimmer();
            BotSwimmingController controller = new BotSwimmingController();
            assertTrue(controller.update(c, w, DT));
            // Low gravity can leave a bot airborne longer than the shore-coasting grace.
            c.position.set(19.5f, 12f, 12.5f); c.grounded = false;
            assertTrue(controller.update(c, w, .8f));
            assertTrue(controller.active());
            assertFalse(controller.update(c, w, .06f));
            assertFalse(controller.active());
        } finally { w.release(); }
    }

    @Test public void botEscapesPirateLagoonEvenWithNoBankInTheLocalSearchRadius() {
        VoxelWorld w = new com.ccs.voxelgun.map.maps.PirateCove().build(1L);
        try {
            Combatant c = swimmer();
            c.position.set(124.5f, MapBuilder.GROUND, 3.5f);
            assertTrue(Swimming.inWater(w, c));
            BotSwimmingController controller = new BotSwimmingController();
            boolean escaped = false;
            for (int i = 0; i < 2400; i++) {
                controller.update(c, w, DT); step(w, c, true);
                if (c.grounded && !Swimming.inWater(w, c)) { escaped = true; break; }
            }
            assertTrue("A far lagoon corner must not leave the bot bobbing in place", escaped);
        } finally { w.release(); }
    }
}
