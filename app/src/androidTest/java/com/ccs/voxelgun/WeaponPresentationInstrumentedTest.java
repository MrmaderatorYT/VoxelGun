package com.ccs.voxelgun;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES30;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.ccs.voxelgun.app.Cosmetics;
import com.ccs.voxelgun.game.Combatant;
import com.ccs.voxelgun.game.Match;
import com.ccs.voxelgun.game.MatchConfig;
import com.ccs.voxelgun.input.InputState;
import com.ccs.voxelgun.render.Framebuffer;
import com.ccs.voxelgun.render.GameRenderer;
import com.ccs.voxelgun.render.RenderSettings;
import com.ccs.voxelgun.ui.CosmeticPreview;
import com.ccs.voxelgun.ui.CosmeticTurntable;
import com.ccs.voxelgun.weapon.WeaponType;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real-driver weapon presentation captures; all match and cosmetic state remains in memory. */
@RunWith(AndroidJUnit4.class)
public class WeaponPresentationInstrumentedTest {
    private static final String TAG = "VoxelWeaponImage";
    private static final int WIDTH = 960, HEIGHT = 432;

    @Test public void captureTurntablePresentations() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        String label = InstrumentationRegistry.getArguments().getString("label", "before")
                .replaceAll("[^A-Za-z0-9._-]", "_");
        if (label.isEmpty()) label = "before";
        File external = context.getExternalFilesDir(null);
        assertNotNull("External app files directory", external);
        File output = new File(external, "weapon-checks");
        assertTrue("Cannot create " + output, output.isDirectory() || output.mkdirs());
        CosmeticTurntable[] holder = new CosmeticTurntable[1];
        instrumentation.runOnMainSync(() -> {
            holder[0] = new CosmeticTurntable(context);
            holder[0].measure(View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY));
            holder[0].layout(0, 0, 640, 400);
        });
        CosmeticTurntable turntable = holder[0];
        for (WeaponType weapon : new WeaponType[]{WeaponType.FAMAS_B, WeaponType.AK15}) {
            for (int skin : new int[]{0, 1}) {
                String prefix = label + "-turntable-" + weapon.name().toLowerCase(Locale.ROOT)
                        + (skin == 0 ? "-factory" : "-mint");
                for (int yaw : new int[]{45, 135, 225, 315}) {
                    captureTurntable(turntable, cosmetic(Cosmetics.Category.SKIN, skin), weapon,
                            yaw, -.45f, output, prefix + "-yaw" + yaw, true);
                }
                captureTurntable(turntable, cosmetic(Cosmetics.Category.SKIN, skin), weapon,
                        135, .65f, output, prefix + "-underside", true);
            }
        }
        // Reuse the same View so these also catch stale geometry/cache after item switches.
        captureTurntable(turntable, null, WeaponType.AK15, 45, -.45f,
                output, label + "-turntable-null", false);
        captureTurntable(turntable, cosmetic(Cosmetics.Category.CHARM, 0), null, 45, -.45f,
                output, label + "-turntable-no-charm", false);
        captureTurntable(turntable, cosmetic(Cosmetics.Category.CROSSHAIR, 2), null, 45, -.45f,
                output, label + "-turntable-crosshair", true);
        captureTurntable(turntable, cosmetic(Cosmetics.Category.SKIN, 1), WeaponType.FAMAS_B,
                45, -.45f, output, label + "-turntable-weapon-after-crosshair", true);
    }

    @Test public void captureWeaponPresentations() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String label = InstrumentationRegistry.getArguments().getString("label", "before")
                .replaceAll("[^A-Za-z0-9._-]", "_");
        if (label.isEmpty()) label = "before";
        File external = context.getExternalFilesDir(null);
        assertNotNull("External app files directory", external);
        File output = new File(external, "weapon-checks");
        assertTrue("Cannot create " + output, output.isDirectory() || output.mkdirs());
        saveCatalog(output, label + "-catalog-factory", 0);
        saveCatalog(output, label + "-catalog-mint", 1);

        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        assertNotEquals(EGL14.EGL_NO_DISPLAY, display);
        int[] version = new int[2];
        assertTrue(EGL14.eglInitialize(display, version, 0, version, 1));
        EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
        EGLSurface surface = EGL14.EGL_NO_SURFACE;
        Match match = null;
        GameRenderer renderer = null;
        try {
            EGLConfig[] configs = new EGLConfig[1];
            int[] count = new int[1];
            assertTrue(EGL14.eglChooseConfig(display, new int[]{
                    EGL14.EGL_RENDERABLE_TYPE, 0x40,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_DEPTH_SIZE, 24, EGL14.EGL_NONE}, 0, configs, 0, 1, count, 0));
            assertTrue("An ES3 pbuffer config is required", count[0] > 0);
            eglContext = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                    new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE}, 0);
            assertNotEquals(EGL14.EGL_NO_CONTEXT, eglContext);
            surface = EGL14.eglCreatePbufferSurface(display, configs[0], new int[]{
                    EGL14.EGL_WIDTH, WIDTH, EGL14.EGL_HEIGHT, HEIGHT, EGL14.EGL_NONE}, 0);
            assertNotEquals(EGL14.EGL_NO_SURFACE, surface);
            assertTrue(EGL14.eglMakeCurrent(display, surface, surface, eglContext));
            Log.i(TAG, "GPU=" + GLES30.glGetString(GLES30.GL_RENDERER));

            MatchConfig config = new MatchConfig();
            config.mapIndex = 17;
            config.botCount = 1;
            config.seed = 4242L;
            match = new Match(config);
            match.player().position.set(64f, 6f - Combatant.EYE_HEIGHT, 90f);
            match.player().yaw = 0f;
            match.player().pitch = -.10f;
            match.cameraPosition.set(64f, 6f, 90f);
            match.cameraYaw = 0f;
            match.cameraPitch = -.10f;
            InputState input = new InputState();
            input.paused = true;
            RenderSettings settings = new RenderSettings();
            settings.applyPreset(RenderSettings.Quality.MEDIUM);
            settings.disableTracing();
            settings.temporalAntiAliasing = false;
            settings.bloom = false;
            settings.adaptivePerformance = false;
            settings.renderScale = 1f;
            settings.gamma = 1f;
            settings.frameRateCap = 0;
            renderer = new GameRenderer(context, match, input, settings);
            renderer.onSurfaceCreated(null, null);
            renderer.onSurfaceChanged(null, WIDTH, HEIGHT);
            assertNoGlErrors("initialize");
            long deadline = SystemClock.uptimeMillis() + 60000;
            while (!renderer.ready() && SystemClock.uptimeMillis() < deadline) {
                renderer.onDrawFrame(null);
                SystemClock.sleep(5);
            }
            assertTrue("Chunks must finish loading", renderer.ready());

            for (WeaponType weapon : new WeaponType[]{WeaponType.AK15, WeaponType.P9,
                    WeaponType.FAMAS_B, WeaponType.AWM_X, WeaponType.PUMPER, WeaponType.KNIFE}) {
                capture(renderer, match, weapon, 0, false, false, output,
                        label + "-" + weapon.name().toLowerCase(Locale.ROOT) + "-factory");
            }
            capture(renderer, match, WeaponType.AK15, 0, true, false, output, label + "-ak15-ads");
            capture(renderer, match, WeaponType.AK15, 0, false, true, output, label + "-ak15-reload");
            capture(renderer, match, WeaponType.AK15, 1, false, false, output, label + "-ak15-mint");
            capture(renderer, match, WeaponType.AK15, 5, false, false, output, label + "-ak15-neon");
        } finally {
            try {
                if (renderer != null) {
                    Framebuffer.bindDefault(WIDTH, HEIGHT);
                    renderer.release();
                    assertNoGlErrors("release");
                }
            } finally {
                if (match != null) match.release();
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_CONTEXT);
                if (!EGL14.EGL_NO_SURFACE.equals(surface)) EGL14.eglDestroySurface(display, surface);
                if (!EGL14.EGL_NO_CONTEXT.equals(eglContext)) EGL14.eglDestroyContext(display, eglContext);
                EGL14.eglTerminate(display);
            }
        }
    }

    private static void capture(GameRenderer renderer, Match match, WeaponType type, int skin,
                                boolean ads, boolean reload, File output, String label)
            throws Exception {
        Combatant player = match.player();
        player.setSingleWeapon(type);
        player.weaponSkins = new int[WeaponType.values().length];
        player.weaponSkins[type.ordinal()] = skin;
        player.switchTimer = 0f;
        player.aiming = ads;
        player.crouchBlend = player.viewBob = player.recoilPitch = player.recoilYaw = 0f;
        player.meleeSwing = 0f;
        player.gloveColor = 0;
        player.charm = 0;
        match.cameraFovScale = ads ? 1f / type.zoom() : 1f;
        match.scopeBlend = 0f;
        if (reload) {
            player.weapon().consume();
            assertTrue("Reload must start", player.weapon().startReload());
            player.weapon().update(type.reloadSeconds() * .5f);
            assertEquals(.5f, player.weapon().reloadProgress(), .0001f);
        }
        // Let camera-side interpolation settle without advancing combat or weapon timers.
        for (int frame = 0; frame < 20; frame++) renderer.onDrawFrame(null);
        GLES30.glFinish();
        assertNoGlErrors(label + " draw");
        Framebuffer.bindDefault(WIDTH, HEIGHT);
        GLES30.glReadBuffer(GLES30.GL_BACK);
        saveRgba(output, label, readRgba(WIDTH, HEIGHT), WIDTH, HEIGHT, false);
        Field field = GameRenderer.class.getDeclaredField("gbuffer");
        field.setAccessible(true);
        Framebuffer gbuffer = (Framebuffer) field.get(renderer);
        gbuffer.bind();
        GLES30.glReadBuffer(GLES30.GL_COLOR_ATTACHMENT0);
        saveRgba(output, label + "-mask", readRgba(gbuffer.width(), gbuffer.height()),
                gbuffer.width(), gbuffer.height(), true);
        Framebuffer.bindDefault(WIDTH, HEIGHT);
        Log.i(TAG, "capture=" + label + " weapon=" + type + " skin=" + skin
                + " ads=" + ads + " reload_progress=" + player.weapon().reloadProgress());
    }

    private static ByteBuffer readRgba(int width, int height) {
        ByteBuffer rgba = ByteBuffer.allocateDirect(width * height * 4);
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, rgba);
        assertNoGlErrors("readback " + width + "x" + height);
        return rgba;
    }

    private static void saveRgba(File output, String label, ByteBuffer rgba, int width,
                                 int height, boolean mask) throws Exception {
        int[] pixels = new int[width * height];
        int visible = 0, interior = 0, minX = width, minY = height, maxX = -1, maxY = -1;
        for (int y = 0; y < height; y++) {
            int topY = height - 1 - y;
            for (int x = 0; x < width; x++) {
                int source = (y * width + x) * 4;
                int color = 0xff000000;
                if (mask) {
                    int alpha = rgba.get(source + 3) & 255;
                    // Both the original flat tag and AO-preserving rig tags occupy (0.08,0.12).
                    float tag = alpha / 255f;
                    if (tag > .08f && tag < .12f) {
                        color = 0xffffffff;
                        visible++;
                        if (x > 1 && x < width - 2 && topY > 1 && topY < height - 2) interior++;
                        minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                        minY = Math.min(minY, topY); maxY = Math.max(maxY, topY);
                    }
                } else {
                    color |= (rgba.get(source) & 255) << 16
                            | (rgba.get(source + 1) & 255) << 8 | rgba.get(source + 2) & 255;
                }
                pixels[topY * width + x] = color;
            }
        }
        Bitmap image = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        savePng(image, new File(output, label + ".png"));
        if (mask) {
            Log.i(TAG, label + " rig_pixels=" + visible + " interior_pixels=" + interior
                    + " bounds=" + minX + "," + minY + "-" + maxX + "," + maxY
                    + " touches_edges=" + (minX == 0 || minY == 0 || maxX == width - 1 || maxY == height - 1));
            // Hands may intentionally leave the bottom edge. Save exact bounds for visual
            // clipping review; only an absent or entirely edge-clipped rig fails this smoke test.
            assertTrue(label + " first-person rig must be visible inside the frame", interior > 16);
        }
    }

    private static void saveCatalog(File output, String label, int skin) throws Exception {
        WeaponType[] weapons = WeaponType.values();
        int columns = 4, cellWidth = 300, cellHeight = 200;
        int rows = (weapons.length + columns - 1) / columns;
        Bitmap sheet = Bitmap.createBitmap(columns * cellWidth, rows * cellHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sheet);
        canvas.drawColor(0xffe9edf0);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(19f);
        try {
            for (int i = 0; i < weapons.length; i++) {
                int x = (i % columns) * cellWidth, y = (i / columns) * cellHeight;
                paint.setColor((i / columns + i % columns) % 2 == 0 ? 0xffe9edf0 : 0xffdbe2e7);
                canvas.drawRect(x, y, x + cellWidth, y + cellHeight, paint);
                Bitmap preview = CosmeticPreview.renderWeapon(weapons[i], skin, cellWidth - 20, cellHeight - 40);
                try {
                    int[] pixels = new int[preview.getWidth() * preview.getHeight()];
                    preview.getPixels(pixels, 0, preview.getWidth(), 0, 0, preview.getWidth(), preview.getHeight());
                    int opaque = 0;
                    for (int pixel : pixels) if ((pixel >>> 24) != 0) opaque++;
                    assertTrue(weapons[i] + " catalog preview must contain geometry", opaque > 16);
                    canvas.drawBitmap(preview, x + 10, y + 5, null);
                } finally { preview.recycle(); }
                paint.setColor(0xff16232d);
                canvas.drawText(weapons[i].displayName(), x + 12, y + cellHeight - 14, paint);
            }
            try (FileOutputStream stream = new FileOutputStream(new File(output, label + ".png"))) {
                assertTrue(sheet.compress(Bitmap.CompressFormat.PNG, 100, stream));
            }
        } finally { sheet.recycle(); }
        Log.i(TAG, "catalog=" + label + " weapons=" + weapons.length + " skin=" + skin);
    }

    private static Cosmetics.Item cosmetic(Cosmetics.Category category, int value) {
        for (Cosmetics.Item item : Cosmetics.ITEMS) {
            if (item.category() == category && item.value() == value) return item;
        }
        throw new AssertionError("Missing cosmetic " + category + ":" + value);
    }

    private static void captureTurntable(CosmeticTurntable view, Cosmetics.Item item,
                                         WeaponType weapon, int yawDegrees, float pitch,
                                         File output, String label, boolean expectedVisible)
            throws Exception {
        Bitmap[] result = new Bitmap[1];
        Throwable[] failure = new Throwable[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                view.setItem(item, weapon);
                // These fields exist in the installed baseline too. Stop idle animation without
                // depending on new model metadata or the turntable's cache implementation.
                Field yaw = CosmeticTurntable.class.getDeclaredField("yaw");
                Field tilt = CosmeticTurntable.class.getDeclaredField("pitch");
                Field dragging = CosmeticTurntable.class.getDeclaredField("dragging");
                yaw.setAccessible(true); tilt.setAccessible(true); dragging.setAccessible(true);
                yaw.setFloat(view, (float) Math.toRadians(yawDegrees));
                tilt.setFloat(view, pitch);
                dragging.setBoolean(view, true);
                result[0] = Bitmap.createBitmap(640, 400, Bitmap.Config.ARGB_8888);
                // A fresh bitmap starts transparent; do not paint a background behind the View.
                view.draw(new Canvas(result[0]));
            } catch (Throwable error) { failure[0] = error; }
        });
        if (failure[0] != null) {
            if (result[0] != null) result[0].recycle();
            throw new AssertionError("Turntable draw " + label, failure[0]);
        }
        Bitmap image = result[0];
        assertNotNull("Turntable image " + label, image);
        try {
            int width = image.getWidth(), height = image.getHeight();
            int[] pixels = new int[width * height];
            image.getPixels(pixels, 0, width, 0, 0, width, height);
            int visible = 0, edge = 0, minX = width, minY = height, maxX = -1, maxY = -1;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if ((pixels[y * width + x] >>> 24) == 0) continue;
                    visible++;
                    if (x == 0 || y == 0 || x == width - 1 || y == height - 1) edge++;
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                }
            }
            try (FileOutputStream stream = new FileOutputStream(new File(output, label + ".png"))) {
                assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, stream));
            }
            Log.i(TAG, label + " visible_pixels=" + visible + " edge_pixels=" + edge
                    + " bounds=" + minX + "," + minY + "-" + maxX + "," + maxY
                    + " yaw=" + yawDegrees + " pitch=" + pitch);
            if (expectedVisible) assertTrue(label + " must contain visible geometry", visible > 16);
            else assertEquals(label + " must clear the previous item", 0, visible);
            assertEquals(label + " must fit inside a transparent margin", 0, edge);
        } finally { image.recycle(); }
    }

    private static void savePng(Bitmap image, File file) throws Exception {
        try (FileOutputStream stream = new FileOutputStream(file)) {
            assertTrue("PNG compression " + file, image.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } finally { image.recycle(); }
    }

    private static void assertNoGlErrors(String stage) {
        StringBuilder errors = new StringBuilder();
        for (int error = GLES30.glGetError(); error != GLES30.GL_NO_ERROR; error = GLES30.glGetError()) {
            if (errors.length() > 0) errors.append(", ");
            errors.append("0x").append(Integer.toHexString(error));
        }
        Log.i(TAG, stage + " gl_errors=" + (errors.length() == 0 ? "none" : errors));
        assertEquals(stage + " GLES errors", "", errors.toString());
    }
}
