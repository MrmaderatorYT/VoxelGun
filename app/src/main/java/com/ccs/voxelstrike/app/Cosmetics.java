package com.ccs.voxelstrike.app;

import com.ccs.voxelstrike.model.VoxelModel;
import com.ccs.voxelstrike.voxel.Materials;

/** Cosmetics never modify weapon or combat statistics. */
public final class Cosmetics {
    public enum Category { SKIN, GLOVES, CHARM, CROSSHAIR }
    public record Item(String id, Category category, int value, String name, int cost, int color) {}
    public static final Item[] ITEMS = {
        new Item("skin_stock", Category.SKIN, 0, "Factory", 0, 0xFF757A80),
        new Item("skin_mint", Category.SKIN, 1, "Mint", 300, 0xFF67E0AE),
        new Item("skin_coral", Category.SKIN, 2, "Coral", 300, 0xFFF57585),
        new Item("skin_gold", Category.SKIN, 3, "Gold", 500, 0xFFFFD35C),
        new Item("skin_arctic", Category.SKIN, 4, "Arctic", 600, 0xFFC6E8FF),
        new Item("skin_neon_grid", Category.SKIN, 5, "Neon Grid", 750, 0xFF55F4FF),
        new Item("glove_stock", Category.GLOVES, 0, "Graphite", 0, 0xFF424242),
        new Item("glove_mint", Category.GLOVES, 1, "Mint gloves", 200, 0xFF67E0AE),
        new Item("glove_coral", Category.GLOVES, 2, "Coral gloves", 200, 0xFFF57585),
        new Item("charm_none", Category.CHARM, 0, "No charm", 0, 0xFF888888),
        new Item("charm_core", Category.CHARM, 1, "Pocket core", 250, 0xFF67E0AE),
        new Item("charm_gold", Category.CHARM, 2, "Gold cube", 250, 0xFFFFD35C),
        new Item("cross_dynamic", Category.CROSSHAIR, 0, "Dynamic cross", 0, 0xFFFFFFFF),
        new Item("cross_dot", Category.CROSSHAIR, 1, "Dot", 0, 0xFFFFFFFF),
        new Item("cross_ring", Category.CROSSHAIR, 2, "Ring", 0, 0xFFFFFFFF)
    };
    private Cosmetics() {}

    public static int material(int value) {
        return value == 1 ? Materials.MINT : value == 2 ? Materials.CORAL
                : value == 3 ? Materials.GOLD : value == 4 ? Materials.ICE
                : value == 5 ? Materials.GRAPE : Materials.GUN_METAL;
    }

    /**
     * Applies a complete, coordinated finish to a weapon model.
     *
     * <p>The old implementation only painted metal and wood the same flat colour, leaving most of
     * a modern gun black. Each finish now has a body colour, hardware, grip and a sparse stripe
     * pattern. This method is shared by the match renderer, the arsenal turntable and HUD previews,
     * so the skin bought by the player is the skin actually seen in their hands.
     */
    public static VoxelModel applyWeaponSkin(VoxelModel model, int value) {
        if (model == null || value <= 0) {
            return model;
        }
        for (int z = 0; z < model.sizeZ; z++) {
            int skinZ = (int) (z * model.voxelSize);
            for (int y = 0; y < model.sizeY; y++) {
                int skinY = (int) (y * model.voxelSize);
                for (int x = 0; x < model.sizeX; x++) {
                    int original = model.get(x, y, z) & 0xFF;
                    if (!isWeaponSurface(original)) {
                        continue;
                    }
                    // Pattern coordinates are physical model units. Subdividing a voxel into
                    // smaller detail cells must not make an equipped finish's stripes thinner.
                    int skinX = (int) (x * model.voxelSize);
                    boolean stripe = ((skinZ / 2) + skinX * 2 + skinY / 2) % 7 == 0;
                    model.set(x, y, z, skinMaterial(value, original, stripe));
                }
            }
        }
        return model;
    }

    private static boolean isWeaponSurface(int material) {
        return material == Materials.GUN_BODY || material == Materials.GUN_GRIP
                || material == Materials.GUN_METAL || material == Materials.GUN_WOOD;
    }

    private static int skinMaterial(int value, int original, boolean stripe) {
        switch (value) {
            case 1: // Mint Circuit
                if (original == Materials.GUN_BODY) {
                    return stripe ? Materials.NEON_CYAN : Materials.MINT;
                }
                if (original == Materials.GUN_METAL) return Materials.CHROME;
                if (original == Materials.GUN_WOOD) return Materials.ROOF_TEAL;
                return Materials.TILE_DARK;
            case 2: // Coral Strike
                if (original == Materials.GUN_BODY) {
                    return stripe ? Materials.SUNNY : Materials.CORAL;
                }
                if (original == Materials.GUN_METAL) return Materials.COPPER;
                if (original == Materials.GUN_WOOD) return Materials.PLASTIC_ORANGE;
                return Materials.RUBBER;
            case 3: // Gilded hardware with a dark, usable grip
                if (original == Materials.GUN_GRIP) return Materials.WOOD_DARK;
                if (original == Materials.GUN_METAL && stripe) return Materials.CHROME;
                return original == Materials.GUN_WOOD ? Materials.SUNNY : Materials.GOLD;
            case 4: // Arctic digital blocks
                if (original == Materials.GUN_BODY) {
                    return stripe ? Materials.SKY_BLUE : Materials.PLASTIC_WHITE;
                }
                if (original == Materials.GUN_METAL) return Materials.CHROME;
                if (original == Materials.GUN_WOOD) return Materials.ICE;
                return Materials.TILE_DARK;
            case 5: // Neon Grid
                if (original == Materials.GUN_GRIP) return Materials.TILE_DARK;
                if (original == Materials.GUN_METAL) return Materials.NEON_CYAN;
                if (original == Materials.GUN_WOOD) return Materials.NEON_MAGENTA;
                return stripe ? Materials.NEON_MAGENTA : Materials.GRAPE;
            default:
                return original;
        }
    }
}
