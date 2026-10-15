package com.ccs.voxelgun.app;

import android.content.Context;
import android.content.SharedPreferences;
import com.ccs.voxelgun.map.CourseLayout;

/** UI-thread persistence; each rule set has an independent personal best. */
public final class MovementRecords {
    private final SharedPreferences preferences;
    public MovementRecords(Context context) {
        preferences = context.getSharedPreferences("voxelgun_movement_records", Context.MODE_PRIVATE);
    }
    private String key(int modifiers) { return "v" + CourseLayout.VERSION + "_rules_" + modifiers; }
    public int best(int modifiers) { return preferences.getInt(key(modifiers), 0); }
    public void saveBest(int modifiers, int millis) {
        int old = best(modifiers);
        if (millis > 0 && (old == 0 || millis < old)) preferences.edit().putInt(key(modifiers), millis).apply();
    }
}
