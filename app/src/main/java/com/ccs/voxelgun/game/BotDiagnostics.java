package com.ccs.voxelgun.game;

import com.ccs.voxelgun.math.MathUtil;

/**
 * Opt-in, single-writer behaviour telemetry for headless scenarios and debug sessions.
 * Nothing is allocated or logged by sample/event methods. Call {@link #snapshot()} only when a
 * report is needed. Sampling must happen after collision, with intent captured before collision.
 */
public final class BotDiagnostics {
    public static final int NONE = 0, COMBAT = 1, OBJECTIVE = 2, COVER = 3, RANGE = 4,
            INVESTIGATION = 5;
    /** A short reversal or a collision being resolved is not a sustained stall. */
    public static final float STALL_GRACE_SECONDS = 1.5f;
    /** Progress is measured from an anchor, so jitter against a wall cannot count as travel. */
    public static final float PROGRESS_DISTANCE = .45f;
    public static final float MOVING_SPEED = .30f;
    private static final float SPIN_TURN = (float) (Math.PI * 2.0);
    private final Tracker[] trackers;

    public BotDiagnostics(int combatantCapacity) {
        trackers = new Tracker[Math.max(1, combatantCapacity)];
        for (int i = 0; i < trackers.length; i++) trackers[i] = new Tracker(i);
    }

    /** Countdown, round breaks, dead bodies and respawn teleports never count as stalls. */
    public void sample(Combatant bot, float dt, boolean combatActive, boolean wantsMovement,
                       int holdReason) {
        Tracker t = tracker(bot.id);
        if (t == null || !bot.bot || dt <= 0f || !Float.isFinite(dt)) return;
        t.seen = true;
        if (!combatActive || !bot.alive) {
            t.resetObservation();
            return;
        }
        if (!t.observing || t.lastDeaths != bot.deaths) {
            t.observing = true;
            t.lastX = t.anchorX = bot.position.x;
            t.lastZ = t.anchorZ = bot.position.z;
            t.lastYaw = bot.yaw;
            t.lastDeaths = bot.deaths;
            t.resetWindow();
        }
        float dx = bot.position.x - t.lastX, dz = bot.position.z - t.lastZ;
        float distance = (float) Math.sqrt(dx * dx + dz * dz);
        float turn = MathUtil.wrapAngle(bot.yaw - t.lastYaw);
        t.lastX = bot.position.x;
        t.lastZ = bot.position.z;
        t.lastYaw = bot.yaw;
        t.activeSeconds += dt;
        // Movement/travel metrics exclude respawns, scripted teleports and transient bad positions.
        if (!Float.isFinite(distance) || !Float.isFinite(turn)) {
            t.invalidSamples++;
            t.resetObservation();
            return;
        }
        if (distance > Math.max(4f, dt * 80f)) {
            t.anchorX = bot.position.x;
            t.anchorZ = bot.position.z;
            t.resetWindow();
            t.teleports++;
            return;
        }
        t.distance += distance;
        t.turnRadians += Math.abs(turn);
        boolean moving = distance > dt * MOVING_SPEED;
        boolean intentional = holdReason != NONE;
        if (moving) t.movingSeconds += dt;
        else if (intentional) {
            t.holdSeconds += dt;
            if (holdReason >= COMBAT && holdReason <= INVESTIGATION) t.holds[holdReason] += dt;
        } else t.idleSeconds += dt;
        if (wantsMovement) t.movementIntentSeconds += dt;

        float progressX = bot.position.x - t.anchorX;
        float progressZ = bot.position.z - t.anchorZ;
        boolean progressed = progressX * progressX + progressZ * progressZ
                >= PROGRESS_DISTANCE * PROGRESS_DISTANCE;
        if (progressed || intentional) {
            t.anchorX = bot.position.x;
            t.anchorZ = bot.position.z;
            t.resetWindow();
            return;
        }
        t.noProgressSeconds += dt;
        t.signedTurn += turn;
        if (wantsMovement) {
            t.blockedSeconds += dt;
            if (t.blockedSeconds > STALL_GRACE_SECONDS) {
                t.stalledSeconds += dt;
                t.currentStall += dt;
                t.maxStallSeconds = Math.max(t.maxStallSeconds, t.currentStall);
            }
        } else {
            // A brief zero command from recovery must not erase a wall stall. A sustained
            // pause is reported as unexplained idle instead of pretending it was a collision.
            t.blockedSeconds = Math.max(0f, t.blockedSeconds - dt);
            t.currentStall = 0f;
        }
        if (t.noProgressSeconds > STALL_GRACE_SECONDS && Math.abs(t.signedTurn) >= SPIN_TURN) {
            t.spinningSeconds += dt;
        }
    }

    /** Record a failed search or invalidated route edge, never a frame-budget deferral. */
    public void pathFailed(int id, int goalNode) {
        Tracker t = tracker(id);
        if (t == null) return;
        t.pathFailures++;
        if (goalNode >= 0 && t.lastFailedGoal == goalNode) t.repeatedGoalFailures++;
        t.lastFailedGoal = goalNode;
    }

    public void pathSucceeded(int id) {
        Tracker t = tracker(id);
        // A successful search is not arrival: a route can still fail body-clearance validation.
        if (t != null) t.pathSuccesses++;
    }

    /** Clear failure history only after movement actually reaches its goal. */
    public void goalReached(int id) {
        Tracker t = tracker(id);
        if (t != null) t.lastFailedGoal = -1;
    }

    public void pathDeferred(int id) {
        Tracker t = tracker(id);
        if (t != null) t.pathDeferrals++;
    }

    public void recoveryStarted(int id) {
        Tracker t = tracker(id);
        if (t != null) t.recoveries++;
    }

    public void goalAbandoned(int id) {
        Tracker t = tracker(id);
        if (t != null) { t.abandonedGoals++; t.lastFailedGoal = -1; }
    }

    public Snapshot snapshot() {
        int count = 0;
        for (Tracker tracker : trackers) if (tracker.seen) count++;
        BotSnapshot[] bots = new BotSnapshot[count];
        int at = 0;
        for (Tracker tracker : trackers) if (tracker.seen) bots[at++] = new BotSnapshot(tracker);
        return new Snapshot(bots);
    }

    private Tracker tracker(int id) {
        return id >= 0 && id < trackers.length ? trackers[id] : null;
    }

    private static final class Tracker {
        final int id;
        final double[] holds = new double[INVESTIGATION + 1];
        boolean seen, observing;
        int lastDeaths, lastFailedGoal = -1;
        float lastX, lastZ, lastYaw, anchorX, anchorZ, noProgressSeconds, blockedSeconds,
                signedTurn, currentStall;
        double activeSeconds, movingSeconds, holdSeconds, idleSeconds, movementIntentSeconds,
                stalledSeconds, spinningSeconds, maxStallSeconds, distance, turnRadians;
        long pathFailures, repeatedGoalFailures, pathSuccesses, pathDeferrals, recoveries,
                abandonedGoals, teleports, invalidSamples;
        Tracker(int id) { this.id = id; }
        void resetObservation() { observing = false; resetWindow(); }
        void resetWindow() {
            noProgressSeconds = blockedSeconds = signedTurn = currentStall = 0f;
        }
    }

    /** Detached readout: safe to format/store without retaining mutable simulation state. */
    public static final class BotSnapshot {
        public final int id;
        public final double activeSeconds, movingSeconds, holdSeconds, idleSeconds,
                movementIntentSeconds, stalledSeconds, spinningSeconds, maxStallSeconds,
                distance, turnRadians;
        public final double[] holdSecondsByReason;
        public final long pathFailures, repeatedGoalFailures, pathSuccesses, pathDeferrals,
                recoveries, abandonedGoals, teleports, invalidSamples;
        private BotSnapshot(Tracker t) {
            id = t.id;
            activeSeconds = t.activeSeconds; movingSeconds = t.movingSeconds;
            holdSeconds = t.holdSeconds; idleSeconds = t.idleSeconds;
            movementIntentSeconds = t.movementIntentSeconds; stalledSeconds = t.stalledSeconds;
            spinningSeconds = t.spinningSeconds; maxStallSeconds = t.maxStallSeconds;
            distance = t.distance; turnRadians = t.turnRadians;
            holdSecondsByReason = t.holds.clone();
            pathFailures = t.pathFailures; repeatedGoalFailures = t.repeatedGoalFailures;
            pathSuccesses = t.pathSuccesses; pathDeferrals = t.pathDeferrals;
            recoveries = t.recoveries; abandonedGoals = t.abandonedGoals;
            teleports = t.teleports; invalidSamples = t.invalidSamples;
        }
    }

    public static final class Snapshot {
        public final BotSnapshot[] bots;
        public final double activeSeconds, movingSeconds, holdSeconds, idleSeconds,
                movementIntentSeconds, stalledSeconds, spinningSeconds, maxStallSeconds,
                distance, turnRadians;
        public final long pathFailures, repeatedGoalFailures, pathSuccesses, pathDeferrals,
                recoveries, abandonedGoals, teleports, invalidSamples;
        private Snapshot(BotSnapshot[] bots) {
            this.bots = bots;
            double active = 0, moving = 0, holding = 0, idle = 0, intent = 0, stalled = 0,
                    spinning = 0, maxStall = 0, travel = 0, turning = 0;
            long failures = 0, repeated = 0, successes = 0, deferred = 0, recovery = 0,
                    abandoned = 0, teleported = 0, invalid = 0;
            for (BotSnapshot bot : bots) {
                active += bot.activeSeconds; moving += bot.movingSeconds;
                holding += bot.holdSeconds; idle += bot.idleSeconds;
                intent += bot.movementIntentSeconds; stalled += bot.stalledSeconds;
                spinning += bot.spinningSeconds; maxStall = Math.max(maxStall, bot.maxStallSeconds);
                travel += bot.distance; turning += bot.turnRadians;
                failures += bot.pathFailures; repeated += bot.repeatedGoalFailures;
                successes += bot.pathSuccesses; deferred += bot.pathDeferrals;
                recovery += bot.recoveries; abandoned += bot.abandonedGoals;
                teleported += bot.teleports; invalid += bot.invalidSamples;
            }
            activeSeconds = active; movingSeconds = moving; holdSeconds = holding;
            idleSeconds = idle; movementIntentSeconds = intent; stalledSeconds = stalled;
            spinningSeconds = spinning; maxStallSeconds = maxStall;
            distance = travel; turnRadians = turning; pathFailures = failures;
            repeatedGoalFailures = repeated; pathSuccesses = successes; pathDeferrals = deferred;
            recoveries = recovery; abandonedGoals = abandoned; teleports = teleported;
            invalidSamples = invalid;
        }
    }
}
