package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.voxel.VoxelWorld;

/**
 * A short-lived, approximate sound cue; it never acquires or tracks a combat target.
 *
 * <p>Distance/priority checks precede a bounded sample of authoritative Java voxels. Accepted
 * bursts are rate limited, with one exception for a substantially closer danger. No raycasts,
 * native calls, lists or per-event allocations are needed, even during automatic gunfire.
 */
public final class BotHearing {
    private static final int MAX_SAMPLES = 48;
    private static final float PROBE_INTERVAL = .12f;
    private static final float PRIORITY_MARGIN = .22f;

    private final int seed;
    private float cooldown, probeCooldown, suppressed, cueLeft;
    private float cueX, cueY, cueZ, cueUrgency, cueLifetime;
    private boolean priorityOverride;
    private int probeSamples;

    public BotHearing(long seed) { this.seed = (int) (seed ^ (seed >>> 32)); }

    public void reset() {
        cooldown = probeCooldown = suppressed = cueLeft = 0f;
        cueX = cueY = cueZ = cueUrgency = cueLifetime = 0f;
        priorityOverride = false;
        probeSamples = 0;
    }

    public void update(float dt) {
        if (!(dt > 0f) || !Float.isFinite(dt)) return;
        cooldown = Math.max(0f, cooldown - dt);
        probeCooldown = Math.max(0f, probeCooldown - dt);
        suppressed = Math.max(0f, suppressed - dt);
        cueLeft = Math.max(0f, cueLeft - dt);
    }

    /** Prevents a failed investigation being restarted by the same continuous noise. */
    public void suppressFor(float seconds) {
        if (Float.isFinite(seconds)) suppressed = Math.max(suppressed, seconds);
    }

    /**
     * Inputs use combatant feet positions, matching Match's sound dispatch. The caller supplies
     * the posture/skill-scaled range and retains mode, visible-target and objective restrictions.
     * A true result replaces the cue exposed by the getters; false preserves the previous cue.
     */
    public boolean accept(Combatant self, VoxelWorld world, float x, float y, float z,
                          float range, boolean footstep, Difficulty difficulty) {
        probeSamples = 0;
        if (!self.alive || suppressed > 0f || !(range > 0f) || !Float.isFinite(range)
                || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) return false;
        float dx = x - self.position.x, dy = y - self.position.y, dz = z - self.position.z;
        float distanceSquared = dx * dx + dy * dy + dz * dz;
        if (!Float.isFinite(distanceSquared) || distanceSquared > range * range) return false;
        float distance = (float) Math.sqrt(distanceSquared);
        float urgency = urgency(distance, range, footstep);
        boolean overriding = cooldown > 0f;
        // Retain the close threat briefly even when a distant gun has a much larger sound radius.
        if (cueLeft > 0f && urgency + .08f < cueUrgency) return false;
        if (overriding && (!priorityOverride || urgency < cueUrgency + PRIORITY_MARGIN)) return false;
        if (probeCooldown > 0f && !overriding) return false;
        if (overriding) priorityOverride = false;
        probeCooldown = PROBE_INTERVAL;

        float depth = obstructionDepth(self, world, x, y, z, footstep);
        float audibleRange = range / (1f + depth * (footstep ? .55f : .28f));
        if (distance > audibleRange) return false;
        urgency = urgency(distance, audibleRange, footstep);
        if (cueLeft > 0f && urgency + .08f < cueUrgency) return false;
        if (overriding && urgency < cueUrgency + PRIORITY_MARGIN) return false;

        float skill = difficulty.tactics();
        cooldown = .8f + (1f - skill) * .6f;
        if (!overriding) priorityOverride = true;
        cueUrgency = urgency;
        cueLifetime = 1.1f + Math.min(.7f, urgency * .45f);
        cueLeft = cueLifetime;

        // A stable offset for each small source area avoids averaging repeated shots into exact
        // coordinates, and avoids a stationary burst continually changing the investigation path.
        int key = seed ^ (int) Math.floor(x / 4f) * 0x1f123bb5
                ^ (int) Math.floor(z / 4f) * 0x5f356495 ^ (int) Math.floor(y / 3f) * 0x2c1b3c6d;
        key ^= key >>> 16;
        key *= 0x7feb352d;
        key ^= key >>> 15;
        float angle = (key & 0xffff) * ((float) (Math.PI * 2.0) / 65536f);
        float error = Math.min(4.5f, .55f + distance * (.03f + (1f - skill) * .07f)
                + Math.min(2f, depth) * .3f);
        cueX = x + (float) Math.cos(angle) * error;
        cueZ = z + (float) Math.sin(angle) * error;
        // Keep the estimated floor while discarding sub-voxel vertical precision. Navigation
        // resolves the horizontal region to a standable node; this does not test enemy visibility.
        cueY = Math.round(y);
        return true;
    }

    private static float urgency(float distance, float range, boolean footstep) {
        return 1f / (1f + distance * .17f)
                + Math.max(0f, 1f - distance / range) * .25f
                + (footstep && distance < 7f ? .35f : 0f);
    }

    private float obstructionDepth(Combatant self, VoxelWorld world,
                                   float x, float y, float z, boolean footstep) {
        float ax = self.eyeX(), ay = self.eyeY(), az = self.eyeZ();
        float dx = x - ax, dy = y + (footstep ? .2f : 1.25f) - ay, dz = z - az;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        int samples = Math.min(MAX_SAMPLES, Math.max(1, (int) Math.ceil(length / .65f)));
        float stepLength = length / samples;
        float depth = 0f;
        for (int i = 0; i < samples; i++) {
            float t = (i + .5f) / samples;
            int material = world.get((int) Math.floor(ax + dx * t),
                    (int) Math.floor(ay + dy * t), (int) Math.floor(az + dz * t)) & 255;
            probeSamples++;
            if (Materials.solid((byte) material)) {
                float weight = material == (Materials.GLASS & 255)
                        || material == (Materials.GLASS_TINTED & 255)
                        || material == (Materials.GRATE & 255) ? .3f
                        : material == Materials.LEAVES || material == Materials.CANVAS ? .4f : 1f;
                depth += stepLength * weight;
            }
        }
        return depth;
    }

    public float x() { return cueX; }
    public float y() { return cueY; }
    public float z() { return cueZ; }
    public float urgency() { return cueUrgency; }
    public float lifetimeSeconds() { return cueLifetime; }

    /** Package-visible for the acoustic work-budget regression test. */
    int probeSamples() { return probeSamples; }
}
