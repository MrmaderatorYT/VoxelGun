package com.ccs.voxelstrike.app;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Player-customised HUD.
 *
 * <p>Every control and readout — action buttons, weapon slots, movement stick, fire zone, ammo and
 * vitals — carries its own size multiplier and an offset from the stock position. Offsets are
 * stored as a fraction of the screen so a layout arranged on one device still lands correctly on
 * another.
 */
public final class HudLayout {
    /** Must match the button count in {@code HudView}. */
    public static final int BUTTON_COUNT = 12;
    /** Buttons, the movement controls, then the two informational HUD blocks. */
    public static final int ITEM_COUNT = BUTTON_COUNT + 4;
    public static final int STICK = BUTTON_COUNT;
    /** The invisible rectangle in the corner that shoots when touched. */
    public static final int FIRE_ZONE = BUTTON_COUNT + 1;
    /** Weapon name, ammo/reserve and the ALL WEAPONS / buy-state caption. */
    public static final int WEAPON_INFO = BUTTON_COUNT + 2;
    /** Health number plus health and armour bars. */
    public static final int VITALS = BUTTON_COUNT + 3;

    public static final float MIN_SCALE = 0.7f;
    public static final float MAX_SCALE = 1.6f;
    public static final float MIN_ITEM_SCALE = 0.5f;
    public static final float MAX_ITEM_SCALE = 2.0f;

    private static final String FILE = "voxelstrike_hud";
    private static final String KEY_SCALE = "scale";
    private static final String KEY_OPACITY = "opacity";
    private static final String KEY_OFFSET_X = "ox";
    private static final String KEY_OFFSET_Y = "oy";
    private static final String KEY_ITEM_SCALE = "is";
    private static final String KEY_STATIC_JOYSTICK = "static_joystick";
    private static final String KEY_VERSION = "version";
    /** Bumped when an item index changes meaning, so saved layouts can be moved across. */
    private static final int VERSION = 6;

    private final SharedPreferences preferences;

    /** Global multiplier applied to every control. */
    public float scale = 1f;
    /** 0.35..1 multiplier on the button artwork alpha. */
    public float opacity = 1f;
    public final float[] offsetX = new float[ITEM_COUNT];
    public final float[] offsetY = new float[ITEM_COUNT];
    /** Per-control size, on top of {@link #scale}. */
    public final float[] itemScale = new float[ITEM_COUNT];
    /** Fixed centre when true; touch-down-relative floating origin when false. */
    public boolean staticJoystick;

    public HudLayout(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
        load();
    }

    private void load() {
        migrate();
        scale = clamp(preferences.getFloat(KEY_SCALE, 1f), MIN_SCALE, MAX_SCALE);
        opacity = clamp(preferences.getFloat(KEY_OPACITY, 1f), 0.35f, 1f);
        staticJoystick = preferences.getBoolean(KEY_STATIC_JOYSTICK, false);
        for (int i = 0; i < ITEM_COUNT; i++) {
            offsetX[i] = preferences.getFloat(KEY_OFFSET_X + i, 0f);
            offsetY[i] = preferences.getFloat(KEY_OFFSET_Y + i, 0f);
            itemScale[i] = clamp(preferences.getFloat(KEY_ITEM_SCALE + i, 1f), MIN_ITEM_SCALE,
                    MAX_ITEM_SCALE);
        }
    }

    /** Moves old item indices forward and repairs defaults whose handedness was wrong. */
    private void migrate() {
        int stored = preferences.getInt(KEY_VERSION, 1);
        if (stored >= VERSION) {
            return;
        }
        SharedPreferences.Editor editor = preferences.edit();
        if (stored < 2) {
            // Version 2 inserted the arsenal button at index 11, where the stick used to live.
            int oldStick = 11;
            editor.putFloat(KEY_OFFSET_X + STICK,
                    preferences.getFloat(KEY_OFFSET_X + oldStick, 0f));
            editor.putFloat(KEY_OFFSET_Y + STICK,
                    preferences.getFloat(KEY_OFFSET_Y + oldStick, 0f));
            editor.putFloat(KEY_ITEM_SCALE + STICK,
                    preferences.getFloat(KEY_ITEM_SCALE + oldStick, 1f));
            editor.remove(KEY_OFFSET_X + oldStick);
            editor.remove(KEY_OFFSET_Y + oldStick);
            editor.remove(KEY_ITEM_SCALE + oldStick);
        }
        if (stored < 4) {
            // Version 4 replaced the independent 1/2/KNIFE circles with one weapon dock.
            int[] changed = {HudView_BUTTON_SLOT_MELEE, HudView_BUTTON_SLOT_PRIMARY,
                    HudView_BUTTON_SLOT_SECONDARY};
            for (int index : changed) {
                editor.remove(KEY_OFFSET_X + index);
                editor.remove(KEY_OFFSET_Y + index);
                editor.remove(KEY_ITEM_SCALE + index);
            }
        }
        if (stored < 5) {
            // Version 5 moves the stock Fire zone from right to left. Only that control needs a
            // fresh base-relative position; all other personal HUD edits remain untouched.
            editor.remove(KEY_OFFSET_X + FIRE_ZONE);
            editor.remove(KEY_OFFSET_Y + FIRE_ZONE);
            editor.remove(KEY_ITEM_SCALE + FIRE_ZONE);
        }
        editor.putInt(KEY_VERSION, VERSION);
        editor.apply();
    }

    public void save() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(KEY_VERSION, VERSION);
        editor.putFloat(KEY_SCALE, scale);
        editor.putFloat(KEY_OPACITY, opacity);
        editor.putBoolean(KEY_STATIC_JOYSTICK, staticJoystick);
        for (int i = 0; i < ITEM_COUNT; i++) {
            editor.putFloat(KEY_OFFSET_X + i, offsetX[i]);
            editor.putFloat(KEY_OFFSET_Y + i, offsetY[i]);
            editor.putFloat(KEY_ITEM_SCALE + i, itemScale[i]);
        }
        editor.apply();
    }

    public void reset() {
        scale = 1f;
        opacity = 1f;
        staticJoystick = false;
        for (int i = 0; i < ITEM_COUNT; i++) {
            offsetX[i] = 0f;
            offsetY[i] = 0f;
            itemScale[i] = 1f;
        }
        save();
    }

    /** Restores one control to its stock size and position. */
    public void resetItem(int index) {
        if (index < 0 || index >= ITEM_COUNT) {
            return;
        }
        offsetX[index] = 0f;
        offsetY[index] = 0f;
        itemScale[index] = 1f;
        save();
    }

    /**
     * Ready-made arrangements.
     *
     * <p>The stock layout stacks the action buttons into the bottom-right corner, which overlaps
     * badly once the global size goes up — and rebuilding a custom arrangement by hand is a long
     * job, as anyone who has lost one knows. Offsets are fractions of the screen, so a preset
     * lands the same way on any device.
     */
    public enum Preset {
        STOCK("Stock"),
        SPREAD("Spread"),
        COMPACT("Compact");

        private final String label;

        Preset(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Applies a preset over the current layout, replacing every per-control offset and size. */
    public void apply(Preset preset) {
        for (int i = 0; i < ITEM_COUNT; i++) {
            offsetX[i] = 0f;
            offsetY[i] = 0f;
            itemScale[i] = 1f;
        }
        switch (preset) {
            case SPREAD:
                // Walks the right-hand column up the edge so nothing overlaps at a large size.
                scale = 1.15f;
                move(HudView_BUTTON_AIM, -0.02f, -0.05f);
                move(HudView_BUTTON_RELOAD, 0.00f, -0.14f);
                move(HudView_BUTTON_GRENADE, 0.00f, -0.34f);
                move(HudView_BUTTON_CROUCH, -0.06f, 0.00f);
                move(HudView_BUTTON_JUMP, -0.02f, -0.02f);
                itemScale[HudView_BUTTON_FIRE] = 1.15f;
                itemScale[STICK] = 1.10f;
                break;
            case COMPACT:
                // Everything pulled in and shrunk, for smaller screens or smaller hands.
                scale = 0.85f;
                move(HudView_BUTTON_RELOAD, 0.00f, -0.06f);
                move(HudView_BUTTON_GRENADE, 0.00f, -0.18f);
                itemScale[HudView_BUTTON_FIRE] = 0.95f;
                break;
            case STOCK:
            default:
                scale = 1f;
                break;
        }
        save();
    }

    /** How many arrangements the player can park and come back to. */
    public static final int SLOT_COUNT = 3;

    /** Stores the current arrangement in a slot, so an experiment never costs the old one. */
    public void saveSlot(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return;
        }
        SharedPreferences.Editor editor = preferences.edit();
        String prefix = "slot" + slot + "_";
        editor.putBoolean(prefix + "used", true);
        editor.putInt(prefix + KEY_VERSION, VERSION);
        editor.putFloat(prefix + KEY_SCALE, scale);
        editor.putFloat(prefix + KEY_OPACITY, opacity);
        editor.putBoolean(prefix + KEY_STATIC_JOYSTICK, staticJoystick);
        for (int i = 0; i < ITEM_COUNT; i++) {
            editor.putFloat(prefix + KEY_OFFSET_X + i, offsetX[i]);
            editor.putFloat(prefix + KEY_OFFSET_Y + i, offsetY[i]);
            editor.putFloat(prefix + KEY_ITEM_SCALE + i, itemScale[i]);
        }
        editor.apply();
    }

    /** Restores a slot. Returns false when nothing was ever stored there. */
    public boolean loadSlot(int slot) {
        if (!hasSlot(slot)) {
            return false;
        }
        String prefix = "slot" + slot + "_";
        int slotVersion = preferences.getInt(prefix + KEY_VERSION, 1);
        boolean legacyWeaponDock = slotVersion < 4;
        boolean legacyFireZone = slotVersion < 5;
        scale = clamp(preferences.getFloat(prefix + KEY_SCALE, 1f), MIN_SCALE, MAX_SCALE);
        opacity = clamp(preferences.getFloat(prefix + KEY_OPACITY, 1f), 0.35f, 1f);
        staticJoystick = preferences.getBoolean(prefix + KEY_STATIC_JOYSTICK, false);
        for (int i = 0; i < ITEM_COUNT; i++) {
            offsetX[i] = preferences.getFloat(prefix + KEY_OFFSET_X + i, 0f);
            offsetY[i] = preferences.getFloat(prefix + KEY_OFFSET_Y + i, 0f);
            itemScale[i] = clamp(preferences.getFloat(prefix + KEY_ITEM_SCALE + i, 1f),
                    MIN_ITEM_SCALE, MAX_ITEM_SCALE);
        }
        if (legacyFireZone) {
            offsetX[FIRE_ZONE] = 0f;
            offsetY[FIRE_ZONE] = 0f;
            itemScale[FIRE_ZONE] = 1f;
        }
        if (legacyWeaponDock) {
            offsetX[HudView_BUTTON_SLOT_MELEE] = 0f;
            offsetY[HudView_BUTTON_SLOT_MELEE] = 0f;
            itemScale[HudView_BUTTON_SLOT_MELEE] = 1f;
            offsetX[HudView_BUTTON_SLOT_PRIMARY] = 0f;
            offsetY[HudView_BUTTON_SLOT_PRIMARY] = 0f;
            itemScale[HudView_BUTTON_SLOT_PRIMARY] = 1f;
            offsetX[HudView_BUTTON_SLOT_SECONDARY] = 0f;
            offsetY[HudView_BUTTON_SLOT_SECONDARY] = 0f;
            itemScale[HudView_BUTTON_SLOT_SECONDARY] = 1f;
        }
        save();
        return true;
    }

    public boolean hasSlot(int slot) {
        return slot >= 0 && slot < SLOT_COUNT
                && preferences.getBoolean("slot" + slot + "_used", false);
    }

    private void move(int index, float fractionX, float fractionY) {
        offsetX[index] = fractionX;
        offsetY[index] = fractionY;
    }

    // Button indices, mirrored from HudView so the presets can name what they move. Kept here
    // rather than imported because this class must not depend on the view layer.
    private static final int HudView_BUTTON_AIM = 1;
    private static final int HudView_BUTTON_JUMP = 2;
    private static final int HudView_BUTTON_CROUCH = 3;
    private static final int HudView_BUTTON_RELOAD = 4;
    private static final int HudView_BUTTON_SLOT_MELEE = 5;
    private static final int HudView_BUTTON_GRENADE = 6;
    private static final int HudView_BUTTON_SLOT_PRIMARY = 7;
    private static final int HudView_BUTTON_SLOT_SECONDARY = 8;
    private static final int HudView_BUTTON_FIRE = 0;

    public void setScale(float value) {
        scale = clamp(value, MIN_SCALE, MAX_SCALE);
    }

    public void setItemScale(int index, float value) {
        if (index < 0 || index >= ITEM_COUNT) {
            return;
        }
        itemScale[index] = clamp(value, MIN_ITEM_SCALE, MAX_ITEM_SCALE);
    }

    public float itemScale(int index) {
        return index < 0 || index >= ITEM_COUNT ? 1f : itemScale[index];
    }

    public void moveItem(int index, float deltaFractionX, float deltaFractionY) {
        if (index < 0 || index >= ITEM_COUNT) {
            return;
        }
        offsetX[index] = clamp(offsetX[index] + deltaFractionX, -0.9f, 0.9f);
        offsetY[index] = clamp(offsetY[index] + deltaFractionY, -0.9f, 0.9f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
