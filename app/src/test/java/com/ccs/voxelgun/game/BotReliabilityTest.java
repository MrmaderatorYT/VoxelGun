package com.ccs.voxelgun.game;

import com.ccs.voxelgun.input.InputState;
import com.ccs.voxelgun.math.MathUtil;
import com.ccs.voxelgun.math.Vec3;
import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.weapon.Weapon;
import com.ccs.voxelgun.weapon.WeaponType;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.*;

/** Regression scenarios exercise the real FSM, collision, sound dispatch and weapon timers. */
public class BotReliabilityTest {
    @Test public void repeatedSoundPreservesRouteAndInvestigationDeadline() throws Exception {
        try (Fixture f = new Fixture(GameMode.DEATHMATCH, 0)) {
            f.brain.hearGunshot(85.5f, 1f, 30.5f, 1f);
            f.match.pathFinder().beginStep(8);
            call(f.brain, "followPath", new Class<?>[]{float.class, float.class}, Match.STEP, 1f);
            PathFinder.Path path = (PathFinder.Path) field(f.brain, "path");
            assertFalse(path.finished());
            int node = path.currentNode();
            set(f.brain, "repathTimer", .7f);
            set(f.brain, "investigationTime", 2f);
            for (int i = 0; i < 50; i++) f.brain.hearGunshot(85.5f, 1f, 30.5f, 1f);
            assertEquals(.7f, number(f.brain, "repathTimer"), 0f);
            assertEquals(node, path.currentNode());
            ((BotHearing) field(f.brain, "hearing")).update(2f);
            f.brain.hearGunshot(85.5f, 1f, 30.5f, 1f);
            assertEquals("a later shot in the same burst must not renew the hunt", 2f,
                    number(f.brain, "investigationTime"), 0f);
            assertEquals(.7f, number(f.brain, "repathTimer"), 0f);
        }
    }

    @Test public void closerFootstepsOverrideADistantShotWithoutGrantingVision() throws Exception {
        try (Fixture f = new Fixture(GameMode.DEATHMATCH, 0)) {
            f.brain.hearGunshot(95.5f, 1f, 30.5f, 1f);
            Vec3 distant = new Vec3();
            distant.set((Vec3) field(f.brain, "heardPosition"));
            f.brain.hearFootstep(63.5f, 1f, 30.5f, 14f);
            Vec3 heard = (Vec3) field(f.brain, "heardPosition");
            assertTrue("a close danger overrides the earlier cue without waiting for cooldown",
                    Vec3.horizontalDistance(f.bot.position, heard)
                            < Vec3.horizontalDistance(f.bot.position, distant) - 20f);
            assertNull("sound gives a search region, never a visible enemy", field(f.brain, "target"));
            assertFalse(f.bot.firing);
            assertTrue("the region must remain imprecise", Math.hypot(heard.x - 63.5f, heard.z - 30.5f) > .5f);
        }
    }

    @Test public void acceptedBoundarySoundIsNotRejectedBecauseItsEstimateIsFartherAway()
            throws Exception {
        try (Fixture f = new Fixture(GameMode.DEATHMATCH, 0)) {
            boolean exercised = false;
            for (int sourceZ = 23; sourceZ <= 38; sourceZ++) {
                float x = 80.5f, z = sourceZ + .5f;
                float distance = (float) Math.hypot(x - f.bot.position.x, z - f.bot.position.z);
                float range = distance + .01f;
                BotHearing estimate = new BotHearing(9L);
                assertTrue(estimate.accept(f.bot, f.match.world(), x, 1f, z, range,
                        false, Difficulty.REGULAR));
                if (Math.hypot(estimate.x() - f.bot.position.x,
                        estimate.z() - f.bot.position.z) <= range) continue;
                f.brain.hearGunshot(x, 1f, z, range / 45f);
                assertEquals("audibility must use the actual source, not the noisy estimate",
                        "HUNT", field(f.brain, "state").toString());
                exercised = true;
                break;
            }
            assertTrue("fixture must exercise an outward localization error", exercised);
        }
    }

    @Test public void unreachableSoundExpiresWithoutSeeingAnEnemy() throws Exception {
        try (Fixture f = new Fixture(GameMode.DEATHMATCH, 0)) {
            f.wall(75);
            f.brain.hearGunshot(85.5f, 1f, 30.5f, 1f);
            assertEquals("HUNT", field(f.brain, "state").toString());
            // Keep a genuinely inaccessible destination, even if patrol could walk around the lane.
            for (int i = 0; i < 780; i++) {
                f.match.pathFinder().beginStep(4);
                f.brain.update(Match.STEP);
                f.bot.velocity.set(0f, 0f, 0f);
            }
            assertEquals("PATROL", field(f.brain, "state").toString());
        }
    }

    @Test public void friendlyGunfireDoesNotLureBotButHostileGunfireDoes() throws Exception {
        try (Fixture f = new Fixture(GameMode.DEATHMATCH, 0)) {
            BotBrain actual = ((BotBrain[]) field(f.match, "brainById"))[f.bot.id];
            actual.onRespawn();
            Combatant shooter = f.match.player();
            shooter.position.set(80.5f, 1f, 30.5f);
            shooter.team = f.bot.team = Team.BLUE;
            shooter.alive = true;
            shooter.yaw = (float) Math.PI / 2f;
            shooter.setSingleWeapon(WeaponType.AK15);
            shooter.switchTimer = 0f;
            assertTrue(f.match.tryFire(shooter, true));
            assertEquals("PATROL", field(actual, "state").toString());
            shooter.team = Team.RED;
            shooter.weapon().update(1f);
            assertTrue(f.match.tryFire(shooter, true));
            assertEquals("HUNT", field(actual, "state").toString());
        }
    }

    @Test public void stepsRespectDistancePostureAndPassiveTargets() throws Exception {
        try (Fixture f = new Fixture(GameMode.DEATHMATCH, 0)) {
            f.brain.hearFootstep(70.5f, 1f, 30.5f, 2.5f);
            assertEquals("PATROL", field(f.brain, "state").toString());
            f.brain.hearFootstep(70.5f, 1f, 30.5f, 14f);
            assertEquals("HUNT", field(f.brain, "state").toString());
        }
        try (Fixture f = new Fixture(GameMode.RANGE, 0)) {
            float yaw = f.bot.yaw;
            f.brain.hearGunshot(61f, 1f, 30f, 1f);
            f.brain.hearFootstep(61f, 1f, 30f, 14f);
            for (int i = 0; i < 120; i++) f.brain.update(Match.STEP);
            assertEquals("PATROL", field(f.brain, "state").toString());
            assertEquals(yaw, f.bot.yaw, 0f);
            assertEquals(0f, f.bot.velocity.lengthSquared(), 0f);
            assertFalse(f.bot.firing);
        }
    }

    @Test public void objectiveGuardWatchesAFiniteSector() throws Exception {
        try (Fixture f = new Fixture(GameMode.KING_OF_THE_HILL, 0)) {
            int goal = f.match.objectives().goalFor(f.bot);
            f.bot.position.set(f.match.nav().cellX(goal) + .5f, f.match.nav().nodeY(goal),
                    f.match.nav().cellZ(goal) + .5f);
            assertTrue(f.match.objectives().shouldHold(f.bot));
            int enemyBase = f.match.objectives().base(f.bot.team == Team.BLUE ? Team.RED : Team.BLUE);
            float center = (float) Math.atan2(f.match.nav().cellX(enemyBase) + .5f - f.bot.position.x,
                    f.bot.position.z - f.match.nav().cellZ(enemyBase) - .5f);
            f.bot.yaw = center;
            for (int i = 0; i < 1500; i++) {
                call(f.brain, "patrol", new Class<?>[]{float.class}, Match.STEP);
                assertTrue("guard should not spin around its own axis",
                        Math.abs(MathUtil.wrapAngle(f.bot.yaw - center)) < .65f);
                assertEquals(0f, f.bot.velocity.x, 0f);
                assertEquals(0f, f.bot.velocity.z, 0f);
            }
        }
    }

    @Test public void realFootstepDispatchIgnoresAlliesAndQuietCrouching() throws Exception {
        try (Fixture f = new Fixture(GameMode.DEATHMATCH, 0)) {
            BotBrain actual = ((BotBrain[]) field(f.match, "brainById"))[f.bot.id];
            Combatant walker = f.match.player();
            walker.alive = true;
            f.bot.team = Team.RED;
            for (int scenario = 0; scenario < 3; scenario++) {
                actual.onRespawn();
                walker.position.set(70.5f, 1f, 30.5f);
                walker.velocity.set(0f, 0f, 5f);
                walker.grounded = true;
                walker.team = scenario == 0 ? Team.RED : Team.BLUE;
                walker.crouching = scenario == 1;
                walker.sprinting = !walker.crouching;
                walker.stepAccumulator = 3.2f;
                call(f.match, "integrateBody", new Class<?>[]{Combatant.class, float.class, boolean.class},
                        walker, Match.STEP, false);
                assertEquals(scenario == 2 ? "HUNT" : "PATROL", field(actual, "state").toString());
            }
        }
    }

    @Test public void recoveryStopsAtNewlyMissingFloorEvenWithSpeedModifiers() throws Exception {
        for (int modifiers : new int[]{0, MatchModifier.DOUBLE_SPEED.bit | MatchModifier.BUNNY_HOP.bit}) {
            try (Fixture f = new Fixture(GameMode.DEATHMATCH, modifiers)) {
                set(f.brain, "recoveryTime", .75f);
                set(f.brain, "recoveryX", 1f);
                set(f.brain, "recoveryZ", 0f);
                for (int x = 62; x < 68; x++) for (int z = 28; z < 33; z++)
                    f.match.world().set(x, 0, z, Materials.AIR);
                f.match.world().commit();
                f.bot.velocity.set(15f, 0f, 0f);
                f.match.player().alive = true;
                f.match.player().team = f.bot.team = Team.BLUE;
                f.match.player().position.set(60f, 1f, 30.5f);
                Object evasion = field(f.brain, "evasion");
                set(evasion, "activeLeft", .4f);
                set(evasion, "needsSelection", false);
                set(evasion, "directionX", 1f);
                set(evasion, "directionZ", 0f);
                f.match.pathFinder().beginStep(4);
                f.brain.update(Match.STEP);
                assertEquals("recovery must stop before unsupported terrain", 0f,
                        number(f.brain, "recoveryTime"), 0f);
                assertEquals(0f, f.bot.velocity.x, .001f);
                assertEquals(0f, f.bot.velocity.z, .001f);
                assertTrue(f.bot.grounded);
            }
        }
    }

    @Test public void realWallCollisionTriggersRecoveryAtOrdinaryPatrolSpeed() throws Exception {
        try (Fixture f = new Fixture(GameMode.DEATHMATCH, 0)) {
            f.wall(61);
            f.bot.position.x = 61f - Combatant.RADIUS - .01f;
            f.bot.setSingleWeapon(WeaponType.AK15);
            f.brain.onRespawn();
            boolean recovered = false;
            for (int i = 0; i < 100; i++) {
                call(f.brain, "steer", new Class<?>[]{float.class, float.class, float.class, float.class},
                        1f, 0f, Match.STEP, .9f);
                assertTrue("collision permits less than old intent threshold", f.bot.velocity.x < 1.2f);
                call(f.brain, "detectStuck", new Class<?>[]{float.class}, Match.STEP);
                recovered |= number(f.brain, "recoveryTime") > 0f;
                f.bot.velocity.y = -1f;
                f.bot.grounded = Physics.move(f.match.world(), f.bot.position, f.bot.velocity,
                        Match.STEP, Combatant.RADIUS, Combatant.HEIGHT, true);
                if (recovered) break;
            }
            assertTrue("blocked wish movement must trigger a safe local detour", recovered);
            f.brain.onRespawn();
            ((Vec3) field(f.brain, "desired")).set(0f, 0f, 0f);
            for (int i = 0; i < 120; i++)
                call(f.brain, "detectStuck", new Class<?>[]{float.class}, Match.STEP);
            assertEquals("intentional stillness is not a stall", 0f,
                    number(f.brain, "recoveryTime"), 0f);
        }
    }

    @Test public void repeatedAirborneBouncesStillTriggerRecovery() throws Exception {
        try (Fixture f = new Fixture(GameMode.DEATHMATCH, MatchModifier.BUNNY_HOP.bit
                | MatchModifier.DOUBLE_JUMP.bit | MatchModifier.LOW_GRAVITY.bit)) {
            f.wall(61);
            f.bot.position.x = 61f - Combatant.RADIUS - .01f;
            f.bot.position.y = 1.3f;
            f.brain.onRespawn();
            for (int i = 0; i < 60; i++) {
                f.bot.grounded = false;
                f.bot.position.y = 1.3f + .2f * (float) Math.sin(i * .2f);
                ((Vec3) field(f.brain, "desired")).set(8f, 0f, 0f);
                call(f.brain, "detectStuck", new Class<?>[]{float.class}, Match.STEP);
            }
            assertTrue("airborne oscillation must stop jump combos", number(f.brain, "hopSuppressionTime") > 0f);
            assertTrue("a wall has safe sideways exits", number(f.brain, "recoveryTime") > 0f);
            assertTrue(number(f.brain, "jumpCooldown") >= 2f);
            f.bot.velocity.set(0f, 0f, 0f);
            f.bot.grounded = true;
            call(f.brain, "steer", new Class<?>[]{float.class, float.class, float.class, float.class},
                    0f, 1f, Match.STEP, .9f);
            assertTrue("ordinary locomotion still works while hopping is suppressed", f.bot.velocity.z > 0f);
        }
    }

    @Test public void specialistCanFinishReloadAfterDrawingSidearm() throws Exception {
        for (WeaponType type : new WeaponType[]{WeaponType.AWM_X, WeaponType.RPG_V, WeaponType.PUMPER}) {
            try (Fixture f = new Fixture(GameMode.DEATHMATCH, 0)) {
                f.bot.setLoadout(type, WeaponType.P9, WeaponType.KNIFE, null);
                Weapon primary = f.bot.weapons[0];
                while (!primary.empty()) primary.consume();
                f.bot.slot = 1;
                f.bot.switchTimer = 0f;
                float distance = type == WeaponType.PUMPER ? 8f : 40f;
                for (int i = 0; i < 480 && primary.empty(); i++) {
                    f.bot.switchTimer = Math.max(0f, f.bot.switchTimer - Match.STEP);
                    set(f.brain, "weaponDecisionTimer", number(f.brain, "weaponDecisionTimer") - Match.STEP);
                    primary.update(Match.STEP);
                    f.brain.chooseCombatWeapon(distance);
                    if (f.bot.slot == 0) f.match.startReload(f.bot);
                }
                assertTrue(type + " must finish rather than cancel the reload repeatedly", primary.ammo() > 0);
                assertEquals(0, f.bot.slot);
            }
        }
    }

    @Test public void downtimeRestoresEmptyAssaultPrimary() throws Exception {
        try (Fixture f = new Fixture(GameMode.DEATHMATCH, 0)) {
            Weapon primary = f.bot.weapons[0];
            while (!primary.empty()) primary.consume();
            f.brain.chooseCombatWeapon(7f);
            assertEquals(1, f.bot.slot);
            for (int i = 0; i < 360 && primary.empty(); i++) {
                f.bot.switchTimer = Math.max(0f, f.bot.switchTimer - Match.STEP);
                set(f.brain, "weaponDecisionTimer", number(f.brain, "weaponDecisionTimer") - Match.STEP);
                primary.update(Match.STEP);
                call(f.brain, "manageWeapon", new Class<?>[0]);
            }
            assertEquals(0, f.bot.slot);
            assertTrue(primary.ammo() > 0);
        }
    }

    @Test public void dryShotgunKeepsLoadedSidearmUntilThereIsRoomToReload() throws Exception {
        try (Fixture f = new Fixture(GameMode.DEATHMATCH, 0)) {
            f.bot.setLoadout(WeaponType.PUMPER, WeaponType.P9, WeaponType.KNIFE, null);
            Weapon primary = f.bot.weapons[0];
            while (!primary.empty()) primary.consume();
            f.bot.slot = 1; f.bot.switchTimer = 0f;
            for (int i = 0; i < 120; i++) {
                set(f.brain, "weaponDecisionTimer", 0f);
                f.brain.chooseCombatWeapon(4f);
                assertEquals("keep a loaded defensive weapon at point blank", 1, f.bot.slot);
            }
            f.brain.chooseCombatWeapon(8f);
            assertEquals(0, f.bot.slot);
            f.bot.switchTimer = 0f;
            f.match.startReload(f.bot);
            assertTrue(primary.reloading());
            for (int i = 0; i < 300 && primary.empty(); i++) {
                set(f.brain, "weaponDecisionTimer", 0f);
                f.brain.chooseCombatWeapon(8f);
                primary.update(Match.STEP);
            }
            assertTrue(primary.ammo() > 0);
        }
    }

    @Test public void headshotMirrorKeepsKitButSelectsUsablePistol() throws Exception {
        try (Fixture f = new Fixture(GameMode.DEATHMATCH, MatchModifier.HEADSHOTS_ONLY.bit
                | MatchModifier.MIRROR_LOADOUT.bit)) {
            f.bot.setLoadout(WeaponType.RPG_V, WeaponType.P9, WeaponType.KNIFE, WeaponType.FRAG);
            f.bot.switchTimer = 0f;
            f.brain.chooseCombatWeapon(30f);
            assertEquals(1, f.bot.slot);
            assertEquals(WeaponType.RPG_V, f.bot.kit[0]);
            f.bot.switchTimer = 0f;
            set(f.brain, "weaponDecisionTimer", 0f);
            f.brain.chooseCombatWeapon(30f);
            call(f.brain, "manageWeapon", new Class<?>[0]);
            assertEquals(1, f.bot.slot);
            set(f.brain, "target", f.match.player());
            set(f.brain, "grenadeCooldown", 0f);
            int grenades = f.bot.weapons[3].ammo();
            call(f.brain, "maybeThrowGrenade", new Class<?>[]{float.class}, 20f);
            assertEquals(grenades, f.bot.weapons[3].ammo());
            f.match.player().alive = true;
            f.match.player().position.set(85.5f, 1f, 30.5f);
            f.bot.slot = 0; f.bot.switchTimer = 0f;
            set(f.brain, "weaponDecisionTimer", 0f);
            call(f.brain, "regroup", new Class<?>[]{float.class}, Match.STEP);
            assertEquals("retreat combat must also choose a useful weapon", 1, f.bot.slot);
        }
    }

    private static class Fixture implements AutoCloseable {
        final Match match;
        final Combatant bot;
        final BotBrain brain;
        Fixture(GameMode mode, int modifiers) throws Exception {
            MatchConfig config = new MatchConfig();
            config.mode = mode; config.botCount = 1; config.seed = 53261L; config.modifiers = modifiers;
            match = new Match(config);
            InputState idle = new InputState();
            for (int i = 0; i < 300 && !match.combatActive(); i++) match.update(Match.STEP, idle);
            assertTrue(match.combatActive());
            for (int x = 50; x < 100; x++) for (int z = 20; z < 42; z++)
                for (int y = 0; y < match.world().sizeY(); y++)
                    match.world().set(x, y, z, y == 0 ? Materials.CONCRETE : Materials.AIR);
            match.world().commit(); match.nav().refreshArea(match.world(), 50, 20, 99, 41);
            match.player().alive = false;
            bot = match.combatants().get(1);
            bot.setLoadout(WeaponType.AK15, WeaponType.P9, WeaponType.KNIFE, null);
            bot.position.set(60.5f, 1f, 30.5f); bot.velocity.set(0f, 0f, 0f);
            bot.grounded = true; bot.switchTimer = 0f;
            brain = new BotBrain(bot, Difficulty.REGULAR, match, 9L);
        }
        void wall(int x) {
            for (int z = 20; z < 42; z++) for (int y = 1; y < 8; y++)
                match.world().set(x, y, z, Materials.CONCRETE);
            match.world().commit(); match.nav().refreshArea(match.world(), x, 20, x, 41);
        }
        public void close() { match.release(); }
    }

    private static Object field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }
    private static float number(Object object, String name) throws Exception { return (Float) field(object, name); }
    private static void set(Object object, String name, Object value) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); f.set(object, value);
    }
    private static void call(Object object, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = object.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true); method.invoke(object, args);
    }
}
