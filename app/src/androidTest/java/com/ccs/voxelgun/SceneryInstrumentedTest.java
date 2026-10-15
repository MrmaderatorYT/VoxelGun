package com.ccs.voxelgun;

import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;
import android.os.SystemClock;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.ccs.voxelgun.app.GameSettings;
import com.ccs.voxelgun.game.*;
import com.ccs.voxelgun.input.InputState;
import com.ccs.voxelgun.map.Maps;
import com.ccs.voxelgun.render.RenderSettings;
import com.ccs.voxelgun.ui.MatchActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Loads real maps on a mobile GPU and leaves reproducible visual-QA captures in app cache. */
@RunWith(AndroidJUnit4.class)
public class SceneryInstrumentedTest {
    @Test public void redesignedLayoutsRenderAndPublishTheirOwnCallouts() throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        GameSettings previous = new GameSettings(context), settings = new GameSettings(context);
        settings.applyPreset(RenderSettings.Quality.LOW);
        settings.adaptivePerformance = false;
        settings.matchModifiers = 0;
        settings.save();
        try {
            capture("metro_junction", "metro-concourse", 64, 8, 64,
                    (float)Math.PI, -.08f, "Concourse");
            capture("canyon_outpost", "canyon-fort", 64, 15, 40,
                    (float)Math.PI, -.25f, "North Fort");
            capture("bamboo_temple", "temple-garden", 64, 8, 82,
                    0, -.03f, "Garden Bridges");
            capture("harbor_yard", "harbor-crane", 66, 20, 48,
                    (float)Math.PI, -.3f, "Crane");
        } finally { previous.save(); }
    }

    @Test public void dressedMapsLoadAndPublishLocationNames() throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        GameSettings previous = new GameSettings(context), testSettings = new GameSettings(context);
        testSettings.applyPreset(RenderSettings.Quality.LOW);
        testSettings.adaptivePerformance = false;
        testSettings.matchModifiers = 0;
        testSettings.battlefieldHardcore = false;
        testSettings.battlefieldTotalPlayers = 2;
        testSettings.save();
        try {
            capture("village", "market", 64, 12, 43, (float)Math.PI, -.18f, "Market");
            capture("village", "blue-house", 87, 6, 7, (float)Math.PI, .18f, "Blue House");
            capture("battlefield_alps", "bridge", 403, 24, 190, (float)Math.PI, -.45f, "Gorge Bridge");
            capture("battlefield_alps", "station", 379, 8, 307, (float)Math.PI, .12f, "Mountain Station");
            capture("battlefield_alps", "lake", 451, 9, 390, (float)Math.PI, -.12f, "Frozen Lake");
            capture("battlefield_city", "supermarket", 234, 8, 114, (float)Math.PI, -.05f, "Supermarket");
            capture("warehouse", "hall", 45, 7, 30, (float)Math.PI, .03f, "Container Hall");
        } finally { previous.save(); }
    }

    @Test public void battlefieldDestinationsRenderAndPublishCallouts() throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        GameSettings previous = new GameSettings(context), settings = new GameSettings(context);
        settings.applyPreset(RenderSettings.Quality.LOW);
        settings.adaptivePerformance = false;
        settings.matchModifiers = 0;
        settings.battlefieldHardcore = false;
        settings.battlefieldTotalPlayers = 2;
        settings.save();
        try {
            capture("battlefield_city", "city-supermarket", 234, 8, 114,
                    (float)Math.PI, -.05f, "Supermarket");
            capture("battlefield_city", "city-park", 497, 12, 330,
                    (float)Math.PI, -.16f, "Lakeside Park");
            capture("battlefield_farmland", "farm-market", 235, 11, 180,
                    (float)Math.PI, -.12f, "Market Circle");
            capture("battlefield_alps", "alps-village", 250, 11, 206,
                    (float)Math.PI, -.1f, "Village Square");
            capture("battlefield_alps", "alps-station", 379, 8, 307,
                    (float)Math.PI, .08f, "Mountain Station");
            capture("battlefield_alps", "alps-lake", 451, 9, 390,
                    (float)Math.PI, -.12f, "Frozen Lake");
        } finally { previous.save(); }
    }

    private void capture(String id, String view, float x, float y, float z, float yaw,
                         float pitch, String expectedArea) throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        int mode = id.startsWith("battlefield_") ? GameMode.BATTLE_FIELD.ordinal() : GameMode.RANGE.ordinal();
        try (ActivityScenario<MatchActivity> scenario = ActivityScenario.launch(
                MatchActivity.intent(context, mode, Maps.indexOf(id), 0, 1))) {
            AtomicBoolean ready = new AtomicBoolean();
            long deadline = SystemClock.uptimeMillis() + 45000;
            do {
                scenario.onActivity(a -> ready.set(a.findViewById(R.id.loading_overlay).getVisibility() != View.VISIBLE));
                if (ready.get()) break;
                SystemClock.sleep(100);
            } while (SystemClock.uptimeMillis() < deadline);
            assertTrue(id + " did not finish loading", ready.get());
            CountDownLatch changed = new CountDownLatch(1);
            AtomicReference<String> area = new AtomicReference<>();
            scenario.onActivity(a -> {
                try {
                    var matchField = MatchActivity.class.getDeclaredField("match"); matchField.setAccessible(true);
                    var inputField = MatchActivity.class.getDeclaredField("input"); inputField.setAccessible(true);
                    Match match = (Match) matchField.get(a);
                    InputState input = (InputState) inputField.get(a);
                    GLSurfaceView surface = a.findViewById(R.id.surface);
                    surface.queueEvent(() -> {
                        input.paused = true; // Freeze combat, not the renderer or HUD.
                        match.player().position.set(x, y - Combatant.EYE_HEIGHT, z);
                        match.player().yaw = yaw; match.player().pitch = pitch;
                        match.cameraPosition.set(x, y, z); match.cameraYaw = yaw; match.cameraPitch = pitch;
                        match.update(.2f, input);
                        area.set(match.snapshot().areaName);
                        changed.countDown();
                    });
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
            assertTrue(id + " GL queue stalled", changed.await(10, TimeUnit.SECONDS));
            assertEquals(expectedArea, area.get());
            SystemClock.sleep(1200); // Allow the queued camera and HUD to reach a presented frame.
            Bitmap image = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            assertNotNull(image);
            try (FileOutputStream output = new FileOutputStream(new File(context.getCacheDir(),
                    "scenery-" + view + ".png"))) {
                assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, output));
            } finally { image.recycle(); }
        }
    }
}
