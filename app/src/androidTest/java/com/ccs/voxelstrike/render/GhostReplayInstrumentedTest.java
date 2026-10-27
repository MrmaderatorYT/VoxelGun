package com.ccs.voxelstrike.render;

import android.content.Context;
import android.content.ContextWrapper;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES30;
import android.util.AtomicFile;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.ccs.voxelstrike.app.GhostReplayStore;
import com.ccs.voxelstrike.game.GhostPose;
import com.ccs.voxelstrike.game.MatchModifier;
import com.ccs.voxelstrike.game.MovementGhost;
import com.ccs.voxelstrike.map.CourseLayout;
import com.ccs.voxelstrike.math.Frustum;
import com.ccs.voxelstrike.math.Mat4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.UUID;

import static org.junit.Assert.*;

/** Android AtomicFile persistence and an actual ES3 draw/readback of the procedural replay. */
@RunWith(AndroidJUnit4.class)
public class GhostReplayInstrumentedTest {
    private File directory;
    private Context isolated;

    @Before public void isolateReplayFiles() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        directory = new File(target.getCacheDir(), "ghost-replay-test-" + UUID.randomUUID());
        assertTrue(directory.mkdir());
        isolated = new ContextWrapper(target) {
            @Override public File getFilesDir() { return directory; }
            @Override public Context getApplicationContext() { return this; }
        };
    }

    @After public void removeOnlyThisTestsReplayFiles() {
        if (directory != null) deleteOwned(directory);
    }

    @Test public void atomicStoreRoundTripsAndKeepsDifferentModifierRecordsSeparate() {
        GhostReplayStore store = new GhostReplayStore(isolated);
        MovementGhost standard = replay(1000), bhop = replay(700);
        assertTrue(store.save(0, standard));
        assertTrue(store.save(MatchModifier.BUNNY_HOP.bit, bhop));
        MovementGhost a = new MovementGhost(), b = new MovementGhost();
        assertTrue(store.load(0, a));
        assertTrue(store.load(MatchModifier.BUNNY_HOP.bit, b));
        assertEquals(1000, a.bestDurationMillis()); assertEquals(700, b.bestDurationMillis());
        assertEquals(2, a.sampleCount()); assertEquals(2, b.sampleCount());
        GhostPose expected = new GhostPose(), actual = new GhostPose();
        assertTrue(standard.sample(500, expected)); assertTrue(a.sample(500, actual));
        assertEquals(expected.x, actual.x, 0f); assertEquals(expected.y, actual.y, 0f);
        assertEquals(expected.z, actual.z, 0f); assertEquals(expected.yaw, actual.yaw, 0f);
        assertEquals(expected.crouching, actual.crouching);
        assertFalse(store.load(MatchModifier.DOUBLE_JUMP.bit, new MovementGhost()));
        assertFalse(store.save(0, new MovementGhost()));
        assertTrue("failed empty save must retain the valid best", store.load(0, a));
    }

    @Test public void wrongFormatCourseVersionOrModifierHeaderCannotOverwriteCurrentBest() throws Exception {
        GhostReplayStore store = new GhostReplayStore(isolated);
        MovementGhost target = replay(700);
        for (int offset : new int[]{4, 8, 12}) {
            assertTrue(store.save(0, replay(1000)));
            try (RandomAccessFile file = new RandomAccessFile(replayFile(0), "rw")) {
                file.seek(offset); file.writeInt(0x7FFFFFFF);
            }
            int revision = target.bestRevision();
            assertFalse(store.load(0, target));
            assertEquals(700, target.bestDurationMillis());
            assertEquals(revision, target.bestRevision());
        }
    }

    @Test public void interruptedAtomicWriteRetainsTheLastCompleteReplay() throws Exception {
        GhostReplayStore store = new GhostReplayStore(isolated);
        assertTrue(store.save(0, replay(1000)));
        AtomicFile atomic = new AtomicFile(replayFile(0));
        FileOutputStream unfinished = atomic.startWrite();
        try {
            unfinished.write(new byte[]{1, 2, 3});
            unfinished.flush(); unfinished.getFD().sync();
            // Simulate a process stopping without finishWrite/failWrite. openRead must recover
            // the old complete file rather than exposing the partially written new file.
        } finally { unfinished.close(); }
        MovementGhost recovered = new MovementGhost();
        assertTrue(store.load(0, recovered));
        assertEquals(1000, recovered.bestDurationMillis());
        assertEquals(2, recovered.sampleCount());
    }

    @Test public void proceduralGhostShaderReallyDrawsStandingAndCrouchedRunnerInEs3() {
        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        assertNotEquals(EGL14.EGL_NO_DISPLAY, display);
        assertTrue(EGL14.eglInitialize(display, new int[2], 0, new int[2], 0));
        EGLConfig[] configurations = new EGLConfig[1];
        int[] count = new int[1];
        assertTrue(EGL14.eglChooseConfig(display, new int[]{EGL14.EGL_RENDERABLE_TYPE, 0x40,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 24, EGL14.EGL_NONE}, 0, configurations, 0, 1, count, 0));
        assertTrue(count[0] > 0);
        EGLContext eglContext = EGL14.eglCreateContext(display, configurations[0], EGL14.EGL_NO_CONTEXT,
                new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE}, 0);
        EGLSurface surface = EGL14.eglCreatePbufferSurface(display, configurations[0],
                new int[]{EGL14.EGL_WIDTH, 128, EGL14.EGL_HEIGHT, 128, EGL14.EGL_NONE}, 0);
        ShaderLibrary shaders = null;
        GhostRenderer runner = new GhostRenderer();
        try {
            assertNotEquals(EGL14.EGL_NO_CONTEXT, eglContext);
            assertNotEquals(EGL14.EGL_NO_SURFACE, surface);
            assertTrue(EGL14.eglMakeCurrent(display, surface, surface, eglContext));
            shaders = new ShaderLibrary(isolated.getAssets());
            runner.create(shaders); // Compiles/links the shipped shaders and uploads the voxel mesh.
            GLES30.glViewport(0, 0, 128, 128);
            GLES30.glDisable(GLES30.GL_CULL_FACE);
            GLES30.glEnable(GLES30.GL_DEPTH_TEST); GLES30.glDepthFunc(GLES30.GL_LEQUAL);
            GLES30.glEnable(GLES30.GL_BLEND);
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
            float[] projection = new float[16];
            Mat4.ortho(projection, -1.5f, 1.5f, -.2f, 2f, .1f, 10f);
            Frustum frustum = new Frustum(); frustum.update(projection);
            GhostPose pose = new GhostPose(); pose.visible = true; pose.z = -4f;
            ByteBuffer pixels = ByteBuffer.allocateDirect(128 * 128 * 4);
            int standingPixels = drawAndCount(runner, pose, projection, frustum, pixels);
            assertTrue("draw produced no cyan runner pixels", standingPixels > 100);
            pose.crouching = true;
            int crouchedPixels = drawAndCount(runner, pose, projection, frustum, pixels);
            assertTrue(crouchedPixels > 50);
            assertTrue("crouching should shrink the runner while preserving its feet", crouchedPixels < standingPixels);
            pose.visible = false;
            assertEquals(0, drawAndCount(runner, pose, projection, frustum, pixels));
            pose.visible = true; pose.x = 100;
            assertEquals("frustum must reject an off-screen replay", 0,
                    drawAndCount(runner, pose, projection, frustum, pixels));
            assertEquals(GLES30.GL_NO_ERROR, GLES30.glGetError());
        } finally {
            runner.release();
            if (shaders != null) shaders.release();
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface);
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext);
            EGL14.eglTerminate(display);
        }
    }

    private static int drawAndCount(GhostRenderer runner, GhostPose pose, float[] projection,
                                    Frustum frustum, ByteBuffer pixels) {
        GLES30.glClearColor(0, 0, 0, 1);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
        runner.draw(pose, projection, frustum);
        GLES30.glFinish();
        pixels.clear();
        GLES30.glReadPixels(0, 0, 128, 128, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels);
        assertEquals("ghost mesh draw/readback issued an invalid GL call", GLES30.GL_NO_ERROR,
                GLES30.glGetError());
        int visible = 0;
        for (int i = 0; i < 128 * 128; i++) {
            int red = pixels.get(i * 4) & 255, green = pixels.get(i * 4 + 1) & 255;
            int blue = pixels.get(i * 4 + 2) & 255;
            if (green > 8 && blue > 8 && green > red) visible++;
        }
        return visible;
    }

    private static MovementGhost replay(int duration) {
        MovementGhost ghost = new MovementGhost();
        ghost.record(0, 14, 3, 32, .2f, false, true);
        ghost.record(duration, 114, 3.5f, 32, .6f, true, true);
        assertTrue(ghost.completeRun(duration)); return ghost;
    }

    private File replayFile(int modifiers) {
        return new File(new File(directory, "movement_ghosts"), "course_" + CourseLayout.VERSION
                + "_rules_" + Integer.toHexString(modifiers) + ".ghost");
    }

    private void deleteOwned(File file) {
        assertTrue("cleanup target left the isolated test directory",
                file.equals(directory) || file.getAbsolutePath().startsWith(directory.getAbsolutePath() + File.separator));
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteOwned(child);
        assertTrue("could not remove isolated test file " + file, !file.exists() || file.delete());
    }
}
