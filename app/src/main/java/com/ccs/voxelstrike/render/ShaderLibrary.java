package com.ccs.voxelstrike.render;

import android.content.res.AssetManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads GLSL from assets with a tiny {@code #include} preprocessor and per-program defines, so
 * the ray tracing core can be shared between the lighting, reflection and probe shaders.
 */
public final class ShaderLibrary {
    private static final String ROOT = "shaders/";
    private static final String VERSION = "#version 300 es\n";

    private final AssetManager assets;
    private final Map<String, String> sources = new HashMap<>();
    private final List<Shader> created = new ArrayList<>();

    public ShaderLibrary(AssetManager assets) {
        this.assets = assets;
    }

    public Shader create(String name, String vertexFile, String fragmentFile, String... defines) {
        String header = VERSION + buildDefines(defines);
        Shader shader = new Shader(name, header + resolve(vertexFile),
                header + resolve(fragmentFile));
        created.add(shader);
        return shader;
    }

    private static String buildDefines(String[] defines) {
        if (defines == null || defines.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String define : defines) {
            builder.append("#define ").append(define).append('\n');
        }
        return builder.toString();
    }

    /** Reads a file and inlines every {@code #include "name"} directive it contains. */
    private String resolve(String file) {
        StringBuilder out = new StringBuilder();
        for (String line : read(file).split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#include")) {
                int start = trimmed.indexOf('"');
                int end = trimmed.lastIndexOf('"');
                if (start >= 0 && end > start) {
                    out.append(resolve(trimmed.substring(start + 1, end))).append('\n');
                    continue;
                }
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    private String read(String file) {
        String cached = sources.get(file);
        if (cached != null) {
            return cached;
        }
        StringBuilder builder = new StringBuilder();
        try (InputStream stream = assets.open(ROOT + file);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        } catch (IOException error) {
            throw new IllegalStateException("Missing shader asset: " + file, error);
        }
        String source = builder.toString();
        sources.put(file, source);
        return source;
    }

    public void release() {
        for (Shader shader : created) {
            shader.delete();
        }
        created.clear();
    }
}
