package com.ccs.voxelstrike;

import android.app.Application;

/** Application entry point. The game keeps its own authored palette on every device. */
public final class VoxelStrikeApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        com.ccs.voxelstrike.app.LocalCrashReports.install(this);
    }
}
