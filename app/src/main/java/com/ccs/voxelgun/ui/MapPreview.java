package com.ccs.voxelgun.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import com.ccs.voxelgun.map.GameMap;
import com.ccs.voxelgun.map.SpawnPoint;
import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.voxel.VoxelWorld;

/** Actual map columns, rendered as an isometric bitmap beside a north-up tactical plan. */
public final class MapPreview {
    private MapPreview() {}
    public static Bitmap render(VoxelWorld world, GameMap map) {
        int legendRows = (map.scenery().landmarks().size() + 2) / 3;
        Bitmap result = Bitmap.createBitmap(900, Math.max(420, 410 + legendRows * 21),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        canvas.drawColor(0xFF191E22);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path path = new Path();
        // The original projection was tuned for 128x128 arenas. Scale larger Battlefield volumes
        // into the same left-hand panel instead of clipping their outer districts off the bitmap.
        float isoScale = Math.min(2.35f,
                600f / Math.max(1f, world.sizeX() + world.sizeZ() - 2f));
        float depthScale = isoScale * 0.48f;
        float heightScale = Math.max(1.15f, Math.min(2.4f, isoScale * 1.04f));
        float halfColumn = Math.max(1.2f, isoScale * 1.03f);
        for (int z = 0; z < world.sizeZ(); z++) for (int x = 0; x < world.sizeX(); x++) {
            int y = world.height(x, z);
            if (y <= 0) continue;
            float sx = 315 + (x - z) * isoScale;
            float sy = 80 + (x + z) * depthScale - y * heightScale;
            int color = 0xFF000000 | Materials.rgb(world.get(x, y - 1, z) & 255);
            paint.setColor(shade(color, 0.55f));
            canvas.drawRect(sx - halfColumn, sy, sx + halfColumn,
                    sy + y * heightScale + halfColumn, paint);
            path.reset(); path.moveTo(sx, sy - halfColumn * 0.5f);
            path.lineTo(sx + halfColumn, sy);
            path.lineTo(sx, sy + halfColumn * 0.5f);
            path.lineTo(sx - halfColumn, sy); path.close();
            paint.setColor(color); canvas.drawPath(path, paint);
        }
        Bitmap plan = Minimap.render(world);
        RectF bounds = new RectF(650, 92, 890, 332);
        canvas.drawBitmap(plan, null, bounds, paint);
        plan.recycle();
        paint.setColor(0xFF65BBFF);
        for (SpawnPoint s : map.blueSpawns()) canvas.drawCircle(650 + s.x / world.sizeX() * 240,
                92 + s.z / world.sizeZ() * 240, 3.5f, paint);
        paint.setColor(0xFFFF7060);
        for (SpawnPoint s : map.redSpawns()) canvas.drawCircle(650 + s.x / world.sizeX() * 240,
                92 + s.z / world.sizeZ() * 240, 3.5f, paint);
        int number = 0;
        for (com.ccs.voxelgun.map.scenery.Landmark landmark : map.scenery().landmarks()) {
            number++;
            float x = 650 + landmark.x / world.sizeX() * 240;
            float z = 92 + landmark.z / world.sizeZ() * 240;
            paint.setColor(0xFF14222D);
            canvas.drawCircle(x, z, 9, paint);
            paint.setColor(0xFFFFD184);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(12);
            canvas.drawText(Integer.toString(number), x, z + 4, paint);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSize(13);
            // Full-size maps have several distinct destinations. Keep the complete legend below
            // both views rather than clipping every callout after the third off the bitmap.
            int column = (number - 1) % 3;
            int row = (number - 1) / 3;
            canvas.drawText(number + " · " + landmark.name, 18 + column * 300,
                    410 + row * 21, paint);
        }
        return result;
    }
    private static int shade(int rgb, float gain) {
        return 0xFF000000 | (int) (((rgb >> 16) & 255) * gain) << 16
                | (int) (((rgb >> 8) & 255) * gain) << 8 | (int) ((rgb & 255) * gain);
    }
}
