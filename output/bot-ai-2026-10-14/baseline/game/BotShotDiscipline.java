package com.ccs.voxelgun.game;

import com.ccs.voxelgun.weapon.WeaponClass;

/** Snipers brake, land and settle their optic before firing; other families retain mobile fire. */
public final class BotShotDiscipline {
    private float settledSeconds;

    public void reset() { settledSeconds = 0f; }

    /** Called once per simulation step, before behavior chooses this frame's next action. */
    public void update(Combatant self, float dt) {
        if (self.weaponType().weaponClass() == WeaponClass.SNIPER && stable(self)) {
            settledSeconds += dt;
        } else settledSeconds = 0f;
    }

    public boolean allowsShot(Combatant self, Difficulty difficulty) {
        return self.weaponType().weaponClass() != WeaponClass.SNIPER
                || stable(self) && settledSeconds >= difficulty.sniperSettleSeconds();
    }

    private static boolean stable(Combatant self) {
        return self.grounded && self.aiming && !self.sprinting && Math.abs(self.velocity.y) < .1f
                && self.velocity.x * self.velocity.x + self.velocity.z * self.velocity.z <= 1.44f;
    }
}
