package com.ccs.voxelgun;

import android.opengl.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.ccs.voxelgun.render.GpuMesh;
import com.ccs.voxelgun.render.DecorationRenderer;
import com.ccs.voxelgun.render.Framebuffer;
import com.ccs.voxelgun.render.Shader;
import com.ccs.voxelgun.map.scenery.MapScenery;
import com.ccs.voxelgun.math.Frustum;
import com.ccs.voxelgun.render.VoxelGpuScene;
import com.ccs.voxelgun.render.ShaderLibrary;
import com.ccs.voxelgun.voxel.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real GLES 3 pbuffer: upload, edit, release and repeat with a new GL context. */
@RunWith(AndroidJUnit4.class)
public class RendererInstrumentedTest {
    @Test public void meshAndVolumeSurviveContextRecreation() {
        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] versions = new int[2];
        assertTrue(EGL14.eglInitialize(display, versions, 0, versions, 1));
        EGLConfig[] configs = new EGLConfig[1]; int[] count = new int[1];
        int[] attributes = {EGL14.EGL_RENDERABLE_TYPE, 0x40, EGL14.EGL_SURFACE_TYPE,
                EGL14.EGL_PBUFFER_BIT, EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_NONE};
        assertTrue(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0));
        assertTrue(count[0] > 0);
        try {
            for (int iteration = 0; iteration < 2; iteration++) {
                EGLContext context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                        new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE}, 0);
                EGLSurface surface = EGL14.eglCreatePbufferSurface(display, configs[0],
                        new int[]{EGL14.EGL_WIDTH, 16, EGL14.EGL_HEIGHT, 16, EGL14.EGL_NONE}, 0);
                assertTrue(EGL14.eglMakeCurrent(display, surface, surface, context));
                ShaderLibrary shaders = new ShaderLibrary(InstrumentationRegistry.getInstrumentation()
                        .getTargetContext().getAssets());
                VoxelWorld world = new VoxelWorld(32, 16, 32);
                VoxelGpuScene scene = new VoxelGpuScene(); GpuMesh mesh = null;
                DecorationRenderer decoration = null; Framebuffer target = null;
                try {
                    // Compile/link on the real mobile driver, including the shared terrain textures.
                    shaders.create("test_composite", "fullscreen.vert", "composite.frag");
                    shaders.create("test_rt", "fullscreen.vert", "rt.frag");
                    shaders.create("test_reflections", "fullscreen.vert", "rt.frag", "REFLECTIONS");
                    world.set(15, 3, 12, Materials.CRATE); world.commit();
                    mesh = new GpuMesh(VoxelMesher.meshChunk(world, 0, 0, 0, VoxelMesher.PASS_OPAQUE));
                    scene.upload(world);
                    assertEquals(GLES30.GL_NO_ERROR, GLES30.glGetError());
                    // An unchanged world must not touch the GL texture binding on idle frames.
                    int[] sentinel = new int[1], bound = new int[1];
                    GLES30.glGenTextures(1, sentinel, 0);
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, sentinel[0]);
                    try {
                        assertFalse(scene.updateEdits(world));
                        GLES30.glGetIntegerv(GLES30.GL_TEXTURE_BINDING_3D, bound, 0);
                        assertEquals(sentinel[0], bound[0]);
                    } finally { GLES30.glDeleteTextures(1, sentinel, 0); }
                    assertTrue(world.removeDestructible(15, 3, 12));
                    assertTrue(scene.updateEdits(world));
                    assertEquals(GLES30.GL_NO_ERROR, GLES30.glGetError());
                    // Exercise the new render-only layer, including material encoding and culling.
                    MapScenery scenery = new MapScenery();
                    scenery.box(-.5f, -.5f, -.5f, 1, 1, 1, Materials.COPPER);
                    decoration = new DecorationRenderer(scenery);
                    Shader shader = shaders.create("test_decor", "gbuffer.vert", "gbuffer.frag");
                    target = new Framebuffer(2);
                    target.resize(16, 16, new int[]{GLES30.GL_RGBA8, GLES30.GL_RG8}, GLES30.GL_NEAREST, true);
                    target.bind();
                    GLES30.glClearColor(0, 0, 0, 0);
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
                    GLES30.glEnable(GLES30.GL_DEPTH_TEST); GLES30.glEnable(GLES30.GL_CULL_FACE);
                    float[] identity = new float[16]; Matrix.setIdentityM(identity, 0);
                    shader.use();
                    shader.setMatrix("uViewProjection", identity);
                    shader.setMatrix("uViewProjectionStable", identity);
                    shader.setMatrix("uPreviousViewProjection", identity);
                    Frustum frustum = new Frustum(); frustum.update(identity);
                    decoration.draw(shader, frustum, 0, 0, 0, 100);
                    var pixel = java.nio.ByteBuffer.allocateDirect(4);
                    GLES30.glReadBuffer(GLES30.GL_COLOR_ATTACHMENT0);
                    GLES30.glReadPixels(8, 8, 1, 1, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixel);
                    assertEquals(Materials.COPPER & 255, pixel.get(2) & 255);
                    assertTrue((pixel.get(3) & 255) > 230);
                    assertEquals(GLES30.GL_NO_ERROR, GLES30.glGetError());
                    // Adaptive quality removes motion vectors on this same FBO. The old second
                    // draw target must not survive the two-to-one attachment transition.
                    target.resize(16, 16, new int[]{GLES30.GL_RGBA8}, GLES30.GL_NEAREST, true);
                    target.bind();
                    assertEquals(GLES30.GL_FRAMEBUFFER_COMPLETE,
                            GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER));
                    decoration.draw(shader, frustum, 0, 0, 0, 100);
                    assertEquals(GLES30.GL_NO_ERROR, GLES30.glGetError());
                } finally {
                    shaders.release();
                    if (decoration != null) decoration.release();
                    if (target != null) target.release(true);
                    if (mesh != null) mesh.release(); scene.release(); world.release();
                    EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                    EGL14.eglDestroySurface(display, surface); EGL14.eglDestroyContext(display, context);
                }
            }
        } finally { EGL14.eglTerminate(display); }
    }
}
