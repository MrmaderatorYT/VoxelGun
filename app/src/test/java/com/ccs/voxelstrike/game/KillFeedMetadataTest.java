package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.input.InputState;
import com.ccs.voxelstrike.weapon.WeaponType;
import org.junit.Test;

import static org.junit.Assert.*;

/** The reusable feed ring and double-buffered HUD must retain event-time metadata. */
public class KillFeedMetadataTest {
    @Test public void newEntryDoesNotAccidentallyRepresentKnife() {
        KillFeedEntry entry = new KillFeedEntry();
        assertEquals(-1, entry.weaponOrdinal);
        assertEquals(0, entry.flags);
        assertEquals(0f, entry.life, 0f);
    }

    @Test public void legacySetClearsReusedMetadataAndKeepsHeadshotCompatibility() {
        KillFeedEntry entry = new KillFeedEntry();
        entry.set("A", 1, "B", 2, "AWM-X", true, true, WeaponType.AWM_X.ordinal(),
                KillContext.HEADSHOT | KillContext.SCOPED | KillContext.BLINDED);
        entry.life = 0.1f;
        entry.set("World", 3, "C", 4, "Fall", false, false);
        assertEquals(-1, entry.weaponOrdinal);
        assertEquals(0, entry.flags);
        assertFalse(entry.headshot);
        assertFalse(entry.involvesPlayer);
        assertEquals(5.5f, entry.life, 0f);
        entry.set("D", 5, "E", 6, "P9", true, false);
        assertEquals(KillContext.HEADSHOT, entry.flags);
        assertTrue(entry.headshot);
    }

    @Test public void copyPreservesMetadataColorsAndRemainingLifeIndependently() {
        KillFeedEntry source = new KillFeedEntry(), target = new KillFeedEntry();
        int flags = KillContext.NO_SCOPE | KillContext.AIRBORNE;
        source.set("Shooter", 0x12345678, "Target", 0x87654321, "AWM-X", false, true,
                WeaponType.AWM_X.ordinal(), flags);
        source.life = 2.125f;
        target.copyFrom(source);
        assertEquals(source.attacker, target.attacker);
        assertEquals(source.victim, target.victim);
        assertEquals(source.weapon, target.weapon);
        assertEquals(source.attackerColor, target.attackerColor);
        assertEquals(source.victimColor, target.victimColor);
        assertEquals(source.weaponOrdinal, target.weaponOrdinal);
        assertEquals(flags, target.flags);
        assertFalse(target.headshot);
        assertTrue(target.involvesPlayer);
        assertEquals(2.125f, target.life, 0f);
        source.set("World", 0, "Other", 0, "Fall", false, false);
        assertEquals("Shooter", target.attacker);
        assertEquals(WeaponType.AWM_X.ordinal(), target.weaponOrdinal);
        assertEquals(flags, target.flags);
        target.copyFrom(target);
        assertEquals(2.125f, target.life, 0f);
    }

    @Test public void lethalHitCapturesContextBeforeLaterChangesToCombatants() {
        Match match = range();
        try {
            Combatant attacker = match.player(), victim = match.combatants().get(1);
            attacker.aiming = true;
            attacker.grounded = false;
            attacker.flash = 1f;
            victim.flash = 1f;
            kill(match, victim, attacker, WeaponType.AWM_X, true);
            attacker.aiming = false;
            attacker.grounded = true;
            attacker.flash = 0f;
            victim.flash = 0f;
            HudSnapshot snapshot = publish(match);
            assertEquals(1, snapshot.feedCount);
            assertEquals(WeaponType.AWM_X.ordinal(), snapshot.feedWeaponOrdinal[0]);
            assertEquals(KillContext.HEADSHOT | KillContext.SCOPED | KillContext.AIRBORNE
                    | KillContext.BLINDED | KillContext.VICTIM_BLINDED, snapshot.feedFlags[0]);
            assertTrue(snapshot.feedHeadshot[0]);
            assertTrue(snapshot.feedInvolvesPlayer[0]);
        } finally { match.release(); }
    }

    @Test public void ringShiftRetainsOlderFlagsAndDoesNotAliasPublishedSnapshot() {
        Match match = range();
        try {
            Combatant attacker = match.player(), victim = match.combatants().get(1);
            attacker.aiming = true;
            attacker.grounded = true;
            kill(match, victim, attacker, WeaponType.AWM_X, true);
            HudSnapshot first = publish(match);
            int firstFlags = KillContext.HEADSHOT | KillContext.SCOPED;
            assertEquals(firstFlags, first.feedFlags[0]);
            attacker.aiming = false;
            kill(match, victim, attacker, WeaponType.KNIFE, false);
            HudSnapshot second = publish(match);
            assertNotSame(first, second);
            assertEquals(2, second.feedCount);
            assertEquals(WeaponType.KNIFE.ordinal(), second.feedWeaponOrdinal[0]);
            assertEquals(0, second.feedFlags[0]);
            assertEquals(WeaponType.AWM_X.ordinal(), second.feedWeaponOrdinal[1]);
            assertEquals(firstFlags, second.feedFlags[1]);
            assertTrue(second.feedInvolvesPlayer[1]);
            assertTrue(second.feedHeadshot[1]);
            assertEquals(1, first.feedCount);
            assertEquals(WeaponType.AWM_X.ordinal(), first.feedWeaponOrdinal[0]);
            assertEquals(firstFlags, first.feedFlags[0]);
            assertTrue(second.feedLife[1] < first.feedLife[0]);
        } finally { match.release(); }
    }

    @Test public void ringEvictsOldestAndRetainsPerEventWeaponAndHeadshotFlags() {
        Match match = range();
        try {
            Combatant attacker = match.player(), victim = match.combatants().get(1);
            attacker.grounded = true;
            for (int i = 0; i < HudSnapshot.MAX_FEED + 2; i++) {
                boolean scoped = (i & 1) == 0;
                attacker.aiming = scoped;
                victim.name = "Victim " + i;
                kill(match, victim, attacker, WeaponType.AWM_X, scoped);
            }
            HudSnapshot snapshot = publish(match);
            assertEquals(HudSnapshot.MAX_FEED, snapshot.feedCount);
            for (int row = 0; row < snapshot.feedCount; row++) {
                int event = HudSnapshot.MAX_FEED + 1 - row;
                boolean scoped = (event & 1) == 0;
                assertEquals("Victim " + event, snapshot.feedVictim[row]);
                assertEquals(WeaponType.AWM_X.ordinal(), snapshot.feedWeaponOrdinal[row]);
                assertEquals(scoped ? KillContext.SCOPED | KillContext.HEADSHOT
                        : KillContext.NO_SCOPE, snapshot.feedFlags[row]);
                assertEquals(scoped, snapshot.feedHeadshot[row]);
            }
        } finally { match.release(); }
    }

    @Test public void worldFallNeverShowsKnifeIconButActualMeleeKillStillDoes() {
        Match match = range();
        try {
            Combatant victim = match.combatants().get(1);
            kill(match, victim, null, WeaponType.KNIFE, false);
            HudSnapshot fall = publish(match);
            assertEquals("World", fall.feedAttacker[0]);
            assertEquals("Fall", fall.feedWeapon[0]);
            assertEquals(-1, fall.feedWeaponOrdinal[0]);
            assertEquals(0, fall.feedFlags[0]);
            assertFalse(fall.feedInvolvesPlayer[0]);
            match.player().grounded = true;
            kill(match, victim, match.player(), WeaponType.KNIFE, false);
            HudSnapshot melee = publish(match);
            assertEquals(WeaponType.KNIFE.displayName(), melee.feedWeapon[0]);
            assertEquals(WeaponType.KNIFE.ordinal(), melee.feedWeaponOrdinal[0]);
            assertTrue(melee.feedInvolvesPlayer[0]);
        } finally { match.release(); }
    }

    @Test public void botKillKeepsBadgesWithoutMarkingPlayerInvolvement() {
        Match match = range();
        try {
            Combatant attacker = match.combatants().get(1), victim = match.combatants().get(2);
            attacker.grounded = false;
            attacker.aiming = false;
            attacker.flash = 0.8f;
            kill(match, victim, attacker, WeaponType.AWM_X, false);
            HudSnapshot snapshot = publish(match);
            assertEquals(1, snapshot.feedCount);
            assertEquals(KillContext.NO_SCOPE | KillContext.AIRBORNE | KillContext.BLINDED,
                    snapshot.feedFlags[0]);
            assertFalse(snapshot.feedInvolvesPlayer[0]);
        } finally { match.release(); }
    }

    @Test public void nonLethalHitDoesNotCreateKillBadges() {
        Match match = range();
        try {
            Combatant victim = match.combatants().get(1);
            victim.health = Combatant.MAX_HEALTH;
            victim.armor = 0f;
            match.player().aiming = true;
            match.applyDamage(victim, match.player(), 1f, true, WeaponType.AWM_X);
            assertEquals(0, publish(match).feedCount);
            assertTrue(victim.alive);
        } finally { match.release(); }
    }

    private Match range() {
        MatchConfig config = new MatchConfig();
        config.mode = GameMode.RANGE;
        config.botCount = 2;
        config.seed = 918;
        return new Match(config);
    }

    private void kill(Match match, Combatant victim, Combatant attacker,
                      WeaponType weapon, boolean headshot) {
        victim.alive = true;
        victim.health = Combatant.MAX_HEALTH;
        victim.armor = 0f;
        match.applyDamage(victim, attacker, 1000f, headshot, weapon);
        assertFalse(victim.alive);
    }

    private HudSnapshot publish(Match match) {
        InputState input = new InputState();
        input.paused = true;
        match.update(0.04f, input);
        return match.snapshot();
    }
}
