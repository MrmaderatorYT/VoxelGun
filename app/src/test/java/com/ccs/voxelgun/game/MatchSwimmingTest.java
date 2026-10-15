package com.ccs.voxelgun.game;

import com.ccs.voxelgun.input.InputState;
import com.ccs.voxelgun.map.MapBuilder;
import com.ccs.voxelgun.voxel.Materials;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercises the real input, AI and shared-body integration, not only water helpers. */
public class MatchSwimmingTest {
    private Match match(GameMode mode, int bots, int modifiers) {
        MatchConfig config = new MatchConfig();
        config.mode = mode; config.botCount = bots; config.modifiers = modifiers;
        Match m = new Match(config); InputState input = new InputState();
        for (int i = 0; i < 210; i++) m.update(Match.STEP, input);
        MapBuilder b = new MapBuilder(m.world(), 1L);
        b.fill(8, 0, 8, 24, 6, 24, Materials.CONCRETE);
        b.clear(8, 7, 8, 24, 20, 24);
        b.pool(10, 10, 22, 22, 6, 6);
        m.world().commit();
        return m;
    }

    @Test public void heldInputSwimsWithoutRepeatedPressesOrSpendingDoubleJump() {
        Match m = match(GameMode.RANGE, 0,
                MatchModifier.BUNNY_HOP.bit | MatchModifier.DOUBLE_JUMP.bit);
        try {
            Combatant c = m.player(); c.position.set(16.5f, 1, 16.5f);
            c.velocity.set(0, 0, 0); c.grounded = true;
            InputState input = new InputState(); input.setDeviceJump(true); input.queueJump();
            for (int i = 0; i < 50; i++) m.update(Match.STEP, input);
            assertTrue(c.position.y > 4); assertFalse(c.airJumpUsed);
            input.setDeviceJump(false); float y = c.position.y;
            m.update(Match.STEP, input);
            assertTrue(c.position.y <= y); assertTrue(c.velocity.y <= 0);
        } finally { m.release(); }
    }

    @Test public void activeBotsEscapeInBhopMatchesAndResumeOnDryLand() {
        Match m = match(GameMode.TEAM_DEATHMATCH, 1, MatchModifier.BUNNY_HOP.bit);
        try {
            Combatant bot = m.combatants().get(1);
            bot.position.set(16.5f, 1, 16.5f); bot.velocity.set(0, 0, 0); bot.grounded = true;
            m.player().position.set(100, 3, 100);
            boolean escaped = false; InputState input = new InputState();
            for (int i = 0; i < 480; i++) {
                m.update(Match.STEP, input);
                if (bot.grounded && !Swimming.inWater(m.world(), bot)) { escaped = true; break; }
                assertFalse(m.world().boxCollides(bot.position.x, bot.position.y, bot.position.z,
                        Combatant.RADIUS, bot.height()));
            }
            assertTrue("Bots must not depend on the underwater A* floor path", escaped);
            assertTrue(bot.alive);
        } finally { m.release(); }
    }

    @Test public void waterCushionsAHardFall() {
        Match m = match(GameMode.RANGE, 0, 0);
        try {
            Combatant c = m.player(); c.armor = 0;
            c.position.set(16.5f, 7.05f, 16.5f);
            c.velocity.set(0, -46, 0); c.grounded = false;
            InputState input = new InputState();
            for (int i = 0; i < 240; i++) m.update(Match.STEP, input);
            assertEquals(100f, c.health, .001f); assertTrue(c.alive);
            assertTrue(c.position.y >= 1);
        } finally { m.release(); }
    }

    @Test public void enteringShallowWaterOnTheLandingTickDoesNotCauseFallDamage() {
        Match m = match(GameMode.RANGE, 0, 0);
        try {
            for (int x = 10; x <= 22; x++) for (int z = 10; z <= 22; z++)
                m.world().set(x, 5, z, Materials.CONCRETE);
            m.world().commit();
            Combatant c = m.player(); c.armor = 0;
            c.position.set(16.5f, 7.05f, 16.5f);
            c.velocity.set(0, -46, 0); c.grounded = false;
            m.update(.04f, new InputState());
            assertTrue(c.grounded); assertEquals(6f, c.position.y, .001f);
            assertEquals(100f, c.health, .001f);
        } finally { m.release(); }
    }

    @Test public void evenPassiveRangeTargetsCanEscapeWater() {
        Match m = match(GameMode.RANGE, 1, 0);
        try {
            Combatant bot = m.combatants().get(1);
            bot.position.set(16.5f, 1, 16.5f); bot.velocity.set(0, 0, 0); bot.grounded = true;
            InputState input = new InputState(); boolean escaped = false;
            for (int i = 0; i < 480; i++) {
                m.update(Match.STEP, input);
                if (bot.grounded && !Swimming.inWater(m.world(), bot)) { escaped = true; break; }
            }
            assertTrue(escaped);
            for (int i = 0; i < 15; i++) m.update(Match.STEP, input);
            assertFalse(bot.firing);
            assertEquals(0, bot.velocity.x, .001f); assertEquals(0, bot.velocity.z, .001f);
        } finally { m.release(); }
    }

    @Test public void pausingInWaterStopsSimulationAndClearingHoldStopsLiftAfterResume() {
        Match m = match(GameMode.RANGE, 0, 0);
        try {
            Combatant c = m.player(); c.position.set(16.5f, 2, 16.5f); c.velocity.set(0, 0, 0);
            InputState input = new InputState(); input.setTouchJump(true);
            m.update(Match.STEP, input); float y = c.position.y;
            input.paused = true;
            for (int i = 0; i < 30; i++) m.update(Match.STEP, input);
            assertEquals(y, c.position.y, 0);
            input.releaseAll(); input.paused = false; m.update(Match.STEP, input);
            assertTrue(c.velocity.y <= 0);
        } finally { m.release(); }
    }
}
