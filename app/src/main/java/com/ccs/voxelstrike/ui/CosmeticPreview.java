package com.ccs.voxelstrike.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import com.ccs.voxelstrike.app.Cosmetics;
import com.ccs.voxelstrike.model.Models;
import com.ccs.voxelstrike.model.VoxelModel;
import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.weapon.WeaponType;

/**
 * Renders a cosmetic exactly as the match builds it — same voxel models, same recolouring — into
 * an isometric bitmap, so the arsenal shows the item itself rather than a colour dot.
 */
public final class CosmeticPreview {
    private CosmeticPreview() {}

    /** The item as it is worn: a painted gun, a glove, a charm, or the crosshair shape. */
    public static Bitmap render(Cosmetics.Item item, int width, int height) {
        return render(item, null, width, height);
    }

    /**
     * As above, but a skin is shown on {@code weapon} rather than on the stock rifle — paints are
     * chosen per weapon, so the preview has to be told which gun is being painted.
     */
    public static Bitmap render(Cosmetics.Item item, WeaponType weapon, int width, int height) {
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        if (item.category() == Cosmetics.Category.CROSSHAIR) {
            drawCrosshair(canvas, item.value(), width, height);
            return result;
        }
        VoxelModel model = model(item, weapon);
        if (model != null) {
            drawIsometric(canvas, model, width, height);
        }
        return result;
    }

    /** Stock weapon render used by the in-match loadout terminal. */
    public static Bitmap renderWeapon(WeaponType weapon, int width, int height) {
        return renderWeapon(weapon, 0, width, height);
    }

    /** The exact weapon and equipped finish used by the in-match HUD rack. */
    public static Bitmap renderWeapon(WeaponType weapon, int skin, int width, int height) {
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        VoxelModel model = Models.weapon(weapon.modelKey());
        Cosmetics.applyWeaponSkin(model, skin);
        drawIsometric(new Canvas(result), model, width, height);
        return result;
    }

    public static VoxelModel model(Cosmetics.Item item) {
        return model(item, null);
    }

    /** The same geometry the renderer uses for this category, painted with the item's material. */
    public static VoxelModel model(Cosmetics.Item item, WeaponType weapon) {
        int value = item.value();
        switch (item.category()) {
            case SKIN:
                // Mirrors EntityRenderer: all weapon surfaces receive the same patterned finish.
                VoxelModel gun = Models.weapon(weapon == null ? "rifle" : weapon.modelKey());
                Cosmetics.applyWeaponSkin(gun, value);
                return gun;
            case GLOVES:
                int cloth = value == 0 ? Materials.CLOTH_DARK : Cosmetics.material(value);
                return Models.glove(cloth);
            case CHARM:
                if (value == 0) {
                    return null;
                }
                return new VoxelModel(3, 7, 3).box(1, 3, 1, 1, 6, 1, Materials.GUN_METAL)
                        .box(0, 0, 0, 2, 2, 2, value == 1 ? Materials.MINT : Materials.SUNNY);
            default:
                return null;
        }
    }

    /**
     * Painter's algorithm over the voxel grid: cubes are drawn back to front along the view
     * diagonal, and a face is skipped when the neighbour in front of it fills it in anyway.
     */
    private static void drawIsometric(Canvas canvas, VoxelModel model, int width, int height) {
        // Fit the model's isometric footprint into the bitmap with a small margin.
        float spanX = (model.sizeX + model.sizeZ) * 0.5f;
        float spanY = (model.sizeX + model.sizeZ) * 0.25f + model.sizeY * 0.75f;
        float scale = Math.min((width - 8f) / Math.max(spanX, 1e-3f),
                (height - 8f) / Math.max(spanY, 1e-3f));
        float halfW = scale * 0.5f;
        float quarterH = scale * 0.25f;
        float cubeH = scale * 0.75f;
        float originX = width * 0.5f + (model.sizeZ - model.sizeX) * 0.5f * halfW;
        float originY = height * 0.5f - (model.sizeX + model.sizeZ) * 0.5f * quarterH
                + model.sizeY * 0.5f * cubeH;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path path = new Path();
        for (int diagonal = 0; diagonal <= model.sizeX + model.sizeZ - 2; diagonal++) {
            for (int y = 0; y < model.sizeY; y++) {
                for (int x = Math.max(0, diagonal - model.sizeZ + 1);
                        x <= Math.min(diagonal, model.sizeX - 1); x++) {
                    int z = diagonal - x;
                    int material = model.get(x, y, z) & 255;
                    if (material == 0) {
                        continue;
                    }
                    int color = 0xFF000000 | Materials.rgb(material);
                    float sx = originX + (x - z) * halfW;
                    float sy = originY + (x + z) * quarterH - y * cubeH;
                    if (model.get(x, y + 1, z) == 0) {
                        face(canvas, paint, path, shade(color, 1f),
                                sx, sy - cubeH, sx + halfW, sy - cubeH + quarterH,
                                sx, sy - cubeH + quarterH * 2f, sx - halfW, sy - cubeH + quarterH);
                    }
                    if (model.get(x + 1, y, z) == 0) {
                        face(canvas, paint, path, shade(color, 0.72f),
                                sx, sy + quarterH * 2f - cubeH, sx + halfW, sy + quarterH - cubeH,
                                sx + halfW, sy + quarterH, sx, sy + quarterH * 2f);
                    }
                    if (model.get(x, y, z + 1) == 0) {
                        face(canvas, paint, path, shade(color, 0.5f),
                                sx, sy + quarterH * 2f - cubeH, sx - halfW, sy + quarterH - cubeH,
                                sx - halfW, sy + quarterH, sx, sy + quarterH * 2f);
                    }
                }
            }
        }
    }

    private static void face(Canvas canvas, Paint paint, Path path, int color,
                             float x0, float y0, float x1, float y1,
                             float x2, float y2, float x3, float y3) {
        path.reset();
        path.moveTo(x0, y0);
        path.lineTo(x1, y1);
        path.lineTo(x2, y2);
        path.lineTo(x3, y3);
        path.close();
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(path, paint);
    }

    /** Crosshairs have no geometry, so the preview draws the HUD shape the value selects. */
    private static void drawCrosshair(Canvas canvas, int value, int width, int height) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xFFFFFFFF);
        float cx = width * 0.5f, cy = height * 0.5f;
        float radius = Math.min(width, height) * 0.3f;
        if (value == 1) {
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy, Math.max(1.5f, radius * 0.22f), paint);
            return;
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, radius * 0.16f));
        if (value == 2) {
            canvas.drawCircle(cx, cy, radius, paint);
            return;
        }
        float gap = radius * 0.35f;
        canvas.drawLine(cx, cy - radius, cx, cy - gap, paint);
        canvas.drawLine(cx, cy + gap, cx, cy + radius, paint);
        canvas.drawLine(cx - radius, cy, cx - gap, cy, paint);
        canvas.drawLine(cx + gap, cy, cx + radius, cy, paint);
    }

    private static int shade(int rgb, float gain) {
        return 0xFF000000 | (int) (((rgb >> 16) & 255) * gain) << 16
                | (int) (((rgb >> 8) & 255) * gain) << 8 | (int) ((rgb & 255) * gain);
    }
}
