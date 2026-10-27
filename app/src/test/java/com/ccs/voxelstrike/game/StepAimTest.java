package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.input.InputState;
import com.ccs.voxelstrike.map.MapBuilder;
import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.weapon.WeaponType;
import org.junit.Test;
import static org.junit.Assert.*;

/** Local attacks must originate at the visible viewpoint while a stair rise is being smoothed. */
public class StepAimTest {
    private Match steppedMatch(int modifiers, WeaponType primary, int bots) {
        MatchConfig config = new MatchConfig();
        config.mode = GameMode.RANGE;
        config.botCount = bots;
        config.modifiers = modifiers;
        config.primary = primary;
        config.seed = 4242L;
        Match match = new Match(config);
        InputState idle = new InputState();
        for (int i = 0; i < 210; i++) match.update(Match.STEP, idle);

        MapBuilder b = new MapBuilder(match.world(), 1L);
        b.fill(60, 0, 10, 110, 0, 30, Materials.CONCRETE);
        b.clear(60, 1, 10, 110, 20, 30);
        b.fill(70, 1, 20, 110, 1, 30, Materials.STEEL);
        match.world().commit();
        Combatant player = match.player();
        player.position.set(69.45f, 1f, 25.5f);
        player.velocity.set(0f, 0f, 0f);
        player.grounded = true;
        player.yaw = player.pitch = player.recoilYaw = player.recoilPitch = 0f;
        player.switchTimer = 0f;
        InputState walk = new InputState();
        walk.moveX = 1f;
        for (int i = 0; i < 90 && player.position.y < 1.9f; i++) {
            match.update(Match.STEP, walk);
        }
        assertEquals("the setup must physically climb one voxel", 2f, player.position.y, .0001f);
        assertTrue("the test must shoot during the visible stair recovery",
                player.eyeY() - match.cameraPosition.y > .5f);
        return match;
    }

    private Combatant target(Match match, float feetY) {
        Combatant target = match.combatants().get(1);
        target.position.set(match.player().position.x, feetY, 14f);
        target.velocity.set(0f, 0f, 0f);
        target.grounded = true;
        target.health = 100f;
        target.armor = 0f;
        target.alive = true;
        return target;
    }

    @Test public void levelPrecisionShotCannotClearCoverBlockingTheSmoothedView() {
        Match match = steppedMatch(MatchModifier.ONE_SHOT_RAIL.bit, WeaponType.SCOUT_V, 1);
        try {
            Combatant player = match.player();
            Combatant target = target(match, 2f);
            MapBuilder b = new MapBuilder(match.world(), 2L);
            // Three voxels of thickness prevent the sniper's intentional thin-wall penetration.
            b.fill(60, 1, 17, 110, 2, 19, Materials.CONCRETE);
            b.fill(60, 1, 12, 110, 1, 15, Materials.CONCRETE);
            match.world().commit();
            assertFalse(match.world().lineOfSight(player.position.x, match.cameraPosition.y,
                    player.position.z, target.position.x, match.cameraPosition.y, target.position.z));
            assertTrue("the old physical eye origin would incorrectly fire over this cover",
                    match.world().lineOfSight(player.position.x, player.eyeY(), player.position.z,
                            target.position.x, player.eyeY(), target.position.z));

            int ammo = player.weapon().ammo();
            InputState fire = new InputState();
            fire.fireHeld = true;
            match.update(Match.STEP, fire);
            assertEquals("the primary shot must actually be fired", ammo - 1, player.weapon().ammo());
            assertEquals("visible cover must stop the shot instead of exposing a hidden eye origin",
                    100f, target.health, .0001f);
            assertTrue(target.alive);
        } finally { match.release(); }
    }

    @Test public void precisionShotHitsTheVisibleLowerFloorTargetDuringStepRecovery() {
        Match match = steppedMatch(MatchModifier.ONE_SHOT_RAIL.bit, WeaponType.SCOUT_V, 1);
        try {
            Combatant player = match.player();
            Combatant target = target(match, 1f);
            // Aim at the head visible from the smoothed viewpoint. The physical eye is nearly
            // one voxel higher, so its parallel ray would pass completely over this target.
            player.pitch = (float) Math.atan2(target.eyeY() - match.cameraPosition.y,
                    player.position.z - target.position.z);
            InputState fire = new InputState();
            fire.fireHeld = true;
            match.update(Match.STEP, fire);
            assertFalse("a zero-spread shot at the visible target must connect", target.alive);
        } finally { match.release(); }
    }

    @Test public void quickGrenadeUsesTheVisibleEyeAtTheMomentOfTheThrow() {
        Match match = steppedMatch(0, WeaponType.AK15, 0);
        try {
            float visibleEye = match.cameraPosition.y;
            InputState input = new InputState();
            input.queueGrenade();
            match.update(Match.STEP, input);
            Projectile grenade = projectile(match, false);
            assertEquals(0, grenade.bounces);
            // The level throw has no vertical launch offset; account for its first physics tick.
            assertEquals(visibleEye, grenade.y - grenade.vy * Match.STEP, .0001f);
        } finally { match.release(); }
    }

    @Test public void rocketStartsAtTheSmoothedEyeAfterThePlayerMovementTick() {
        Match match = steppedMatch(0, WeaponType.RPG_V, 0);
        try {
            InputState input = new InputState();
            input.fireHeld = true;
            match.update(Match.STEP, input);
            Projectile rocket = projectile(match, true);
            // The launcher keeps its ordinary small spread. Undo the one-tick flight and the
            // muzzle's 0.8m forward offset using the resulting direction at the 42m/s speed.
            float launchEye = rocket.y - rocket.vy * Match.STEP - rocket.vy / 42f * .8f;
            assertEquals(match.cameraPosition.y, launchEye, .0001f);
            assertTrue("the projectile must not originate at the unsmoothed physical eye",
                    match.player().eyeY() - launchEye > .5f);
        } finally { match.release(); }
    }

    private Projectile projectile(Match match, boolean rocket) {
        for (Projectile p : match.projectiles()) {
            if (p.active && p.ownerId == match.player().id && p.rocket == rocket) return p;
        }
        throw new AssertionError(rocket ? "no player rocket was fired" : "no player grenade was thrown");
    }
}
