package com.ccs.voxelstrike;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.ccs.voxelstrike.app.GameSettings;
import com.ccs.voxelstrike.app.MovementRecords;
import com.ccs.voxelstrike.game.GameMode;
import com.ccs.voxelstrike.game.MatchModifier;
import com.ccs.voxelstrike.map.CourseLayout;
import com.ccs.voxelstrike.ui.MenuActivity;
import com.google.android.material.materialswitch.MaterialSwitch;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MovementInstrumentedTest {
    @Test public void personalBestsPersistAndDifferentRuleSetsStaySeparate() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // Reserved synthetic masks isolate test data from every real player's record.
        int mask = 1 << 29, other = mask | MatchModifier.BUNNY_HOP.bit;
        var prefs = context.getSharedPreferences("voxelstrike_movement_records", Context.MODE_PRIVATE);
        String firstKey = "v" + CourseLayout.VERSION + "_rules_" + mask;
        String otherKey = "v" + CourseLayout.VERSION + "_rules_" + other;
        prefs.edit().remove(firstKey).remove(otherKey).commit();
        try {
            MovementRecords records = new MovementRecords(context);
            records.saveBest(mask, 15000);
            records.saveBest(mask, 18000);
            records.saveBest(other, 12000);
            assertEquals(15000, new MovementRecords(context).best(mask));
            assertEquals(12000, new MovementRecords(context).best(other));
            records.saveBest(mask, 14000);
            assertEquals(14000, new MovementRecords(context).best(mask));
        } finally { prefs.edit().remove(firstKey).remove(otherKey).commit(); }
    }

    @Test public void trainingShowsFixedCourseAndKeepsBhopAsModeModifier() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        GameSettings previous = new GameSettings(context);
        GameSettings settings = new GameSettings(context);
        settings.lastMode = GameMode.MOVEMENT_TRAINING.ordinal();
        settings.matchModifiers = MatchModifier.BUNNY_HOP.bit;
        settings.save();
        try (ActivityScenario<MenuActivity> scenario = ActivityScenario.launch(MenuActivity.class)) {
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.button_play).performClick();
                LinearLayout maps = activity.findViewById(R.id.map_container);
                assertEquals(View.GONE, maps.getChildAt(0).getVisibility());
                assertEquals("Movement Course", maps.getChildAt(1).getContentDescription());
                assertEquals(View.GONE, activity.findViewById(R.id.legacy_bot_options).getVisibility());
                MaterialSwitch bhop = activity.findViewById(R.id.mode_container)
                        .findViewWithTag(MatchModifier.BUNNY_HOP);
                assertTrue(bhop.isEnabled());
                assertTrue(bhop.isChecked());
            });
        } finally { previous.save(); }
    }
}
