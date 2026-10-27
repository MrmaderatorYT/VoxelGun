package com.ccs.voxelstrike;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.ccs.voxelstrike.app.ProfileBackup;

import org.json.JSONException;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The backup format, exercised without a device.
 *
 * <p>Types are the whole risk here: SharedPreferences throws when a value comes back as the wrong
 * type, so a backup that turns floats into doubles restores into a crash rather than a profile.
 */
public class ProfileBackupTest {
    private static Map<String, Map<String, ?>> sampleProfile() {
        LinkedHashMap<String, Object> profile = new LinkedHashMap<>();
        profile.put("coins", 3861);
        profile.put("playtime", 2100L);
        profile.put("name", "Player");
        profile.put("owned_skin_gold", true);
        LinkedHashMap<String, Object> hud = new LinkedHashMap<>();
        hud.put("scale", 1.35f);
        hud.put("opacity", 0.85f);
        LinkedHashMap<String, Map<String, ?>> files = new LinkedHashMap<>();
        files.put("voxelstrike_profile", profile);
        files.put("voxelstrike_hud", hud);
        return files;
    }

    @Test
    public void everyTypeSurvivesTheRoundTrip() throws JSONException {
        Map<String, Map<String, Object>> back =
                ProfileBackup.decode(ProfileBackup.encode(sampleProfile()));

        Map<String, Object> profile = back.get("voxelstrike_profile");
        assertEquals(Integer.valueOf(3861), profile.get("coins"));
        assertEquals(Long.valueOf(2100L), profile.get("playtime"));
        assertEquals("Player", profile.get("name"));
        assertEquals(Boolean.TRUE, profile.get("owned_skin_gold"));

        Map<String, Object> hud = back.get("voxelstrike_hud");
        assertTrue("hud scale came back as " + hud.get("scale").getClass(),
                hud.get("scale") instanceof Float);
        assertEquals(1.35f, (Float) hud.get("scale"), 1e-6f);
        assertEquals(0.85f, (Float) hud.get("opacity"), 1e-6f);
    }

    @Test
    public void aBackupFromAnotherFormatIsRefused() {
        try {
            ProfileBackup.decode("{\"format\":99,\"files\":{}}");
            fail("a future format was accepted");
        } catch (JSONException expected) {
            // The import must refuse rather than half-restore something it cannot read.
        }
    }

    @Test
    public void unknownFilesAreIgnoredRatherThanCrashing() throws JSONException {
        String json = ProfileBackup.encode(sampleProfile())
                .replace("voxelstrike_hud", "voxelstrike_from_the_future");
        Map<String, Map<String, Object>> back = ProfileBackup.decode(json);
        assertTrue(back.containsKey("voxelstrike_profile"));
        assertTrue(!back.containsKey("voxelstrike_from_the_future"));
    }
}
