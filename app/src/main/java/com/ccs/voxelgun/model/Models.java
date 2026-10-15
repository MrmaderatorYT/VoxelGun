package com.ccs.voxelgun.model;

import com.ccs.voxelgun.voxel.Materials;

/**
 * Every character and weapon in the game, built from code. No model files ship with the app.
 *
 * <p>Character units are 1/32 of a body, so 32 voxels tall is 1.8 m. Weapons use a half-unit
 * detail grid with explicit voxel size, preserving physical dimensions in every renderer.
 */
public final class Models {
    public static final float CHARACTER_SCALE = 0.056f;
    public static final float WEAPON_SCALE = 0.032f;
    private static final float FIRST_PERSON_WEAPON_SCALE = 0.028f;
    private static final float VIEW_REFERENCE_WIDTH = 7f;
    private static final float VIEW_REFERENCE_HEIGHT = 11f;
    private static final float VIEW_REFERENCE_LENGTH = 23f;

    /**
     * How far above the pivot a weapon's sight line sits, in voxels.
     *
     * <p>Every gun is modelled around its grip, and the pivot is the grip — so aiming every weapon
     * with one shared offset put the receiver, not the sights, in front of the eye. The sight line
     * is the top of the model ahead of the grip: the front sight on an iron sighted gun, the scope
     * tube on a sniper. Half a voxel comes off so the eye looks just over the sights instead of
     * through the metal.
     */
    public static float sightLift(VoxelModel model) {
        int front = Math.min(model.sizeZ, Math.max(1, Math.round(model.pivotZ)));
        int top = -1;
        for (int z = 0; z < front; z++) {
            for (int y = model.sizeY - 1; y > top; y--) {
                for (int x = 0; x < model.sizeX; x++) {
                    if (model.get(x, y, z) != 0) {
                        top = y;
                        break;
                    }
                }
            }
        }
        return top < 0 ? 0f : Math.max(0f, top - 0.5f - model.pivotY);
    }

    public static float firstPersonScale(VoxelModel model) {
        float fit = Math.min(VIEW_REFERENCE_WIDTH / (model.sizeX * model.voxelSize),
                Math.min(VIEW_REFERENCE_HEIGHT / (model.sizeY * model.voxelSize),
                        VIEW_REFERENCE_LENGTH / (model.sizeZ * model.voxelSize)));
        return FIRST_PERSON_WEAPON_SCALE * model.voxelSize * Math.min(1f, fit);
    }

    public static float weaponScale(VoxelModel model) {
        return WEAPON_SCALE * model.voxelSize;
    }

    private Models() {}

    // ------------------------------------------------------------------ characters

    public static VoxelModel head(int accent, boolean zombie) {
        int skin = zombie ? Materials.GRASS : Materials.SKIN;
        VoxelModel model = new VoxelModel(8, 8, 8);
        model.box(0, 0, 0, 7, 7, 7, skin);
        // Helmet.
        model.box(0, 6, 0, 7, 7, 7, accent);
        model.box(0, 5, 0, 7, 5, 1, accent);
        // Visor across the front face (local -Z is forward).
        model.box(1, 3, 0, 6, 4, 0, Materials.VISOR);
        model.box(0, 3, 1, 0, 4, 1, Materials.VISOR);
        model.box(7, 3, 1, 7, 4, 1, Materials.VISOR);
        if (zombie) {
            model.box(2, 3, 0, 2, 3, 0, Materials.EMERGENCY);
            model.box(5, 3, 0, 5, 3, 0, Materials.EMERGENCY);
        }
        return model.pivot(4f, 0f, 4f);
    }

    public static VoxelModel torso(int accent, boolean zombie) {
        VoxelModel model = new VoxelModel(8, 12, 4);
        model.box(0, 0, 0, 7, 11, 3, zombie ? Materials.CLOTH_DARK : Materials.CLOTH_DARK);
        // Chest rig and team stripe.
        model.box(1, 5, 0, 6, 8, 0, accent);
        model.box(0, 8, 0, 7, 11, 3, accent);
        model.box(1, 5, 3, 6, 8, 3, accent);
        model.box(0, 4, 0, 7, 4, 3, Materials.RUBBER);
        model.box(2, 9, 0, 5, 10, 0, Materials.PLASTIC_WHITE);
        if (zombie) {
            model.box(1, 2, 0, 3, 4, 0, Materials.GRASS);
            model.box(4, 6, 3, 6, 8, 3, Materials.GRASS);
        }
        return model.pivot(4f, 0f, 2f);
    }

    public static VoxelModel arm(int accent, boolean zombie) {
        int skin = zombie ? Materials.GRASS : Materials.SKIN;
        VoxelModel model = new VoxelModel(4, 12, 4);
        model.box(0, 0, 0, 3, 11, 3, Materials.CLOTH_DARK);
        model.box(0, 8, 0, 3, 11, 3, accent);
        model.box(0, 0, 0, 3, 2, 3, skin);
        return model.pivot(2f, 11f, 2f);
    }

    public static VoxelModel leg(boolean zombie) {
        VoxelModel model = new VoxelModel(4, 12, 4);
        model.box(0, 0, 0, 3, 11, 3, zombie ? Materials.CLOTH_DARK : Materials.CONCRETE_DARK);
        model.box(0, 0, 0, 3, 1, 3, Materials.RUBBER);
        model.box(0, 0, 0, 3, 1, 0, Materials.RUBBER);
        return model.pivot(2f, 11f, 2f);
    }

    // ------------------------------------------------------------------ weapons

    /** Shared detailed geometry for first person, world rigs, skins and preview artwork. */
    public static VoxelModel weapon(String key) {
        return WeaponModels.create(key);
    }

    /** The same shaped glove is used on the first-person rig and in cosmetic previews. */
    public static VoxelModel glove(int material) {
        return new VoxelModel(6, 11, 10)
                .box(1, 5, 0, 4, 9, 4, material)
                .box(0, 6, 1, 5, 8, 3, material)
                .box(1, 3, 3, 4, 5, 6, Materials.CLOTH_DARK)
                .box(1, 0, 4, 4, 3, 9, material)
                .box(1, 8, 0, 4, 8, 0, Materials.GUN_GRIP)
                .pivot(3, 9, 2);
    }

    /** Rocket in flight. */
    public static VoxelModel rocket() {
        VoxelModel model = new VoxelModel(3, 3, 8);
        model.box(0, 0, 1, 2, 2, 6, Materials.GUN_METAL);
        model.box(1, 1, 0, 1, 1, 1, Materials.PAINT_RED);
        model.box(0, 1, 6, 2, 1, 7, Materials.GUN_BODY);
        model.box(1, 0, 6, 1, 2, 7, Materials.GUN_BODY);
        return model.pivot(1.5f, 1.5f, 4f);
    }
}
