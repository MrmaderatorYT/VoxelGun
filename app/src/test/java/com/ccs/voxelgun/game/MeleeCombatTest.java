package com.ccs.voxelgun.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ccs.voxelgun.input.InputState;
import com.ccs.voxelgun.math.MathUtil;
import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.voxel.RayHit;
import com.ccs.voxelgun.voxel.VoxelWorld;
import com.ccs.voxelgun.weapon.WeaponSlot;
import com.ccs.voxelgun.weapon.WeaponType;

import org.junit.Test;

/** Regression coverage for the centre-ray knife controls and their movement interactions. */
public class MeleeCombatTest {
    private static Match createMatch() {
        MatchConfig config = new MatchConfig();
        config.mode = GameMode.RANGE;
        config.mapIndex = 0;
        config.botCount = 1;
        config.difficulty = Difficulty.RECRUIT;
        config.seed = 4242L;
        Match match = new Match(config);
        run(match, new InputState(), 3.4f);
        return match;
    }

    private static void run(Match match, InputState input, float seconds) {
        int steps = Math.round(seconds / Match.STEP);
        for (int i = 0; i < steps; i++) {
            match.update(Match.STEP, input);
        }
    }

    private static Combatant prepareKnifeDuel(Match match) {
        Combatant player = match.player();
        player.setSingleWeapon(WeaponType.KNIFE);
        player.slot = WeaponSlot.MELEE.ordinal();
        player.switchTimer = 0f;
        player.pitch = 0f;
        player.recoilPitch = 0f;
        player.recoilYaw = 0f;
        placePlayerInClearLane(match, player);

        Combatant target = match.combatants().get(1);
        target.team = Team.NONE;
        placeTarget(player, target, 2f, 0f);
        return target;
    }

    private static void placePlayerInClearLane(Match match, Combatant player) {
        NavGrid nav = match.nav();
        RayHit hit = new RayHit();
        float[] yaws = {0f, MathUtil.TAU * 0.25f, MathUtil.TAU * 0.5f,
                MathUtil.TAU * 0.75f};
        for (int z = 2; z < nav.depth() - 2; z++) {
            for (int x = 2; x < nav.width() - 2; x++) {
                for (int level = 0; level < nav.levelsAt(x, z); level++) {
                    int y = nav.nodeY(nav.node(x, z, level));
                    for (float yaw : yaws) {
                        float dx = (float) Math.sin(yaw);
                        float dz = -(float) Math.cos(yaw);
                        if (match.world().raycast(x + 0.5f, y + Combatant.EYE_HEIGHT, z + 0.5f,
                                dx, 0f, dz, 3f, false, hit)) {
                            continue;
                        }
                        player.position.set(x + 0.5f, y, z + 0.5f);
                        player.velocity.set(0f, 0f, 0f);
                        player.yaw = yaw;
                        player.grounded = true;
                        return;
                    }
                }
            }
        }
        throw new AssertionError("No clear three-metre melee lane on the test map");
    }

    private static void placeTarget(Combatant player, Combatant target, float forward, float side) {
        float forwardX = (float) Math.sin(player.yaw);
        float forwardZ = -(float) Math.cos(player.yaw);
        float rightX = (float) Math.cos(player.yaw);
        float rightZ = (float) Math.sin(player.yaw);
        target.position.set(player.position.x + forwardX * forward + rightX * side,
                player.position.y, player.position.z + forwardZ * forward + rightZ * side);
        target.velocity.set(0f, 0f, 0f);
        target.health = Combatant.MAX_HEALTH;
        target.armor = 0f;
        target.alive = true;
    }

    @Test
    public void knifeOnlyKillsWhenTheEnemyIntersectsTheCentrePlus() {
        Match match = createMatch();
        try {
            Combatant player = match.player();
            Combatant target = prepareKnifeDuel(match);
            placeTarget(player, target, 2f, 0.75f);

            assertTrue(match.tryFire(player, true));
            assertEquals("an enemy outside the centre plus was hit", Combatant.MAX_HEALTH,
                    target.health, 0.001f);

            player.weapon().releaseTrigger();
            player.weapon().update(1f);
            placeTarget(player, target, 2f, 0f);
            assertTrue(match.tryFire(player, true));
            assertFalse("an enemy under the centre plus survived", target.alive);
        } finally {
            match.release();
        }
    }

    @Test
    public void heldKnifeRetriesAfterAMissWithoutReleasingFire() {
        Match match = createMatch();
        try {
            Combatant player = match.player();
            Combatant target = prepareKnifeDuel(match);
            placeTarget(player, target, 2f, 0.75f);

            assertTrue(match.tryFire(player, true));
            assertTrue(target.alive);
            player.weapon().update(1f);

            placeTarget(player, target, 2f, 0f);
            assertTrue("the held knife did not swing again", match.tryFire(player, true));
            assertFalse(target.alive);
        } finally {
            match.release();
        }
    }

    @Test
    public void knifeCanKillWhileSprintingAndJumpingWithFireHeld() {
        Match match = createMatch();
        try {
            Combatant player = match.player();
            Combatant target = prepareKnifeDuel(match);
            InputState input = new InputState();
            input.setTouchMove(0f, 1f);
            input.setTouchSprint(true);
            input.setTouchFire(true);
            input.queueJump();

            match.update(Match.STEP, input);

            assertTrue("forward sprint was not active", player.sprinting);
            assertFalse("the jump did not leave the ground", player.grounded);
            assertFalse("the sprinting airborne knife attack was suppressed", target.alive);
        } finally {
            match.release();
        }
    }

    @Test
    public void knifeHitStopsAtAnOpaqueVoxel() {
        Match match = createMatch();
        try {
            Combatant player = match.player();
            Combatant target = prepareKnifeDuel(match);
            float dx = (float) Math.sin(player.yaw);
            float dz = -(float) Math.cos(player.yaw);
            int wallX = VoxelWorld.floor(player.eyeX() + dx);
            int wallY = VoxelWorld.floor(player.eyeY());
            int wallZ = VoxelWorld.floor(player.eyeZ() + dz);
            int previous = match.world().get(wallX, wallY, wallZ) & 0xFF;
            match.world().set(wallX, wallY, wallZ, Materials.CONCRETE);
            try {
                assertTrue(match.tryFire(player, true));
                assertEquals("the knife passed through an opaque voxel", Combatant.MAX_HEALTH,
                        target.health, 0.001f);
            } finally {
                match.world().set(wallX, wallY, wallZ, previous);
            }
        } finally {
            match.release();
        }
    }

    @Test
    public void queuedQuickMeleeRemainsAQuickStrikeOutsideBattlefield() {
        Match match = createMatch();
        try {
            Combatant player = match.player();
            Combatant target = prepareKnifeDuel(match);
            player.setLoadout(WeaponType.AK15, WeaponType.P9, WeaponType.KNIFE, WeaponType.FRAG);
            player.slot = WeaponSlot.PRIMARY.ordinal();
            player.switchTimer = 0f;

            InputState keyboard = new InputState();
            keyboard.queueMelee();
            match.update(Match.STEP, keyboard);

            assertEquals("quick melee switched away from the equipped rifle",
                    WeaponSlot.PRIMARY.ordinal(), player.slot);
            assertFalse("legacy melee queue did not perform its quick strike", target.alive);
        } finally {
            match.release();
        }
    }

    @Test
    public void selectingKnifeImmediatelyClearsSniperOptics() {
        Match match = createMatch();
        try {
            Combatant player = match.player();
            player.setLoadout(WeaponType.AWM_X, WeaponType.P9, WeaponType.KNIFE, WeaponType.FRAG);
            player.slot = WeaponSlot.PRIMARY.ordinal();
            player.switchTimer = 0f;
            InputState input = new InputState();
            input.setDeviceAim(true);
            run(match, input, 0.5f);
            assertTrue("the sniper scope never opened", match.scopeBlend > 0.5f);
            assertTrue("the sniper camera never zoomed", match.cameraFovScale < 1f);

            input.requestSlot(WeaponSlot.MELEE.ordinal());
            match.update(Match.STEP, input);
            run(match, input, 0.05f);

            assertEquals(0f, match.scopeBlend, 0.0001f);
            assertEquals(1f, match.cameraFovScale, 0.0001f);
            assertTrue(match.snapshot().melee);
            assertFalse(match.snapshot().hasScope);
            assertFalse(match.snapshot().scoped);
        } finally {
            match.release();
        }
    }
}
