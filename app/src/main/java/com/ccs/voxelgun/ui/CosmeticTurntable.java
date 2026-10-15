package com.ccs.voxelgun.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import com.ccs.voxelgun.app.Cosmetics;
import com.ccs.voxelgun.model.VoxelModel;
import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.weapon.WeaponType;

/**
 * A large, draggable view of one cosmetic: the voxel model is rotated on a turntable and drawn
 * cube by cube, so the item can be inspected from any side instead of one fixed isometric angle.
 *
 * <p>The renderer is a tiny software rasteriser — orthographic projection, painter's algorithm
 * over the voxel centres, back faces culled by the rotated normal. The models are a few thousand
 * voxels at most, which is well inside what a Canvas can draw at 60fps.
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
    private final float[] normalZ = new float[6];
    private final float[] shade = new float[6];
    private int[] drawOrder = new int[0];
    private float[] drawDepth = new float[0];

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
        if (!dragging || Math.abs(spin) > 1e-3f) {
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
        for (int f = 0; f < 6; f++) {
            float nx = NORMALS[f][0], ny = NORMALS[f][1], nz = NORMALS[f][2];
            float rx = nx * cosYaw + nz * sinYaw;
            float rz = -nx * sinYaw + nz * cosYaw;
            float ry = ny * cosPitch - rz * sinPitch;
            float rzz = ny * sinPitch + rz * cosPitch;
            normalZ[f] = rzz;
            shade[f] = 0.45f + 0.55f * Math.max(0f, rx * -0.35f + ry * 0.72f + rzz * 0.60f);
        }

        float scale = fitScale();
        float centreX = getWidth() * 0.5f;
        float centreY = getHeight() * 0.5f;
        float pivotX = model.sizeX * 0.5f, pivotY = model.sizeY * 0.5f, pivotZ = model.sizeZ * 0.5f;

        // Painter's algorithm: sort the filled voxels by view depth and draw the far ones first.
        int count = 0;
        int total = model.sizeX * model.sizeY * model.sizeZ;
        ensureCapacity(total);
        int[] order = drawOrder;
        float[] depth = drawDepth;
        for (int z = 0; z < model.sizeZ; z++) {
            for (int y = 0; y < model.sizeY; y++) {
                for (int x = 0; x < model.sizeX; x++) {
                    if (model.get(x, y, z) == 0) {
                        continue;
                    }
                    float px = x + 0.5f - pivotX, py = y + 0.5f - pivotY, pz = z + 0.5f - pivotZ;
                    float rz = -px * sinYaw + pz * cosYaw;
                    order[count] = (x << 20) | (y << 10) | z;
                    depth[count] = py * sinPitch + rz * cosPitch;
                    count++;
                }
            }
        }
        sortByDepth(order, depth, count);

        for (int i = 0; i < count; i++) {
            int packed = order[i];
            int x = packed >>> 20, y = (packed >>> 10) & 1023, z = packed & 1023;
            int material = model.get(x, y, z) & 255;
            int color = 0xFF000000 | Materials.rgb(material);
            float px = x + 0.5f - pivotX, py = y + 0.5f - pivotY, pz = z + 0.5f - pivotZ;
            float rx = px * cosYaw + pz * sinYaw;
            float rz = -px * sinYaw + pz * cosYaw;
            float ry = py * cosPitch - rz * sinPitch;
            float sx = centreX + rx * scale;
            float sy = centreY - ry * scale;
            drawCube(canvas, sx, sy, scale, color, x, y, z);
        }
    }

    /** Corner indices per face, in winding order, matching the normal table above. */
    private static final int[][] FACES = {
        {1, 5, 7, 3}, {0, 2, 6, 4}, {2, 3, 7, 6}, {0, 4, 5, 1}, {4, 6, 7, 5}, {0, 1, 3, 2}
    };
    private static final int[][] FACE_STEP = {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private void drawCube(Canvas canvas, float sx, float sy, float scale, int color,
                          int x, int y, int z) {
        for (int f = 0; f < 6; f++) {
            if (normalZ[f] <= 0f) {
                continue;                       // back face
            }
            if (model.get(x + FACE_STEP[f][0], y + FACE_STEP[f][1], z + FACE_STEP[f][2]) != 0) {
                continue;                       // hidden behind a neighbouring voxel
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
            paint.setColor(tint(color, shade[f]));
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

    /** Insertion sort: the voxel counts here are small and nearly sorted between frames. */
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

    private void ensureCapacity(int total) {
        if (drawOrder.length >= total) {
            return;
        }
        drawOrder = new int[total];
        drawDepth = new float[total];
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
