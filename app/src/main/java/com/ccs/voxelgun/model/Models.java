package com.ccs.voxelgun.model;

import com.ccs.voxelgun.voxel.Materials;

/**
 * Every character and weapon in the game, built from code. No model files ship with the app.
 *
 * <p>Character units are 1/32 of a body, so 32 voxels tall is 1.8 m. Weapons use the same voxel
 * grid at a smaller world scale.
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
        float fit = Math.min(VIEW_REFERENCE_WIDTH / model.sizeX,
                Math.min(VIEW_REFERENCE_HEIGHT / model.sizeY,
                        VIEW_REFERENCE_LENGTH / model.sizeZ));
        return FIRST_PERSON_WEAPON_SCALE * Math.min(1f, fit);
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

    /** Builds a weapon by the key declared on {@code WeaponType}. */
    public static VoxelModel weapon(String key) {
        switch (key) {
            case "knife":
                return knife();
            case "scrap_knife":
                return scrapKnife();
            case "p9":
            case "pistol": // Legacy model keys kept for old previews and saved diagnostics.
                return p9();
            case "talon_s":
                return talonS();
            case "auto_9":
                return auto9();
            case "mag_5":
                return mag5();
            case "ranger_50":
            case "revolver":
                return ranger50();
            case "sidewinder":
                return sidewinder();
            case "spectre":
            case "smg":
                return spectre();
            case "hornet":
                return hornet();
            case "uzi_k":
                return uziK();
            case "mp7_k":
                return mp7K();
            case "vector_9":
                return vector9();
            case "pumper_8":
            case "shotgun":
                return pumper8();
            case "breaker_12":
                return breaker12();
            case "auto_slug":
                return autoSlug();
            case "drum_12":
                return drum12();
            case "famas_b":
            case "rifle":
                return famasB();
            case "bullpup_a":
                return bullpupA();
            case "ak_15":
                return ak15();
            case "vektor_ar":
                return vektorAr();
            case "m4_falcon":
                return m4Falcon();
            case "scar_h":
                return scarH();
            case "scout_v":
            case "sniper":
                return scoutV();
            case "dragunov_s":
                return dragunovS();
            case "awm_x":
                return awmX();
            case "barrett_m":
                return barrettM();
            case "bulldog_lmg":
            case "lmg":
                return bulldogLmg();
            case "negev_m":
                return negevM();
            case "rpg_v":
            case "launcher":
                return rpgV();
            case "frag_grenade":
            case "grenade":
                return fragGrenade();
            case "flash_grenade":
                return flashGrenade();
            default:
                return famasB();
        }
    }

    // Every arsenal entry below owns its silhouette. Models in one family deliberately share the
    // same visual language, but never the same voxel grid: length, magazine, stock, rail, optic and
    // muzzle details all match the weapon the player selected in the shop.

    private static VoxelModel p9() {
        VoxelModel model = new VoxelModel(3, 8, 9);
        model.box(0, 4, 1, 2, 6, 7, Materials.GUN_BODY);
        model.box(1, 5, 0, 1, 5, 2, Materials.GUN_METAL);
        model.box(0, 0, 5, 2, 3, 7, Materials.GUN_GRIP);
        model.box(1, 7, 2, 1, 7, 2, Materials.GUN_METAL);
        return model.pivot(1f, 1f, 6f);
    }

    private static VoxelModel talonS() {
        VoxelModel model = new VoxelModel(3, 8, 13);
        model.box(0, 4, 5, 2, 6, 11, Materials.GUN_BODY);
        model.box(1, 5, 0, 1, 5, 6, Materials.GUN_METAL);          // suppressor
        model.box(0, 0, 9, 2, 3, 11, Materials.GUN_GRIP);
        model.box(1, 7, 6, 1, 7, 7, Materials.GUN_METAL);
        model.box(0, 4, 3, 2, 5, 4, Materials.GUN_METAL);
        return model.pivot(1f, 1f, 10f);
    }

    private static VoxelModel auto9() {
        VoxelModel model = new VoxelModel(5, 10, 11);
        model.box(1, 5, 1, 3, 7, 8, Materials.GUN_BODY);
        model.box(2, 6, 0, 2, 6, 3, Materials.GUN_METAL);
        model.box(1, 0, 7, 3, 4, 9, Materials.GUN_GRIP);
        model.box(1, 0, 8, 3, 1, 10, Materials.GUN_METAL);         // extended magazine
        model.box(0, 5, 2, 0, 7, 4, Materials.GUN_METAL);         // machine-pistol brace
        model.box(4, 5, 2, 4, 7, 4, Materials.GUN_METAL);
        model.box(2, 8, 3, 2, 9, 4, Materials.GUN_BODY);
        return model.pivot(2f, 2f, 8f);
    }

    private static VoxelModel mag5() {
        VoxelModel model = new VoxelModel(5, 9, 12);
        model.box(0, 4, 2, 4, 7, 9, Materials.GUN_BODY);
        model.box(1, 5, 0, 3, 6, 3, Materials.GUN_METAL);          // compensator
        model.box(1, 0, 8, 3, 3, 10, Materials.GUN_GRIP);
        model.box(0, 3, 1, 4, 4, 3, Materials.GUN_METAL);
        model.box(1, 8, 4, 3, 8, 7, Materials.GUN_METAL);          // heavy top rail
        return model.pivot(2f, 1f, 9f);
    }

    private static VoxelModel ranger50() {
        VoxelModel model = new VoxelModel(5, 9, 13);
        model.box(2, 6, 0, 2, 6, 7, Materials.GUN_METAL);          // long barrel
        model.box(1, 4, 5, 3, 7, 8, Materials.GUN_BODY);           // cylinder
        model.box(0, 3, 6, 4, 6, 8, Materials.GUN_METAL);
        model.box(1, 0, 8, 3, 3, 11, Materials.GUN_WOOD);
        model.box(2, 8, 2, 2, 8, 3, Materials.GUN_METAL);
        return model.pivot(2f, 2f, 9f);
    }

    private static VoxelModel sidewinder() {
        VoxelModel model = new VoxelModel(5, 10, 15);
        model.box(1, 6, 0, 3, 7, 8, Materials.GUN_METAL);
        model.box(0, 4, 6, 4, 8, 10, Materials.GUN_BODY);          // oversized cylinder
        model.box(1, 0, 10, 3, 4, 13, Materials.GUN_WOOD);
        model.box(0, 5, 1, 4, 5, 3, Materials.GUN_BODY);           // muzzle brake
        model.box(2, 9, 3, 2, 9, 7, Materials.GUN_METAL);
        model.box(0, 3, 9, 4, 3, 10, Materials.GUN_GRIP);
        return model.pivot(2f, 2f, 11f);
    }

    private static VoxelModel spectre() {
        VoxelModel model = new VoxelModel(5, 9, 15);
        model.box(1, 4, 2, 3, 6, 11, Materials.GUN_BODY);
        model.box(2, 5, 0, 2, 5, 4, Materials.GUN_METAL);
        model.box(1, 0, 6, 3, 3, 8, Materials.GUN_METAL);
        model.box(1, 1, 9, 3, 3, 11, Materials.GUN_GRIP);
        model.box(1, 4, 12, 3, 5, 14, Materials.GUN_METAL);
        model.box(2, 7, 4, 2, 7, 6, Materials.GUN_METAL);
        return model.pivot(2f, 2f, 10f);
    }

    private static VoxelModel hornet() {
        VoxelModel model = new VoxelModel(7, 10, 17);
        model.box(1, 4, 3, 5, 7, 13, Materials.GUN_BODY);
        model.box(3, 6, 0, 3, 6, 5, Materials.GUN_METAL);
        model.box(0, 3, 5, 1, 5, 9, Materials.GUN_WOOD);           // twin side shrouds
        model.box(5, 3, 5, 6, 5, 9, Materials.GUN_WOOD);
        model.box(2, 0, 9, 4, 3, 11, Materials.GUN_METAL);
        model.box(2, 1, 12, 4, 3, 14, Materials.GUN_GRIP);
        model.box(1, 5, 14, 5, 6, 16, Materials.GUN_BODY);
        model.box(2, 8, 5, 4, 9, 7, Materials.GUN_METAL);
        return model.pivot(3f, 2f, 12f);
    }

    private static VoxelModel uziK() {
        VoxelModel model = new VoxelModel(5, 10, 13);
        model.box(1, 4, 2, 3, 8, 10, Materials.GUN_BODY);          // upright receiver
        model.box(2, 6, 0, 2, 6, 3, Materials.GUN_METAL);
        model.box(1, 0, 7, 3, 4, 9, Materials.GUN_GRIP);           // magazine in grip
        model.box(0, 3, 9, 4, 4, 10, Materials.GUN_METAL);
        model.box(0, 7, 4, 0, 8, 7, Materials.GUN_METAL);
        model.box(4, 7, 4, 4, 8, 7, Materials.GUN_METAL);
        model.box(1, 5, 11, 3, 6, 12, Materials.GUN_GRIP);
        return model.pivot(2f, 2f, 8f);
    }

    private static VoxelModel mp7K() {
        VoxelModel model = new VoxelModel(5, 9, 17);
        model.box(1, 4, 2, 3, 6, 13, Materials.GUN_BODY);
        model.box(2, 5, 0, 2, 5, 4, Materials.GUN_METAL);
        model.box(1, 0, 8, 3, 3, 10, Materials.GUN_METAL);
        model.box(1, 1, 11, 3, 3, 13, Materials.GUN_GRIP);
        model.box(1, 1, 5, 3, 3, 6, Materials.GUN_GRIP);           // folding foregrip
        model.box(0, 7, 4, 4, 7, 11, Materials.GUN_METAL);
        model.box(2, 5, 14, 2, 5, 16, Materials.GUN_METAL);        // wire stock
        return model.pivot(2f, 2f, 12f);
    }

    private static VoxelModel vector9() {
        VoxelModel model = new VoxelModel(7, 10, 18);
        model.box(1, 5, 3, 5, 7, 12, Materials.GUN_BODY);
        model.box(2, 3, 6, 4, 5, 14, Materials.GUN_BODY);          // dropped vector housing
        model.box(3, 6, 0, 3, 6, 5, Materials.GUN_METAL);
        model.box(2, 0, 10, 4, 3, 12, Materials.GUN_METAL);
        model.box(1, 1, 13, 3, 4, 15, Materials.GUN_GRIP);
        model.box(0, 6, 4, 0, 8, 9, Materials.GUN_WOOD);
        model.box(6, 6, 4, 6, 8, 9, Materials.GUN_WOOD);
        model.box(1, 5, 15, 5, 6, 17, Materials.GUN_METAL);
        return model.pivot(3f, 2f, 13f);
    }

    private static VoxelModel pumper8() {
        VoxelModel model = new VoxelModel(5, 9, 21);
        model.box(1, 4, 2, 3, 6, 16, Materials.GUN_BODY);
        model.box(1, 5, 0, 3, 6, 5, Materials.GUN_METAL);
        model.box(1, 3, 5, 3, 4, 10, Materials.GUN_WOOD);          // pump
        model.box(1, 1, 13, 3, 3, 15, Materials.GUN_GRIP);
        model.box(1, 4, 16, 3, 6, 20, Materials.GUN_WOOD);
        model.box(2, 7, 3, 2, 7, 4, Materials.GUN_METAL);
        return model.pivot(2f, 2f, 13f);
    }

    private static VoxelModel breaker12() {
        VoxelModel model = new VoxelModel(7, 9, 24);
        model.box(1, 5, 0, 2, 6, 16, Materials.GUN_METAL);         // twin barrels
        model.box(4, 5, 0, 5, 6, 16, Materials.GUN_METAL);
        model.box(1, 4, 8, 5, 6, 18, Materials.GUN_BODY);
        model.box(2, 1, 15, 4, 3, 18, Materials.GUN_GRIP);
        model.box(1, 4, 18, 5, 7, 23, Materials.GUN_WOOD);
        model.box(2, 7, 12, 4, 8, 13, Materials.GUN_METAL);
        return model.pivot(3f, 2f, 16f);
    }

    private static VoxelModel autoSlug() {
        VoxelModel model = new VoxelModel(5, 10, 23);
        model.box(1, 4, 3, 3, 7, 18, Materials.GUN_BODY);
        model.box(2, 6, 0, 2, 6, 6, Materials.GUN_METAL);
        model.box(1, 1, 12, 3, 3, 15, Materials.GUN_GRIP);
        model.box(1, 0, 9, 3, 3, 11, Materials.GUN_METAL);         // box magazine
        model.box(1, 3, 5, 3, 4, 9, Materials.GUN_WOOD);
        model.box(1, 5, 18, 3, 7, 22, Materials.GUN_BODY);
        model.box(0, 8, 7, 4, 9, 15, Materials.GUN_METAL);
        return model.pivot(2f, 2f, 14f);
    }

    private static VoxelModel drum12() {
        VoxelModel model = new VoxelModel(7, 11, 23);
        model.box(1, 5, 3, 5, 8, 18, Materials.GUN_BODY);
        model.box(3, 7, 0, 3, 7, 6, Materials.GUN_METAL);
        model.box(0, 0, 9, 6, 5, 14, Materials.GUN_METAL);         // drum
        model.box(1, 1, 15, 3, 4, 17, Materials.GUN_GRIP);
        model.box(1, 5, 18, 5, 7, 22, Materials.GUN_BODY);
        model.box(0, 4, 4, 6, 5, 8, Materials.GUN_WOOD);
        model.box(2, 9, 5, 4, 10, 9, Materials.GUN_METAL);
        return model.pivot(3f, 2f, 15f);
    }

    private static VoxelModel famasB() {
        VoxelModel model = new VoxelModel(7, 11, 23);
        model.box(1, 4, 4, 5, 7, 18, Materials.GUN_BODY);
        model.box(3, 6, 0, 3, 6, 7, Materials.GUN_METAL);
        model.box(1, 3, 5, 5, 4, 10, Materials.GUN_WOOD);
        model.box(2, 1, 11, 4, 3, 14, Materials.GUN_GRIP);
        model.box(1, 0, 16, 5, 3, 19, Materials.GUN_METAL);        // rear bullpup magazine
        model.box(1, 5, 18, 5, 7, 22, Materials.GUN_BODY);
        model.box(1, 8, 7, 5, 9, 17, Materials.GUN_BODY);          // signature carry handle
        model.box(2, 10, 8, 4, 10, 9, Materials.GUN_METAL);
        model.box(2, 10, 15, 4, 10, 16, Materials.GUN_METAL);
        return model.pivot(3f, 2f, 14f);
    }

    private static VoxelModel bullpupA() {
        VoxelModel model = new VoxelModel(7, 10, 22);
        model.box(1, 4, 3, 5, 7, 18, Materials.GUN_BODY);
        model.box(3, 6, 0, 3, 6, 6, Materials.GUN_METAL);
        model.box(1, 3, 4, 5, 4, 9, Materials.GUN_WOOD);
        model.box(2, 1, 10, 4, 3, 13, Materials.GUN_GRIP);
        model.box(1, 0, 16, 5, 3, 19, Materials.GUN_METAL);
        model.box(0, 5, 17, 6, 7, 21, Materials.GUN_BODY);
        model.box(1, 8, 5, 5, 9, 14, Materials.GUN_METAL);         // full optic rail
        return model.pivot(3f, 2f, 13f);
    }

    private static VoxelModel ak15() {
        VoxelModel model = new VoxelModel(5, 10, 25);
        model.box(1, 4, 4, 3, 7, 19, Materials.GUN_BODY);
        model.box(2, 6, 0, 2, 6, 7, Materials.GUN_METAL);
        model.box(1, 3, 5, 3, 4, 10, Materials.GUN_WOOD);
        model.box(1, 2, 11, 3, 4, 14, Materials.GUN_METAL);        // curved magazine steps
        model.box(1, 0, 13, 3, 2, 16, Materials.GUN_METAL);
        model.box(1, 1, 16, 3, 3, 18, Materials.GUN_GRIP);
        model.box(1, 4, 19, 3, 7, 24, Materials.GUN_WOOD);
        model.box(2, 8, 6, 2, 9, 7, Materials.GUN_METAL);
        model.box(2, 8, 17, 2, 8, 18, Materials.GUN_METAL);
        return model.pivot(2f, 2f, 16f);
    }

    private static VoxelModel vektorAr() {
        VoxelModel model = new VoxelModel(5, 11, 24);
        model.box(0, 4, 3, 4, 7, 19, Materials.GUN_BODY);
        model.box(2, 6, 0, 2, 6, 5, Materials.GUN_METAL);
        model.box(0, 3, 4, 4, 4, 10, Materials.GUN_WOOD);
        model.box(1, 0, 11, 3, 3, 14, Materials.GUN_METAL);
        model.box(1, 1, 15, 3, 3, 17, Materials.GUN_GRIP);
        model.box(0, 5, 19, 4, 7, 22, Materials.GUN_BODY);
        model.box(2, 5, 22, 2, 6, 23, Materials.GUN_METAL);        // skeleton stock tail
        model.box(0, 8, 4, 4, 8, 17, Materials.GUN_METAL);
        model.box(1, 9, 8, 3, 10, 11, Materials.GLASS);            // compact holo
        return model.pivot(2f, 2f, 16f);
    }

    private static VoxelModel m4Falcon() {
        VoxelModel model = new VoxelModel(5, 10, 25);
        model.box(1, 4, 4, 3, 7, 18, Materials.GUN_BODY);
        model.box(2, 6, 0, 2, 6, 7, Materials.GUN_METAL);
        model.box(0, 3, 4, 4, 4, 9, Materials.GUN_BODY);
        model.box(1, 0, 10, 3, 3, 13, Materials.GUN_METAL);
        model.box(1, 1, 14, 3, 3, 17, Materials.GUN_GRIP);
        model.box(2, 5, 18, 2, 6, 21, Materials.GUN_METAL);        // buffer tube
        model.box(0, 4, 21, 4, 7, 24, Materials.GUN_BODY);
        model.box(0, 8, 5, 4, 8, 17, Materials.GUN_METAL);
        model.box(2, 9, 6, 2, 9, 7, Materials.GUN_METAL);
        return model.pivot(2f, 2f, 15f);
    }

    private static VoxelModel scarH() {
        VoxelModel model = new VoxelModel(7, 11, 26);
        model.box(1, 4, 4, 5, 8, 20, Materials.GUN_BODY);
        model.box(3, 7, 0, 3, 7, 7, Materials.GUN_METAL);
        model.box(0, 3, 5, 6, 5, 10, Materials.GUN_WOOD);
        model.box(2, 0, 11, 4, 3, 15, Materials.GUN_METAL);
        model.box(2, 1, 16, 4, 3, 19, Materials.GUN_GRIP);
        model.box(1, 5, 20, 5, 8, 25, Materials.GUN_WOOD);
        model.box(0, 9, 5, 6, 9, 18, Materials.GUN_METAL);
        model.box(2, 10, 11, 4, 10, 13, Materials.GUN_BODY);
        return model.pivot(3f, 2f, 17f);
    }

    private static VoxelModel scoutV() {
        VoxelModel model = new VoxelModel(5, 11, 27);
        model.box(1, 4, 3, 3, 6, 22, Materials.GUN_BODY);
        model.box(2, 5, 0, 2, 5, 8, Materials.GUN_METAL);
        model.box(1, 1, 14, 3, 3, 16, Materials.GUN_GRIP);
        model.box(1, 3, 10, 3, 4, 13, Materials.GUN_METAL);
        model.box(1, 4, 21, 3, 7, 26, Materials.GUN_WOOD);
        model.box(1, 7, 9, 3, 9, 17, Materials.GUN_METAL);
        model.box(2, 8, 17, 2, 8, 17, Materials.GLASS);
        return model.pivot(2f, 2f, 15f);
    }

    private static VoxelModel dragunovS() {
        VoxelModel model = new VoxelModel(7, 12, 30);
        model.box(1, 4, 4, 5, 7, 24, Materials.GUN_BODY);
        model.box(3, 6, 0, 3, 6, 9, Materials.GUN_METAL);
        model.box(1, 3, 5, 5, 5, 12, Materials.GUN_WOOD);
        model.box(2, 0, 14, 4, 3, 17, Materials.GUN_METAL);
        model.box(2, 1, 18, 4, 3, 20, Materials.GUN_GRIP);
        model.box(1, 4, 23, 5, 8, 29, Materials.GUN_WOOD);
        model.box(1, 8, 10, 5, 10, 19, Materials.GUN_METAL);
        model.box(2, 9, 19, 4, 9, 19, Materials.GLASS);
        model.box(0, 8, 9, 1, 9, 20, Materials.GUN_BODY);          // side-mounted optic
        return model.pivot(3f, 2f, 19f);
    }

    private static VoxelModel awmX() {
        VoxelModel model = new VoxelModel(7, 13, 32);
        model.box(1, 4, 4, 5, 8, 25, Materials.GUN_BODY);
        model.box(3, 7, 0, 3, 7, 10, Materials.GUN_METAL);
        model.box(0, 3, 5, 6, 5, 12, Materials.GUN_WOOD);
        model.box(2, 0, 16, 4, 3, 19, Materials.GUN_METAL);
        model.box(2, 1, 20, 4, 3, 22, Materials.GUN_GRIP);
        model.box(1, 4, 25, 5, 8, 31, Materials.GUN_BODY);
        model.box(0, 9, 9, 6, 11, 21, Materials.GUN_METAL);        // large optic
        model.box(1, 10, 21, 5, 10, 21, Materials.GLASS);
        model.box(5, 5, 14, 6, 6, 16, Materials.CHROME);           // bolt handle
        return model.pivot(3f, 2f, 20f);
    }

    private static VoxelModel barrettM() {
        VoxelModel model = new VoxelModel(9, 14, 35);
        model.box(1, 5, 5, 7, 9, 28, Materials.GUN_BODY);
        model.box(4, 8, 0, 4, 8, 11, Materials.GUN_METAL);
        model.box(1, 6, 0, 7, 7, 4, Materials.GUN_METAL);          // broad muzzle brake
        model.box(2, 0, 16, 6, 4, 21, Materials.GUN_METAL);
        model.box(3, 1, 22, 5, 4, 25, Materials.GUN_GRIP);
        model.box(1, 5, 28, 7, 9, 34, Materials.GUN_BODY);
        model.box(1, 10, 10, 7, 12, 24, Materials.GUN_METAL);
        model.box(2, 11, 24, 6, 11, 24, Materials.GLASS);
        model.box(0, 3, 7, 1, 5, 12, Materials.GUN_WOOD);          // folded bipod
        model.box(7, 3, 7, 8, 5, 12, Materials.GUN_WOOD);
        return model.pivot(4f, 3f, 22f);
    }

    private static VoxelModel bulldogLmg() {
        VoxelModel model = new VoxelModel(7, 12, 28);
        model.box(1, 5, 4, 5, 8, 22, Materials.GUN_BODY);
        model.box(3, 7, 0, 3, 7, 8, Materials.GUN_METAL);
        model.box(0, 0, 9, 6, 5, 15, Materials.GUN_METAL);         // ammunition box
        model.box(2, 2, 16, 4, 4, 19, Materials.GUN_GRIP);
        model.box(1, 5, 22, 5, 8, 27, Materials.GUN_BODY);
        model.box(2, 9, 7, 4, 11, 12, Materials.GUN_METAL);        // carry handle
        model.box(0, 3, 2, 1, 5, 5, Materials.GUN_WOOD);
        model.box(5, 3, 2, 6, 5, 5, Materials.GUN_WOOD);
        return model.pivot(3f, 3f, 17f);
    }

    private static VoxelModel negevM() {
        VoxelModel model = new VoxelModel(9, 14, 30);
        model.box(1, 6, 4, 7, 10, 24, Materials.GUN_BODY);
        model.box(4, 9, 0, 4, 9, 9, Materials.GUN_METAL);
        model.box(0, 0, 9, 8, 6, 17, Materials.GUN_METAL);         // deep belt box
        model.box(3, 2, 18, 5, 5, 21, Materials.GUN_GRIP);
        model.box(1, 6, 24, 7, 10, 29, Materials.GUN_BODY);
        model.box(2, 11, 7, 6, 13, 15, Materials.GUN_METAL);
        model.box(0, 4, 2, 2, 6, 7, Materials.GUN_WOOD);           // deployed bipod silhouette
        model.box(6, 4, 2, 8, 6, 7, Materials.GUN_WOOD);
        model.box(1, 5, 5, 7, 5, 10, Materials.GUN_METAL);
        return model.pivot(4f, 3f, 19f);
    }

    private static VoxelModel rpgV() {
        VoxelModel model = new VoxelModel(7, 11, 29);
        model.box(2, 4, 3, 4, 8, 24, Materials.GUN_METAL);
        model.box(1, 5, 0, 5, 7, 4, Materials.PAINT_RED);          // warhead
        model.box(0, 6, 2, 6, 6, 3, Materials.GUN_BODY);
        model.box(2, 1, 14, 4, 3, 17, Materials.GUN_GRIP);
        model.box(1, 9, 8, 5, 10, 13, Materials.GUN_BODY);
        model.box(1, 3, 23, 5, 9, 28, Materials.GUN_BODY);         // exhaust cone
        model.box(0, 4, 18, 1, 6, 21, Materials.GUN_WOOD);
        model.box(5, 4, 18, 6, 6, 21, Materials.GUN_WOOD);
        return model.pivot(3f, 2f, 17f);
    }

    /** The horde's chipped blade: the smaller, plainer shape. */
    private static VoxelModel scrapKnife() {
        VoxelModel model = new VoxelModel(3, 7, 12);
        model.box(1, 3, 0, 1, 5, 7, Materials.GUN_METAL);
        model.box(0, 3, 7, 2, 5, 8, Materials.GUN_BODY);
        model.box(1, 2, 8, 1, 5, 11, Materials.GUN_GRIP);
        return model.pivot(1f, 3f, 9f);
    }

    /**
     * The combat knife: a chromed edge, a crossguard and a wrapped grip, so it reads as a weapon at
     * view model scale rather than as a sliver.
     */
    private static VoxelModel knife() {
        VoxelModel model = new VoxelModel(3, 9, 15);
        model.box(1, 3, 0, 1, 3, 8, Materials.CHROME);            // edge
        model.box(1, 4, 1, 1, 6, 8, Materials.GUN_METAL);         // blade body
        model.box(1, 6, 2, 1, 6, 6, Materials.CHROME);            // fuller highlight
        model.box(0, 2, 9, 2, 7, 9, Materials.GUN_BODY);          // crossguard
        model.box(1, 3, 10, 1, 6, 13, Materials.GUN_GRIP);        // grip
        model.box(0, 4, 11, 2, 5, 12, Materials.GUN_BODY);        // wrap
        model.box(0, 3, 14, 2, 6, 14, Materials.GUN_METAL);       // pommel
        return model.pivot(1f, 3f, 11f);
    }

    private static VoxelModel fragGrenade() {
        VoxelModel model = new VoxelModel(7, 8, 7);
        model.box(1, 1, 1, 5, 6, 5, Materials.GUN_BODY);
        model.box(0, 2, 2, 6, 3, 4, Materials.GUN_METAL);          // fragmentation ribs
        model.box(2, 2, 0, 4, 5, 6, Materials.GUN_METAL);
        model.box(2, 7, 2, 4, 7, 4, Materials.GUN_METAL);
        model.box(1, 7, 3, 1, 7, 5, Materials.GUN_METAL);          // safety lever
        return model.pivot(3.5f, 3.5f, 3.5f);
    }

    private static VoxelModel flashGrenade() {
        VoxelModel model = new VoxelModel(5, 9, 5);
        model.box(1, 1, 1, 3, 6, 3, Materials.PLASTIC_WHITE);
        model.box(0, 2, 1, 4, 5, 3, Materials.GUN_BODY);
        model.box(1, 2, 0, 3, 5, 4, Materials.GUN_BODY);
        model.box(0, 2, 2, 4, 2, 2, Materials.SKY_BLUE);           // identification bands
        model.box(0, 5, 2, 4, 5, 2, Materials.SKY_BLUE);
        model.box(2, 7, 2, 2, 8, 2, Materials.GUN_METAL);
        model.box(1, 8, 2, 1, 8, 4, Materials.GUN_METAL);
        model.box(2, 5, 2, 2, 5, 2, Materials.GUN_METAL);
        return model.pivot(2.5f, 4f, 2.5f);
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
