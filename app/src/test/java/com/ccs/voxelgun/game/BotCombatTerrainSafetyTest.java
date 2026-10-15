package com.ccs.voxelgun.game;

import com.ccs.voxelgun.math.Vec3;
import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.weapon.WeaponType;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.*;

/** Combat steering must respect walkable terrain even when the enemy is plainly visible. */
public class BotCombatTerrainSafetyTest {
    @Test public void visiblePursuitUsesASafeNavigationDetourAroundLavaAndVoid() throws Exception {
        for (int material : new int[]{Materials.LAVA, Materials.AIR}) {
            try (Fixture f = new Fixture(0)) {
                f.hazard(62, 69, 28, 33, material);
                assertTrue("the firing lane remains open above the hazard",
                        f.match.world().lineOfSight(60.5f, 1.9f, 30.5f, 76.5f, 1.9f, 30.5f));
                f.moveToward(76.5f, 30.5f);
                PathFinder.Path path = (PathFinder.Path) field(f.brain, "path");
                assertTrue("a supported detour should be found", path.length() > 0);
                boolean routedAround = false;
                for (int i = 0; i < path.length(); i++) {
                    int n = path.node(i), x = f.match.nav().cellX(n), z = f.match.nav().cellZ(n);
                    assertEquals(Materials.CONCRETE, f.match.world().get(x, 0, z));
                    routedAround |= z < 28 || z >= 33;
                }
                assertTrue("the route must leave the blocked straight corridor", routedAround);
                assertEquals(f.match.nav().nearestNode(76.5f, 1f, 30.5f),
                        path.node(path.length() - 1));
            }
        }
    }

    @Test public void unsafePursuitWaitsWhenPathBudgetIsSpentInsteadOfWalkingIntoLava()
            throws Exception {
        try (Fixture f = new Fixture(MatchModifier.DOUBLE_SPEED.bit | MatchModifier.BUNNY_HOP.bit)) {
            f.hazard(62, 69, 28, 33, Materials.LAVA);
            f.match.pathFinder().beginStep(0);
            f.bot.velocity.set(14f, 0f, 0f);
            f.moveToward(76.5f, 30.5f);
            assertEquals(0f, f.bot.velocity.x, 0f);
            assertEquals(0f, f.bot.velocity.z, 0f);
            assertFalse(f.brain.diagnosticWantsMovement());
            int goal = (Integer) field(f.brain, "goalNode");
            assertTrue(goal >= 0);
            assertEquals(0, ((BotRouteMemory) field(f.brain, "routeMemory")).failures(goal));
        }
    }

    @Test public void strafeReversesAwayFromGroundLevelLavaAndVoidWithMovementModifiers()
            throws Exception {
        for (int modifiers : new int[]{0, MatchModifier.DOUBLE_SPEED.bit | MatchModifier.BUNNY_HOP.bit}) {
            for (int material : new int[]{Materials.LAVA, Materials.AIR}) {
                try (Fixture f = new Fixture(modifiers)) {
                    f.hazard(61, 66, 25, 36, material);
                    set(f.brain, "strafeTimer", 1f);
                    set(f.brain, "strafeDirection", 1f);
                    f.bot.yaw = 0f; // Requested strafe points east, into the hazard.
                    call(f.brain, "strafe", new Class<?>[]{float.class, float.class}, Match.STEP, .7f);
                    Vec3 desired = (Vec3) field(f.brain, "desired");
                    assertTrue("steering must reverse toward the supported side", desired.x < 0f);
                    assertEquals(-1f, (Float) field(f.brain, "strafeDirection"), 0f);
                    assertTrue(f.brain.diagnosticWantsMovement());
                    assertTrue(BotMovementSafety.canDodge(f.bot, f.match.world(), -1f, 0f, false));
                    assertFalse(BotMovementSafety.canDodge(f.bot, f.match.world(), 1f, 0f, false));
                }
            }
        }
    }

    @Test public void aNarrowBridgeAllowsAnIntentionalCombatHoldInsteadOfLateralSuicide()
            throws Exception {
        try (Fixture f = new Fixture(MatchModifier.DOUBLE_SPEED.bit | MatchModifier.BUNNY_HOP.bit)) {
            f.hazard(55, 60, 25, 36, Materials.LAVA);
            f.hazard(61, 68, 25, 36, Materials.AIR);
            set(f.brain, "strafeTimer", 1f); set(f.brain, "strafeDirection", 1f);
            f.bot.yaw = 0f; f.bot.velocity.set(12f, 0f, 0f);
            call(f.brain, "strafe", new Class<?>[]{float.class, float.class}, Match.STEP, .7f);
            assertEquals(0f, f.bot.velocity.x, 0f);
            assertEquals(0f, f.bot.velocity.z, 0f);
            assertFalse(f.brain.diagnosticWantsMovement());
            assertEquals(BotDiagnostics.COMBAT, f.brain.diagnosticHoldReason());
            assertTrue("safe holding must not manufacture a jump", f.bot.grounded);
        }
    }

    @Test public void directionChangeCannotReuseTheOppositeDirectionsSafeFloorProbe()
            throws Exception {
        try (Fixture f = new Fixture(0)) {
            f.hazard(61, 66, 25, 36, Materials.LAVA);
            f.moveToward(55.5f, 30.5f);
            assertTrue((Boolean) field(f.brain, "directPathClear"));
            assertTrue((Float) field(f.brain, "navigationProbeTimer") > 0f);
            ((Vec3) field(f.brain, "desired")).set(0f, 0f, 0f);
            f.moveToward(76.5f, 30.5f);
            assertTrue("a safe westward sample must still route a new eastward request around lava",
                    ((PathFinder.Path) field(f.brain, "path")).length() > 0);
            assertTrue((Integer) field(f.brain, "goalNode") >= 0);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Match match;
        final Combatant bot;
        final BotBrain brain;

        Fixture(int modifiers) {
            MatchConfig config = new MatchConfig();
            config.mode = GameMode.DEATHMATCH; config.botCount = 1;
            config.seed = 372L; config.modifiers = modifiers;
            match = new Match(config);
            for (int x = 50; x < 90; x++) for (int z = 20; z < 42; z++)
                for (int y = 0; y < match.world().sizeY(); y++)
                    match.world().set(x, y, z, y == 0 ? Materials.CONCRETE : Materials.AIR);
            match.world().commit(); match.nav().refreshArea(match.world(), 50, 20, 89, 41);
            bot = match.combatants().get(1); bot.setSingleWeapon(WeaponType.AK15);
            bot.position.set(60.5f, 1f, 30.5f); bot.velocity.set(0f, 0f, 0f); bot.grounded = true;
            brain = new BotBrain(bot, Difficulty.REGULAR, match, 373L);
            brain.onRespawn(); match.pathFinder().beginStep(4);
        }
        void hazard(int x0, int x1, int z0, int z1, int material) {
            for (int x = x0; x < x1; x++) for (int z = z0; z < z1; z++)
                match.world().set(x, 0, z, material);
            match.world().commit(); match.nav().refreshArea(match.world(), x0, z0, x1 - 1, z1 - 1);
        }
        void moveToward(float x, float z) throws Exception {
            call(brain, "moveToward", new Class<?>[]{float.class, float.class, float.class, float.class},
                    x, z, Match.STEP, 1f);
        }
        @Override public void close() { match.release(); }
    }

    private static Object field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); f.set(object, value);
    }
    private static Object call(Object object, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = object.getClass().getDeclaredMethod(name, types); m.setAccessible(true);
        return m.invoke(object, args);
    }
}
