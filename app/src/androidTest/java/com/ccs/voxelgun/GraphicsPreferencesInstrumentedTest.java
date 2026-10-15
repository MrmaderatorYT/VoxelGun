package com.ccs.voxelgun;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.core.app.ActivityScenario;
import androidx.lifecycle.Lifecycle;
import com.ccs.voxelgun.app.GameSettings;
import com.ccs.voxelgun.render.RenderSettings;
import com.ccs.voxelgun.ui.MenuActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.slider.Slider;
import org.junit.Test;
import static org.junit.Assert.*;

public class GraphicsPreferencesInstrumentedTest {
    @Test public void resumedMenuReloadsMatchGraphicsAndShowsCheckedState() {
        Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
        GameSettings original = new GameSettings(base);
        try (ActivityScenario<MenuActivity> scenario = ActivityScenario.launch(MenuActivity.class)) {
            scenario.moveToState(Lifecycle.State.CREATED);
            // Simulate settings saved by a separate MatchActivity while the menu is stopped.
            GameSettings match = new GameSettings(base);
            match.gamma = 1.35f;
            match.applyPreset(RenderSettings.Quality.HIGH);
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> {
                MaterialButtonToggleGroup group = activity.findViewById(R.id.quality_group);
                assertEquals(RenderSettings.Quality.values().length, group.getChildCount());
                MaterialButton selected = group.findViewById(group.getCheckedButtonId());
                assertEquals(RenderSettings.Quality.HIGH.ordinal(), selected.getTag());
                assertTrue(selected.isChecked());
                int[] checked = {android.R.attr.state_enabled, android.R.attr.state_checked};
                int[] unchecked = {android.R.attr.state_enabled};
                assertNotEquals(selected.getBackgroundTintList().getColorForState(checked, 0),
                        selected.getBackgroundTintList().getColorForState(unchecked, 0));
                Slider gamma = activity.findViewById(R.id.slider_gamma);
                assertEquals(1.35f, gamma.getValue(), .0001f);
                // Another setting save must not write the pre-match graphics over the new ones.
                Slider volume = activity.findViewById(R.id.slider_volume);
                volume.setValue(.2f);
                GameSettings saved = new GameSettings(activity);
                assertEquals(RenderSettings.Quality.HIGH, saved.quality);
                assertEquals(1.35f, saved.gamma, .0001f);
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                Slider gamma = activity.findViewById(R.id.slider_gamma);
                assertEquals(1.35f, gamma.getValue(), .0001f);
            });
        } finally { original.save(); }
    }

    @Test public void matchChangesPersistAcrossSettingsInstancesAndPresetsKeepGamma() {
        Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String file = "graphics_preferences_regression_test";
        base.deleteSharedPreferences(file);
        Context isolated = new ContextWrapper(base) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return base.getSharedPreferences(file, mode);
            }
        };
        try {
            GameSettings match = new GameSettings(isolated);
            match.gamma = 1.35f;
            match.applyPreset(RenderSettings.Quality.HIGH);
            GameSettings menu = new GameSettings(isolated);
            assertEquals(RenderSettings.Quality.HIGH, menu.quality);
            assertEquals(1.35f, menu.gamma, .0001f);
            assertEquals(1.35f, menu.toRenderSettings().gamma, .0001f);
            menu.frameRateCap = 30; menu.save();
            GameSettings nextMatch = new GameSettings(isolated);
            assertEquals(RenderSettings.Quality.HIGH, nextMatch.quality);
            assertEquals(1.35f, nextMatch.gamma, .0001f);
        } finally { base.deleteSharedPreferences(file); }
    }
}
