package com.ccs.voxelstrike;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES30;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.ccs.voxelstrike.game.Combatant;
import com.ccs.voxelstrike.game.Match;
import com.ccs.voxelstrike.game.MatchConfig;
import com.ccs.voxelstrike.input.InputState;
import com.ccs.voxelstrike.render.Framebuffer;
import com.ccs.voxelstrike.render.GameRenderer;
import com.ccs.voxelstrike.render.RenderSettings;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Fixed-camera mobile-driver captures. No Activity, profile or persisted settings are used. */
@RunWith(AndroidJUnit4.class)
public class GraphicsImageInstrumentedTest {
    private static final String TAG = "VoxelGraphicsImage";
    private static final int WIDTH = 960;
    private static final int HEIGHT = 432;
    private static final int HISTORY_FRAMES = 32;

    @Test public void captureFixedCameraGraphicsVariants() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String label = InstrumentationRegistry.getArguments().getString("label", "before")
                .replaceAll("[^A-Za-z0-9._-]", "_");
        if (label.isEmpty()) label = "before";
        boolean motion = Boolean.parseBoolean(
                InstrumentationRegistry.getArguments().getString("motion", "false"));
        File external = context.getExternalFilesDir(null);
        assertNotNull("External app files directory must be available", external);
        File output = new File(external, "graphics-checks");
        assertTrue("Cannot create " + output, output.isDirectory() || output.mkdirs());

        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        assertNotEquals(EGL14.EGL_NO_DISPLAY, display);
        int[] version = new int[2];
        assertTrue("eglInitialize", EGL14.eglInitialize(display, version, 0, version, 1));
        EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
        EGLSurface surface = EGL14.EGL_NO_SURFACE;
        Match match = null;
        try {
            EGLConfig[] configs = new EGLConfig[1];
            int[] count = new int[1];
            assertTrue("eglChooseConfig", EGL14.eglChooseConfig(display, new int[]{
                    EGL14.EGL_RENDERABLE_TYPE, 0x40,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_DEPTH_SIZE, 24, EGL14.EGL_NONE}, 0, configs, 0, 1, count, 0));
            assertTrue("An ES3 pbuffer config is required", count[0] > 0);
            eglContext = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                    new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE}, 0);
            assertNotEquals("eglCreateContext", EGL14.EGL_NO_CONTEXT, eglContext);
            surface = EGL14.eglCreatePbufferSurface(display, configs[0], new int[]{
                    EGL14.EGL_WIDTH, WIDTH, EGL14.EGL_HEIGHT, HEIGHT, EGL14.EGL_NONE}, 0);
            assertNotEquals("eglCreatePbufferSurface", EGL14.EGL_NO_SURFACE, surface);
            assertTrue("eglMakeCurrent", EGL14.eglMakeCurrent(display, surface, surface, eglContext));
            Log.i(TAG, "GPU=" + GLES30.glGetString(GLES30.GL_RENDERER)
                    + " GL=" + GLES30.glGetString(GLES30.GL_VERSION));

            MatchConfig config = new MatchConfig();
            config.mapIndex = 17;
            config.botCount = 1;
            config.seed = 4242L;
            match = new Match(config);
            // Flooded Plaza: stand in the central lane looking toward the fountain. Keep both
            // the player and the render camera fixed, including during the paused heartbeat.
            match.player().position.set(64f, 6f - Combatant.EYE_HEIGHT, 90f);
            match.player().yaw = 0f;
            match.player().pitch = -.10f;
            match.cameraPosition.set(64f, 6f, 90f);
            match.cameraYaw = 0f;
            match.cameraPitch = -.10f;
            Log.i(TAG, "map=" + match.map().id()
                    + " seed=4242 camera=64,6,90 yaw=0 pitch=-0.10");

            for (String variant : new String[]{"ultra", "shadows", "raster"}) {
                capture(context, match, settings(variant), output, label + "-" + variant,
                        motion && "ultra".equals(variant));
            }
            assertNoGlErrors("all captures complete");
        } finally {
            if (match != null) match.release();
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT);
            if (!EGL14.EGL_NO_SURFACE.equals(surface)) EGL14.eglDestroySurface(display, surface);
            if (!EGL14.EGL_NO_CONTEXT.equals(eglContext)) EGL14.eglDestroyContext(display, eglContext);
            EGL14.eglTerminate(display);
        }
    }

    private static RenderSettings settings(String variant) {
        RenderSettings settings = new RenderSettings();
        settings.applyPreset(RenderSettings.Quality.ULTRA);
        settings.temporalAntiAliasing = true;
        settings.bloom = false;
        settings.gamma = 1f;
        settings.adaptivePerformance = false;
        settings.renderScale = 1f;
        settings.traceScale = .5f;
        settings.frameRateCap = 0;
        if ("shadows".equals(variant)) {
            settings.rtGlobalIllumination = false;
            settings.rtReflections = false;
        } else if ("raster".equals(variant)) {
            settings.disableTracing();
        }
        settings.normalize();
        return settings;
    }

    private static void capture(Context context, Match match, RenderSettings settings,
                                File output, String label, boolean motion) throws Exception {
        InputState input = new InputState();
        input.paused = true;
        boolean expectedTracing = settings.tracingActive();
        GameRenderer renderer = new GameRenderer(context, match, input, settings);
        try {
            renderer.onSurfaceCreated(null, null);
            renderer.onSurfaceChanged(null, WIDTH, HEIGHT);
            assertEquals(label + " tracing unexpectedly fell back", expectedTracing,
                    renderer.settings().tracingActive());
            assertTrue(label + " TAA unexpectedly fell back", renderer.settings().temporalAntiAliasing);
            assertNoGlErrors(label + " initialize");

            long deadline = SystemClock.uptimeMillis() + 60000;
            while (!renderer.ready() && SystemClock.uptimeMillis() < deadline) {
                renderer.onDrawFrame(null);
                SystemClock.sleep(5);
            }
            assertTrue(label + " chunks must finish loading", renderer.ready());
            for (int frame = 0; frame < HISTORY_FRAMES; frame++) renderer.onDrawFrame(null);
            GLES30.glFinish();
            assertNoGlErrors(label + " rendered history");
            saveFinalFrame(output, label);

            if (expectedTracing) {
                Framebuffer[] trace = (Framebuffer[]) field(renderer, "trace");
                int nextTraceIndex = (Integer) field(renderer, "traceIndex");
                // renderTracing toggles this index after writing; the other buffer is current.
                saveTracedLight(trace[nextTraceIndex ^ 1], output, label);
            }
            if (motion) captureMotion(renderer, match, output, label);
            Log.i(TAG, "captured=" + new File(output, label + ".png")
                    + " history_frames=" + HISTORY_FRAMES + " tracing=" + expectedTracing
                    + " gi=" + settings.rtGlobalIllumination + " reflections=" + settings.rtReflections);
        } finally {
            Framebuffer.bindDefault(WIDTH, HEIGHT);
            renderer.release();
        }
        assertNoGlErrors(label + " release");
    }

    private static void captureMotion(GameRenderer renderer, Match match, File output,
                                      String label) throws Exception {
        float playerYaw = match.player().yaw;
        float cameraYaw = match.cameraYaw;
        try {
            // Combat stays paused; only the controlled camera motion invalidates history.
            for (int frame = 0; frame < 16; frame++) {
                float rotation = (frame + 1) * .025f;
                match.player().yaw = playerYaw + rotation;
                match.cameraYaw = cameraYaw + rotation;
                renderer.onDrawFrame(null);
            }
            GLES30.glFinish();
            assertNoGlErrors(label + " moving history");
            saveFinalFrame(output, label + "-moving");
            for (int frame = 0; frame < HISTORY_FRAMES; frame++) renderer.onDrawFrame(null);
            GLES30.glFinish();
            assertNoGlErrors(label + " settled history");
            saveFinalFrame(output, label + "-settled");
        } finally {
            // The following shadows/raster captures must retain the original fixed view.
            match.player().yaw = playerYaw;
            match.cameraYaw = cameraYaw;
        }
    }

    private static void saveFinalFrame(File output, String label) throws Exception {
        Framebuffer.bindDefault(WIDTH, HEIGHT);
        GLES30.glReadBuffer(GLES30.GL_BACK);
        ByteBuffer rgba = ByteBuffer.allocateDirect(WIDTH * HEIGHT * 4);
        GLES30.glReadPixels(0, 0, WIDTH, HEIGHT, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, rgba);
        assertNoGlErrors(label + " final readback");
        int[] pixels = new int[WIDTH * HEIGHT];
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int source = (y * WIDTH + x) * 4;
                pixels[(HEIGHT - 1 - y) * WIDTH + x] = 0xff000000
                        | (rgba.get(source) & 255) << 16
                        | (rgba.get(source + 1) & 255) << 8
                        | (rgba.get(source + 2) & 255);
            }
        }
        savePng(output, label, WIDTH, HEIGHT, pixels);
    }

    private static void saveTracedLight(Framebuffer trace, File output, String label)
            throws Exception {
        int width = trace.width(), height = trace.height();
        trace.bind();
        GLES30.glReadBuffer(GLES30.GL_COLOR_ATTACHMENT0);
        FloatBuffer rgba = ByteBuffer.allocateDirect(width * height * 16)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_FLOAT, rgba);
        assertNoGlErrors(label + " raw traced-light readback");
        int[] lighting = new int[width * height];
        int[] visibility = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int source = (y * width + x) * 4;
                int target = (height - 1 - y) * width + x;
                // RGB is pre-denoiser HDR radiance, visualized with x/(1+x) and gamma 2.2.
                lighting[target] = 0xff000000 | radianceByte(rgba.get(source)) << 16
                        | radianceByte(rgba.get(source + 1)) << 8 | radianceByte(rgba.get(source + 2));
                // Alpha is sun visibility. Preserve its linear [0,1] scale as opaque grey.
                int sun = Math.round(Math.max(0f, Math.min(1f, rgba.get(source + 3))) * 255f);
                visibility[target] = 0xff000000 | sun << 16 | sun << 8 | sun;
            }
        }
        savePng(output, label + "-rt-light", width, height, lighting);
        savePng(output, label + "-rt-alpha", width, height, visibility);
        Framebuffer.bindDefault(WIDTH, HEIGHT);
    }

    private static int radianceByte(float value) {
        float positive = Math.max(0f, value);
        return Math.round((float) Math.pow(positive / (1f + positive), 1f / 2.2f) * 255f);
    }

    private static void savePng(File directory, String label, int width, int height,
                                int[] pixels) throws Exception {
        Bitmap image = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        try (FileOutputStream stream = new FileOutputStream(new File(directory, label + ".png"))) {
            assertTrue("PNG compression failed for " + label,
                    image.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } finally {
            image.recycle();
        }
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void assertNoGlErrors(String stage) {
        StringBuilder errors = new StringBuilder();
        for (int error = GLES30.glGetError(); error != GLES30.GL_NO_ERROR;
             error = GLES30.glGetError()) {
            if (errors.length() > 0) errors.append(", ");
            errors.append("0x").append(Integer.toHexString(error));
        }
        Log.i(TAG, stage + " gl_errors=" + (errors.length() == 0 ? "none" : errors));
        assertEquals(stage + " GLES errors", "", errors.toString());
    }
}
