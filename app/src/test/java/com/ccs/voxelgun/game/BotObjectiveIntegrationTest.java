package com.ccs.voxelgun.game;

import com.ccs.voxelgun.math.Vec3;
import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.weapon.WeaponType;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.*;

/** Real brain, physics and objective rules with a controlled flat lane and visible opponents. */
public class BotObjectiveIntegrationTest {
    @Test public void bombWorkerFinishesPlantAndDefuseDespiteAnExistingFightAndMovement() throws Exception {
        try (Fixture f = new Fixture(GameMode.BOMB_PLANT)) {
            Combatant blue = f.activate(2, Team.BLUE, WeaponType.AK15, 60.5f, 32.5f);
            Combatant red = f.activate(1, Team.RED, WeaponType.AK15, 80.5f, 32.5f);
            red.firing = true;
            assertTrue(f.objectives.isInteractor(blue));
            BotBrain planter = f.engagedBrain(blue, red);
            blue.velocity.set(3f, 0f, 2f);
            blue.firing = blue.sprinting = blue.crouching = true;
            set(planter, "recoveryTime", .8f);
            set(planter, "recoveryX", 1f);
            int ammo = blue.weapon().ammo();
            for (int tick = 0; tick < 250 && !f.objectives.planted(); tick++) {
                f.step(planter, blue, true);
                assertFalse("the planter's existing target interrupted interaction", blue.firing);
                assertFalse(blue.sprinting);
                assertEquals(0f, blue.velocity.x, .001f);
                assertEquals(0f, blue.velocity.z, .001f);
                assertEquals(1f, blue.position.y, .001f);
            }
            assertTrue("the actual brain never completed its three-second plant", f.objectives.planted());
            assertEquals(100, blue.score);
            assertEquals("planting spent bullets", ammo, blue.weapon().ammo());

            f.place(red, 60.5f, 32.5f);
            f.place(blue, 40.5f, 32.5f);
            blue.firing = true;
            BotBrain defuser = f.engagedBrain(red, blue);
            red.velocity.set(-3f, 0f, 2f);
            red.firing = true;
            assertTrue(f.objectives.shouldInteract(red));
            for (int tick = 0; tick < 370 && !f.objectives.roundPaused(); tick++) {
                f.step(defuser, red, true);
                assertFalse("the defuser fired instead of finishing the interaction", red.firing);
                assertEquals(0f, red.velocity.x, .001f);
                assertEquals(0f, red.velocity.z, .001f);
            }
            assertTrue("the actual brain never completed its five-second defuse", f.objectives.roundPaused());
            assertEquals(150, red.score);
        }
    }

    @Test public void sniperCarrierMovesHomeWhileKeepingAVisibleOpponentAsItsCombatTarget() throws Exception {
        try (Fixture f = new Fixture(GameMode.CRYSTAL_HEIST)) {
            Combatant carrier = f.activate(2, Team.BLUE, WeaponType.AWM_X, 60.5f, 32.5f);
            Combatant enemy = f.activate(1, Team.RED, WeaponType.AK15, 100.5f, 32.5f);
            f.objectives.update(Match.STEP);
            assertEquals(carrier.id, f.objectives.carrierId());
            BotBrain brain = f.engagedBrain(carrier, enemy);
            float before = f.distanceTo(carrier, f.objectives.base(Team.BLUE));
            f.run(brain, carrier, 150);
            assertEquals(ObjectiveBotTactics.Role.CARRY, f.objectives.roleFor(carrier));
            assertEquals("marksman combat stole the carrier's home destination",
                    f.objectives.base(Team.BLUE), ((Integer) field(brain, "goalNode")).intValue());
            assertTrue("sniper carrier stopped at its preferred combat distance",
                    f.distanceTo(carrier, f.objectives.base(Team.BLUE)) < before - 7f);
            assertSame(enemy, field(brain, "target"));
            assertEquals("ENGAGE", field(brain, "state").toString());
        }
    }

    @Test public void escortMovesTowardItsCarrierInsteadOfChasingADistantVisibleEnemy() throws Exception {
        try (Fixture f = new Fixture(GameMode.CRYSTAL_HEIST)) {
            Combatant carrier = f.activate(2, Team.BLUE, WeaponType.AK15, 60.5f, 32.5f);
            Combatant escort = f.activate(4, Team.BLUE, WeaponType.AK15, 80.5f, 32.5f);
            Combatant enemy = f.activate(1, Team.RED, WeaponType.AK15, 112.5f, 32.5f);
            f.objectives.update(Match.STEP);
            assertEquals(carrier.id, f.objectives.carrierId());
            assertEquals(ObjectiveBotTactics.Role.ESCORT, f.objectives.roleFor(escort));
            int objective = f.objectives.goalFor(escort);
            BotBrain brain = f.engagedBrain(escort, enemy);
            float before = f.distanceTo(escort, objective);
            int ammo = escort.weapon().ammo();
            f.run(brain, escort, 120);
            assertEquals(objective, ((Integer) field(brain, "goalNode")).intValue());
            assertTrue("escort abandoned its carrier to fight in the opposite direction",
                    f.distanceTo(escort, objective) < before - 5f);
            assertTrue("escort lost its ability to provide moving cover fire", escort.weapon().ammo() < ammo);
            assertSame(enemy, field(brain, "target"));
        }
    }

    @Test public void interceptorFollowsThePublicCoreRouteWhileFightingAnUnrelatedEnemy() throws Exception {
        try (Fixture f = new Fixture(GameMode.CRYSTAL_HEIST)) {
            Combatant carrier = f.activate(2, Team.BLUE, WeaponType.AK15, 60.5f, 32.5f);
            f.activate(1, Team.RED, WeaponType.AK15, 85.5f, 41.5f);
            Combatant interceptor = f.activate(3, Team.RED, WeaponType.MP7K, 95.5f, 32.5f);
            Combatant enemy = f.activate(4, Team.BLUE, WeaponType.AK15, 115.5f, 32.5f);
            f.objectives.update(Match.STEP);
            assertEquals(carrier.id, f.objectives.carrierId());
            assertEquals(ObjectiveBotTactics.Role.INTERCEPT, f.objectives.roleFor(interceptor));
            int objective = f.objectives.goalFor(interceptor);
            assertTrue("fixture must exercise a waypoint ahead of the carrier",
                    f.match.nav().cellX(objective) < f.match.nav().cellX(f.objectives.node(0)) - 4);
            BotBrain brain = f.engagedBrain(interceptor, enemy);
            float before = f.distanceTo(interceptor, objective);
            int ammo = interceptor.weapon().ammo();
            f.run(brain, interceptor, 120);
            assertEquals(objective, ((Integer) field(brain, "goalNode")).intValue());
            assertTrue("a visible bystander pulled the interceptor away from the core route",
                    f.distanceTo(interceptor, objective) < before - 5f);
            assertTrue(interceptor.weapon().ammo() < ammo);
            assertSame(enemy, field(brain, "target"));
        }
    }

    @Test public void pickingUpTheCoreCancelsAnAlreadyActiveCoverRetreat() throws Exception {
        try (Fixture f = new Fixture(GameMode.CRYSTAL_HEIST)) {
            Combatant carrier = f.activate(2, Team.BLUE, WeaponType.AK15, 65.5f, 32.5f);
            Combatant enemy = f.activate(1, Team.RED, WeaponType.AK15, 100.5f, 32.5f);
            BotBrain brain = f.engagedBrain(carrier, enemy);
            Field state = BotBrain.class.getDeclaredField("state");
            state.setAccessible(true);
            for (Object value : state.getType().getEnumConstants()) if (value.toString().equals("REGROUP"))
                state.set(brain, value);
            BotCoverCycle cover = (BotCoverCycle) field(brain, "coverCycle");
            cover.start(f.match.nav().nearestNode(70.5f, 1f, 32.5f),
                    f.match.nav().nearestNode(71.5f, 1f, 32.5f), carrier, Difficulty.ELITE);
            set(brain, "coverTime", 5f);
            f.place(carrier, 60.5f, 32.5f);
            f.objectives.update(Match.STEP);
            assertEquals(carrier.id, f.objectives.carrierId());
            float before = f.distanceTo(carrier, f.objectives.base(Team.BLUE));
            f.run(brain, carrier, 90);
            assertFalse("a pickup preserved the previous cover order", cover.active());
            assertNotEquals("REGROUP", field(brain, "state").toString());
            assertTrue("the new carrier completed an unrelated retreat instead of delivering",
                    f.distanceTo(carrier, f.objectives.base(Team.BLUE)) < before - 4f);
        }
    }

    @Test public void losingSightOfAnEnemyDoesNotSendAnObjectiveCarrierOnAPersonalHunt() throws Exception {
        try (Fixture f = new Fixture(GameMode.CRYSTAL_HEIST)) {
            Combatant carrier = f.activate(2, Team.BLUE, WeaponType.AK15, 60.5f, 32.5f);
            Combatant enemy = f.activate(1, Team.RED, WeaponType.AK15, 100.5f, 32.5f);
            f.objectives.update(Match.STEP);
            BotBrain brain = f.engagedBrain(carrier, enemy);
            f.run(brain, carrier, 15);
            assertSame(enemy, field(brain, "target"));
            for (int z = 20; z <= 44; z++) for (int y = 1; y <= 7; y++)
                f.match.world().set(85, y, z, Materials.CONCRETE);
            f.match.world().commit();
            f.match.nav().refreshArea(f.match.world(), 85, 20, 85, 44);
            assertFalse(f.match.world().lineOfSight(carrier.eyeX(), carrier.eyeY(), carrier.eyeZ(),
                    enemy.eyeX(), enemy.eyeY(), enemy.eyeZ()));
            float before = f.distanceTo(carrier, f.objectives.base(Team.BLUE));
            f.run(brain, carrier, 120);
            assertNotEquals("delivery was replaced by an eight-second hunt for a hidden opponent",
                    "HUNT", field(brain, "state").toString());
            assertTrue("loss of a firing angle reversed delivery progress",
                    f.distanceTo(carrier, f.objectives.base(Team.BLUE)) < before - 5f);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Match match;
        final Objectives objectives;
        Fixture(GameMode mode) throws Exception {
            MatchConfig config = new MatchConfig();
            config.mode = mode;
            config.botCount = 4;
            config.difficulty = Difficulty.ELITE;
            config.modifiers = MatchModifier.BUNNY_HOP.bit;
            config.seed = 4242L;
            match = new Match(config);
            objectives = match.objectives();
            set(match, "state", Match.State.PLAYING);
            for (int x = 10; x <= 118; x++) for (int z = 20; z <= 44; z++)
                for (int y = 0; y < match.world().sizeY(); y++)
                    match.world().set(x, y, z, y == 0 ? Materials.CONCRETE : Materials.AIR);
            match.world().commit();
            match.nav().refreshArea(match.world(), 10, 20, 118, 44);
            int center = match.nav().nearestNode(60.5f, 1f, 32.5f);
            ((int[]) field(objectives, "nodes"))[0] = center;
            set(objectives, "blueBase", match.nav().nearestNode(20.5f, 1f, 32.5f));
            set(objectives, "redBase", match.nav().nearestNode(110.5f, 1f, 32.5f));
            ((Vec3) field(objectives, "core")).set(60.5f, 1f, 32.5f);
            set(objectives, "planned", false);
            for (Combatant c : match.combatants()) c.alive = false;
        }

        Combatant activate(int id, Team team, WeaponType weapon, float x, float z) {
            Combatant c = match.combatants().get(id);
            c.team = team;
            c.setLoadout(weapon, null, WeaponType.KNIFE, null);
            c.health = c.armor = 100000f;
            c.alive = true;
            place(c, x, z);
            return c;
        }

        void place(Combatant c, float x, float z) {
            c.position.set(x, 1f, z);
            c.velocity.set(0f, 0f, 0f);
            c.yaw = (float) Math.PI * .5f;
            c.pitch = 0f;
            c.grounded = true;
            c.switchTimer = c.crouchBlend = 0f;
            c.sprinting = c.crouching = c.firing = c.aiming = false;
            c.lastDamageTime = 100f;
        }

        BotBrain engagedBrain(Combatant c, Combatant enemy) throws Exception {
            BotBrain brain = new BotBrain(c, Difficulty.ELITE, match, 1242L + c.id);
            Field state = BotBrain.class.getDeclaredField("state");
            state.setAccessible(true);
            for (Object value : state.getType().getEnumConstants()) if (value.toString().equals("ENGAGE"))
                state.set(brain, value);
            set(brain, "target", enemy);
            set(brain, "reactionTimer", 0f);
            set(brain, "trackedTime", 2f);
            ((Vec3) field(brain, "lastKnown")).set(enemy.position);
            return brain;
        }

        void run(BotBrain brain, Combatant c, int steps) {
            for (int i = 0; i < steps; i++) step(brain, c, true);
        }

        void step(BotBrain brain, Combatant c, boolean rules) {
            c.updateTimers(Match.STEP);
            match.pathFinder().beginStep(8);
            match.tacticalBudget().beginStep(6);
            brain.update(Match.STEP);
            c.velocity.y = Math.max(Physics.TERMINAL_VELOCITY,
                    c.velocity.y + Physics.GRAVITY * Match.STEP);
            c.grounded = Physics.move(match.world(), c.position, c.velocity, Match.STEP,
                    Combatant.RADIUS, c.height(), true);
            if (c.grounded) c.airJumpUsed = false;
            if (rules) objectives.update(Match.STEP);
        }

        float distanceTo(Combatant c, int node) {
            return (float) Math.hypot(c.position.x - match.nav().cellX(node) - .5f,
                    c.position.z - match.nav().cellZ(node) - .5f);
        }

        @Override public void close() { match.release(); }
    }

    private static Object field(Object instance, String name) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }
    private static void set(Object instance, String name, Object value) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }
}
