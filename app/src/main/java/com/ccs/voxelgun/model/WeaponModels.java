package com.ccs.voxelgun.model;

import com.ccs.voxelgun.voxel.Materials;

/**
 * Authored half-unit voxel weapons. Broad panels merge into inexpensive mesh faces; the smaller
 * grid is reserved for silhouettes, open trigger guards, sight housings and recessed hardware.
 * Local -Z is the muzzle direction and every pivot sits inside the firing hand's grip.
 */
final class WeaponModels {
    private static final int BODY = Materials.GUN_BODY;
    private static final int METAL = Materials.GUN_METAL;
    private static final int GRIP = Materials.GUN_GRIP;
    private static final int WOOD = Materials.GUN_WOOD;
    private static final int DARK = Materials.VISOR;
    private static final int AIR = Materials.AIR;

    private WeaponModels() {}

    static VoxelModel create(String key) {
        if (key == null) return famas();
        switch (key) {
            case "knife": return knife(false);
            case "scrap_knife": return knife(true);
            case "pistol": case "p9": return p9();
            case "talon_s": return talon();
            case "auto_9": return auto9();
            case "mag_5": return mag5();
            case "revolver": case "ranger_50": return revolver(false);
            case "sidewinder": return revolver(true);
            case "smg": case "spectre": return spectre();
            case "hornet": return hornet();
            case "uzi_k": return uzi();
            case "mp7_k": return mp7();
            case "vector_9": return vector();
            case "shotgun": case "pumper_8": return pumper();
            case "breaker_12": return breaker();
            case "auto_slug": return autoSlug();
            case "drum_12": return drum();
            case "rifle": case "famas_b": return famas();
            case "bullpup_a": return bullpup();
            case "ak_15": return ak();
            case "vektor_ar": return vektor();
            case "m4_falcon": return falcon();
            case "scar_h": return scar();
            case "sniper": case "scout_v": return scout();
            case "dragunov_s": return dragunov();
            case "awm_x": return awm();
            case "barrett_m": return barrett();
            case "lmg": case "bulldog_lmg": return bulldog();
            case "negev_m": return negev();
            case "launcher": case "rpg_v": return rpg();
            case "grenade": case "frag_grenade": return grenade(false);
            case "flash_grenade": return grenade(true);
            default: return famas();
        }
    }

    private static VoxelModel p9() {
        Gun g = new Gun(6, 16, 18, 12, 4);
        g.pistolFrame(2, 14, 10, METAL);
        g.b(-1, 9, 0, 1, 11, 4, BODY);
        g.b(0, 10, 0, 0, 10, 1, AIR);
        g.b(-2, 6, 5, 1, 7, 8, BODY);
        g.b(2, 10, 7, 2, 11, 10, DARK); // Ejection recess.
        g.b(2, 10, 8, 2, 10, 9, METAL);
        g.pistolSights(3, 13, 13);
        return g.model;
    }

    private static VoxelModel talon() {
        Gun g = new Gun(6, 16, 26, 20, 4);
        g.pistolFrame(10, 22, 18, BODY);
        g.tube(10, 0, 11, 2, DARK, true);
        g.b(-2, 9, 4, 2, 9, 5, METAL); // Suppressor collar, recessed lower seam.
        g.b(-2, 10, 14, -2, 11, 17, Materials.CORAL);
        g.b(2, 10, 14, 2, 11, 17, Materials.CORAL);
        g.pistolSights(11, 21, 13);
        return g.model;
    }

    private static VoxelModel auto9() {
        Gun g = new Gun(10, 20, 22, 16, 5);
        g.pistolFrame(3, 17, 13, BODY);
        g.b(-2, 12, 4, 2, 14, 15, METAL);
        g.b(-3, 10, 5, -3, 12, 10, Materials.MINT);
        g.b(3, 10, 5, 3, 12, 10, Materials.MINT);
        g.tube(11, 0, 5, 2, METAL, true);
        g.b(-1, 0, 15, 1, 3, 20, METAL); // Extended magazine behind the grip.
        g.b(-2, 0, 17, 2, 1, 21, GRIP);
        g.b(-4, 8, 6, -3, 10, 14, BODY);
        g.b(3, 8, 6, 4, 10, 14, BODY);
        g.holo(7, 14, 15);
        return g.model;
    }

    private static VoxelModel mag5() {
        Gun g = new Gun(10, 18, 24, 18, 4);
        g.pistolFrame(4, 19, 15, METAL);
        g.b(-3, 9, 5, 3, 12, 15, BODY);
        g.b(-3, 11, 6, -3, 11, 12, Materials.COPPER);
        g.b(3, 11, 6, 3, 11, 12, Materials.COPPER);
        g.tube(11, 0, 5, 3, METAL, true);
        g.b(-3, 10, 1, 3, 10, 1, AIR); // Compensator side ports.
        g.b(-3, 12, 3, 3, 12, 3, AIR);
        g.pistolSights(6, 18, 14);
        g.b(-2, 0, 17, 2, 1, 22, METAL);
        return g.model;
    }

    private static VoxelModel revolver(boolean heavy) {
        Gun g = new Gun(10, heavy ? 20 : 18, heavy ? 30 : 26, heavy ? 22 : 18, 5);
        int cylinder = heavy ? 15 : 11;
        int grip = heavy ? 20 : 16;
        g.tube(12, 0, cylinder + 1, heavy ? 2 : 1, METAL, true);
        g.b(-1, 9, 2, 1, 10, cylinder, BODY); // Separate under-barrel ejector rod.
        g.b(-2, 10, cylinder - 1, 2, 13, cylinder + 6, BODY);
        g.tube(11, cylinder, cylinder + 5, 3, METAL, false);
        g.b(-3, 10, cylinder + 1, -3, 12, cylinder + 1, DARK);
        g.b(3, 10, cylinder + 3, 3, 12, cylinder + 3, DARK);
        g.b(-1, 8, cylinder + 2, 1, 8, cylinder + 4, DARK);
        g.grip(grip, WOOD);
        g.trigger(grip - 4, grip + 1, 7);
        g.b(-1, 14, grip - 1, 1, 15, grip + 1, BODY); // Exposed hammer.
        g.b(-1, 16, grip, 1, 16, grip + 2, METAL);
        g.pistolSights(3, cylinder + 4, 15);
        if (heavy) {
            g.b(-4, 10, 1, 4, 11, 5, BODY);
            g.b(-4, 10, 2, -3, 11, 3, AIR);
            g.b(3, 10, 2, 4, 11, 3, AIR);
            g.b(-2, 7, grip + 1, -2, 8, grip + 3, Materials.COPPER);
        }
        return g.model;
    }

    private static VoxelModel spectre() {
        Gun g = new Gun(10, 18, 30, 20, 5);
        g.receiver(7, 23, 2, 9, 14, BODY);
        g.tube(12, 0, 10, 1, METAL, true);
        g.b(-2, 9, 5, 2, 13, 10, GRIP);
        g.magazine(12, 5, 3, METAL, false);
        g.grip(18, GRIP);
        g.trigger(14, 19, 7);
        g.wireStock(24, 29, 12);
        g.rail(8, 22, 15);
        g.ironSights(8, 21, 16);
        g.sideStripe(8, 13, 13, Materials.SKY_BLUE, 2);
        return g.model;
    }

    private static VoxelModel hornet() {
        Gun g = new Gun(14, 20, 34, 25, 5);
        g.receiver(9, 27, 3, 9, 15, BODY);
        g.tube(13, 0, 10, 2, METAL, true);
        g.b(-5, 8, 9, -3, 12, 18, WOOD);
        g.b(3, 8, 9, 5, 12, 18, WOOD);
        g.vents(10, 17, 11, 5);
        g.magazine(18, 5, 3, METAL, false);
        g.grip(23, GRIP);
        g.trigger(19, 24, 7);
        g.slimStock(28, 33, BODY);
        g.rail(10, 24, 16);
        g.holo(12, 17, 16);
        g.sideStripe(20, 24, 14, Materials.SUNNY, 3);
        return g.model;
    }

    private static VoxelModel uzi() {
        Gun g = new Gun(10, 20, 26, 16, 5);
        g.receiver(5, 21, 3, 10, 16, BODY);
        g.b(-2, 9, 4, 2, 13, 10, GRIP);
        g.tube(13, 0, 7, 1, METAL, true);
        g.grip(13, GRIP);
        g.b(-1, 0, 15, 1, 3, 19, METAL);
        g.trigger(8, 14, 8);
        g.b(-2, 17, 8, 2, 17, 15, METAL);
        g.b(0, 18, 12, 0, 19, 14, BODY); // Top charging handle.
        g.wireStock(21, 25, 12);
        g.b(-3, 11, 7, -3, 11, 8, DARK);
        g.b(3, 11, 7, 3, 11, 8, DARK);
        g.ironSights(5, 19, 17);
        return g.model;
    }

    private static VoxelModel mp7() {
        Gun g = new Gun(10, 18, 34, 24, 5);
        g.receiver(6, 26, 2, 9, 14, BODY);
        g.tube(12, 0, 9, 1, METAL, true);
        g.grip(21, GRIP);
        g.b(-1, 1, 22, 1, 3, 26, METAL);
        g.trigger(16, 22, 7);
        g.b(-1, 3, 10, 1, 9, 12, GRIP); // Folding forward grip.
        g.b(-2, 3, 10, 2, 4, 12, METAL);
        g.rail(7, 25, 15);
        g.ironSights(8, 23, 16);
        g.wireStock(27, 33, 12);
        g.sideStripe(7, 15, 11, Materials.MINT, 2);
        return g.model;
    }

    private static VoxelModel vector() {
        Gun g = new Gun(14, 20, 36, 27, 5);
        g.receiver(7, 26, 3, 12, 16, BODY);
        g.tube(14, 0, 9, 2, METAL, true);
        g.b(-3, 5, 15, 3, 11, 25, BODY); // Distinct dropped recoil housing.
        g.b(-3, 7, 17, -3, 10, 23, WOOD);
        g.b(3, 7, 17, 3, 10, 23, WOOD);
        g.magazine(19, 5, 3, METAL, false);
        g.grip(25, GRIP);
        g.trigger(22, 26, 9);
        g.wireStock(28, 35, 14);
        g.rail(8, 25, 17);
        g.ironSights(9, 24, 18);
        g.b(-4, 12, 9, -4, 14, 14, Materials.CORAL);
        g.b(4, 12, 9, 4, 14, 14, Materials.CORAL);
        return g.model;
    }

    private static VoxelModel pumper() {
        Gun g = new Gun(10, 18, 42, 28, 5);
        g.receiver(18, 32, 2, 9, 14, BODY);
        g.tube(13, 0, 22, 1, METAL, true);
        g.tube(9, 3, 20, 1, METAL, false);
        g.b(-2, 8, 9, 2, 11, 19, WOOD);
        g.ribs(10, 18, 8, 11, 2, GRIP);
        g.grip(26, GRIP);
        g.trigger(22, 27, 7);
        g.woodStock(32, 41);
        g.ironSights(3, 27, 15);
        g.b(2, 10, 22, 2, 12, 27, DARK);
        g.b(2, 11, 23, 2, 11, 26, METAL);
        return g.model;
    }

    private static VoxelModel breaker() {
        Gun g = new Gun(14, 18, 48, 34, 5);
        for (int x : new int[]{-3, 3}) {
            g.box(x - 1, 11, 0, x + 1, 14, 31, METAL);
            g.box(x, 12, 0, x, 13, 2, AIR);
        }
        g.b(-1, 12, 3, 1, 13, 30, BODY); // Narrow connecting barrel rib.
        g.receiver(27, 37, 4, 9, 14, BODY);
        g.b(-4, 9, 18, 4, 10, 28, WOOD);
        g.grip(32, WOOD);
        g.trigger(28, 33, 7);
        g.woodStock(37, 47);
        g.b(-1, 15, 29, 1, 16, 33, METAL); // Break lever.
        g.b(0, 15, 3, 0, 16, 4, Materials.CORAL);
        return g.model;
    }

    private static VoxelModel autoSlug() {
        Gun g = new Gun(10, 20, 46, 29, 5);
        g.receiver(15, 35, 2, 9, 15, BODY);
        g.tube(13, 0, 18, 1, METAL, true);
        g.b(-2, 9, 7, 2, 12, 17, WOOD);
        g.ribs(8, 16, 9, 12, 2, GRIP);
        g.magazine(19, 6, 4, METAL, false);
        g.grip(27, GRIP);
        g.trigger(23, 28, 7);
        g.slimStock(35, 45, BODY);
        g.rail(16, 33, 16);
        g.holo(23, 29, 16);
        return g.model;
    }

    private static VoxelModel drum() {
        Gun g = new Gun(14, 22, 46, 31, 5);
        g.receiver(11, 35, 3, 11, 17, BODY);
        g.tube(15, 0, 14, 2, METAL, true);
        g.b(-4, 10, 8, 4, 13, 17, WOOD);
        g.vents(9, 16, 11, 4);
        g.drum(21, 6, 5, 7);
        g.grip(29, GRIP);
        g.trigger(25, 30, 8);
        g.slimStock(35, 45, BODY);
        g.rail(13, 33, 18);
        g.holo(16, 23, 18);
        return g.model;
    }

    private static VoxelModel famas() {
        Gun g = new Gun(14, 22, 46, 29, 5);
        g.receiver(11, 37, 3, 9, 15, BODY);
        g.tube(13, 0, 14, 1, METAL, true);
        g.b(-3, 8, 11, 3, 11, 21, WOOD);
        g.vents(12, 20, 10, 3);
        g.grip(26, GRIP);
        g.trigger(21, 27, 7);
        g.magazine(34, 6, 4, METAL, false);
        g.b(-3, 11, 37, 3, 15, 43, BODY);
        g.b(-3, 9, 43, 3, 16, 45, GRIP);
        // Raised carry bridge has a real opening, not a solid rectangular optic slab.
        g.b(-2, 16, 15, 2, 20, 17, BODY);
        g.b(-2, 16, 31, 2, 20, 33, BODY);
        g.b(-2, 20, 17, 2, 20, 31, BODY);
        g.b(-1, 21, 17, 1, 21, 18, METAL);
        g.b(-1, 21, 30, 1, 21, 31, METAL);
        g.sideStripe(23, 30, 13, Materials.SKY_BLUE, 3);
        return g.model;
    }

    private static VoxelModel bullpup() {
        Gun g = new Gun(14, 20, 44, 27, 5);
        g.receiver(8, 39, 3, 9, 15, WOOD);
        g.tube(13, 0, 11, 1, METAL, true);
        g.b(-3, 8, 9, 3, 10, 18, GRIP);
        g.vents(10, 18, 12, 3);
        g.grip(24, GRIP);
        g.trigger(18, 25, 7);
        g.magazine(33, 7, 4, METAL, false);
        g.b(-4, 10, 37, 4, 15, 41, BODY);
        g.b(-3, 9, 42, 3, 16, 43, GRIP);
        g.rail(10, 35, 16);
        g.holo(14, 23, 16);
        g.sideStripe(27, 32, 13, BODY, 3);
        return g.model;
    }

    private static VoxelModel ak() {
        Gun g = new Gun(10, 20, 50, 33, 5);
        g.receiver(18, 38, 2, 9, 15, BODY);
        g.tube(13, 0, 22, 1, METAL, true);
        g.b(-2, 9, 10, 2, 12, 20, WOOD);
        g.b(-1, 15, 9, 1, 15, 20, BODY); // Gas tube separated by a dark seam.
        g.b(-2, 12, 12, -2, 12, 17, GRIP);
        g.b(2, 12, 12, 2, 12, 17, GRIP);
        g.magazine(23, 7, 5, METAL, true);
        g.grip(31, GRIP);
        g.trigger(27, 32, 7);
        g.woodStock(38, 49);
        g.ironSights(7, 34, 16);
        g.b(3, 12, 31, 4, 12, 33, METAL); // Charging handle.
        return g.model;
    }

    private static VoxelModel vektor() {
        Gun g = new Gun(10, 22, 48, 33, 5);
        g.receiver(11, 37, 3, 9, 15, BODY);
        g.tube(13, 0, 14, 1, METAL, true);
        g.b(-3, 8, 11, 3, 10, 20, WOOD);
        g.vents(12, 20, 12, 3);
        g.magazine(23, 7, 4, METAL, false);
        g.grip(31, GRIP);
        g.trigger(27, 32, 7);
        g.wireStock(38, 47, 13);
        g.rail(10, 35, 16);
        g.holo(18, 26, 17);
        g.sideStripe(13, 19, 14, Materials.MINT, 3);
        return g.model;
    }

    private static VoxelModel falcon() {
        Gun g = new Gun(10, 20, 50, 31, 5);
        g.receiver(19, 37, 2, 9, 15, BODY);
        g.tube(13, 0, 22, 1, METAL, true);
        g.b(-3, 10, 8, 3, 14, 20, BODY);
        g.vents(9, 19, 12, 3);
        g.rail(8, 35, 16);
        g.magazine(21, 7, 4, METAL, false);
        g.grip(29, GRIP);
        g.trigger(25, 30, 7);
        g.slimStock(37, 49, BODY);
        g.ironSights(10, 34, 17);
        g.b(2, 11, 27, 2, 13, 32, DARK);
        g.b(3, 10, 27, 3, 10, 32, METAL);
        return g.model;
    }

    private static VoxelModel scar() {
        Gun g = new Gun(14, 22, 52, 35, 5);
        g.receiver(12, 40, 3, 10, 17, WOOD);
        g.tube(15, 0, 15, 1, METAL, true);
        g.b(-4, 9, 12, 4, 12, 21, BODY);
        g.vents(13, 22, 14, 3);
        g.magazine(23, 7, 5, METAL, false);
        g.grip(33, GRIP);
        g.trigger(28, 34, 8);
        g.slimStock(40, 51, WOOD);
        g.rail(12, 38, 18);
        g.ironSights(14, 37, 19);
        g.b(-4, 14, 26, -4, 14, 30, METAL);
        g.b(3, 12, 30, 3, 14, 35, DARK);
        return g.model;
    }

    private static VoxelModel scout() {
        Gun g = new Gun(10, 22, 54, 31, 5);
        g.receiver(22, 40, 2, 8, 12, BODY);
        g.tube(11, 0, 25, 1, METAL, true);
        g.b(-2, 7, 16, 2, 10, 27, WOOD);
        g.magazine(23, 4, 3, METAL, false);
        g.grip(29, GRIP);
        g.trigger(25, 30, 6);
        g.woodStock(39, 53);
        g.scope(19, 35, 17, 2);
        g.b(3, 10, 33, 4, 10, 35, METAL);
        g.b(4, 8, 35, 4, 10, 36, GRIP);
        return g.model;
    }

    private static VoxelModel dragunov() {
        Gun g = new Gun(14, 24, 60, 39, 5);
        g.receiver(25, 46, 3, 9, 14, BODY);
        g.tube(12, 0, 27, 1, METAL, true);
        g.b(-3, 8, 11, 3, 12, 26, WOOD);
        g.vents(13, 24, 10, 3);
        g.magazine(29, 7, 4, METAL, true);
        g.grip(37, WOOD);
        g.trigger(33, 38, 7);
        g.slimStock(46, 59, WOOD);
        g.b(-3, 7, 42, -2, 10, 53, WOOD); // Thumb-hole stock's lower brace.
        g.b(2, 7, 42, 3, 10, 53, WOOD);
        g.b(-4, 14, 24, -3, 18, 38, BODY); // Side scope mount.
        g.scope(21, 39, 19, 2);
        g.ironSights(7, 42, 15);
        return g.model;
    }

    private static VoxelModel awm() {
        Gun g = new Gun(14, 26, 64, 41, 5);
        g.receiver(27, 49, 3, 10, 16, BODY);
        g.tube(14, 0, 30, 1, METAL, true);
        g.b(-3, 9, 11, 3, 13, 28, WOOD);
        g.vents(13, 25, 11, 3);
        g.magazine(31, 7, 4, METAL, false);
        g.grip(39, GRIP);
        g.trigger(35, 40, 8);
        g.slimStock(49, 63, WOOD);
        g.scope(21, 42, 21, 3);
        g.b(4, 14, 36, 5, 14, 38, METAL);
        g.b(5, 10, 38, 5, 14, 39, METAL);
        g.b(4, 9, 38, 6, 10, 40, GRIP);
        g.bipod(13, 17, 7);
        return g.model;
    }

    private static VoxelModel barrett() {
        Gun g = new Gun(18, 28, 70, 45, 6);
        g.receiver(28, 55, 4, 11, 18, BODY);
        g.tube(16, 0, 31, 2, METAL, true);
        g.b(-6, 13, 0, 6, 17, 7, METAL);
        g.b(-1, 15, 0, 1, 16, 8, AIR);
        for (int z : new int[]{2, 5}) {
            g.b(-6, 14, z, -3, 16, z, AIR);
            g.b(3, 14, z, 6, 16, z, AIR);
        }
        g.b(-4, 10, 15, 4, 15, 29, BODY);
        g.vents(17, 27, 13, 4);
        g.magazine(33, 8, 6, METAL, false);
        g.grip(43, GRIP);
        g.trigger(38, 44, 9);
        g.slimStock(55, 69, BODY);
        g.scope(24, 48, 23, 3);
        g.bipod(17, 24, 9);
        return g.model;
    }

    private static VoxelModel bulldog() {
        Gun g = new Gun(14, 24, 56, 35, 7);
        g.receiver(17, 44, 3, 12, 18, BODY);
        g.tube(16, 0, 20, 2, METAL, true);
        g.b(-3, 11, 8, 3, 14, 18, WOOD);
        g.vents(10, 17, 13, 3);
        g.ammoBox(20, 11, 5);
        g.grip(33, GRIP);
        g.b(-1, 10, 33, 1, 12, 36, GRIP);
        g.trigger(29, 34, 9);
        g.slimStock(44, 55, BODY);
        g.carryHandle(19, 29, 19, 23);
        g.ironSights(7, 40, 19);
        g.bipod(7, 13, 8);
        return g.model;
    }

    private static VoxelModel negev() {
        Gun g = new Gun(18, 28, 60, 39, 8);
        g.receiver(20, 47, 4, 14, 21, BODY);
        g.tube(18, 0, 23, 2, METAL, true);
        g.b(-4, 13, 8, 4, 17, 21, WOOD);
        g.vents(10, 20, 15, 4);
        g.ammoBox(22, 13, 6);
        g.grip(37, GRIP);
        g.b(-2, 9, 37, 2, 14, 41, GRIP);
        g.trigger(32, 38, 11);
        g.slimStock(47, 59, WOOD);
        g.b(-3, 21, 24, 3, 22, 41, METAL); // Feed tray lid.
        g.carryHandle(23, 33, 23, 27);
        g.bipod(7, 15, 10);
        g.ironSights(6, 43, 22);
        return g.model;
    }

    private static VoxelModel rpg() {
        Gun g = new Gun(14, 22, 58, 35, 5);
        g.tube(13, 7, 50, 3, WOOD, false);
        g.b(-2, 10, 17, 2, 16, 39, BODY);
        g.tube(13, 2, 9, 4, Materials.PAINT_RED, false);
        g.tube(13, 0, 3, 2, METAL, false);
        g.b(0, 13, 0, 0, 13, 0, DARK);
        g.tube(13, 48, 53, 4, BODY, false);
        g.tube(13, 53, 57, 5, METAL, false);
        g.b(-2, 11, 55, 2, 15, 57, DARK); // Readable exhaust recess.
        g.grip(33, GRIP);
        g.b(-1, 5, 22, 1, 10, 25, GRIP);
        g.trigger(28, 34, 8);
        g.b(-3, 16, 19, -2, 20, 25, BODY);
        g.b(-1, 19, 21, 2, 20, 24, METAL);
        g.b(0, 20, 22, 1, 20, 23, DARK);
        g.b(-4, 12, 28, -3, 14, 38, GRIP);
        g.b(3, 12, 28, 4, 14, 38, GRIP);
        return g.model;
    }

    private static VoxelModel knife(boolean scrap) {
        Gun g = new Gun(6, scrap ? 14 : 18, scrap ? 24 : 30, scrap ? 19 : 23, scrap ? 7 : 8);
        int guard = scrap ? 15 : 18;
        int middle = scrap ? 7 : 8;
        g.b(0, middle - 2, 1, 1, middle + 2, guard - 1, METAL);
        g.b(0, middle - 3, 3, 1, middle - 3, guard - 2, Materials.CHROME);
        g.b(0, middle - 2, 0, 1, middle, 2, Materials.CHROME);
        g.b(0, middle + 3, 5, 1, middle + 3, guard - 3, BODY);
        if (scrap) {
            g.b(0, middle + 2, 5, 1, middle + 3, 6, AIR);
            g.b(0, middle - 3, 10, 1, middle - 2, 11, AIR);
            g.b(0, middle - 1, 9, 1, middle, 13, Materials.RUST);
        } else {
            g.b(0, middle, 5, 1, middle + 1, 15, DARK); // Deep blade fuller.
            g.b(0, middle + 2, 4, 1, middle + 2, 14, METAL);
            for (int z = 10; z < 17; z += 3) g.b(0, middle + 3, z, 1, middle + 3, z, AIR);
        }
        g.b(-2, middle - 4, guard, 2, middle + 4, guard + 1, BODY);
        g.b(-1, middle - 2, guard + 2, 2, middle + 2, g.model.sizeZ - 3, GRIP);
        for (int z = guard + 3; z < g.model.sizeZ - 3; z += 3) {
            g.b(-1, middle - 2, z, 2, middle - 2, z, BODY);
            g.b(-1, middle + 2, z, 2, middle + 2, z, BODY);
        }
        g.b(-1, middle - 2, g.model.sizeZ - 2, 2, middle + 2, g.model.sizeZ - 1, METAL);
        g.model.pivot(g.cx + 1f, middle, scrap ? 19 : 23);
        return g.model;
    }

    private static VoxelModel grenade(boolean flash) {
        Gun g = new Gun(flash ? 10 : 14, flash ? 18 : 16, flash ? 10 : 14,
                flash ? 5 : 7, flash ? 8 : 7);
        int centreZ = flash ? 5 : 7;
        int radius = flash ? 3 : 5;
        int bottom = 1;
        int top = flash ? 13 : 11;
        int paint = flash ? Materials.PLASTIC_WHITE : BODY;
        g.b(-radius + 1, bottom, centreZ - radius + 1, radius - 1, top, centreZ + radius - 1, paint);
        g.b(-radius, bottom + 2, centreZ - radius + 1, radius, top - 2, centreZ + radius - 1, paint);
        g.b(-radius + 1, bottom + 2, centreZ - radius, radius - 1, top - 2, centreZ + radius, paint);
        for (int y = bottom + 3; y < top; y += flash ? 6 : 3) {
            int band = flash ? Materials.SKY_BLUE : GRIP;
            g.b(-radius, y, centreZ - radius + 1, radius, y, centreZ + radius - 1, band);
            g.b(-radius + 1, y, centreZ - radius, radius - 1, y, centreZ + radius, band);
        }
        if (!flash) {
            g.b(-radius, 7, centreZ - 1, -radius, 8, centreZ + 1, Materials.SUNNY);
            for (int x : new int[]{-2, 2}) {
                g.b(x, 3, centreZ - radius, x, 9, centreZ - radius, METAL);
                g.b(x, 3, centreZ + radius, x, 9, centreZ + radius, METAL);
            }
        } else {
            for (int y : new int[]{5, 8, 11}) {
                g.b(-radius, y, centreZ - 1, -radius, y, centreZ + 1, DARK);
                g.b(radius, y, centreZ - 1, radius, y, centreZ + 1, DARK);
            }
        }
        g.b(-1, top + 1, centreZ - 1, 1, top + 2, centreZ + 1, METAL);
        g.b(2, top, centreZ - 1, 2, top + 3, centreZ + 3, METAL); // Safety spoon.
        g.b(3, top - 5, centreZ + 2, 3, top, centreZ + 3, METAL);
        g.b(-3, top + 2, centreZ - 1, -2, top + 3, centreZ + 2, METAL);
        g.b(-3, top + 2, centreZ, -3, top + 2, centreZ + 1, AIR);
        g.model.pivot(g.cx + .5f, flash ? 8f : 7f, centreZ + .5f);
        return g.model;
    }

    /** Shared construction vocabulary; each weapon above explicitly chooses its proportions. */
    private static final class Gun {
        final VoxelModel model;
        final int cx;

        Gun(int width, int height, int length, float pivotZ, float pivotY) {
            model = new VoxelModel(width, height, length);
            model.voxelSize = .5f;
            cx = width / 2;
            model.pivot(cx + .5f, pivotY, pivotZ);
        }

        void b(int x0, int y0, int z0, int x1, int y1, int z1, int material) {
            model.box(cx + x0, y0, z0, cx + x1, y1, z1, material);
        }

        void box(int x0, int y0, int z0, int x1, int y1, int z1, int material) {
            b(x0, y0, z0, x1, y1, z1, material);
        }

        void receiver(int start, int end, int radius, int bottom, int top, int material) {
            b(-radius, bottom + 1, start, radius, top - 1, end, material);
            b(-radius + 1, bottom, start + 1, radius - 1, top, end - 1, material);
            // Stepped top cover, distinct hardware line and two fasteners on either side.
            b(-radius + 1, top, start + 3, radius - 1, top, end - 3, METAL);
            sideStripe(start + 3, end - 4, bottom + 2, GRIP, radius);
            for (int z : new int[]{start + 2, end - 2}) {
                b(-radius, bottom + 3, z, -radius, bottom + 3, z, METAL);
                b(radius, bottom + 3, z, radius, bottom + 3, z, METAL);
            }
            b(radius, top - 3, end - 9, radius, top - 2, end - 4, DARK);
            b(radius, top - 3, end - 8, radius, top - 3, end - 5, METAL);
        }

        void tube(int y, int start, int end, int radius, int material, boolean bore) {
            b(-radius, y - radius + 1, start, radius, y + radius - 1, end, material);
            b(-radius + 1, y - radius, start, radius - 1, y + radius, end, material);
            if (bore) {
                int hole = Math.max(0, radius - 2);
                b(-hole, y - hole, start, hole, y + hole, start + 2, AIR);
                b(-hole, y - hole, start + 3, hole, y + hole, start + 3, DARK);
            }
        }

        void pistolFrame(int start, int end, int gripZ, int slide) {
            b(-2, 8, start, 2, 11, end, slide);
            b(-1, 12, start + 1, 1, 12, end - 1, slide);
            b(-2, 7, start + 2, 2, 8, end, BODY);
            grip(gripZ, GRIP);
            trigger(gripZ - 5, gripZ, 6);
            for (int z = end - 4; z <= end - 1; z += 2) {
                b(-2, 9, z, -2, 11, z, GRIP);
                b(2, 9, z, 2, 11, z, GRIP);
            }
        }

        void grip(int z, int material) {
            b(-1, 5, z, 1, 10, z + 3, material);
            b(-1, 1, z + 1, 1, 5, z + 4, material);
            b(-2, 2, z + 2, -2, 7, z + 3, material);
            b(2, 2, z + 2, 2, 7, z + 3, material);
            b(-1, 0, z + 1, 1, 1, z + 4, BODY);
            b(-2, 4, z + 2, -2, 4, z + 3, BODY);
            b(2, 4, z + 2, 2, 4, z + 3, BODY);
        }

        void trigger(int front, int rear, int y) {
            b(-1, y, front, 1, y, rear, BODY);
            b(-1, y + 1, front, 1, y + 2, front, BODY);
            b(0, y + 2, rear - 2, 0, y + 3, rear - 2, METAL);
        }

        void magazine(int start, int length, int height, int material, boolean curved) {
            int top = 10;
            b(-1, top - height, start, 1, top, start + length - 1, material);
            if (curved) {
                b(-1, 2, start + 2, 1, top - height, start + length + 1, material);
                b(-1, 0, start + 4, 1, 3, start + length + 2, material);
                b(-1, 0, start + 4, 1, 0, start + length + 2, GRIP);
            } else {
                b(-1, 1, start + 1, 1, top - height, start + length, material);
                b(-2, 0, start + 1, 2, 1, start + length, GRIP);
            }
            for (int z = start + 2; z < start + length - 1; z += 3) {
                b(-1, 3, z, -1, 8, z, GRIP);
                b(1, 3, z, 1, 8, z, GRIP);
            }
        }

        void slimStock(int start, int end, int material) {
            b(-1, 12, start, 1, 14, end - 2, METAL);
            b(-2, 14, start + 3, 2, 16, end - 2, material);
            b(-1, 9, start + 4, 1, 10, end - 1, material);
            b(-2, 9, end - 3, 2, 16, end - 1, material);
            b(-2, 8, end, 2, 17, end, GRIP);
        }

        void woodStock(int start, int end) {
            b(-1, 11, start, 1, 14, end - 1, WOOD);
            b(-2, 10, start + 3, 2, 15, end - 1, WOOD);
            b(-2, 9, end - 4, 2, 14, end - 1, WOOD);
            b(-1, 15, start + 4, 1, 16, end - 3, BODY);
            b(-2, 9, end, 2, 15, end, GRIP);
            b(-2, 12, start + 5, -2, 12, end - 3, GRIP);
            b(2, 12, start + 5, 2, 12, end - 3, GRIP);
        }

        void wireStock(int start, int end, int y) {
            b(-2, y, start, -2, y, end, METAL);
            b(2, y, start, 2, y, end, METAL);
            b(-2, y - 4, end, 2, y, end, GRIP);
            b(-1, y - 4, end, 1, y - 1, end, AIR);
        }

        void rail(int start, int end, int y) {
            b(-1, y, start, 1, y, end, BODY);
            for (int z = start; z <= end; z += 3) b(-2, y + 1, z, 2, y + 1, z, METAL);
        }

        void ironSights(int front, int rear, int y) {
            b(-1, y, front, 1, y + 1, front + 1, BODY);
            b(0, y + 2, front, 0, y + 2, front, Materials.SUNNY);
            b(-2, y, rear, 2, y, rear + 1, BODY);
            b(-2, y + 1, rear, -1, y + 2, rear + 1, METAL);
            b(1, y + 1, rear, 2, y + 2, rear + 1, METAL);
        }

        void pistolSights(int front, int rear, int y) { ironSights(front, rear, y); }

        void holo(int start, int end, int base) {
            b(-2, base, start, 2, base, end, BODY);
            b(-3, base + 1, start + 1, -2, base + 4, end - 1, METAL);
            b(2, base + 1, start + 1, 3, base + 4, end - 1, METAL);
            b(-2, base + 4, start + 1, 2, base + 4, end - 1, METAL);
            // Opaque lens: weapon meshes use one opaque pass, never invisible GLASS voxels.
            b(-1, base + 1, start + 1, 1, base + 3, start + 1, DARK);
            b(0, base + 2, start + 1, 0, base + 2, start + 1, Materials.SKY_BLUE);
        }

        void scope(int start, int end, int y, int radius) {
            b(-1, y - radius - 3, start + 4, 1, y - radius, start + 5, BODY);
            b(-1, y - radius - 3, end - 5, 1, y - radius, end - 4, BODY);
            tube(y, start + 2, end - 2, Math.max(1, radius - 1), BODY, false);
            tube(y, start, start + 3, radius, METAL, false);
            tube(y, end - 3, end, radius, METAL, false);
            int inset = Math.max(0, radius - 1);
            b(-inset, y - inset, start, inset, y + inset, start, DARK);
            b(-inset, y - inset, end, inset, y + inset, end, DARK);
            b(-inset, y, end, -inset, y + inset, end, Materials.SKY_BLUE);
            b(-1, y + radius, start + 7, 1, y + radius + 1, start + 9, GRIP);
            b(radius, y - 1, start + 7, radius + 1, y + 1, start + 9, GRIP);
        }

        void sideStripe(int start, int end, int y, int material, int radius) {
            b(-radius, y, start, -radius, y, end, material);
            b(radius, y, start, radius, y, end, material);
        }

        void vents(int start, int end, int y, int radius) {
            for (int z = start; z <= end; z += 4) {
                b(-radius, y, z, -radius, y + 1, z + 1, AIR);
                b(radius, y, z, radius, y + 1, z + 1, AIR);
                b(-radius + 1, y, z, -radius + 1, y + 1, z + 1, DARK);
                b(radius - 1, y, z, radius - 1, y + 1, z + 1, DARK);
            }
        }

        void ribs(int start, int end, int low, int high, int radius, int material) {
            for (int z = start; z <= end; z += 3) {
                b(-radius, low, z, -radius, high, z, material);
                b(radius, low, z, radius, high, z, material);
                b(-radius, low, z, radius, low, z, material);
            }
        }

        void drum(int z, int y, int radius, int depth) {
            b(-radius, y - radius + 2, z, radius, y + radius - 2, z + depth, METAL);
            b(-radius + 1, y - radius + 1, z, radius - 1, y + radius - 1, z + depth, METAL);
            b(-radius + 2, y - radius, z, radius - 2, y + radius, z + depth, METAL);
            b(-radius + 2, y - radius + 2, z + depth, radius - 2, y + radius - 2, z + depth, BODY);
            b(-1, y - 1, z + depth, 1, y + 1, z + depth, GRIP);
        }

        void ammoBox(int z, int top, int radius) {
            b(-radius, 2, z, radius, top, z + 9, WOOD);
            b(-radius + 1, 1, z + 1, radius - 1, top + 1, z + 8, WOOD);
            b(-radius, 2, z + 2, -radius, top - 1, z + 2, GRIP);
            b(radius, 2, z + 6, radius, top - 1, z + 6, GRIP);
            b(-radius, 4, z + 1, -radius, 6, z + 7, BODY);
            // Just four cartridges: clear family identity without a high-frequency mesh pattern.
            for (int cartridge = 0; cartridge < 4; cartridge++) {
                int zz = z + 1 + cartridge * 2;
                b(-radius - 1, top + 1, zz, -3, top + 2, zz, Materials.GOLD);
                b(-radius - 1, top + 1, zz, -radius - 1, top + 2, zz, METAL);
            }
        }

        void carryHandle(int start, int end, int base, int top) {
            b(-1, base, start, 1, top, start + 1, BODY);
            b(-1, base, end - 1, 1, top, end, BODY);
            b(-1, top, start, 1, top, end, GRIP);
        }

        void bipod(int start, int end, int y) {
            b(-4, y, start, 4, y + 1, start + 1, METAL);
            b(-5, y - 3, start + 1, -4, y, end, BODY);
            b(4, y - 3, start + 1, 5, y, end, BODY);
            b(-6, y - 3, end - 1, -4, y - 2, end + 1, GRIP);
            b(4, y - 3, end - 1, 6, y - 2, end + 1, GRIP);
        }
    }
}
