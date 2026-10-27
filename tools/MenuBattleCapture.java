package com.ccs.voxelstrike;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES30;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.ccs.voxelstrike.game.BattlefieldClass;
import com.ccs.voxelstrike.game.Combatant;
import com.ccs.voxelstrike.game.GameMode;
import com.ccs.voxelstrike.game.Match;
import com.ccs.voxelstrike.game.MatchConfig;
import com.ccs.voxelstrike.game.Team;
import com.ccs.voxelstrike.input.InputState;
import com.ccs.voxelstrike.map.Maps;
import com.ccs.voxelstrike.render.GameRenderer;
import com.ccs.voxelstrike.render.RenderSettings;
import com.ccs.voxelstrike.voxel.NativeBridge;
import com.ccs.voxelstrike.weapon.WeaponType;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MANUAL ASSET CAPTURE UTILITY, intentionally outside every Gradle source set.
 *
 * This is not a regression test and must not be kept in androidTest. An operator may explicitly
 * copy it temporarily into app/src/androidTest/java/com/ccs/voxelstrike/MenuBattleCapture.java,
 * build the instrumentation APK, run only com.ccs.voxelstrike.MenuBattleCapture, pull the resulting
 * target-cache PNGs, and remove that temporary source. The JUnit entry point is only the manual
 * Android launcher. It captures the actual game renderer on the device; it generates no artwork
 * and performs no compositing, color treatment, cropping or image retouching.
 */
@RunWith(AndroidJUnit4.class)
public final class MenuBattleCapture {
    private static final int WIDTH = 2400, HEIGHT = 1080;
    private static final long SEED = 4242L;

    // Deliberately staggered, collision-free points on the real Civic Circle pavement.
    // The foreground infantry lies to the right of the camera; the left 40% can host menu copy.
    private static final float[][] BLUE = {
            {314.5f, 315.5f}, {321.5f, 310.5f}, {327.5f, 305.5f}, {335.5f, 301.5f},
            {307.5f, 301.5f}, {313.5f, 296.5f}, {322.5f, 299.5f}, {329.5f, 293.5f},
            {338.5f, 297.5f}, {319.5f, 291.5f}
    };
    private static final float[][] RED = {
            {318.5f, 294.5f}, {327.5f, 293.5f}, {335.5f, 289.5f}, {312.5f, 287.5f},
            {338.5f, 273.5f}, {308.5f, 264.5f}, {317.5f, 266.5f}, {326.5f, 260.5f},
            {344.5f, 267.5f}, {335.5f, 264.5f}
    };
    // xyz, look-at xyz. Two real views of exactly the same frozen battle moment.
    private static final float[][] CAMERAS = {
            {311.5f, 4.6f, 319.5f, 311.5f, 4.1f, 277.5f},
            {312.5f, 4.7f, 319.5f, 312.5f, 4.1f, 277.5f}
    };

    @Test public void captureActualBattleFrames() throws Exception {
        require(NativeBridge.available(), "This utility requires Android JNI, not the JVM fallback");
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        require(display != EGL14.EGL_NO_DISPLAY, "No EGL display");
        require(EGL14.eglInitialize(display, new int[2], 0, new int[2], 0), "EGL initialize");
        EGLContext context = EGL14.EGL_NO_CONTEXT;
        EGLSurface surface = EGL14.EGL_NO_SURFACE;
        Match match = null;
        GameRenderer renderer = null;
        Map<String, Object> weaponMeshes = null;
        Object hiddenKnife = null;
        String knifeKey = WeaponType.KNIFE.modelKey();
        try {
            EGLConfig[] configs = new EGLConfig[1];
            int[] count = new int[1];
            require(EGL14.eglChooseConfig(display, new int[]{
                    EGL14.EGL_RENDERABLE_TYPE, 0x40, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_DEPTH_SIZE, 24, EGL14.EGL_NONE
            }, 0, configs, 0, 1, count, 0) && count[0] > 0, "No GLES 3 pbuffer config");
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                    new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE}, 0);
            require(context != EGL14.EGL_NO_CONTEXT, "GLES 3 context");
            surface = EGL14.eglCreatePbufferSurface(display, configs[0], new int[]{
                    EGL14.EGL_WIDTH, WIDTH, EGL14.EGL_HEIGHT, HEIGHT, EGL14.EGL_NONE
            }, 0);
            require(surface != EGL14.EGL_NO_SURFACE, "2400x1080 pbuffer");
            require(EGL14.eglMakeCurrent(display, surface, surface, context), "Make EGL current");

            MatchConfig config = new MatchConfig();
            config.mode = GameMode.BATTLE_FIELD;
            config.mapIndex = Maps.indexOf("battlefield_city");
            require(Maps.create(config.mapIndex).id().equals("battlefield_city"), "City map missing");
            config.seed = SEED;
            config.totalPlayers = 22;
            match = new Match(config);
            require(match.world().nativeHandle() != 0, "World is not native");
            // Production matchmaking requires even teams. The capture has one invisible observer
            // and ten visible fighters on each side: remove only the surplus staged participant.
            while (match.combatants().size() > 21) {
                Combatant surplus = match.combatants().remove(match.combatants().size() - 1);
                surplus.alive = false; // Its retained brain safely returns without updating.
            }
            require(match.combatants().size() == 21, "Capture requires observer plus twenty actors");

            InputState input = new InputState();
            input.paused = true;
            RenderSettings settings = new RenderSettings();
            settings.applyPreset(RenderSettings.Quality.LOW);
            settings.rayTracing = false;
            settings.temporalAntiAliasing = true;
            settings.bloom = false;
            settings.brightness = .82f;
            settings.contrast = 1.08f;
            settings.filmGrain = false;
            settings.playerHighlights = false;
            settings.renderScale = 1f;
            settings.viewDistance = 190f;
            settings.fieldOfView = 68f;
            settings.adaptivePerformance = false;
            settings.frameRateCap = 0;
            renderer = new GameRenderer(target, match, input, settings);
            renderer.onSurfaceCreated(null, null);
            renderer.onSurfaceChanged(null, WIDTH, HEIGHT);
            long deadline = System.nanoTime() + 60_000_000_000L;
            while (!renderer.ready() && System.nanoTime() < deadline) {
                renderer.onDrawFrame(null);
                Thread.sleep(5L);
            }
            require(renderer.ready(), "City chunks did not upload within sixty seconds");

            input.paused = false;
            for (int tick = 0; tick < 300 && match.state() == Match.State.COUNTDOWN; tick++)
                match.update(Match.STEP, input);
            require(match.combatActive(), "Real Match countdown did not reach active combat");

            List<Combatant> blue = fighters(match, Team.BLUE), red = fighters(match, Team.RED);
            require(blue.size() == 10 && red.size() == 10, "Expected ten visible fighters per team");
            stage(match, blue, BLUE, false);
            stage(match, red, RED, true);
            for (int i = 0; i < blue.size(); i++) {
                aimAt(blue.get(i), red.get((i + 2) % red.size()));
                aimAt(red.get(i), blue.get((i + 3) % blue.size()));
            }

            Combatant observer = match.player();
            observer.resetForSpawn(305.5f, 3f, 329.5f, 0f);
            observer.setSingleWeapon(WeaponType.KNIFE);
            observer.health = 100f;
            observer.damageFlash = observer.flash = observer.screenKick = 0f;
            observer.aiming = false;
            match.scopeBlend = 0f;
            match.cameraFovScale = 1f;

            // A normal game explosion, advanced through the real effect physics so its smoke and
            // debris form a volume. Terrain remains the authored City geometry.
            match.effects().clear();
            match.effects().explosion(341.5f, 3.4f, 291.5f, 5f);
            match.effects().update(.14f, match.world());
            Method tryFire = Match.class.getDeclaredMethod("tryFire", Combatant.class, boolean.class);
            tryFire.setAccessible(true);
            int actualShots = 0;
            for (int i = 0; i < 4; i++) {
                actualShots += fire(match, tryFire, blue.get(i));
                actualShots += fire(match, tryFire, red.get(i));
            }
            require(actualShots == 8, "Expected eight actual ammo-consuming hitscan attacks");
            require(match.effects().tracerCount() >= 4, "Real shots did not create tracers");
            input.paused = true; // Freeze Match only; the renderer still resolves complete frames.

            // Suppress only the observer's unique first-person knife via the renderer's existing
            // mesh=null path. Keep the observer alive to avoid the death vignette/scope mask.
            Object entities = field(renderer, "entities");
            @SuppressWarnings("unchecked")
            Map<String, Object> meshes = (Map<String, Object>) field(entities, "weapons");
            weaponMeshes = meshes;
            hiddenKnife = weaponMeshes.remove(knifeKey);
            require(hiddenKnife != null, "Knife mesh was not loaded");

            for (int view = 0; view < CAMERAS.length; view++) {
                float[] camera = CAMERAS[view];
                setCamera(match, camera);
                require(!match.world().boxCollides(camera[0], camera[1], camera[2], .15f, .15f),
                        "Camera intersects native City geometry");
                int visible = visibleActors(match, camera);
                require(visible >= 18, "City geometry occludes too many staged actors: " + visible);
                setField(renderer, "taaHistoryValid", false);
                setField(renderer, "resetTraceHistory", true);
                for (Combatant c : match.combatants()) c.renderPreviousValid = false;
                for (int frame = 0; frame < 12; frame++) {
                    renderer.onDrawFrame(null);
                    GLES30.glFinish();
                }
                require(GLES30.glGetError() == GLES30.GL_NO_ERROR, "GLES rendering failed");
                String stem = "menu-battle-" + (view + 1);
                File png = new File(target.getCacheDir(), stem + ".png");
                saveFramebuffer(png);
                String metadata = metadata(match, camera, visible, actualShots);
                File text = new File(target.getCacheDir(), stem + ".txt");
                try (Writer writer = new OutputStreamWriter(new FileOutputStream(text), StandardCharsets.UTF_8)) {
                    writer.write(metadata);
                }
                Log.i("MenuBattleCapture", png.getAbsolutePath() + "\n" + metadata);
            }
        } finally {
            if (weaponMeshes != null && hiddenKnife != null) weaponMeshes.put(knifeKey, hiddenKnife);
            if (renderer != null) renderer.release();
            if (match != null) match.release();
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface);
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context);
            EGL14.eglTerminate(display);
        }
    }

    private static List<Combatant> fighters(Match match, Team team) {
        List<Combatant> result = new ArrayList<>();
        for (Combatant c : match.combatants()) if (c.bot && c.team == team) result.add(c);
        // The close silhouettes are infantry; the real sniper kits remain farther behind them.
        result.sort(Comparator.comparingInt(c -> c.battlefieldClass == BattlefieldClass.SNIPER ? 1 : 0));
        return result;
    }

    private static void stage(Match match, List<Combatant> actors, float[][] points, boolean red) {
        for (int i = 0; i < actors.size(); i++) {
            Combatant c = actors.get(i);
            float x = points[i][0], z = points[i][1];
            require(!match.world().boxCollides(x, 3f, z, Combatant.RADIUS, Combatant.HEIGHT),
                    "Actor position intersects native City geometry: " + x + "," + z);
            c.applyKit();
            c.resetForSpawn(x, 3f, z, red ? (float) Math.PI : 0f);
            c.grounded = true;
            c.health = 100000f;
            c.armor = 5000f;
            c.switchTimer = 0f;
            c.steady = 1f;
            c.crouching = i == 2 || i == 6;
            c.crouchBlend = c.crouching ? 1f : 0f;
            c.aiming = c.battlefieldClass == BattlefieldClass.SNIPER;
            c.walkCycle = .6f + i * .77f;
            if (i == 0 || i == 3 || i == 5) c.velocity.z = red ? 1.6f : -1.6f;
        }
    }

    private static void aimAt(Combatant actor, Combatant target) {
        float dx = target.eyeX() - actor.eyeX(), dz = target.eyeZ() - actor.eyeZ();
        actor.yaw = (float) Math.atan2(dx, -dz);
        actor.pitch = (float) Math.atan2(target.eyeY() - .25f - actor.eyeY(), Math.hypot(dx, dz));
    }

    private static int fire(Match match, Method method, Combatant actor) throws Exception {
        actor.sprinting = false;
        int before = actor.weapon().ammo();
        boolean fired = (Boolean) method.invoke(match, actor, true);
        actor.firing = fired;
        require(fired && actor.weapon().ammo() == before - 1, "Real weapon failed to consume ammo");
        return 1;
    }

    private static void setCamera(Match match, float[] camera) {
        match.cameraPosition.set(camera[0], camera[1], camera[2]);
        float dx = camera[3] - camera[0], dz = camera[5] - camera[2];
        match.cameraYaw = (float) Math.atan2(dx, -dz);
        match.cameraPitch = (float) Math.atan2(camera[4] - camera[1], Math.hypot(dx, dz));
        match.cameraRoll = 0f;
        match.cameraFovScale = 1f;
    }

    private static int visibleActors(Match match, float[] camera) {
        int visible = 0;
        for (Combatant c : match.combatants()) if (c.bot && c.alive
                && match.world().lineOfSight(camera[0], camera[1], camera[2], c.eyeX(), c.eyeY(), c.eyeZ()))
            visible++;
        return visible;
    }

    private static void saveFramebuffer(File target) throws Exception {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 1);
        ByteBuffer rgba = ByteBuffer.allocateDirect(WIDTH * HEIGHT * 4);
        GLES30.glReadPixels(0, 0, WIDTH, HEIGHT, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, rgba);
        require(GLES30.glGetError() == GLES30.GL_NO_ERROR, "Native framebuffer read failed");
        int[] argb = new int[WIDTH * HEIGHT];
        // GLES returns bottom-up RGBA bytes; Android Bitmap consumes top-down ARGB integers.
        // Byte access makes this independent of native byte order and avoids a red/blue swap.
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            int source = ((HEIGHT - 1 - y) * WIDTH + x) * 4;
            int r = rgba.get(source) & 255, g = rgba.get(source + 1) & 255;
            int b = rgba.get(source + 2) & 255, a = rgba.get(source + 3) & 255;
            argb[y * WIDTH + x] = a << 24 | r << 16 | g << 8 | b;
        }
        Bitmap bitmap = Bitmap.createBitmap(argb, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        try (FileOutputStream stream = new FileOutputStream(target)) {
            require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream), "PNG encoding failed");
        } finally { bitmap.recycle(); }
    }

    private static String metadata(Match match, float[] camera, int visible, int shots) {
        StringBuilder out = new StringBuilder();
        out.append("Source: actual Android JNI Match / GameRenderer GLES framebuffer; no generated art or image retouching.\n");
        out.append("Map: battlefield_city; seed: ").append(SEED).append("; mode: BATTLE_FIELD\n");
        out.append("Capture actors: 21 (one invisible observer, ten blue and ten red); authored geometry unchanged.\n");
        out.append("Renderer: raster + TAA; bloom=false; brightness=0.82; contrast=1.08; 2400x1080; renderScale=1; FOV=68; viewDistance=190; adaptive=false\n");
        out.append("GPU: ").append(GLES30.glGetString(GLES30.GL_RENDERER)).append('\n');
        out.append("GL: ").append(GLES30.glGetString(GLES30.GL_VERSION)).append('\n');
        out.append(String.format(Locale.US,
                "Camera: (%.3f, %.3f, %.3f); lookAt: (%.3f, %.3f, %.3f); yaw=%.6f; pitch=%.6f\n",
                camera[0], camera[1], camera[2], camera[3], camera[4], camera[5], match.cameraYaw, match.cameraPitch));
        out.append("Frozen after real attacks: ").append(shots).append("; tracers: ")
                .append(match.effects().tracerCount()).append("; particles: ").append(match.effects().particleCount())
                .append("; native LOS-visible actors: ").append(visible).append('\n');
        out.append("Effects: actual Effects.explosion(341.5,3.4,291.5,5), aged 0.14s, and Match.tryFire hitscans.\n");
        out.append("Observer knife mesh temporarily suppressed through existing mesh=null path; restored before renderer cleanup.\n");
        for (Combatant c : match.combatants()) if (c.bot)
            out.append(String.format(Locale.US, "Actor %d: %s %s %s at (%.2f, %.2f, %.2f), crouch=%s\n",
                    c.id, c.team, c.battlefieldClass, c.weaponType(), c.position.x, c.position.y, c.position.z, c.crouching));
        return out.toString();
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void setField(Object owner, String name, Object value) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
