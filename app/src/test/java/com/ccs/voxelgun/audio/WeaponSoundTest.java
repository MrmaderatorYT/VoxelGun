package com.ccs.voxelgun.audio;

import com.ccs.voxelgun.weapon.WeaponType;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class WeaponSoundTest {
    private static final int SAMPLE_RATE = 44100;

    @Test public void everyFirearmHasItsOwnShotProfile() {
        Set<Integer> assigned = new HashSet<>();
        int firearms = 0;
        for (WeaponType type : WeaponType.values()) {
            if (type.melee() || type.grenade()) continue;
            firearms++;
            assertTrue(type.displayName(), Sfx.weaponShot(type.soundKind()));
            assertTrue("duplicate sound profile for " + type.displayName(),
                    assigned.add(type.soundKind()));
            assertTrue(type.displayName(), Sfx.gunshot(type.soundKind()));
        }

        assertEquals(Sfx.LAST_WEAPON_SHOT - Sfx.FIRST_WEAPON_SHOT + 1, firearms);
        assertEquals(firearms, assigned.size());
        assertFalse(Sfx.weaponShot(Sfx.SWING));
        assertFalse(Sfx.gunshot(Sfx.PIN));
    }

    @Test public void everyShotProfileRendersAudiblePcmAndSeededVariants() {
        Set<Integer> fingerprints = new HashSet<>();
        for (int kind = Sfx.FIRST_WEAPON_SHOT; kind <= Sfx.LAST_WEAPON_SHOT; kind++) {
            short[] pcm = NativeSynth.synthesize(kind, SAMPLE_RATE, 123, 0.9f);
            long energy = 0;
            int peak = 0;
            for (short sample : pcm) {
                energy += Math.abs((int) sample);
                peak = Math.max(peak, Math.abs((int) sample));
            }
            assertTrue("clip too short: " + kind, pcm.length >= SAMPLE_RATE * 0.13f);
            assertTrue("silent profile: " + kind, energy > 100_000L);
            assertTrue("weak profile: " + kind, peak > 12_000);
            assertTrue("identical profile: " + kind,
                    fingerprints.add(Arrays.hashCode(pcm)));

            short[] variant = NativeSynth.synthesize(kind, SAMPLE_RATE, 154, 0.9f);
            assertNotEquals("seed did not vary profile " + kind,
                    Arrays.hashCode(pcm), Arrays.hashCode(variant));
        }
    }

    @Test public void weaponFamiliesKeepReadableTailLengths() {
        int vectorFrames = render(WeaponType.VECTOR9).length;
        int p9Frames = render(WeaponType.P9).length;
        int scarFrames = render(WeaponType.SCAR_H).length;
        int pumperFrames = render(WeaponType.PUMPER).length;
        int barrettFrames = render(WeaponType.BARRETT_M).length;
        int launcherFrames = render(WeaponType.RPG_V).length;

        assertTrue(vectorFrames < p9Frames);
        assertTrue(p9Frames < scarFrames);
        assertTrue(scarFrames < pumperFrames);
        assertTrue(pumperFrames < barrettFrames);
        assertTrue(pumperFrames < launcherFrames);
    }

    private short[] render(WeaponType type) {
        return NativeSynth.synthesize(type.soundKind(), SAMPLE_RATE, 321, 0.9f);
    }
}
