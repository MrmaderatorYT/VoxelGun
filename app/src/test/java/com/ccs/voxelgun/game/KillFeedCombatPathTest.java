package com.ccs.voxelgun.game;

import com.ccs.voxelgun.input.InputState;
import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.weapon.WeaponType;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

/** Exercises real hitscan, penetration, melee and blast code, rather than badges alone. */
public class KillFeedCombatPathTest {
    @Test public void ownBlastImpulseDoesNotInventAirborneBadgeForGroundedThrower()
            throws Exception {
        Match match = arenaPatch();
        try {
            Combatant owner = match.player(), victim = match.combatants().get(1);
            owner.position.set(66.5f, 1f, 28.5f);
            owner.grounded = true;
            owner.health = 1000f;
            victim.position.set(65.5f, 1f, 28.5f);
            victim.health = 1f;
            victim.flash = 0.8f;
            Projectile projectile = grenade(match, owner);
            assertEquals(0, projectile.shotFlags);
            detonate(match, projectile, 65.5f, 1.9f, 28.5f);
            assertFalse(owner.grounded); // The owner was visited first and propelled by the blast.
            assertTrue(owner.alive);
            assertFalse(victim.alive);
            HudSnapshot snapshot = publish(match);
            assertEquals(1, snapshot.feedCount);
            assertEquals(WeaponType.FRAG.ordinal(), snapshot.feedWeaponOrdinal[0]);
            assertEquals(KillContext.VICTIM_BLINDED, snapshot.feedFlags[0]);
        } finally { match.release(); }
    }

    @Test public void detonationRetainsLaunchTraitsAfterOwnerLandsAndFlashExpires()
            throws Exception {
        Match match = arenaPatch();
        try {
            Combatant owner = match.player(), victim = match.combatants().get(1);
            owner.position.set(78.5f, 1f, 28.5f);
            owner.grounded = false;
            owner.flash = 1f;
            Projectile projectile = grenade(match, owner);
            assertEquals(KillContext.AIRBORNE | KillContext.BLINDED, projectile.shotFlags);
            owner.grounded = true;
            owner.flash = 0f;
            victim.position.set(65.5f, 1f, 28.5f);
            victim.health = 1f;
            victim.flash = 0f;
            detonate(match, projectile, 65.5f, 1.9f, 28.5f);
            assertFalse(victim.alive);
            assertTrue(owner.grounded);
            assertEquals(KillContext.AIRBORNE | KillContext.BLINDED,
                    publish(match).feedFlags[0]);
        } finally { match.release(); }
    }

    @Test public void realHitscanHeadshotHasNoScopeAndAirborneWithoutWallbang()
            throws Exception {
        Match match = arenaPatch();
        try {
            Combatant owner = match.player(), victim = match.combatants().get(1);
            owner.position.set(62.5f, 1f, 28.5f);
            owner.grounded = false;
            owner.aiming = false;
            victim.position.set(68.5f, 1f, 28.5f);
            fireRay(match, owner, true);
            assertFalse(victim.alive);
            HudSnapshot snapshot = publish(match);
            assertEquals(1, snapshot.feedCount);
            assertEquals(WeaponType.AWM_X.ordinal(), snapshot.feedWeaponOrdinal[0]);
            assertEquals(KillContext.HEADSHOT | KillContext.NO_SCOPE | KillContext.AIRBORNE,
                    snapshot.feedFlags[0]);
        } finally { match.release(); }
    }

    @Test public void realThinWallPenetrationAddsWallbangToScopedHeadshot() throws Exception {
        Match match = arenaPatch();
        try {
            Combatant owner = match.player(), victim = match.combatants().get(1);
            owner.position.set(62.5f, 1f, 28.5f);
            owner.grounded = true;
            owner.aiming = true;
            victim.position.set(68.5f, 1f, 28.5f);
            wall(match, 65);
            fireRay(match, owner, true);
            assertFalse(victim.alive);
            HudSnapshot snapshot = publish(match);
            assertEquals(1, snapshot.feedCount);
            assertEquals(KillContext.HEADSHOT | KillContext.SCOPED | KillContext.WALLBANG,
                    snapshot.feedFlags[0]);
        } finally { match.release(); }
    }

    @Test public void blockedOrDisabledPenetrationDoesNotProduceKillFeed() throws Exception {
        Match match = arenaPatch();
        try {
            Combatant owner = match.player(), victim = match.combatants().get(1);
            owner.position.set(62.5f, 1f, 28.5f);
            owner.grounded = true;
            victim.position.set(68.5f, 1f, 28.5f);
            wall(match, 65);
            fireRay(match, owner, false);
            assertTrue(victim.alive);
            assertEquals(0, publish(match).feedCount);
            wall(match, 66);
            fireRay(match, owner, true);
            assertTrue(victim.alive);
            assertEquals(0, publish(match).feedCount);
        } finally { match.release(); }
    }

    @Test public void realMeleeUsesKnifeMetadataNotTheSniperStillInHand() throws Exception {
        Match match = arenaPatch();
        try {
            Combatant owner = match.player(), victim = match.combatants().get(1);
            owner.position.set(62.5f, 1f, 28.5f);
            owner.setSingleWeapon(WeaponType.AWM_X);
            owner.aiming = true;
            owner.grounded = false;
            owner.flash = 0.8f;
            owner.yaw = (float) (Math.PI * 0.5);
            owner.pitch = 0f;
            victim.position.set(64.5f, 1f, 28.5f);
            Method method = Match.class.getDeclaredMethod("meleeAttack", Combatant.class,
                    WeaponType.class);
            method.setAccessible(true);
            method.invoke(match, owner, WeaponType.KNIFE);
            assertFalse(victim.alive);
            HudSnapshot snapshot = publish(match);
            assertEquals(WeaponType.KNIFE.ordinal(), snapshot.feedWeaponOrdinal[0]);
            assertEquals(KillContext.AIRBORNE | KillContext.BLINDED, snapshot.feedFlags[0]);
            assertFalse(snapshot.feedHeadshot[0]);
        } finally { match.release(); }
    }

    @Test public void resettingPooledProjectileCannotCarryOldLaunchBadges() {
        Projectile projectile = new Projectile();
        projectile.type = WeaponType.RPG_V;
        projectile.active = true;
        projectile.shotFlags = KillContext.AIRBORNE | KillContext.BLINDED;
        projectile.reset();
        assertEquals(0, projectile.shotFlags);
        assertNull(projectile.type);
        assertFalse(projectile.active);
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
            c.health = Combatant.MAX_HEALTH;
            c.armor = 0f;
            c.flash = 0f;
            c.grounded = true;
            c.crouchBlend = 0f;
            c.position.set(79.5f, 1f, 38.5f);
        }
        return match;
    }

    private Projectile grenade(Match match, Combatant owner) {
        Projectile projectile = match.projectiles()[0];
        projectile.reset();
        projectile.active = true;
        projectile.type = WeaponType.FRAG;
        projectile.ownerId = owner.id;
        projectile.team = owner.team;
        projectile.shotFlags = KillContext.shooterFlags(owner, projectile.type);
        return projectile;
    }

    private void detonate(Match match, Projectile projectile, float x, float y, float z)
            throws Exception {
        Method method = Match.class.getDeclaredMethod("detonate", Projectile.class, float.class,
                float.class, float.class);
        method.setAccessible(true);
        method.invoke(match, projectile, x, y, z);
    }

    private void fireRay(Match match, Combatant owner, boolean penetrate) throws Exception {
        Method method = Match.class.getDeclaredMethod("fireRay", Combatant.class, WeaponType.class,
                float.class, float.class, float.class, float.class, float.class, boolean.class);
        method.setAccessible(true);
        method.invoke(match, owner, WeaponType.AWM_X, 1f, 0f, 0f, 40f, 1000f, penetrate);
    }

    private void wall(Match match, int x) {
        for (int y = 1; y < 5; y++) match.world().set(x, y, 28, Materials.CONCRETE);
        match.world().commit();
    }

    private HudSnapshot publish(Match match) {
        InputState input = new InputState();
        input.paused = true;
        match.update(0.04f, input);
        return match.snapshot();
    }
}
