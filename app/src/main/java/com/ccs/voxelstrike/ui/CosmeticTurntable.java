package com.ccs.voxelstrike.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import com.ccs.voxelstrike.app.Cosmetics;
import com.ccs.voxelstrike.model.VoxelModel;
import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.weapon.WeaponType;

/**
 * A large, draggable view of one cosmetic: the voxel model is rotated on a turntable and drawn
 * cube by cube, so the item can be inspected from any side instead of one fixed isometric angle.
 *
 * <p>The renderer is a tiny software rasteriser — orthographic projection, painter's algorithm
 * over exposed voxel centres, back faces culled by the rotated normal. The surface is cached when
 * the item changes, so detailed models never rescan or sort their solid interior while spinning.
 */
public final class CosmeticTurntable extends View {
    /** Idle spin, in radians per second, so the item reads as three dimensional before a touch. */
    private static final float IDLE_SPIN = 0.5f;
    private static final float FLICK_DECAY = 2.6f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    /** Rotated cube corners, shared by every voxel: the rotation is the same for all of them. */
    private final float[] cornerX = new float[8];
    private final float[] cornerY = new float[8];
    private int[] drawOrder = new int[0];
    private float[] drawDepth = new float[0];
    private float[] surfaceX = new float[0];
    private float[] surfaceY = new float[0];
    private float[] surfaceZ = new float[0];
    private float[] screenX = new float[0];
    private float[] screenY = new float[0];
    private byte[] surfaceFaces = new byte[0];
    private byte[] surfaceMaterials = new byte[0];
    private final int[] materialColors = new int[256];
    private final int[] faceColors = new int[6 * 256];
    private int[] usedMaterials = new int[0];

    private VoxelModel model;
    private Cosmetics.Item item;
    private float yaw = (float) (Math.PI * 0.25);
    private float pitch = -0.45f;
    private float spin = IDLE_SPIN;
    private boolean dragging;
    private float lastX;
    private float lastY;
    private long lastFrame;

    public CosmeticTurntable(Context context) {
        this(context, null);
    }

    public CosmeticTurntable(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.FILL);
    }

    /** Points the turntable at another cosmetic, keeping whatever angle the player turned it to. */
    public void setItem(Cosmetics.Item value) {
        setItem(value, null);
    }

    /** As above, with the weapon a skin is being painted onto. */
    public void setItem(Cosmetics.Item value, WeaponType weapon) {
        item = value;
        model = value == null ? null : CosmeticPreview.model(value, weapon);
        cacheSurface();
        invalidate();
    }

    public Cosmetics.Item item() {
        return item;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                spin = 0f;
                lastX = event.getX();
                lastY = event.getY();
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = event.getX() - lastX;
                float dy = event.getY() - lastY;
                lastX = event.getX();
                lastY = event.getY();
                yaw += dx * 0.012f;
                // Clamped so the model never rolls past vertical and reads upside down.
                pitch = Math.max(-1.2f, Math.min(1.2f, pitch + dy * 0.010f));
                // A flick keeps turning after the finger leaves.
                spin = dx * 0.012f * 12f;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    performClick();
                }
                invalidate();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private static final float[] CORNER_X =
            {-0.5f, 0.5f, -0.5f, 0.5f, -0.5f, 0.5f, -0.5f, 0.5f};
    private static final float[] CORNER_Y =
            {-0.5f, -0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f};
    private static final float[] CORNER_Z =
            {-0.5f, -0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f};
    private static final float[][] NORMALS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = android.os.SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        if (!dragging) {
            yaw += spin * dt;
            // A flick decays back into the idle spin rather than stopping dead.
            spin += (IDLE_SPIN - spin) * Math.min(1f, FLICK_DECAY * dt);
        }

        if (item != null && item.category() == Cosmetics.Category.CROSSHAIR) {
            drawCrosshairItem(canvas);
            return;
        }
        if (model != null) {
            drawModel(canvas);
        }
        if (model != null && !dragging) {
            postInvalidateOnAnimation();
        }
    }

    private void drawModel(Canvas canvas) {
        float cosYaw = (float) Math.cos(yaw), sinYaw = (float) Math.sin(yaw);
        float cosPitch = (float) Math.cos(pitch), sinPitch = (float) Math.sin(pitch);

        // Corner and normal tables, rotated once for the whole model.
        for (int i = 0; i < 8; i++) {
            float rx = CORNER_X[i] * cosYaw + CORNER_Z[i] * sinYaw;
            float rz = -CORNER_X[i] * sinYaw + CORNER_Z[i] * cosYaw;
            float ry = CORNER_Y[i] * cosPitch - rz * sinPitch;
            cornerX[i] = rx;
            cornerY[i] = ry;
        }
        // Face normals: +X, -X, +Y, -Y, +Z, -Z. Only the depth component decides visibility, and
        // the light is fixed in view space so the shading stays readable at every angle.
        int visibleFaces = 0;
        for (int f = 0; f < 6; f++) {
            float nx = NORMALS[f][0], ny = NORMALS[f][1], nz = NORMALS[f][2];
            float rx = nx * cosYaw + nz * sinYaw;
            float rz = -nx * sinYaw + nz * cosYaw;
            float ry = ny * cosPitch - rz * sinPitch;
            float rzz = ny * sinPitch + rz * cosPitch;
            float shade = 0.45f + 0.55f * Math.max(0f, rx * -0.35f + ry * 0.72f + rzz * 0.60f);
            if (rzz > 0f) {
                visibleFaces |= 1 << f;
                for (int material : usedMaterials) {
                    faceColors[f * 256 + material] = tint(materialColors[material], shade);
                }
            }
        }

        float scale = fitScale();
        float centreX = getWidth() * 0.5f;
        float centreY = getHeight() * 0.5f;
        // Keep unit-voxel depth ordering: merging large faces would make their centre depth an
        // unreliable painter's order around sights, rails and other overlapping gun details.
        int count = 0;
        int[] order = drawOrder;
        float[] depth = drawDepth;
        for (int i = 0; i < surfaceFaces.length; i++) {
            if ((surfaceFaces[i] & visibleFaces) == 0) continue;
            float px = surfaceX[i], py = surfaceY[i], pz = surfaceZ[i];
            float rx = px * cosYaw + pz * sinYaw;
            float rz = -px * sinYaw + pz * cosYaw;
            float ry = py * cosPitch - rz * sinPitch;
            screenX[i] = centreX + rx * scale;
            screenY[i] = centreY - ry * scale;
            order[count] = i;
            depth[count] = py * sinPitch + rz * cosPitch;
            count++;
        }
        sortByDepth(order, depth, count);
        for (int i = 0; i < count; i++) {
            int index = order[i];
            drawCube(canvas, screenX[index], screenY[index], scale,
                    surfaceMaterials[index] & 255, surfaceFaces[index] & visibleFaces);
        }
    }

    /** Corner indices per face, in winding order, matching the normal table above. */
    private static final int[][] FACES = {
        {1, 5, 7, 3}, {0, 2, 6, 4}, {2, 3, 7, 6}, {0, 4, 5, 1}, {4, 6, 7, 5}, {0, 1, 3, 2}
    };
    private static final int[][] FACE_STEP = {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private void drawCube(Canvas canvas, float sx, float sy, float scale, int material, int faces) {
        for (int f = 0; f < 6; f++) {
            if ((faces & (1 << f)) == 0) continue;
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
            paint.setColor(faceColors[f * 256 + material]);
            canvas.drawPath(path, paint);
        }
    }

    /**
     * Scale that keeps the model inside the view at <em>every</em> angle. Fitting the current
     * angle instead would make the item swell and shrink as it turns, so the bound is taken over
     * a full revolution: horizontally the diagonal of the footprint, vertically that plus height.
     */
    private float fitScale() {
        float hx = model.sizeX * 0.5f, hy = model.sizeY * 0.5f, hz = model.sizeZ * 0.5f;
        float radius = (float) Math.hypot(hx, hz);
        float usableWidth = (getWidth() - getPaddingLeft() - getPaddingRight()) * 0.92f;
        float usableHeight = (getHeight() - getPaddingTop() - getPaddingBottom()) * 0.92f;
        return Math.min(usableWidth / Math.max(2f * radius, 1e-3f),
                usableHeight / Math.max(2f * (hy + radius * (float) Math.sin(Math.abs(pitch))), 1e-3f));
    }

    /** In-place heapsort: bounded O(n log n), with no objects allocated while the item rotates. */
    private static void sortByDepth(int[] order, float[] depth, int count) {
        for (int root = count / 2 - 1; root >= 0; root--) siftDown(order, depth, root, count);
        for (int end = count - 1; end > 0; end--) {
            int index = order[end];
            float value = depth[end];
            order[end] = order[0];
            depth[end] = depth[0];
            order[0] = index;
            depth[0] = value;
            siftDown(order, depth, 0, end);
        }
    }

    private static void siftDown(int[] order, float[] depth, int root, int count) {
        int index = order[root];
        float value = depth[root];
        for (int child = root * 2 + 1; child < count; child = root * 2 + 1) {
            if (child + 1 < count && (depth[child + 1] > depth[child]
                    || (depth[child + 1] == depth[child] && order[child + 1] > order[child]))) {
                child++;
            }
            if (value > depth[child] || (value == depth[child] && index >= order[child])) break;
            order[root] = order[child];
            depth[root] = depth[child];
            root = child;
        }
        order[root] = index;
        depth[root] = value;
    }

    /** Builds the visible shell once; sealed internal voxels never reach per-frame sorting. */
    private void cacheSurface() {
        int total = model == null ? 0 : model.voxels.length;
        byte[] masks = new byte[total];
        boolean[] used = new boolean[256];
        int count = 0;
        if (model != null) {
            for (int z = 0; z < model.sizeZ; z++) {
                for (int y = 0; y < model.sizeY; y++) {
                    for (int x = 0; x < model.sizeX; x++) {
                        if (model.get(x, y, z) == 0) continue;
                        int faces = 0;
                        for (int f = 0; f < 6; f++) {
                            if (model.get(x + FACE_STEP[f][0], y + FACE_STEP[f][1],
                                    z + FACE_STEP[f][2]) == 0) faces |= 1 << f;
                        }
                        if (faces != 0) {
                            masks[x + model.sizeX * (y + model.sizeY * z)] = (byte) faces;
                            used[model.get(x, y, z) & 255] = true;
                            count++;
                        }
                    }
                }
            }
        }
        surfaceX = new float[count];
        surfaceY = new float[count];
        surfaceZ = new float[count];
        screenX = new float[count];
        screenY = new float[count];
        surfaceFaces = new byte[count];
        surfaceMaterials = new byte[count];
        drawOrder = new int[count];
        drawDepth = new float[count];
        int materialCount = 0;
        for (boolean present : used) if (present) materialCount++;
        usedMaterials = new int[materialCount];
        for (int material = 0, i = 0; material < used.length; material++) {
            if (!used[material]) continue;
            usedMaterials[i++] = material;
            materialColors[material] = 0xFF000000 | Materials.rgb(material);
        }
        if (model == null) return;
        for (int z = 0, i = 0; z < model.sizeZ; z++) {
            for (int y = 0; y < model.sizeY; y++) {
                for (int x = 0; x < model.sizeX; x++) {
                    int voxel = x + model.sizeX * (y + model.sizeY * z);
                    if (masks[voxel] == 0) continue;
                    surfaceX[i] = x + 0.5f - model.sizeX * 0.5f;
                    surfaceY[i] = y + 0.5f - model.sizeY * 0.5f;
                    surfaceZ[i] = z + 0.5f - model.sizeZ * 0.5f;
                    surfaceFaces[i] = masks[voxel];
                    surfaceMaterials[i] = model.voxels[voxel];
                    i++;
                }
            }
        }
    }

    /** Crosshairs have no geometry; the big preview draws the shape at HUD scale. */
    private void drawCrosshairItem(Canvas canvas) {
        paint.setColor(0xFFFFFFFF);
        float cx = getWidth() * 0.5f, cy = getHeight() * 0.5f;
        float radius = Math.min(getWidth(), getHeight()) * 0.22f;
        int value = item.value();
        if (value == 1) {
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy, radius * 0.22f, paint);
            paint.setStyle(Paint.Style.FILL);
            return;
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, radius * 0.12f));
        if (value == 2) {
            canvas.drawCircle(cx, cy, radius, paint);
        } else {
            float gap = radius * 0.35f;
            canvas.drawLine(cx, cy - radius, cx, cy - gap, paint);
            canvas.drawLine(cx, cy + gap, cx, cy + radius, paint);
            canvas.drawLine(cx - radius, cy, cx - gap, cy, paint);
            canvas.drawLine(cx + gap, cy, cx + radius, cy, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private static int tint(int rgb, float gain) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * gain));
        int g = Math.min(255, (int) (((rgb >> 8) & 255) * gain));
        int b = Math.min(255, (int) ((rgb & 255) * gain));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
