package com.ccs.voxelstrike.input;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.ccs.voxelstrike.app.KeyBindings;
import com.ccs.voxelstrike.weapon.WeaponSlot;

/**
 * Physical keyboard and mouse handling, for Chromebooks, DeX, tablets with a keyboard case and
 * emulators.
 *
 * <p>Movement keys are digital, so the four directions are combined into a unit vector before
 * they reach the simulation. Mouse look prefers pointer capture, which delivers relative motion
 * with no cursor to run into the screen edge; without capture it falls back to hover deltas.
 */
public final class DeviceInput {
    /** Ignore deltas larger than this; they are cursor warps, not aim. */
    private static final float MAX_STEP = 220f;

    private final InputState input;
    private final KeyBindings bindings;

    private boolean forward;
    private boolean back;
    private boolean left;
    private boolean right;
    private boolean crouchHeld;
    private boolean scoreboardHeld;

    private float lastHoverX;
    private float lastHoverY;
    private boolean hasHover;
    private boolean captured;

    private Runnable pauseListener;
    private Runnable activityListener;
    private Runnable keyboardActivityListener;
    private ScoreboardListener scoreboardListener;
    private Runnable buyListener;

    public DeviceInput(InputState input, KeyBindings bindings) {
        this.input = input;
        this.bindings = bindings;
    }

    public void setPauseListener(Runnable listener) {
        pauseListener = listener;
    }

    /** Called the first time a physical key or mouse event arrives. */
    public void setActivityListener(Runnable listener) {
        activityListener = listener;
    }

    public void setKeyboardActivityListener(Runnable listener) {
        keyboardActivityListener = listener;
    }

    /** Fired when the buy menu key is pressed; the activity decides whether it may open. */
    public void setBuyListener(Runnable listener) {
        buyListener = listener;
    }

    public void setScoreboardListener(ScoreboardListener listener) {
        scoreboardListener = listener;
    }

    public void setCaptured(boolean value) {
        captured = value;
        hasHover = false;
    }

    public boolean isCaptured() {
        return captured;
    }

    /** Clears every held key; used when the match pauses or focus is lost. */
    public void releaseAll() {
        forward = false;
        back = false;
        left = false;
        right = false;
        crouchHeld = false;
        scoreboardHeld = false;
        hasHover = false;
        input.crouching = false;
        input.setKeyboardMove(0f, 0f);
        input.setDeviceFire(false);
        input.setDeviceAim(false);
        input.setDeviceSprint(false);
        input.setDeviceJump(false);
        if (scoreboardListener != null) {
            scoreboardListener.onScoreboard(false);
        }
    }

    /** @return true when the event was consumed */
    public boolean onKey(KeyEvent event) {
        if (!isKeyboard(event) || isSystemKey(event.getKeyCode())) {
            return false;
        }
        int action = event.getAction();
        if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) {
            return false;
        }
        KeyBindings.Action bound = bindings.actionFor(event.getKeyCode());
        if (bound == null) {
            return false;
        }
        boolean down = action == KeyEvent.ACTION_DOWN;
        // Auto-repeat would re-trigger one-shot actions like jump every frame.
        if (down && event.getRepeatCount() > 0) {
            return true;
        }
        if (event.isFromSource(InputDevice.SOURCE_KEYBOARD) && keyboardActivityListener != null) {
            keyboardActivityListener.run();
        }
        notifyActivity();
        apply(bound, down);
        return true;
    }

    void apply(KeyBindings.Action action, boolean down) {
        switch (action) {
            case FORWARD:
                forward = down;
                updateMove();
                break;
            case COURSE_RESTART:
                if (down) input.queueCourseRestart();
                break;
            case BACK:
                back = down;
                updateMove();
                break;
            case LEFT:
                left = down;
                updateMove();
                break;
            case RIGHT:
                right = down;
                updateMove();
                break;
            case JUMP:
                input.setDeviceJump(down);
                if (down) {
                    input.queueJump();
                }
                break;
            case CROUCH:
                // Hold to crouch, which is what a keyboard player expects.
                crouchHeld = down;
                input.crouching = down;
                break;
            case SPRINT:
                input.setDeviceSprint(down);
                break;
            case FIRE:
                input.setDeviceFire(down);
                break;
            case AIM:
                input.setDeviceAim(down);
                break;
            case RELOAD:
                if (down) {
                    input.queueReload();
                }
                break;
            case MELEE:
                if (down) {
                    input.requestSlot(WeaponSlot.MELEE.ordinal());
                }
                break;
            case GRENADE:
                if (down) {
                    input.queueGrenade();
                }
                break;
            case SLOT_PRIMARY:
                if (down) {
                    input.requestSlot(0);
                }
                break;
            case SLOT_SECONDARY:
                if (down) {
                    input.requestSlot(1);
                }
                break;
            case BUY:
                if (down && buyListener != null) {
                    buyListener.run();
                }
                break;
            case SCOREBOARD:
                scoreboardHeld = down;
                if (scoreboardListener != null) {
                    scoreboardListener.onScoreboard(down);
                }
                break;
            case PAUSE:
                if (down && pauseListener != null) {
                    pauseListener.run();
                }
                break;
            default:
                break;
        }
    }

    private void updateMove() {
        float x = (right ? 1f : 0f) - (left ? 1f : 0f);
        float y = (forward ? 1f : 0f) - (back ? 1f : 0f);
        if (x != 0f && y != 0f) {
            // Diagonals must not be faster than the cardinals.
            float inverse = 0.70710678f;
            x *= inverse;
            y *= inverse;
        }
        input.setKeyboardMove(x, y);
    }

    /** Relative motion from a captured pointer. */
    public boolean onCapturedPointer(MotionEvent event) {
        notifyActivity();
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            addLook(event.getX(), event.getY());
        }
        updateMouseButtons(event);
        return true;
    }

    /** Hover and button events from an uncaptured mouse. */
    public boolean onGenericMotion(MotionEvent event) {
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            return false;
        }
        notifyActivity();
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_HOVER_MOVE || action == MotionEvent.ACTION_MOVE) {
            if (hasHover) {
                addLook(event.getX() - lastHoverX, event.getY() - lastHoverY);
            }
            lastHoverX = event.getX();
            lastHoverY = event.getY();
            hasHover = true;
        } else if (action == MotionEvent.ACTION_HOVER_EXIT) {
            hasHover = false;
        }
        updateMouseButtons(event);
        return true;
    }

    /** Mouse button presses arriving as touch-like events when the mouse is not captured. */
    public boolean onMouseTouch(MotionEvent event) {
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            return false;
        }
        notifyActivity();
        updateMouseButtons(event);
        return true;
    }

    private void updateMouseButtons(MotionEvent event) {
        int buttons = event.getButtonState();
        input.setDeviceFire((buttons & MotionEvent.BUTTON_PRIMARY) != 0);
        input.setDeviceAim((buttons & MotionEvent.BUTTON_SECONDARY) != 0);
    }

    private void addLook(float dx, float dy) {
        if (Math.abs(dx) > MAX_STEP || Math.abs(dy) > MAX_STEP) {
            return;
        }
        float scale = bindings.mouseSensitivity;
        input.addLook(dx * scale, (bindings.invertY ? -dy : dy) * scale);
    }

    private void notifyActivity() {
        if (activityListener != null) {
            activityListener.run();
        }
    }

    /** Navigation and hardware keys stay with the system and can never be bound. */
    private static boolean isSystemKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME
                || keyCode == KeyEvent.KEYCODE_APP_SWITCH || keyCode == KeyEvent.KEYCODE_POWER
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN;
    }

    private static boolean isKeyboard(KeyEvent event) {
        // Volume and system keys arrive with the same source; the binding lookup filters those.
        return event.getDevice() == null
                || (event.getSource() & InputDevice.SOURCE_KEYBOARD) != 0
                || (event.getSource() & InputDevice.SOURCE_GAMEPAD) != 0;
    }

    /** Lets the HUD show the scoreboard while the key is held. */
    public interface ScoreboardListener {
        void onScoreboard(boolean visible);
    }
}
