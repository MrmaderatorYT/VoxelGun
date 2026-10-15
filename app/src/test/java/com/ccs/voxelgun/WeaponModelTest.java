package com.ccs.voxelgun;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ccs.voxelgun.app.Cosmetics;
import com.ccs.voxelgun.model.Models;
import com.ccs.voxelgun.model.VoxelModel;
import com.ccs.voxelgun.weapon.WeaponType;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Pins the one-weapon/one-model contract and the shared cosmetic paint pipeline. */
public class WeaponModelTest {
    @Test
    public void everyWeaponHasItsOwnKeyAndVoxelSilhouette() {
        WeaponType[] types = WeaponType.values();
        VoxelModel[] models = new VoxelModel[types.length];
        Set<String> keys = new HashSet<>();

        for (int i = 0; i < types.length; i++) {
            WeaponType type = types[i];
            assertTrue(type + " reuses model key " + type.modelKey(), keys.add(type.modelKey()));
            models[i] = Models.weapon(type.modelKey());
            assertTrue(type + " has an empty model", occupied(models[i]) > 0);
            assertTrue(type + " pivot X is outside its model",
                    models[i].pivotX >= 0f && models[i].pivotX <= models[i].sizeX);
            assertTrue(type + " pivot Y is outside its model",
                    models[i].pivotY >= 0f && models[i].pivotY <= models[i].sizeY);
            assertTrue(type + " pivot Z is outside its model",
                    models[i].pivotZ >= 0f && models[i].pivotZ <= models[i].sizeZ);
        }

        for (int left = 0; left < types.length; left++) {
            for (int right = left + 1; right < types.length; right++) {
                boolean identical = models[left].sizeX == models[right].sizeX
                        && models[left].sizeY == models[right].sizeY
                        && models[left].sizeZ == models[right].sizeZ
                        && Arrays.equals(models[left].voxels, models[right].voxels);
                assertFalse(types[left] + " and " + types[right] + " share one silhouette",
                        identical);
            }
        }
    }

    @Test
    public void everyPaintableWeaponChangesUnderEverySkinWithoutChangingGeometry() {
        for (WeaponType type : WeaponType.values()) {
            if (type.modeOnly() || type.grenade()) {
                continue;
            }
            VoxelModel factory = Models.weapon(type.modelKey());
            for (int skin = 1; skin <= 5; skin++) {
                VoxelModel painted = Cosmetics.applyWeaponSkin(factory.copy(), skin);
                assertTrue(type + " skin " + skin + " painted no voxels",
                        !Arrays.equals(factory.voxels, painted.voxels));
                assertTrue(type + " skin changed occupied geometry",
                        occupied(factory) == occupied(painted));
                assertArrayEquals(type + " skin changed the factory model in place",
                        Models.weapon(type.modelKey()).voxels, factory.voxels);
            }
        }
    }

    @Test
    public void allFiveFinishesProduceDifferentFamasPaint() {
        Set<String> finishes = new HashSet<>();
        for (int skin = 1; skin <= 5; skin++) {
            VoxelModel painted = Cosmetics.applyWeaponSkin(
                    Models.weapon(WeaponType.FAMAS_B.modelKey()), skin);
            finishes.add(Arrays.toString(painted.voxels));
        }
        assertTrue("two named skins render identically", finishes.size() == 5);
    }

    @Test
    public void firstPersonModelsFitInsideTheFamasViewEnvelope() {
        VoxelModel reference = Models.weapon(WeaponType.FAMAS_B.modelKey());
        float referenceScale = Models.firstPersonScale(reference);
        assertEquals(0.028f, referenceScale, 0.000001f);

        for (WeaponType type : WeaponType.values()) {
            VoxelModel model = Models.weapon(type.modelKey());
            float scale = Models.firstPersonScale(model);
            assertTrue(type + " became larger than its world-model scale",
                    scale <= Models.WEAPON_SCALE);
            assertTrue(type + " is too wide in first person",
                    model.sizeX * scale <= reference.sizeX * referenceScale + 0.000001f);
            assertTrue(type + " is too tall in first person",
                    model.sizeY * scale <= reference.sizeY * referenceScale + 0.000001f);
            assertTrue(type + " is too long in first person",
                    model.sizeZ * scale <= reference.sizeZ * referenceScale + 0.000001f);
        }

        assertTrue("the Barrett must be reduced for the first-person camera",
                Models.firstPersonScale(Models.weapon(WeaponType.BARRETT_M.modelKey()))
                        < Models.WEAPON_SCALE);
    }

    private static int occupied(VoxelModel model) {
        int count = 0;
        for (byte voxel : model.voxels) {
            if (voxel != 0) count++;
        }
        return count;
    }
}
