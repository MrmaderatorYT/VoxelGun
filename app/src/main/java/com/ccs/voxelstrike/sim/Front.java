package com.ccs.voxelstrike.sim;

import java.util.List;
import java.util.Random;

/**
 * The line, as one number: how many kilometres in from the border the fighting has reached.
 *
 * <p>It moves on the ratio between the attacker's strength and what the defence bought, so defence
 * spending is never a win condition, only a brake. The attacker is stronger by construction and
 * grows; the most a well-funded defence does is stop the line and hold it.
 */
public final class Front {
    /** Kilometres advanced from the border. Only ever compared against region distances. */
    public float advanceKm;
    /** The attacker's current strength; grows every tick. */
    public float enemyStrength;
    /** Last tick's movement, negative when the defence pushed back. */
    public float lastAdvanceKm;

    public Front(SimConfig config) {
        this.enemyStrength = config.enemyStrength;
    }

    /** Moves the line, then restates every region's status against it. */
    void advance(List<Region> regions, double defenceStrength, SimConfig config, Random random) {
        // Logistic, not linear: an attacker mobilises hard early and then runs out of reserves.
        if (config.enemyStrengthCap > 0f) {
            enemyStrength += config.enemyStrengthGrowth
                    * (1f - enemyStrength / config.enemyStrengthCap);
        } else {
            enemyStrength += config.enemyStrengthGrowth;
        }
        double ratio = enemyStrength / (enemyStrength + Math.max(0.0, defenceStrength));
        // A defence at parity holds; below it, the line moves in proportion to the shortfall.
        double push = config.enemyPushKmPerTick * (ratio - 0.5) * 2.0;
        double noise = 1.0 + (random.nextFloat() * 2f - 1f) * config.frontNoise;
        lastAdvanceKm = (float) (push * noise);
        advanceKm = Math.max(0f, advanceKm + lastAdvanceKm);
        for (Region region : regions) {
            region.frontDistanceKm = region.borderDistanceKm - advanceKm;
            region.status = statusFor(region.frontDistanceKm, region.status);
        }
    }

    /** Status is a ratchet: a region that fell does not come back when the line wobbles. */
    static RegionStatus statusFor(float frontDistanceKm, RegionStatus current) {
        RegionStatus next;
        if (frontDistanceKm <= 0f) {
            next = RegionStatus.OCCUPIED;
        } else if (frontDistanceKm < 30f) {
            next = RegionStatus.CONTESTED;
        } else if (frontDistanceKm < 120f) {
            next = RegionStatus.THREATENED;
        } else {
            next = RegionStatus.REAR;
        }
        if (current == RegionStatus.OCCUPIED) {
            return RegionStatus.OCCUPIED;
        }
        return next;
    }
}
