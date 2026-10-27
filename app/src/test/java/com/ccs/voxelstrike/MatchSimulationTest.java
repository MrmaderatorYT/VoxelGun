package com.ccs.voxelstrike;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ccs.voxelstrike.game.Combatant;
import com.ccs.voxelstrike.game.Difficulty;
import com.ccs.voxelstrike.game.Economy;
import com.ccs.voxelstrike.game.GameMode;
import com.ccs.voxelstrike.game.HudSnapshot;
import com.ccs.voxelstrike.game.KillFeedEntry;
import com.ccs.voxelstrike.game.Match;
import com.ccs.voxelstrike.game.MatchConfig;
import com.ccs.voxelstrike.game.MatchListener;
import com.ccs.voxelstrike.game.MatchResult;
import com.ccs.voxelstrike.input.InputState;
import com.ccs.voxelstrike.math.Vec3;
import com.ccs.voxelstrike.weapon.WeaponSlot;
import com.ccs.voxelstrike.weapon.WeaponType;

import org.junit.Test;

import java.lang.reflect.Field;

/**
 * Runs the simulation headlessly on the JVM. Nothing here touches Android or GL, so the whole
 * gameplay loop — bots, ballistics, scoring and mode rules — is covered by ordinary unit tests.
 */
public class MatchSimulationTest {
    /** Counts what the simulation reports back so tests can assert on it. */
    private static final class Recorder implements MatchListener {
        int sounds;
        int kills;
        int localHits;
        MatchResult result;

        @Override
        public void onSound(int kind, float x, float y, float z, float volume, float pitch) {
            sounds++;
        }

        @Override
        public void onLocalHit(boolean headshot, boolean lethal) {
            localHits++;
        }

        @Override
        public void onLocalDamage(float amount, float fromYaw) {}

        @Override
        public void onKillFeed(KillFeedEntry entry) {
            kills++;
        }

        @Override
        public void onMatchEnd(MatchResult result) {
            this.result = result;
        }
    }

    private static MatchConfig config(GameMode mode, int bots, Difficulty difficulty) {
        MatchConfig config = new MatchConfig();
        config.mode = mode;
        config.mapIndex = 0;
        config.botCount = bots;
        config.difficulty = difficulty;
        config.seed = 4242L;
        return config;
    }

    private static void run(Match match, InputState input, float seconds) {
        int steps = (int) (seconds / Match.STEP);
        for (int i = 0; i < steps; i++) {
            match.update(Match.STEP, input);
        }
    }

    @Test
    public void teamDeathmatchRunsAndBotsFight() {
        Match match = new Match(config(GameMode.TEAM_DEATHMATCH, 7, Difficulty.VETERAN));
        Recorder recorder = new Recorder();
        match.setListener(recorder);

        Vec3 start = new Vec3();
        Combatant firstBot = match.combatants().get(1);
        start.set(firstBot.position);

        run(match, new InputState(), 45f);

        assertTrue("bots never moved", Vec3.distance(start, firstBot.position) > 2f);
        assertTrue("no shots were fired", recorder.sounds > 20);
        assertTrue("nobody scored in 45 seconds", recorder.kills > 0);

        HudSnapshot snapshot = match.snapshot();
        assertEquals(GameMode.TEAM_DEATHMATCH, snapshot.mode);
        assertTrue(snapshot.blueScore + snapshot.redScore > 0);
        assertTrue(snapshot.scoreboardCount >= 8);
        match.release();
    }

    @Test
    public void everyCombatantStaysInsideTheMap() {
        Match match = new Match(config(GameMode.DEATHMATCH, 6, Difficulty.REGULAR));
        run(match, new InputState(), 30f);
        for (Combatant combatant : match.combatants()) {
            assertTrue(combatant.name + " fell out of the world", combatant.position.y > -1f);
            assertTrue(combatant.name + " left the map on x",
                    combatant.position.x > 0 && combatant.position.x < 128);
            assertTrue(combatant.name + " left the map on z",
                    combatant.position.z > 0 && combatant.position.z < 128);
        }
        match.release();
    }

    @Test
    public void playerInputDrivesMovementAndFiring() {
        Match match = new Match(config(GameMode.DEATHMATCH, 1, Difficulty.RECRUIT));
        Recorder recorder = new Recorder();
        match.setListener(recorder);
        Combatant player = match.player();
        run(match, new InputState(), 3.3f);
        Vec3 start = new Vec3().set(player.position);

        InputState input = new InputState();
        input.moveY = 1f;
        input.fireHeld = true;
        run(match, input, 3f);

        assertTrue("player did not move", Vec3.distance(start, player.position) > 1f);
        assertTrue("player weapon never fired",
                player.weapon().ammo() < player.weaponType().magazine());
        assertTrue(recorder.sounds > 0);
        match.release();
    }

    @Test
    public void gunGameStartsEveryoneOnTheFirstLadderStep() {
        Match match = new Match(config(GameMode.GUN_GAME, 4, Difficulty.REGULAR));
        for (Combatant combatant : match.combatants()) {
            assertEquals(0, combatant.ladderStep);
        }
        run(match, new InputState(), 20f);
        HudSnapshot snapshot = match.snapshot();
        assertTrue(snapshot.ladderTotal >= 10);
        match.release();
    }

    @Test
    public void zombieModeSpawnsMeleeHordeAndEndsWithThePlayer() {
        Match match = new Match(config(GameMode.ZOMBIE, 5, Difficulty.REGULAR));
        Recorder recorder = new Recorder();
        match.setListener(recorder);

        int zombies = 0;
        for (Combatant combatant : match.combatants()) {
            if (combatant.zombie) {
                zombies++;
                assertTrue(combatant.name + " is not melee only",
                        combatant.weaponType().melee());
            }
        }
        assertEquals(5, zombies);
        assertFalse(match.player().zombie);

        // Drive the player into the horde with no input; the match must terminate cleanly.
        run(match, new InputState(), 120f);
        assertTrue(match.state() == Match.State.PLAYING
                || match.state() == Match.State.FINISHED);
        match.release();
    }

    @Test
    public void knifeFightArmsEveryoneWithTheLethalBlade() {
        Match match = new Match(config(GameMode.KNIFE_FIGHT, 5, Difficulty.REGULAR));
        Recorder recorder = new Recorder();
        match.setListener(recorder);

        for (Combatant combatant : match.combatants()) {
            assertEquals(combatant.name + " is not on the knife",
                    WeaponType.KNIFE, combatant.weaponType());
            for (WeaponSlot slot : WeaponSlot.values()) {
                if (slot != WeaponSlot.MELEE) {
                    assertNull(combatant.name + " kept a " + slot + " weapon",
                            combatant.weapons[slot.ordinal()]);
                }
            }
        }

        // The point of the mode: one connected swing kills a target at full health.
        Combatant target = match.combatants().get(1);
        assertEquals(Combatant.MAX_HEALTH, target.health, 0.001f);
        assertTrue("a knife hit must be lethal from full health",
                target.applyDamage(WeaponType.KNIFE.damage(), 0, 0f));

        run(match, new InputState(), 40f);
        assertTrue(match.state() == Match.State.PLAYING
                || match.state() == Match.State.FINISHED);
        match.release();
    }

    @Test
    public void theArsenalKnifeIsTheLethalOneAndTheHordeKeepsTheOldBlade() {
        assertFalse("the knife is a normal loadout weapon", WeaponType.KNIFE.modeOnly());
        assertTrue("any knife hit must be lethal",
                WeaponType.KNIFE.damage() >= Combatant.MAX_HEALTH);
        assertTrue("the horde blade is mode-only", WeaponType.SCRAP_KNIFE.modeOnly());
        assertTrue("a zombie swing must not kill outright",
                WeaponType.SCRAP_KNIFE.damage() < Combatant.MAX_HEALTH);
    }

    @Test
    public void theBuyMenuSellsAPrimaryOutOfRoundMoney() {
        // Zombie survival is the mode that still runs an economy; the deathmatch modes give the
        // whole rack away instead.
        Match match = new Match(config(GameMode.ZOMBIE, 3, Difficulty.RECRUIT));
        InputState input = new InputState();

        // You spawn with a sidearm and nothing in the primary slot; that is what buying is for.
        assertNull(match.player().weapons[WeaponSlot.PRIMARY.ordinal()]);
        assertEquals(Economy.STARTING_MONEY, match.player().money);
        assertTrue("the buy window must be open at spawn", match.buyOpen());

        // A rifle is out of reach on the opening money, which is the point of the pistol start.
        assertTrue(WeaponType.AK15.cost() > Economy.STARTING_MONEY);
        input.queuePurchase(WeaponType.AK15.ordinal());
        run(match, input, 0.2f);
        assertNull(match.player().weapons[WeaponSlot.PRIMARY.ordinal()]);
        assertEquals(Economy.STARTING_MONEY, match.player().money);

        input.queuePurchase(WeaponType.SPECTRE.ordinal());
        run(match, input, 0.2f);

        Combatant player = match.player();
        assertEquals(WeaponType.SPECTRE, player.weapons[WeaponSlot.PRIMARY.ordinal()].type());
        assertEquals(WeaponType.SPECTRE, player.kit[WeaponSlot.PRIMARY.ordinal()]);
        assertEquals(Economy.STARTING_MONEY - WeaponType.SPECTRE.cost(), player.money);

        // Buying the same gun twice must not charge twice.
        input.queuePurchase(WeaponType.SPECTRE.ordinal());
        run(match, input, 0.2f);
        assertEquals(Economy.STARTING_MONEY - WeaponType.SPECTRE.cost(), match.player().money);
        match.release();
    }

    @Test
    public void zombieShopReopensAtTheStartOfEveryFifthWave() throws Exception {
        Match match = new Match(config(GameMode.ZOMBIE, 1, Difficulty.RECRUIT));
        InputState input = new InputState();
        try {
            run(match, input, 3.3f);
            match.player().health = 1_000_000f;
            match.player().buyTimer = 0f;
            assertFalse("the opening buy window should be closed", match.buyOpen());

            setPrivateInt(match, "wave", 4);
            setPrivateFloat(match, "waveTimer", 0f);
            match.update(Match.STEP, input);
            assertEquals(5, getPrivateInt(match, "wave"));
            assertTrue("wave 5 must reopen the shop", match.buyOpen());
            assertEquals(Economy.BUY_WINDOW_SECONDS, match.player().buyTimer, 0.001f);

            match.player().buyTimer = 0f;
            setPrivateInt(match, "wave", 5);
            setPrivateFloat(match, "waveTimer", 0f);
            match.update(Match.STEP, input);
            assertEquals(6, getPrivateInt(match, "wave"));
            assertFalse("ordinary waves must not reopen the shop", match.buyOpen());

            setPrivateInt(match, "wave", 9);
            setPrivateFloat(match, "waveTimer", 0f);
            match.update(Match.STEP, input);
            assertEquals(10, getPrivateInt(match, "wave"));
            assertTrue("wave 10 must reopen the shop again", match.buyOpen());
        } finally {
            match.release();
        }
    }

    @Test
    public void aBoughtWeaponComesBackAfterDeath() {
        Combatant fighter = new Combatant(9, "Test");
        fighter.setLoadout(null, WeaponType.P9, WeaponType.KNIFE, WeaponType.FRAG);
        assertNull(fighter.weapons[WeaponSlot.PRIMARY.ordinal()]);

        fighter.kit[WeaponSlot.PRIMARY.ordinal()] = WeaponType.SCAR_H;
        fighter.applyKit();
        assertEquals(WeaponType.SCAR_H, fighter.weapons[WeaponSlot.PRIMARY.ordinal()].type());
        assertEquals(WeaponSlot.PRIMARY.ordinal(), fighter.slot);

        // Empty the magazine, respawn: the kit is handed back fresh, not as it was left.
        fighter.weapons[WeaponSlot.PRIMARY.ordinal()].consume();
        fighter.applyKit();
        assertEquals(WeaponType.SCAR_H.magazine(),
                fighter.weapons[WeaponSlot.PRIMARY.ordinal()].ammo());
    }

    @Test
    public void theDeathmatchModesGiveEveryWeaponAwayForNothing() {
        for (GameMode mode : new GameMode[]{GameMode.TEAM_DEATHMATCH, GameMode.DEATHMATCH}) {
            assertTrue(mode + " should have a free arsenal", mode.freeArsenal());
            Match match = new Match(config(mode, 3, Difficulty.RECRUIT));
            InputState input = new InputState();

            // A primary from the first second, and the list never closes.
            assertNotNull(match.player().weapons[WeaponSlot.PRIMARY.ordinal()]);
            assertTrue(match.buyOpen());

            // The most expensive gun in the game, on an empty wallet.
            match.player().money = 0;
            input.queuePurchase(WeaponType.RPG_V.ordinal());
            run(match, input, 0.2f);
            assertEquals(WeaponType.RPG_V, match.player().kit[WeaponSlot.PRIMARY.ordinal()]);
            assertEquals(0, match.player().money);

            // Swapping again costs nothing either, and the window is still open well past the
            // twenty second buy time the economy modes get.
            run(match, input, 30f);
            assertTrue("the free rack must stay open", match.buyOpen());
            input.queuePurchase(WeaponType.BARRETT_M.ordinal());
            run(match, input, 0.2f);
            assertEquals(WeaponType.BARRETT_M, match.player().kit[WeaponSlot.PRIMARY.ordinal()]);
            match.release();
        }
    }

    @Test
    public void gunGameAndKnifeFightHaveNoBuyMenu() {
        assertFalse(GameMode.GUN_GAME.buyMenu());
        assertFalse(GameMode.KNIFE_FIGHT.buyMenu());
        assertTrue(GameMode.TEAM_DEATHMATCH.buyMenu());
        assertTrue(GameMode.DEATHMATCH.buyMenu());
        assertTrue(GameMode.ZOMBIE.buyMenu());
        // A knife kill has to pay more than a rifle kill or nobody would ever push with one.
        assertTrue(Economy.killReward(WeaponType.KNIFE) > Economy.killReward(WeaponType.AK15));
    }

    @Test
    public void jumpingOutOfACrouchStandsUpInsteadOfHopping() {
        Match match = new Match(config(GameMode.DEATHMATCH, 2, Difficulty.RECRUIT));
        InputState input = new InputState();
        run(match, input, 4.0f);  // finish the countdown and settle onto the floor

        Combatant player = match.player();
        input.crouching = true;
        run(match, input, 0.5f);
        assertTrue("the crouch should have taken hold", player.crouching);
        assertTrue("the player should be standing on something", player.grounded);

        // The first jump spends itself on standing up: no hop, and the toggle is released.
        input.queueJump();
        match.update(Match.STEP, input);
        assertTrue("a jump out of a crouch must not launch", player.velocity.y <= 0f);
        assertFalse("the crouch toggle must be released", input.crouching);
        assertFalse(player.crouching);

        // Standing, the next jump does what it says.
        run(match, input, 0.4f);
        input.queueJump();
        match.update(Match.STEP, input);
        assertTrue("a standing jump must launch", player.velocity.y > 0f);
        match.release();
    }

    @Test
    public void theMacheteIsGone() {
        for (WeaponType type : WeaponType.values()) {
            assertFalse("the machete should have been removed",
                    "machete".equals(type.modelKey()));
            assertFalse(type.name().contains("MACHETE"));
        }
    }

    @Test
    public void matchEndsOnTheTimeLimit() {
        MatchConfig config = config(GameMode.DEATHMATCH, 3, Difficulty.RECRUIT);
        Match match = new Match(config);
        Recorder recorder = new Recorder();
        match.setListener(recorder);
        run(match, new InputState(), GameMode.DEATHMATCH.timeLimitSeconds() + 6f);
        assertEquals(Match.State.FINISHED, match.state());
        assertTrue(recorder.result != null);
        assertTrue(recorder.result.coinsEarned > 0);
        match.release();
    }

    private static void setPrivateInt(Object target, String name, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static void setPrivateFloat(Object target, String name, float value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setFloat(target, value);
    }

    private static int getPrivateInt(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(target);
    }
}
