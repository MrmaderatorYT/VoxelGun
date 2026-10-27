package com.ccs.voxelstrike;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.ccs.voxelstrike.app.KeyBindings;
import com.ccs.voxelstrike.game.*;
import com.ccs.voxelstrike.input.DeviceInput;
import com.ccs.voxelstrike.input.InputState;
import com.ccs.voxelstrike.map.MapBuilder;
import com.ccs.voxelstrike.voxel.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real Android key events and native voxel collision, without a renderer skewing timing tests. */
@RunWith(AndroidJUnit4.class)
public class SwimmingInstrumentedTest {
    private VoxelWorld pool() {
        assertTrue(NativeBridge.available());
        VoxelWorld w = new VoxelWorld(24, 16, 24);
        MapBuilder b = new MapBuilder(w, 1L);
        b.fill(0, 0, 0, 23, 6, 23, Materials.CONCRETE);
        b.pool(4, 4, 18, 18, 6, 6); w.commit(); return w;
    }

    private Combatant body() {
        Combatant c = new Combatant(1, "Native swimmer");
        c.position.set(12.5f, 1, 12.5f); c.grounded = true; return c;
    }

    private void step(VoxelWorld w, Combatant c, boolean rise) {
        if (!Swimming.applyVertical(w, c, rise, Match.STEP))
            c.velocity.y = Math.max(Physics.TERMINAL_VELOCITY,
                    c.velocity.y + Physics.GRAVITY * Match.STEP);
        c.grounded = Physics.move(w, c.position, c.velocity, Match.STEP,
                Combatant.RADIUS, c.height(), true);
        assertFalse(NativeBridge.boxCollides(w.nativeHandle(), c.position.x, c.position.y,
                c.position.z, Combatant.RADIUS, c.height()));
    }

    @Test public void swimmingUsesTheReboundJumpKeyAndHonoursRelease() {
        String file = "voxelstrike_swimming_bindings_test";
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        target.deleteSharedPreferences(file);
        Context isolated = new ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences(file, mode);
            }
        };
        VoxelWorld w = pool();
        try {
            KeyBindings bindings = new KeyBindings(isolated);
            bindings.bind(KeyBindings.Action.JUMP, KeyEvent.KEYCODE_J);
            InputState input = new InputState(); DeviceInput device = new DeviceInput(input, bindings);
            Combatant c = body();
            assertFalse(device.onKey(key(KeyEvent.KEYCODE_SPACE, KeyEvent.ACTION_DOWN)));
            assertFalse(input.jumpHeld);
            assertTrue(device.onKey(key(KeyEvent.KEYCODE_J, KeyEvent.ACTION_DOWN)));
            for (int i = 0; i < 30; i++) step(w, c, input.jumpHeld);
            assertTrue(c.position.y > 2.8f);
            assertTrue(device.onKey(key(KeyEvent.KEYCODE_J, KeyEvent.ACTION_UP)));
            step(w, c, input.jumpHeld); assertTrue(c.velocity.y <= 0);
            input.setTouchJump(true); step(w, c, input.jumpHeld); assertTrue(c.velocity.y > 0);
            device.releaseAll(); input.releaseAll(); step(w, c, input.jumpHeld);
            assertTrue(c.velocity.y <= 0);
        } finally { w.release(); target.deleteSharedPreferences(file); }
    }

    @Test public void botsEscapeTheRaisedRimWithNativeCollision() {
        VoxelWorld w = pool();
        try {
            Combatant c = body(); c.bot = true;
            BotSwimmingController controller = new BotSwimmingController();
            boolean escaped = false;
            for (int i = 0; i < 600; i++) {
                controller.update(c, w, Match.STEP); step(w, c, true);
                if (c.grounded && !Swimming.inWater(w, c)) { escaped = true; break; }
            }
            assertTrue(escaped); assertTrue(c.position.y >= 7);
        } finally { w.release(); }
    }

    @Test public void submergedCeilingStillStopsHeldAscent() {
        VoxelWorld w = pool();
        try {
            for (int x = 4; x <= 18; x++) for (int z = 4; z <= 18; z++)
                w.set(x, 5, z, Materials.CONCRETE);
            w.commit(); Combatant c = body();
            for (int i = 0; i < 120; i++) step(w, c, true);
            assertTrue(c.position.y + c.height() <= 5);
        } finally { w.release(); }
    }

    private static KeyEvent key(int code, int action) {
        long now = SystemClock.uptimeMillis();
        return new KeyEvent(now, now, action, code, 0, 0, -1, 0, 0, InputDevice.SOURCE_KEYBOARD);
    }
}
