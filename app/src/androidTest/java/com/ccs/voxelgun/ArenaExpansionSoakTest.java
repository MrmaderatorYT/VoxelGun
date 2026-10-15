package com.ccs.voxelgun;

import android.content.Context;
import android.content.SharedPreferences;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.ccs.voxelgun.app.GameSettings;
import com.ccs.voxelgun.game.Combatant;
import com.ccs.voxelgun.game.Destruction;
import com.ccs.voxelgun.game.Effects;
import com.ccs.voxelgun.game.GameMode;
import com.ccs.voxelgun.game.HudSnapshot;
import com.ccs.voxelgun.game.Match;
import com.ccs.voxelgun.game.MatchModifier;
import com.ccs.voxelgun.map.Maps;
import com.ccs.voxelgun.performance.SectionMetrics;
import com.ccs.voxelgun.render.GameRenderer;
import com.ccs.voxelgun.ui.MatchActivity;
import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.voxel.VoxelWorld;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Real SurfaceView/vsync/HUD/AI soak, not a CPU-only benchmark. Default is a ten-second smoke
 * check; use instrumentation argument -e soakSeconds 900 for a fifteen-minute thermal run.
 * Settings and the complete profile preference maps are restored even when a check fails.
 */
@RunWith(AndroidJUnit4.class)
public class ArenaExpansionSoakTest {
    private static final String TAG = "ArenaSoak";
    private static final int PLAYERS = 48;
    private static final int PANEL_X = 240, PANEL_Z = 285, PANEL_WIDTH = 8, PANEL_HEIGHT = 4;

    @Test public void sustainedBattlefieldFramesWithBotsEffectsAndChunkEdits() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        int seconds = Integer.parseInt(InstrumentationRegistry.getArguments().getString("soakSeconds", "10"));
        assertTrue("soakSeconds must be 10..1800", seconds >= 10 && seconds <= 1800);
        String mapId = InstrumentationRegistry.getArguments().getString("soakMap", "battlefield_alps");
        int map = Maps.indexOf(mapId);
        assertTrue("Soak needs a full-size battlefield map", Maps.isBattlefield(map));
        Map<String, ?> previousSettings = snapshot(context, "voxelgun_settings");
        Map<String, ?> previousProfile = snapshot(context, "voxelgun_profile");
        File report = new File(context.getExternalFilesDir(null), "arena-expansion-soak.csv");
        int samples = 0, renderedSamples = 0, rounds = 0, editedBlocks = 0;
        float maximumBotTravel = 0f;
        long started = 0L, deadline = 0L;
        int minimumFps = Integer.MAX_VALUE, maximumFps = 0, maximumP95 = 0, maximumThermal = 0;
        try {
            GameSettings settings = new GameSettings(context);
            settings.battlefieldTotalPlayers = PLAYERS;
            settings.battlefieldScoreLimit = 1000;
            settings.battlefieldTimeLimitSeconds = 3600f;
            settings.battlefieldConquest = true;
            settings.matchModifiers = MatchModifier.BUNNY_HOP.bit;
            settings.adaptivePerformance = true;
            settings.showFps = true;
            settings.haptics = false;
            settings.save();
            try (BufferedWriter csv = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(report), StandardCharsets.UTF_8))) {
                csv.write("elapsed_seconds,round,fps,p95_ms,physics_us,ai_us,combat_us,effects_us,objectives_us,uploads_us,render_cpu_us,meshing_us,thermal_status,view_distance,effect_percent,bot_travel,removed_blocks\n");
                // Territory tickets can legitimately expire during a long soak. Start a fresh
                // real round rather than letting a results screen masquerade as active gameplay.
                while (deadline == 0L || SystemClock.uptimeMillis() < deadline) {
                    try (ActivityScenario<MatchActivity> scenario = ActivityScenario.launch(
                            MatchActivity.intent(context, GameMode.BATTLE_FIELD.ordinal(), map, PLAYERS - 1, 2))) {
                        Session session = new Session();
                        scenario.onActivity(activity -> {
                            session.match = (Match) field(activity, "match");
                            session.renderer = (GameRenderer) field(activity, "renderer");
                            session.view = activity.findViewById(R.id.surface);
                        });
                        awaitReady(session);
                        rounds++;
                        final int round = rounds;
                        onGl(session, () -> {
                            assertEquals(PLAYERS, session.match.combatants().size());
                            session.initialX = new float[PLAYERS]; session.initialZ = new float[PLAYERS];
                            for (int i = 0; i < PLAYERS; i++) {
                                Combatant c = session.match.combatants().get(i);
                                session.initialX[i] = c.position.x; session.initialZ[i] = c.position.z;
                            }
                            Combatant player = session.match.player();
                            int node = session.match.nav().nearestNode(224.5f, 3f, 288.5f);
                            assertTrue(session.match.nav().valid(node));
                            player.position.set(session.match.nav().cellX(node) + .5f,
                                    session.match.nav().nodeY(node), session.match.nav().cellZ(node) + .5f);
                            player.velocity.set(0f, 0f, 0f); player.yaw = (float) Math.PI * .5f;
                            session.destruction = new Destruction(session.match.world(), session.match.nav());
                        });
                        SystemClock.sleep(3500); // Let startup/countdown and frame statistics settle.
                        if (started == 0L) {
                            started = SystemClock.uptimeMillis(); deadline = started + seconds * 1000L;
                        }
                        long nextPulse = SystemClock.uptimeMillis();
                        while (SystemClock.uptimeMillis() < deadline) {
                            final Sample sample = new Sample();
                            onGl(session, () -> {
                                HudSnapshot hud = session.match.snapshot();
                                sample.finished = hud.finished;
                                if (!sample.finished) {
                                    if (session.pulse % 32 == 0) renewPanel(session);
                                    int column = session.pulse % PANEL_WIDTH;
                                    int row = session.pulse / PANEL_WIDTH % PANEL_HEIGHT;
                                    sample.removed = session.destruction.blast(PANEL_X + column + .5f,
                                            4 + row + .5f, PANEL_Z + .5f, .55f);
                                    VoxelWorld world = session.match.world();
                                    Combatant player = session.match.player();
                                    float fx = Math.max(1f, Math.min(world.sizeX() - 2f, player.position.x + 10f));
                                    float fz = Math.max(1f, Math.min(world.sizeZ() - 2f, player.position.z));
                                    session.match.effects().explosion(fx, player.position.y + 1f, fz, 2.5f);
                                    for (int i = 0; i < 24; i++) session.match.effects().spawnParticle(
                                            Effects.KIND_SMOKE, fx + i % 4, player.position.y + 1f, fz + i / 4,
                                            .1f, .5f, .1f, 2f, .6f, .25f, .24f, .23f, 0f);
                                    session.pulse++;
                                }
                                sample.fps = hud.fps; sample.p95 = hud.frameP95;
                                System.arraycopy(hud.cpuMicros, 0, sample.cpu, 0, SectionMetrics.COUNT);
                                sample.meshing = hud.meshingMicros; sample.thermal = hud.thermalStatus;
                                sample.distance = hud.viewDistance; sample.effects = hud.effectPercent;
                                for (int i = 0; i < PLAYERS; i++) {
                                    Combatant c = session.match.combatants().get(i);
                                    assertTrue("non-finite actor x", Float.isFinite(c.position.x));
                                    assertTrue("non-finite actor y", Float.isFinite(c.position.y));
                                    assertTrue("non-finite actor z", Float.isFinite(c.position.z));
                                    if (c.bot) sample.botTravel = Math.max(sample.botTravel,
                                            (float) Math.hypot(c.position.x - session.initialX[i],
                                                    c.position.z - session.initialZ[i]));
                                }
                                assertEquals("invalid GL call during live soak", GLES30.GL_NO_ERROR,
                                        GLES30.glGetError());
                            });
                            scenario.onActivity(activity -> {
                                assertFalse("Activity exited during soak", activity.isFinishing());
                                assertEquals("unexpected pause during active soak", View.GONE,
                                        activity.findViewById(R.id.pause_overlay).getVisibility());
                            });
                            if (sample.finished) break;
                            samples++;
                            if (sample.fps > 0) {
                                renderedSamples++; minimumFps = Math.min(minimumFps, sample.fps);
                                maximumFps = Math.max(maximumFps, sample.fps);
                            }
                            maximumP95 = Math.max(maximumP95, sample.p95);
                            maximumThermal = Math.max(maximumThermal, sample.thermal);
                            maximumBotTravel = Math.max(maximumBotTravel, sample.botTravel);
                            editedBlocks += sample.removed;
                            for (int value : sample.cpu) assertTrue("negative CPU timing", value >= 0);
                            assertTrue(Float.isFinite(sample.distance) && sample.distance > 0);
                            assertTrue(sample.effects >= 0 && sample.effects <= 100);
                            double elapsed = (SystemClock.uptimeMillis() - started) / 1000.0;
                            csv.write(String.format(Locale.US,
                                    "%.1f,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.1f,%d,%.1f,%d\n",
                                    elapsed, round, sample.fps, sample.p95, sample.cpu[0], sample.cpu[1],
                                    sample.cpu[2], sample.cpu[3], sample.cpu[4], sample.cpu[5], sample.cpu[6],
                                    sample.meshing, sample.thermal, sample.distance, sample.effects,
                                    sample.botTravel, sample.removed));
                            csv.flush();
                            if (samples % 30 == 0) Log.i(TAG, "progress seconds=" + (int) elapsed
                                    + " round=" + round + " fps=" + sample.fps + " p95=" + sample.p95
                                    + " thermal=" + sample.thermal + " distance=" + sample.distance
                                    + " effects=" + sample.effects + " botTravel=" + sample.botTravel);
                            nextPulse += 1000L;
                            long wait = Math.min(nextPulse, deadline) - SystemClock.uptimeMillis();
                            if (wait > 0) SystemClock.sleep(wait);
                        }
                    }
                }
            }
            assertTrue("actual presented-frame statistics must keep arriving", renderedSamples >= 3);
            assertTrue("active bots must make observable navigation progress", maximumBotTravel > 2f);
            assertTrue("runtime edits must reach destruction/chunk revision handling", editedBlocks > 0);
            Log.i(TAG, "complete seconds=" + seconds + " rounds=" + rounds + " samples=" + samples
                    + " minFps=" + minimumFps + " maxFps=" + maximumFps + " worstP95=" + maximumP95
                    + " maxThermal=" + maximumThermal + " botTravel=" + maximumBotTravel
                    + " removedBlocks=" + editedBlocks + " csv=" + report.getAbsolutePath());
        } finally {
            // Scenarios/GL workers have stopped before restoring persistent settings/progression.
            restore(context, "voxelgun_settings", previousSettings);
            restore(context, "voxelgun_profile", previousProfile);
        }
    }

    private static void renewPanel(Session session) {
        VoxelWorld world = session.match.world();
        for (int row = 0; row < PANEL_HEIGHT; row++) for (int x = 0; x < PANEL_WIDTH; x++)
            world.set(PANEL_X + x, 4 + row, PANEL_Z, Materials.WOOD);
        world.commit();
        session.match.nav().refreshArea(world, PANEL_X, PANEL_Z, PANEL_X + PANEL_WIDTH - 1, PANEL_Z);
        // The following removal stamps the chunk; workers rebuild the remaining tiny panel.
    }

    private static void awaitReady(Session session) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 60000;
        final boolean[] ready = new boolean[1];
        while (!ready[0] && SystemClock.uptimeMillis() < deadline) {
            onGl(session, () -> ready[0] = session.renderer.ready());
            if (!ready[0]) SystemClock.sleep(100);
        }
        assertTrue("initial chunks failed to finish", ready[0]);
    }

    private static void onGl(Session session, Runnable work) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        session.view.queueEvent(() -> {
            try { work.run(); } catch (Throwable error) { failure.set(error); }
            finally { completed.countDown(); }
        });
        assertTrue("render/simulation thread stalled or crashed", completed.await(8, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("GL-thread soak assertion", failure.get());
    }

    private static Map<String, ?> snapshot(Context context, String file) {
        Map<String, Object> copy = new HashMap<>();
        for (Map.Entry<String, ?> entry : context.getSharedPreferences(file, Context.MODE_PRIVATE).getAll().entrySet()) {
            Object value = entry.getValue();
            copy.put(entry.getKey(), value instanceof Set<?> ? new HashSet<>((Set<?>) value) : value);
        }
        return copy;
    }

    @SuppressWarnings("unchecked") private static void restore(Context context, String file, Map<String, ?> values) {
        SharedPreferences.Editor edit = context.getSharedPreferences(file, Context.MODE_PRIVATE).edit().clear();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String key = entry.getKey(); Object value = entry.getValue();
            if (value instanceof String) edit.putString(key, (String) value);
            else if (value instanceof Integer) edit.putInt(key, (Integer) value);
            else if (value instanceof Long) edit.putLong(key, (Long) value);
            else if (value instanceof Float) edit.putFloat(key, (Float) value);
            else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
            else if (value instanceof Set<?>) edit.putStringSet(key, (Set<String>) value);
            else throw new AssertionError("Unsupported preference type: " + key);
        }
        assertTrue("could not restore " + file, edit.commit());
    }

    private static Object field(Object owner, String name) {
        try {
            Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static final class Session {
        Match match;
        GameRenderer renderer;
        GLSurfaceView view;
        Destruction destruction;
        float[] initialX, initialZ;
        int pulse;
    }

    private static final class Sample {
        final int[] cpu = new int[SectionMetrics.COUNT];
        int fps, p95, meshing, thermal, effects, removed;
        float distance, botTravel;
        boolean finished;
    }
}
