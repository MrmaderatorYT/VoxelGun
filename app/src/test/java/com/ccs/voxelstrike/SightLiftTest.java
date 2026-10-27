package com.ccs.voxelstrike;

import static org.junit.Assert.assertTrue;

import com.ccs.voxelstrike.model.Models;
import com.ccs.voxelstrike.model.VoxelModel;
import com.ccs.voxelstrike.weapon.WeaponType;

import org.junit.Test;

/**
 * The aim offset is derived from each weapon model rather than shared, so a gun cannot end up
 * being aimed by its grip. This pins the derivation to sane values for every model in the game.
 */
public class SightLiftTest {
    @Test
    public void everyGunAimsOverItsOwnSights() {
        for (WeaponType type : WeaponType.values()) {
            VoxelModel model = Models.weapon(type.modelKey());
            float lift = Models.sightLift(model);
            System.out.println(type.modelKey() + " lift=" + lift
                    + " pivotY=" + model.pivotY + " sizeY=" + model.sizeY);
            assertTrue(type.modelKey() + " has no lift at all", lift >= 0f);
            assertTrue(type.modelKey() + " lifts past the top of the model",
                    model.pivotY + lift < model.sizeY);
            if (!type.melee() && !type.grenade()) {
                // A gun aimed at its grip is the bug this guards: the sights on every firearm
                // model sit well above the pivot.
                assertTrue(type.modelKey() + " still aims at the grip", lift >= 2f);
            }
        }
    }
}
