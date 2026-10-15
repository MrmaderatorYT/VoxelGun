package com.ccs.voxelgun.game;

import com.ccs.voxelgun.input.InputState;
import com.ccs.voxelgun.map.CourseLayout;
import com.ccs.voxelgun.map.Maps;
import com.ccs.voxelgun.weapon.WeaponType;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercises course geometry, movement, collision and timing together at the real fixed step. */
public class MovementTrainingTest {
    @Test public void repeatedUntimedLandingsDoNotGrantSlideHopSpeed() {
        MatchConfig config = new MatchConfig();
        config.mode = GameMode.MOVEMENT_TRAINING;
        config.modifiers = MatchModifier.BUNNY_HOP.bit;
        Match match = new Match(config);
        try {
            InputState input = new InputState();
            for (int i = 0; i < 210; i++) match.update(Match.STEP, input);
            input.moveY = 1f;
            input.sprinting = true;
            input.setDeviceJump(true);
            match.player().yaw = (float) Math.PI / 2f;
            float reference = 7.7f * WeaponType.KNIFE.moveSpeedMultiplier();
            float maximum = 0f;
            for (int i = 0; i < 1200; i++) {
                // Wrap a flat lane for a long run without reaching the map boundary.
                if (match.player().position.x > 110f) match.player().position.x = 8f;
                match.update(Match.STEP, input);
                float speed = (float) Math.hypot(match.player().velocity.x, match.player().velocity.z);
                maximum = Math.max(maximum, speed);
                assertTrue(speed <= reference * ArenaMovement.MAX_SPEED_FACTOR + 0.001f);
            }
            assertEquals("holding Jump alone is not a timed slide hop", reference, maximum, 0.01f);
            input.setDeviceJump(false);
            for (int i = 0; i < 90; i++) {
                if (match.player().position.x > 110f) match.player().position.x = 8f;
                match.update(Match.STEP, input);
            }
            assertEquals(reference, match.player().velocity.x, 0.01f);
        } finally { match.release(); }
    }

    @Test public void trainingUsesDedicatedMapAndNoBotsRegardlessOfMenuSettings() {
        MatchConfig config = new MatchConfig();
        config.mode = GameMode.MOVEMENT_TRAINING;
        config.mapIndex = Maps.firstBattlefieldIndex();
        config.botCount = 99;
        config.courseBestMillis = 12345;
        config.modifiers = MatchModifier.BUNNY_HOP.bit | MatchModifier.FOG.bit;
        Match match = new Match(config);
        try {
            assertEquals("movement_course_v1", match.map().id());
            assertEquals(1, match.combatants().size());
            assertEquals(WeaponType.KNIFE, match.player().weaponType());
            InputState input = new InputState();
            for (int i = 0; i < 210; i++) match.update(Match.STEP, input);
            assertTrue(match.snapshot().movementTrial);
            assertTrue(match.snapshot().bhop);
            assertEquals(12345, match.snapshot().courseBestMillis);
            assertEquals(Match.State.PLAYING, match.state());
        } finally { match.release(); }
    }

    @Test public void courseCanBeCompletedWithRealPhysicsAndPauseFreezesTimer() {
        MatchConfig config = new MatchConfig();
        config.mode = GameMode.MOVEMENT_TRAINING;
        Match match = new Match(config);
        try {
            InputState input = new InputState();
            for (int i = 0; i < 210; i++) match.update(Match.STEP, input);
            input.moveY = 1f;
            input.sprinting = true;
            boolean jumped = false, paused = false;
            int target = 0;
            for (int i = 0; i < 2400 && !match.snapshot().courseFinished; i++) {
                Combatant p = match.player();
                while (target < CourseLayout.count() - 1 && p.position.x > CourseLayout.x(target) + 0.3f) target++;
                // Aim beyond the gate plane so the player crosses rather than stopping at it.
                float dx = CourseLayout.x(target) + 0.7f - p.position.x;
                float dz = CourseLayout.z(target) - p.position.z;
                p.yaw = (float) Math.atan2(dx, -dz);
                if (!jumped && p.position.x > 42.9f) { input.queueJump(); jumped = true; }
                input.crouching = p.position.x > 81.8f && p.position.x < 85f;
                match.update(Match.STEP, input);
                if (!paused && match.snapshot().courseRunning) {
                    input.paused = true;
                    match.update(0.04f, input); // flush the HUD before comparing timer snapshots
                    int before = match.snapshot().courseMillis;
                    for (int j = 0; j < 120; j++) match.update(Match.STEP, input);
                    assertEquals(before, match.snapshot().courseMillis);
                    input.paused = false;
                    paused = true;
                }
            }
            assertTrue("course stalled at gate " + match.snapshot().courseGate
                    + ", position " + match.player().position.x + "," + match.player().position.y
                    + "," + match.player().position.z, match.snapshot().courseFinished);
            assertEquals(CourseLayout.count(), match.snapshot().courseGate);
            assertTrue(match.snapshot().courseBestMillis > 0);
            assertTrue(paused);
        } finally { match.release(); }
    }
}
