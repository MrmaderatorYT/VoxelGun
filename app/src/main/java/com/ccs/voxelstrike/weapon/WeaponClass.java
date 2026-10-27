package com.ccs.voxelstrike.weapon;

/** Weapon families, used for loadout slots, shop tabs and pick-up rules. */
public enum WeaponClass {
    MELEE("Melee", WeaponSlot.MELEE),
    PISTOL("Pistol", WeaponSlot.SECONDARY),
    SMG("SMG", WeaponSlot.PRIMARY),
    RIFLE("Rifle", WeaponSlot.PRIMARY),
    SNIPER("Sniper", WeaponSlot.PRIMARY),
    SHOTGUN("Shotgun", WeaponSlot.PRIMARY),
    LMG("LMG", WeaponSlot.PRIMARY),
    LAUNCHER("Launcher", WeaponSlot.PRIMARY),
    GRENADE("Grenade", WeaponSlot.GRENADE);

    private final String label;
    private final WeaponSlot slot;

    WeaponClass(String label, WeaponSlot slot) {
        this.label = label;
        this.slot = slot;
    }

    public String label() {
        return label;
    }

    public WeaponSlot slot() {
        return slot;
    }
}
