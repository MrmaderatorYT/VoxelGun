package com.ccs.voxelgun;

import android.app.Instrumentation;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.ViewFlipper;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.ccs.voxelgun.app.GameSettings;
import com.ccs.voxelgun.game.GameMode;
import com.ccs.voxelgun.game.MatchModifier;
import com.ccs.voxelgun.ui.MenuActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.materialswitch.MaterialSwitch;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/** Exercises the actual menu controls without clearing player preferences or touching key binds. */
@RunWith(AndroidJUnit4.class)
public final class MenuPolishInstrumentedTest {
    private static final int[] IDLE_CHECKED = {
            android.R.attr.state_enabled, android.R.attr.state_checked
    };
    private static final int[] IDLE_UNCHECKED = {android.R.attr.state_enabled};

    @Test public void noBotsDisablesBotOptionsWithoutDiscardingSelections() {
        Context context = context();
        GameSettings original = new GameSettings(context);
        prepareArenaSettings(context);
        try (ActivityScenario<MenuActivity> scenario = ActivityScenario.launch(MenuActivity.class)) {
            openPlay(scenario);
            scenario.onActivity(activity -> {
                GameSettings before = new GameSettings(activity);
                MaterialSwitch solo = activity.findViewById(R.id.mode_container)
                        .findViewWithTag(MatchModifier.NO_BOTS);
                assertNotNull(solo);
                solo.setChecked(true);
                assertFalse(activity.findViewById(R.id.slider_bots).isEnabled());
                assertFalse(activity.findViewById(R.id.slider_battlefield_players).isEnabled());
                MaterialButtonToggleGroup group = activity.findViewById(R.id.difficulty_group);
                for (int i = 0; i < group.getChildCount(); i++) assertFalse(group.getChildAt(i).isEnabled());
                GameSettings enabled = new GameSettings(activity);
                assertTrue((enabled.matchModifiers & MatchModifier.NO_BOTS.bit) != 0);
                assertEquals(before.lastBotCount, enabled.lastBotCount);
                assertEquals(before.battlefieldTotalPlayers, enabled.battlefieldTotalPlayers);
                assertEquals(before.lastDifficulty, enabled.lastDifficulty);
                solo.setChecked(false);
                assertTrue(activity.findViewById(R.id.slider_bots).isEnabled());
                for (int i = 0; i < group.getChildCount(); i++) assertTrue(group.getChildAt(i).isEnabled());
                assertEquals(before.lastDifficulty, new GameSettings(activity).lastDifficulty);
            });
        } finally { original.save(); }
    }

    @Test public void backLabelsStayOnOneLineIncludingLongText() {
        Context context = context();
        GameSettings original = new GameSettings(context);
        prepareArenaSettings(context);
        try (ActivityScenario<MenuActivity> scenario = ActivityScenario.launch(MenuActivity.class)) {
            openPlay(scenario);
            scenario.onActivity(activity -> {
                MaterialButton back = activity.findViewById(R.id.play_back);
                assertEquals(1, back.getMaxLines());
                assertNotNull(back.getLayout());
                assertEquals(1, back.getLineCount());
                assertEquals(dp(activity, 18), back.getIconSize());
                assertTrue(back.getPaddingStart() <= dp(activity, 8));
                assertTrue(back.getPaddingEnd() <= dp(activity, 8));
                assertTrue(back.getHeight() >= dp(activity, 48));
                back.setText("Back to the previous menu without wrapping the final letter");
            });
            instrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                MaterialButton back = activity.findViewById(R.id.play_back);
                assertNotNull(back.getLayout());
                assertEquals("A long label must remain a single navigation line", 1,
                        back.getLineCount());
            });
        } finally {
            // Restore every GameSettings field only after ActivityScenario has been closed.
            original.save();
        }
    }

    @Test public void difficultySelectionIsVisibleAndPersistsAfterRecreation() {
        Context context = context();
        GameSettings original = new GameSettings(context);
        prepareArenaSettings(context);
        try (ActivityScenario<MenuActivity> scenario = ActivityScenario.launch(MenuActivity.class)) {
            openPlay(scenario);
            scenario.onActivity(activity -> {
                MaterialButtonToggleGroup group = activity.findViewById(R.id.difficulty_group);
                assertTrue(group.getChildCount() > 1);
                MaterialButton selected = (MaterialButton) group.getChildAt(1);
                MaterialButton other = (MaterialButton) group.getChildAt(0);
                // MaterialButton toggles before super.performClick(); without an OnClickListener
                // its return value is false even though the group's checked listener ran.
                selected.performClick();
                assertTrue(selected.isChecked());
                assertFalse(other.isChecked());
                assertEquals(selected.getId(), group.getCheckedButtonId());
                assertEquals(1, new GameSettings(activity).lastDifficulty);
                assertExactlyOneChecked(group);

                // Compare idle states, not the transient press/focus colors from a click.
                ColorStateList selectedBackground = selected.getBackgroundTintList();
                ColorStateList otherBackground = other.getBackgroundTintList();
                assertNotNull(selectedBackground);
                assertNotNull(otherBackground);
                assertNotEquals("Checked background must remain visibly selected after release",
                        idleColor(selectedBackground, true), idleColor(otherBackground, false));
                assertNotEquals("Checked label must contrast with the unselected label",
                        idleColor(selected.getTextColors(), true),
                        idleColor(other.getTextColors(), false));
            });
            scenario.recreate();
            openPlay(scenario);
            scenario.onActivity(activity -> {
                MaterialButtonToggleGroup group = activity.findViewById(R.id.difficulty_group);
                MaterialButton selected = (MaterialButton) group.getChildAt(1);
                assertTrue(selected.isChecked());
                assertEquals(selected.getId(), group.getCheckedButtonId());
                assertEquals(1, new GameSettings(activity).lastDifficulty);
                assertExactlyOneChecked(group);
            });
        } finally {
            original.save();
        }
    }

    @Test public void selectorArrowsAcceptTouchesNearTopAndBottomOfTheirTargets() {
        Context context = context();
        GameSettings original = new GameSettings(context);
        prepareArenaSettings(context);
        try (ActivityScenario<MenuActivity> scenario = ActivityScenario.launch(MenuActivity.class)) {
            openPlay(scenario);
            scenario.onActivity(activity -> {
                assertSelectorTouchTargets(activity, R.id.mode_container);
                assertSelectorTouchTargets(activity, R.id.map_container);
            });
            exerciseArrowEdges(scenario, R.id.mode_container);
            exerciseArrowEdges(scenario, R.id.map_container);
        } finally {
            original.save();
        }
    }

    @Test public void mainScreenDoesNotShowOfflineBuildBadge() {
        Context context = context();
        GameSettings original = new GameSettings(context);
        prepareArenaSettings(context);
        try (ActivityScenario<MenuActivity> scenario = ActivityScenario.launch(MenuActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals(0, ((ViewFlipper) activity.findViewById(R.id.flipper))
                        .getDisplayedChild());
                // Look up dynamically: deleting the view can also remove the generated R.id.
                int statusId = activity.getResources().getIdentifier("menu_status", "id",
                        activity.getPackageName());
                if (statusId != 0) assertNull(activity.findViewById(statusId));
            });
        } finally {
            original.save();
        }
    }

    private static void prepareArenaSettings(Context context) {
        GameSettings prepared = new GameSettings(context);
        prepared.lastMode = GameMode.TEAM_DEATHMATCH.ordinal();
        prepared.lastArenaMap = prepared.lastMap = 0;
        prepared.lastDifficulty = 0;
        prepared.matchModifiers = 0;
        prepared.save();
    }

    private static void openPlay(ActivityScenario<MenuActivity> scenario) {
        scenario.onActivity(activity -> {
            assertTrue(activity.findViewById(R.id.button_play).performClick());
            // Finish the cosmetic entrance so event coordinates are deterministic.
            View page = ((ViewFlipper) activity.findViewById(R.id.flipper)).getCurrentView();
            page.animate().cancel();
            page.setAlpha(1f);
            page.setTranslationX(0f);
        });
        instrumentation().waitForIdleSync();
    }

    private static void assertExactlyOneChecked(MaterialButtonToggleGroup group) {
        int checked = 0;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (((MaterialButton) group.getChildAt(i)).isChecked()) checked++;
        }
        assertEquals(1, checked);
    }

    private static int idleColor(ColorStateList colors, boolean checked) {
        return colors.getColorForState(checked ? IDLE_CHECKED : IDLE_UNCHECKED,
                colors.getDefaultColor());
    }

    private static void assertSelectorTouchTargets(MenuActivity activity, int containerId) {
        View container = activity.findViewById(containerId);
        for (int arrowId : new int[]{R.id.selector_previous, R.id.selector_next}) {
            View arrow = container.findViewById(arrowId);
            assertTrue(arrow.isShown());
            assertTrue("Arrow width must offer a full finger target",
                    arrow.getWidth() >= dp(activity, 48));
            assertTrue("Arrow height must offer a full finger target",
                    arrow.getHeight() >= dp(activity, 48));
        }
    }

    private static void exerciseArrowEdges(ActivityScenario<MenuActivity> scenario,
                                          int containerId) {
        String[] choices = new String[3];
        scenario.onActivity(activity -> choices[0] = selectorText(activity, containerId));

        tapSelectorEdge(scenario, containerId, R.id.selector_next, true);
        scenario.onActivity(activity -> {
            choices[1] = selectorText(activity, containerId);
            assertNotEquals("Top-edge touch must advance the selector", choices[0], choices[1]);
        });
        tapSelectorEdge(scenario, containerId, R.id.selector_next, false);
        scenario.onActivity(activity -> {
            choices[2] = selectorText(activity, containerId);
            assertNotEquals("Bottom-edge touch must advance the selector", choices[1], choices[2]);
        });
        tapSelectorEdge(scenario, containerId, R.id.selector_previous, true);
        scenario.onActivity(activity -> assertEquals(
                "Previous top edge must go back, not advance", choices[1],
                selectorText(activity, containerId)));
        tapSelectorEdge(scenario, containerId, R.id.selector_previous, false);
        scenario.onActivity(activity -> assertEquals(
                "Previous bottom edge must restore the initial choice", choices[0],
                selectorText(activity, containerId)));
    }

    private static String selectorText(MenuActivity activity, int containerId) {
        TextView value = activity.findViewById(containerId).findViewById(R.id.selector_value);
        return value.getText().toString();
    }

    private static void tapSelectorEdge(ActivityScenario<MenuActivity> scenario, int containerId,
                                        int arrowId, boolean nearTop) {
        scenario.onActivity(activity -> tapArrowEdge(activity,
                activity.findViewById(containerId).findViewById(arrowId), nearTop));
        // View touch handling may post performClick instead of invoking it inline.
        instrumentation().waitForIdleSync();
    }

    /** Sends DOWN/UP through the Activity window, so actual child hit testing is exercised. */
    private static void tapArrowEdge(MenuActivity activity, View arrow, boolean nearTop) {
        int[] location = new int[2];
        arrow.getLocationInWindow(location);
        float x = location[0] + arrow.getWidth() * 0.5f;
        int inset = dp(activity, 2);
        float y = location[1] + (nearTop ? inset : arrow.getHeight() - inset);
        long downTime = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN,
                x, y, 0);
        MotionEvent up = MotionEvent.obtain(downTime, downTime + 16,
                MotionEvent.ACTION_UP, x, y, 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try {
            assertTrue(activity.dispatchTouchEvent(down));
            assertTrue(activity.dispatchTouchEvent(up));
        } finally {
            down.recycle();
            up.recycle();
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static Context context() {
        return instrumentation().getTargetContext();
    }

    private static Instrumentation instrumentation() {
        return InstrumentationRegistry.getInstrumentation();
    }
}
