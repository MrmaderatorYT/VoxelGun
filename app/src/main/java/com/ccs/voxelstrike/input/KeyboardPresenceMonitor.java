package com.ccs.voxelstrike.input;

import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;

/** Hot-plug awareness independent of mouse activity and the visibility of touch controls. */
public final class KeyboardPresenceMonitor implements InputManager.InputDeviceListener {
    public interface Listener { void onKeyboardPresence(boolean present); }
    private final InputManager manager;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean started;

    public KeyboardPresenceMonitor(Context context, Listener listener) {
        manager = (InputManager) context.getApplicationContext().getSystemService(Context.INPUT_SERVICE);
        this.listener = listener;
    }

    public void start() {
        if (!started && manager != null) {
            manager.registerInputDeviceListener(this, mainHandler);
            started = true;
        }
        refresh();
    }

    public void stop() {
        if (started && manager != null) manager.unregisterInputDeviceListener(this);
        started = false;
    }

    public static boolean physicalKeyboardConnected() {
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device != null && !device.isVirtual()
                    && device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC
                    && device.supportsSource(InputDevice.SOURCE_KEYBOARD)) return true;
        }
        return false;
    }

    private void refresh() { listener.onKeyboardPresence(physicalKeyboardConnected()); }
    @Override public void onInputDeviceAdded(int id) { refresh(); }
    @Override public void onInputDeviceRemoved(int id) { refresh(); }
    @Override public void onInputDeviceChanged(int id) { refresh(); }
}
