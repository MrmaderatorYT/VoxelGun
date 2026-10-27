package com.ccs.voxelstrike;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.ccs.voxelstrike.audio.AudioEngine;
import com.ccs.voxelstrike.audio.Sfx;
import com.ccs.voxelstrike.game.GameMode;
import com.ccs.voxelstrike.map.Maps;
import com.ccs.voxelstrike.ui.MatchActivity;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AudioStartupInstrumentedTest {
    @Test public void aColdAudioStartupAndRapidResumeNeverWaitOnTheMainThread() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        AudioEngine audio = new AudioEngine();
        audio.setMasterVolume(0f);
        List<Thread> workers = new ArrayList<>();
        long[] worst = new long[1];
        try {
            for (int cycle = 0; cycle < 8; cycle++) {
                CountDownLatch pulse = new CountDownLatch(1);
                instrumentation.runOnMainSync(() -> {
                    long before = System.nanoTime();
                    audio.start();
                    Thread worker = field(audio, "mixerThread");
                    assertNotNull(worker);
                    workers.add(worker);
                    audio.start();
                    assertSame("duplicate onCreate/onResume must keep one session", worker,
                            field(audio, "mixerThread"));
                    audio.playUi(Sfx.UI, .2f, 1f);
                    audio.release();
                    worst[0] = Math.max(worst[0], System.nanoTime() - before);
                    new Handler(Looper.getMainLooper()).post(pulse::countDown);
                });
                assertTrue("UI must keep processing while sounds are being generated",
                        pulse.await(250, TimeUnit.MILLISECONDS));
            }
            assertTrue("audio lifecycle blocked UI for " + worst[0] / 1e6 + "ms",
                    worst[0] < 250_000_000L);
            audio.start();
            Thread live = field(audio, "mixerThread");
            workers.add(live);
            long deadline = SystemClock.uptimeMillis() + 5000;
            while (field(audio, "track") == null && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(10);
            }
            assertNotNull("the final session must open a real Android audio output", field(audio, "track"));
            assertTrue(live.isAlive());
            Log.i("AudioStartupTest", "worst main-thread start/release ms=" + worst[0] / 1e6);
        } finally {
            audio.release();
            for (Thread worker : workers) {
                worker.join(2500);
                assertFalse("a canceled audio session leaked its worker", worker.isAlive());
            }
        }
    }

    @Test public void bBattlefieldLaunchKeepsPauseInputResponsive() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        long start = SystemClock.uptimeMillis();
        try (ActivityScenario<MatchActivity> scenario = ActivityScenario.launch(MatchActivity.intent(
                instrumentation.getTargetContext(), GameMode.BATTLE_FIELD.ordinal(),
                Maps.firstBattlefieldIndex(), 0, 1))) {
            long launchMillis = SystemClock.uptimeMillis() - start;
            CountDownLatch pulse = new CountDownLatch(1);
            scenario.onActivity(activity -> {
                new Handler(Looper.getMainLooper()).post(pulse::countDown);
                assertTrue(activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE)));
                assertTrue(activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE)));
                assertEquals(View.VISIBLE, activity.findViewById(R.id.pause_overlay).getVisibility());
            });
            assertTrue("Battlefield startup must not block the main looper on sound generation",
                    pulse.await(250, TimeUnit.MILLISECONDS));
            Log.i("AudioStartupTest", "Battlefield Activity launch ms=" + launchMillis);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(AudioEngine engine, String name) {
        synchronized (engine) {
            try {
                Field field = AudioEngine.class.getDeclaredField(name);
                field.setAccessible(true);
                return (T) field.get(engine);
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        }
    }
}
