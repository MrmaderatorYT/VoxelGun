package com.ccs.voxelgun.app;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import androidx.annotation.RequiresApi;

/** Lifecycle-bound Android thermal signal. Old/unsupported devices keep cadence adaptation. */
public final class ThermalMonitor {
    private final Context context;
    private Api29 api;
    private volatile int status;
    public ThermalMonitor(Context context) { this.context = context.getApplicationContext(); }
    public int status() { return status; }
    public void start() {
        if (Build.VERSION.SDK_INT < 29 || api != null) return;
        Api29 candidate = new Api29(context, this);
        if (candidate.start()) api = candidate;
    }
    public void stop() {
        if (Build.VERSION.SDK_INT >= 29 && api != null) { api.stop(); api = null; }
    }

    // Keep newer API types out of the class verified on Android 7/8/9.
    @RequiresApi(29)
    private static final class Api29 {
        final PowerManager power;
        final ThermalMonitor owner;
        final PowerManager.OnThermalStatusChangedListener listener;
        Api29(Context context, ThermalMonitor owner) {
            power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            this.owner = owner;
            listener = value -> owner.status = value;
        }
        boolean start() {
            if (power == null) return false;
            try {
                owner.status = power.getCurrentThermalStatus();
                power.addThermalStatusListener(listener);
                return true;
            } catch (IllegalStateException | UnsupportedOperationException error) { return false; }
        }
        void stop() {
            try { power.removeThermalStatusListener(listener); }
            catch (IllegalStateException | UnsupportedOperationException ignored) { /* Service gone. */ }
        }
    }
}
