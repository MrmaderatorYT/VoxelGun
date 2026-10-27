package com.ccs.voxelstrike;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ccs.voxelstrike.app.Cosmetics;
import com.ccs.voxelstrike.model.Models;
import com.ccs.voxelstrike.model.VoxelModel;
import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.weapon.WeaponType;

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
        assertEquals(0.028f, referenceScale / reference.voxelSize, 0.000001f);

        for (WeaponType type : WeaponType.values()) {
            VoxelModel model = Models.weapon(type.modelKey());
            float scale = Models.firstPersonScale(model);
            assertTrue(type + " became larger than its world-model scale",
                    scale <= Models.weaponScale(model));
            assertTrue(type + " is too wide in first person",
                    model.sizeX * scale <= reference.sizeX * referenceScale + 0.000001f);
            assertTrue(type + " is too tall in first person",
                    model.sizeY * scale <= reference.sizeY * referenceScale + 0.000001f);
            assertTrue(type + " is too long in first person",
                    model.sizeZ * scale <= reference.sizeZ * referenceScale + 0.000001f);
        }

        assertTrue("the Barrett must be reduced for the first-person camera",
                Models.firstPersonScale(Models.weapon(WeaponType.BARRETT_M.modelKey()))
                        < Models.weaponScale(Models.weapon(WeaponType.BARRETT_M.modelKey())));
    }

    @Test
    public void higherDetailGridDoesNotEnlargeThePhysicalWeaponOrItsPaintPattern() {
        VoxelModel coarse = new VoxelModel(5, 9, 25)
                .box(0, 0, 0, 4, 8, 24, Materials.GUN_BODY).pivot(2.5f, 3f, 16f);
        VoxelModel fine = new VoxelModel(10, 18, 50)
                .box(0, 0, 0, 9, 17, 49, Materials.GUN_BODY).pivot(5f, 6f, 32f);
        fine.voxelSize = .5f;
        assertEquals(coarse.sizeZ * Models.weaponScale(coarse),
                fine.sizeZ * Models.weaponScale(fine), .000001f);
        assertEquals(coarse.sizeZ * Models.firstPersonScale(coarse),
                fine.sizeZ * Models.firstPersonScale(fine), .000001f);
        VoxelModel copied = fine.copy();
        assertEquals(fine.voxelSize, copied.voxelSize, 0f);
        assertEquals(fine.pivotZ, copied.pivotZ, 0f);
        for (int skin : new int[]{1, 5}) {
            VoxelModel lowPaint = Cosmetics.applyWeaponSkin(coarse.copy(), skin);
            VoxelModel highPaint = Cosmetics.applyWeaponSkin(fine.copy(), skin);
            for (int z = 0; z < coarse.sizeZ; z++) {
                for (int y = 0; y < coarse.sizeY; y++) {
                    for (int x = 0; x < coarse.sizeX; x++) {
                        for (int dz = 0; dz < 2; dz++) {
                            for (int dy = 0; dy < 2; dy++) {
                                for (int dx = 0; dx < 2; dx++) {
                                    assertEquals(lowPaint.get(x, y, z),
                                            highPaint.get(x * 2 + dx, y * 2 + dy, z * 2 + dz));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void weaponGeometryUsesOpaqueDetailWithinMobileStorageAndSurfaceBudgets() {
        int[][] faces = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0},
                {0, 0, 1}, {0, 0, -1}};
        for (WeaponType type : WeaponType.values()) {
            VoxelModel model = Models.weapon(type.modelKey());
            assertEquals(type + " must use the authored half-unit grid", .5f, model.voxelSize, 0f);
            assertTrue(type + " exceeds 40 KiB of source voxels", model.voxels.length <= 40 * 1024);
            Set<Integer> palette = new HashSet<>();
            int exposedFaces = 0;
            for (int z = 0; z < model.sizeZ; z++) {
                for (int y = 0; y < model.sizeY; y++) {
                    for (int x = 0; x < model.sizeX; x++) {
                        int material = model.get(x, y, z) & 255;
                        if (material == 0) continue;
                        assertTrue(type + " contains an invisible translucent lens",
                                Materials.opaque(material));
                        palette.add(material);
                        for (int[] face : faces) {
                            if (model.get(x + face[0], y + face[1], z + face[2]) == 0) {
                                exposedFaces++;
                            }
                        }
                    }
                }
            }
            assertTrue(type + " lost contrasting material regions", palette.size() >= 4);
            // This is the unmerged Java fallback: native greedy meshes are substantially smaller.
            assertTrue(type + " exceeds the fallback surface budget: " + exposedFaces,
                    exposedFaces <= 8000);
        }
    }

    @Test
    public void legacyModelKeysStillResolveToTheCorrespondingDetailedWeapon() {
        String[][] aliases = {{"pistol", "p9"}, {"revolver", "ranger_50"},
                {"smg", "spectre"}, {"shotgun", "pumper_8"}, {"rifle", "famas_b"},
                {"sniper", "scout_v"}, {"lmg", "bulldog_lmg"},
                {"launcher", "rpg_v"}, {"grenade", "frag_grenade"}};
        for (String[] alias : aliases) {
            assertArrayEquals(alias[0], Models.weapon(alias[1]).voxels,
                    Models.weapon(alias[0]).voxels);
        }
    }

    private static int occupied(VoxelModel model) {
        int count = 0;
        for (byte voxel : model.voxels) {
            if (voxel != 0) count++;
        }
        return count;
    }
}
