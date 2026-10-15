package com.ccs.voxelgun;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.widget.ViewFlipper;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.ccs.voxelgun.app.GameSettings;
import com.ccs.voxelgun.game.MatchModifier;
import com.ccs.voxelgun.ui.MenuActivity;
import com.google.android.material.materialswitch.MaterialSwitch;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MenuInputInstrumentedTest {
    @Test public void bunnyHopMatchModifierPersistsAcrossMenuRecreation() {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        int previousMask = new GameSettings(context).matchModifiers;
        boolean previous = (previousMask & MatchModifier.BUNNY_HOP.bit) != 0;
        try (ActivityScenario<MenuActivity> scenario = ActivityScenario.launch(MenuActivity.class)) {
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.button_play).performClick();
                MaterialSwitch toggle = activity.findViewById(R.id.mode_container)
                        .findViewWithTag(MatchModifier.BUNNY_HOP);
                assertEquals(previous, toggle.isChecked());
                toggle.setChecked(!previous);
                assertEquals(!previous, (new GameSettings(activity).matchModifiers
                        & MatchModifier.BUNNY_HOP.bit) != 0);
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.button_play).performClick();
                MaterialSwitch toggle = activity.findViewById(R.id.mode_container)
                        .findViewWithTag(MatchModifier.BUNNY_HOP);
                assertEquals(!previous, toggle.isChecked());
            });
        } finally {
            GameSettings settings = new GameSettings(context);
            settings.matchModifiers = previousMask;
            settings.save();
        }
    }

    @Test public void escapeNavigatesBackButNeverClosesTitleScreen() {
        try (ActivityScenario<MenuActivity> scenario = ActivityScenario.launch(MenuActivity.class)) {
            scenario.onActivity(activity -> {
                for (int source : new int[]{InputDevice.SOURCE_UNKNOWN, InputDevice.SOURCE_KEYBOARD}) {
                    ViewFlipper pages = activity.findViewById(R.id.flipper);
                    activity.findViewById(R.id.button_settings).performClick();
                    assertNotEquals(0, pages.getDisplayedChild());
                    for (int action : new int[]{KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
                        KeyEvent event = new KeyEvent(action, KeyEvent.KEYCODE_ESCAPE);
                        event.setSource(source);
                        assertTrue(activity.dispatchKeyEvent(event));
                    }
                    assertEquals(0, pages.getDisplayedChild());
                    assertFalse(activity.isFinishing());
                    assertTrue(activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE)));
                    assertTrue(activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE)));
                    assertFalse(activity.isFinishing());
                }
            });
        }
    }
}
