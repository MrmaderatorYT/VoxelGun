package com.ccs.voxelgun;

import android.app.Application;

/** Application entry point. The game keeps its own authored palette on every device. */
public final class VoxelGunApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        com.ccs.voxelgun.app.LocalCrashReports.install(this);
    }
}
