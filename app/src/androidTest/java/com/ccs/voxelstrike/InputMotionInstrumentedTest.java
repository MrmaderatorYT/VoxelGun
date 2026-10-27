package com.ccs.voxelstrike;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.ccs.voxelstrike.game.GameMode;
import com.ccs.voxelstrike.game.HudSnapshot;
import com.ccs.voxelstrike.game.Match;
import com.ccs.voxelstrike.game.MatchConfig;
import com.ccs.voxelstrike.game.MatchModifier;
import com.ccs.voxelstrike.input.InputState;
import com.ccs.voxelstrike.map.Maps;
import com.ccs.voxelstrike.map.maps.BattlefieldMaps;
import com.ccs.voxelstrike.voxel.NativeBridge;
import com.ccs.voxelstrike.weapon.WeaponSlot;
import com.ccs.voxelstrike.ui.HudView;
import com.ccs.voxelstrike.ui.MatchActivity;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class InputMotionInstrumentedTest {
    @Test public void actualTouchGrenadeButtonThrowsOnEveryNativeBattlefield() {
        assertTrue(NativeBridge.available());
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        for (int map = 0; map < BattlefieldMaps.COUNT; map++) {
            MatchConfig config = new MatchConfig();
            config.mode = GameMode.BATTLE_FIELD;
            config.mapIndex = Maps.firstBattlefieldIndex() + map;
            config.conquest = true;
            config.modifiers = MatchModifier.NO_BOTS.bit;
            Match match = new Match(config);
            try {
                InputState input = new InputState();
                for (int i = 0; i < 210; i++) match.update(Match.STEP, input);
                instrumentation.runOnMainSync(() -> {
                    HudView hud = new HudView(instrumentation.getTargetContext());
                    hud.bind(input, () -> {});
                    hud.setSnapshot(match.snapshot());
                    hud.layout(0, 0, 1280, 720);
                    float[] xs = field(hud, "buttonX"), ys = field(hud, "buttonY");
                    int button = fieldStatic(HudView.class, "BUTTON_GRENADE");
                    long now = SystemClock.uptimeMillis();
                    for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
                        MotionEvent event = MotionEvent.obtain(now, now, action, xs[button], ys[button], 0);
                        try { assertTrue(hud.onTouchEvent(event)); } finally { event.recycle(); }
                    }
                });
                match.update(Match.STEP, input);
                var grenade = match.player().weapons[WeaponSlot.GRENADE.ordinal()];
                assertEquals(match.map().id(), 2, grenade.ammo() + grenade.reserve());
                int projectiles = 0;
                for (var projectile : match.projectiles()) if (projectile.active) projectiles++;
                assertEquals(match.map().id(), 1, projectiles);
            } finally { match.release(); }
        }
    }

    @Test public void enterClosesShopWithoutBuyingOrFiring() { closeShop(KeyEvent.KEYCODE_ENTER); }
    @Test public void numpadEnterAlsoClosesShop() { closeShop(KeyEvent.KEYCODE_NUMPAD_ENTER); }

    private void closeShop(int code) {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (ActivityScenario<MatchActivity> scenario = ActivityScenario.launch(MatchActivity.intent(
                context, GameMode.RANGE.ordinal(), 0, 0, 1))) {
            AtomicBoolean ready = new AtomicBoolean();
            long deadline = SystemClock.uptimeMillis() + 30000;
            do {
                scenario.onActivity(activity -> ready.set(activity.hasWindowFocus()
                        && activity.findViewById(R.id.loading_overlay).getVisibility() == View.GONE));
                if (!ready.get()) SystemClock.sleep(50);
            } while (!ready.get() && SystemClock.uptimeMillis() < deadline);
            assertTrue("match must finish loading", ready.get());
            scenario.onActivity(activity -> {
                invoke(activity, "showBuyMenu");
                Dialog dialog = field(activity, "buyDialog");
                InputState input = field(activity, "input");
                assertNotNull(dialog);
                assertTrue(dialog.isShowing());
                assertTrue(input.paused);
                // Enter must close even when a purchasable card button has keyboard focus.
                View purchase = dialog.findViewById(R.id.weapon_action);
                assertNotNull(purchase);
                purchase.setFocusableInTouchMode(true);
                purchase.requestFocus();
                assertTrue(dialog.dispatchKeyEvent(key(code, KeyEvent.ACTION_DOWN, 0)));
                assertTrue(dialog.dispatchKeyEvent(key(code, KeyEvent.ACTION_DOWN, 2)));
                assertTrue(dialog.isShowing());
                assertEquals(-1, input.consumePurchase());
                assertTrue(dialog.dispatchKeyEvent(key(code, KeyEvent.ACTION_UP, 0)));
                assertFalse(dialog.isShowing());
                assertFalse("closing Enter cannot leak into gameplay fire", input.fireHeld);
                assertEquals(-1, input.consumePurchase());
            });
            // Android posts the dismiss listener to the main queue after removing the window.
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                InputState input = field(activity, "input");
                assertNull(field(activity, "buyDialog"));
                assertFalse(input.paused);
                assertFalse("closing Enter cannot leak into gameplay fire", input.fireHeld);
                assertEquals(-1, input.consumePurchase());
            });
        }
    }

    @Test public void speedLineNeedsBhopButCourseTimerDoesNot() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            HudView hud = new HudView(InstrumentationRegistry.getInstrumentation().getTargetContext());
            hud.layout(0, 0, 1280, 720);
            HudSnapshot state = new HudSnapshot();
            state.alive = true;
            state.speed = 12.3f;
            for (boolean bhop : new boolean[]{false, true}) {
                state.bhop = bhop;
                Bitmap image = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888);
                drawMovement(hud, new Canvas(image), state);
                assertEquals(bhop, painted(image, 625, 648));
                image.recycle();
            }
            state.bhop = false;
            state.movementTrial = true;
            Bitmap image = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888);
            drawMovement(hud, new Canvas(image), state);
            assertFalse("disabled bhop hides speed even during training", painted(image, 625, 644));
            assertTrue("course timer remains visible", painted(image, 650, 665));
            assertTrue("course instructions remain visible", painted(image, 667, 685));
            image.recycle();
        });
    }

    private static boolean painted(Bitmap image, int top, int bottom) {
        for (int y = top; y < bottom; y++) for (int x = 0; x < image.getWidth(); x++) {
            if (image.getPixel(x, y) != 0) return true;
        }
        return false;
    }

    private static void drawMovement(HudView hud, Canvas canvas, HudSnapshot state) {
        try {
            Method method = HudView.class.getDeclaredMethod("drawMovement", Canvas.class,
                    HudSnapshot.class, float.class);
            method.setAccessible(true);
            method.invoke(hud, canvas, state, 1f);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void invoke(Object object, String name) {
        try {
            Method method = object.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(object);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object object, String name) {
        try {
            Field field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(object);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static KeyEvent key(int code, int action, int repeat) {
        long now = SystemClock.uptimeMillis();
        return new KeyEvent(now, now, action, code, repeat, 0, -1, 0, 0,
                InputDevice.SOURCE_KEYBOARD);
    }

    private static int fieldStatic(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(null);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
}
