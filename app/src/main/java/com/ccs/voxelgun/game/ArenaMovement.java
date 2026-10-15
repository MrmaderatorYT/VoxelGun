package com.ccs.voxelgun.game;

import com.ccs.voxelgun.input.InputState;

/** Allocation-free horizontal movement. Collision/gravity remain owned by Physics. */
public final class ArenaMovement {
    public static final float MAX_SPEED_FACTOR = 1.75f;
    public static final float SLIDE_SECONDS = 0.75f;
    public static final float BHOP_MAX_SPEED_FACTOR = 2.5f;
    public static final float HOP_WINDOW_SECONDS = 0.18f;
    public static final float JUMP_BUFFER_SECONDS = 0.10f;
    private float slideLeft;
    private float cooldown;
    private float slideX, slideZ;
    private boolean crouchHeld;
    private boolean wasGrounded = true;
    private boolean airCrouchArmed;
    private boolean bhopSlide;
    /** Persists through takeoff and crouch release, until a landing misses the combo window. */
    private boolean bhopChainActive;
    private float hopWindow, jumpBuffer, referenceSpeed;

    public void reset() {
        slideLeft = cooldown = 0f;
        crouchHeld = false;
        wasGrounded = true;
        airCrouchArmed = bhopSlide = bhopChainActive = false;
        hopWindow = jumpBuffer = referenceSpeed = 0f;
    }

    public boolean sliding() { return slideLeft > 0f; }
    public boolean hopReady() { return hopWindow > 0f; }
    public void bufferJump() { jumpBuffer = JUMP_BUFFER_SECONDS; }
    public boolean bufferedJump() { return jumpBuffer > 0f; }
    /** Called only after collision/headroom checks actually launched a jump. */
    public void onJump(Combatant p, boolean timedPress) {
        if (timedPress && hopReady() && referenceSpeed > 0f) {
            float speed = (float) Math.hypot(p.velocity.x, p.velocity.z);
            if (speed > 0.001f) {
                float boosted = Math.min(referenceSpeed * BHOP_MAX_SPEED_FACTOR,
                        speed * 1.14f + referenceSpeed * 0.10f);
                p.velocity.x *= boosted / speed;
                p.velocity.z *= boosted / speed;
                bhopChainActive = true;
            }
        }
        slideLeft = hopWindow = jumpBuffer = 0f;
        airCrouchArmed = bhopSlide = false;
    }

    public void update(Combatant p, InputState input, float wishX, float wishZ,
                       float referenceSprintSpeed, boolean bhop, float dt) {
        float cap = referenceSprintSpeed * (bhop ? BHOP_MAX_SPEED_FACTOR : MAX_SPEED_FACTOR);
        referenceSpeed = referenceSprintSpeed;
        float speed = (float) Math.sqrt(p.velocity.x * p.velocity.x + p.velocity.z * p.velocity.z);
        cooldown = Math.max(0f, cooldown - dt);
        hopWindow = Math.max(0f, hopWindow - dt);
        jumpBuffer = Math.max(0f, jumpBuffer - dt);
        boolean crouching = input.crouching || (bhop && input.slideHeld);
        boolean pressed = crouching && !crouchHeld;
        crouchHeld = crouching;
        boolean landed = p.grounded && !wasGrounded;
        wasGrounded = p.grounded;
        if (!bhop || p.aiming || !crouching) airCrouchArmed = false;
        // The player may crouch on ascent or descent, but must first release the previous Jump.
        // Holding crouch from the previous hop is not a fresh combo for the next one.
        if (bhop && pressed && !p.grounded && !input.jumpHeld && !p.aiming) {
            airCrouchArmed = true;
        }
        if (bhop && landed && airCrouchArmed && crouching && !p.aiming
                && speed >= referenceSprintSpeed * 0.35f) {
            slideX = p.velocity.x / speed;
            slideZ = p.velocity.z / speed;
            hopWindow = HOP_WINDOW_SECONDS;
            slideLeft = SLIDE_SECONDS;
            bhopSlide = true;
            // Landing preserves momentum. The reward belongs exclusively to onJump().
        } else if (!bhop && pressed && p.grounded && !p.aiming && input.sprinting && cooldown <= 0f
                && speed >= referenceSprintSpeed * 0.78f) {
            slideX = p.velocity.x / speed;
            slideZ = p.velocity.z / speed;
            slideLeft = SLIDE_SECONDS;
            bhopSlide = false;
            cooldown = 1.1f;
            speed = Math.min(cap, speed * 1.16f);
            p.velocity.x = slideX * speed;
            p.velocity.z = slideZ * speed;
        }
        if (landed) airCrouchArmed = false;
        if (p.aiming || speed < 0.5f || !bhop) hopWindow = 0f;
        if (!crouching || p.aiming || !p.grounded || speed < 0.5f) slideLeft = 0f;
        if (!bhop || p.aiming || (p.grounded && !hopReady())) bhopChainActive = false;

        // Active bhop is direction-controlled, not Quake-style additive air strafe. Match has
        // already transformed the stick/keys by camera yaw. Rotate the stored horizontal speed
        // into that wish direction without awarding speed or changing vertical jump physics.
        boolean steerBhop = bhop && !p.aiming && (hopReady()
                || (!p.grounded && (airCrouchArmed || bhopChainActive)));
        float wishSpeed = (float) Math.sqrt(wishX * wishX + wishZ * wishZ);
        if (steerBhop && wishSpeed > 0.001f) {
            float x = wishX / wishSpeed, z = wishZ / wishSpeed;
            p.velocity.x = x * speed;
            p.velocity.z = z * speed;
            // A landing slide must use the new heading too, or it would restore the old one.
            if (bhopSlide && sliding()) {
                slideX = x;
                slideZ = z;
            }
        }
        if (sliding()) {
            float friction = bhopSlide ? (hopReady() ? 0.08f : 2.4f) : 0.48f;
            speed = Math.max(bhopSlide ? Math.min(speed, wishSpeed) : 0f,
                    speed - referenceSprintSpeed * friction * dt);
            p.velocity.x = slideX * speed;
            p.velocity.z = slideZ * speed;
            slideLeft = Math.max(0f, slideLeft - dt);
        } else if (!bhop) {
            // Ordinary movement follows input even while crossing a step or descending a ledge.
            p.velocity.x = wishX;
            p.velocity.z = wishZ;
        } else if (p.grounded) {
            if (!hopReady()) {
                if (bhop && speed > wishSpeed + 0.01f) {
                    // A missed window loses stored momentum over time, not in a one-frame snap.
                    float remaining = Math.max(wishSpeed, speed - referenceSprintSpeed * 2.4f * dt);
                    p.velocity.x *= remaining / speed;
                    p.velocity.z *= remaining / speed;
                } else {
                    p.velocity.x = wishX;
                    p.velocity.z = wishZ;
                }
            }
        } else if (!steerBhop) {
            // Quake-style projected acceleration: perpendicular input can turn/gain momentum,
            // while releasing the stick no longer brakes the player in mid-air. Retained for
            // ordinary jumps, including when the modifier is enabled but no combo is active.
            if (wishSpeed > 0.001f) {
                float x = wishX / wishSpeed, z = wishZ / wishSpeed;
                float projected = p.velocity.x * x + p.velocity.z * z;
                float airWish = Math.min(wishSpeed, referenceSprintSpeed * 0.55f);
                float add = Math.max(0f, Math.min(airWish - projected, wishSpeed * 3.2f * dt));
                p.velocity.x += x * add;
                p.velocity.z += z * add;
            }
        }
        // The cap scales with weapon/class/ADS and Double Speed, not with crouch interpolation.
        speed = (float) Math.sqrt(p.velocity.x * p.velocity.x + p.velocity.z * p.velocity.z);
        if (speed > cap && cap > 0f) {
            p.velocity.x *= cap / speed;
            p.velocity.z *= cap / speed;
        }
    }
}
