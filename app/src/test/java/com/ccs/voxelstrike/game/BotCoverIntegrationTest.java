package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.input.InputState;
import com.ccs.voxelstrike.math.Vec3;
import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.weapon.WeaponType;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.*;

/** Real brain, weapon timers and collision exercise the hide/peek/return integration. */
public class BotCoverIntegrationTest {
    @Test public void realMatchReloadsBehindCoverThenPeeksFiresAndReturns() throws Exception {
        try (Fixture f = new Fixture()) {
            while (f.bot.weapon().ammo() > f.bot.weaponType().magazine() / 2) f.bot.weapon().consume();
            f.start();
            boolean hiddenReload = false, peekShot = false, returned = false;
            for (int tick = 0; tick < 480 && !returned; tick++) {
                f.tick();
                if (f.cycle.hidden()) {
                    assertFalse("the brain must not shoot through its own shelter", f.bot.firing);
                    if (f.bot.weapon().reloading()) hiddenReload = true;
                    if (peekShot) returned = true;
                }
                if (f.bot.firing && f.cycle.canFire()) {
                    assertTrue("reload must complete before the peek can shoot", hiddenReload);
                    assertFalse(f.bot.weapon().reloading());
                    peekShot = true;
                }
            }
            assertTrue("the real brain never reached a hidden reload", hiddenReload);
            assertTrue("cover must still permit actual shots at the visible enemy", peekShot);
            assertTrue("exposure must end with physical return to shelter", returned);
        }
    }

    @Test public void finalHalfStepSettlesInsideTheSmallerCoverArrivalRadius() throws Exception {
        try (Fixture f = new Fixture()) {
            f.start();
            int cover = f.cycle.coverNode();
            float x = f.match.nav().cellX(cover) + .5f;
            float z = f.match.nav().cellZ(cover) + .5f;
            f.bot.position.set(x - .70f, 3f, z);
            f.bot.velocity.set(0f, 0f, 0f);
            for (int tick = 0; tick < 90 && !f.cycle.hidden(); tick++) f.tick();
            assertTrue("the waypoint's .75 radius must not strand the .6-radius cover phase",
                    f.cycle.hidden());
            assertTrue(Math.hypot(f.bot.position.x - x, f.bot.position.z - z) < .6f);
            assertFalse(f.bot.firing);
        }
    }

    @Test public void repeatedGunfireCannotReplaceAnActiveHiddenReloadWithAHunt() throws Exception {
        try (Fixture f = new Fixture()) {
            while (f.bot.weapon().ammo() > f.bot.weaponType().magazine() / 2) f.bot.weapon().consume();
            f.start();
            int cover = f.cycle.coverNode();
            f.place(cover);
            f.tick();
            assertTrue(f.cycle.hidden());
            assertTrue(f.bot.weapon().reloading());
            // Legacy fallback time can expire before the longer, explicit cover cycle finishes.
            set(f.brain, "coverTime", 0f);
            f.brain.hearGunshot(f.enemy.position.x, f.enemy.position.y, f.enemy.position.z, 1f);
            assertEquals("audible hostile fire must not replace the active retreat route",
                    "REGROUP", field(f.brain, "state").toString());
            assertEquals(cover, ((Integer) field(f.brain, "goalNode")).intValue());
            assertTrue(f.cycle.hidden());
            assertFalse(f.bot.firing);
        }
    }

    @Test public void aDestroyedShieldCancelsCoverEvenThoughBothStandingNodesRemainValid()
            throws Exception {
        try (Fixture f = new Fixture()) {
            f.start();
            int cover = f.cycle.coverNode();
            f.place(cover);
            f.tick();
            assertTrue(f.cycle.hidden());
            BotPositionPlanner planner = (BotPositionPlanner) field(f.brain, "positions");
            assertTrue(planner.usable(cover));
            for (int y = 3; y < 9; y++) assertTrue(f.match.world().removeDestructible(63, y, 30));
            assertTrue("destroying the shield leaves the floor usable", planner.usable(cover));
            assertTrue(f.match.world().lineOfSight(f.bot.eyeX(), f.bot.eyeY(), f.bot.eyeZ(),
                    f.enemy.eyeX(), f.enemy.eyeY(), f.enemy.eyeZ()));
            f.match.tacticalBudget().beginStep(6);
            call(f.brain, "useCover", new Class<?>[]{float.class}, Match.STEP);
            assertFalse("a standable node is no longer cover when its shield disappears", f.cycle.active());
            assertEquals("PATROL", field(f.brain, "state").toString());
            assertEquals(-1, ((Integer) field(f.brain, "goalNode")).intValue());
            assertTrue(((BotRouteMemory) field(f.brain, "routeMemory")).cost(cover) > 0f);
            assertFalse(f.bot.firing);
            assertEquals(0f, f.bot.velocity.x, 0f);
            assertEquals(0f, f.bot.velocity.z, 0f);
        }
    }

    @Test public void staleNavigationCannotKeepCoverAfterTheFloorIsDestroyed() throws Exception {
        try (Fixture f = new Fixture()) {
            f.start();
            int cover = f.cycle.coverNode();
            f.place(cover);
            f.tick();
            int x = f.match.nav().cellX(cover), z = f.match.nav().cellZ(cover);
            f.match.world().set(x, 2, z, Materials.AIR);
            f.match.world().commit();
            assertTrue("deliberately leave the shared nav snapshot stale", f.match.nav().valid(cover));
            call(f.brain, "useCover", new Class<?>[]{float.class}, Match.STEP);
            assertFalse(f.cycle.active());
            assertEquals("PATROL", field(f.brain, "state").toString());
            assertEquals(-1, ((Integer) field(f.brain, "goalNode")).intValue());
            assertFalse(f.bot.firing);
        }
    }

    @Test public void enemyMovingBehindFreshWallCannotBeShotFromThePeek() throws Exception {
        try (Fixture f = new Fixture()) {
            f.start();
            int cover = f.cycle.coverNode();
            f.place(cover);
            for (int tick = 0; tick < 180 && !f.cycle.canFire(); tick++) f.tick();
            assertTrue("fixture must reach the exposed phase", f.cycle.canFire());
            assertTrue("the exposed position must initially have an actual firing lane",
                    f.match.world().lineOfSight(f.bot.eyeX(), f.bot.eyeY(), f.bot.eyeZ(),
                            f.enemy.eyeX(), f.enemy.eyeY() - .2f, f.enemy.eyeZ()));
            for (int z = 20; z <= 42; z++) for (int y = 3; y < 10; y++)
                f.match.world().set(70, y, z, Materials.CONCRETE);
            f.match.world().commit();
            // Simulate the interval before the next perception tick notices lost sight.
            set(f.brain, "lostTimer", 0f);
            int before = f.bot.weapon().ammo();
            call(f.brain, "useCover", new Class<?>[]{float.class}, Match.STEP);
            assertFalse("the final firing gate must check current geometry", f.bot.firing);
            assertEquals(before, f.bot.weapon().ammo());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Match match;
        final Combatant bot, enemy;
        final BotBrain brain;
        final BotCoverCycle cycle;
        final InputState idle = new InputState();

        Fixture() throws Exception {
            MatchConfig config = new MatchConfig();
            config.mode = GameMode.DEATHMATCH; config.botCount = 1;
            config.difficulty = Difficulty.ELITE; config.seed = 667L;
            match = new Match(config);
            for (int tick = 0; tick < 300 && !match.combatActive(); tick++) match.update(Match.STEP, idle);
            assertTrue(match.combatActive());
            for (int x = 50; x <= 99; x++) for (int z = 20; z <= 42; z++)
                for (int y = 0; y < match.world().sizeY(); y++)
                    match.world().set(x, y, z, y == 2 ? Materials.CONCRETE : Materials.AIR);
            for (int y = 3; y < 9; y++) match.world().set(63, y, 30, Materials.WOOD);
            match.world().commit(); match.nav().refreshArea(match.world(), 50, 20, 99, 42);
            bot = match.combatants().get(1); enemy = match.player();
            bot.setSingleWeapon(WeaponType.AK15); bot.team = Team.RED; enemy.team = Team.BLUE;
            bot.position.set(60.5f, 3f, 30.5f); bot.velocity.set(0f, 0f, 0f);
            bot.grounded = true; bot.crouching = false; bot.crouchBlend = 0f;
            bot.yaw = (float) Math.PI / 2f; bot.switchTimer = 0f;
            bot.health = 100f; bot.armor = 0f; bot.lastDamageTime = 100f;
            enemy.position.set(85.5f, 3f, 30.5f); enemy.velocity.set(0f, 0f, 0f);
            enemy.grounded = true; enemy.health = enemy.armor = 100000f;
            brain = ((BotBrain[]) field(match, "brainById"))[bot.id];
            brain.onRespawn();
            cycle = (BotCoverCycle) field(brain, "coverCycle");
        }

        void start() throws Exception {
            set(brain, "target", enemy);
            ((Vec3) field(brain, "lastKnown")).set(enemy.position);
            set(brain, "reactionTimer", 0f);
            set(brain, "trackedTime", 3f);
            match.pathFinder().beginStep(4); match.tacticalBudget().beginStep(8);
            assertTrue((Boolean) call(brain, "beginCover", new Class<?>[0]));
            Field state = BotBrain.class.getDeclaredField("state"); state.setAccessible(true);
            for (Object value : state.getType().getEnumConstants())
                if (value.toString().equals("REGROUP")) state.set(brain, value);
            assertEquals("REGROUP", state.get(brain).toString());
        }

        void place(int node) {
            bot.position.set(match.nav().cellX(node) + .5f, match.nav().nodeY(node),
                    match.nav().cellZ(node) + .5f);
            bot.velocity.set(0f, 0f, 0f); bot.grounded = true;
        }
        void tick() { match.update(Match.STEP, idle); }
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
