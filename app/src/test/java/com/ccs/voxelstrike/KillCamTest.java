package com.ccs.voxelstrike;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ccs.voxelstrike.game.Combatant;
import com.ccs.voxelstrike.game.KillCam;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

/** The replay buffer: what it records, what it refuses, and that playback is smooth. */
public class KillCamTest {
    private static List<Combatant> oneCombatant(Combatant combatant) {
        return Collections.singletonList(combatant);
    }

    /** Walks a combatant in a straight line for {@code seconds}, sampling as the match would. */
    private static KillCam walk(Combatant combatant, float seconds) {
        KillCam cam = new KillCam(1);
        float step = 1f / 60f;
        for (float t = 0f; t < seconds; t += step) {
            combatant.position.x += step;          // one metre per second
            cam.record(step, oneCombatant(combatant));
        }
        return cam;
    }

    @Test
    public void aReplayNeedsSomethingRecorded() {
        KillCam cam = new KillCam(2);
        assertFalse("an empty buffer started a replay", cam.start(0));
        assertFalse(cam.playing());
    }

    @Test
    public void playbackFollowsTheRecordedPath() {
        Combatant killer = new Combatant(1, "Killer");
        KillCam cam = walk(killer, 4f);

        assertTrue(cam.start(0));
        float[] camera = new float[5];
        assertTrue(cam.advance(1f / 60f, camera));
        float startX = camera[0];

        for (int i = 0; i < 60; i++) {
            cam.advance(1f / 60f, camera);
        }
        assertTrue("the replay did not move forward along the path", camera[0] > startX);
        assertEquals("the replay drifted off the recorded line", killer.eyeY(), camera[1], 0.01f);
    }

    @Test
    public void aReplayEndsAfterItsWindow() {
        Combatant killer = new Combatant(1, "Killer");
        KillCam cam = walk(killer, 4f);
        cam.start(0);
        float[] camera = new float[5];
        for (int i = 0; i < (int) (KillCam.DURATION_SECONDS * 60) + 10; i++) {
            cam.advance(1f / 60f, camera);
        }
        assertFalse("the killcam never handed the camera back", cam.playing());
    }
}
