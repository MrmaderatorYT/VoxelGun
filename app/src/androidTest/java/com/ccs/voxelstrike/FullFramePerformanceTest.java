package com.ccs.voxelstrike;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES30;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.ccs.voxelstrike.game.Match;
import com.ccs.voxelstrike.game.MatchConfig;
import com.ccs.voxelstrike.input.InputState;
import com.ccs.voxelstrike.render.GameRenderer;
import com.ccs.voxelstrike.render.RenderSettings;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Complete mobile GPU frames, not a CPU-only meshing benchmark. Does not save preferences. */
@RunWith(AndroidJUnit4.class)
public class FullFramePerformanceTest {
    @Test public void profileRasterFrame() throws Exception {
        var args = InstrumentationRegistry.getArguments();
        int width = Integer.parseInt(args.getString("width", "1920"));
        int height = Integer.parseInt(args.getString("height", "1200"));
        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        assertTrue(EGL14.eglInitialize(display, new int[2], 0, new int[2], 0));
        EGLConfig[] configs = new EGLConfig[1];
        int[] count = new int[1];
        assertTrue(EGL14.eglChooseConfig(display, new int[]{EGL14.EGL_RENDERABLE_TYPE, 0x40,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_DEPTH_SIZE, 24,
                EGL14.EGL_NONE}, 0, configs, 0, 1, count, 0));
        assertTrue(count[0] > 0);
        EGLContext context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE}, 0);
        EGLSurface surface = EGL14.eglCreatePbufferSurface(display, configs[0],
                new int[]{EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE}, 0);
        Match match = null;
        GameRenderer renderer = null;
        try {
            assertTrue(EGL14.eglMakeCurrent(display, surface, surface, context));
            MatchConfig config = new MatchConfig(); config.mapIndex = 1;
            config.botCount = 1; config.seed = 4242L;
            match = new Match(config);
            InputState input = new InputState(); input.paused = true;
            RenderSettings settings = new RenderSettings();
            settings.adaptivePerformance = false; settings.frameRateCap = 0;
            renderer = new GameRenderer(InstrumentationRegistry.getInstrumentation()
                    .getTargetContext(), match, input, settings);
            renderer.onSurfaceCreated(null, null);
            renderer.onSurfaceChanged(null, width, height);
            long deadline = System.nanoTime() + 60_000_000_000L;
            while (!renderer.ready() && System.nanoTime() < deadline) {
                renderer.onDrawFrame(null); Thread.sleep(5);
            }
            assertTrue("Chunks must finish loading", renderer.ready());
            for (int i = 0; i < 12; i++) { renderer.onDrawFrame(null); GLES30.glFinish(); }
            long[] frames = new long[30];
            for (int i = 0; i < frames.length; i++) {
                long start = System.nanoTime(); renderer.onDrawFrame(null); GLES30.glFinish();
                frames[i] = System.nanoTime() - start;
            }
            Arrays.sort(frames);
            Log.i("VoxelPerf", "full_frame map=Warehouse resolution=" + width + "x" + height + " gpu="
                    + GLES30.glGetString(GLES30.GL_RENDERER) + " median_ms="
                    + frames[frames.length / 2] / 1_000_000.0 + " p95_ms="
                    + frames[28] / 1_000_000.0);
            for (String name : new String[]{"renderGeometry", "renderComposite", "renderForward",
                    "renderTonemap"}) {
                Method method = GameRenderer.class.getDeclaredMethod(name); method.setAccessible(true);
                long[] samples = new long[15];
                for (int i = 0; i < samples.length; i++) {
                    GLES30.glFinish(); long start = System.nanoTime();
                    method.invoke(renderer); GLES30.glFinish(); samples[i] = System.nanoTime() - start;
                }
                Arrays.sort(samples);
                Log.i("VoxelPerf", name + " median_ms=" + samples[7] / 1_000_000.0);
            }
            assertEquals("Full frame must not issue invalid GL calls", GLES30.GL_NO_ERROR,
                    GLES30.glGetError());
        } finally {
            if (renderer != null) renderer.release();
            if (match != null) match.release();
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroySurface(display, surface); EGL14.eglDestroyContext(display, context);
            EGL14.eglTerminate(display);
        }
    }
}
