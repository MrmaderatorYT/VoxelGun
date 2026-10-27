package com.ccs.voxelstrike;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ccs.voxelstrike.weapon.Weapon;
import com.ccs.voxelstrike.weapon.WeaponClass;
import com.ccs.voxelstrike.weapon.WeaponSlot;
import com.ccs.voxelstrike.weapon.WeaponType;
import com.ccs.voxelstrike.game.Combatant;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class WeaponTest {
    @Test
    public void selectingTheMeleeSlotKeepsTheKnifeInHand() {
        Combatant player = new Combatant(1, "Player");
        player.setLoadout(WeaponType.FAMAS_B, WeaponType.P9, WeaponType.KNIFE, WeaponType.FRAG);

        assertTrue(player.selectSlot(WeaponSlot.MELEE.ordinal()));
        assertEquals(WeaponSlot.MELEE.ordinal(), player.slot);
        assertEquals(WeaponType.KNIFE, player.weaponType());
        player.weapon().update(1f);
        assertEquals(WeaponType.KNIFE, player.weaponType());
    }

    @Test
    public void automaticWeaponRespectsItsFireRate() {
        Weapon weapon = new Weapon(WeaponType.AK15);
        int shots = 0;
        // One simulated second at 60 Hz.
        for (int step = 0; step < 60; step++) {
            if (weapon.canFire(true)) {
                weapon.consume();
                shots++;
            }
            weapon.update(1f / 60f);
        }
        // 600 rpm is ten rounds a second; the discrete step allows one either way.
        assertTrue("fired " + shots, shots >= 9 && shots <= 11);
    }

    @Test
    public void semiAutomaticNeedsTheTriggerReleased() {
        Weapon weapon = new Weapon(WeaponType.P9);
        assertTrue(weapon.canFire(true));
        weapon.consume();
        for (int step = 0; step < 30; step++) {
            weapon.update(1f / 60f);
        }
        assertFalse("held trigger must not repeat", weapon.canFire(true));
        weapon.releaseTrigger();
        assertTrue(weapon.canFire(true));
    }

    @Test
    public void heldMeleeRepeatsAtItsConfiguredRateWithoutChangingPistols() {
        Weapon knife = new Weapon(WeaponType.KNIFE);
        assertTrue(knife.canFire(true));
        knife.consume();
        assertFalse("the knife ignored its swing cooldown", knife.canFire(true));
        knife.update(WeaponType.KNIFE.shotInterval() + 0.01f);
        assertTrue("a held knife did not become ready for the next swing", knife.canFire(true));

        Weapon pistol = new Weapon(WeaponType.P9);
        assertTrue(pistol.canFire(true));
        pistol.consume();
        pistol.update(WeaponType.P9.shotInterval() + 0.01f);
        assertFalse("a held semi-automatic pistol fired again", pistol.canFire(true));
    }

    @Test
    public void reloadMovesRoundsFromTheReserve() {
        Weapon weapon = new Weapon(WeaponType.AK15);
        for (int i = 0; i < 10; i++) {
            weapon.consume();
            weapon.update(0.2f);
        }
        int before = weapon.ammo();
        assertEquals(WeaponType.AK15.magazine() - 10, before);
        assertTrue(weapon.startReload());
        for (int step = 0; step < 200 && weapon.reloading(); step++) {
            weapon.update(1f / 60f);
        }
        assertEquals(WeaponType.AK15.magazine(), weapon.ammo());
        assertEquals(WeaponType.AK15.reserve() - 10, weapon.reserve());
    }

    @Test
    public void spreadGrowsWithFireAndShrinksWhenAiming() {
        Weapon weapon = new Weapon(WeaponType.AK15);
        float hip = weapon.spread(false, 0f, false, false, 0f);
        float aimed = weapon.spread(true, 0f, false, false, 0f);
        assertTrue(aimed < hip);

        for (int i = 0; i < 5; i++) {
            weapon.consume();
        }
        assertTrue(weapon.spread(false, 0f, false, false, 0f) > hip);
        assertTrue(weapon.spread(false, 0f, true, false, 0f)
                < weapon.spread(false, 0f, false, false, 0f));
        assertTrue(weapon.spread(false, 0f, false, true, 0f)
                > weapon.spread(false, 0f, false, false, 0f));
    }

    @Test
    public void grenadesDrawFromThePouchInsteadOfRefilling() {
        Weapon grenade = new Weapon(WeaponType.FRAG);
        int total = grenade.ammo() + grenade.reserve();
        for (int i = 0; i < total; i++) {
            assertTrue("throw " + i, grenade.ammo() > 0 || grenade.loadOne());
            grenade.consume();
            grenade.loadOne();
            grenade.update(1f);
        }
        assertTrue(grenade.depleted());
    }

    @Test
    public void everyWeaponDeclaresACoherentSlotAndCost() {
        for (WeaponType type : WeaponType.values()) {
            WeaponSlot slot = type.slot();
            assertEquals(type.weaponClass().slot(), slot);
            assertTrue(type.name() + " has a negative cost", type.cost() >= 0);
            if (slot == WeaponSlot.PRIMARY && !type.modeOnly()) {
                assertTrue(type.name() + " is a free primary", type.cost() > 0);
            }
            if (!type.melee() && !type.grenade()) {
                assertTrue(type.name() + " has no magazine", type.magazine() > 0);
                assertTrue(type.name() + " has no rate of fire", type.roundsPerMinute() > 0f);
            }
        }
    }

    @Test
    public void onlyScopedWeaponsMagnify() {
        for (WeaponType type : WeaponType.values()) {
            if (type.scoped()) {
                assertTrue(type.displayName() + " is scoped but does not magnify",
                        type.zoom() > 1f);
            } else {
                assertEquals(type.displayName() + " zooms without a scope", 1f, type.zoom(), 0.001f);
            }
        }
    }

    @Test
    public void everyScopedWeaponIsASniper() {
        for (WeaponType type : WeaponType.values()) {
            if (type.scoped()) {
                assertEquals(type.displayName() + " carries a scope without being a sniper",
                        WeaponClass.SNIPER, type.weaponClass());
            }
        }
    }

    @Test
    public void holdingStillTightensTheConeTowardTheOpticsValue() {
        Weapon weapon = new Weapon(WeaponType.AK15);
        float loose = weapon.spread(false, 0f, false, false, 0f);
        float settled = weapon.spread(false, 0f, false, false, 1f);
        float optics = weapon.spread(true, 0f, false, false, 0f);

        assertTrue("standing still did not help", settled < loose);
        assertTrue("a settled stance beat the tuned optics value", settled > optics);
    }

    @Test
    public void movingUndoesASettledStance() {
        Weapon weapon = new Weapon(WeaponType.AK15);
        float settled = weapon.spread(false, 0f, false, false, 1f);
        float running = weapon.spread(false, 1f, false, false, 0f);
        assertTrue("running was no worse than standing still", running > settled);
    }

    @Test
    public void everyFirearmHasItsOwnCompleteSpreadProfile() {
        Set<String> profiles = new HashSet<>();
        int firearms = 0;
        for (WeaponType type : WeaponType.values()) {
            if (type.melee() || type.grenade()) {
                continue;
            }
            firearms++;
            assertTrue(type + " has no movement spread", type.movementSpreadPenalty() > 0f);
            assertTrue(type + " has no per-shot bloom", type.bloomPerShot() > 0f);
            assertTrue(type + " has no bloom recovery", type.bloomRecovery() > 0f);
            String profile = Float.floatToIntBits(type.hipSpread()) + ":"
                    + Float.floatToIntBits(type.adsSpread()) + ":"
                    + Float.floatToIntBits(type.movementSpreadPenalty()) + ":"
                    + Float.floatToIntBits(type.bloomPerShot()) + ":"
                    + Float.floatToIntBits(type.bloomRecovery());
            assertTrue(type + " duplicates another gun's entire spread profile",
                    profiles.add(profile));
        }
        assertEquals(firearms, profiles.size());
    }

    @Test
    public void lightAndHeavyWeaponsBuildAndRecoverSpreadDifferently() {
        Weapon mp7 = new Weapon(WeaponType.MP7K);
        Weapon negev = new Weapon(WeaponType.NEGEV_M);
        for (int step = 0; step < 36; step++) {
            if (mp7.canFire(true)) mp7.consume();
            if (negev.canFire(true)) negev.consume();
            mp7.update(1f / 60f);
            negev.update(1f / 60f);
        }
        assertTrue("the Negev should lose sustained accuracy faster than the MP7",
                negev.bloom() > mp7.bloom());

        Weapon barrett = new Weapon(WeaponType.BARRETT_M);
        float barrettMovementRatio = barrett.spread(false, 1f, false, false, 0f)
                / barrett.spread(false, 0f, false, false, 0f);
        float mp7MovementRatio = new Weapon(WeaponType.MP7K)
                .spread(false, 1f, false, false, 0f)
                / new Weapon(WeaponType.MP7K).spread(false, 0f, false, false, 0f);
        assertTrue("a heavy sniper should suffer more movement spread than an SMG",
                barrettMovementRatio > mp7MovementRatio);
    }
}
