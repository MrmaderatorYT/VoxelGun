package com.ccs.voxelstrike.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.ccs.voxelstrike.app.KeyBindings;
import com.ccs.voxelstrike.weapon.WeaponSlot;

import org.junit.Test;

/** Keyboard action dispatch that can be tested without Android KeyEvent stubs. */
public class DeviceInputTest {
    @Test
    public void meleeActionSelectsThePersistentKnifeSlot() {
        InputState input = new InputState();
        DeviceInput device = new DeviceInput(input, null);

        device.apply(KeyBindings.Action.MELEE, true);

        assertEquals(WeaponSlot.MELEE.ordinal(), input.consumeSlotRequest());
        assertFalse("V must not use the old quick-strike mailbox", input.consumeMelee());
    }
}
