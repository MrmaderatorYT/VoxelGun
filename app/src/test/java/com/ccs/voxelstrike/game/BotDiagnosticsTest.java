package com.ccs.voxelstrike.game;

import org.junit.Test;

import static org.junit.Assert.*;

public class BotDiagnosticsTest {
    private static final float STEP = 1f / 60f;
    private static Combatant bot() {
        Combatant bot = new Combatant(1, "Diagnostic bot");
        bot.bot = true;
        bot.position.set(12f, 1f, 12f);
        return bot;
    }

    @Test public void intentionalCombatObjectiveCoverAndRangeHoldsAreNotStalls() {
        for (int reason : new int[] {BotDiagnostics.COMBAT, BotDiagnostics.OBJECTIVE,
                BotDiagnostics.COVER, BotDiagnostics.RANGE, BotDiagnostics.INVESTIGATION}) {
            BotDiagnostics diagnostics = new BotDiagnostics(2);
            Combatant bot = bot();
            for (int i = 0; i < 1200; i++) {
                bot.yaw = (float) Math.sin(i * .02) * .4f;
                diagnostics.sample(bot, STEP, true, false, reason);
            }
            BotDiagnostics.Snapshot result = diagnostics.snapshot();
            assertEquals(20f, result.holdSeconds, .01f);
            assertEquals(0f, result.stalledSeconds, 0f);
            assertEquals(0f, result.idleSeconds, 0f);
            assertEquals(0f, result.spinningSeconds, 0f);
            assertEquals(20f, result.bots[0].holdSecondsByReason[reason], .01f);
        }
    }

    @Test public void blockedIntentIsMeasuredFromActualPositionNotCommandedVelocity() {
        BotDiagnostics diagnostics = new BotDiagnostics(2);
        Combatant bot = bot();
        bot.velocity.x = 8f;
        for (int i = 0; i < 600; i++) {
            // Collision jitter must not be credited as forward progress.
            bot.position.x = 12f + ((i & 1) == 0 ? .015f : -.015f);
            diagnostics.sample(bot, STEP, true, true, BotDiagnostics.NONE);
        }
        BotDiagnostics.Snapshot result = diagnostics.snapshot();
        assertTrue(result.stalledSeconds > 8f);
        assertTrue(result.maxStallSeconds > 8f);
        assertEquals(10f, result.movementIntentSeconds, .01f);
    }

    @Test public void stationaryIdleAndContinuousTurningRemainVisible() {
        BotDiagnostics diagnostics = new BotDiagnostics(2);
        Combatant bot = bot();
        for (int i = 0; i < 600; i++) {
            bot.yaw += .05f;
            diagnostics.sample(bot, STEP, true, false, BotDiagnostics.NONE);
        }
        BotDiagnostics.Snapshot result = diagnostics.snapshot();
        assertEquals(10f, result.idleSeconds, .01f);
        assertEquals(0f, result.stalledSeconds, 0f);
        assertTrue(result.spinningSeconds > 6f);
        assertTrue(result.turnRadians > 29f);
    }

    @Test public void walkingAndShortReversalsAreNotStalls() {
        BotDiagnostics diagnostics = new BotDiagnostics(2);
        Combatant bot = bot();
        for (int i = 0; i < 600; i++) {
            bot.position.x += .05f;
            diagnostics.sample(bot, STEP, true, true, BotDiagnostics.NONE);
        }
        BotDiagnostics.Snapshot result = diagnostics.snapshot();
        assertTrue(result.movingSeconds > 9.9f);
        assertTrue(result.distance > 29f);
        assertEquals(0f, result.stalledSeconds, 0f);
    }

    @Test public void countdownDeathAndRespawnTeleportDoNotInflateMetrics() {
        BotDiagnostics diagnostics = new BotDiagnostics(2);
        Combatant bot = bot();
        for (int i = 0; i < 600; i++)
            diagnostics.sample(bot, STEP, false, true, BotDiagnostics.NONE);
        assertEquals(0f, diagnostics.snapshot().activeSeconds, 0f);
        diagnostics.sample(bot, STEP, true, true, BotDiagnostics.NONE);
        bot.alive = false;
        for (int i = 0; i < 600; i++)
            diagnostics.sample(bot, STEP, true, true, BotDiagnostics.NONE);
        bot.position.x = 90f;
        bot.alive = true;
        bot.deaths++;
        diagnostics.sample(bot, STEP, true, true, BotDiagnostics.NONE);
        bot.position.x = 20f;
        diagnostics.sample(bot, STEP, true, true, BotDiagnostics.NONE);
        BotDiagnostics.Snapshot result = diagnostics.snapshot();
        assertEquals(0f, result.distance, 0f);
        assertEquals(0f, result.stalledSeconds, 0f);
        assertEquals(1L, result.teleports);
    }

    @Test public void budgetDeferralsDoNotBecomeFailedOrRepeatedGoals() {
        BotDiagnostics diagnostics = new BotDiagnostics(2);
        diagnostics.sample(bot(), STEP, true, false, BotDiagnostics.NONE);
        for (int i = 0; i < 12; i++) diagnostics.pathDeferred(1);
        diagnostics.pathFailed(1, 100);
        diagnostics.pathFailed(1, 100);
        diagnostics.pathSucceeded(1);
        diagnostics.pathFailed(1, 100);
        diagnostics.recoveryStarted(1);
        diagnostics.goalAbandoned(1);
        BotDiagnostics.Snapshot first = diagnostics.snapshot();
        assertEquals(12L, first.pathDeferrals);
        assertEquals(3L, first.pathFailures);
        assertEquals(2L, first.repeatedGoalFailures);
        assertEquals(1L, first.pathSuccesses);
        assertEquals(1L, first.recoveries);
        assertEquals(1L, first.abandonedGoals);
        diagnostics.pathFailed(1, 100);
        assertEquals("abandoning a goal ends its repeated-failure sequence", 2L,
                diagnostics.snapshot().repeatedGoalFailures);
        assertEquals("snapshots must be detached from simulation", 3L, first.pathFailures);
    }

    @Test public void successfulSearchCannotHideRepeatedPhysicalRouteFailures() {
        BotDiagnostics diagnostics = new BotDiagnostics(2);
        diagnostics.sample(bot(), STEP, true, false, BotDiagnostics.NONE);
        diagnostics.pathFailed(1, 100);
        diagnostics.pathSucceeded(1);
        diagnostics.pathFailed(1, 100);
        assertEquals(1L, diagnostics.snapshot().repeatedGoalFailures);
        diagnostics.goalReached(1);
        diagnostics.pathSucceeded(1);
        diagnostics.pathFailed(1, 100);
        assertEquals("reaching the goal ends its repeated-failure sequence", 1L,
                diagnostics.snapshot().repeatedGoalFailures);
        diagnostics.pathFailed(1, 200);
        assertEquals("a different goal starts its own sequence", 1L,
                diagnostics.snapshot().repeatedGoalFailures);
    }

}
