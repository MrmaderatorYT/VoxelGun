package com.ccs.voxelgun.input;

/**
 * Mailbox between the touch HUD (UI thread) and the simulation (GL thread).
 *
 * <p>Continuous values are volatile fields; one-shot actions are latched and consumed exactly
 * once by the simulation so a tap can never be missed or applied twice.
 */
public final class InputState {
    /**
     * Resolved values the simulation reads. Touch and keyboard/mouse both write through the
     * setters below, which merge the two so neither source can cancel the other out.
     */
    public volatile float moveX;
    public volatile float moveY;
    public volatile boolean fireHeld;
    public volatile boolean aimHeld;
    public volatile boolean crouching;
    public volatile boolean sprinting;
    /** Device sprint key (Shift by default): slide/crouch alias only in Bhop matches. */
    public volatile boolean slideHeld;
    public volatile boolean paused;
    /** Gameplay preference: hop only when forward movement meets a climbable ledge. */
    public volatile boolean autoJump;
    /** Held jump from either input source; the match rules decide whether to repeat it. */
    public volatile boolean jumpHeld;
    public volatile float lookSensitivity = 1f;
    public volatile float adsSensitivity = 0.6f;
    /** Applied instead of {@link #adsSensitivity} while a scoped weapon is at the eye. */
    public volatile float scopeSensitivity = 0.4f;

    private float touchMoveX;
    private float touchMoveY;
    private float keyMoveX;
    private float keyMoveY;
    private boolean touchFire;
    private boolean deviceFire;
    private boolean touchAim;
    private boolean deviceAim;
    private boolean touchSprint;
    private boolean deviceSprint;
    private boolean touchJump;
    private boolean deviceJump;

    private float lookX;
    private float lookY;
    private boolean jump;
    private boolean reload;
    private boolean melee;
    private boolean grenade;
    private int slotRequest = -1;
    private boolean courseRestart;
    public synchronized void queueCourseRestart() { courseRestart = true; }
    public synchronized boolean consumeCourseRestart() {
        boolean value = courseRestart; courseRestart = false; return value;
    }
    /** Buy menu taps waiting for the simulation; a short ring is plenty for finger speed. */
    private final int[] purchases = new int[8];
    private int purchaseCount;

    /** Movement from the on-screen stick. */
    public void setTouchMove(float x, float y) {
        touchMoveX = x;
        touchMoveY = y;
        resolveMove();
    }

    /** Movement from the keyboard; takes priority while any direction key is held. */
    public void setKeyboardMove(float x, float y) {
        keyMoveX = x;
        keyMoveY = y;
        resolveMove();
    }

    private void resolveMove() {
        boolean keyboardActive = keyMoveX != 0f || keyMoveY != 0f;
        moveX = keyboardActive ? keyMoveX : touchMoveX;
        moveY = keyboardActive ? keyMoveY : touchMoveY;
    }

    public void setTouchFire(boolean value) {
        touchFire = value;
        fireHeld = touchFire || deviceFire;
    }

    public void setDeviceFire(boolean value) {
        deviceFire = value;
        fireHeld = touchFire || deviceFire;
    }

    public void setTouchAim(boolean value) {
        touchAim = value;
        aimHeld = touchAim || deviceAim;
    }

    public void setDeviceAim(boolean value) {
        deviceAim = value;
        aimHeld = touchAim || deviceAim;
    }

    public void setTouchSprint(boolean value) {
        touchSprint = value;
        sprinting = touchSprint || deviceSprint;
    }

    public void setDeviceSprint(boolean value) {
        deviceSprint = value;
        slideHeld = value;
        sprinting = touchSprint || deviceSprint;
    }

    public synchronized void addLook(float dx, float dy) {
        lookX += dx;
        lookY += dy;
    }

    public synchronized void consumeLook(float[] out) {
        out[0] = lookX;
        out[1] = lookY;
        lookX = 0f;
        lookY = 0f;
    }

    public synchronized void queueJump() {
        jump = true;
    }

    public void setTouchJump(boolean held) {
        touchJump = held;
        jumpHeld = touchJump || deviceJump;
    }

    public void setDeviceJump(boolean held) {
        deviceJump = held;
        jumpHeld = touchJump || deviceJump;
    }

    /** Consumes an explicit jump press once. Held-jump repetition is a match modifier. */
    public synchronized boolean consumeJump() {
        boolean value = jump;
        jump = false;
        return value;
    }

    public synchronized void queueReload() {
        reload = true;
    }

    public synchronized boolean consumeReload() {
        boolean value = reload;
        reload = false;
        return value;
    }

    public synchronized void queueMelee() {
        melee = true;
    }

    public synchronized boolean consumeMelee() {
        boolean value = melee;
        melee = false;
        return value;
    }

    public synchronized void queueGrenade() {
        grenade = true;
    }

    public synchronized boolean consumeGrenade() {
        boolean value = grenade;
        grenade = false;
        return value;
    }

    /** Queues one buy menu purchase by {@code WeaponType} ordinal. */
    public synchronized void queuePurchase(int weaponOrdinal) {
        if (purchaseCount < purchases.length) {
            purchases[purchaseCount++] = weaponOrdinal;
        }
    }

    /** Returns the oldest queued purchase, or -1 when there is none. */
    public synchronized int consumePurchase() {
        if (purchaseCount == 0) {
            return -1;
        }
        int value = purchases[0];
        System.arraycopy(purchases, 1, purchases, 0, --purchaseCount);
        return value;
    }

    public synchronized void requestSlot(int slot) {
        slotRequest = slot;
    }

    public synchronized int consumeSlotRequest() {
        int value = slotRequest;
        slotRequest = -1;
        return value;
    }

    /** Drops every held input; used when the match pauses or the player dies. */
    public synchronized void releaseAll() {
        touchMoveX = 0f;
        touchMoveY = 0f;
        keyMoveX = 0f;
        keyMoveY = 0f;
        moveX = 0f;
        moveY = 0f;
        touchFire = false;
        deviceFire = false;
        touchAim = false;
        deviceAim = false;
        touchSprint = false;
        deviceSprint = false;
        touchJump = false;
        deviceJump = false;
        jumpHeld = false;
        fireHeld = false;
        aimHeld = false;
        crouching = false;
        sprinting = false;
        slideHeld = false;
        lookX = 0f;
        lookY = 0f;
        jump = false;
        reload = false;
        melee = false;
        grenade = false;
        slotRequest = -1;
        purchaseCount = 0;
        courseRestart = false;
    }
}
