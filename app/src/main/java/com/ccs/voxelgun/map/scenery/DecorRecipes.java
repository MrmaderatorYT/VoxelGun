package com.ccs.voxelgun.map.scenery;

import com.ccs.voxelgun.voxel.Materials;

/** Small, non-blocking details assembled from procedural cuboids, not static assets. */
public final class DecorRecipes {
    private DecorRecipes() {}
    public static void flowers(MapScenery s, float x, float y, float z, int color) {
        s.box(x, y, z, 3.5f, .18f, .8f, Materials.WOOD_DARK);
        s.box(x + .1f, y + .18f, z + .1f, 3.3f, .12f, .6f, Materials.DIRT);
        for (int i = 0; i < 7; i++) {
            float fx = x + .25f + i * .48f;
            s.box(fx, y + .3f, z + .35f, .07f, .35f, .07f, Materials.LEAVES);
            s.box(fx - .1f, y + .6f, z + .25f, .28f, .13f, .28f, color);
            s.box(fx - .02f, y + .73f, z + .33f, .12f, .04f, .12f, Materials.SUNNY);
        }
    }
    public static void fence(MapScenery s, float x, float y, float z, float length, int color) {
        for (float i = 0; i <= length; i += .7f) s.box(x + i, y, z, .12f, 1.2f, .12f, color);
        s.box(x, y + .35f, z, length, .12f, .12f, color);
        s.box(x, y + .85f, z, length, .12f, .12f, color);
    }
    public static void awning(MapScenery s, float x, float y, float z, float width, int color) {
        for (int i = 0; i < (int) width * 2; i++)
            s.box(x + i * .5f, y, z, .5f, .18f, 1.8f, i % 2 == 0 ? color : Materials.FRAME_LIGHT);
        s.box(x, y - .35f, z, width, .35f, .12f, color);
    }
    public static void vent(MapScenery s, float x, float y, float z) {
        s.box(x, y, z, 1.8f, 1.1f, .18f, Materials.STEEL);
        for (int i = 0; i < 6; i++) s.box(x + .12f, y + .12f + i * .15f, z - .04f,
                1.56f, .055f, .05f, Materials.CONCRETE_DARK);
    }
    public static void pipe(MapScenery s, float x, float y, float z, float length, boolean alongX, int color) {
        s.box(x, y, z, alongX ? length : .35f, .35f, alongX ? .35f : length, color);
        for (float i = 1f; i < length; i += 3f) s.box(x + (alongX ? i : -.08f), y - .08f,
                z + (alongX ? -.08f : i), alongX ? .12f : .51f, .51f,
                alongX ? .51f : .12f, Materials.STEEL);
    }
    public static void sign(MapScenery s, float x, float y, float z, String word, float pixel, int background) {
        float width = (word.length() * 4 + 1) * pixel;
        s.box(x - pixel, y - pixel, z, width, 7 * pixel, .12f, background);
        // Facing -Z, a viewer looking +Z has screen-right along -X. Reverse both the
        // letter order and glyph columns in world space so the front reads left-to-right.
        for (int letter = 0; letter < word.length(); letter++) {
            int bits = glyph(word.charAt(letter));
            for (int row = 0; row < 5; row++) for (int col = 0; col < 3; col++) {
                if ((bits & (1 << (14 - row * 3 - col))) != 0)
                    s.box(x + ((word.length() - 1 - letter) * 4 + 2 - col) * pixel,
                            y + (4 - row) * pixel, z - .025f,
                            pixel * .9f, pixel * .9f, .03f, Materials.FRAME_LIGHT);
            }
        }
    }
    /** Roadside sign with its own slender visual supports. */
    public static void standingSign(MapScenery s, float x, float groundY, float z,
                                    String word, float pixel, int background) {
        float width = (word.length() * 4 + 1) * pixel;
        s.box(x, groundY, z + .08f, .12f, 3.2f, .12f, Materials.STEEL);
        s.box(x + width - 2 * pixel, groundY, z + .08f, .12f, 3.2f, .12f, Materials.STEEL);
        sign(s, x, groundY + 2.6f, z, word, pixel, background);
    }
    private static int glyph(char c) {
        return switch (c) {
            case 'A' -> 0b010_101_111_101_101; case 'B' -> 0b110_101_110_101_110;
            case 'C' -> 0b011_100_100_100_011; case 'D' -> 0b110_101_101_101_110;
            case 'E' -> 0b111_100_110_100_111; case 'F' -> 0b111_100_110_100_100;
            case 'G' -> 0b011_100_101_101_011; case 'H' -> 0b101_101_111_101_101;
            case 'I' -> 0b111_010_010_010_111; case 'J' -> 0b001_001_001_101_010;
            case 'K' -> 0b101_101_110_101_101; case 'L' -> 0b100_100_100_100_111;
            case 'M' -> 0b101_111_111_101_101; case 'N' -> 0b101_111_111_111_101;
            case 'O' -> 0b010_101_101_101_010; case 'P' -> 0b110_101_110_100_100;
            case 'Q' -> 0b010_101_101_111_011; case 'R' -> 0b110_101_110_101_101;
            case 'S' -> 0b011_100_010_001_110; case 'T' -> 0b111_010_010_010_010;
            case 'U' -> 0b101_101_101_101_111; case 'V' -> 0b101_101_101_101_010;
            case 'W' -> 0b101_101_111_111_101; case 'X' -> 0b101_101_010_101_101;
            case 'Y' -> 0b101_101_010_010_010; case 'Z' -> 0b111_001_010_100_111;
            default -> 0;
        };
    }
}
