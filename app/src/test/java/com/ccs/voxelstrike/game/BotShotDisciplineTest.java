package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.weapon.WeaponType;
import org.junit.Test;
import static org.junit.Assert.*;

public class BotShotDisciplineTest {
    @Test public void sniperMustLandBrakeAndSettleOptic() {
        Combatant c = new Combatant(3, "Sniper");
        c.setSingleWeapon(WeaponType.AWM_X);
        c.aiming = true;
        BotShotDiscipline discipline = new BotShotDiscipline();
        for (int i = 0; i < 60; i++) discipline.update(c, Match.STEP);
        assertFalse("tracking during flight cannot make a shot ready", discipline.allowsShot(c, Difficulty.ELITE));
        c.grounded = true;
        c.velocity.x = 4f;
        for (int i = 0; i < 60; i++) discipline.update(c, Match.STEP);
        assertFalse("landing is insufficient while still sliding", discipline.allowsShot(c, Difficulty.ELITE));
        c.velocity.x = 0f;
        discipline.update(c, Match.STEP);
        assertFalse("a frame after braking is not a settled scope", discipline.allowsShot(c, Difficulty.ELITE));
        for (int i = 0; i < 20; i++) discipline.update(c, Match.STEP);
        assertTrue(discipline.allowsShot(c, Difficulty.ELITE));
        c.grounded = false;
        assertFalse("a new jump invalidates readiness immediately", discipline.allowsShot(c, Difficulty.ELITE));
        discipline.update(c, Match.STEP);
        c.grounded = true;
        assertFalse("the previous grounded settle cannot leak into a landing", discipline.allowsShot(c, Difficulty.ELITE));
    }

    @Test public void hipFireAndSprintDoNotCountAsScopedSteadyTime() {
        Combatant c = new Combatant(3, "Sniper");
        c.setSingleWeapon(WeaponType.SCOUT_V);
        c.grounded = true;
        BotShotDiscipline discipline = new BotShotDiscipline();
        for (int i = 0; i < 60; i++) discipline.update(c, Match.STEP);
        assertFalse(discipline.allowsShot(c, Difficulty.ELITE));
        c.aiming = true; c.sprinting = true;
        for (int i = 0; i < 60; i++) discipline.update(c, Match.STEP);
        assertFalse(discipline.allowsShot(c, Difficulty.ELITE));
    }

    @Test public void ordinaryWeaponsCanContinueMobileAirFire() {
        Combatant c = new Combatant(2, "Rusher");
        c.setSingleWeapon(WeaponType.MP7K);
        c.grounded = false; c.velocity.set(5f, -2f, 6f);
        BotShotDiscipline discipline = new BotShotDiscipline();
        discipline.update(c, Match.STEP);
        assertTrue(discipline.allowsShot(c, Difficulty.RECRUIT));
    }
}
