package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.weapon.WeaponClass;

/**
 * A Battle Field weapon class. The class chooses the soldier's one long-gun family; every class
 * also deploys with the same pistol and combat knife.
 */
public enum BattlefieldClass {
    RIFLEMAN("Rifleman", "Assault rifle, pistol and knife", WeaponClass.RIFLE),
    SNIPER("Sniper", "Sniper rifle, pistol and knife", WeaponClass.SNIPER),
    MACHINE_GUNNER("Machine Gunner", "Machine gun, pistol and knife", WeaponClass.LMG);

    private final String label;
    private final String description;
    private final WeaponClass weaponClass;

    BattlefieldClass(String label, String description, WeaponClass weaponClass) {
        this.label = label;
        this.description = description;
        this.weaponClass = weaponClass;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public WeaponClass weaponClass() {
        return weaponClass;
    }

    /** Weapon classes do not silently change movement, armour or healing balance. */
    public float movementMultiplier() {
        return 1f;
    }

    public float spawnArmor() {
        return 25f;
    }

    public float regenerationPerSecond() {
        return 8f;
    }
}
