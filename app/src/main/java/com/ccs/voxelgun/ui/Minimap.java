package com.ccs.voxelgun.ui;

import android.graphics.Bitmap;

import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.voxel.VoxelWorld;

/** Bakes a top-down image of the map once, shaded by column height. */
public final class Minimap {
    private Minimap() {}

    public static Bitmap render(VoxelWorld world) {
        int width = world.sizeX();
        int depth = world.sizeZ();
        int[] pixels = new int[width * depth];

        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                int top = world.height(x, z);
                int color;
                if (top <= 0) {
                    color = 0xFF0C0C0E;
                } else {
                    int material = world.get(x, top - 1, z) & 0xFF;
                    int rgb = Materials.rgb(material);
                    // Shade by height so walls and platforms read at a glance.
                    float shade = 0.45f + Math.min(0.55f, top / 26f);
                    int r = (int) (((rgb >> 16) & 0xFF) * shade);
                    int g = (int) (((rgb >> 8) & 0xFF) * shade);
                    int b = (int) ((rgb & 0xFF) * shade);
                    color = 0xFF000000 | (r << 16) | (g << 8) | b;
                }
                pixels[x + width * z] = color;
            }
        }
        return Bitmap.createBitmap(pixels, width, depth, Bitmap.Config.ARGB_8888);
    }
}
