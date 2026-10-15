package com.ccs.voxelgun.game;

import com.ccs.voxelgun.input.InputState;
import com.ccs.voxelgun.map.Maps;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/** Regression checks through the real match/brain/physics integration, not just planners. */
public class BotStrategicMovementTest {
    @Test public void bothBattlefieldTeamsAdvanceBeyondTheirDeploymentArea() {
        MatchConfig config = new MatchConfig();
        config.mode = GameMode.BATTLE_FIELD;
        config.mapIndex = Maps.firstBattlefieldIndex();
        config.totalPlayers = 4;
        config.seed = 4242L;
        Match match = new Match(config);
        try {
            Combatant blue = match.combatants().get(2);
            Combatant red = match.combatants().get(1);
            assertEquals(Team.BLUE, blue.team);
            assertEquals(Team.RED, red.team);
            float blueX = blue.position.x, redX = red.position.x;
            float blueDistance = distanceToFront(match, blue);
            float redDistance = distanceToFront(match, red);
            // Keep this a locomotion test even if a route eventually acquires an enemy.
            for (Combatant c : match.combatants()) {
                c.health = 100000f;
                c.armor = 100000f;
            }
            run(match, 73.3f);
            assertEquals(Match.State.PLAYING, match.state());
            assertTrue("blue patrol remained around its home deployment: " + blue.position.x,
                    blue.position.x > blueX + 25f);
            assertTrue("red patrol remained around its home deployment: " + red.position.x,
                    red.position.x < redX - 25f);
            assertTrue("blue did not make sustained progress toward the opposing front",
                    distanceToFront(match, blue) < blueDistance - 75f);
            assertTrue("red did not make sustained progress toward the opposing front",
                    distanceToFront(match, red) < redDistance - 75f);
        } finally { match.release(); }
    }

    @Test public void captureAssignmentsHaveIndividualHoldingSlotsRatherThanSharedCenters() {
        MatchConfig config = new MatchConfig();
        config.mode = GameMode.CAPTURE_POINT;
        config.mapIndex = 0;
        config.botCount = 8;
        config.seed = 4242L;
        Match match = new Match(config);
        try {
            Set<Integer> targets = new HashSet<>();
            int offsetTargets = 0;
            for (Combatant c : match.combatants()) {
                if (!c.bot) continue;
                int center = match.objectives().node(c.id % 3);
                int target = match.objectives().goalFor(c);
                targets.add(target);
                if (target != center) {
                    offsetTargets++;
                    place(match, c, center);
                    assertFalse("standing at a shared point center must not finish an offset slot",
                            match.objectives().shouldHold(c));
                }
                float dx = match.nav().cellX(target) - match.nav().cellX(center);
                float dz = match.nav().cellZ(target) - match.nav().cellZ(center);
                assertTrue("an individual slot left its assigned capture point",
                        dx * dx + dz * dz < Objectives.RADIUS * Objectives.RADIUS);
            }
            assertTrue("too many bots still target the same three centers", offsetTargets >= 6);
            assertTrue("formation did not distribute the actual objective assignments", targets.size() >= 6);
            assertEquals(match.objectives().node(0), match.objectives().goalFor(match.player()));
        } finally { match.release(); }
    }

    @Test public void botsStackedAtTheHillSpreadOutWithoutLeavingTheCaptureArea() {
        MatchConfig config = new MatchConfig();
        config.mode = GameMode.KING_OF_THE_HILL;
        config.mapIndex = 0;
        config.botCount = 8;
        config.seed = 4242L;
        Match match = new Match(config);
        try {
            // Remove hostile engagement as a confounder; this exercises point patrol and spacing.
            for (Combatant c : match.combatants()) c.team = Team.BLUE;
            run(match, 3.3f);
            int center = match.objectives().node(0);
            for (Combatant c : match.combatants()) if (c.bot) place(match, c, center);
            run(match, 4f);
            Set<String> occupied = new HashSet<>();
            int occupants = 0;
            float cx = match.nav().cellX(center) + .5f;
            float cz = match.nav().cellZ(center) + .5f;
            for (Combatant c : match.combatants()) {
                if (!c.bot) continue;
                occupied.add(Math.round(c.position.x * 2f) + ":" + Math.round(c.position.z * 2f));
                float dx = c.position.x - cx, dz = c.position.z - cz;
                if (Math.abs(c.position.y - match.nav().nodeY(center)) < 2f
                        && dx * dx + dz * dz < Objectives.RADIUS * Objectives.RADIUS
                        && match.world().lineOfSight(cx, match.nav().nodeY(center) + 1f, cz,
                        c.eyeX(), c.eyeY(), c.eyeZ())) occupants++;
            }
            assertTrue("bots remained in a single overlapped stack", occupied.size() >= 5);
            assertTrue("spacing pushed most of the squad outside the hill", occupants >= 6);
            assertEquals(Match.State.PLAYING, match.state());
        } finally { match.release(); }
    }

    private static float distanceToFront(Match match, Combatant c) {
        int n = match.battlefront().destinationFor(c);
        return (float) Math.hypot(match.nav().cellX(n) + .5f - c.position.x,
                match.nav().cellZ(n) + .5f - c.position.z);
    }

    private static void place(Match match, Combatant c, int node) {
        c.position.set(match.nav().cellX(node) + .5f, match.nav().nodeY(node),
                match.nav().cellZ(node) + .5f);
        c.velocity.set(0f, 0f, 0f);
        c.grounded = true;
        c.crouching = false;
        c.crouchBlend = 0f;
        c.alive = true;
    }

    private static void run(Match match, float seconds) {
        InputState idle = new InputState();
        for (int i = 0; i < Math.ceil(seconds / Match.STEP); i++) match.update(Match.STEP, idle);
    }
}
