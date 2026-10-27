package com.ccs.voxelstrike.game;

/** Bot skill tuning. Every value is read by {@link BotBrain} at update time. */
public enum Difficulty {
    RECRUIT("Recruit", 0.62f, 2.1f, 6.5f, 1.9f, 3.2f, 0.45f, 58f, 0.55f, 0.75f),
    REGULAR("Regular", 0.42f, 3.4f, 3.8f, 1.35f, 2.2f, 0.62f, 64f, 0.75f, 1.00f),
    VETERAN("Veteran", 0.26f, 5.0f, 2.1f, 1.05f, 1.35f, 0.78f, 70f, 0.90f, 1.25f),
    ELITE("Elite", 0.14f, 7.5f, 1.1f, 0.85f, 0.8f, 0.92f, 78f, 1.00f, 1.55f);

    private final String label;
    /** Seconds between spotting a target and being allowed to shoot. */
    private final float reactionSeconds;
    /** Maximum turn rate in radians per second while tracking. */
    private final float turnSpeed;
    /** Standing aim error in degrees, reduced the longer a target is tracked. */
    private final float aimErrorDegrees;
    /** Multiplier applied to the weapon's own spread. */
    private final float spreadMultiplier;
    /** Minimum motor-error cone in degrees; prevents settled bots becoming perfect shots. */
    private final float minimumSpreadDegrees;
    /** Chance to keep pushing instead of holding an angle. */
    private final float aggression;
    private final float sightRange;
    /** How reliably the bot reloads, uses cover and swaps weapons. */
    private final float tactics;
    private final float rewardMultiplier;

    Difficulty(String label, float reactionSeconds, float turnSpeed, float aimErrorDegrees,
               float spreadMultiplier, float minimumSpreadDegrees, float aggression,
               float sightRange, float tactics, float rewardMultiplier) {
        this.label = label;
        this.reactionSeconds = reactionSeconds;
        this.turnSpeed = turnSpeed;
        this.aimErrorDegrees = aimErrorDegrees;
        this.spreadMultiplier = spreadMultiplier;
        this.minimumSpreadDegrees = minimumSpreadDegrees;
        this.aggression = aggression;
        this.sightRange = sightRange;
        this.tactics = tactics;
        this.rewardMultiplier = rewardMultiplier;
    }

    public String label() {
        return label;
    }

    public float reactionSeconds() {
        return reactionSeconds;
    }

    public float turnSpeed() {
        return turnSpeed;
    }

    public float aimErrorDegrees() {
        return aimErrorDegrees;
    }

    public float spreadMultiplier() {
        return spreadMultiplier;
    }

    public float minimumSpreadDegrees() {
        return minimumSpreadDegrees;
    }

    public float aggression() {
        return aggression;
    }

    public float sightRange() {
        return sightRange;
    }

    public float tactics() {
        return tactics;
    }

    /** A dodge is a reaction to a visible shot/hit, not an instantaneous damage-avoidance cheat. */
    public float movementReactionSeconds() {
        return .09f + (1f - tactics) * .45f;
    }

    /** Better soldiers can react more often, but never jump/slide once per simulation frame. */
    public float evasionCooldownSeconds() {
        return 2.8f - tactics * 1.6f;
    }

    public float sniperSettleSeconds() {
        return .10f + (1f - tactics) * .16f;
    }

    public float rewardMultiplier() {
        return rewardMultiplier;
    }
}
