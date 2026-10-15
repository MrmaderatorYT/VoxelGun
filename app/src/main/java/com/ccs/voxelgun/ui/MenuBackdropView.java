package com.ccs.voxelgun.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Random;

/**
 * Lightweight animated menu scene.  It deliberately uses Canvas primitives instead of a
 * Material surface so the front end reads as a game before the first match has even started.
 */
public final class MenuBackdropView extends View {
    private static final int PARTICLE_COUNT = 42;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float[] particleX = new float[PARTICLE_COUNT];
    private final float[] particleY = new float[PARTICLE_COUNT];
    private final float[] particleSize = new float[PARTICLE_COUNT];
    private final float[] particlePhase = new float[PARTICLE_COUNT];

    private LinearGradient sky;
    private RadialGradient horizonGlow;
    private int sceneWidth;
    private int sceneHeight;

    public MenuBackdropView(Context context) {
        this(context, null);
    }

    public MenuBackdropView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        Random random = new Random(0x564F58454CL);
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particleX[i] = random.nextFloat();
            particleY[i] = random.nextFloat();
            particleSize[i] = 0.45f + random.nextFloat() * 1.35f;
            particlePhase[i] = random.nextFloat() * 6.28318f;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        sceneWidth = w;
        sceneHeight = h;
        sky = new LinearGradient(0f, 0f, w, h,
                new int[]{Color.rgb(4, 7, 11), Color.rgb(9, 14, 20), Color.rgb(17, 12, 10)},
                new float[]{0f, 0.53f, 1f}, Shader.TileMode.CLAMP);
        horizonGlow = new RadialGradient(w * 0.73f, h * 0.50f, Math.max(w, h) * 0.48f,
                new int[]{0x5AFF6A16, 0x1AFF8A24, 0x00000000},
                new float[]{0f, 0.42f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (sceneWidth == 0 || sceneHeight == 0) return;

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(sky);
        canvas.drawRect(0f, 0f, sceneWidth, sceneHeight, paint);
        paint.setShader(horizonGlow);
        canvas.drawRect(0f, 0f, sceneWidth, sceneHeight, paint);
        paint.setShader(null);

        drawLightShafts(canvas);
        drawVoxelSkyline(canvas);
        drawPerspectiveGrid(canvas);
        drawParticles(canvas);
        drawScanLines(canvas);

        if (isShown()) postInvalidateDelayed(50L);
    }

    private void drawLightShafts(Canvas canvas) {
        path.reset();
        path.moveTo(sceneWidth * 0.57f, 0f);
        path.lineTo(sceneWidth * 0.69f, 0f);
        path.lineTo(sceneWidth * 0.92f, sceneHeight);
        path.lineTo(sceneWidth * 0.77f, sceneHeight);
        path.close();
        paint.setColor(0x09FF8A20);
        canvas.drawPath(path, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, sceneHeight / 540f));
        paint.setColor(0x2BFF7A18);
        canvas.drawLine(sceneWidth * 0.66f, 0f, sceneWidth * 0.86f, sceneHeight, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawVoxelSkyline(Canvas canvas) {
        float ground = sceneHeight * 0.63f;
        drawBlock(canvas, sceneWidth * 0.47f, ground, sceneWidth * 0.050f,
                sceneHeight * 0.035f, sceneHeight * 0.13f, 0xFF0B1118, 0xFF101923, 0xFF17222D);
        drawBlock(canvas, sceneWidth * 0.55f, ground + sceneHeight * 0.02f, sceneWidth * 0.07f,
                sceneHeight * 0.045f, sceneHeight * 0.21f, 0xFF0A1017, 0xFF0E1720, 0xFF18242E);
        drawBlock(canvas, sceneWidth * 0.68f, ground - sceneHeight * 0.01f, sceneWidth * 0.055f,
                sceneHeight * 0.038f, sceneHeight * 0.16f, 0xFF0A1016, 0xFF121A22, 0xFF1A252D);
        drawBlock(canvas, sceneWidth * 0.79f, ground + sceneHeight * 0.025f, sceneWidth * 0.085f,
                sceneHeight * 0.052f, sceneHeight * 0.24f, 0xFF090E14, 0xFF101820, 0xFF1A242C);
        drawBlock(canvas, sceneWidth * 0.93f, ground, sceneWidth * 0.045f,
                sceneHeight * 0.033f, sceneHeight * 0.12f, 0xFF0A0F15, 0xFF111921, 0xFF18232B);

        paint.setColor(0x8CFF6B16);
        float dash = Math.max(2f, sceneHeight * 0.008f);
        canvas.drawRect(sceneWidth * 0.72f, ground - sceneHeight * 0.19f,
                sceneWidth * 0.724f, ground - sceneHeight * 0.19f + dash, paint);
        canvas.drawRect(sceneWidth * 0.83f, ground - sceneHeight * 0.12f,
                sceneWidth * 0.834f, ground - sceneHeight * 0.12f + dash, paint);
    }

    private void drawBlock(Canvas canvas, float cx, float floorY, float halfWidth, float depth,
                           float height, int leftColor, int rightColor, int topColor) {
        float topY = floorY - height;

        path.reset();
        path.moveTo(cx - halfWidth, topY);
        path.lineTo(cx, topY - depth);
        path.lineTo(cx + halfWidth, topY);
        path.lineTo(cx, topY + depth);
        path.close();
        paint.setColor(topColor);
        canvas.drawPath(path, paint);

        path.reset();
        path.moveTo(cx - halfWidth, topY);
        path.lineTo(cx, topY + depth);
        path.lineTo(cx, floorY + depth);
        path.lineTo(cx - halfWidth, floorY);
        path.close();
        paint.setColor(leftColor);
        canvas.drawPath(path, paint);

        path.reset();
        path.moveTo(cx + halfWidth, topY);
        path.lineTo(cx, topY + depth);
        path.lineTo(cx, floorY + depth);
        path.lineTo(cx + halfWidth, floorY);
        path.close();
        paint.setColor(rightColor);
        canvas.drawPath(path, paint);
    }

    private void drawPerspectiveGrid(Canvas canvas) {
        float horizon = sceneHeight * 0.62f;
        float vanishX = sceneWidth * 0.72f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, sceneHeight / 900f));
        paint.setColor(0x214C9BA5);

        int rays = 18;
        for (int i = 0; i <= rays; i++) {
            float x = sceneWidth * i / (float) rays;
            canvas.drawLine(vanishX, horizon, x, sceneHeight, paint);
        }
        for (int i = 1; i <= 10; i++) {
            float t = i / 10f;
            float y = horizon + (sceneHeight - horizon) * t * t;
            canvas.drawLine(0f, y, sceneWidth, y, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawParticles(Canvas canvas) {
        float seconds = SystemClock.uptimeMillis() * 0.001f;
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            float pulse = 0.45f + 0.55f * (float) Math.sin(seconds * 0.72f + particlePhase[i]);
            int alpha = 18 + Math.round(42f * pulse);
            paint.setColor((alpha << 24) | (i % 4 == 0 ? 0x00FF8120 : 0x00B7DCE0));
            float x = particleX[i] * sceneWidth;
            float y = particleY[i] * sceneHeight
                    + (float) Math.sin(seconds * 0.24f + particlePhase[i]) * sceneHeight * 0.008f;
            canvas.drawRect(x, y, x + particleSize[i] * 1.6f, y + particleSize[i] * 1.6f, paint);
        }
    }

    private void drawScanLines(Canvas canvas) {
        paint.setColor(0x09000000);
        int spacing = Math.max(5, Math.round(getResources().getDisplayMetrics().density * 3f));
        for (int y = 0; y < sceneHeight; y += spacing) {
            canvas.drawRect(0f, y, sceneWidth, y + 1f, paint);
        }
    }
}
