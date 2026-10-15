package com.ccs.voxelgun.game;

import com.ccs.voxelgun.input.InputState;
import com.ccs.voxelgun.map.Maps;
import com.ccs.voxelgun.map.maps.BattlefieldMaps;
import com.ccs.voxelgun.weapon.Weapon;
import com.ccs.voxelgun.weapon.WeaponSlot;
import com.ccs.voxelgun.weapon.WeaponType;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Exercises the same queued action as the HUD grenade button, through a real match update. */
public class BattlefieldGrenadeTest {
    @Test
    public void conquestQuickThrowUsesThePouchAndKeepsThePrimaryOnEveryBattlefieldMap() {
        for (int map = 0; map < BattlefieldMaps.COUNT; map++) {
            Match match = create(map, true);
            try {
                Combatant player = match.player();
                Weapon primary = player.weapon();
                int primaryAmmo = primary.ammo();
                int primarySlot = player.slot;
                Weapon grenades = player.weapons[WeaponSlot.GRENADE.ordinal()];
                assertNotNull(grenades);
                assertEquals(WeaponType.FRAG, grenades.type());
                assertEquals(3, pouch(grenades));

                InputState input = new InputState();
                pressGrenade(match, input);

                assertEquals(label(match), 1, activeGrenades(match));
                assertEquals(label(match), 2, pouch(grenades));
                assertTrue(label(match), player.switchTimer > 0f);
                assertEquals(label(match), primarySlot, player.slot);
                assertSame(label(match), primary, player.weapon());
                assertEquals(label(match), primaryAmmo, primary.ammo());
                assertEquals(label(match), 2, publishHud(match).grenadeCount);

                // A one-shot input must not keep throwing merely because more frames elapse.
                run(match, input, .7f);
                assertEquals(label(match), 1, activeGrenades(match));
                assertEquals(label(match), 2, pouch(grenades));
                assertFalse(label(match), input.consumeGrenade());
            } finally {
                match.release();
            }
        }
    }

    @Test
    public void quickThrowRespectsCooldownAndStopsWhenThePouchIsEmptyOnEveryBattlefieldMap() {
        for (int map = 0; map < BattlefieldMaps.COUNT; map++) {
            Match match = create(map, true);
            try {
                Weapon grenades = match.player().weapons[WeaponSlot.GRENADE.ordinal()];
                InputState input = new InputState();
                pressGrenade(match, input);
                assertEquals(label(match), 2, pouch(grenades));
                assertEquals(label(match), 1, activeGrenades(match));

                // A second tap during the existing 0.6-second quick-throw lockout is discarded.
                pressGrenade(match, input);
                assertEquals(label(match), 2, pouch(grenades));
                assertEquals(label(match), 1, activeGrenades(match));

                run(match, input, .65f);
                assertEquals(label(match), 0f, match.player().switchTimer, .0001f);
                assertEquals(label(match), 2, pouch(grenades));
                pressGrenade(match, input);
                assertEquals(label(match), 1, pouch(grenades));
                assertEquals(label(match), 2, activeGrenades(match));

                run(match, input, .65f);
                pressGrenade(match, input);
                assertTrue(label(match), grenades.depleted());
                assertEquals(label(match), 0, pouch(grenades));
                assertEquals(label(match), 3, activeGrenades(match));
                assertEquals(label(match), 0, publishHud(match).grenadeCount);

                // All three projectiles are still inside their 2.6-second fuse at this point.
                // A fourth tap after lockout may neither create a grenade nor refill the pouch.
                run(match, input, .65f);
                pressGrenade(match, input);
                assertEquals(label(match), 3, activeGrenades(match));
                assertEquals(label(match), 0, pouch(grenades));
                assertEquals(label(match), 0, grenades.ammo());
                assertEquals(label(match), 0, grenades.reserve());
                assertEquals(label(match), 0f, match.player().switchTimer, .0001f);
            } finally {
                match.release();
            }
        }
    }

    @Test
    public void originalBattlefieldWithoutGrenadesSafelyConsumesTheAction() {
        for (int map = 0; map < BattlefieldMaps.COUNT; map++) {
            Match match = create(map, false);
            try {
                Combatant player = match.player();
                Weapon primary = player.weapon();
                int primaryAmmo = primary.ammo();
                assertNull(player.weapons[WeaponSlot.GRENADE.ordinal()]);
                InputState input = new InputState();

                pressGrenade(match, input);

                assertEquals(label(match), 0, activeGrenades(match));
                assertEquals(label(match), 0, publishHud(match).grenadeCount);
                assertEquals(label(match), 0f, player.switchTimer, .0001f);
                assertSame(label(match), primary, player.weapon());
                assertEquals(label(match), primaryAmmo, primary.ammo());
                assertFalse(label(match), input.consumeGrenade());
            } finally {
                match.release();
            }
        }
    }

    private static Match create(int battlefield, boolean conquest) {
        MatchConfig config = new MatchConfig();
        config.mode = GameMode.BATTLE_FIELD;
        config.mapIndex = Maps.firstBattlefieldIndex() + battlefield;
        config.conquest = conquest;
        config.totalPlayers = 2;
        config.modifiers = MatchModifier.NO_BOTS.bit;
        config.seed = 4242L;
        Match match = new Match(config);
        run(match, new InputState(), 3.5f);
        assertEquals(label(match), Match.State.PLAYING, match.state());
        assertTrue(label(match), match.player().alive);
        assertEquals(label(match), 0f, match.player().switchTimer, .0001f);
        return match;
    }

    private static void pressGrenade(Match match, InputState input) {
        input.queueGrenade();
        match.update(Match.STEP, input);
    }

    private static void run(Match match, InputState input, float seconds) {
        int steps = Math.round(seconds / Match.STEP);
        for (int step = 0; step < steps; step++) match.update(Match.STEP, input);
    }

    private static HudSnapshot publishHud(Match match) {
        InputState paused = new InputState();
        paused.paused = true;
        match.update(.04f, paused);
        return match.snapshot();
    }

    private static int pouch(Weapon grenades) {
        return grenades.ammo() + grenades.reserve();
    }

    private static int activeGrenades(Match match) {
        int active = 0;
        for (Projectile projectile : match.projectiles()) {
            if (!projectile.active) continue;
            assertEquals(label(match), WeaponType.FRAG, projectile.type);
            assertEquals(label(match), match.player().id, projectile.ownerId);
            assertEquals(label(match), match.player().team, projectile.team);
            assertFalse(label(match), projectile.rocket);
            assertTrue(label(match), projectile.fuse > 0f);
            active++;
        }
        return active;
    }

    private static String label(Match match) {
        return match.map().id();
    }
}
