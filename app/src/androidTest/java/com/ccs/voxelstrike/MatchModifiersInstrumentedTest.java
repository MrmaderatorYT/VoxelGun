package com.ccs.voxelstrike;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.ccs.voxelstrike.app.GameSettings;
import com.ccs.voxelstrike.game.GameMode;
import com.ccs.voxelstrike.game.MatchModifier;
import com.ccs.voxelstrike.ui.MenuActivity;
import com.google.android.material.materialswitch.MaterialSwitch;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MatchModifiersInstrumentedTest {
    @Test public void allEightNewSwitchesExistAndPersistAfterRecreation() {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        GameSettings previous = new GameSettings(context), settings = new GameSettings(context);
        settings.lastMode = GameMode.TEAM_DEATHMATCH.ordinal();
        settings.matchModifiers = 0;
        settings.save();
        MatchModifier[] added = {MatchModifier.NO_FALL_DAMAGE, MatchModifier.DOUBLE_JUMP,
                MatchModifier.VAMPIRE, MatchModifier.INFINITE_AMMO, MatchModifier.NO_RELOAD,
                MatchModifier.EXPLOSIVE_DEATH, MatchModifier.MIRROR_LOADOUT, MatchModifier.HARDCORE_HUD};
        try (ActivityScenario<MenuActivity> scenario = ActivityScenario.launch(MenuActivity.class)) {
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.button_play).performClick();
                for (MatchModifier modifier : added) {
                    MaterialSwitch control = activity.findViewById(R.id.mode_container).findViewWithTag(modifier);
                    assertNotNull(control); assertTrue(control.isEnabled());
                    control.setChecked(true);
                    assertTrue((new GameSettings(activity).matchModifiers & modifier.bit) != 0);
                }
                MaterialSwitch random = activity.findViewById(R.id.mode_container).findViewWithTag(MatchModifier.RANDOM_WEAPONS);
                random.setChecked(true);
                MaterialSwitch mirror = activity.findViewById(R.id.mode_container).findViewWithTag(MatchModifier.MIRROR_LOADOUT);
                assertFalse(mirror.isChecked());
                mirror.setChecked(true);
                assertFalse(random.isChecked());
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                for (MatchModifier modifier : added) {
                    MaterialSwitch control = activity.findViewById(R.id.mode_container).findViewWithTag(modifier);
                    assertTrue(control.isChecked());
                }
            });
        } finally { previous.save(); }
    }
}
