package com.ccs.voxelgun.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

/**
 * Keyboard bindings for players on a physical keyboard — Chromebooks, DeX, tablets with a case
 * keyboard, emulators. Every action can be rebound and is persisted by key code.
 *
 * <p>Mouse buttons are handled separately and always work: left fires, right aims.
 */
public final class KeyBindings {
    /** One rebindable action. The order is the order shown in the settings list. */
    public enum Action {
        FORWARD("Move forward", KeyEvent.KEYCODE_W),
        BACK("Move back", KeyEvent.KEYCODE_S),
        LEFT("Strafe left", KeyEvent.KEYCODE_A),
        RIGHT("Strafe right", KeyEvent.KEYCODE_D),
        JUMP("Jump", KeyEvent.KEYCODE_SPACE),
        CROUCH("Crouch / Slide", KeyEvent.KEYCODE_CTRL_LEFT),
        SPRINT("Sprint / Bhop slide", KeyEvent.KEYCODE_SHIFT_LEFT),
        FIRE("Fire", KeyEvent.KEYCODE_ENTER),
        AIM("Aim", KeyEvent.KEYCODE_Z),
        RELOAD("Reload", KeyEvent.KEYCODE_R),
        MELEE("Melee", KeyEvent.KEYCODE_V),
        GRENADE("Grenade", KeyEvent.KEYCODE_G),
        SLOT_PRIMARY("Primary weapon", KeyEvent.KEYCODE_1),
        SLOT_SECONDARY("Secondary weapon", KeyEvent.KEYCODE_2),
        BUY("Buy menu", KeyEvent.KEYCODE_B),
        SCOREBOARD("Scoreboard", KeyEvent.KEYCODE_TAB),
        PAUSE("Pause", KeyEvent.KEYCODE_ESCAPE),
        COURSE_RESTART("Restart movement course", KeyEvent.KEYCODE_T);

        private final String label;
        private final int defaultKey;

        Action(String label, int defaultKey) {
            this.label = label;
            this.defaultKey = defaultKey;
        }

        public String label() {
            return label;
        }

        public int defaultKey() {
            return defaultKey;
        }
    }

    private static final String FILE = "voxelgun_keys";
    private static final String KEY_MOUSE_SENSITIVITY = "mouse_sensitivity";
    private static final String KEY_INVERT_Y = "invert_y";

    private final SharedPreferences preferences;
    private final int[] keys = new int[Action.values().length];

    public float mouseSensitivity = 1f;
    public boolean invertY;

    public KeyBindings(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
        load();
    }

    private void load() {
        for (Action action : Action.values()) {
            keys[action.ordinal()] = preferences.getInt(action.name(), action.defaultKey());
        }
        mouseSensitivity = preferences.getFloat(KEY_MOUSE_SENSITIVITY, 1f);
        invertY = preferences.getBoolean(KEY_INVERT_Y, false);
    }

    public void save() {
        SharedPreferences.Editor editor = preferences.edit();
        for (Action action : Action.values()) {
            editor.putInt(action.name(), keys[action.ordinal()]);
        }
        editor.putFloat(KEY_MOUSE_SENSITIVITY, mouseSensitivity);
        editor.putBoolean(KEY_INVERT_Y, invertY);
        editor.apply();
    }

    public int keyFor(Action action) {
        return keys[action.ordinal()];
    }

    /** Built once when the match HUD is configured, not every rendered frame. */
    public String bhopHint() {
        int jumpKey = keyFor(Action.JUMP), sprintKey = keyFor(Action.SPRINT), crouchKey = keyFor(Action.CROUCH);
        String jump = jumpKey == 0 ? "Jump (unbound)" : describe(jumpKey);
        String slide;
        if (sprintKey != 0 && crouchKey != 0) slide = describe(sprintKey) + " / " + describe(crouchKey);
        else if (sprintKey != 0) slide = describe(sprintKey);
        else if (crouchKey != 0) slide = describe(crouchKey);
        else slide = "Crouch/Slide (unbound)";
        return jump + " → release " + jump + " → " + slide + " in air → land → " + jump + " quickly";
    }

    /** Setup-time strings share the exact lookup used to dispatch real keyboard events. */
    public String keyboardCombatHint() {
        StringBuilder hint = new StringBuilder(100);
        appendHint(hint, Action.JUMP, "Jump");
        appendHint(hint, Action.RELOAD, "Reload");
        appendHint(hint, Action.GRENADE, "Grenade");
        appendHint(hint, Action.MELEE, "Melee");
        return hint.toString();
    }

    public String keyboardUtilityHint(boolean includeShop) {
        StringBuilder hint = new StringBuilder(100);
        if (includeShop) {
            if (keyFor(Action.BUY) == 0) hint.append("Shop (unbound)");
            else appendHint(hint, Action.BUY, "Shop");
        }
        appendHint(hint, Action.SCOREBOARD, "Scores");
        if (hint.length() > 0) hint.append("  ·  ");
        // Escape is an unconditional safety shortcut in MatchActivity, even after rebinding.
        hint.append("Esc");
        int pause = keyFor(Action.PAUSE);
        if (pause != 0 && pause != KeyEvent.KEYCODE_ESCAPE) hint.append(" / ").append(describe(pause));
        return hint.append(": Pause").toString();
    }

    private void appendHint(StringBuilder hint, Action action, String label) {
        int key = keyFor(action);
        if (key == 0) return;
        if (hint.length() > 0) hint.append("  ·  ");
        hint.append(describe(key)).append(": ").append(label);
    }

    /** Only the movement-training HUD displays this hint. Bots have no player commands. */
    public String courseRestartHint() {
        StringBuilder hint = new StringBuilder(32);
        appendHint(hint, Action.COURSE_RESTART, "Restart course");
        return hint.toString();
    }

    /**
     * Binds a key to an action, clearing it from whatever else held it so two actions can never
     * fight over the same key.
     */
    public void bind(Action action, int keyCode) {
        for (Action other : Action.values()) {
            if (other != action && keys[other.ordinal()] == keyCode) {
                keys[other.ordinal()] = 0;
            }
        }
        keys[action.ordinal()] = keyCode;
        save();
    }

    /** The action a key code triggers, or null. */
    public Action actionFor(int keyCode) {
        for (Action action : Action.values()) {
            if (keys[action.ordinal()] == keyCode) {
                return action;
            }
        }
        return null;
    }

    public void reset() {
        for (Action action : Action.values()) {
            keys[action.ordinal()] = action.defaultKey();
        }
        mouseSensitivity = 1f;
        invertY = false;
        save();
    }

    /** Human readable name for a key code, for the settings list. */
    public static String describe(int keyCode) {
        if (keyCode == 0) {
            return "—";
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_SPACE:
                return "Space";
            case KeyEvent.KEYCODE_TAB:
                return "Tab";
            case KeyEvent.KEYCODE_ESCAPE:
                return "Esc";
            case KeyEvent.KEYCODE_ENTER:
                return "Enter";
            case KeyEvent.KEYCODE_SHIFT_LEFT:
                return "Left Shift";
            case KeyEvent.KEYCODE_SHIFT_RIGHT:
                return "Right Shift";
            case KeyEvent.KEYCODE_CTRL_LEFT:
                return "Left Ctrl";
            case KeyEvent.KEYCODE_CTRL_RIGHT:
                return "Right Ctrl";
            case KeyEvent.KEYCODE_ALT_LEFT:
                return "Left Alt";
            case KeyEvent.KEYCODE_ALT_RIGHT:
                return "Right Alt";
            default:
                break;
        }
        String name = KeyEvent.keyCodeToString(keyCode).replace("KEYCODE_", "");
        return name.replace('_', ' ');
    }
}
