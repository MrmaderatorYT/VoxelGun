package com.ccs.voxelgun.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.appcompat.content.res.AppCompatResources;

import com.ccs.voxelgun.R;
import com.ccs.voxelgun.app.HudLayout;
import com.ccs.voxelgun.game.GameMode;
import com.ccs.voxelgun.game.HudSnapshot;
import com.ccs.voxelgun.game.Team;
import com.ccs.voxelgun.input.InputState;
import com.ccs.voxelgun.weapon.WeaponSlot;
import com.ccs.voxelgun.weapon.WeaponType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The in-match touch layout and heads-up display, drawn entirely with Canvas primitives.
 *
 * <p>Left thumb drives movement, right thumb looks, and the action buttons sit under the right
 * hand. The layout mirrors when the left-handed option is on.
 */
public final class HudView extends View {
    private static final int BUTTON_FIRE = 0;
    private static final int BUTTON_AIM = 1;
    private static final int BUTTON_JUMP = 2;
    private static final int BUTTON_CROUCH = 3;
    private static final int BUTTON_RELOAD = 4;
    /** Persistent melee loadout slot; this replaces the old one-shot quick-knife button. */
    private static final int BUTTON_SLOT_MELEE = 5;
    private static final int BUTTON_GRENADE = 6;
    private static final int BUTTON_SLOT_PRIMARY = 7;
    private static final int BUTTON_SLOT_SECONDARY = 8;
    private static final int BUTTON_PAUSE = 9;
    private static final int BUTTON_SCOREBOARD = 10;
    /** Opens the in-round arsenal; drawn as a trolley and only live while the buy window is. */
    private static final int BUTTON_BUY = 11;
    private static final int BUTTON_COUNT = 12;
    /** The movement stick is edited like a button, as the item just past the last one. */
    public static final int ITEM_STICK = BUTTON_COUNT;
    /** The fire zone: a rectangle rather than a button, edited the same way. */
    public static final int ITEM_FIRE_ZONE = BUTTON_COUNT + 1;
    /** Ammo, reserve, weapon name and the buy/free-arsenal caption move as one readable block. */
    public static final int ITEM_WEAPON_INFO = BUTTON_COUNT + 2;
    /** Health number, health bar and armour bar move as one block. */
    public static final int ITEM_VITALS = BUTTON_COUNT + 3;
    public static final int ITEM_COUNT = BUTTON_COUNT + 4;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint hintMeasure = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint previewPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final RectF previewRect = new RectF();
    private final RectF killFeedAvoidanceBounds = new RectF();
    private final Path path = new Path();
    private final Matrix minimapMatrix = new Matrix();

    /** Stand-in state so the arrange screen can draw real weapon art without a running match. */
    private static final HudSnapshot EDITOR_STATE = createEditorState();
    private static final WeaponType[] WEAPON_TYPES = WeaponType.values();

    private static final int MAX_POINTERS = 16;
    private static final int ROLE_NONE = 0;
    private static final int ROLE_MOVE = 1;
    private static final int ROLE_LOOK = 2;
    private static final int ROLE_BUTTON = 3;

    private final float[] buttonX = new float[BUTTON_COUNT];
    private final float[] buttonY = new float[BUTTON_COUNT];
    private final float[] buttonRadius = new float[BUTTON_COUNT];
    private final float[] baseX = new float[BUTTON_COUNT];
    private final float[] baseY = new float[BUTTON_COUNT];
    private final float[] baseRadius = new float[BUTTON_COUNT];
    private final String[] buttonLabel = new String[BUTTON_COUNT];
    /** Small transparent renders used by the Standoff-style weapon dock. */
    private final Map<String, Bitmap> weaponPreviews = new HashMap<>();
    /** Android vector drawables (SVG path data) used instead of text for the core actions. */
    private Drawable fireGlyph;
    private Drawable jumpGlyph;
    private Drawable crouchGlyph;
    private KillFeedRenderer killFeedRenderer;

    /**
     * Per-pointer state. A finger keeps whatever role it was given on the way down, so a held
     * button stays held for as long as that finger is on the glass — and once it slides far
     * enough it starts steering the camera as well, without letting go of the trigger.
     */
    private final int[] pointerRole = new int[MAX_POINTERS];
    private final int[] pointerButton = new int[MAX_POINTERS];
    private final float[] pointerX = new float[MAX_POINTERS];
    private final float[] pointerY = new float[MAX_POINTERS];
    private final float[] pointerDownX = new float[MAX_POINTERS];
    private final float[] pointerDownY = new float[MAX_POINTERS];
    private final boolean[] pointerDragging = new boolean[MAX_POINTERS];

    private InputState input;
    private HudLayout layout;
    private HudSnapshot snapshot;
    private final StringBuilder movementLabel = new StringBuilder(160);
    private final char[] movementChars = new char[160];
    private boolean editMode;
    private int editButton = -1;
    private Bitmap minimap;
    private Runnable pauseListener;
    private Runnable buyListener;
    /** Set by the match while the round's buy window accepts purchases. */
    private boolean buyAvailable;
    private boolean southpaw;
    private boolean autoFire;
    /** When set, the aim button latches: tap to go behind the sight, tap again to come out. */
    private boolean aimToggle = true;
    private boolean aimLatched;
    private boolean showFps;
    /** Hidden while a keyboard and mouse are driving; the readouts stay. */
    private boolean touchControlsVisible = true;
    private static final String TOUCH_BHOP_HINT = "jump → release → crouch in air → land → jump quickly";
    private String keyboardBhopHint = TOUCH_BHOP_HINT;
    private boolean keyboardAvailable;
    private String keyboardCombatHint = "", keyboardUtilityHint = "", keyboardShopHint = "";
    private String courseRestartHint = "";
    public void setCourseRestartHint(String value) { courseRestartHint = value; invalidate(); }
    private String fittedCombatHint = "", fittedUtilityHint = "", fittedShopHint = "";
    private float density = 3f;

    /**
     * The fire zone: touching anywhere inside it pulls the trigger, and sliding from it turns the
     * view. Its stock position is the upper-left, above the movement stick. It is drawn only while
     * arranging the layout — in a match it is deliberately invisible, so the corner stays clear.
     */
    private final RectF fireZone = new RectF();
    private float fireZoneBaseWidth;
    private float fireZoneBaseHeight;
    private float fireZoneBaseX;
    private float fireZoneBaseY;

    private float stickBaseX;
    private float stickBaseY;
    private float stickRestX;
    private float stickRestY;
    private OnItemSelectedListener itemSelectedListener;
    private CapturedPointerHandler capturedPointerHandler;

    private int movePointer = -1;
    private float moveOriginX;
    private float moveOriginY;
    private float moveCurrentX;
    private float moveCurrentY;
    private boolean scoreboardVisible;
    private float joystickBaseRadius = 120f;
    private float joystickRadius = 120f;
    /** Anchors and scales for the two informational blocks exposed in the layout editor. */
    private float vitalsX, vitalsY, vitalsScale = 1f;
    private float weaponInfoX, weaponInfoY, weaponInfoScale = 1f;
    /** How far a finger must slide off a button before it also starts turning the view. */
    private float dragThreshold = 24f;

    public HudView(Context context) {
        super(context);
        init();
    }

    public HudView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        joystickBaseRadius = 46f * density;
        joystickRadius = joystickBaseRadius;
        dragThreshold = 8f * density;
        stroke.setStyle(Paint.Style.STROKE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(Color.WHITE);
        for (int i = 0; i < MAX_POINTERS; i++) {
            pointerButton[i] = -1;
            pointerRole[i] = ROLE_NONE;
        }
        buttonLabel[BUTTON_FIRE] = "FIRE";
        buttonLabel[BUTTON_AIM] = "AIM";
        buttonLabel[BUTTON_JUMP] = "JUMP";
        buttonLabel[BUTTON_CROUCH] = "CROUCH";
        buttonLabel[BUTTON_RELOAD] = "RELOAD";
        buttonLabel[BUTTON_SLOT_MELEE] = "MELEE SLOT";
        buttonLabel[BUTTON_GRENADE] = "GRENADE";
        buttonLabel[BUTTON_SLOT_PRIMARY] = "PRIMARY SLOT";
        buttonLabel[BUTTON_SLOT_SECONDARY] = "SECONDARY SLOT";
        buttonLabel[BUTTON_PAUSE] = "II";
        buttonLabel[BUTTON_SCOREBOARD] = "TAB";
        buttonLabel[BUTTON_BUY] = "SHOP";
        fireGlyph = vector(R.drawable.ic_hud_fire);
        jumpGlyph = vector(R.drawable.ic_hud_jump);
        crouchGlyph = vector(R.drawable.ic_hud_crouch);
        killFeedRenderer = new KillFeedRenderer(getContext());
        setClickable(true);
    }

    private Drawable vector(int resource) {
        Drawable drawable = AppCompatResources.getDrawable(getContext(), resource);
        return drawable == null ? null : drawable.mutate();
    }

    private static HudSnapshot createEditorState() {
        HudSnapshot state = new HudSnapshot();
        state.kit[WeaponSlot.PRIMARY.ordinal()] = WeaponType.FAMAS_B.ordinal();
        state.kit[WeaponSlot.SECONDARY.ordinal()] = WeaponType.P9.ordinal();
        state.kit[WeaponSlot.MELEE.ordinal()] = WeaponType.KNIFE.ordinal();
        state.kit[WeaponSlot.GRENADE.ordinal()] = WeaponType.FRAG.ordinal();
        state.grenadeCount = 2;
        state.alive = true;
        state.health = 82f;
        state.armor = 46f;
        state.ammo = 30;
        state.reserve = 120;
        state.weaponName = WeaponType.FAMAS_B.displayName();
        state.freeArsenal = true;
        state.reloading = true;
        state.reloadProgress = 0.42f;
        return state;
    }

    /** The arsenal button reports here; the activity owns the buy dialog. */
    public void setBuyListener(Runnable listener) {
        buyListener = listener;
    }

    public void setBuyAvailable(boolean available) {
        if (buyAvailable != available) {
            buyAvailable = available;
            invalidate();
        }
    }

    public void bind(InputState input, Runnable pauseListener) {
        this.input = input;
        this.pauseListener = pauseListener;
    }

    public void setSnapshot(HudSnapshot snapshot) {
        if (this.snapshot == snapshot) {
            return;
        }
        this.snapshot = snapshot;
        if (aimLatched && snapshot != null && (!snapshot.alive || !snapshot.hasScope)) {
            // Not alive, or the weapon that had the scope is no longer in hand.
            dropAim();
        }
        postInvalidateOnAnimation();
    }

    public void setMinimap(Bitmap bitmap) {
        this.minimap = bitmap;
    }

    public void setSouthpaw(boolean value) {
        southpaw = value;
        layoutButtons(getWidth(), getHeight());
        invalidate();
    }

    public void setLayout(HudLayout value) {
        layout = value;
        layoutButtons(getWidth(), getHeight());
        invalidate();
    }

    /** Turns the HUD into a drag-to-arrange editor instead of a live controller. */
    public void setEditMode(boolean value) {
        editMode = value;
        editButton = -1;
        invalidate();
    }

    /** Index of the control currently picked in the editor, or -1. */
    public int selectedItem() {
        return editButton;
    }

    public void setOnItemSelectedListener(OnItemSelectedListener listener) {
        itemSelectedListener = listener;
    }

    private boolean withinStick(float x, float y) {
        float dx = x - stickRestX;
        float dy = y - stickRestY;
        return dx * dx + dy * dy <= joystickRadius * joystickRadius;
    }

    /** Reports which control the arrange screen should show sliders for. */
    public interface OnItemSelectedListener {
        void onItemSelected(int index);
    }

    /** Receives relative mouse motion while the pointer is captured. */
    public interface CapturedPointerHandler {
        boolean onCapturedPointer(MotionEvent event);

        void onCaptureChanged(boolean captured);
    }

    public void setCapturedPointerHandler(CapturedPointerHandler handler) {
        capturedPointerHandler = handler;
    }

    // Pointer capture exists from API 26; on older devices the framework never calls these.
    @Override
    public boolean onCapturedPointerEvent(MotionEvent event) {
        return capturedPointerHandler != null && capturedPointerHandler.onCapturedPointer(event);
    }

    @Override
    public void onPointerCaptureChange(boolean hasCapture) {
        super.onPointerCaptureChange(hasCapture);
        if (capturedPointerHandler != null) {
            capturedPointerHandler.onCaptureChanged(hasCapture);
        }
    }

    public void setAutoFire(boolean value) {
        autoFire = value;
        refreshFire();
    }

    /** Drops a latched sight from outside — the pause menu and the buy menu both do this. */
    public void clearAim() {
        dropAim();
    }

    /** Hold-to-aim when false, tap-to-latch when true. */
    public void setAimToggle(boolean value) {
        if (aimToggle == value) {
            return;
        }
        aimToggle = value;
        dropAim();
    }

    public void setShowFps(boolean value) {
        showFps = value;
    }

    /** Hides the stick and the action buttons when the player is on keyboard and mouse. */
    public void setTouchControlsVisible(boolean value) {
        if (touchControlsVisible == value) {
            return;
        }
        touchControlsVisible = value;
        if (!value && input != null) {
            releaseAll();
        }
        invalidate();
    }

    public boolean touchControlsVisible() {
        return touchControlsVisible;
    }

    public void setBhopKeyboardHint(String hint) {
        keyboardBhopHint = hint;
        invalidate();
    }

    public String bhopControlHint() {
        return keyboardAvailable || !touchControlsVisible ? keyboardBhopHint : TOUCH_BHOP_HINT;
    }

    public void setKeyboardHints(String combat, String utility, String shop) {
        keyboardCombatHint = combat;
        keyboardUtilityHint = utility;
        keyboardShopHint = shop;
        fitKeyboardHints();
        invalidate();
    }

    public void setKeyboardAvailable(boolean available) {
        if (keyboardAvailable == available) return;
        keyboardAvailable = available;
        invalidate();
    }

    public boolean keyboardAvailable() { return keyboardAvailable; }
    public String keyboardUtilityHint() { return buyAvailable ? keyboardShopHint : keyboardUtilityHint; }

    /** All truncation happens on setup/resize, never on the draw path. */
    private void fitKeyboardHints() {
        hintMeasure.setTextSize(10f * density);
        float combatWidth = Math.max(0f, getWidth() - 32f * density);
        float utilityWidth = Math.max(0f, getWidth() - 176f * density);
        fittedCombatHint = TextUtils.ellipsize(keyboardCombatHint, hintMeasure, combatWidth, TextUtils.TruncateAt.END).toString();
        fittedUtilityHint = TextUtils.ellipsize(keyboardUtilityHint, hintMeasure, utilityWidth, TextUtils.TruncateAt.END).toString();
        fittedShopHint = TextUtils.ellipsize(keyboardShopHint, hintMeasure, utilityWidth, TextUtils.TruncateAt.END).toString();
    }

    /** Shows the scoreboard while a bound key is held. */
    public void setScoreboardVisible(boolean value) {
        scoreboardVisible = value;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        layoutButtons(width, height);
        fitKeyboardHints();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        for (Bitmap bitmap : weaponPreviews.values()) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
        weaponPreviews.clear();
    }

    private void layoutButtons(int width, int height) {
        if (width == 0 || height == 0) {
            return;
        }
        float unit = density;
        float margin = 26f * unit;
        float actionX = southpaw ? margin + 34f * unit : width - margin - 34f * unit;
        float mirror = southpaw ? -1f : 1f;

        joystickBaseRadius = 46f * unit;
        stickBaseX = southpaw ? width - 96f * unit : 96f * unit;
        stickBaseY = height - 86f * unit;

        // The requested stock position is the upper-left, safely above the movement stick. The
        // touch dispatcher resolves this zone before it can allocate a floating joystick, so both
        // controls may share a side without the stick teleporting into the fire area. Southpaw
        // mirrors the complete arrangement.
        fireZoneBaseWidth = Math.min(width * 0.38f, 220f * unit);
        fireZoneBaseHeight = Math.min(height * 0.40f, 150f * unit);
        fireZoneBaseX = southpaw ? width - margin - fireZoneBaseWidth * 0.5f
                : margin + fireZoneBaseWidth * 0.5f;
        fireZoneBaseY = 104f * unit + fireZoneBaseHeight * 0.5f;

        baseRadius[BUTTON_FIRE] = 38f * unit;
        baseX[BUTTON_FIRE] = actionX;
        baseY[BUTTON_FIRE] = height - margin - 34f * unit;

        baseRadius[BUTTON_AIM] = 26f * unit;
        baseX[BUTTON_AIM] = actionX - mirror * 74f * unit;
        baseY[BUTTON_AIM] = height - margin - 62f * unit;

        baseRadius[BUTTON_JUMP] = 24f * unit;
        baseX[BUTTON_JUMP] = actionX - mirror * 72f * unit;
        baseY[BUTTON_JUMP] = height - margin - 8f * unit;

        baseRadius[BUTTON_CROUCH] = 22f * unit;
        baseX[BUTTON_CROUCH] = actionX - mirror * 128f * unit;
        baseY[BUTTON_CROUCH] = height - margin - 4f * unit;

        baseRadius[BUTTON_RELOAD] = 24f * unit;
        baseX[BUTTON_RELOAD] = actionX;
        baseY[BUTTON_RELOAD] = height - margin - 104f * unit;

        // Three persistent loadout slots form one dock. Historical indices are retained, so old
        // layouts still map to the same actions even though the numbered circles are gone.
        baseRadius[BUTTON_SLOT_MELEE] = 18f * unit;
        baseX[BUTTON_SLOT_MELEE] = width * 0.5f + 94f * unit;
        baseY[BUTTON_SLOT_MELEE] = height - 27f * unit;

        baseRadius[BUTTON_GRENADE] = 21f * unit;
        baseX[BUTTON_GRENADE] = actionX - mirror * 8f * unit;
        baseY[BUTTON_GRENADE] = height - margin - 160f * unit;

        baseRadius[BUTTON_SLOT_PRIMARY] = 21f * unit;
        baseX[BUTTON_SLOT_PRIMARY] = width * 0.5f - 52f * unit;
        baseY[BUTTON_SLOT_PRIMARY] = height - 27f * unit;

        baseRadius[BUTTON_SLOT_SECONDARY] = 18f * unit;
        baseX[BUTTON_SLOT_SECONDARY] = width * 0.5f + 33f * unit;
        baseY[BUTTON_SLOT_SECONDARY] = height - 27f * unit;

        baseRadius[BUTTON_PAUSE] = 17f * unit;
        baseX[BUTTON_PAUSE] = width - 26f * unit;
        baseY[BUTTON_PAUSE] = 24f * unit;

        baseRadius[BUTTON_SCOREBOARD] = 17f * unit;
        baseX[BUTTON_SCOREBOARD] = width - 66f * unit;
        baseY[BUTTON_SCOREBOARD] = 24f * unit;

        baseRadius[BUTTON_BUY] = 22f * unit;
        // Tucked under the minimap rather than inside it: the minimap fills 18..96 units from the
        // top-left corner, and a button drawn there is simply invisible behind it.
        baseX[BUTTON_BUY] = 40f * unit;
        baseY[BUTTON_BUY] = 126f * unit;

        applyLayout(width, height);
    }

    /** Applies the saved global size, per-control size and offsets to the stock positions. */
    private void applyLayout(int width, int height) {
        float global = layout == null ? 1f : layout.scale;
        for (int i = 0; i < BUTTON_COUNT; i++) {
            float item = layout == null ? 1f : layout.itemScale(i);
            buttonRadius[i] = baseRadius[i] * global * item;
            float offsetX = layout == null ? 0f : layout.offsetX[i] * width;
            float offsetY = layout == null ? 0f : layout.offsetY[i] * height;
            float marginX = controlHalfWidth(i, buttonRadius[i]) + 2f * density;
            float marginY = buttonRadius[i] + 2f * density;
            buttonX[i] = Math.max(marginX, Math.min(width - marginX, baseX[i] + offsetX));
            buttonY[i] = Math.max(marginY, Math.min(height - marginY, baseY[i] + offsetY));
        }

        float zoneItem = layout == null ? 1f : layout.itemScale(HudLayout.FIRE_ZONE);
        float zoneWidth = fireZoneBaseWidth * global * zoneItem;
        float zoneHeight = fireZoneBaseHeight * global * zoneItem;
        float zoneOffsetX = layout == null ? 0f : layout.offsetX[HudLayout.FIRE_ZONE] * width;
        float zoneOffsetY = layout == null ? 0f : layout.offsetY[HudLayout.FIRE_ZONE] * height;
        float zoneX = Math.max(zoneWidth * 0.5f, Math.min(width - zoneWidth * 0.5f,
                fireZoneBaseX + zoneOffsetX));
        float zoneY = Math.max(zoneHeight * 0.5f, Math.min(height - zoneHeight * 0.5f,
                fireZoneBaseY + zoneOffsetY));
        fireZone.set(zoneX - zoneWidth * 0.5f, zoneY - zoneHeight * 0.5f,
                zoneX + zoneWidth * 0.5f, zoneY + zoneHeight * 0.5f);

        float stickItem = layout == null ? 1f : layout.itemScale(HudLayout.STICK);
        joystickRadius = joystickBaseRadius * global * stickItem;
        float stickOffsetX = layout == null ? 0f : layout.offsetX[HudLayout.STICK] * width;
        float stickOffsetY = layout == null ? 0f : layout.offsetY[HudLayout.STICK] * height;
        float stickMargin = joystickRadius + 2f * density;
        stickRestX = Math.max(stickMargin, Math.min(width - stickMargin,
                stickBaseX + stickOffsetX));
        stickRestY = Math.max(stickMargin, Math.min(height - stickMargin,
                stickBaseY + stickOffsetY));

        float vitalsItem = layout == null ? 1f : layout.itemScale(HudLayout.VITALS);
        vitalsScale = global * vitalsItem;
        float vitalsUnit = density * vitalsScale;
        float vitalsOffsetX = layout == null ? 0f : layout.offsetX[HudLayout.VITALS] * width;
        float vitalsOffsetY = layout == null ? 0f : layout.offsetY[HudLayout.VITALS] * height;
        vitalsX = Math.max(4f * vitalsUnit, Math.min(width - 154f * vitalsUnit,
                18f * density + vitalsOffsetX));
        vitalsY = Math.max(28f * vitalsUnit, Math.min(height - 22f * vitalsUnit,
                height - 150f * density + vitalsOffsetY));

        float weaponItem = layout == null ? 1f : layout.itemScale(HudLayout.WEAPON_INFO);
        weaponInfoScale = global * weaponItem;
        float weaponUnit = density * weaponInfoScale;
        float weaponOffsetX = layout == null ? 0f
                : layout.offsetX[HudLayout.WEAPON_INFO] * width;
        float weaponOffsetY = layout == null ? 0f
                : layout.offsetY[HudLayout.WEAPON_INFO] * height;
        weaponInfoX = Math.max(125f * weaponUnit, Math.min(width - 4f * weaponUnit,
                width - 18f * density + weaponOffsetX));
        weaponInfoY = Math.max(58f * weaponUnit, Math.min(height - 24f * weaponUnit,
                height - 210f * density + weaponOffsetY));
    }

    /** Display name of an editable control. */
    public static String itemLabel(int index, String[] labels) {
        return index == ITEM_STICK ? "STICK"
                : index == ITEM_FIRE_ZONE ? "FIRE ZONE"
                : index == ITEM_WEAPON_INFO ? "WEAPON INFO"
                : index == ITEM_VITALS ? "HEALTH & ARMOR"
                : index >= 0 && index < labels.length ? labels[index] : "";
    }

    private void weaponInfoBounds(RectF out) {
        float unit = density * weaponInfoScale;
        out.set(weaponInfoX - 125f * unit, weaponInfoY - 58f * unit,
                weaponInfoX + 4f * unit, weaponInfoY + 23f * unit);
    }

    private void vitalsBounds(RectF out) {
        float unit = density * vitalsScale;
        out.set(vitalsX - 4f * unit, vitalsY - 28f * unit,
                vitalsX + 154f * unit, vitalsY + 22f * unit);
    }

    private int readoutAt(float x, float y) {
        weaponInfoBounds(rect);
        if (rect.contains(x, y)) {
            return ITEM_WEAPON_INFO;
        }
        vitalsBounds(rect);
        return rect.contains(x, y) ? ITEM_VITALS : -1;
    }

    public String labelOf(int index) {
        return itemLabel(index, buttonLabel);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (editMode) {
            return handleEditTouch(event);
        }
        if (input == null) {
            return false;
        }
        if (!touchControlsVisible) {
            // A real finger means the player went back to touch; bring the controls back.
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                    && !event.isFromSource(android.view.InputDevice.SOURCE_MOUSE)) {
                setTouchControlsVisible(true);
            }
            return true;
        }
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int index = event.getActionIndex();
                handleDown(event.getPointerId(index), event.getX(index), event.getY(index));
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < event.getPointerCount(); i++) {
                    handleMove(event.getPointerId(i), event.getX(i), event.getY(i));
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int index = event.getActionIndex();
                handleUp(event.getPointerId(index));
                if (action == MotionEvent.ACTION_UP) {
                    performClick();
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                releaseAll();
                break;
            default:
                break;
        }
        invalidate();
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    /** Drag-to-arrange: a touch picks the control under it and moves it with the finger. */
    private boolean handleEditTouch(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                int picked = buttonAt(x, y);
                if (picked < 0 && withinStick(x, y)) {
                    picked = ITEM_STICK;
                }
                if (picked < 0) {
                    picked = readoutAt(x, y);
                }
                if (picked < 0 && fireZone.contains(x, y)) {
                    picked = ITEM_FIRE_ZONE;
                }
                editButton = picked;
                pointerX[0] = x;
                pointerY[0] = y;
                if (itemSelectedListener != null) {
                    itemSelectedListener.onItemSelected(editButton);
                }
                break;
            }
            case MotionEvent.ACTION_MOVE:
                if (editButton >= 0 && layout != null && getWidth() > 0) {
                    layout.moveItem(editButton, (x - pointerX[0]) / getWidth(),
                            (y - pointerY[0]) / getHeight());
                    pointerX[0] = x;
                    pointerY[0] = y;
                    applyLayout(getWidth(), getHeight());
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (editButton >= 0 && layout != null) {
                    layout.save();
                }
                performClick();
                break;
            default:
                break;
        }
        invalidate();
        return true;
    }

    private void handleDown(int pointerId, float x, float y) {
        if (pointerId < 0 || pointerId >= MAX_POINTERS) {
            return;
        }
        pointerX[pointerId] = x;
        pointerY[pointerId] = y;
        pointerDownX[pointerId] = x;
        pointerDownY[pointerId] = y;
        pointerDragging[pointerId] = false;

        int button = buttonAt(x, y);
        if (button < 0 && fireZone.contains(x, y)) {
            // The zone is the trigger, so it behaves exactly like the fire button — including
            // sliding off it to keep turning while still holding fire.
            button = BUTTON_FIRE;
        }
        if (button >= 0) {
            pointerRole[pointerId] = ROLE_BUTTON;
            pointerButton[pointerId] = button;
            pressButton(button, true);
            return;
        }
        pointerButton[pointerId] = -1;

        boolean leftHalf = x < getWidth() * 0.46f;
        boolean movementSide = southpaw != leftHalf;
        if (movementSide) {
            boolean fixed = staticJoystick();
            if (movePointer < 0 && (!fixed || withinStick(x, y))) {
                pointerRole[pointerId] = ROLE_MOVE;
                movePointer = pointerId;
                moveOriginX = fixed ? stickRestX : x;
                moveOriginY = fixed ? stickRestY : y;
                moveCurrentX = moveOriginX;
                moveCurrentY = moveOriginY;
                updateMovementStick(x, y);
            } else {
                // The movement half is not a second look surface. With a fixed stick, missing its
                // visible base should do nothing rather than unexpectedly turning the camera.
                pointerRole[pointerId] = ROLE_NONE;
            }
        } else {
            // Any other finger looks; several may be down at once.
            pointerRole[pointerId] = ROLE_LOOK;
        }
    }

    private void handleMove(int pointerId, float x, float y) {
        if (pointerId < 0 || pointerId >= MAX_POINTERS) {
            return;
        }
        float deltaX = x - pointerX[pointerId];
        float deltaY = y - pointerY[pointerId];
        pointerX[pointerId] = x;
        pointerY[pointerId] = y;

        switch (pointerRole[pointerId]) {
            case ROLE_MOVE: {
                updateMovementStick(x, y);
                break;
            }
            case ROLE_LOOK:
                input.addLook(deltaX, deltaY);
                break;
            case ROLE_BUTTON: {
                // The button stays held by this finger. Once the finger has travelled past the
                // dead zone it also aims, so you can keep firing and still turn with that thumb.
                int button = pointerButton[pointerId];
                if (!draggableButton(button)) {
                    break;
                }
                if (!pointerDragging[pointerId]) {
                    float travelX = x - pointerDownX[pointerId];
                    float travelY = y - pointerDownY[pointerId];
                    if (travelX * travelX + travelY * travelY > dragThreshold * dragThreshold) {
                        pointerDragging[pointerId] = true;
                    }
                }
                if (pointerDragging[pointerId]) {
                    input.addLook(deltaX, deltaY);
                }
                break;
            }
            default:
                break;
        }
    }

    private boolean staticJoystick() {
        return layout != null && layout.staticJoystick;
    }

    /** Resolves the thumb against either the fixed centre or the floating drag-along origin. */
    private void updateMovementStick(float x, float y) {
        float dx = x - moveOriginX;
        float dy = y - moveOriginY;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length > joystickRadius) {
            if (!staticJoystick()) {
                // A floating origin follows the thumb, keeping a long swipe away from a hard rim.
                moveOriginX += dx * (1f - joystickRadius / length);
                moveOriginY += dy * (1f - joystickRadius / length);
            }
            dx *= joystickRadius / length;
            dy *= joystickRadius / length;
        }
        moveCurrentX = moveOriginX + dx;
        moveCurrentY = moveOriginY + dy;
        input.setTouchMove(dx / joystickRadius, -dy / joystickRadius);
        input.setTouchSprint(-dy / joystickRadius > 0.82f);
    }

    /** Hold-style buttons double as an aim surface; one-shot taps do not. */
    private boolean draggableButton(int button) {
        return button == BUTTON_FIRE || button == BUTTON_AIM || button == BUTTON_CROUCH
                || button == BUTTON_JUMP || button == BUTTON_RELOAD || button == BUTTON_GRENADE;
    }

    private void handleUp(int pointerId) {
        if (pointerId < 0 || pointerId >= MAX_POINTERS) {
            return;
        }
        int released = -1;
        if (pointerRole[pointerId] == ROLE_MOVE) {
            movePointer = -1;
            input.setTouchMove(0f, 0f);
            input.setTouchSprint(false);
        } else if (pointerRole[pointerId] == ROLE_BUTTON) {
            released = pointerButton[pointerId];
        }
        // Clear the slot before reporting the release so held-state queries see the new truth.
        pointerRole[pointerId] = ROLE_NONE;
        pointerButton[pointerId] = -1;
        pointerDragging[pointerId] = false;
        if (released >= 0) {
            pressButton(released, false);
        }
    }

    private boolean isButtonHeld(int button) {
        for (int i = 0; i < MAX_POINTERS; i++) {
            if (pointerRole[i] == ROLE_BUTTON && pointerButton[i] == button) {
                return true;
            }
        }
        return false;
    }

    /** True while the sight is up, whether that is a held button or a latched one. */
    private boolean aiming() {
        return aimToggle ? aimLatched : isButtonHeld(BUTTON_AIM);
    }

    /**
     * Fire stays on while the trigger is down. Aiming only pulls the trigger too when auto fire is
     * switched on, which it is not by default: bringing up the sight should just bring up the sight.
     */
    private void refreshFire() {
        if (input == null) {
            return;
        }
        input.setTouchFire(isButtonHeld(BUTTON_FIRE) || (autoFire && aiming()));
    }

    /** Drops a latched sight; used on death, on pause and when the toggle setting changes. */
    private void dropAim() {
        aimLatched = false;
        if (input != null) {
            input.setTouchAim(false);
            refreshFire();
        }
    }

    private void releaseAll() {
        dropAim();
        movePointer = -1;
        for (int i = 0; i < MAX_POINTERS; i++) {
            if (pointerRole[i] == ROLE_BUTTON && pointerButton[i] >= 0) {
                pressButton(pointerButton[i], false);
            }
            pointerRole[i] = ROLE_NONE;
            pointerButton[i] = -1;
            pointerDragging[i] = false;
        }
        input.setTouchMove(0f, 0f);
        input.setTouchFire(false);
        input.setTouchAim(false);
        input.setTouchSprint(false);
        input.setTouchJump(false);
    }

    private int buttonAt(float x, float y) {
        for (int i = 0; i < BUTTON_COUNT; i++) {
            if (!isButtonVisible(i)) {
                continue;
            }
            if (isLoadoutSlotButton(i)) {
                loadoutSlotBounds(i, rect);
                float slop = 4f * density;
                rect.inset(-slop, -slop);
                if (rect.contains(x, y)) {
                    return i;
                }
                continue;
            }
            float dx = x - buttonX[i];
            float dy = y - buttonY[i];
            float radius = buttonRadius[i] * 1.18f;
            if (dx * dx + dy * dy <= radius * radius) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isLoadoutSlotButton(int button) {
        return button == BUTTON_SLOT_PRIMARY || button == BUTTON_SLOT_SECONDARY
                || button == BUTTON_SLOT_MELEE;
    }

    private static int slotForButton(int button) {
        if (button == BUTTON_SLOT_PRIMARY) return WeaponSlot.PRIMARY.ordinal();
        if (button == BUTTON_SLOT_SECONDARY) return WeaponSlot.SECONDARY.ordinal();
        if (button == BUTTON_SLOT_MELEE) return WeaponSlot.MELEE.ordinal();
        if (button == BUTTON_GRENADE) return WeaponSlot.GRENADE.ordinal();
        return -1;
    }

    private static float controlHalfWidth(int button, float radius) {
        if (button == BUTTON_SLOT_PRIMARY) return radius * 2.3f;
        if (button == BUTTON_SLOT_SECONDARY) return radius * 1.7f;
        if (button == BUTTON_SLOT_MELEE) return radius * 1.5f;
        return radius;
    }

    private void loadoutSlotBounds(int button, RectF out) {
        float radius = buttonRadius[button];
        float halfWidth = controlHalfWidth(button, radius);
        float halfHeight = radius * 0.82f;
        out.set(buttonX[button] - halfWidth, buttonY[button] - halfHeight,
                buttonX[button] + halfWidth, buttonY[button] + halfHeight);
    }

    private void pressButton(int button, boolean down) {
        switch (button) {
            case BUTTON_FIRE:
                refreshFire();
                break;
            case BUTTON_AIM:
                if (aimToggle) {
                    if (down) {
                        aimLatched = !aimLatched;
                        input.setTouchAim(aimLatched);
                    }
                } else {
                    aimLatched = false;
                    input.setTouchAim(down);
                }
                refreshFire();
                break;
            case BUTTON_JUMP:
                input.setTouchJump(down || isButtonHeld(BUTTON_JUMP));
                if (down) {
                    input.queueJump();
                }
                break;
            case BUTTON_CROUCH:
                if (down) {
                    input.crouching = !input.crouching;
                }
                break;
            case BUTTON_RELOAD:
                if (down) {
                    input.queueReload();
                }
                break;
            case BUTTON_SLOT_MELEE:
                if (down) {
                    input.requestSlot(WeaponSlot.MELEE.ordinal());
                }
                break;
            case BUTTON_GRENADE:
                if (down) {
                    input.queueGrenade();
                }
                break;
            case BUTTON_SLOT_PRIMARY:
                if (down) {
                    input.requestSlot(WeaponSlot.PRIMARY.ordinal());
                }
                break;
            case BUTTON_SLOT_SECONDARY:
                if (down) {
                    input.requestSlot(WeaponSlot.SECONDARY.ordinal());
                }
                break;
            case BUTTON_SCOREBOARD:
                scoreboardVisible = down;
                break;
            case BUTTON_PAUSE:
                if (down && pauseListener != null) {
                    pauseListener.run();
                }
                break;
            case BUTTON_BUY:
                if (down && buyListener != null) {
                    buyListener.run();
                }
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float unit = density;
        if (editMode) {
            drawEditor(canvas, unit);
            return;
        }
        HudSnapshot state = snapshot;
        if (state == null) {
            return;
        }

        drawScope(canvas, state, unit);
        if (!state.hardcoreHud) drawDamageIndicator(canvas, state, unit);
        drawCrosshair(canvas, state, unit);
        if (touchControlsVisible) {
            drawJoystick(canvas, unit);
            drawButtons(canvas, state, unit);
        }
        drawVitals(canvas, state, unit);
        drawAmmo(canvas, state, unit);
        drawScoreHeader(canvas, state, unit);
        if (!state.hardcoreHud) drawMinimap(canvas, state, unit);
        drawRangeScore(canvas, state, unit);
        drawMovement(canvas, state, unit);
        drawKillCamBanner(canvas, state, unit);
        drawObjectives(canvas, state, unit);
        if (!state.hardcoreHud) drawKillFeed(canvas, state, unit);
        drawBanner(canvas, state, unit);
        drawKeyboardHints(canvas, state, unit);
        if (scoreboardVisible) {
            drawScoreboard(canvas, state, unit);
        }
        if (showFps) {
            text.setTextAlign(Paint.Align.LEFT);
            text.setTextSize(11f * unit);
            text.setColor(0xCCFFFFFF);
            canvas.drawText(state.fps + " FPS · p95 " + state.frameP95 + "ms",
                    8f * unit, getHeight() - 8f * unit, text);
        }
    }

    private void drawKeyboardHints(Canvas canvas, HudSnapshot state, float unit) {
        if (!keyboardAvailable || state.hardcoreHud || state.killCam || scoreboardVisible) return;
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(10f * unit);
        text.setColor(0xCCEAFAFF);
        text.setShadowLayer(2f * unit, 0f, unit, 0xDD000000);
        if (state.alive) canvas.drawText(fittedCombatHint, getWidth() * .5f,
                getHeight() - 30f * unit, text);
        canvas.drawText(buyAvailable ? fittedShopHint : fittedUtilityHint,
                (getWidth() + 144f * unit) * .5f, getHeight() - 14f * unit, text);
        text.clearShadowLayer();
    }

    private void drawMovement(Canvas canvas, HudSnapshot state, float unit) {
        if (!state.alive || state.killCam) return;
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(12f * unit);
        text.setColor(state.sliding ? 0xFFFFCB77 : 0xFFE2F8FF);
        text.setShadowLayer(2f * unit, 0f, unit, 0xDD000000);
        if (state.bhop) {
            movementLabel.setLength(0);
            int tenth = Math.round(state.speed * 10f);
            movementLabel.append(tenth / 10).append('.').append(tenth % 10).append(" m/s");
            movementLabel.append(state.sliding ? "  SLIDE" : "  BHOP");
            drawMovementLine(canvas, getHeight() - 78f * unit);
        }
        if (state.movementTrial) {
            movementLabel.setLength(0);
            movementLabel.append("TIME "); appendCourseTime(state.courseMillis);
            movementLabel.append("  ·  BEST ");
            if (state.courseBestMillis == 0) movementLabel.append('—');
            else appendCourseTime(state.courseBestMillis);
            drawMovementLine(canvas, getHeight() - 61f * unit);
            if (state.hardcoreHud) { text.clearShadowLayer(); return; }
            movementLabel.setLength(0);
            if (state.courseFinished) movementLabel.append("FINISH · ").append(keyboardAvailable
                    && !courseRestartHint.isEmpty() ? courseRestartHint : "Pause → Restart course");
            else if (state.courseRetryRecommended) movementLabel.append("CHECKPOINT MISSED · ")
                    .append(keyboardAvailable && !courseRestartHint.isEmpty()
                            ? courseRestartHint : "Pause → Restart course");
            else if (!state.courseRunning) {
                if (state.bhop) movementLabel.append("START · ").append(bhopControlHint());
                else movementLabel.append("Cross the green START gate · sprint, then crouch to slide");
            }
            else {
                movementLabel.append("NEXT ").append(state.courseGate + 1).append('/').append(com.ccs.voxelgun.map.CourseLayout.count());
                if (state.courseGate == com.ccs.voxelgun.map.CourseLayout.JUMP_GATE) movementLabel.append(" · JUMP through the blue gate");
                else if (state.courseGate == com.ccs.voxelgun.map.CourseLayout.SLIDE_GATE) movementLabel.append(" · SLIDE through the orange gate");
            }
            if (state.courseGhost && state.courseRunning) movementLabel.append(" · BEST GHOST");
            text.setTextSize(10f * unit);
            drawMovementLine(canvas, getHeight() - 45f * unit);
        }
        text.clearShadowLayer();
    }

    private void appendCourseTime(int millis) {
        movementLabel.append(millis / 1000).append('.');
        int fraction = millis % 1000;
        if (fraction < 100) movementLabel.append('0');
        if (fraction < 10) movementLabel.append('0');
        movementLabel.append(fraction).append('s');
    }

    private void drawMovementLine(Canvas canvas, float y) {
        int length = Math.min(movementChars.length, movementLabel.length());
        movementLabel.getChars(0, length, movementChars, 0);
        canvas.drawText(movementChars, 0, length, getWidth() * 0.5f, y, text);
    }

    private void drawCrosshair(Canvas canvas, HudSnapshot state, float unit) {
        float cx = getWidth() * 0.5f;
        float cy = getHeight() * 0.5f;

        // Melee always uses the exact centre ray, so its reticle is a fixed plus with no spread,
        // cosmetic preset or inherited optic state.
        boolean scoped = !state.melee && state.scopeBlend > 0.5f;
        if (state.alive && state.melee) {
            float halfSize = 7f * unit;
            stroke.setStrokeWidth(3.6f * unit);
            stroke.setColor(0x99000000);
            canvas.drawLine(cx - halfSize, cy, cx + halfSize, cy, stroke);
            canvas.drawLine(cx, cy - halfSize, cx, cy + halfSize, stroke);
            stroke.setStrokeWidth(1.8f * unit);
            stroke.setColor(0xFFFFFFFF);
            canvas.drawLine(cx - halfSize, cy, cx + halfSize, cy, stroke);
            canvas.drawLine(cx, cy - halfSize, cx, cy + halfSize, stroke);
        } else if (state.alive && !scoped && state.crosshairPreset == 0) {
            float gap = (6f + state.crosshairSpread * 9f) * unit;
            float length = 6f * unit;
            stroke.setStrokeWidth(1.8f * unit);
            stroke.setColor(0xE6FFFFFF);
            canvas.drawLine(cx - gap - length, cy, cx - gap, cy, stroke);
            canvas.drawLine(cx + gap, cy, cx + gap + length, cy, stroke);
            canvas.drawLine(cx, cy - gap - length, cx, cy - gap, stroke);
            canvas.drawLine(cx, cy + gap, cx, cy + gap + length, stroke);
            fill.setColor(0x66FFFFFF);
            canvas.drawCircle(cx, cy, 1.2f * unit, fill);
        }
        if (state.alive && !state.melee && !scoped && state.crosshairPreset != 0) {
            fill.setColor(0xFFFFFFFF);
            stroke.setColor(0xFFFFFFFF);
            stroke.setStrokeWidth(1.5f * unit);
            canvas.drawCircle(cx, cy, 1.6f * unit, fill);
            if (state.crosshairPreset == 2) canvas.drawCircle(cx, cy, 7f * unit, stroke);
        }

        if (state.hitMarker > 0f) {
            stroke.setColor(state.hitMarkerLethal ? 0xFFFF5A4B : state.hitKind == 2 ? 0xFFFFD65A
                    : state.hitKind == 1 ? 0xFF66D9FF : 0xFFFFFFFF);
            stroke.setAlpha(Math.round(255 * Math.min(1f, state.hitMarker * 2f)));
            stroke.setStrokeWidth((state.hitKind == 2 ? 2.5f : 1.8f) * unit);
            float size = (8f + state.hitMarker * 2f) * unit;
            float inner = 4f * unit;
            canvas.drawLine(cx - size, cy - size, cx - inner, cy - inner, stroke);
            canvas.drawLine(cx + size, cy - size, cx + inner, cy - inner, stroke);
            canvas.drawLine(cx - size, cy + size, cx - inner, cy + inner, stroke);
            canvas.drawLine(cx + size, cy + size, cx + inner, cy + inner, stroke);
            if (state.hitKind == 1) canvas.drawCircle(cx, cy, 12f * unit, stroke);
        }
        if (state.alive && state.reloading) {
            drawCrosshairReload(canvas, state, cx, cy, unit);
        }
    }

    /** Reload status stays in peripheral vision instead of forcing a glance at the screen edge. */
    private void drawCrosshairReload(Canvas canvas, HudSnapshot state, float cx, float cy,
                                     float unit) {
        float progress = Math.max(0f, Math.min(1f, state.reloadProgress));
        float width = 50f * unit;
        float height = 3f * unit;
        float top = cy + 24f * unit;
        rect.set(cx - width * 0.5f, top, cx + width * 0.5f, top + height);
        fill.setColor(0x99070A0E);
        canvas.drawRoundRect(rect, height * 0.5f, height * 0.5f, fill);
        rect.right = rect.left + width * progress;
        fill.setColor(0xFFFFA65F);
        canvas.drawRoundRect(rect, height * 0.5f, height * 0.5f, fill);
        canvas.drawCircle(cx - width * 0.5f + width * progress, top + height * 0.5f,
                2.2f * unit, fill);

        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(8.5f * unit);
        text.setColor(0xE6FFFFFF);
        canvas.drawText("RELOAD  " + Math.round(progress * 100f) + "%", cx,
                top + 13f * unit, text);
    }

    private void drawObjectives(Canvas canvas, HudSnapshot state, float unit) {
        if (state.objectiveCount == 0) return;
        float cx = getWidth() * 0.5f;
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(11f * unit);
        text.setColor(0xFFFFDF78);
        String timer = (state.modifiers & com.ccs.voxelgun.game.MatchModifier.NO_BOTS.bit) == 0
                && (state.mode == com.ccs.voxelgun.game.GameMode.KING_OF_THE_HILL
                || state.mode == com.ccs.voxelgun.game.GameMode.BOMB_PLANT)
                ? "  " + (int) Math.ceil(state.objectiveSeconds) + "s" : "";
        canvas.drawText(state.objectiveStatus + timer + "  /  " + Math.round(state.objectiveDistance) + "m",
                cx, 72f * unit, text);
        if (state.mode == com.ccs.voxelgun.game.GameMode.CAPTURE_POINT || state.conquest) {
            for (int i = 0; i < state.objectiveCount; i++) {
                float x = cx + (i - (state.objectiveCount - 1) * .5f) * 48f * unit;
                text.setColor(state.objectiveContested[i] ? 0xFFFFFFFF : state.objectiveColor[i]);
                canvas.drawText(String.valueOf((char) ('A' + i)) + (state.objectiveContested[i] ? " !" : ""),
                        x, 89f * unit, text);
                fill.setColor(state.objectiveProgress[i] >= 0f ? 0xFF50B8FF : 0xFFFF6855);
                canvas.drawRect(x - 14f * unit, 94f * unit,
                        x - 14f * unit + Math.abs(state.objectiveProgress[i]) * 28f * unit, 96f * unit, fill);
            }
        }
        if (state.interactProgress > 0f) {
            fill.setColor(0xFFFFDF78);
            canvas.drawRect(cx - 45f * unit, 102f * unit,
                    cx - 45f * unit + state.interactProgress * 90f * unit, 106f * unit, fill);
        }
        canvas.save();
        canvas.rotate((float) Math.toDegrees(state.objectiveBearing), cx, 115f * unit);
        stroke.setStrokeWidth(2f * unit);
        stroke.setColor(0xFFFFDF78);
        canvas.drawLine(cx, 110f * unit, cx - 4f * unit, 119f * unit, stroke);
        canvas.drawLine(cx, 110f * unit, cx + 4f * unit, 119f * unit, stroke);
        canvas.restore();
    }

    /**
     * The sniper optic: everything outside the lens goes black and a mil-dot reticle is drawn
     * inside it. The lens opens up as the scope is raised rather than snapping on.
     */
    private void drawScope(Canvas canvas, HudSnapshot state, float unit) {
        if (state.melee) {
            return;
        }
        float blend = state.scopeBlend;
        if (blend <= 0.01f) {
            return;
        }
        float cx = getWidth() * 0.5f;
        float cy = getHeight() * 0.5f;
        float radius = Math.min(getWidth(), getHeight()) * 0.46f * (0.86f + 0.14f * blend);
        int alpha = (int) (255f * Math.min(1f, blend * 1.15f));

        // Black everything except the lens, using an even-odd fill so the circle is a hole.
        path.reset();
        path.setFillType(Path.FillType.EVEN_ODD);
        path.addRect(0f, 0f, getWidth(), getHeight(), Path.Direction.CW);
        path.addCircle(cx, cy, radius, Path.Direction.CW);
        fill.setColor(alpha << 24);
        canvas.drawPath(path, fill);
        path.setFillType(Path.FillType.WINDING);

        // Lens rim: a soft inner shadow plus a hard edge.
        stroke.setColor((alpha / 2) << 24);
        stroke.setStrokeWidth(10f * unit);
        canvas.drawCircle(cx, cy, radius - 5f * unit, stroke);
        stroke.setColor((alpha << 24) | 0x00161616);
        stroke.setStrokeWidth(2.6f * unit);
        canvas.drawCircle(cx, cy, radius, stroke);

        int ink = (alpha << 24) | 0x00121212;
        float centreGap = 7f * unit;
        float thinEnd = radius * 0.46f;

        // Heavy outer posts.
        stroke.setColor(ink);
        stroke.setStrokeWidth(3.4f * unit);
        canvas.drawLine(cx - radius, cy, cx - thinEnd, cy, stroke);
        canvas.drawLine(cx + thinEnd, cy, cx + radius, cy, stroke);
        canvas.drawLine(cx, cy - radius, cx, cy - thinEnd, stroke);
        canvas.drawLine(cx, cy + thinEnd, cx, cy + radius, stroke);

        // Fine inner cross with a gap over the target.
        stroke.setStrokeWidth(1.3f * unit);
        canvas.drawLine(cx - thinEnd, cy, cx - centreGap, cy, stroke);
        canvas.drawLine(cx + centreGap, cy, cx + thinEnd, cy, stroke);
        canvas.drawLine(cx, cy - thinEnd, cx, cy - centreGap, stroke);
        canvas.drawLine(cx, cy + centreGap, cx, cy + thinEnd, stroke);

        // Mil dots for holdover.
        fill.setColor(ink);
        float dot = 1.9f * unit;
        for (int i = 1; i <= 3; i++) {
            float step = thinEnd * i / 3.6f;
            canvas.drawCircle(cx - step, cy, dot, fill);
            canvas.drawCircle(cx + step, cy, dot, fill);
            canvas.drawCircle(cx, cy - step, dot, fill);
            canvas.drawCircle(cx, cy + step, dot, fill);
        }
        canvas.drawCircle(cx, cy, 1.5f * unit, fill);
    }

    /**
     * Outlines the fire zone — only ever called from the arrange screen.
     *
     * <p>In a match the zone has to be invisible: it covers a big part of the corner, and drawing
     * it there would bury the scoreboard and the view behind a slab of tint. Showing it here is
     * what makes it possible to place at all.
     */
    private void drawFireZone(Canvas canvas, float unit) {
        float opacity = layout == null ? 1f : layout.opacity;
        fill.setColor(withOpacity(0x22FFB68A, opacity));
        canvas.drawRoundRect(fireZone, 12f * unit, 12f * unit, fill);
        stroke.setStrokeWidth(1.6f * unit);
        stroke.setColor(withOpacity(0x88FFB68A, opacity));
        canvas.drawRoundRect(fireZone, 12f * unit, 12f * unit, stroke);

        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(withOpacity(0xCCFFFFFF, opacity));
        text.setTextSize(Math.min(13f * unit, fireZone.height() * 0.22f));
        canvas.drawText("FIRE ZONE", fireZone.centerX(),
                fireZone.centerY() + text.getTextSize() * 0.36f, text);
    }

    private void drawJoystick(Canvas canvas, float unit) {
        float baseX = movePointer >= 0 ? moveOriginX : stickRestX;
        float baseY = movePointer >= 0 ? moveOriginY : stickRestY;
        stroke.setStrokeWidth(2f * unit);
        stroke.setColor(movePointer >= 0 ? 0x66FFFFFF : 0x33FFFFFF);
        canvas.drawCircle(baseX, baseY, joystickRadius, stroke);
        fill.setColor(movePointer >= 0 ? 0x55FFFFFF : 0x22FFFFFF);
        float knobX = movePointer >= 0 ? moveCurrentX : baseX;
        float knobY = movePointer >= 0 ? moveCurrentY : baseY;
        canvas.drawCircle(knobX, knobY, joystickRadius * 0.39f, fill);
    }

    private void drawButtons(Canvas canvas, HudSnapshot state, float unit) {
        float opacity = layout == null ? 1f : layout.opacity;
        for (int i = 0; i < BUTTON_COUNT; i++) {
            if (!isButtonVisible(i)) {
                continue;
            }
            if (isLoadoutSlotButton(i)) {
                drawLoadoutSlot(canvas, state, i, opacity, unit);
                continue;
            }
            boolean active = isButtonActive(i, state);
            float radius = buttonRadius[i];
            fill.setColor(withOpacity(active ? 0x77FFB68A : 0x33121212, opacity));
            canvas.drawCircle(buttonX[i], buttonY[i], radius, fill);
            stroke.setStrokeWidth(1.6f * unit);
            stroke.setColor(withOpacity(active ? 0xFFFFB68A : 0x66FFFFFF, opacity));
            canvas.drawCircle(buttonX[i], buttonY[i], radius, stroke);

            int ink = withOpacity(active ? 0xFF1B1008 : 0xE6FFFFFF, opacity);
            if (i == BUTTON_FIRE) {
                drawVectorGlyph(canvas, fireGlyph, buttonX[i], buttonY[i], radius * 0.67f, ink);
                continue;
            }
            if (i == BUTTON_JUMP) {
                drawVectorGlyph(canvas, jumpGlyph, buttonX[i], buttonY[i], radius * 0.58f, ink);
                continue;
            }
            if (i == BUTTON_CROUCH) {
                drawVectorGlyph(canvas, crouchGlyph, buttonX[i], buttonY[i], radius * 0.58f, ink);
                continue;
            }
            if (i == BUTTON_AIM) {
                drawAimGlyph(canvas, buttonX[i], buttonY[i], radius, ink, unit);
                continue;
            }
            if (i == BUTTON_BUY) {
                drawTrolleyGlyph(canvas, buttonX[i], buttonY[i], radius, ink, unit);
                continue;
            }
            if (i == BUTTON_GRENADE) {
                drawGrenadeGlyph(canvas, state, radius, opacity);
                continue;
            }
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(ink);
            text.setTextSize(Math.min(radius * 0.52f, 12f * unit));
            canvas.drawText(buttonLabel[i], buttonX[i], buttonY[i] + text.getTextSize() * 0.36f,
                    text);
        }

        // Reload progress ring.
        if (state.reloading) {
            float progress = Math.max(0f, Math.min(1f, state.reloadProgress));
            rect.set(buttonX[BUTTON_RELOAD] - buttonRadius[BUTTON_RELOAD],
                    buttonY[BUTTON_RELOAD] - buttonRadius[BUTTON_RELOAD],
                    buttonX[BUTTON_RELOAD] + buttonRadius[BUTTON_RELOAD],
                    buttonY[BUTTON_RELOAD] + buttonRadius[BUTTON_RELOAD]);
            stroke.setStrokeWidth(3.4f * unit);
            stroke.setColor(withOpacity(0xFFFFB68A, opacity));
            canvas.drawArc(rect, -90f, 360f * progress, false, stroke);
        }
        // Grenade count.
        if (state.grenadeCount > 0) {
            text.setTextSize(10f * unit);
            text.setColor(0xFFFFB68A);
            canvas.drawText("x" + state.grenadeCount,
                    buttonX[BUTTON_GRENADE] + buttonRadius[BUTTON_GRENADE] * 0.9f,
                    buttonY[BUTTON_GRENADE] - buttonRadius[BUTTON_GRENADE] * 0.6f, text);
        }
    }

    /** One pill in the unified weapon dock. The model and its equipped skin are both real. */
    private void drawLoadoutSlot(Canvas canvas, HudSnapshot state, int button, float opacity,
                                 float unit) {
        loadoutSlotBounds(button, rect);
        boolean active = isButtonActive(button, state);
        float corner = rect.height() * 0.5f;
        fill.setColor(withOpacity(active ? 0xCC2A1A12 : 0xB3141A21, opacity));
        canvas.drawRoundRect(rect, corner, corner, fill);
        stroke.setStrokeWidth((active ? 2.2f : 1.2f) * unit);
        stroke.setColor(withOpacity(active ? 0xFFFFB06C : 0x667C91A3, opacity));
        canvas.drawRoundRect(rect, corner, corner, stroke);

        int slot = slotForButton(button);
        Bitmap art = weaponPreview(state, slot);
        if (art != null) {
            previewRect.set(rect);
            previewRect.inset(4f * unit, 2f * unit);
            previewPaint.setAlpha(Math.round(255f * opacity));
            canvas.drawBitmap(art, null, previewRect, previewPaint);
            previewPaint.setAlpha(255);
        }
        if (active) {
            fill.setColor(withOpacity(0xFFFFB06C, opacity));
            float marker = 2.4f * unit;
            canvas.drawRoundRect(rect.left + rect.width() * 0.28f, rect.bottom - marker,
                    rect.right - rect.width() * 0.28f, rect.bottom, marker, marker, fill);
        }
    }

    /** The grenade remains a throw action, but now shows the grenade actually carried. */
    private void drawGrenadeGlyph(Canvas canvas, HudSnapshot state, float radius, float opacity) {
        Bitmap art = weaponPreview(state, WeaponSlot.GRENADE.ordinal());
        if (art == null) {
            return;
        }
        previewRect.set(buttonX[BUTTON_GRENADE] - radius * 0.78f,
                buttonY[BUTTON_GRENADE] - radius * 0.78f,
                buttonX[BUTTON_GRENADE] + radius * 0.78f,
                buttonY[BUTTON_GRENADE] + radius * 0.78f);
        previewPaint.setAlpha(Math.round(255f * opacity));
        canvas.drawBitmap(art, null, previewRect, previewPaint);
        previewPaint.setAlpha(255);
    }

    private Bitmap weaponPreview(HudSnapshot state, int slot) {
        if (state == null || slot < 0 || slot >= state.kit.length) {
            return null;
        }
        int ordinal = state.kit[slot];
        if (ordinal < 0 || ordinal >= WEAPON_TYPES.length) {
            return null;
        }
        int skin = slot < state.kitSkin.length ? state.kitSkin[slot] : 0;
        String key = ordinal + ":" + skin;
        Bitmap cached = weaponPreviews.get(key);
        if (cached == null || cached.isRecycled()) {
            cached = CosmeticPreview.renderWeapon(WEAPON_TYPES[ordinal], skin,
                    Math.max(96, Math.round(112f * density)),
                    Math.max(54, Math.round(64f * density)));
            weaponPreviews.put(key, cached);
        }
        return cached;
    }

    /**
     * The arsenal button only exists while the round's buy window is open, so it never sits dead
     * on screen mid-firefight. The arrange screen always shows it, or it could not be moved.
     */
    private boolean isButtonVisible(int button) {
        if (isLoadoutSlotButton(button)) {
            if (editMode) return true;
            int slot = slotForButton(button);
            return snapshot != null && slot >= 0 && slot < snapshot.kit.length
                    && snapshot.kit[slot] >= 0;
        }
        if (button == BUTTON_GRENADE) {
            return editMode || (snapshot != null
                    && snapshot.kit[WeaponSlot.GRENADE.ordinal()] >= 0
                    && snapshot.grenadeCount > 0);
        }
        if (button == BUTTON_BUY) {
            return editMode || buyAvailable;
        }
        if (button == BUTTON_AIM) {
            // Without optics there is nothing to raise: iron sights neither zoom nor change the
            // view, so the button would be a control that does nothing the player can see.
            return editMode || (snapshot != null && snapshot.hasScope);
        }
        if (button == BUTTON_RELOAD) {
            // A knife has nothing to reload; hiding the dead control also keeps it clear of the
            // MELEE readout after the persistent slot is selected.
            return editMode || (snapshot != null && !snapshot.melee && !snapshot.grenade);
        }
        return true;
    }

    /** A shopping trolley: basket, handle and two wheels, drawn to the button's radius. */
    private void drawTrolleyGlyph(Canvas canvas, float cx, float cy, float radius, int ink,
                                  float unit) {
        float width = radius * 0.86f;
        float height = radius * 0.52f;
        float line = Math.max(1.4f * unit, radius * 0.085f);
        stroke.setColor(ink);
        stroke.setStrokeWidth(line);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);

        float left = cx - width * 0.42f;
        float right = cx + width * 0.62f;
        float top = cy - height * 0.45f;
        float bottom = cy + height * 0.45f;

        // Basket: a tapered box, so it reads as a trolley rather than a crate.
        path.reset();
        path.moveTo(left, top);
        path.lineTo(right, top);
        path.lineTo(right - width * 0.12f, bottom);
        path.lineTo(left + width * 0.10f, bottom);
        path.close();
        canvas.drawPath(path, stroke);

        // Handle, running back and up from the basket's near corner.
        canvas.drawLine(left, top, left - width * 0.28f, top - height * 0.55f, stroke);
        canvas.drawLine(left - width * 0.28f, top - height * 0.55f,
                left - width * 0.52f, top - height * 0.55f, stroke);

        fill.setColor(ink);
        float wheel = Math.max(1.3f * unit, radius * 0.10f);
        canvas.drawCircle(left + width * 0.26f, bottom + wheel * 1.5f, wheel, fill);
        canvas.drawCircle(right - width * 0.26f, bottom + wheel * 1.5f, wheel, fill);
        stroke.setStrokeCap(Paint.Cap.BUTT);
        stroke.setStrokeJoin(Paint.Join.MITER);
    }

    /** The aim button reads as a reticle rather than a word: a ring with four ticks. */
    private void drawAimGlyph(Canvas canvas, float cx, float cy, float radius, int ink,
                              float unit) {
        float ring = radius * 0.46f;
        stroke.setColor(ink);
        stroke.setStrokeWidth(Math.max(1.4f * unit, radius * 0.075f));
        canvas.drawCircle(cx, cy, ring, stroke);

        float inner = ring * 1.28f;
        float outer = radius * 0.86f;
        canvas.drawLine(cx - outer, cy, cx - inner, cy, stroke);
        canvas.drawLine(cx + inner, cy, cx + outer, cy, stroke);
        canvas.drawLine(cx, cy - outer, cx, cy - inner, stroke);
        canvas.drawLine(cx, cy + inner, cx, cy + outer, stroke);

        fill.setColor(ink);
        canvas.drawCircle(cx, cy, Math.max(1.2f * unit, radius * 0.07f), fill);
    }

    private static int withOpacity(int argb, float opacity) {
        int alpha = (int) (((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, opacity)));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /** The arrange screen: buttons at their live size, labelled, with the dragged one lit. */
    private void drawEditor(Canvas canvas, float unit) {
        drawJoystick(canvas, unit);
        drawFireZone(canvas, unit);
        drawButtons(canvas, EDITOR_STATE, unit);
        drawVitals(canvas, EDITOR_STATE, unit);
        drawAmmo(canvas, EDITOR_STATE, unit);

        if (editButton == ITEM_FIRE_ZONE) {
            stroke.setStrokeWidth(2.4f * unit);
            stroke.setColor(0xFFFFB68A);
            rect.set(fireZone.left - 5f * unit, fireZone.top - 5f * unit,
                    fireZone.right + 5f * unit, fireZone.bottom + 5f * unit);
            canvas.drawRoundRect(rect, 14f * unit, 14f * unit, stroke);
        } else if (isLoadoutSlotButton(editButton)) {
            stroke.setStrokeWidth(2.4f * unit);
            stroke.setColor(0xFFFFB68A);
            loadoutSlotBounds(editButton, rect);
            rect.inset(-6f * unit, -6f * unit);
            canvas.drawRoundRect(rect, rect.height() * 0.5f, rect.height() * 0.5f, stroke);
        } else if (editButton == ITEM_WEAPON_INFO || editButton == ITEM_VITALS) {
            stroke.setStrokeWidth(2.4f * unit);
            stroke.setColor(0xFFFFB68A);
            if (editButton == ITEM_WEAPON_INFO) {
                weaponInfoBounds(rect);
            } else {
                vitalsBounds(rect);
            }
            rect.inset(-5f * unit, -5f * unit);
            canvas.drawRoundRect(rect, 8f * unit, 8f * unit, stroke);
        } else if (editButton >= 0) {
            stroke.setStrokeWidth(2.4f * unit);
            stroke.setColor(0xFFFFB68A);
            float cx = editButton == ITEM_STICK ? stickRestX : buttonX[editButton];
            float cy = editButton == ITEM_STICK ? stickRestY : buttonY[editButton];
            float radius = editButton == ITEM_STICK ? joystickRadius : buttonRadius[editButton];
            canvas.drawCircle(cx, cy, radius + 6f * unit, stroke);
        }

        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(13f * unit);
        text.setColor(0xCCFFFFFF);
        canvas.drawText("Drag a control to move it · tap to resize it", getWidth() * 0.5f,
                getHeight() * 0.5f, text);
    }

    private boolean isButtonActive(int button, HudSnapshot state) {
        if (editMode) {
            return button == editButton;
        }
        switch (button) {
            case BUTTON_FIRE:
                return input != null && input.fireHeld;
            case BUTTON_AIM:
                return state.aiming;
            case BUTTON_CROUCH:
                return input != null && input.crouching;
            case BUTTON_SLOT_MELEE:
                return state.slot == WeaponSlot.MELEE.ordinal();
            case BUTTON_SLOT_PRIMARY:
                return state.slot == WeaponSlot.PRIMARY.ordinal();
            case BUTTON_SLOT_SECONDARY:
                return state.slot == WeaponSlot.SECONDARY.ordinal();
            case BUTTON_SCOREBOARD:
                return scoreboardVisible;
            default:
                return false;
        }
    }

    /** Draws one SVG-backed action glyph at the requested HUD size and active-state tint. */
    private void drawVectorGlyph(Canvas canvas, Drawable drawable, float cx, float cy,
                                 float halfSize, int ink) {
        if (drawable == null) {
            return;
        }
        drawable.setTint(0xFF000000 | (ink & 0x00FFFFFF));
        drawable.setAlpha((ink >>> 24) & 0xFF);
        drawable.setBounds(Math.round(cx - halfSize), Math.round(cy - halfSize),
                Math.round(cx + halfSize), Math.round(cy + halfSize));
        drawable.draw(canvas);
        drawable.setAlpha(255);
    }

    private void drawVitals(Canvas canvas, HudSnapshot state, float unit) {
        float drawUnit = unit * vitalsScale;
        float x = vitalsX;
        float y = vitalsY;
        float width = 150f * drawUnit;
        float height = 9f * drawUnit;

        fill.setColor(0x77000000);
        rect.set(x, y, x + width, y + height);
        canvas.drawRoundRect(rect, height * 0.5f, height * 0.5f, fill);
        float health = Math.max(0f, Math.min(1f, state.health / 100f));
        fill.setColor(health > 0.55f ? 0xFF7BE08A : health > 0.25f ? 0xFFFFC66B : 0xFFFF6B5A);
        rect.set(x, y, x + width * health, y + height);
        canvas.drawRoundRect(rect, height * 0.5f, height * 0.5f, fill);

        float armorY = y + height + 5f * drawUnit;
        fill.setColor(0x77000000);
        rect.set(x, armorY, x + width, armorY + height * 0.65f);
        canvas.drawRoundRect(rect, height * 0.4f, height * 0.4f, fill);
        float armor = Math.max(0f, Math.min(1f, state.armor / 100f));
        fill.setColor(0xFF6BB6FF);
        rect.set(x, armorY, x + width * armor, armorY + height * 0.65f);
        canvas.drawRoundRect(rect, height * 0.4f, height * 0.4f, fill);

        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(15f * drawUnit);
        text.setColor(Color.WHITE);
        canvas.drawText(String.valueOf(Math.round(state.health)), x, y - 6f * drawUnit, text);

        if (!state.alive) {
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(20f * unit);
            text.setColor(0xFFFF6B5A);
            String message = state.mode == GameMode.ZOMBIE ? "ELIMINATED"
                    : state.mode == GameMode.BOMB_PLANT ? "WAITING FOR NEXT ROUND"
                    : "RESPAWN IN " + (int) Math.ceil(state.respawnTimer);
            canvas.drawText(message, getWidth() * 0.5f, getHeight() * 0.5f - 40f * unit, text);
        }
    }

    private void drawAmmo(Canvas canvas, HudSnapshot state, float unit) {
        float drawUnit = unit * weaponInfoScale;
        float x = weaponInfoX;
        float y = weaponInfoY;
        text.setTextAlign(Paint.Align.RIGHT);
        text.setColor(Color.WHITE);
        text.setTextSize(26f * drawUnit);
        String ammo = state.melee ? "—" : state.noReload ? "∞" : String.valueOf(state.ammo);
        canvas.drawText(ammo, x, y, text);
        text.setTextSize(13f * drawUnit);
        text.setColor(0xB3FFFFFF);
        canvas.drawText(state.melee ? "MELEE" : state.infiniteAmmo || state.noReload ? "/ ∞" : "/ " + state.reserve, x,
                y + 16f * drawUnit, text);
        text.setTextSize(12f * drawUnit);
        text.setColor(0xFFFFB68A);
        if (!state.hardcoreHud) canvas.drawText(state.weaponName, x, y - 26f * drawUnit, text);

        if (state.freeArsenal && !state.hardcoreHud) {
            // Nothing to spend and no window to miss: just say the whole rack is open.
            text.setTextSize(11f * drawUnit);
            text.setColor(0xB39BE07B);
            canvas.drawText("ALL WEAPONS", x, y - 40f * drawUnit, text);
        } else if (!state.freeArsenal && state.mode.buyMenu()) {
            // Round money sits above the weapon name; it is what the buy menu spends.
            text.setTextSize(15f * drawUnit);
            text.setColor(state.buyOpen ? 0xFF9BE07B : 0x8CFFFFFF);
            canvas.drawText("$" + state.money, x, y - 46f * drawUnit, text);
            if (state.buyOpen && state.alive && !state.hardcoreHud) {
                text.setTextSize(11f * drawUnit);
                text.setColor(0xB39BE07B);
                canvas.drawText("BUY " + (int) Math.ceil(state.buyTimeLeft) + "s", x,
                        y - 33f * drawUnit, text);
            }
        }
    }

    private void drawScoreHeader(Canvas canvas, HudSnapshot state, float unit) {
        float cx = getWidth() * 0.5f;
        text.setTextAlign(Paint.Align.CENTER);

        if (state.mode.teamBased()) {
            text.setTextSize(20f * unit);
            text.setColor(0xFF4FA3FF);
            canvas.drawText(String.valueOf(state.blueScore), cx - 44f * unit, 26f * unit, text);
            text.setColor(0xFFFF6B5A);
            canvas.drawText(String.valueOf(state.redScore), cx + 44f * unit, 26f * unit, text);
            text.setColor(0x99FFFFFF);
            text.setTextSize(12f * unit);
            canvas.drawText("/" + state.scoreLimit, cx, 26f * unit, text);
        } else if (state.mode == GameMode.ZOMBIE) {
            text.setTextSize(17f * unit);
            text.setColor(0xFF9BE07B);
            canvas.drawText("WAVE " + state.wave + " · " + state.enemiesAlive + " LEFT", cx,
                    26f * unit, text);
        } else if (state.mode == GameMode.GUN_GAME) {
            text.setTextSize(17f * unit);
            text.setColor(0xFFFFB68A);
            canvas.drawText("LEVEL " + (state.ladderStep + 1) + " / " + state.ladderTotal, cx,
                    26f * unit, text);
        } else {
            text.setTextSize(17f * unit);
            text.setColor(0xFFFFB68A);
            canvas.drawText(state.kills + " / " + state.scoreLimit, cx, 26f * unit, text);
        }

        if (state.timed) {
            int seconds = (int) state.timeLeft;
            text.setTextSize(13f * unit);
            text.setColor(seconds < 30 ? 0xFFFF6B5A : 0xCCFFFFFF);
            canvas.drawText(String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60), cx,
                    42f * unit, text);
        }
        if (!state.hardcoreHud && !state.killCam && !state.areaName.isEmpty()) {
            text.setTextSize(11f * unit);
            text.setColor(0xFFE1D6B4);
            canvas.drawText(state.areaName, cx, 58f * unit, text);
        }
    }

    /**
     * The practice range's own scoreboard. Range shooting is kept out of the career stats on
     * purpose — standing targets would flatter them — so without this the player has no feedback
     * at all on whether the session is going well.
     */
    /** Names the replay while it runs, so the jump to another viewpoint reads as deliberate. */
    private void drawKillCamBanner(Canvas canvas, HudSnapshot state, float unit) {
        if (!state.killCam) {
            return;
        }
        float centreX = getWidth() * 0.5f;
        float top = 54f * unit;
        rect.set(centreX - 78f * unit, top, centreX + 78f * unit, top + 30f * unit);
        fill.setColor(0xAA1B1008);
        canvas.drawRoundRect(rect, 15f * unit, 15f * unit, fill);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(0xFFFFB68A);
        text.setTextSize(13f * unit);
        canvas.drawText("KILLCAM", centreX, top + 20f * unit, text);
    }

    private void drawRangeScore(Canvas canvas, HudSnapshot state, float unit) {
        if (state.mode != GameMode.RANGE) {
            return;
        }
        float right = getWidth() - 18f * unit;
        // Below the scoreboard and pause buttons, which occupy the corner down to ~41 units.
        float top = 50f * unit;
        float width = 132f * unit;
        float height = 52f * unit;
        rect.set(right - width, top, right, top + height);
        fill.setColor(0x99101010);
        canvas.drawRoundRect(rect, 8f * unit, 8f * unit, fill);

        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(0xCCFFFFFF);
        text.setTextSize(10f * unit);
        canvas.drawText("RANGE", rect.left + 10f * unit, rect.top + 15f * unit, text);

        int accuracy = state.rangeShots == 0 ? 0
                : Math.round(state.rangeHits * 100f / state.rangeShots);
        text.setTextSize(17f * unit);
        text.setColor(0xFFFFB68A);
        canvas.drawText(state.rangeShots == 0 ? "--" : accuracy + "%",
                rect.left + 10f * unit, rect.top + 36f * unit, text);

        text.setTextAlign(Paint.Align.RIGHT);
        text.setTextSize(10f * unit);
        text.setColor(0xCCFFFFFF);
        canvas.drawText(state.rangeHits + " / " + state.rangeShots,
                rect.right - 10f * unit, rect.top + 22f * unit, text);
        canvas.drawText(state.rangeHeadshots + " HS", rect.right - 10f * unit,
                rect.top + 38f * unit, text);
        text.setTextAlign(Paint.Align.CENTER);
    }

    private void drawMinimap(Canvas canvas, HudSnapshot state, float unit) {
        float size = 78f * unit;
        float left = 18f * unit;
        float top = 18f * unit;
        float cx = left + size * 0.5f;
        float cy = top + size * 0.5f;

        fill.setColor(0x99101010);
        rect.set(left, top, left + size, top + size);
        canvas.drawRoundRect(rect, 12f * unit, 12f * unit, fill);

        canvas.save();
        path.reset();
        path.addRoundRect(rect, 12f * unit, 12f * unit, Path.Direction.CW);
        canvas.clipPath(path);

        if (minimap != null) {
            // Zoomed, player-centred, north-up view of the baked map image.
            float zoom = size / 46f;
            minimapMatrix.reset();
            minimapMatrix.postTranslate(-state.playerX, -state.playerZ);
            minimapMatrix.postScale(zoom, zoom);
            minimapMatrix.postTranslate(cx, cy);
            canvas.drawBitmap(minimap, minimapMatrix, null);
        }

        for (int i = 0; i < state.objectiveCount; i++) {
            float zoom = size / 46f;
            float x = cx + (state.objectiveX[i] - state.playerX) * zoom;
            float y = cy + (state.objectiveZ[i] - state.playerZ) * zoom;
            stroke.setColor(state.objectiveColor[i]);
            stroke.setStrokeWidth(1.5f * unit);
            canvas.drawCircle(x, y, 4f * zoom, stroke);
            text.setColor(state.objectiveColor[i]);
            text.setTextSize(9f * unit);
            text.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(String.valueOf((char) ('A' + i)), x, y + 3f * unit, text);
        }
        for (int i = 0; i < state.blipCount; i++) {
            float zoom = size / 46f;
            float bx = cx + (state.blipX[i] - state.playerX) * zoom;
            float by = cy + (state.blipZ[i] - state.playerZ) * zoom;
            fill.setColor(state.blipColor[i]);
            canvas.drawCircle(bx, by, (state.blipEnemy[i] ? 3.4f : 2.8f) * unit, fill);
        }

        // Player arrow.
        canvas.save();
        canvas.rotate((float) Math.toDegrees(state.playerYaw), cx, cy);
        path.reset();
        path.moveTo(cx, cy - 6f * unit);
        path.lineTo(cx - 4.2f * unit, cy + 4.5f * unit);
        path.lineTo(cx + 4.2f * unit, cy + 4.5f * unit);
        path.close();
        fill.setColor(0xFFFFFFFF);
        canvas.drawPath(path, fill);
        canvas.restore();
        canvas.restore();

        stroke.setStrokeWidth(1.4f * unit);
        stroke.setColor(0x55FFFFFF);
        canvas.drawRoundRect(rect, 12f * unit, 12f * unit, stroke);
    }

    private void drawKillFeed(Canvas canvas, HudSnapshot state, float unit) {
        float right = KillFeedRenderer.rightEdge(getWidth(), unit);
        float left = KillFeedRenderer.leftEdge(getWidth(), unit);
        float top = KillFeedRenderer.firstBaseline(state, unit) - 13f * unit;
        float bottom = getHeight();
        weaponInfoBounds(killFeedAvoidanceBounds);
        bottom = avoidFeedOverlap(killFeedAvoidanceBounds, left, right, top, bottom, unit);
        vitalsBounds(killFeedAvoidanceBounds);
        bottom = avoidFeedOverlap(killFeedAvoidanceBounds, left, right, top, bottom, unit);
        if (touchControlsVisible) {
            for (int i = 0; i < BUTTON_COUNT; i++) {
                if (!isButtonVisible(i)) continue;
                if (isLoadoutSlotButton(i)) {
                    loadoutSlotBounds(i, killFeedAvoidanceBounds);
                } else {
                    float radius = buttonRadius[i];
                    killFeedAvoidanceBounds.set(buttonX[i] - radius, buttonY[i] - radius,
                            buttonX[i] + radius, buttonY[i] + radius);
                }
                bottom = avoidFeedOverlap(killFeedAvoidanceBounds, left, right, top, bottom, unit);
            }
            killFeedAvoidanceBounds.set(stickRestX - joystickRadius, stickRestY - joystickRadius,
                    stickRestX + joystickRadius, stickRestY + joystickRadius);
            bottom = avoidFeedOverlap(killFeedAvoidanceBounds, left, right, top, bottom, unit);
        }
        killFeedRenderer.draw(canvas, state, getWidth(), getHeight(), unit, bottom);
    }

    private static float avoidFeedOverlap(RectF bounds, float left, float right, float top,
                                         float bottom, float unit) {
        if (bounds.left < right && bounds.right > left && bounds.bottom > top
                && bounds.top < bottom) {
            return Math.min(bottom, bounds.top - 5f * unit);
        }
        return bottom;
    }

    private void drawDamageIndicator(Canvas canvas, HudSnapshot state, float unit) {
        if (state.damageAmount <= 0.01f) {
            return;
        }
        float cx = getWidth() * 0.5f;
        float cy = getHeight() * 0.5f;
        canvas.save();
        canvas.rotate((float) Math.toDegrees(state.damageDirection), cx, cy);
        path.reset();
        float radius = 90f * unit;
        // Point away from the player, toward the damage source. Keep the
        // existing bounds and rotation so only the arrow's orientation changes.
        path.moveTo(cx, cy - radius);
        path.lineTo(cx + 26f * unit, cy - radius + 20f * unit);
        path.lineTo(cx - 26f * unit, cy - radius + 20f * unit);
        path.close();
        fill.setColor(((int) (state.damageAmount * 210) << 24) | 0x00FF4436);
        canvas.drawPath(path, fill);
        canvas.restore();
    }

    private void drawBanner(Canvas canvas, HudSnapshot state, float unit) {
        if (state.banner == null || state.banner.isEmpty()) {
            return;
        }
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(28f * unit);
        float alpha = Math.min(1f, state.bannerLife);
        text.setColor(((int) (alpha * 255) << 24) | 0x00FFB68A);
        canvas.drawText(state.banner, getWidth() * 0.5f, getHeight() * 0.28f, text);
    }

    private void drawScoreboard(Canvas canvas, HudSnapshot state, float unit) {
        if (state.mode == GameMode.BATTLE_FIELD && state.scoreboardCount > 24) {
            drawBattlefieldScoreboard(canvas, state, unit);
            return;
        }
        float width = Math.min(getWidth() * 0.62f, 420f * unit);
        float rowHeight = 20f * unit;
        float height = rowHeight * (state.scoreboardCount + 2);
        float left = (getWidth() - width) * 0.5f;
        float top = (getHeight() - height) * 0.5f;

        fill.setColor(0xE6121212);
        rect.set(left, top, left + width, top + height);
        canvas.drawRoundRect(rect, 14f * unit, 14f * unit, fill);

        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(12f * unit);
        text.setColor(0x99FFFFFF);
        float y = top + rowHeight;
        canvas.drawText("PLAYER", left + 14f * unit, y, text);
        text.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("K", left + width - 96f * unit, y, text);
        canvas.drawText("D", left + width - 56f * unit, y, text);
        canvas.drawText("SCORE", left + width - 14f * unit, y, text);

        for (int i = 0; i < state.scoreboardCount; i++) {
            y += rowHeight;
            if (state.rowIsPlayer[i]) {
                fill.setColor(0x33FFB68A);
                rect.set(left + 6f * unit, y - rowHeight * 0.75f, left + width - 6f * unit,
                        y + rowHeight * 0.25f);
                canvas.drawRoundRect(rect, 8f * unit, 8f * unit, fill);
            }
            text.setTextAlign(Paint.Align.LEFT);
            text.setTextSize(13f * unit);
            text.setColor(state.rowColor[i]);
            canvas.drawText(state.rowName[i], left + 14f * unit, y, text);
            text.setTextAlign(Paint.Align.RIGHT);
            text.setColor(0xFFFFFFFF);
            canvas.drawText(String.valueOf(state.rowKills[i]), left + width - 96f * unit, y, text);
            canvas.drawText(String.valueOf(state.rowDeaths[i]), left + width - 56f * unit, y, text);
            canvas.drawText(String.valueOf(state.rowScore[i]), left + width - 14f * unit, y, text);
        }
    }

    /** Two compact 50-player columns keep a full Battle Field roster on a landscape screen. */
    private void drawBattlefieldScoreboard(Canvas canvas, HudSnapshot state, float unit) {
        int blueRows = scoreboardTeamCount(state, Team.BLUE.color());
        int redRows = scoreboardTeamCount(state, Team.RED.color());
        int rows = Math.max(blueRows, redRows);
        float maxHeight = getHeight() * 0.90f;
        float rowHeight = Math.min(18f * unit, maxHeight / Math.max(3, rows + 2));
        float height = rowHeight * (rows + 2);
        float width = Math.min(getWidth() * 0.94f, 800f * unit);
        float left = (getWidth() - width) * 0.5f;
        float top = (getHeight() - height) * 0.5f;
        float gap = 8f * unit;
        float columnWidth = (width - gap) * 0.5f;

        fill.setColor(0xE6121212);
        rect.set(left, top, left + width, top + height);
        canvas.drawRoundRect(rect, 14f * unit, 14f * unit, fill);

        drawBattlefieldScoreboardColumn(canvas, state, Team.BLUE.color(), "BLUE · " + blueRows,
                left, top, columnWidth, rowHeight, unit);
        drawBattlefieldScoreboardColumn(canvas, state, Team.RED.color(), "RED · " + redRows,
                left + columnWidth + gap, top, columnWidth, rowHeight, unit);
    }

    private int scoreboardTeamCount(HudSnapshot state, int teamColor) {
        int count = 0;
        for (int i = 0; i < state.scoreboardCount; i++) {
            if (state.rowColor[i] == teamColor) {
                count++;
            }
        }
        return count;
    }

    private void drawBattlefieldScoreboardColumn(Canvas canvas, HudSnapshot state, int teamColor,
                                                 String title, float left, float top, float width,
                                                 float rowHeight, float unit) {
        float headerY = top + rowHeight;
        float fontSize = Math.min(12f * unit, rowHeight * 0.66f);
        text.setTextSize(fontSize);
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(teamColor);
        canvas.drawText(title, left + 10f * unit, headerY, text);
        text.setTextAlign(Paint.Align.RIGHT);
        text.setColor(0x99FFFFFF);
        canvas.drawText("K", left + width - 74f * unit, headerY, text);
        canvas.drawText("D", left + width - 43f * unit, headerY, text);
        canvas.drawText("S", left + width - 10f * unit, headerY, text);

        int row = 0;
        for (int i = 0; i < state.scoreboardCount; i++) {
            if (state.rowColor[i] != teamColor) {
                continue;
            }
            float y = top + rowHeight * (row + 2);
            if (state.rowIsPlayer[i]) {
                fill.setColor(0x33FFB68A);
                rect.set(left + 4f * unit, y - rowHeight * 0.75f,
                        left + width - 4f * unit, y + rowHeight * 0.25f);
                canvas.drawRoundRect(rect, 6f * unit, 6f * unit, fill);
            }
            text.setTextSize(fontSize);
            text.setTextAlign(Paint.Align.LEFT);
            text.setColor(teamColor);
            canvas.drawText(state.rowName[i], left + 10f * unit, y, text);
            text.setTextAlign(Paint.Align.RIGHT);
            text.setColor(0xFFFFFFFF);
            canvas.drawText(String.valueOf(state.rowKills[i]), left + width - 74f * unit, y, text);
            canvas.drawText(String.valueOf(state.rowDeaths[i]), left + width - 43f * unit, y, text);
            canvas.drawText(String.valueOf(state.rowScore[i]), left + width - 10f * unit, y, text);
            row++;
        }
    }
}
