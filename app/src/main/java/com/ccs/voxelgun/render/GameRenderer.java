package com.ccs.voxelgun.render;

import android.content.Context;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;

import com.ccs.voxelgun.game.Combatant;
import com.ccs.voxelgun.game.Effects;
import com.ccs.voxelgun.game.Match;
import com.ccs.voxelgun.input.InputState;
import com.ccs.voxelgun.map.MapEnvironment;
import com.ccs.voxelgun.math.Frustum;
import com.ccs.voxelgun.math.Mat4;
import com.ccs.voxelgun.math.MathUtil;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * The hybrid renderer.
 *
 * <p>Primary visibility is rasterised into a compact G-buffer, then a ray tracing pass walks the
 * voxel volume for sun shadows, one or two diffuse bounces and (on Ultra) glossy reflections.
 * Results are accumulated temporally, filtered with an a-trous wavelet, resolved with a PBR
 * composite and finished with bloom and ACES tone mapping.
 */
public final class GameRenderer implements GLSurfaceView.Renderer {
    private static final float NEAR_PLANE = 0.045f;

    private final Context context;
    private final Match match;
    private final InputState input;
    private final RenderSettings settings;
    private final AdaptivePerformanceController adaptivePerformance =
            new AdaptivePerformanceController();
    private final AdaptiveWorkloadController adaptiveWorkload = new AdaptiveWorkloadController();
    private final com.ccs.voxelgun.app.ThermalMonitor thermalMonitor;
    private float configuredViewDistance;
    private final GhostRenderer ghostRenderer = new GhostRenderer();
    private final com.ccs.voxelgun.game.GhostPose ghostPose = new com.ccs.voxelgun.game.GhostPose();

    public void startThermalMonitoring() { thermalMonitor.start(); }
    public void stopThermalMonitoring() { thermalMonitor.stop(); }

    private ShaderLibrary library;
    private Shader gbufferShader;
    private Shader rtShader;
    private Shader rtReflectShader;
    private Shader atrousShader;
    private Shader compositeShader;
    private Shader rasterCompositeShader;
    private Shader glassShader;
    private Shader particleShader;
    private Shader tracerShader;
    private Shader decalShader;
    private Shader taaShader;
    private Shader bloomDownShader;
    private Shader bloomUpShader;
    private Shader tonemapShader;

    private final VoxelGpuScene scene = new VoxelGpuScene();
    private final ProceduralSurfaceNoise surfaceNoise = new ProceduralSurfaceNoise();
    private ChunkRenderer chunks;
    private DecorationRenderer decorations;
    private final EntityRenderer entities = new EntityRenderer();
    private FullscreenQuad quad;
    private InstancedQuads particlesAlpha;
    private InstancedQuads particlesAdditive;
    private InstancedQuads tracers;
    private InstancedQuads decals;

    private Framebuffer gbuffer;
    private Framebuffer hdr;
    private final Framebuffer[] trace = new Framebuffer[2];
    private final Framebuffer[] denoise = new Framebuffer[2];
    private final Framebuffer[] bloom = new Framebuffer[5];
    private final Framebuffer[] taa = new Framebuffer[2];

    private final float[] projection = Mat4.create();
    private final float[] view = Mat4.create();
    private final float[] viewProjection = Mat4.create();
    private final float[] inverseViewProjection = Mat4.create();
    /** Unjittered pair, used only for motion vectors so the jitter never enters reprojection. */
    private final float[] viewProjectionStable = Mat4.create();
    private final float[] previousViewProjection = Mat4.create();
    private final Frustum frustum = new Frustum();

    private int surfaceWidth = 1;
    private int surfaceHeight = 1;
    private int renderWidth = 1;
    private int renderHeight = 1;
    private int traceWidth = 1;
    private int traceHeight = 1;
    private int hdrFormat = GLES30.GL_RGBA16F;
    private boolean floatTargetsSupported;
    private volatile float displayRefreshRate = 60f;
    private int sampledTargetFps;

    public void setDisplayRefreshRate(float hz) { displayRefreshRate = hz; }
    /** Asks the view for a smaller surface buffer; see {@link #requestSurfaceSize}. */
    public interface SurfaceSizer {
        void requestSurfaceSize(int width, int height);
    }

    private SurfaceSizer surfaceSizer;
    private int displayWidth;
    private int displayHeight;
    private int requestedSurfaceWidth;
    private int requestedSurfaceHeight;
    private int traceIndex;
    private int taaIndex;
    private int frameIndex;
    private final FrameMetrics frameMetrics = new FrameMetrics();
    private boolean taaHistoryValid;
    private boolean resetTraceHistory;

    private long previousFrameNanos;
    private long nextFrameNanos;
    private float accumulator;
    private float simulationTime;
    private float fpsTimer;
    private int fpsFrames;
    private int fps;
    private boolean initialised;

    private float swayX;
    private float swayY;
    private float adsBlend;
    private float previousCameraYaw;
    private float previousCameraPitch;
    private float previousCameraX;
    private float previousCameraY;
    private float previousCameraZ;
    private float cameraMotion;

    public GameRenderer(Context context, Match match, InputState input, RenderSettings settings) {
        this.context = context.getApplicationContext();
        this.match = match;
        this.input = input;
        this.settings = settings;
        settings.normalize();
        thermalMonitor = new com.ccs.voxelgun.app.ThermalMonitor(context);
        configuredViewDistance = settings.viewDistance;
        adaptiveWorkload.reset(configuredViewDistance);
        adaptivePerformance.reset(settings.renderScale);
    }

    public RenderSettings settings() {
        return settings;
    }

    public float loadProgress() {
        return chunks == null ? 0f : chunks.progress();
    }

    public boolean ready() {
        return chunks != null && chunks.ready();
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // The context can be recreated after a surface loss; drop the old objects first.
        if (initialised) {
            release();
        }
        String extensions = GLES30.glGetString(GLES30.GL_EXTENSIONS);
        boolean fullFloat = extensions != null && extensions.contains("GL_EXT_color_buffer_float");
        boolean halfFloat = fullFloat || (extensions != null
                && extensions.contains("GL_EXT_color_buffer_half_float"));
        floatTargetsSupported = halfFloat
                && Framebuffer.supports(true, GLES30.GL_RGBA8, GLES30.GL_RG16F)
                && Framebuffer.supports(false, GLES30.GL_RGBA16F, GLES30.GL_R16F, GLES30.GL_RGBA16F);
        hdrFormat = fullFloat && Framebuffer.supports(GLES30.GL_R11F_G11F_B10F)
                ? GLES30.GL_R11F_G11F_B10F
                : floatTargetsSupported ? GLES30.GL_RGBA16F : GLES30.GL_RGBA8;
        settings.normalize();
        if (!floatTargetsSupported) {
            settings.disableTracing();
            settings.temporalAntiAliasing = false;
        }
        adaptivePerformance.reset(settings.renderScale);

        rtShader = rtReflectShader = atrousShader = compositeShader = null;
        taaShader = bloomDownShader = bloomUpShader = null;
        library = new ShaderLibrary(context.getAssets());
        gbufferShader = library.create("gbuffer", "gbuffer.vert", "gbuffer.frag");
        rasterCompositeShader = library.create("composite_raster", "fullscreen.vert",
                "composite.frag", "RASTER_ONLY", "CACHED_SURFACE_NOISE");
        glassShader = library.create("glass", "gbuffer.vert", "glass.frag");
        particleShader = library.create("particle", "particle.vert", "particle.frag");
        tracerShader = library.create("tracer", "tracer.vert", "tracer.frag");
        decalShader = library.create("decal", "decal.vert", "decal.frag");
        tonemapShader = library.create("tonemap", "fullscreen.vert", "tonemap.frag");

        scene.upload(match.world(), settings.tracingActive());
        surfaceNoise.create();
        if (!scene.tracingSupported()) {
            settings.disableTracing();
        }
        prepareOptionalShaders();
        if (match.world().sizeX() >= 640) {
            settings.viewDistance = Math.max(settings.viewDistance, 240f);
        }
        configuredViewDistance = settings.viewDistance;
        adaptiveWorkload.reset(configuredViewDistance);
        if (match.movementTrial() != null) ghostRenderer.create(library);
        chunks = new ChunkRenderer(match.world(), match.player().position.x, match.player().position.z);
        entities.create();
        decorations = new DecorationRenderer(match.map().scenery());
        quad = new FullscreenQuad();
        particlesAlpha = new InstancedQuads(Effects.MAX_PARTICLES);
        particlesAdditive = new InstancedQuads(Effects.MAX_PARTICLES);
        tracers = new InstancedQuads(Effects.MAX_TRACERS);
        decals = new InstancedQuads(Effects.MAX_DECALS);

        gbuffer = new Framebuffer(2);
        hdr = new Framebuffer(1);
        trace[0] = new Framebuffer(3);
        trace[1] = new Framebuffer(3);
        denoise[0] = new Framebuffer(1);
        denoise[1] = new Framebuffer(1);
        for (int i = 0; i < bloom.length; i++) {
            bloom[i] = new Framebuffer(1);
        }
        taa[0] = new Framebuffer(1);
        taa[1] = new Framebuffer(1);

        GLES30.glEnable(GLES30.GL_CULL_FACE);
        GLES30.glCullFace(GLES30.GL_BACK);
        GLES30.glDepthFunc(GLES30.GL_LEQUAL);
        previousFrameNanos = System.nanoTime();
        initialised = true;
    }

    /**
     * Tells the renderer how big the window really is and how to shrink its buffer.
     *
     * <p>Render scale used to shrink the offscreen passes only: the final tonemap still wrote
     * every pixel of a 2560x1600 panel, which on this class of GPU costs more than the lighting
     * does. Shrinking the surface itself hands that upscale to the display hardware, where it
     * is free.
     */
    public void attachSurfaceSizer(SurfaceSizer sizer, int width, int height) {
        surfaceSizer = sizer;
        displayWidth = Math.max(1, width);
        displayHeight = Math.max(1, height);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        surfaceWidth = Math.max(1, width);
        surfaceHeight = Math.max(1, height);
        if (displayWidth <= 1) {
            displayWidth = surfaceWidth;
            displayHeight = surfaceHeight;
        }
        allocateTargets();
    }

    private void prepareOptionalShaders() {
        if (settings.tracingActive()) {
            if (rtShader == null) rtShader = library.create("rt", "fullscreen.vert", "rt.frag");
            if (settings.rtReflections && rtReflectShader == null) {
                rtReflectShader = library.create("rt_reflect", "fullscreen.vert", "rt.frag", "REFLECTIONS");
            }
            if (settings.denoiser && atrousShader == null) {
                atrousShader = library.create("atrous", "fullscreen.vert", "atrous.frag");
            }
            if (compositeShader == null) {
                compositeShader = library.create("composite", "fullscreen.vert", "composite.frag",
                        "CACHED_SURFACE_NOISE");
            }
        }
        if (settings.temporalAntiAliasing && taaShader == null) {
            taaShader = library.create("taa", "fullscreen.vert", "taa.frag");
        }
        if (settings.bloom && bloomDownShader == null) {
            bloomDownShader = library.create("bloom_down", "fullscreen.vert", "bloom_down.frag");
            bloomUpShader = library.create("bloom_up", "fullscreen.vert", "bloom_up.frag");
        }
    }

    private void allocateTargets() {
        float renderScale = settings.adaptivePerformance
                ? adaptivePerformance.renderScale() : settings.renderScale;
        renderScale = RenderResolution.surfaceScale(displayWidth, displayHeight,
                settings.quality, renderScale);
        // Drive the window's own buffer down to the render resolution, so nothing anywhere in the
        // frame — including the pass that writes to the screen — pays for pixels being thrown
        // away. The request is asynchronous: this frame still uses whatever the surface is now.
        int desiredWidth = Math.max(1, (int) (displayWidth * renderScale));
        int desiredHeight = Math.max(1, (int) (displayHeight * renderScale));
        if (surfaceSizer != null && (desiredWidth != requestedSurfaceWidth
                || desiredHeight != requestedSurfaceHeight)) {
            requestedSurfaceWidth = desiredWidth;
            requestedSurfaceHeight = desiredHeight;
            surfaceSizer.requestSurfaceSize(desiredWidth, desiredHeight);
        }
        // Also bound the offscreen targets during asynchronous surface resizing and in tests
        // which do not attach a SurfaceSizer.
        renderWidth = Math.min(surfaceWidth, desiredWidth);
        renderHeight = Math.min(surfaceHeight, desiredHeight);
        float traceScale = RenderResolution.traceScale(renderWidth, renderHeight, settings.traceScale);
        traceWidth = Math.max(1, (int) (renderWidth * traceScale));
        traceHeight = Math.max(1, (int) (renderHeight * traceScale));
        boolean tracing = settings.tracingActive();
        boolean denoising = tracing && settings.denoiser && settings.denoisePasses > 0;

        // Second target carries the screen-space motion vector; it needs signed storage, so
        // without renderable half float there are no motion vectors and no TAA.
        int velocityFormat = hdrFormat != GLES30.GL_RGBA8 ? GLES30.GL_RG16F : GLES30.GL_RGBA8;
        // Motion vectors are read by temporal antialiasing and by the tracer's reprojection.
        // With both off they are four bytes per pixel written every frame for nothing.
        boolean motionVectors = settings.temporalAntiAliasing || tracing;
        gbuffer.resize(renderWidth, renderHeight, motionVectors
                        ? new int[]{GLES30.GL_RGBA8, velocityFormat}
                        : new int[]{GLES30.GL_RGBA8},
                GLES30.GL_NEAREST, true);
        hdr.resize(renderWidth, renderHeight, new int[]{hdrFormat}, GLES30.GL_LINEAR, false);


        // The trace buffer's second target carries linear depth and needs real precision, so it
        // stays half float wherever that is renderable at all.
        int[] traceFormats = settings.rtReflections
                ? new int[]{GLES30.GL_RGBA16F, GLES30.GL_R16F, GLES30.GL_RGBA16F}
                : new int[]{GLES30.GL_RGBA16F, GLES30.GL_R16F};
        for (Framebuffer buffer : trace) {
            if (tracing) {
                buffer.resize(traceWidth, traceHeight, traceFormats, GLES30.GL_LINEAR, false);
            } else {
                buffer.release(false);
            }
        }
        for (Framebuffer buffer : denoise) {
            if (denoising) {
                buffer.resize(traceWidth, traceHeight, new int[]{GLES30.GL_RGBA16F}, GLES30.GL_LINEAR,
                        false);
            } else {
                buffer.release(false);
            }
        }
        for (Framebuffer buffer : taa) {
            if (settings.temporalAntiAliasing) {
                buffer.resize(renderWidth, renderHeight, new int[]{hdrFormat}, GLES30.GL_LINEAR,
                        false);
            } else {
                buffer.release(false);
            }
        }
        taaHistoryValid = false;
        resetTraceHistory = true;

        int width = renderWidth;
        int height = renderHeight;
        for (Framebuffer buffer : bloom) {
            width = Math.max(1, width / 2);
            height = Math.max(1, height / 2);
            if (settings.bloom) {
                buffer.resize(width, height, new int[]{hdrFormat}, GLES30.GL_LINEAR, false);
            } else {
                buffer.release(false);
            }
        }

        // Freshly allocated attachments hold undefined data; the temporal pass would otherwise
        // reproject garbage for the first few frames.
        GLES30.glClearColor(0f, 0f, 0f, 0f);
        if (tracing) {
            for (Framebuffer buffer : trace) {
                buffer.bind();
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
            }
        }
        if (denoising) {
            for (Framebuffer buffer : denoise) {
                buffer.bind();
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
            }
        }
        if (settings.temporalAntiAliasing) {
            for (Framebuffer buffer : taa) {
                buffer.bind();
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
            }
        }
        Framebuffer.bindDefault(surfaceWidth, surfaceHeight);
    }

    /** Re-allocates render targets after the quality settings change. */
    public void onSettingsChanged() {
        if (initialised) {
            settings.normalize();
            if (!floatTargetsSupported || !scene.tracingSupported()) settings.disableTracing();
            if (!floatTargetsSupported) settings.temporalAntiAliasing = false;
            scene.setTracingEnabled(match.world(), settings.tracingActive());
            prepareOptionalShaders();
            adaptivePerformance.reset(settings.renderScale);
            if (match.world().sizeX() >= 640) {
                settings.viewDistance = Math.max(settings.viewDistance, 240f);
            }
            configuredViewDistance = settings.viewDistance;
            adaptiveWorkload.reset(configuredViewDistance);
            sampledTargetFps = 0;
            nextFrameNanos = 0;
            allocateTargets();
        }
    }

    /** Removes one expensive optional stage when resolution alone cannot meet the frame budget. */
    private boolean reduceAdaptiveQuality() {
        if (settings.rtReflections) {
            settings.rtReflections = false;
            return true;
        }
        if (settings.rtGlobalIllumination) {
            settings.rtGlobalIllumination = false;
            settings.giSamples = 0;
            settings.denoisePasses = Math.min(settings.denoisePasses, 2);
            return true;
        }
        if (settings.rtShadows) {
            settings.rtShadows = false;
            settings.rayTracing = false;
            settings.denoiser = false;
            settings.denoisePasses = 0;
            return true;
        }
        if (settings.bloom) {
            settings.bloom = false;
            return true;
        }
        if (settings.temporalAntiAliasing) {
            settings.temporalAntiAliasing = false;
            taaHistoryValid = false;
            return true;
        }
        return false;
    }

    /** Samples achieved cadence and adapts only the live match settings, never persisted choices. */
    private void updateAdaptivePerformance(float frameSeconds) {
        if (!settings.adaptivePerformance) {
            adaptivePerformance.pauseSampling();
            adaptiveWorkload.pauseSampling();
            settings.viewDistance = configuredViewDistance;
            return;
        }
        int targetFps = effectiveFrameCap();
        if (sampledTargetFps != targetFps) {
            adaptivePerformance.pauseSampling();
            adaptiveWorkload.pauseSampling();
            sampledTargetFps = targetFps;
        }
        // Thermal status must still be consumed while a menu covers the running renderer.
        int thermal = thermalMonitor.status();
        adaptiveWorkload.addFrame(input.paused ? 0f : frameSeconds, targetFps, thermal);
        settings.viewDistance = adaptiveWorkload.viewDistance();
        boolean thermalShed = thermal >= 3 && (settings.tracingActive() || settings.bloom);
        if (thermalShed) {
            settings.disableTracing();
            settings.bloom = false;
        }
        boolean resize = false;
        boolean reduceQuality = false;
        if (input.paused) {
            adaptivePerformance.pauseSampling();
            adaptiveWorkload.pauseSampling();
        } else {
            resize = adaptivePerformance.addFrame(frameSeconds, targetFps, thermal < 2);
            reduceQuality = adaptivePerformance.consumeQualityReductionRequest()
                    && reduceAdaptiveQuality();
        }
        if (resize || reduceQuality || thermalShed) {
            scene.setTracingEnabled(match.world(), settings.tracingActive());
            adaptivePerformance.pauseSampling();
            allocateTargets();
        }
    }

    /** Both pacing and quality adaptation chase a rate the current display can present. */
    private int effectiveFrameCap() {
        return FrameRatePolicy.resolve(settings.frameRateCap, displayRefreshRate,
                settings.adaptivePerformance ? adaptivePerformance.recommendedFrameCap() : 0,
                settings.adaptivePerformance && thermalMonitor.status() >= 3 ? 30 : 0,
                input.paused);
    }

    private void paceFrame() {
        int cap = effectiveFrameCap();
        if (cap <= 0) {
            nextFrameNanos = 0L;
            return;
        }
        long interval = 1_000_000_000L / cap;
        long now = System.nanoTime();
        if (nextFrameNanos > now) {
            long wait = nextFrameNanos - now;
            try {
                Thread.sleep(wait / 1_000_000L, (int) (wait % 1_000_000L));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
            now = System.nanoTime();
        }
        nextFrameNanos = Math.max(now, nextFrameNanos) + interval;
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        paceFrame();
        long now = System.nanoTime();
        float elapsedSeconds = (now - previousFrameNanos) * 1e-9f;
        float frameSeconds = Math.min(0.1f, elapsedSeconds);
        previousFrameNanos = now;
        simulationTime += frameSeconds;

        fpsFrames++;
        // Cadence uses real elapsed time; the physics clamp must not invent a 10-FPS floor.
        fpsTimer += elapsedSeconds;
        if (fpsTimer >= 0.5f) {
            fps = Math.round(fpsFrames / fpsTimer);
            fpsFrames = 0;
            fpsTimer = 0f;
            match.setFps(fps);
            match.setFrameP95(frameMetrics.p95Ms());
        }

        long sectionStart = System.nanoTime();
        chunks.uploadPending();
        if (settings.tracingActive() && scene.updateEdits(match.world())) {
            taaHistoryValid = false; resetTraceHistory = true;
        }
        match.metrics().record(com.ccs.voxelgun.performance.SectionMetrics.UPLOAD,
                System.nanoTime() - sectionStart);

        // Hold the simulation until the map is on the GPU, so nobody fights in an empty world.
        if (!chunks.ready()) {
            accumulator = 0f;
            renderLoadingFrame();
            return;
        }

        updateAdaptivePerformance(elapsedSeconds);
        if (!input.paused) frameMetrics.record(elapsedSeconds);

        // Fixed step simulation with a spiral-of-death guard.
        if (input.paused) {
            accumulator = 0f;
            // Menus freeze combat, but Match still needs a heartbeat to consume queued shop
            // purchases and publish the changed loadout back to the terminal UI.
            match.update(frameSeconds, input);
        } else {
            accumulator += frameSeconds;
            int steps = 0;
            while (accumulator >= Match.STEP && steps < 5) {
                match.update(Match.STEP, input);
                accumulator -= Match.STEP;
                steps++;
            }
            if (steps == 5 && accumulator >= Match.STEP) {
                // Discard only overdue whole ticks; keep the fractional remainder for pacing.
                accumulator %= Match.STEP;
            }
        }

        updateViewState(frameSeconds);
        buildMatrices();

        sectionStart = System.nanoTime();
        renderGeometry();
        if (settings.tracingActive()) {
            renderTracing();
            renderDenoise();
        }
        renderComposite();
        renderForward();
        renderTaa();
        renderBloom();
        renderTonemap();
        match.metrics().record(com.ccs.voxelgun.performance.SectionMetrics.RENDER_CPU,
                System.nanoTime() - sectionStart);
        match.setRenderTelemetry(chunks.averageMeshingMicros(), thermalMonitor.status(),
                settings.viewDistance, settings.adaptivePerformance ? adaptiveWorkload.effectFraction() : 1f);

        Mat4.copy(previousViewProjection, viewProjectionStable);
        frameIndex++;
    }

    /** Clears to black while the loading overlay is on top. */
    private void renderLoadingFrame() {
        Framebuffer.bindDefault(surfaceWidth, surfaceHeight);
        GLES30.glClearColor(0.04f, 0.04f, 0.05f, 1f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
    }

    private void updateViewState(float dt) {
        Combatant player = match.player();
        float targetAds = player.aiming ? 1f : 0f;
        adsBlend = MathUtil.damp(adsBlend, targetAds, 16f, Math.max(dt, 1e-4f));

        // Weapon lag follows the camera with a short delay.
        float deltaYaw = MathUtil.angleDelta(previousCameraYaw, match.cameraYaw);
        float deltaPitch = match.cameraPitch - previousCameraPitch;
        previousCameraYaw = match.cameraYaw;
        previousCameraPitch = match.cameraPitch;
        swayX = MathUtil.damp(swayX, MathUtil.clamp(-deltaYaw * 14f, -1.2f, 1.2f), 9f,
                Math.max(dt, 1e-4f));
        swayY = MathUtil.damp(swayY, MathUtil.clamp(-deltaPitch * 14f, -1.2f, 1.2f), 9f,
                Math.max(dt, 1e-4f));

        float dx = match.cameraPosition.x - previousCameraX;
        float dy = match.cameraPosition.y - previousCameraY;
        float dz = match.cameraPosition.z - previousCameraZ;
        cameraMotion = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) / Math.max(dt, 1e-4f);
        previousCameraX = match.cameraPosition.x;
        previousCameraY = match.cameraPosition.y;
        previousCameraZ = match.cameraPosition.z;
    }

    private void buildMatrices() {
        float aspect = (float) renderWidth / renderHeight;
        float fov = settings.fieldOfView * match.cameraFovScale;
        Mat4.perspective(projection, fov, aspect, NEAR_PLANE, settings.viewDistance);
        Mat4.lookAlong(view, match.cameraPosition.x, match.cameraPosition.y,
                match.cameraPosition.z, match.cameraYaw, match.cameraPitch);
        Mat4.multiply(viewProjectionStable, projection, view);

        if (settings.temporalAntiAliasing) {
            // Halton(2,3) sub-pixel offset, applied as a clip space shear on the third column.
            int sample = (frameIndex & 7) + 1;
            float jitterX = (halton(sample, 2) - 0.5f) * 2f / renderWidth;
            float jitterY = (halton(sample, 3) - 0.5f) * 2f / renderHeight;
            projection[8] += jitterX;
            projection[9] += jitterY;
        }
        Mat4.multiply(viewProjection, projection, view);
        Mat4.invert(inverseViewProjection, viewProjection);
        frustum.update(viewProjection);
        if (frameIndex == 0) {
            Mat4.copy(previousViewProjection, viewProjectionStable);
        }
    }

    /** Halton low discrepancy sequence, used for the temporal sub-pixel offsets. */
    private static float halton(int index, int base) {
        float result = 0f;
        float fraction = 1f;
        int cursor = index;
        while (cursor > 0) {
            fraction /= base;
            result += fraction * (cursor % base);
            cursor /= base;
        }
        return result;
    }

    // ------------------------------------------------------------------ passes

    private void renderGeometry() {
        gbuffer.bind();
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthMask(true);
        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glClearColor(0f, 0f, 0f, 0f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);

        gbufferShader.use();
        gbufferShader.setMatrix("uViewProjection", viewProjection);
        gbufferShader.setMatrix("uViewProjectionStable", viewProjectionStable);
        gbufferShader.setMatrix("uPreviousViewProjection", previousViewProjection);
        chunks.cull(frustum, match.cameraPosition.x, match.cameraPosition.y,
                match.cameraPosition.z, settings.viewDistance);
        chunks.drawOpaque(gbufferShader);
        decorations.draw(gbufferShader, frustum, match.cameraPosition.x, match.cameraPosition.y,
                match.cameraPosition.z, settings.viewDistance);
        entities.drawCombatants(gbufferShader, match.combatants(), match.player(), frustum);
        entities.drawProjectiles(gbufferShader, match.projectiles());
        entities.drawObjectives(gbufferShader, match);

        // The view model remains real G-buffer geometry so metal, skins and motion vectors use the
        // same pipeline as the world. A tag in the AO channel gives only this camera-side rig a
        // minimum fill light in the composite pass, keeping dark guns and the knife readable.
        Combatant player = match.player();
        boolean hideViewModel = !player.alive || match.scopeBlend > 0.55f;
        if (!hideViewModel) {
            gbufferShader.set("uViewModel", 1f);
            entities.drawViewModel(gbufferShader, player, match.cameraPosition.x,
                    match.cameraPosition.y, match.cameraPosition.z, match.cameraYaw,
                    match.cameraPitch, swayX, swayY, adsBlend, player.viewBob * 1.4f
                            + match.weaponJumpOffset * (1f - adsBlend),
                    player.recoilPitch * 3.2f);
            gbufferShader.set("uViewModel", 0f);
        } else {
            entities.invalidateViewHistory();
        }
    }

    private void renderTracing() {
        Framebuffer target = trace[traceIndex];
        Framebuffer history = trace[traceIndex ^ 1];
        target.bind();
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthMask(false);
        GLES30.glDisable(GLES30.GL_BLEND);

        Shader shader = settings.rtReflections ? rtReflectShader : rtShader;
        shader.use();
        applySceneUniforms(shader);
        shader.bindTexture("uSurface", 4, GLES30.GL_TEXTURE_2D, gbuffer.colorTexture(0));
        shader.bindTexture("uDepth", 5, GLES30.GL_TEXTURE_2D, gbuffer.depthTexture());
        shader.bindTexture("uVelocity", 6, GLES30.GL_TEXTURE_2D, gbuffer.colorTexture(1));
        shader.bindTexture("uHistoryLight", 7, GLES30.GL_TEXTURE_2D, history.colorTexture(0));
        shader.bindTexture("uHistoryDepth", 8, GLES30.GL_TEXTURE_2D, history.colorTexture(1));
        // Denoise runs after tracing, so its previous final buffer is still intact here.
        // Only diffuse RGB feeds back; rt.frag keeps unfiltered alpha for sun visibility.
        int historyIndirect = settings.denoiser && settings.denoisePasses > 0
                ? denoise[(settings.denoisePasses - 1) % 2].colorTexture(0)
                : history.colorTexture(0);
        shader.bindTexture("uHistoryIndirect", 10, GLES30.GL_TEXTURE_2D, historyIndirect);
        if (settings.rtReflections) {
            shader.bindTexture("uHistoryReflection", 9, GLES30.GL_TEXTURE_2D,
                    history.colorTexture(2));
        }
        GLES30.glUniform1ui(shader.uniform("uFrameIndex"), frameIndex);
        shader.set("uGiSamples", settings.rtGlobalIllumination ? settings.giSamples : 0);
        shader.set("uShadowSteps", settings.rtShadows ? settings.shadowSteps : 0);
        shader.set("uGiSteps", settings.giSteps);
        shader.set("uGiRange", settings.giRange);
        shader.set("uLocalLighting", settings.rtGlobalIllumination ? 1 : 0);
        // Fast camera motion invalidates history quickly; blend less to avoid smearing.
        float blend = MathUtil.clamp(0.93f - cameraMotion * 0.018f, 0.62f, 0.93f);
        shader.set("uHistoryBlend", resetTraceHistory ? 0f : blend);
        resetTraceHistory = false;
        quad.draw();
        traceIndex ^= 1;
    }

    private void renderDenoise() {
        if (settings.denoisePasses <= 0 || !settings.denoiser) {
            return;
        }
        Framebuffer source = trace[traceIndex ^ 1];
        atrousShader.use();
        scene.bindPalette(atrousShader, 0);
        atrousShader.bindTexture("uLinearDepth", 1, GLES30.GL_TEXTURE_2D, source.colorTexture(1));
        atrousShader.bindTexture("uSurface", 2, GLES30.GL_TEXTURE_2D, gbuffer.colorTexture(0));
        atrousShader.set("uTexelSize", 1f / traceWidth, 1f / traceHeight);
        atrousShader.set("uDepthSigma", 0.08f);
        atrousShader.set("uNormalSigma", 24f);

        int input = source.colorTexture(0);
        for (int pass = 0; pass < settings.denoisePasses; pass++) {
            Framebuffer target = denoise[pass % 2];
            target.bind();
            atrousShader.set("uStride", (float) (1 << pass));
            atrousShader.bindTexture("uLight", 3, GLES30.GL_TEXTURE_2D, input);
            quad.draw();
            input = target.colorTexture(0);
        }
    }

    private int tracedLightTexture() {
        if (!settings.tracingActive()) {
            return 0;
        }
        if (settings.denoiser && settings.denoisePasses > 0) {
            return denoise[(settings.denoisePasses - 1) % 2].colorTexture(0);
        }
        return trace[traceIndex ^ 1].colorTexture(0);
    }

    private void renderComposite() {
        // Sampling attached depth is a GLES feedback loop even when depth writes are disabled.
        hdr.attachExternalDepth(0);
        hdr.bind();
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthMask(false);
        GLES30.glDisable(GLES30.GL_BLEND);

        boolean tracing = settings.tracingActive();
        Shader shader = tracing ? compositeShader : rasterCompositeShader;
        shader.use();
        applySceneUniforms(shader);
        shader.bindTexture("uSurface", 4, GLES30.GL_TEXTURE_2D, gbuffer.colorTexture(0));
        shader.bindTexture("uDepth", 5, GLES30.GL_TEXTURE_2D, gbuffer.depthTexture());
        surfaceNoise.bind(shader, 9);
        Framebuffer traced = trace[traceIndex ^ 1];
        shader.bindTexture("uLight", 6, GLES30.GL_TEXTURE_2D,
                tracing ? tracedLightTexture() : gbuffer.colorTexture(0));
        shader.bindTexture("uRtDepth", 7, GLES30.GL_TEXTURE_2D,
                tracing ? traced.colorTexture(1) : gbuffer.colorTexture(0));
        shader.bindTexture("uReflection", 8, GLES30.GL_TEXTURE_2D,
                tracing && settings.rtReflections ? traced.colorTexture(2) : gbuffer.colorTexture(0));
        shader.set("uLightTexelSize", 1f / traceWidth, 1f / traceHeight);
        shader.set("uRayTracing", tracing ? 1f : 0f);
        shader.set("uReflectionStrength", settings.rtReflections ? 1f : 0f);
        // Hardcore Battle Field deliberately removes the silhouette information advantage. Keep
        // the user preference intact so it resumes automatically in modes where it is allowed.
        boolean showPlayerHighlights = settings.playerHighlights && !match.hideTeamIndicators();
        shader.set("uPlayerHighlights", showPlayerHighlights ? 1f : 0f);
        quad.draw();
    }

    /** Glass, decals, tracers and particles, blended over the resolved image. */
    private void renderForward() {
        hdr.attachExternalDepth(gbuffer.depthTexture());
        hdr.bind();
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthMask(false);

        // Decals first so particles and tracers draw over them.
        Effects effects = match.effects();
        int decalCount = fillDecals(effects);
        if (decalCount > 0) {
            GLES30.glEnable(GLES30.GL_BLEND);
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
            decalShader.use();
            decalShader.setMatrix("uViewProjection", viewProjection);
            decals.upload(decalCount);
            decals.draw();
        }

        // Translucent voxels.
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
        GLES30.glDisable(GLES30.GL_CULL_FACE);
        glassShader.use();
        applySceneUniforms(glassShader);
        glassShader.setMatrix("uViewProjection", viewProjection);
        chunks.drawTranslucent(glassShader);
        GLES30.glEnable(GLES30.GL_CULL_FACE);

        com.ccs.voxelgun.game.MovementTrial trial = match.movementTrial();
        if (trial != null && trial.running() && trial.ghost().sample(trial.millis(), ghostPose)) {
            ghostRenderer.draw(ghostPose, viewProjection, frustum);
        }

        int alphaCount = fillParticles(effects, false);
        if (alphaCount > 0) {
            particleShader.use();
            setParticleUniforms(particleShader);
            particlesAlpha.upload(alphaCount);
            particlesAlpha.draw();
        }

        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE);
        int additiveCount = fillParticles(effects, true);
        if (additiveCount > 0) {
            particleShader.use();
            setParticleUniforms(particleShader);
            particlesAdditive.upload(additiveCount);
            particlesAdditive.draw();
        }

        int tracerCount = fillTracers(effects);
        if (tracerCount > 0) {
            tracerShader.use();
            tracerShader.setMatrix("uViewProjection", viewProjection);
            tracerShader.set("uCameraPosition", match.cameraPosition.x, match.cameraPosition.y,
                    match.cameraPosition.z);
            tracers.upload(tracerCount);
            tracers.draw();
        }

        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glDepthMask(true);
    }

    private void setParticleUniforms(Shader shader) {
        shader.setMatrix("uViewProjection", viewProjection);
        // Camera basis for billboarding, taken straight from the view matrix rows.
        shader.set("uCameraRight", view[0], view[4], view[8]);
        shader.set("uCameraUp", view[1], view[5], view[9]);
    }

    private int fillParticles(Effects effects, boolean additive) {
        InstancedQuads target = additive ? particlesAdditive : particlesAlpha;
        float[] data = target.data();
        int count = 0;
        float fraction = settings.adaptivePerformance ? adaptiveWorkload.effectFraction() : 1f;
        int budget = Math.max(32, Math.round(target.capacity() * fraction));
        for (int i = 0; i < effects.particleCount(); i++) {
            int kind = effects.particleKind[i];
            boolean glowing = kind == Effects.KIND_SPARK || kind == Effects.KIND_FIRE;
            if (glowing != additive || count >= target.capacity()) {
                continue;
            }
            // Stable spatial subsampling avoids changing the surviving particles every frame.
            // Smoke is preserved: reducing its density can expose players hidden by grenades.
            if (kind != Effects.KIND_SMOKE && count >= budget) continue;
            if (kind != Effects.KIND_SMOKE && ((i * 37) % 100) >= Math.round(fraction * 100f)) continue;
            float radius = effects.particleSize[i] * 2.2f;
            if (!frustum.containsBox(effects.particleX[i] - radius, effects.particleY[i] - radius,
                    effects.particleZ[i] - radius, effects.particleX[i] + radius,
                    effects.particleY[i] + radius, effects.particleZ[i] + radius)) continue;
            float life = effects.particleLife[i] / Math.max(0.001f, effects.particleMaxLife[i]);
            float fade = kind == Effects.KIND_SMOKE ? life * 0.55f : Math.min(1f, life * 1.8f);
            float size = effects.particleSize[i]
                    * (kind == Effects.KIND_SMOKE ? (2.2f - life * 1.2f) : 1f);
            float glow = 1f + effects.particleGlow[i] * life;

            int base = count * InstancedQuads.FLOATS_PER_INSTANCE;
            data[base] = effects.particleX[i];
            data[base + 1] = effects.particleY[i];
            data[base + 2] = effects.particleZ[i];
            data[base + 3] = size;
            data[base + 4] = effects.particleR[i] * glow;
            data[base + 5] = effects.particleG[i] * glow;
            data[base + 6] = effects.particleB[i] * glow;
            data[base + 7] = fade;
            count++;
        }
        return count;
    }

    private int fillTracers(Effects effects) {
        float[] data = tracers.data();
        int count = 0;
        for (int i = 0; i < effects.tracerCount() && count < tracers.capacity(); i++) {
            int base = count * InstancedQuads.FLOATS_PER_INSTANCE;
            data[base] = effects.tracerX0[i];
            data[base + 1] = effects.tracerY0[i];
            data[base + 2] = effects.tracerZ0[i];
            data[base + 3] = 0.035f;
            data[base + 4] = effects.tracerX1[i];
            data[base + 5] = effects.tracerY1[i];
            data[base + 6] = effects.tracerZ1[i];
            data[base + 7] = effects.tracerHeat[i];
            count++;
        }
        return count;
    }

    private int fillDecals(Effects effects) {
        float[] data = decals.data();
        int count = 0;
        for (int i = 0; i < effects.decalCount() && count < decals.capacity(); i++) {
            int base = count * InstancedQuads.FLOATS_PER_INSTANCE;
            data[base] = effects.decalX[i];
            data[base + 1] = effects.decalY[i];
            data[base + 2] = effects.decalZ[i];
            data[base + 3] = effects.decalSize[i];
            data[base + 4] = effects.decalNx[i];
            data[base + 5] = effects.decalNy[i];
            data[base + 6] = effects.decalNz[i];
            data[base + 7] = Math.min(1f, effects.decalLife[i] * 0.5f);
            count++;
        }
        return count;
    }

    /** Folds the jittered frames together, following the motion vectors. */
    private void renderTaa() {
        if (!settings.temporalAntiAliasing) {
            taaHistoryValid = false;
            return;
        }
        Framebuffer target = taa[taaIndex];
        Framebuffer history = taa[taaIndex ^ 1];
        target.bind();
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthMask(false);
        GLES30.glDisable(GLES30.GL_BLEND);

        taaShader.use();
        taaShader.bindTexture("uCurrent", 0, GLES30.GL_TEXTURE_2D, hdr.colorTexture(0));
        taaShader.bindTexture("uHistory", 1, GLES30.GL_TEXTURE_2D, history.colorTexture(0));
        taaShader.bindTexture("uVelocity", 2, GLES30.GL_TEXTURE_2D, gbuffer.colorTexture(1));
        taaShader.bindTexture("uDepth", 3, GLES30.GL_TEXTURE_2D, gbuffer.depthTexture());
        taaShader.set("uTexelSize", 1f / renderWidth, 1f / renderHeight);
        taaShader.set("uBlend", taaHistoryValid ? 0.9f : 0f);
        quad.draw();

        taaIndex ^= 1;
        taaHistoryValid = true;
    }

    /** The image the post chain should read: the TAA result when it ran, otherwise the HDR pass. */
    private int sceneTexture() {
        if (settings.temporalAntiAliasing && taaHistoryValid) {
            return taa[taaIndex ^ 1].colorTexture(0);
        }
        return hdr.colorTexture(0);
    }

    private void renderBloom() {
        if (!settings.bloom) {
            return;
        }
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDisable(GLES30.GL_BLEND);
        bloomDownShader.use();
        int sourceTexture = sceneTexture();
        int sourceWidth = renderWidth;
        int sourceHeight = renderHeight;
        for (int i = 0; i < bloom.length; i++) {
            bloom[i].bind();
            bloomDownShader.bindTexture("uSource", 0, GLES30.GL_TEXTURE_2D, sourceTexture);
            bloomDownShader.set("uTexelSize", 1f / sourceWidth, 1f / sourceHeight);
            bloomDownShader.set("uThreshold", 1.05f);
            bloomDownShader.set("uFirstLevel", i == 0 ? 1f : 0f);
            quad.draw();
            sourceTexture = bloom[i].colorTexture(0);
            sourceWidth = bloom[i].width();
            sourceHeight = bloom[i].height();
        }

        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE);
        bloomUpShader.use();
        bloomUpShader.set("uRadius", 1.1f);
        for (int i = bloom.length - 1; i > 0; i--) {
            bloom[i - 1].bind();
            bloomUpShader.bindTexture("uSource", 0, GLES30.GL_TEXTURE_2D, bloom[i].colorTexture(0));
            bloomUpShader.set("uTexelSize", 1f / bloom[i].width(), 1f / bloom[i].height());
            quad.draw();
        }
        GLES30.glDisable(GLES30.GL_BLEND);
    }

    private void renderTonemap() {
        Framebuffer.bindDefault(surfaceWidth, surfaceHeight);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glClearColor(0f, 0f, 0f, 1f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);

        MapEnvironment environment = match.environment();
        Combatant player = match.player();
        tonemapShader.use();
        tonemapShader.bindTexture("uScene", 0, GLES30.GL_TEXTURE_2D, sceneTexture());
        tonemapShader.bindTexture("uBloom", 1, GLES30.GL_TEXTURE_2D,
                settings.bloom ? bloom[0].colorTexture(0) : sceneTexture());
        tonemapShader.set("uTexelSize", 1f / renderWidth, 1f / renderHeight);
        tonemapShader.set("uExposure", environment.exposure);
        tonemapShader.set("uBrightness", settings.brightness);
        tonemapShader.set("uGamma", settings.gamma);
        tonemapShader.set("uContrast", settings.contrast);
        tonemapShader.set("uBloomStrength", settings.bloom ? 0.55f : 0f);
        tonemapShader.set("uVignette", 0.34f);
        tonemapShader.set("uGrain", settings.filmGrain ? 0.018f : 0f);
        tonemapShader.set("uTime", simulationTime);
        // The raster-only compatibility path stays to one scene lookup in the final pass.
        tonemapShader.set("uSharpen", settings.temporalAntiAliasing ? 0.34f
                : settings.tracingActive() ? 0.28f : 0f);
        tonemapShader.set("uDamage", MathUtil.clamp(player.damageFlash, 0f, 1f));
        tonemapShader.set("uFlash", MathUtil.clamp(player.flash, 0f, 1f));
        tonemapShader.set("uLowHealth", player.alive
                ? MathUtil.clamp(1f - player.health / 45f, 0f, 1f) : 1f);
        tonemapShader.set("uScopeMask", MathUtil.clamp(match.scopeBlend, 0f, 1f));
        tonemapShader.set("uAspect", (float) renderWidth / renderHeight);
        quad.draw();
    }

    private void applySceneUniforms(Shader shader) {
        MapEnvironment environment = match.environment();
        scene.bind(shader, 0);
        shader.setMatrix("uInverseViewProjection", inverseViewProjection);
        shader.setMatrix("uView", view);
        shader.set("uCameraPosition", match.cameraPosition.x, match.cameraPosition.y,
                match.cameraPosition.z);
        shader.set("uDepthPlanes", NEAR_PLANE, settings.viewDistance);
        shader.set("uSunDirection", environment.sunX, environment.sunY, environment.sunZ);
        shader.set("uSunColor", environment.sunR, environment.sunG, environment.sunB);
        shader.set("uSunIntensity", environment.sunIntensity);
        shader.set("uSunAngle", environment.sunAngle);
        shader.set("uSkyZenith", environment.zenithR, environment.zenithG, environment.zenithB);
        shader.set("uSkyHorizon", environment.horizonR, environment.horizonG,
                environment.horizonB);
        shader.set("uSkyIntensity", environment.skyIntensity);
        shader.set("uFogDensity", environment.fogDensity);
        shader.set("uLightRange", settings.lightRange);
    }

    /** Stops background meshing; safe to call from any thread. */
    public void shutdownWorkers() {
        stopThermalMonitoring();
        if (chunks != null) {
            chunks.stopWorkers();
        }
    }

    /** Frees GL objects; call from the GL thread before the context goes away. */
    public void release() {
        ghostRenderer.release();
        if (decorations != null) { decorations.release(); decorations = null; }
        if (!initialised) {
            return;
        }
        initialised = false;
        if (chunks != null) {
            chunks.release();
        }
        entities.release();
        scene.release();
        surfaceNoise.release();
        if (quad != null) {
            quad.release();
        }
        if (particlesAlpha != null) {
            particlesAlpha.release();
            particlesAdditive.release();
            tracers.release();
            decals.release();
        }
        if (gbuffer != null) {
            gbuffer.release(true);
            hdr.release(true);
            for (Framebuffer buffer : trace) {
                buffer.release(true);
            }
            for (Framebuffer buffer : denoise) {
                buffer.release(true);
            }
            for (Framebuffer buffer : bloom) {
                buffer.release(true);
            }
            for (Framebuffer buffer : taa) {
                buffer.release(true);
            }
        }
        if (library != null) {
            library.release();
        }
    }
}
