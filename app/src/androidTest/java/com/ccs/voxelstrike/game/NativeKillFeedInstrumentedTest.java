package com.ccs.voxelstrike.game;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.ccs.voxelstrike.input.InputState;
import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.voxel.NativeBridge;
import com.ccs.voxelstrike.weapon.Weapon;
import com.ccs.voxelstrike.weapon.WeaponType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

/** Real JNI raycasts and sight checks; no activities, preferences, or simulated UI input. */
@RunWith(AndroidJUnit4.class)
public class NativeKillFeedInstrumentedTest {
    @Before public void nativeCoreMustBeLoaded() {
        assertTrue("These checks require the native voxel implementation", NativeBridge.available());
    }

    @Test public void nativeThinWallHitPublishesScopedHeadshotWallbang() throws Exception {
        Match match = arenaPatch();
        try {
            Combatant owner = match.player(), victim = match.combatants().get(1);
            owner.position.set(62.5f, 1f, 28.5f);
            owner.aiming = true;
            victim.position.set(68.5f, 1f, 28.5f);
            for (int y = 1; y < 5; y++) match.world().set(65, y, 28, Materials.CONCRETE);
            match.world().commit();
            assertFalse(match.world().lineOfSight(owner.eyeX(), owner.eyeY(), owner.eyeZ(),
                    victim.eyeX(), victim.eyeY(), victim.eyeZ()));
            Method fire = Match.class.getDeclaredMethod("fireRay", Combatant.class, WeaponType.class,
                    float.class, float.class, float.class, float.class, float.class, boolean.class);
            fire.setAccessible(true);
            fire.invoke(match, owner, WeaponType.AWM_X, 1f, 0f, 0f, 40f, 1000f, true);
            assertFalse(victim.alive);
            HudSnapshot snapshot = publish(match);
            assertEquals(1, snapshot.feedCount);
            assertEquals(WeaponType.AWM_X.ordinal(), snapshot.feedWeaponOrdinal[0]);
            assertEquals(KillContext.SCOPED | KillContext.HEADSHOT | KillContext.WALLBANG,
                    snapshot.feedFlags[0]);
            assertTrue(snapshot.feedInvolvesPlayer[0]);
        } finally { match.release(); }
    }

    @Test public void realGrenadeAndRocketSpawnsCaptureLaunchFlagsAndResetReusedSlot()
            throws Exception {
        Match match = arenaPatch();
        try {
            Combatant owner = match.player();
            owner.grounded = false;
            owner.flash = KillContext.FLASH_THRESHOLD;
            Projectile pooled = match.projectiles()[0];
            spawnGrenade(match, owner);
            assertTrue(pooled.active);
            assertEquals(WeaponType.FRAG, pooled.type);
            assertEquals(KillContext.AIRBORNE | KillContext.BLINDED, pooled.shotFlags);
            assertEquals(owner.id, pooled.ownerId);
            pooled.reset();
            assertEquals(0, pooled.shotFlags);
            assertFalse(pooled.active);

            owner.grounded = true;
            owner.flash = 0f;
            spawnRocket(match, owner);
            assertSame(pooled, match.projectiles()[0]);
            assertTrue(pooled.active);
            assertTrue(pooled.rocket);
            assertEquals(WeaponType.RPG_V, pooled.type);
            assertEquals(0, pooled.shotFlags); // No stale airborne/flash from the earlier grenade.
            pooled.active = false;
            owner.grounded = false;
            owner.flash = 1f;
            spawnRocket(match, owner);
            assertEquals(KillContext.AIRBORNE | KillContext.BLINDED, pooled.shotFlags);
        } finally { match.release(); }
    }

    @Test public void actualGrenadeRetainsLaunchTraitsAndAddsVictimFlashOnlyAtImpact()
            throws Exception {
        Match match = arenaPatch();
        try {
            Combatant owner = match.player(), victim = match.combatants().get(1);
            owner.position.set(78.5f, 1f, 28.5f);
            owner.grounded = false;
            owner.flash = 1f;
            spawnGrenade(match, owner);
            Projectile projectile = match.projectiles()[0];
            assertEquals(KillContext.AIRBORNE | KillContext.BLINDED, projectile.shotFlags);
            owner.grounded = true;
            owner.flash = 0f;
            victim.position.set(65.5f, 1f, 28.5f);
            victim.health = 1f;
            victim.flash = 0.8f;
            Method detonate = Match.class.getDeclaredMethod("detonate", Projectile.class,
                    float.class, float.class, float.class);
            detonate.setAccessible(true);
            detonate.invoke(match, projectile, 65.5f, 1.9f, 28.5f);
            assertFalse(victim.alive);
            assertTrue(owner.alive);
            assertTrue(owner.grounded);
            assertFalse(projectile.active);
            HudSnapshot snapshot = publish(match);
            assertEquals(1, snapshot.feedCount);
            assertEquals(WeaponType.FRAG.ordinal(), snapshot.feedWeaponOrdinal[0]);
            assertEquals(KillContext.AIRBORNE | KillContext.BLINDED | KillContext.VICTIM_BLINDED,
                    snapshot.feedFlags[0]);
            assertFalse(snapshot.feedHeadshot[0]);
        } finally { match.release(); }
    }

    @Test public void environmentFallUsesFallMetadataAndNotTheKnifeOrdinal() {
        Match match = arenaPatch();
        try {
            Combatant victim = match.combatants().get(1);
            match.applyDamage(victim, null, 1000f, false, WeaponType.KNIFE);
            assertFalse(victim.alive);
            HudSnapshot snapshot = publish(match);
            assertEquals(1, snapshot.feedCount);
            assertEquals("World", snapshot.feedAttacker[0]);
            assertEquals("Fall", snapshot.feedWeapon[0]);
            assertEquals(-1, snapshot.feedWeaponOrdinal[0]);
            assertEquals(0, snapshot.feedFlags[0]);
            assertFalse(snapshot.feedInvolvesPlayer[0]);
        } finally { match.release(); }
    }

    private Match arenaPatch() {
        MatchConfig config = new MatchConfig();
        config.mode = GameMode.RANGE;
        config.botCount = 2;
        config.seed = 918;
        Match match = new Match(config);
        for (int x = 60; x <= 81; x++) for (int z = 20; z <= 40; z++) {
            for (int y = 0; y < 30; y++) {
                match.world().set(x, y, z, y == 0 ? Materials.CONCRETE : Materials.AIR);
            }
        }
        match.world().commit();
        for (Combatant c : match.combatants()) {
            c.position.set(79.5f, 1f, 38.5f);
            c.health = Combatant.MAX_HEALTH;
            c.armor = 0f;
            c.flash = 0f;
            c.grounded = true;
            c.crouchBlend = 0f;
        }
        return match;
    }

    private void spawnGrenade(Match match, Combatant owner) throws Exception {
        Method method = Match.class.getDeclaredMethod("spawnGrenade", Combatant.class,
                WeaponType.class);
        method.setAccessible(true);
        method.invoke(match, owner, WeaponType.FRAG);
    }

    private void spawnRocket(Match match, Combatant owner) throws Exception {
        Method method = Match.class.getDeclaredMethod("spawnRocket", Combatant.class,
                WeaponType.class, Weapon.class);
        method.setAccessible(true);
        method.invoke(match, owner, WeaponType.RPG_V, new Weapon(WeaponType.RPG_V));
    }

    private HudSnapshot publish(Match match) {
        InputState input = new InputState();
        input.paused = true;
        match.update(0.04f, input);
        return match.snapshot();
    }
}
