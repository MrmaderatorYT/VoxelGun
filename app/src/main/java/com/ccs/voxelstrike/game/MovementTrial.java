package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.map.CourseLayout;
import com.ccs.voxelstrike.map.MapBuilder;

/** Ordered swept checkpoint timing. No Android/file access, objects or strings per step. */
public final class MovementTrial {
    private int next;
    private float elapsed;
    private boolean running, finished;
    private final MovementGhost ghost = new MovementGhost();
    private int mistakes;
    private boolean retryRecommended;
    // Read once more by the Activity after the GL thread stops, even if the HUD has not ticked.
    private volatile int bestMillis;
    public MovementTrial(int bestMillis) { this.bestMillis = Math.max(0, bestMillis); }
    public int nextGate() { return next; }
    public boolean running() { return running; }
    public boolean finished() { return finished; }
    public int millis() { return Math.round(elapsed * 1000f); }
    public int bestMillis() { return bestMillis; }
    public MovementGhost ghost() { return ghost; }
    /** Reconcile two independent crash-safe records after loading, before starting simulation. */
    public void restoreBestFromGhost() {
        int replayBest = ghost.bestDurationMillis();
        if (replayBest > 0 && (bestMillis == 0 || replayBest < bestMillis)) bestMillis = replayBest;
    }
    public int mistakes() { return mistakes; }
    public boolean retryRecommended() { return retryRecommended; }
    public void reset() {
        next = 0; elapsed = 0f; running = finished = false;
        mistakes = 0; retryRecommended = false; ghost.resetRun();
    }

    /** Start-pad retry, not a respawn: best time/replay and the equipped kit are retained. */
    public void restart(Combatant player) {
        reset();
        player.position.set(8.5f, MapBuilder.GROUND, 32.5f);
        player.velocity.set(0f, 0f, 0f);
        player.yaw = (float) Math.PI / 2f; player.pitch = 0f;
        player.alive = true; player.health = Combatant.MAX_HEALTH; player.respawnTimer = 0f;
        player.grounded = true; player.airJumpUsed = player.wantsJump = false;
        player.crouching = false; player.crouchBlend = 0f; player.sprinting = false;
        player.flash = player.damageFlash = player.landImpact = 0f;
        player.renderPreviousValid = false;
    }

    public void update(float dt, float oldX, float oldY, float oldZ, Combatant p, boolean sliding) {
        if (!Float.isFinite(dt) || dt <= 0f) return;
        float dx = p.position.x - oldX, dz = p.position.z - oldZ;
        if (!p.alive || dx * dx + dz * dz > (64f * dt + 1f) * (64f * dt + 1f)) {
            reset(); return; // respawn/teleport cannot finish or improve a run
        }
        if (p.position.x < 10f && Math.abs(p.position.z - 32f) < 7f) {
            if (running || finished) reset();
        }
        if (running) elapsed += dt;
        if (finished || next >= CourseLayout.count()) return;
        int gate = next;
        if (oldX >= CourseLayout.x(gate) || p.position.x < CourseLayout.x(gate) || dx <= 0f) {
            record(p, false); return;
        }
        float t = (CourseLayout.x(gate) - oldX) / dx;
        float z = oldZ + dz * t, y = oldY + (p.position.y - oldY) * t;
        if (Math.abs(z - CourseLayout.z(gate)) > 6f || y < 2.8f || y > 8f
                || (gate == CourseLayout.JUMP_GATE && (p.grounded || y < 3.2f))
                || (gate == CourseLayout.SLIDE_GATE && !sliding)) {
            if (running) { mistakes++; retryRecommended = true; }
            record(p, false); return;
        }
        retryRecommended = false;
        if (gate == 0) {
            running = true; elapsed = dt * (1f - t);
            ghost.resetRun();
            ghost.record(0, CourseLayout.x(0), y, z, p.yaw, p.crouching, true);
        }
        next++;
        if (next == CourseLayout.count()) {
            elapsed -= dt * (1f - t);
            running = false; finished = true;
            int time = millis();
            // Finish at the swept checkpoint, not at the end-of-step position beyond the gate.
            ghost.record(time, CourseLayout.x(gate), y, z, p.yaw, p.crouching, true);
            if (time > 0 && (bestMillis == 0 || time < bestMillis)) {
                bestMillis = time; ghost.completeRun(time);
            } else if (time == bestMillis && !ghost.hasBest()) {
                ghost.completeRun(time);
            }
        } else {
            record(p, false);
        }
    }

    private void record(Combatant p, boolean force) {
        if (running) ghost.record(millis(), p.position.x, p.position.y, p.position.z,
                p.yaw, p.crouching, force);
    }
}
