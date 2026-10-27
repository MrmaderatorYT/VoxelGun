package com.ccs.voxelstrike.app;

import android.content.Context;
import android.os.Build;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/** Local Java crash record only. No account identifiers, uploads, SDK, or network connection. */
public final class LocalCrashReports implements Thread.UncaughtExceptionHandler {
    private final File report;
    private final Thread.UncaughtExceptionHandler previous;
    private boolean recording;

    private LocalCrashReports(Context context) {
        report = new File(context.getFilesDir(), "last-java-crash.txt");
        previous = Thread.getDefaultUncaughtExceptionHandler();
    }

    public static void install(Context context) {
        Thread.setDefaultUncaughtExceptionHandler(new LocalCrashReports(context));
    }

    @Override public synchronized void uncaughtException(Thread thread, Throwable error) {
        if (!recording) {
            recording = true;
            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(report), StandardCharsets.UTF_8))) {
                writer.println("VoxelGun Java crash / Android " + Build.VERSION.SDK_INT);
                writer.println("Time: " + System.currentTimeMillis());
                writer.println("Thread: " + thread.getName());
                // Bound depth and frame count even when the throwable is unusually large.
                Throwable cause = error;
                for (int depth = 0; cause != null && depth < 8; depth++) {
                    writer.println(cause.getClass().getName());
                    StackTraceElement[] trace = cause.getStackTrace();
                    for (int i = 0; i < Math.min(64, trace.length); i++) writer.println("  at " + trace[i]);
                    cause = cause.getCause();
                }
            } catch (Throwable ignored) {
                // Reporting must not prevent the platform from handling the original crash.
            }
        }
        if (previous != null) previous.uncaughtException(thread, error);
        else { android.os.Process.killProcess(android.os.Process.myPid()); System.exit(10); }
    }
}
