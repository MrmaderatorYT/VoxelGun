package com.ccs.voxelgun.game;

import com.ccs.voxelgun.math.MathUtil;
import com.ccs.voxelgun.math.Vec3;
import com.ccs.voxelgun.weapon.Weapon;
import com.ccs.voxelgun.weapon.WeaponSlot;
import com.ccs.voxelgun.weapon.WeaponType;

import java.util.List;
import java.util.Random;

/**
 * Bot behaviour: a small state machine over a shared A* grid.
 *
 * <p>Bots see with a field of view and a line-of-sight test, hear gunfire, take a reaction delay
 * before shooting, lead their aim imperfectly, strafe while engaging, break off to reload and
 * fall back to cover when hurt. Difficulty scales every one of those numbers.
 */
public final class BotBrain {
    private enum State {
        PATROL,
        ENGAGE,
        HUNT,
        REGROUP
    }

    private static final float FIELD_OF_VIEW = 1.30f;  // radians from the forward axis
    private static final float COS_FIELD_OF_VIEW_SQUARED =
            (float) (Math.cos(FIELD_OF_VIEW) * Math.cos(FIELD_OF_VIEW));
    private static final float REPATH_INTERVAL = 0.85f;
    private static final float WAYPOINT_RADIUS = 0.75f;
    /** Expensive visibility queries do not need to run at the 60 Hz locomotion rate. */
    private static final float PERCEPTION_INTERVAL = 0.10f;
    /** Five perception updates a second keep a 100-soldier match affordable on a phone. */
    private static final float BATTLEFIELD_PERCEPTION_INTERVAL = 0.20f;
    private static final float NAVIGATION_PROBE_INTERVAL = 0.12f;
    private static final int TACTICAL_SAMPLES = 12;
    private static final int TACTICAL_VERIFIED = 3;
    private final Combatant self;
    private final Difficulty difficulty;
    private final Match match;
    private final Random random;
    private final PathFinder.Path path = new PathFinder.Path(2048);
    private final Vec3 lastKnown = new Vec3();
    private final Vec3 desired = new Vec3();
    /** Reused by the infrequent tactical spot search to avoid transition-time garbage. */
    private final int[] tacticalCandidates = new int[TACTICAL_VERIFIED];
    private final float[] tacticalScores = new float[TACTICAL_VERIFIED];

    private State state = State.PATROL;
    private Combatant target;
    private float repathTimer;
    /**
     * Cools down goal selection.
     *
     * <p>Picking a wander target samples a dozen spots and verifies them with path searches. When
     * a bot cannot reach the goal it just chose, the old code re-picked on the very next tick, so
     * on maps where that happens often every bot paid that cost sixty times a second.
     */
    private float goalCooldown;
    private float quietPatrolSeconds;
    /** Time left before this bot lets the trigger up again; how fast it can tap. */
    private float triggerReset;
    private float perceptionTimer;
    private float perceptionElapsed;
    private float navigationProbeTimer;
    private boolean directPathClear = true;
    private float trackedTime;
    private float reactionTimer;
    private float lostTimer;
    /** Stable aim correction sampled a few times a second instead of fresh jitter every frame. */
    private float aimSampleTimer;
    private float aimOffsetYaw, aimOffsetPitch;
    private float aimTargetYaw, aimTargetPitch;
    /** Automatic weapons fire deliberate bursts with a short recovery between them. */
    private float burstTime, burstPause;
    private float strafeDirection = 1f;
    private float strafeTimer;
    private float jumpCooldown;
    private float grenadeCooldown = 8f;
    private float stuckTimer;
    private float lastX;
    private float lastZ;
    private int goalNode = -1;
    /** A failed cached waypoint remains a goal, but cannot spend another search before retry. */
    private int failedGoalNode = -1;
    private int previousWaypoint = -1;
    private int validatedWaypoint = -1;
    private int validatedRevision = -1;
    private float coverTime, tacticalCooldown, pushTime;
    private int terrainRevision;
    /** Human-like combat choices are held for a moment rather than rerolled every simulation tick. */
    private float combatDecisionTimer;
    /** Prevents range-based weapon choices from flickering at their distance thresholds. */
    private float weaponDecisionTimer;
    private boolean holdPosition, combatCrouch;
    /** Time spent checking the last known position before returning to patrol. */
    private float searchTimer;
    private final PathFinder.Path tacticalPath = new PathFinder.Path(2048);
    private final BotBhopController bhop;
    private BotCombatStyle style;
    private BotEvasionController evasion;
    private WeaponType rolePrimary;
    private final BotShotDiscipline shotDiscipline = new BotShotDiscipline();
    private final BotSwimmingController swimming = new BotSwimmingController();
    private boolean squadWithdrawing;
    private float sectorWatchTime;

    public BotBrain(Combatant self, Difficulty difficulty, Match match, long seed) {
        this.self = self;
        this.difficulty = difficulty;
        this.match = match;
        this.random = new Random(seed);
        this.style = BotCombatStyle.forCombatant(self);
        this.rolePrimary = self.kit[WeaponSlot.PRIMARY.ordinal()];
        this.evasion = new BotEvasionController(difficulty, style);
        this.bhop = new BotBhopController(difficulty.tactics(), self.id);
        this.grenadeCooldown = 6f + random.nextFloat() * 8f;
        // Spread JNI ray casts across simulation ticks instead of spiking all bots at once.
        float perceptionInterval = perceptionInterval();
        int perceptionPhases = match.isBattlefield() ? 12 : 6;
        this.perceptionTimer = self.id % perceptionPhases
                * (perceptionInterval / perceptionPhases);
        this.navigationProbeTimer = self.id % 5 * (NAVIGATION_PROBE_INTERVAL / 5f);
    }

    public boolean owns(Combatant combatant) {
        return combatant == self;
    }

    public BotCombatStyle combatStyle() { refreshCombatRole(); return style; }

    /** Respawn modifiers and Gun Game change the kit; drawing a sidearm does not. */
    private void refreshCombatRole() {
        WeaponType primary = self.kit[WeaponSlot.PRIMARY.ordinal()];
        if (primary == rolePrimary) return;
        rolePrimary = primary;
        BotCombatStyle next = BotCombatStyle.forCombatant(self);
        if (next == style) return;
        style = next;
        evasion = new BotEvasionController(difficulty, style);
        bhop.reset(); shotDiscipline.reset();
        holdPosition = combatCrouch = false;
        combatDecisionTimer = weaponDecisionTimer = pushTime = 0f;
        goalNode = -1; path.clear(); repathTimer = 0f;
        if (style == BotCombatStyle.SNIPER && state == State.HUNT) state = State.PATROL;
    }

    /**
     * Applies both the selected weapon's dispersion and the bot's finite motor accuracy.
     * Wide native cones (notably shotguns) remain untouched; precision weapons cannot collapse
     * to a perfect ray merely because the bot has finished settling its aim.
     */
    public float shotSpread(WeaponType type, float weaponSpread) {
        float classFactor;
        switch (type.weaponClass()) {
            case SNIPER:
                classFactor = 0.78f;
                break;
            case SHOTGUN:
                classFactor = 0.55f;
                break;
            case SMG:
                classFactor = 1.12f;
                break;
            case LMG:
                classFactor = 1.10f;
                break;
            case PISTOL:
                classFactor = 1.05f;
                break;
            case LAUNCHER:
                classFactor = 0.90f;
                break;
            default:
                classFactor = 1f;
                break;
        }
        float motorError = difficulty.minimumSpreadDegrees() * classFactor;
        return Math.max(weaponSpread * difficulty.spreadMultiplier(), motorError);
    }

    public void onRespawn() {
        refreshCombatRole();
        bhop.reset();
        swimming.reset();
        evasion.reset(); shotDiscipline.reset();
        squadWithdrawing = false;
        state = State.PATROL;
        target = null;
        path.clear();
        goalNode = -1;
        failedGoalNode = previousWaypoint = validatedWaypoint = -1;
        validatedRevision = -1;
        goalCooldown = 0f;
        quietPatrolSeconds = 0f;
        coverTime = pushTime = tacticalCooldown = triggerReset = 0f;
        burstTime = burstPause = 0f;
        aimSampleTimer = aimOffsetYaw = aimOffsetPitch = 0f;
        aimTargetYaw = aimTargetPitch = 0f;
        combatDecisionTimer = weaponDecisionTimer = searchTimer = 0f;
        holdPosition = combatCrouch = false;
        self.crouching = false;
        repathTimer = 0f;
        float perceptionInterval = perceptionInterval();
        int perceptionPhases = match.isBattlefield() ? 12 : 6;
        perceptionTimer = self.id % perceptionPhases * (perceptionInterval / perceptionPhases);
        perceptionElapsed = 0f;
        navigationProbeTimer = 0f;
        directPathClear = true;
        trackedTime = 0f;
        reactionTimer = difficulty.reactionSeconds();
    }

    public void update(float dt) {
        refreshCombatRole();
        int revision = match.world().revision();
        if (revision != terrainRevision) {
            terrainRevision = revision;
            // Remote edits do not invalidate every bot's entire route at once. Validate the next
            // cached edge against current terrain before steering, retaining independent timers.
            validatedWaypoint = -1;
        }
        if (!self.alive || !match.combatActive()) {
            bhop.reset();
            swimming.reset();
            evasion.reset(); shotDiscipline.reset();
            self.velocity.x = 0f;
            self.velocity.z = 0f;
            self.firing = false;
            return;
        }

        boolean wasSwimming = swimming.active();
        if (swimming.update(self, match.world(), dt)) {
            // Reaching air/shore takes precedence over combat, evasion and timed slide jumps.
            bhop.reset(); evasion.reset(); shotDiscipline.reset();
            self.firing = false;
            lookAlongMovement(dt);
            return;
        }
        if (wasSwimming) {
            // A cached path/goal on the submerged floor is no longer useful on the bank.
            path.clear(); goalNode = -1; failedGoalNode = validatedWaypoint = previousWaypoint = -1;
            repathTimer = goalCooldown = 0f;
        }
        if (match.mode().passiveBots()) {
            bhop.reset(); evasion.reset(); shotDiscipline.reset();
            // Range targets stay still, except for escaping water they fell into.
            self.velocity.x = self.velocity.z = 0f;
            self.firing = false;
            return;
        }

        desired.set(0f, 0f, 0f);
        shotDiscipline.update(self, dt);
        repathTimer -= dt;
        goalCooldown -= dt;
        jumpCooldown -= dt;
        grenadeCooldown -= dt;
        strafeTimer -= dt;
        navigationProbeTimer -= dt;
        coverTime -= dt;
        tacticalCooldown -= dt;
        pushTime -= dt;
        weaponDecisionTimer -= dt;

        perceptionTimer -= dt;
        perceptionElapsed += dt;
        if (perceptionTimer <= 0f) {
            acquireTarget(perceptionElapsed);
            perceptionElapsed = 0f;
            float perceptionInterval = perceptionInterval();
            do {
                perceptionTimer += perceptionInterval;
            } while (perceptionTimer <= 0f);
        }
        // A failed assault belongs to the squad plan, not a permanent personal chase. A nearby
        // visible opponent still takes precedence so withdrawing bots do not ignore point-blank fire.
        boolean regroupingSquad = match.battlefront() != null
                ? match.battlefront().regrouping(self)
                : match.conquest() != null && match.conquest().regrouping(self);
        boolean withdraw = regroupingSquad && (target == null
                || Vec3.horizontalDistance(self.position, target.position) > 10f);
        if (withdraw) {
            if (!squadWithdrawing) { goalNode = -1; path.clear(); repathTimer = 0f; }
            state = State.PATROL;
        }
        squadWithdrawing = withdraw;
        evasion.observe(self, state == State.ENGAGE || state == State.REGROUP ? target : null, dt);
        switch (state) {
            case ENGAGE:
                engage(dt);
                break;
            case HUNT:
                hunt(dt);
                break;
            case REGROUP:
                regroup(dt);
                break;
            case PATROL:
            default:
                patrol(dt);
                break;
        }
        float referenceSpeed = 7.7f * self.weaponType().moveSpeedMultiplier()
                * match.movementMultiplier() * match.classMovementMultiplier(self);
        if (match.objectives() != null && match.objectives().carrierId() == self.id) {
            referenceSpeed *= .85f;
        }
        boolean bhopEnabled = match.modifier(MatchModifier.BUNNY_HOP) && !self.zombie;
        if (evasion.steer(self, match.world(), desired, referenceSpeed, bhopEnabled, dt)) {
            match.jump(self, 7.2f);
            jumpCooldown = .9f;
        }
        if (bhopEnabled) {
            boolean travel = (state == State.PATROL || state == State.HUNT) && goalNode >= 0;
            if (travel) {
                float gx = match.nav().cellX(goalNode) + .5f - self.position.x;
                float gz = match.nav().cellZ(goalNode) + .5f - self.position.z;
                travel = gx * gx + gz * gz > 36f;
            }
            bhop.update(self, match.world(), desired.x, desired.z, referenceSpeed,
                    style != BotCombatStyle.SNIPER && (travel || evasion.active()), dt);
        } else {
            bhop.reset();
        }
        BotCrowdAvoidance.separate(self, match.combatants(), match.world(), dt);
        manageWeapon();
        detectStuck(dt);
    }

    private float perceptionInterval() {
        return match.isBattlefield() ? BATTLEFIELD_PERCEPTION_INTERVAL : PERCEPTION_INTERVAL;
    }

    // ------------------------------------------------------------------ perception

    private void acquireTarget(float dt) {
        Combatant best = null;
        float bestScore = Float.MAX_VALUE;
        float sightRange = difficulty.sightRange();
        float sightRangeSquared = sightRange * sightRange;
        List<Combatant> all = match.combatants();
        for (int i = 0; i < all.size(); i++) {
            Combatant candidate = all.get(i);
            if (candidate == self || !candidate.alive || !match.hostile(self, candidate)) {
                continue;
            }
            float dx = candidate.position.x - self.position.x;
            float dy = candidate.position.y - self.position.y;
            float dz = candidate.position.z - self.position.z;
            float distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared > sightRangeSquared) {
                continue;
            }
            // A hit gives away the shooter's direction, but never grants vision through a wall.
            boolean recentAttacker = candidate.id == self.lastAttackerId
                    && self.lastDamageTime < 1.2f;
            if (!recentAttacker && !inFieldOfView(candidate) && distanceSquared > 49f) {
                continue;
            }
            // Rank before tracing: candidates that cannot beat the visible target do not need
            // an expensive voxel line-of-sight query.
            float score = distanceSquared * (candidate == target ? 0.36f : 1f)
                    * (recentAttacker ? 0.28f : 1f);
            if (score >= bestScore) {
                continue;
            }
            if (!match.world().lineOfSight(self.eyeX(), self.eyeY(), self.eyeZ(),
                    candidate.eyeX(), candidate.eyeY() - 0.2f, candidate.eyeZ())) {
                continue;
            }
            // Prefer close targets, but weight the one already being tracked.
            // Squared distance preserves ordering; square the tracked-target preference too.
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        if (best != null) {
            quietPatrolSeconds = 0f;
            if (best != target) {
                target = best;
                trackedTime = 0f;
                reactionTimer = difficulty.reactionSeconds() * style.reactionFactor()
                        * (0.7f + random.nextFloat() * 0.6f);
                aimSampleTimer = 0f;
                aimOffsetYaw = aimOffsetPitch = 0f;
                burstTime = burstPause = 0f;
                combatDecisionTimer = 0f;
            }
            trackedTime += dt;
            reactionTimer = Math.max(0f, reactionTimer - dt);
            lostTimer = 0f;
            // Remember where a moving opponent is likely to be a beat later, not only the exact
            // point sampled by the perception tick.
            float memoryLead = 0.08f + difficulty.tactics() * 0.18f;
            lastKnown.set(best.position.x + best.velocity.x * memoryLead, best.position.y,
                    best.position.z + best.velocity.z * memoryLead);
            if (coverTime > 0f) state = State.REGROUP;
            else if (shouldFallBack() && tacticalCooldown <= 0f) {
                state = State.REGROUP;
                coverTime = 1.6f;
                tacticalCooldown = 5f;
                goalNode = tacticalNode(true);
                path.clear(); repathTimer = 0f;
            } else state = State.ENGAGE;
            return;
        }

        if (target != null) {
            lostTimer += dt;
            if (lostTimer > 8f) {
                target = null;
                state = State.PATROL;
            } else if (state == State.ENGAGE) {
                // Losing a firing angle does not send a marksman chasing through the front line.
                state = style == BotCombatStyle.SNIPER || tacticalAssignment()
                        ? State.PATROL : State.HUNT;
                goalNode = state == State.HUNT
                        ? match.nav().nearestNode(lastKnown.x, lastKnown.y, lastKnown.z) : -1;
                path.clear();
                repathTimer = 0f;
                searchTimer = 1f + random.nextFloat() * 1.3f + difficulty.tactics() * 0.8f;
            }
        } else if (state == State.ENGAGE) {
            state = State.PATROL;
        }
    }

    private boolean inFieldOfView(Combatant candidate) {
        float toX = candidate.position.x - self.position.x;
        float toZ = candidate.position.z - self.position.z;
        float forwardDot = toX * (float) Math.sin(self.yaw)
                - toZ * (float) Math.cos(self.yaw);
        return forwardDot > 0f && forwardDot * forwardDot
                > (toX * toX + toZ * toZ) * COS_FIELD_OF_VIEW_SQUARED;
    }

    /** Hurt bots with enough tactical sense retreat instead of trading. */
    private boolean shouldFallBack() {
        if (self.zombie) {
            return false;
        }
        Weapon weapon = self.weapon();
        if (weapon != null && weapon.reloading()) {
            return true;   // nothing to trade with until the magazine is in
        }
        // Being shot at is not by itself a reason to leave: it used to be, and bots spent more
        // than a third of a firefight walking away from one. Losing the fight is.
        boolean hurt = self.health < 35f;
        boolean outgunned = self.health < 60f && self.lastDamageTime < 0.4f
                && weapon != null && weapon.ammo() <= weapon.type().magazine() / 4;
        return (hurt || outgunned) && random.nextFloat() < difficulty.tactics();
    }

    /** Gunfire pulls bots toward the noise even when they cannot see the shooter. */
    public void hearGunshot(float x, float y, float z, float loudness) {
        if (!self.alive || state == State.ENGAGE || coverTime > 0f || match.objectives() != null) {
            return;
        }
        float distance = (float) Math.sqrt((x - self.position.x) * (x - self.position.x)
                + (z - self.position.z) * (z - self.position.z));
        if (distance > 45f * loudness) {
            return;
        }
        lastKnown.set(x, y, z);
        if (style == BotCombatStyle.SNIPER || tacticalAssignment()) {
            // The sound gives a direction, while the squad retains its route and firing position.
            self.yaw = MathUtil.approachAngle(self.yaw,
                    (float) Math.atan2(x - self.position.x, self.position.z - z), .5f);
            return;
        }
        state = State.HUNT;
        goalNode = match.nav().nearestNode(x, self.position.y, z);
        repathTimer = 0f;
        lostTimer = 0f;
        searchTimer = 0.9f + random.nextFloat() * 1.4f + difficulty.tactics() * 0.6f;
    }

    // ------------------------------------------------------------------ states

    private void patrol(float dt) {
        self.aiming = false;
        self.firing = false;
        self.sprinting = style != BotCombatStyle.SNIPER;
        self.crouching = false;
        if (match.conquest() != null) {
            if (goalNode < 0 || path.finished() || repathTimer <= 0) {
                int next = match.conquest().waypointFor(self);
                if (goalNode != next) { goalNode = next; path.clear(); repathTimer = 0f; }
            }
            if (match.conquest().shouldHold(self)) {
                self.velocity.x = self.velocity.z = 0f;
                watchSector(dt);
                return;
            }
            followPath(dt, style == BotCombatStyle.SNIPER ? .75f : 1f);
            lookAlongMovement(dt); return;
        }
        if (match.objectives() != null) {
            int next = match.objectives().goalFor(self);
            if (goalNode != next) { goalNode = next; repathTimer = 0f; }
            if (match.objectives().shouldHold(self)) {
                self.velocity.x = self.velocity.z = 0f;
                self.yaw = MathUtil.wrapAngle(self.yaw + dt * 0.35f);
                return;
            }
            followPath(dt, 1f); lookAlongMovement(dt); return;
        }
        if (match.battlefront() != null) {
            if (goalNode < 0 || path.finished() || repathTimer <= 0f) {
                int next = match.battlefront().waypointFor(self);
                if (goalNode != next) { goalNode = next; path.clear(); repathTimer = 0f; }
            }
            if (match.battlefront().shouldHold(self)) {
                self.velocity.x = self.velocity.z = 0f;
                watchSector(dt);
                return;
            }
            followPath(dt, style == BotCombatStyle.SNIPER ? .75f : 1f);
            lookAlongMovement(dt);
            return;
        }
        quietPatrolSeconds += dt;
        if (match.mode().teamBased() && quietPatrolSeconds >= 12f - difficulty.tactics() * 6f) {
            // Public arena sectors, not hidden enemy positions. Pure local wandering can keep
            // both teams behind new cover forever, especially at low difficulty.
            float centerX = match.nav().width() * .5f;
            float centerZ = match.nav().depth() * .5f + (self.id % 3 - 1) * 8f;
            int meeting = match.nav().nearestNode(centerX, self.position.y, centerZ);
            if (meeting >= 0 && goalNode != meeting) {
                goalNode = meeting; path.clear(); repathTimer = 0f;
            }
            quietPatrolSeconds = 0f;
            goalCooldown = .8f;
        }
        if ((goalNode < 0 || path.finished()) && goalCooldown <= 0f) {
            Combatant teammate = nearbyTeammate();
            if (teammate != null && Vec3.horizontalDistance(self.position, teammate.position) > 9f) {
                goalNode = match.nav().nearestNode(teammate.position.x, teammate.position.y,
                        teammate.position.z);
            } else {
                goalNode = tacticalNode(false);
            }
            int here = match.nav().nearestNode(self.position.x, self.position.y, self.position.z);
            if (goalNode == here || goalNode < 0) {
                // Nothing verified as reachable. Rather than stand still until the next attempt —
                // which is what made the low difficulties inert — strike out toward a spot nearby
                // and let the path search sort it out, retrying sooner than a normal wander.
                float angle = random.nextFloat() * MathUtil.TAU;
                float reach = 8f + random.nextFloat() * 14f;
                goalNode = match.nav().nearestNode(
                        self.position.x + (float) Math.cos(angle) * reach, self.position.y,
                        self.position.z + (float) Math.sin(angle) * reach);
                goalCooldown = 0.25f + random.nextFloat() * 0.25f;
            } else {
                // Staggered so a horde does not all think on the same tick.
                goalCooldown = 0.6f + random.nextFloat() * 0.6f;
            }
            repathTimer = 0f;
        }
        followPath(dt, self.zombie ? 1.15f : 0.9f);
        lookAlongMovement(dt);
    }

    private void hunt(float dt) {
        self.aiming = false;
        self.firing = false;
        self.sprinting = true;
        self.crouching = false;
        if (goalNode < 0) {
            goalNode = match.nav().nearestNode(lastKnown.x, lastKnown.y, lastKnown.z);
        }
        if (Vec3.horizontalDistance(self.position, lastKnown) < 2.5f) {
            // Check both sides for a moment. Immediately switching to patrol at this point made
            // bots look as if they forgot an opponent the instant they crossed a doorway.
            self.velocity.x *= Math.max(0f, 1f - dt * 8f);
            self.velocity.z *= Math.max(0f, 1f - dt * 8f);
            self.sprinting = false;
            if (strafeTimer <= 0f) {
                strafeTimer = 0.45f + random.nextFloat() * 0.65f;
                strafeDirection = -strafeDirection;
            }
            self.yaw = MathUtil.wrapAngle(self.yaw + strafeDirection
                    * (0.55f + difficulty.tactics() * 0.45f) * dt);
            self.pitch = MathUtil.damp(self.pitch, 0f, 3f, dt);
            searchTimer -= dt;
            if (searchTimer <= 0f) {
                state = State.PATROL;
                target = null;
                goalNode = -1;
                lostTimer = 0f;
            }
            return;
        }
        followPath(dt, 1.0f);
        lookAlongMovement(dt);
    }

    private void regroup(float dt) {
        self.aiming = false;
        self.crouching = false;
        if (goalNode < 0 || path.finished() && goalNode != failedGoalNode) {
            goalNode = tacticalNode(true);
            repathTimer = 0f;
        }
        followPath(dt, 1.05f);

        // A fighting withdrawal: while the enemy is still in sight the bot keeps its weapon on
        // them and keeps shooting, and only turns its back once they are out of view.
        boolean covering = target != null && target.alive && !self.zombie
                && match.world().lineOfSight(self.eyeX(), self.eyeY(), self.eyeZ(),
                        target.eyeX(), target.eyeY() - 0.2f, target.eyeZ());
        if (covering) {
            float distance = Vec3.distance(self.position, target.position);
            self.sprinting = false;   // you cannot shoot at a sprint
            aimAt(target, dt, distance);
            shootAt(dt, distance, self.weaponType().melee(), self.weaponType());
        } else {
            self.firing = false;
            self.sprinting = true;
            lookAlongMovement(dt);
        }
        if (coverTime <= 0f && (self.weapon() == null || !self.weapon().reloading())) {
            state = State.PATROL;
            goalNode = -1;
        }
        Weapon weapon = self.weapon();
        if (weapon != null && weapon.ammo() < weapon.type().magazine() / 2) {
            match.startReload(self);
        }
    }

    private void engage(float dt) {
        if (target == null) {
            state = State.PATROL;
            return;
        }
        float distance = Vec3.distance(self.position, target.position);
        chooseCombatWeapon(distance);
        WeaponType type = self.weaponType();
        boolean melee = type.melee() || self.zombie;
        float preferred = melee ? 1.6f : preferredRangeFor(type) * style.rangeFactor();
        boolean marksman = style == BotCombatStyle.SNIPER && !melee;
        if (marksman) preferred = 38f;
        boolean pushing = pushTime > 0f && style == BotCombatStyle.RUSHER;

        // Hold a tactical choice long enough to read as intent: sometimes plant, settle and
        // crouch; otherwise strafe or close distance. Higher skill makes these pauses useful,
        // without giving any difficulty perfect aim or knowledge.
        combatDecisionTimer -= dt;
        if (combatDecisionTimer <= 0f) {
            boolean usefulAngle = !melee && !pushing && distance > preferred * 0.70f
                    && distance < preferred * 1.30f;
            float holdChance = Math.min(.95f, holdBiasFor(type) * style.holdFactor()
                    * (0.55f + difficulty.tactics() * 0.45f));
            holdPosition = usefulAngle && random.nextFloat() < holdChance;
            combatCrouch = holdPosition && self.grounded && distance > 8f
                    && random.nextFloat() < crouchBiasFor(type)
                    * (0.55f + difficulty.tactics() * 0.45f);
            combatDecisionTimer = decisionTimeFor(type) * (1.15f - difficulty.tactics() * .35f)
                    * (0.8f + random.nextFloat() * 0.4f);
        }
        self.crouching = !evasion.active() && combatCrouch && holdPosition && self.grounded;
        self.aiming = !evasion.active() && !melee && type.scoped() && distance > 12f
                && self.grounded;
        aimAt(target, dt, distance);

        SquadCoordinator.MemberTactic tactic = memberTactic();
        if (marksman) {
            self.sprinting = false;
            if (distance < preferred * .76f) {
                // Keep opening room even with a pistol drawn for close defense.
                self.crouching = false;
                moveToward(self.position.x * 2f - target.position.x,
                        self.position.z * 2f - target.position.z, dt, 1f);
            } else if (tacticalAssignment() && !assignmentShouldHold()
                    && (distance > 46f || aheadOfAssignedPosition())) {
                self.crouching = false;
                followAssignment(dt, .72f);
            } else if (!tacticalAssignment() && distance > preferred * 1.4f) {
                self.crouching = false;
                moveToward(lastKnown.x, lastKnown.z, dt, .65f);
            } else {
                // A deliberate settled firing phase, compatible with sniper shot discipline.
                self.velocity.x = self.velocity.z = 0f;
                holdPosition = true;
                self.crouching = self.grounded && distance > 18f;
            }
        } else if (!melee && (tactic == SquadCoordinator.MemberTactic.FLANK
                || tactic == SquadCoordinator.MemberTactic.SUPPORT
                || tactic == SquadCoordinator.MemberTactic.HOLD)
                && !assignmentShouldHold() && distance > preferred * .75f && !pushing) {
            self.crouching = false;
            followAssignment(dt, tactic == SquadCoordinator.MemberTactic.FLANK ? .95f : .80f);
        } else if (!melee && distance >= preferred * retreatFactorFor(type)
                && (match.objectives() != null && match.objectives().shouldHold(self)
                || tacticalAssignment() && assignmentShouldHold())) {
            self.velocity.x = self.velocity.z = 0f;
        } else if (melee) {
            moveToward(target.position.x, target.position.z, dt, 1.2f);
        } else if (distance > preferred * approachFactorFor(type) || pushing) {
            moveToward(lastKnown.x, lastKnown.z, dt,
                    (approachSpeedFor(type) + (pushing ? 0.10f : 0f)) * style.advanceFactor());
        } else if (distance < preferred * retreatFactorFor(type)) {
            moveToward(self.position.x * 2f - target.position.x,
                    self.position.z * 2f - target.position.z, dt, retreatSpeedFor(type));
        } else if (holdPosition) {
            self.velocity.x *= Math.max(0f, 1f - dt * 10f);
            self.velocity.z *= Math.max(0f, 1f - dt * 10f);
        } else {
            strafe(dt, strafeSpeedFor(type));
        }

        boolean closingFast = distance > preferred * approachFactorFor(type) || pushing;
        self.sprinting = !marksman && (melee || (closingFast && reactionTimer > 0f));

        shootAt(dt, distance, melee, type);

        maybeThrowGrenade(distance);
    }

    private boolean tacticalAssignment() {
        return match.battlefront() != null || match.conquest() != null;
    }

    private SquadCoordinator.MemberTactic memberTactic() {
        return match.conquest() != null ? match.conquest().memberTacticFor(self)
                : match.battlefront() != null ? match.battlefront().memberTacticFor(self)
                : SquadCoordinator.MemberTactic.ADVANCE;
    }

    private boolean assignmentShouldHold() {
        return match.conquest() != null ? match.conquest().shouldHold(self)
                : match.battlefront() != null && match.battlefront().shouldHold(self);
    }

    private boolean aheadOfAssignedPosition() {
        int destination = match.conquest() != null ? match.conquest().goalFor(self)
                : match.battlefront().destinationFor(self);
        return match.nav().valid(destination) && (self.position.x
                - match.nav().cellX(destination) - .5f) * (self.team == Team.BLUE ? 1f : -1f) > 4f;
    }

    private void followAssignment(float dt, float speed) {
        if (goalNode < 0 || path.finished() || repathTimer <= 0f) {
            int next = match.conquest() != null ? match.conquest().waypointFor(self)
                    : match.battlefront().waypointFor(self);
            if (next != goalNode) { goalNode = next; path.clear(); repathTimer = 0f; }
        }
        followPath(dt, speed);
    }

    private void watchSector(float dt) {
        sectorWatchTime += dt;
        float yaw = self.team == Team.BLUE ? (float) Math.PI * .5f : -(float) Math.PI * .5f;
        if (target != null && lostTimer < 4f)
            yaw = (float) Math.atan2(lastKnown.x - self.position.x, self.position.z - lastKnown.z);
        yaw += (float) Math.sin(sectorWatchTime * .6f + self.id) * .55f;
        self.yaw = MathUtil.approachAngle(self.yaw, yaw, difficulty.turnSpeed() * dt);
        self.pitch = MathUtil.damp(self.pitch, 0f, 3f, dt);
        self.sprinting = false;
        self.aiming = style == BotCombatStyle.SNIPER && self.weaponType().scoped() && self.grounded;
    }

    /**
     * Uses the sidearm as an actual tactical tool: marksmen and launchers draw it up close,
     * breachers use it across long lanes, and any role can abandon a dry primary under pressure.
     * Hysteresis plus a short decision lock keeps bots from swapping on every frame.
     */
    void chooseCombatWeapon(float distance) {
        if (self.zombie || weaponDecisionTimer > 0f || self.switchTimer > 0f) {
            return;
        }
        int primarySlot = WeaponSlot.PRIMARY.ordinal();
        int secondarySlot = WeaponSlot.SECONDARY.ordinal();
        Weapon primary = self.weapons[primarySlot];
        Weapon secondary = self.weapons[secondarySlot];
        Weapon current = self.weapon();
        if (current == null) {
            return;
        }

        int desiredSlot = -1;
        if (self.slot == primarySlot && usable(secondary)) {
            WeaponType primaryType = primary.type();
            boolean emergency = current.empty()
                    || (current.reloading() && distance < preferredRangeFor(primaryType));
            switch (primaryType.weaponClass()) {
                case SNIPER:
                    if (emergency || distance < 12.5f) desiredSlot = secondarySlot;
                    break;
                case LAUNCHER:
                    if (emergency || distance < minimumSafeRangeFor(primaryType) + 1.5f) {
                        desiredSlot = secondarySlot;
                    }
                    break;
                case SHOTGUN:
                    if (emergency || distance > 14.5f) desiredSlot = secondarySlot;
                    break;
                case LMG:
                    if (emergency && distance < 20f) desiredSlot = secondarySlot;
                    break;
                default:
                    if (emergency && distance < 16f) desiredSlot = secondarySlot;
                    break;
            }
        } else if (self.slot == secondarySlot && usable(primary)) {
            WeaponType primaryType = primary.type();
            boolean returnToPrimary = current.depleted();
            switch (primaryType.weaponClass()) {
                case SNIPER:
                    returnToPrimary |= distance > 17f;
                    break;
                case LAUNCHER:
                    returnToPrimary |= distance > minimumSafeRangeFor(primaryType) + 6f;
                    break;
                case SHOTGUN:
                    returnToPrimary |= distance < 11.5f;
                    break;
                case LMG:
                    returnToPrimary |= !primary.reloading() && primary.ammo() > 0
                            && distance > 13f;
                    break;
                default:
                    returnToPrimary |= !primary.reloading() && primary.ammo() > 0;
                    break;
            }
            if (returnToPrimary) desiredSlot = primarySlot;
        } else if (self.slot != primarySlot && self.slot != secondarySlot) {
            if (usable(primary)) desiredSlot = primarySlot;
            else if (usable(secondary)) desiredSlot = secondarySlot;
        }

        if (desiredSlot >= 0 && self.selectSlot(desiredSlot)) {
            weaponDecisionTimer = 1.15f + random.nextFloat() * 0.45f;
            burstTime = burstPause = 0f;
            combatDecisionTimer = 0f;
            self.aiming = false;
            self.firing = false;
        }
    }

    private static boolean usable(Weapon weapon) {
        return weapon != null && !weapon.depleted();
    }

    static float preferredRangeFor(WeaponType type) {
        switch (type.weaponClass()) {
            case SNIPER:
                return 38f;
            case SHOTGUN:
                return 6.5f;
            case SMG:
                return 12f;
            case LMG:
                return 24f;
            case PISTOL:
                return 11f;
            case LAUNCHER:
                return 28f;
            case RIFLE:
            default:
                return 18f;
        }
    }

    static float holdBiasFor(WeaponType type) {
        switch (type.weaponClass()) {
            case SNIPER:
                return 0.82f;
            case LMG:
                return 0.66f;
            case LAUNCHER:
                return 0.56f;
            case RIFLE:
                return 0.40f;
            case PISTOL:
                return 0.25f;
            case SMG:
                return 0.14f;
            case SHOTGUN:
                return 0.07f;
            default:
                return 0f;
        }
    }

    private static float crouchBiasFor(WeaponType type) {
        switch (type.weaponClass()) {
            case SNIPER:
                return 0.74f;
            case LMG:
                return 0.56f;
            case RIFLE:
                return 0.34f;
            case LAUNCHER:
                return 0.24f;
            case PISTOL:
                return 0.18f;
            case SMG:
                return 0.10f;
            case SHOTGUN:
                return 0.04f;
            default:
                return 0f;
        }
    }

    private static float decisionTimeFor(WeaponType type) {
        switch (type.weaponClass()) {
            case SNIPER:
            case LMG:
            case LAUNCHER:
                return 1.25f;
            case RIFLE:
                return 0.95f;
            case PISTOL:
                return 0.75f;
            case SMG:
            case SHOTGUN:
                return 0.58f;
            default:
                return 0.5f;
        }
    }

    private static float approachFactorFor(WeaponType type) {
        switch (type.weaponClass()) {
            case SHOTGUN:
                return 0.86f;
            case SMG:
                return 1.00f;
            case PISTOL:
                return 1.10f;
            case RIFLE:
                return 1.22f;
            case LMG:
                return 1.34f;
            case SNIPER:
            case LAUNCHER:
                return 1.40f;
            default:
                return 1.20f;
        }
    }

    private static float retreatFactorFor(WeaponType type) {
        switch (type.weaponClass()) {
            case SHOTGUN:
                return 0.34f;
            case SMG:
                return 0.42f;
            case PISTOL:
                return 0.50f;
            case RIFLE:
                return 0.58f;
            case LMG:
                return 0.68f;
            case SNIPER:
                return 0.76f;
            case LAUNCHER:
                return 0.78f;
            default:
                return 0.5f;
        }
    }

    private static float approachSpeedFor(WeaponType type) {
        switch (type.weaponClass()) {
            case SHOTGUN:
                return 1.14f;
            case SMG:
                return 1.05f;
            case PISTOL:
                return 0.98f;
            case RIFLE:
                return 0.90f;
            case LMG:
                return 0.76f;
            case SNIPER:
                return 0.70f;
            case LAUNCHER:
                return 0.68f;
            default:
                return 0.9f;
        }
    }

    private static float retreatSpeedFor(WeaponType type) {
        switch (type.weaponClass()) {
            case SNIPER:
            case LAUNCHER:
                return 1.00f;
            case LMG:
                return 0.88f;
            case RIFLE:
                return 0.82f;
            default:
                return 0.76f;
        }
    }

    private static float strafeSpeedFor(WeaponType type) {
        switch (type.weaponClass()) {
            case SHOTGUN:
                return 1.00f;
            case SMG:
                return 0.95f;
            case PISTOL:
                return 0.88f;
            case RIFLE:
                return 0.72f;
            case LMG:
                return 0.54f;
            case LAUNCHER:
                return 0.50f;
            case SNIPER:
                return 0.44f;
            default:
                return 0.75f;
        }
    }

    static float minimumSafeRangeFor(WeaponType type) {
        return type.weaponClass() == com.ccs.voxelgun.weapon.WeaponClass.LAUNCHER
                ? Math.max(10.5f, type.splashRadius() * 1.65f) : 0f;
    }

    // ------------------------------------------------------------------ aiming and shooting

    private void aimAt(Combatant enemy, float dt, float distance) {
        float aimHeight = enemy.position.y + enemy.height() * (self.zombie ? 0.55f
                : match.modifier(MatchModifier.HEADSHOTS_ONLY) ? 0.93f : 0.78f);
        float toX = enemy.position.x - self.eyeX();
        float toY = aimHeight - self.eyeY();
        float toZ = enemy.position.z - self.eyeZ();

        // Lead the target a little; better bots lead more accurately.
        float projectileSpeed = self.weaponType().projectile() ? 42f : 90f;
        float travel = distance / projectileSpeed;
        toX += enemy.velocity.x * travel * difficulty.tactics();
        toZ += enemy.velocity.z * travel * difficulty.tactics();

        float targetYaw = (float) Math.atan2(toX, -toZ);
        float horizontal = (float) Math.sqrt(toX * toX + toZ * toZ) + 1e-4f;
        float targetPitch = (float) Math.atan2(toY, horizontal);

        // Aim error shrinks the longer the bot has been tracking this target. The correction is
        // sampled and eased rather than rerolled every frame, eliminating robotic camera jitter.
        float settle = MathUtil.clamp(1f - trackedTime * 0.9f, 0.15f, 1f);
        float error = (float) Math.toRadians(difficulty.aimErrorDegrees()) * settle;
        aimSampleTimer -= dt;
        if (aimSampleTimer <= 0f) {
            aimTargetYaw = (random.nextFloat() - 0.5f) * error;
            aimTargetPitch = (random.nextFloat() - 0.5f) * error * 0.6f;
            aimSampleTimer = 0.16f + (1f - difficulty.tactics()) * 0.22f
                    + random.nextFloat() * 0.12f;
        }
        float correctionRate = 5f + difficulty.tactics() * 6f;
        aimOffsetYaw = MathUtil.damp(aimOffsetYaw, aimTargetYaw, correctionRate, dt);
        aimOffsetPitch = MathUtil.damp(aimOffsetPitch, aimTargetPitch, correctionRate, dt);
        targetYaw += aimOffsetYaw;
        targetPitch += aimOffsetPitch;

        float turn = difficulty.turnSpeed() * dt * (self.aiming ? 0.65f : 1f);
        self.yaw = MathUtil.approachAngle(self.yaw, targetYaw, turn);
        self.pitch = MathUtil.clamp(self.pitch + MathUtil.clamp(targetPitch - self.pitch, -turn,
                turn), -1.4f, 1.4f);
    }

    /**
     * How long the finger stays down between shots on a semi-automatic.
     *
     * <p>Skill shows up here as much as in aim: a recruit taps lazily, an elite is close to the
     * weapon's mechanical limit. Never zero, so no bot out-taps a human by an inhuman margin.
     */
    private float tapDelay(WeaponType type) {
        float skill = difficulty.tactics();
        float weaponRhythm;
        switch (type.weaponClass()) {
            case PISTOL:
                weaponRhythm = 0.04f;
                break;
            case SHOTGUN:
                weaponRhythm = 0.09f;
                break;
            case SNIPER:
                weaponRhythm = 0.12f;
                break;
            case LAUNCHER:
                weaponRhythm = 0.18f;
                break;
            default:
                weaponRhythm = 0.07f;
                break;
        }
        return weaponRhythm + (1f - skill) * 0.40f + random.nextFloat() * 0.06f;
    }

    /**
     * Pulls the trigger when the sights are where they need to be.
     *
     * <p>Shared by the two states that fight: standing and trading, and falling back while still
     * shooting. A retreat that stops firing is what made bots look like they were ignoring each
     * other and wandering off.
     */
    private void shootAt(float dt, float distance, boolean melee, WeaponType type) {
        triggerReset -= dt;
        Weapon weapon = self.weapon();
        boolean ready = reactionTimer <= 0f && !evasion.active()
                && shotDiscipline.allowsShot(self, difficulty);
        boolean aimed = aimedAt(target,
                melee ? 0.55f : 0.16f + 0.10f / Math.max(1f, distance * 0.2f));
        boolean inReach = !melee || distance <= type.range() * 0.95f;
        boolean safeShot = type.weaponClass() != com.ccs.voxelgun.weapon.WeaponClass.LAUNCHER
                || distance >= minimumSafeRangeFor(type);

        if (!ready || !aimed || !inReach || !safeShot || weapon == null || weapon.reloading()) {
            self.firing = false;
            if (weapon != null) {
                weapon.releaseTrigger();
            }
            return;
        }

        if (!automaticBurstAllowsFire(dt, distance, type)) {
            self.firing = false;
            weapon.releaseTrigger();
            return;
        }

        boolean fired = match.tryFire(self, true);
        self.firing = fired;
        if (fired) {
            // Anything that is not full auto needs the trigger to come back up before the next
            // round. A bot that simply holds it down fires once and then stands there aiming,
            // which is what made pistols, snipers and knives look broken.
            triggerReset = type.automatic() ? 0f : tapDelay(type);
        } else if (triggerReset <= 0f && weapon.ammo() > 0) {
            weapon.releaseTrigger();
        }
    }

    /** Gives automatic fire readable bursts whose length still reflects weapon range and skill. */
    private boolean automaticBurstAllowsFire(float dt, float distance, WeaponType type) {
        if (!type.automatic() || type.melee()) {
            return true;
        }
        if (burstPause > 0f) {
            burstPause -= dt;
            return false;
        }
        if (burstTime <= 0f) {
            float rangeFactor = MathUtil.clamp(1f - distance / 42f, 0f, 1f);
            float base;
            float rangeInfluence;
            switch (type.weaponClass()) {
                case LMG:
                    base = 0.42f;
                    rangeInfluence = 0.46f;
                    break;
                case SMG:
                    base = 0.22f;
                    rangeInfluence = 0.28f;
                    break;
                case SHOTGUN:
                    base = 0.12f;
                    rangeInfluence = 0.12f;
                    break;
                case PISTOL:
                    base = 0.14f;
                    rangeInfluence = 0.13f;
                    break;
                case RIFLE:
                default:
                    base = 0.19f;
                    rangeInfluence = 0.23f;
                    break;
            }
            burstTime = base + rangeFactor * rangeInfluence + difficulty.tactics() * 0.18f
                    + random.nextFloat() * 0.12f;
        }
        burstTime -= dt;
        if (burstTime <= 0f) {
            float weaponPause;
            switch (type.weaponClass()) {
                case LMG:
                    weaponPause = 0.07f;
                    break;
                case SMG:
                    weaponPause = 0.10f;
                    break;
                case SHOTGUN:
                    weaponPause = 0.21f;
                    break;
                case PISTOL:
                    weaponPause = 0.16f;
                    break;
                case RIFLE:
                default:
                    weaponPause = 0.13f;
                    break;
            }
            burstPause = weaponPause + (1f - difficulty.tactics()) * 0.20f
                    + random.nextFloat() * 0.10f;
        }
        return true;
    }

    private boolean aimedAt(Combatant enemy, float tolerance) {
        float toX = enemy.position.x - self.eyeX();
        float toZ = enemy.position.z - self.eyeZ();
        float targetYaw = (float) Math.atan2(toX, -toZ);
        return Math.abs(MathUtil.angleDelta(self.yaw, targetYaw)) < tolerance;
    }

    private void maybeThrowGrenade(float distance) {
        if (grenadeCooldown > 0f || self.zombie || target == null) {
            return;
        }
        if (distance < 9f || distance > 30f) {
            return;
        }
        Weapon grenade = self.weapons[WeaponSlot.GRENADE.ordinal()];
        if (grenade == null || grenade.depleted()) {
            return;
        }
        for (Combatant ally : match.combatants()) {
            if (ally != self && ally.alive && !match.hostile(self, ally)
                    && Vec3.distance(ally.position, target.position) < 7f) return;
        }
        if (random.nextFloat() > difficulty.tactics() * 0.5f) {
            grenadeCooldown = 4f;
            return;
        }
        grenadeCooldown = 14f + random.nextFloat() * 10f;
        // Aim slightly above the target so the arc lands near their feet.
        float previousPitch = self.pitch;
        self.pitch = MathUtil.clamp(self.pitch + (match.modifier(MatchModifier.LOW_GRAVITY) ? 0.04f : 0.22f), -1.4f, 1.4f);
        int previous = self.slot;
        self.slot = WeaponSlot.GRENADE.ordinal();
        if (match.tryFire(self, true) && style == BotCombatStyle.RUSHER) pushTime = 3.2f;
        self.slot = previous;
        self.pitch = previousPitch;
        self.switchTimer = 0.5f;
    }

    private void manageWeapon() {
        Weapon weapon = self.weapon();
        if (weapon == null) {
            return;
        }
        if (weapon.empty() && (weapon.reserve() > 0 || weapon.infiniteReserve())) {
            match.startReload(self);
            return;
        }
        if (weapon.depleted()) {
            int primary = WeaponSlot.PRIMARY.ordinal();
            int secondary = WeaponSlot.SECONDARY.ordinal();
            int melee = WeaponSlot.MELEE.ordinal();
            if (self.slot != primary && usable(self.weapons[primary])) self.selectSlot(primary);
            else if (self.slot != secondary && usable(self.weapons[secondary])) {
                self.selectSlot(secondary);
            } else if (self.slot != melee && self.weapons[melee] != null) self.selectSlot(melee);
            return;
        }
        // Reload during downtime rather than mid-fight.
        if (state != State.ENGAGE && !weapon.reloading()
                && weapon.ammo() < weapon.type().magazine() * 0.4f) {
            match.startReload(self);
        }
    }

    // ------------------------------------------------------------------ locomotion

    private void followPath(float dt, float speedScale) {
        if (goalNode < 0) {
            self.velocity.x = 0f;
            self.velocity.z = 0f;
            return;
        }
        if (repathTimer <= 0f) {
            if (match.pathFinder().canSearch()) {
                repathTimer = REPATH_INTERVAL + random.nextFloat() * 0.4f;
                int startNode = match.nav().nearestNode(self.position.x, self.position.y, self.position.z);
                if (match.pathFinder().find(startNode, goalNode, path)) {
                    failedGoalNode = -1;
                    previousWaypoint = path.currentNode();
                    validatedWaypoint = -1;
                } else {
                    failedGoalNode = goalNode;
                }
            } else if (path.finished()) {
                // Budget deferral is not an unreachable goal: retry on the next simulation step.
                self.velocity.x = self.velocity.z = 0f;
                return;
            }
        }
        if (path.finished()) {
            // Do not let Conquest's unchanged cached destination reset a genuine failure's
            // cooldown on the next tick. A new order/target still resets it at the caller.
            if (goalNode != failedGoalNode) goalNode = -1;
            self.velocity.x = 0f;
            self.velocity.z = 0f;
            return;
        }

        // Timed hops need continuous wish input across cells. Preserve ordinary patrol cadence.
        for (int skipped = 0; skipped < 6 && !path.finished(); skipped++) {
            int node = path.currentNode();
            if (!routeEdgeClear(node)) {
                path.clear();
                failedGoalNode = goalNode;
                previousWaypoint = validatedWaypoint = -1;
                // Nearby damage may affect several bots, so do not synchronize their A* retry.
                repathTimer = Math.max(repathTimer, .10f + Math.floorMod(self.id, 12) * .025f);
                self.velocity.x = self.velocity.z = 0f;
                return;
            }
            float dx = match.nav().cellX(node) + .5f - self.position.x;
            float dz = match.nav().cellZ(node) + .5f - self.position.z;
            if (dx * dx + dz * dz >= WAYPOINT_RADIUS * WAYPOINT_RADIUS) {
                steer(dx, dz, dt, speedScale);
                return;
            }
            previousWaypoint = node;
            path.advance();
            if (!match.modifier(MatchModifier.BUNNY_HOP)) return;
        }
    }

    /** Check only the next edge, once per waypoint/revision, never scan/rebuild the full route. */
    private boolean routeEdgeClear(int node) {
        if (validatedWaypoint == node && validatedRevision == terrainRevision) return true;
        NavGrid nav = match.nav();
        if (!nav.valid(node)) return false;
        int x = nav.cellX(node), y = nav.nodeY(node), z = nav.cellZ(node);
        if (!match.world().solid(x, y - 1, z)
                || match.world().boxCollides(x + .5f, y, z + .5f, Combatant.RADIUS, Combatant.HEIGHT)) {
            return false;
        }
        // A refreshed column can reuse a valid node id for a different height. Body clearance
        // alone must not accept a newly disconnected cliff, ledge, or diagonal corner.
        if (previousWaypoint >= 0 && previousWaypoint != node
                && !nav.connected(previousWaypoint, node)) return false;
        if (self.grounded && previousWaypoint == node
                && (y - self.position.y > 1.05f || y - self.position.y < -4.05f)) return false;
        validatedWaypoint = node;
        validatedRevision = terrainRevision;
        return true;
    }

    private void moveToward(float x, float z, float dt, float speedScale) {
        float dx = x - self.position.x;
        float dz = z - self.position.z;
        if (dx * dx + dz * dz < 0.5f) {
            self.velocity.x *= 0.6f;
            self.velocity.z *= 0.6f;
            return;
        }
        // Cache the direct-path ray for a few ticks. Collision still runs at 60 Hz, so a stale
        // clear result cannot let the body pass through a wall.
        if (navigationProbeTimer <= 0f) {
            directPathClear = match.world().lineOfSight(self.position.x,
                    self.position.y + 0.9f, self.position.z, x, self.position.y + 0.9f, z);
            navigationProbeTimer = NAVIGATION_PROBE_INTERVAL;
        }
        if (!directPathClear) {
            if (repathTimer <= 0f || goalNode < 0) {
                goalNode = match.nav().nearestNode(x, self.position.y, z);
                repathTimer = 0f;
            }
            followPath(dt, speedScale);
            return;
        }
        steer(dx, dz, dt, speedScale);
    }

    private void strafe(float dt, float speedScale) {
        if (strafeTimer <= 0f) {
            strafeTimer = 0.6f + random.nextFloat() * 1.1f;
            strafeDirection = random.nextBoolean() ? 1f : -1f;
        }
        float side = self.yaw + (float) Math.PI * 0.5f * strafeDirection;
        steer((float) Math.sin(side), -(float) Math.cos(side), dt, speedScale);
    }

    private void steer(float dx, float dz, float dt, float speedScale) {
        float length = (float) Math.sqrt(dx * dx + dz * dz);
        if (length < 1e-4f) {
            return;
        }
        dx /= length;
        dz /= length;
        float speed = 5.2f * speedScale * self.weaponType().moveSpeedMultiplier()
                * match.movementMultiplier() * match.classMovementMultiplier(self);
        if (match.objectives() != null && match.objectives().carrierId() == self.id) speed *= 0.85f;
        if (self.zombie) {
            speed *= 1.18f;
        }
        desired.set(dx * speed, 0f, dz * speed);
        if (!match.modifier(MatchModifier.BUNNY_HOP) || self.zombie) {
            float blend = self.grounded ? 14f * dt : 3f * dt;
            self.velocity.x = MathUtil.lerp(self.velocity.x, desired.x, Math.min(1f, blend));
            self.velocity.z = MathUtil.lerp(self.velocity.z, desired.z, Math.min(1f, blend));
        }

        // Hop over a one-block ledge rather than grinding against it.
        if ((self.grounded || match.modifier(MatchModifier.DOUBLE_JUMP) && !self.airJumpUsed)
                && jumpCooldown <= 0f) {
            float probeX = self.position.x + dx * 0.8f;
            float probeZ = self.position.z + dz * 0.8f;
            boolean blocked = match.world().boxCollides(probeX, self.position.y + 0.1f, probeZ,
                    Combatant.RADIUS, 0.6f);
            boolean clearAbove = !match.world().boxCollides(probeX, self.position.y + 1.2f, probeZ,
                    Combatant.RADIUS, 1.0f);
            if (blocked && clearAbove) {
                match.jump(self, 7.0f);
                jumpCooldown = 0.8f;
            }
        }
    }

    private void lookAlongMovement(float dt) {
        float speed = (float) Math.sqrt(self.velocity.x * self.velocity.x
                + self.velocity.z * self.velocity.z);
        if (speed < 0.4f) {
            return;
        }
        float targetYaw = (float) Math.atan2(self.velocity.x, -self.velocity.z);
        self.yaw = MathUtil.approachAngle(self.yaw, targetYaw, difficulty.turnSpeed() * dt);
        self.pitch = MathUtil.damp(self.pitch, 0f, 4f, dt);
    }

    private Combatant nearbyTeammate() {
        Combatant best = null;
        float distance = 32f;
        for (Combatant c : match.combatants()) {
            if (c == self || !c.alive || match.hostile(self, c)) continue;
            float d = Vec3.horizontalDistance(self.position, c.position);
            if (d < distance) { distance = d; best = c; }
        }
        return best;
    }

    /** Search is bounded and runs on tactical transitions, never once per simulation tick. */
    /** Spots sampled, then how many of them are worth a line-of-sight test and a path search. */
    /**
     * Picks a spot to fall back to or to push from.
     *
     * <p>Cost is the whole story here. Every sample used to get a full A* search — two dozen of
     * them in one call, which is what turned a single bot taking cover into an 80ms hitch. Now
     * the samples are ranked on geometry alone, which is arithmetic, and only the best few pay
     * for a line-of-sight trace and a path search.
     */
    private int tacticalNode(boolean cover) {
        NavGrid nav = match.nav();
        int start = nav.nearestNode(self.position.x, self.position.y, self.position.z);
        int[] candidates = tacticalCandidates;
        float[] scores = tacticalScores;
        for (int slot = 0; slot < TACTICAL_VERIFIED; slot++) {
            candidates[slot] = -1;
            scores[slot] = -Float.MAX_VALUE;
        }

        for (int i = 0; i < TACTICAL_SAMPLES; i++) {
            float sampleX = self.position.x + (random.nextFloat() - 0.5f) * 30f;
            float sampleZ = self.position.z + (random.nextFloat() - 0.5f) * 30f;
            int node = nav.nearestNode(sampleX, self.position.y + (cover ? 0f : 3f), sampleZ);
            if (node < 0) {
                continue;
            }
            float x = nav.cellX(node) + 0.5f;
            float z = nav.cellZ(node) + 0.5f;
            float travel = (float) Math.hypot(x - self.position.x, z - self.position.z);
            // Geometry only: cover wants somewhere close, a push wants height and distance.
            float score = cover ? -travel
                    : (nav.nodeY(node) - self.position.y) * 4f + travel * 0.25f;
            for (int slot = 0; slot < TACTICAL_VERIFIED; slot++) {
                if (score > scores[slot]) {
                    for (int shift = TACTICAL_VERIFIED - 1; shift > slot; shift--) {
                        scores[shift] = scores[shift - 1];
                        candidates[shift] = candidates[shift - 1];
                    }
                    scores[slot] = score;
                    candidates[slot] = node;
                    break;
                }
            }
        }

        int fallback = -1;
        for (int slot = 0; slot < TACTICAL_VERIFIED; slot++) {
            int node = candidates[slot];
            if (node < 0) {
                continue;
            }
            if (!match.pathFinder().find(start, node, tacticalPath)) {
                continue;   // unreachable, or the step's search budget is spent
            }
            if (fallback < 0) {
                fallback = node;
            }
            if (!cover) {
                return node;
            }
            // Cover is only cover if it actually breaks the line to where the enemy was.
            float x = nav.cellX(node) + 0.5f;
            float z = nav.cellZ(node) + 0.5f;
            if (!match.world().lineOfSight(x, nav.nodeY(node) + 1.5f, z,
                    lastKnown.x, lastKnown.y + 1.5f, lastKnown.z)) {
                return node;
            }
        }
        return fallback < 0 ? start : fallback;
    }

    /** Repaths when the bot has not made progress, which unsticks it from awkward geometry. */
    private void detectStuck(float dt) {
        float moved = Math.abs(self.position.x - lastX) + Math.abs(self.position.z - lastZ);
        lastX = self.position.x;
        lastZ = self.position.z;
        boolean wantsToMove = Math.abs(self.velocity.x) + Math.abs(self.velocity.z) > 1.2f;
        if (wantsToMove && moved < 0.02f) {
            stuckTimer += dt;
        } else {
            stuckTimer = Math.max(0f, stuckTimer - dt * 2f);
        }
        if (stuckTimer > 0.9f) {
            stuckTimer = 0f;
            repathTimer = 0f;
            goalNode = match.nav().randomNodeAwayFrom(random, self.position.x, self.position.z, 12f);
            if (self.grounded) {
                match.jump(self, 7.0f);
            }
        }
    }
}
