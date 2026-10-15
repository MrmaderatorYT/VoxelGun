package com.ccs.voxelgun.voxel;

/**
 * The voxel palette. Every entry carries the PBR data the ray tracer needs: albedo, roughness,
 * metallic and emissive strength. Ids below {@link #FIRST_TRANSLUCENT} are opaque and block
 * bullets and sight; ids at or above it are traced as translucent.
 *
 * <p>The same table is uploaded to the GPU as a uniform array, so the raster pass, the ray tracer
 * and gameplay all agree on what a block is made of.
 */
public final class Materials {
    public static final int COUNT = 96;
    /** Palette slot the translucent range folds onto, past the opaque ids. */
    private static final int TRANSLUCENT_SLOT = 64;
    public static final int FIRST_TRANSLUCENT = 200;

    public static final byte AIR = 0;
    public static final byte CONCRETE = 1;
    public static final byte CONCRETE_DARK = 2;
    public static final byte SAND = 3;
    public static final byte SANDSTONE = 4;
    public static final byte WOOD = 5;
    public static final byte WOOD_DARK = 6;
    public static final byte CRATE = 7;
    public static final byte STEEL = 8;
    public static final byte RUST = 9;
    public static final byte PAINT_RED = 10;
    public static final byte PAINT_BLUE = 11;
    public static final byte BRICK = 12;
    public static final byte ASPHALT = 13;
    public static final byte GRASS = 14;
    public static final byte DIRT = 15;
    public static final byte STONE = 16;
    public static final byte MARBLE = 17;
    public static final byte GOLD = 18;
    public static final byte RUBBER = 19;
    public static final byte CANVAS = 20;
    public static final byte HAZARD = 21;
    public static final byte TILE_DARK = 22;
    public static final byte NEON_CYAN = 23;
    public static final byte NEON_MAGENTA = 24;
    public static final byte LAMP = 25;
    public static final byte TEAM_RED = 26;
    public static final byte TEAM_BLUE = 27;
    public static final byte CHROME = 28;
    public static final byte COPPER = 29;
    public static final byte SNOW = 30;
    public static final byte SANDBAG = 31;
    public static final byte ROOF = 32;
    public static final byte LEAVES = 33;
    public static final byte TRUNK = 34;
    public static final byte LAVA = 35;
    public static final byte SCREEN = 36;
    public static final byte EMERGENCY = 37;
    public static final byte PLASTIC_WHITE = 38;
    public static final byte PLASTIC_ORANGE = 39;
    public static final byte SKIN = 40;
    public static final byte CLOTH_DARK = 41;
    public static final byte VISOR = 42;
    public static final byte GUN_BODY = 43;
    public static final byte GUN_GRIP = 44;
    public static final byte GUN_METAL = 45;
    public static final byte GUN_WOOD = 46;
    public static final byte MUZZLE = 47;

    // Bright, saturated set used by the playful maps.
    public static final byte CANDY_PINK = 48;
    public static final byte MINT = 49;
    public static final byte SKY_BLUE = 50;
    public static final byte SUNNY = 51;
    public static final byte GRAPE = 52;
    public static final byte CORAL = 53;
    public static final byte GRASS_BRIGHT = 54;
    public static final byte SAND_LIGHT = 55;
    public static final byte WOOD_LIGHT = 56;
    public static final byte HAY = 57;
    public static final byte STONE_LIGHT = 58;
    public static final byte ROOF_BLUE = 59;
    public static final byte ROOF_TEAL = 60;
    public static final byte LANTERN = 61;
    /** Launches anyone standing on it; see the jump pad handling in the match loop. */
    public static final byte JUMP_PAD = 62;
    public static final byte ICE = 63;

    // Block Strike style exteriors. Real buildings rather than plain boxes: brick courses in two
    // tones, painted trim, framed openings, tiled plazas, decking and two tone props. Ids 64..67
    // are taken by the translucent fold, so this set starts at 68.
    public static final byte BRICK_WHITE = 68;
    public static final byte BRICK_WHITE_ALT = 69;
    public static final byte BRICK_ORANGE = 70;
    public static final byte BRICK_ORANGE_ALT = 71;
    public static final byte BRICK_TAN = 72;
    public static final byte BRICK_TAN_ALT = 73;
    public static final byte PLASTER = 74;
    public static final byte TRIM_RED = 75;
    public static final byte TRIM_BLUE = 76;
    public static final byte TRIM_GREY = 77;
    public static final byte DOOR_DARK = 78;
    public static final byte FRAME_LIGHT = 79;
    public static final byte TILE_FLOOR = 80;
    public static final byte TILE_GROUT = 81;
    public static final byte POOL_EDGE = 82;
    public static final byte DECK_WOOD = 83;
    public static final byte DECK_WOOD_ALT = 84;
    public static final byte PANEL_GREY = 85;
    public static final byte PANEL_SEAM = 86;
    public static final byte BARREL_TEAL = 87;
    public static final byte BARREL_RUST = 88;
    public static final byte ROOF_TILE = 89;
    public static final byte KERB = 90;
    public static final byte BRICK_BURGUNDY = 91;
    public static final byte BRICK_BURGUNDY_ALT = 92;
    public static final byte BRICK_TEAL = 93;
    public static final byte BRICK_TEAL_ALT = 94;
    public static final byte BRICK_VIOLET = 95;

    public static final byte GLASS = (byte) 200;
    public static final byte GLASS_TINTED = (byte) 201;
    public static final byte GRATE = (byte) 202;
    public static final byte WATER = (byte) 203;

    /** Packed as albedo.rgb + roughness, then emissive.rgb + metallic, per material slot. */
    private static final float[] TABLE = new float[COUNT * 8];

    static {
        define(CONCRETE, 0.72f, 0.70f, 0.65f, 0.88f, 0f, 0f);
        define(CONCRETE_DARK, 0.42f, 0.42f, 0.40f, 0.90f, 0f, 0f);
        define(SAND, 0.82f, 0.72f, 0.48f, 0.95f, 0f, 0f);
        define(SANDSTONE, 0.76f, 0.63f, 0.38f, 0.88f, 0f, 0f);
        define(WOOD, 0.58f, 0.39f, 0.22f, 0.80f, 0f, 0f);
        define(WOOD_DARK, 0.36f, 0.24f, 0.13f, 0.82f, 0f, 0f);
        define(CRATE, 0.68f, 0.50f, 0.27f, 0.78f, 0f, 0f);
        define(STEEL, 0.55f, 0.58f, 0.62f, 0.32f, 0.90f, 0f);
        define(RUST, 0.47f, 0.29f, 0.18f, 0.72f, 0.45f, 0f);
        define(PAINT_RED, 0.62f, 0.16f, 0.12f, 0.45f, 0.15f, 0f);
        define(PAINT_BLUE, 0.13f, 0.32f, 0.66f, 0.45f, 0.15f, 0f);
        define(BRICK, 0.48f, 0.17f, 0.20f, 0.86f, 0f, 0f);
        define(ASPHALT, 0.24f, 0.25f, 0.31f, 0.92f, 0f, 0f);
        define(GRASS, 0.30f, 0.58f, 0.22f, 0.95f, 0f, 0f);
        define(DIRT, 0.43f, 0.29f, 0.17f, 0.94f, 0f, 0f);
        define(STONE, 0.45f, 0.46f, 0.52f, 0.85f, 0f, 0f);
        define(MARBLE, 0.86f, 0.85f, 0.80f, 0.18f, 0f, 0f);
        define(GOLD, 0.94f, 0.72f, 0.24f, 0.22f, 1.00f, 0f);
        define(RUBBER, 0.09f, 0.09f, 0.10f, 0.96f, 0f, 0f);
        define(CANVAS, 0.46f, 0.40f, 0.28f, 0.92f, 0f, 0f);
        define(HAZARD, 0.82f, 0.66f, 0.14f, 0.60f, 0.10f, 0f);
        define(TILE_DARK, 0.12f, 0.13f, 0.15f, 0.14f, 0.05f, 0f);
        define(NEON_CYAN, 0.25f, 0.90f, 1.00f, 0.30f, 0f, 6.0f);
        define(NEON_MAGENTA, 1.00f, 0.25f, 0.75f, 0.30f, 0f, 5.0f);
        define(LAMP, 1.00f, 0.95f, 0.85f, 0.40f, 0f, 9.0f);
        define(TEAM_RED, 0.92f, 0.12f, 0.10f, 0.70f, 0f, 0f);
        define(TEAM_BLUE, 0.10f, 0.48f, 0.94f, 0.70f, 0f, 0f);
        define(CHROME, 0.92f, 0.94f, 0.96f, 0.05f, 1.00f, 0f);
        define(COPPER, 0.72f, 0.45f, 0.20f, 0.28f, 1.00f, 0f);
        define(SNOW, 0.92f, 0.94f, 0.98f, 0.70f, 0f, 0f);
        define(SANDBAG, 0.60f, 0.53f, 0.34f, 0.94f, 0f, 0f);
        define(ROOF, 0.44f, 0.22f, 0.17f, 0.80f, 0f, 0f);
        define(LEAVES, 0.19f, 0.40f, 0.15f, 0.90f, 0f, 0f);
        define(TRUNK, 0.30f, 0.21f, 0.13f, 0.90f, 0f, 0f);
        define(LAVA, 1.00f, 0.35f, 0.06f, 0.70f, 0f, 8.0f);
        define(SCREEN, 0.20f, 0.55f, 0.95f, 0.15f, 0f, 3.0f);
        define(EMERGENCY, 1.00f, 0.15f, 0.10f, 0.35f, 0f, 4.5f);
        define(PLASTIC_WHITE, 0.86f, 0.87f, 0.88f, 0.35f, 0f, 0f);
        define(PLASTIC_ORANGE, 0.90f, 0.42f, 0.10f, 0.38f, 0f, 0f);
        define(SKIN, 0.78f, 0.58f, 0.44f, 0.75f, 0f, 0f);
        define(CLOTH_DARK, 0.16f, 0.17f, 0.20f, 0.88f, 0f, 0f);
        define(VISOR, 0.06f, 0.09f, 0.13f, 0.08f, 0.60f, 0.4f);
        define(GUN_BODY, 0.14f, 0.15f, 0.17f, 0.42f, 0.70f, 0f);
        define(GUN_GRIP, 0.10f, 0.10f, 0.11f, 0.85f, 0f, 0f);
        define(GUN_METAL, 0.62f, 0.64f, 0.68f, 0.24f, 1.00f, 0f);
        define(GUN_WOOD, 0.42f, 0.26f, 0.14f, 0.55f, 0f, 0f);
        define(MUZZLE, 1.00f, 0.80f, 0.35f, 0.50f, 0f, 12.0f);

        define(CANDY_PINK, 0.98f, 0.45f, 0.68f, 0.55f, 0f, 0f);
        define(MINT, 0.52f, 0.94f, 0.76f, 0.55f, 0f, 0f);
        define(SKY_BLUE, 0.42f, 0.76f, 0.98f, 0.55f, 0f, 0f);
        define(SUNNY, 1.00f, 0.86f, 0.28f, 0.58f, 0f, 0f);
        define(GRAPE, 0.62f, 0.42f, 0.92f, 0.55f, 0f, 0f);
        define(CORAL, 1.00f, 0.48f, 0.34f, 0.58f, 0f, 0f);
        define(GRASS_BRIGHT, 0.42f, 0.82f, 0.30f, 0.85f, 0f, 0f);
        define(SAND_LIGHT, 0.96f, 0.88f, 0.62f, 0.90f, 0f, 0f);
        define(WOOD_LIGHT, 0.82f, 0.60f, 0.34f, 0.72f, 0f, 0f);
        define(HAY, 0.90f, 0.74f, 0.32f, 0.92f, 0f, 0f);
        define(STONE_LIGHT, 0.80f, 0.80f, 0.84f, 0.72f, 0f, 0f);
        define(ROOF_BLUE, 0.24f, 0.42f, 0.82f, 0.62f, 0f, 0f);
        define(ROOF_TEAL, 0.16f, 0.68f, 0.66f, 0.62f, 0f, 0f);
        define(LANTERN, 1.00f, 0.82f, 0.45f, 0.45f, 0f, 7.0f);
        define(JUMP_PAD, 0.35f, 1.00f, 0.70f, 0.35f, 0f, 3.5f);
        define(ICE, 0.72f, 0.90f, 0.98f, 0.10f, 0f, 0f);

        // Block Strike exteriors: bright, low contrast surfaces that read cleanly in daylight.
        define(BRICK_WHITE, 0.90f, 0.89f, 0.86f, 0.88f, 0f, 0f);
        define(BRICK_WHITE_ALT, 0.82f, 0.81f, 0.78f, 0.88f, 0f, 0f);
        define(BRICK_ORANGE, 0.93f, 0.66f, 0.24f, 0.86f, 0f, 0f);
        define(BRICK_ORANGE_ALT, 0.85f, 0.57f, 0.19f, 0.86f, 0f, 0f);
        define(BRICK_TAN, 0.86f, 0.75f, 0.58f, 0.88f, 0f, 0f);
        define(BRICK_TAN_ALT, 0.78f, 0.67f, 0.50f, 0.88f, 0f, 0f);
        define(PLASTER, 0.94f, 0.93f, 0.90f, 0.92f, 0f, 0f);
        define(TRIM_RED, 0.70f, 0.26f, 0.20f, 0.72f, 0f, 0f);
        define(TRIM_BLUE, 0.22f, 0.40f, 0.66f, 0.72f, 0f, 0f);
        define(TRIM_GREY, 0.52f, 0.54f, 0.56f, 0.80f, 0f, 0f);
        define(DOOR_DARK, 0.20f, 0.21f, 0.23f, 0.55f, 0.10f, 0f);
        define(FRAME_LIGHT, 0.72f, 0.71f, 0.68f, 0.70f, 0f, 0f);
        define(TILE_FLOOR, 0.86f, 0.85f, 0.82f, 0.55f, 0f, 0f);
        define(TILE_GROUT, 0.72f, 0.71f, 0.68f, 0.70f, 0f, 0f);
        define(POOL_EDGE, 0.93f, 0.93f, 0.92f, 0.45f, 0f, 0f);
        define(DECK_WOOD, 0.74f, 0.55f, 0.33f, 0.80f, 0f, 0f);
        define(DECK_WOOD_ALT, 0.66f, 0.47f, 0.27f, 0.80f, 0f, 0f);
        define(PANEL_GREY, 0.76f, 0.77f, 0.78f, 0.62f, 0.05f, 0f);
        define(PANEL_SEAM, 0.60f, 0.61f, 0.63f, 0.55f, 0.15f, 0f);
        define(BARREL_TEAL, 0.24f, 0.52f, 0.53f, 0.62f, 0.20f, 0f);
        define(BARREL_RUST, 0.58f, 0.40f, 0.26f, 0.78f, 0.25f, 0f);
        define(ROOF_TILE, 0.78f, 0.36f, 0.26f, 0.82f, 0f, 0f);
        define(KERB, 0.80f, 0.79f, 0.76f, 0.80f, 0f, 0f);
        define(BRICK_BURGUNDY, 0.55f, 0.12f, 0.23f, 0.86f, 0f, 0f);
        define(BRICK_BURGUNDY_ALT, 0.40f, 0.075f, 0.16f, 0.88f, 0f, 0f);
        define(BRICK_TEAL, 0.075f, 0.49f, 0.46f, 0.82f, 0f, 0f);
        define(BRICK_TEAL_ALT, 0.045f, 0.34f, 0.37f, 0.86f, 0f, 0f);
        define(BRICK_VIOLET, 0.43f, 0.25f, 0.65f, 0.84f, 0f, 0f);

        // Translucent ids fold into the same table just past the opaque range.
        define(GLASS, 0.85f, 0.92f, 0.95f, 0.03f, 0f, 0f);
        define(GLASS_TINTED, 0.35f, 0.55f, 0.60f, 0.06f, 0f, 0f);
        define(GRATE, 0.40f, 0.42f, 0.45f, 0.45f, 0.80f, 0f);
        define(WATER, 0.18f, 0.42f, 0.55f, 0.04f, 0f, 0f);
    }

    private Materials() {}

    /** Shader slot for a material id: opaque ids map directly, translucent ones fold past them. */
    public static int slot(int material) {
        int id = material & 0xFF;
        return id >= FIRST_TRANSLUCENT ? TRANSLUCENT_SLOT + (id - FIRST_TRANSLUCENT) : id;
    }

    private static void define(byte material, float r, float g, float b, float roughness,
                               float metallic, float emissive) {
        int base = slot(material) * 8;
        TABLE[base] = r;
        TABLE[base + 1] = g;
        TABLE[base + 2] = b;
        TABLE[base + 3] = roughness;
        TABLE[base + 4] = r * emissive;
        TABLE[base + 5] = g * emissive;
        TABLE[base + 6] = b * emissive;
        TABLE[base + 7] = metallic;
    }

    public static float[] table() {
        return TABLE;
    }

    public static boolean opaque(int material) {
        int id = material & 0xFF;
        return id != 0 && id < FIRST_TRANSLUCENT;
    }

    public static boolean translucent(int material) {
        return (material & 0xFF) >= FIRST_TRANSLUCENT;
    }

    /** Water is passable; everything else that is not air stops the player. */
    public static boolean solid(int material) {
        int id = material & 0xFF;
        return id != 0 && id != (WATER & 0xFF);
    }

    public static float emissive(int material) {
        int base = slot(material) * 8;
        return Math.max(TABLE[base + 4], Math.max(TABLE[base + 5], TABLE[base + 6]));
    }

    /** 0xRRGGBB used by the minimap and by particle/decal tinting on the CPU side. */
    public static int rgb(int material) {
        int base = slot(material) * 8;
        int r = (int) (Math.min(1f, TABLE[base]) * 255f);
        int g = (int) (Math.min(1f, TABLE[base + 1]) * 255f);
        int b = (int) (Math.min(1f, TABLE[base + 2]) * 255f);
        return (r << 16) | (g << 8) | b;
    }
}
