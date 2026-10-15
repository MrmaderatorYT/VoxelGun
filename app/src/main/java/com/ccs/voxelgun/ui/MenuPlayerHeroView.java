package com.ccs.voxelgun.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.ccs.voxelgun.model.Models;
import com.ccs.voxelgun.model.VoxelModel;
import com.ccs.voxelgun.voxel.Materials;

/**
 * Lightweight title-screen render of the local operator. It reuses the same voxel body parts as
 * combatants in a match, but stays Canvas-only so opening the menu never spins up GL resources.
 */
public final class MenuPlayerHeroView extends View {
    private static final int PART_HEAD = 0;
    private static final int PART_TORSO = 1;
    private static final int PART_LEFT_ARM = 2;
    private static final int PART_RIGHT_ARM = 3;
    private static final int PART_LEFT_LEG = 4;
    private static final int PART_RIGHT_LEG = 5;

    private static final float[] JOINT_X = {0f, 0f, -6.1f, 6.1f, -2.2f, 2.2f};
    private static final float[] JOINT_Y = {24f, 12f, 22.5f, 22.5f, 12f, 12f};
    private static final float[] JOINT_Z = {-0.3f, 0f, 0.4f, 0.4f, 0.3f, 0.3f};

    private static final float[] CORNER_X =
            {-0.5f, 0.5f, -0.5f, 0.5f, -0.5f, 0.5f, -0.5f, 0.5f};
    private static final float[] CORNER_Y =
            {-0.5f, -0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f};
    private static final float[] CORNER_Z =
            {-0.5f, -0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f};
    private static final float[][] NORMALS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };
    private static final int[][] FACES = {
            {1, 5, 7, 3}, {0, 2, 6, 4}, {2, 3, 7, 6},
            {0, 4, 5, 1}, {4, 6, 7, 5}, {0, 1, 3, 2}
    };
    private static final int[][] FACE_STEP = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private final VoxelModel[] parts = new VoxelModel[6];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float[] cornerX = new float[8];
    private final float[] cornerY = new float[8];
    private final float[] normalZ = new float[6];
    private final float[] shade = new float[6];
    private final int[] order;
    private final float[] depth;

    public MenuPlayerHeroView(Context context) {
        this(context, null);
    }

    public MenuPlayerHeroView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        parts[PART_HEAD] = Models.head(Materials.TEAM_BLUE, false);
        parts[PART_TORSO] = Models.torso(Materials.TEAM_BLUE, false);
        VoxelModel arm = Models.arm(Materials.TEAM_BLUE, false);
        parts[PART_LEFT_ARM] = arm;
        parts[PART_RIGHT_ARM] = arm.copy();
        VoxelModel leg = Models.leg(false);
        parts[PART_LEFT_LEG] = leg;
        parts[PART_RIGHT_LEG] = leg.copy();

        int total = 0;
        for (VoxelModel model : parts) {
            total += model.sizeX * model.sizeY * model.sizeZ;
        }
        order = new int[total];
        depth = new float[total];
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0) {
            return;
        }

        float seconds = SystemClock.uptimeMillis() * 0.001f;
        // Models face local -Z. Show the visor, with a subtle three-quarter, eye-level view.
        float yaw = (float) Math.PI - 0.25f + (float) Math.sin(seconds * 0.55f) * 0.015f;
        float pitch = 0.06f;
        float cosYaw = (float) Math.cos(yaw);
        float sinYaw = (float) Math.sin(yaw);
        float cosPitch = (float) Math.cos(pitch);
        float sinPitch = (float) Math.sin(pitch);
        updateFaceTables(cosYaw, sinYaw, cosPitch, sinPitch);

        int count = collectVisibleVoxels(cosYaw, sinYaw, cosPitch, sinPitch, seconds);
        sortByDepth(order, depth, count);

        float scale = Math.min(getWidth() / 26f, getHeight() / 37f);
        float centreX = getWidth() * 0.50f;
        float floorY = getHeight() * 0.84f;
        for (int i = 0; i < count; i++) {
            int packed = order[i];
            int part = packed >>> 24;
            int x = (packed >>> 16) & 255;
            int y = (packed >>> 8) & 255;
            int z = packed & 255;
            VoxelModel model = parts[part];
            int material = model.get(x, y, z) & 255;
            int color = 0xFF000000 | Materials.rgb(material);
            float wx = worldX(model, part, x);
            float wy = worldY(model, part, y, seconds);
            float wz = worldZ(model, part, z);
            float rx = wx * cosYaw + wz * sinYaw;
            float rz = -wx * sinYaw + wz * cosYaw;
            float ry = wy * cosPitch - rz * sinPitch;
            drawCube(canvas, model, part, x, y, z, centreX + rx * scale,
                    floorY - ry * scale, scale, color);
        }
        if (isShown()) {
            postInvalidateDelayed(50L);
        }
    }

    private int collectVisibleVoxels(float cosYaw, float sinYaw, float cosPitch, float sinPitch,
                                     float seconds) {
        int count = 0;
        for (int part = 0; part < parts.length; part++) {
            VoxelModel model = parts[part];
            for (int z = 0; z < model.sizeZ; z++) {
                for (int y = 0; y < model.sizeY; y++) {
                    for (int x = 0; x < model.sizeX; x++) {
                        if (model.get(x, y, z) == 0) {
                            continue;
                        }
                        float wx = worldX(model, part, x);
                        float wy = worldY(model, part, y, seconds);
                        float wz = worldZ(model, part, z);
                        float rz = -wx * sinYaw + wz * cosYaw;
                        order[count] = (part << 24) | (x << 16) | (y << 8) | z;
                        depth[count] = wy * sinPitch + rz * cosPitch;
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static float worldX(VoxelModel model, int part, int x) {
        return JOINT_X[part] + x + 0.5f - model.pivotX;
    }

    private static float worldY(VoxelModel model, int part, int y, float seconds) {
        float breathing = (float) Math.sin(seconds * 1.4f) * 0.12f;
        return JOINT_Y[part] + y + 0.5f - model.pivotY + breathing;
    }

    private static float worldZ(VoxelModel model, int part, int z) {
        return JOINT_Z[part] + z + 0.5f - model.pivotZ;
    }

    private void updateFaceTables(float cosYaw, float sinYaw, float cosPitch, float sinPitch) {
        for (int i = 0; i < 8; i++) {
            float rx = CORNER_X[i] * cosYaw + CORNER_Z[i] * sinYaw;
            float rz = -CORNER_X[i] * sinYaw + CORNER_Z[i] * cosYaw;
            cornerX[i] = rx;
            cornerY[i] = CORNER_Y[i] * cosPitch - rz * sinPitch;
        }
        for (int f = 0; f < 6; f++) {
            float nx = NORMALS[f][0];
            float ny = NORMALS[f][1];
            float nz = NORMALS[f][2];
            float rx = nx * cosYaw + nz * sinYaw;
            float rz = -nx * sinYaw + nz * cosYaw;
            float ry = ny * cosPitch - rz * sinPitch;
            normalZ[f] = ny * sinPitch + rz * cosPitch;
            shade[f] = 0.44f + 0.56f * Math.max(0f, rx * -0.35f + ry * 0.72f
                    + normalZ[f] * 0.60f);
        }
    }

    private void drawCube(Canvas canvas, VoxelModel model, int part, int x, int y, int z,
                          float sx, float sy, float scale, int color) {
        for (int f = 0; f < 6; f++) {
            if (normalZ[f] <= 0f || model.get(x + FACE_STEP[f][0], y + FACE_STEP[f][1],
                    z + FACE_STEP[f][2]) != 0) {
                continue;
            }
            int[] face = FACES[f];
            path.reset();
            for (int c = 0; c < 4; c++) {
                float px = sx + cornerX[face[c]] * scale;
                float py = sy - cornerY[face[c]] * scale;
                if (c == 0) {
                    path.moveTo(px, py);
                } else {
                    path.lineTo(px, py);
                }
            }
            path.close();
            paint.setColor(tint(part == PART_HEAD ? brighten(color, 1.04f) : color, shade[f]));
            canvas.drawPath(path, paint);
        }
    }

    private static void sortByDepth(int[] order, float[] depth, int count) {
        for (int i = 1; i < count; i++) {
            int value = order[i];
            float key = depth[i];
            int j = i - 1;
            while (j >= 0 && depth[j] > key) {
                depth[j + 1] = depth[j];
                order[j + 1] = order[j];
                j--;
            }
            depth[j + 1] = key;
            order[j + 1] = value;
        }
    }

    private static int brighten(int rgb, float gain) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * gain));
        int g = Math.min(255, (int) (((rgb >> 8) & 255) * gain));
        int b = Math.min(255, (int) ((rgb & 255) * gain));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int tint(int rgb, float gain) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * gain));
        int g = Math.min(255, (int) (((rgb >> 8) & 255) * gain));
        int b = Math.min(255, (int) ((rgb & 255) * gain));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
