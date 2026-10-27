package com.ccs.voxelstrike.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Export and import of everything the player has earned or arranged.
 *
 * <p>Career progress lives in a handful of {@code SharedPreferences} files, which one uninstall —
 * or one stray {@code connectedAndroidTest} — wipes without warning. This writes them to a file
 * the player keeps, and reads them back, so the only copy is never the one on the device.
 */
public final class ProfileBackup {
    /** Bumped if the on-disk shape ever changes; an import refuses anything it does not know. */
    private static final int FORMAT = 1;
    private static final String KEY_FORMAT = "format";
    private static final String KEY_FILES = "files";

    /** The preference files that make up a player: progress, settings, layout and bindings. */
    private static final String[] FILES = {
        "voxelstrike_profile", "voxelstrike_settings", "voxelstrike_hud", "voxelstrike_keys"
    };

    private ProfileBackup() {}

    /** Serialises every preference file into {@code out}. Returns how many values were written. */
    public static int export(Context context, OutputStream out) throws IOException, JSONException {
        java.util.LinkedHashMap<String, Map<String, ?>> files = new java.util.LinkedHashMap<>();
        for (String name : FILES) {
            files.put(name, context.getSharedPreferences(name, Context.MODE_PRIVATE).getAll());
        }
        String json = encode(files);
        out.write(json.getBytes(StandardCharsets.UTF_8));
        out.flush();
        int values = 0;
        for (Map<String, ?> entries : files.values()) {
            values += entries.size();
        }
        return values;
    }

    /**
     * The pure half of the export: preference maps in, JSON text out.
     *
     * <p>Types are written alongside the values because {@code SharedPreferences} is typed and
     * reading a float back as a double throws at runtime. Floats go through their decimal string
     * so no precision is lost in JSON's double.
     */
    public static String encode(Map<String, Map<String, ?>> files) throws JSONException {
        JSONObject root = new JSONObject();
        root.put(KEY_FORMAT, FORMAT);
        JSONObject encoded = new JSONObject();
        for (Map.Entry<String, Map<String, ?>> file : files.entrySet()) {
            JSONObject entries = new JSONObject();
            for (Map.Entry<String, ?> entry : file.getValue().entrySet()) {
                Object value = entry.getValue();
                JSONObject typed = new JSONObject();
                if (value instanceof Boolean) {
                    typed.put("t", "b").put("v", value);
                } else if (value instanceof Integer) {
                    typed.put("t", "i").put("v", value);
                } else if (value instanceof Long) {
                    typed.put("t", "l").put("v", value);
                } else if (value instanceof Float) {
                    typed.put("t", "f").put("v", Float.toString((Float) value));
                } else if (value instanceof String) {
                    typed.put("t", "s").put("v", value);
                } else {
                    continue;  // string sets are not used by the game
                }
                entries.put(entry.getKey(), typed);
            }
            encoded.put(file.getKey(), entries);
        }
        root.put(KEY_FILES, encoded);
        return root.toString(2);
    }

    /** The pure half of the import: JSON text in, typed values out, file by file. */
    public static Map<String, Map<String, Object>> decode(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (root.optInt(KEY_FORMAT) != FORMAT) {
            throw new JSONException("unsupported backup format: " + root.optInt(KEY_FORMAT));
        }
        JSONObject files = root.getJSONObject(KEY_FILES);
        java.util.LinkedHashMap<String, Map<String, Object>> result = new java.util.LinkedHashMap<>();
        for (String name : FILES) {
            JSONObject entries = files.optJSONObject(name);
            if (entries == null) {
                continue;
            }
            java.util.LinkedHashMap<String, Object> values = new java.util.LinkedHashMap<>();
            java.util.Iterator<String> keys = entries.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject typed = entries.getJSONObject(key);
                switch (typed.getString("t")) {
                    case "b": values.put(key, typed.getBoolean("v")); break;
                    case "i": values.put(key, typed.getInt("v")); break;
                    case "l": values.put(key, typed.getLong("v")); break;
                    case "f": values.put(key, Float.parseFloat(typed.getString("v"))); break;
                    case "s": values.put(key, typed.getString("v")); break;
                    default: break;
                }
            }
            result.put(name, values);
        }
        return result;
    }

    /**
     * Replaces the stored preferences with the contents of {@code in}.
     *
     * <p>Each file is cleared before it is written, so an import restores a state rather than
     * merging two. Returns how many values were read.
     */
    public static int restore(Context context, InputStream in) throws IOException, JSONException {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line);
            }
        }
        Map<String, Map<String, Object>> files = decode(text.toString());
        int values = 0;
        for (Map.Entry<String, Map<String, Object>> file : files.entrySet()) {
            SharedPreferences.Editor editor =
                    context.getSharedPreferences(file.getKey(), Context.MODE_PRIVATE).edit();
            editor.clear();
            for (Map.Entry<String, Object> entry : file.getValue().entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Boolean) {
                    editor.putBoolean(entry.getKey(), (Boolean) value);
                } else if (value instanceof Integer) {
                    editor.putInt(entry.getKey(), (Integer) value);
                } else if (value instanceof Long) {
                    editor.putLong(entry.getKey(), (Long) value);
                } else if (value instanceof Float) {
                    editor.putFloat(entry.getKey(), (Float) value);
                } else if (value instanceof String) {
                    editor.putString(entry.getKey(), (String) value);
                }
                values++;
            }
            editor.commit();  // the caller restarts the screens straight after, so commit, not apply
        }
        return values;
    }

    /** Default file name offered to the system file picker. */
    public static String suggestedFileName() {
        return "voxelstrike-profile-"
                + new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                        .format(new java.util.Date()) + ".json";
    }
}
