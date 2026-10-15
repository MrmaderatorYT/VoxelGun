package com.ccs.voxelgun.game;

import com.ccs.voxelgun.input.InputState;
import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.weapon.WeaponType;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real FSM, target perception, weapon gate and voxel collision, rather than a mocked firing API. */
public class BotCombatIntegrationTest {
    @Test public void sniperTravelJumpLandsAndSettlesBeforeActualShot() {
        MatchConfig config = new MatchConfig();
        config.mode = GameMode.DEATHMATCH; config.botCount = 1;
        config.difficulty = Difficulty.ELITE; config.seed = 52443L;
        config.modifiers = MatchModifier.BUNNY_HOP.bit;
        Match match = new Match(config);
        try {
            for (int x = 50; x <= 110; x++) for (int z = 20; z <= 42; z++) {
                for (int y = 0; y < match.world().sizeY(); y++) {
                    match.world().set(x, y, z, y == 0 ? Materials.CONCRETE : Materials.AIR);
                }
            }
            match.world().commit(); match.nav().refreshArea(match.world(), 50, 20, 110, 42);
            InputState idle = new InputState();
            for (int tick = 0; tick < 300 && match.state() == Match.State.COUNTDOWN; tick++) {
                match.update(Match.STEP, idle);
            }
            Combatant sniper = match.combatants().get(1), enemy = match.player();
            sniper.setSingleWeapon(WeaponType.AWM_X);
            sniper.position.set(60.5f, 2.3f, 30.5f); sniper.velocity.set(0f, 3f, 0f);
            sniper.grounded = false; sniper.aiming = false; sniper.sprinting = false;
            sniper.yaw = (float) Math.PI / 2f; sniper.switchTimer = 0f;
            enemy.position.set(94.5f, 1f, 30.5f); enemy.velocity.set(0f, 0f, 0f);
            enemy.grounded = true; enemy.health = 10000f; enemy.armor = 10000f;
            int shots = 0; boolean observedFlight = false;
            for (int tick = 0; tick < 900; tick++) {
                boolean airborneBefore = !sniper.grounded;
                int ammoBefore = sniper.weapon().ammo();
                match.update(Match.STEP, idle);
                int spent = ammoBefore - sniper.weapon().ammo();
                if (spent > 0) {
                    assertFalse("grounded contact is required before the weapon consumes ammo", airborneBefore);
                    assertTrue("sniper waits for scope instead of noscoping through a travel jump", sniper.aiming);
                    assertTrue("a precision shot is not taken while still sliding",
                            Math.hypot(sniper.velocity.x, sniper.velocity.z) <= 1.21f);
                    shots += spent;
                }
                observedFlight |= airborneBefore;
            }
            assertTrue(observedFlight);
            assertTrue("landing discipline must not turn the sniper inert", shots > 0);
        } finally { match.release(); }
    }
}
