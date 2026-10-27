package com.ccs.voxelstrike;

import com.ccs.voxelstrike.weapon.Weapon;
import com.ccs.voxelstrike.weapon.WeaponType;
import org.junit.Test;
import static org.junit.Assert.*;

public class AmmoModifiersTest {
    private void empty(Weapon w) { for (int i = 0; i < w.type().magazine(); i++) w.consume(); w.update(2f); }
    @Test public void infiniteReserveRequiresReloadAndDoesNotSpendReserve() {
        Weapon w = new Weapon(WeaponType.AK15);
        w.setAmmoRules(true, false);
        int reserve = w.reserve();
        empty(w);
        assertFalse(w.canFire(true));
        assertTrue(w.startReload());
        assertFalse(w.canFire(true));
        w.update(w.type().reloadSeconds() + 0.01f);
        assertEquals(w.type().magazine(), w.ammo());
        assertEquals(reserve, w.reserve());
    }
    @Test public void infiniteReserveWorksEvenAfterNormalReserveIsCompletelyDepleted() {
        Weapon w = new Weapon(WeaponType.P9);
        while (!w.depleted()) {
            empty(w);
            if (w.startReload()) w.update(w.type().reloadSeconds() + 0.01f);
        }
        w.setAmmoRules(true, false);
        assertEquals(0, w.reserve());
        assertFalse(w.depleted());
        assertTrue(w.startReload());
        w.update(10f);
        assertEquals(w.type().magazine(), w.ammo());
        assertEquals(0, w.reserve());
    }
    @Test public void noReloadKeepsMagazineButStillEnforcesCadenceAndSemiAuto() {
        Weapon w = new Weapon(WeaponType.P9);
        w.setAmmoRules(false, true);
        for (int i = 0; i < 100; i++) {
            assertTrue(w.canFire(true));
            w.consume();
            assertTrue("no reload must not suppress shot bloom", w.bloom() > 0f);
            assertFalse(w.canFire(true));
            w.update(w.type().shotInterval() + 0.01f);
            assertFalse("semi auto still needs a new press", w.canFire(true));
            w.releaseTrigger();
        }
        assertEquals(w.type().magazine(), w.ammo());
        assertFalse(w.startReload());
        assertFalse(w.reloading());
    }
    @Test public void bothRulesCoexistAndRefillPreservesThem() {
        Weapon w = new Weapon(WeaponType.AK15);
        w.setAmmoRules(true, true);
        for (int i = 0; i < 200; i++) { w.consume(); w.update(0.12f); }
        w.refill();
        assertTrue(w.infiniteReserve()); assertTrue(w.noReload());
        assertEquals(w.type().magazine(), w.ammo());
        assertFalse(w.startReload());
    }
    @Test public void infiniteReserveAlsoSupportsThrownEquipmentWithoutNegativeCounts() {
        Weapon w = new Weapon(WeaponType.FRAG);
        while (!w.depleted()) { w.consume(); if (w.empty()) w.loadOne(); }
        w.setAmmoRules(true, false);
        for (int i = 0; i < 20; i++) { assertTrue(w.loadOne()); w.consume(); }
        assertEquals(0, w.reserve());
        assertFalse(w.depleted());
    }
}
