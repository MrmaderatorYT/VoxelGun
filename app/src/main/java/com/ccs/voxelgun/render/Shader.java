package com.ccs.voxelgun.render;

import android.opengl.GLES30;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/** Compiled program plus a cache of uniform locations. */
public final class Shader {
    private static final String TAG = "VoxelGun";

    private final int program;
    private final Map<String, Integer> uniforms = new HashMap<>();
    private final String name;

    public Shader(String name, String vertexSource, String fragmentSource) {
        this.name = name;
        int vertex = compile(GLES30.GL_VERTEX_SHADER, vertexSource, name + ".vert");
        int fragment = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSource, name + ".frag");
        program = GLES30.glCreateProgram();
        GLES30.glAttachShader(program, vertex);
        GLES30.glAttachShader(program, fragment);
        GLES30.glLinkProgram(program);

        int[] status = new int[1];
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES30.glGetProgramInfoLog(program);
            GLES30.glDeleteProgram(program);
            throw new IllegalStateException("Link failed for " + name + ": " + log);
        }
        GLES30.glDetachShader(program, vertex);
        GLES30.glDetachShader(program, fragment);
        GLES30.glDeleteShader(vertex);
        GLES30.glDeleteShader(fragment);
    }

    private static int compile(int type, String source, String label) {
        int shader = GLES30.glCreateShader(type);
        GLES30.glShaderSource(shader, source);
        GLES30.glCompileShader(shader);
        int[] status = new int[1];
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES30.glGetShaderInfoLog(shader);
            GLES30.glDeleteShader(shader);
            Log.e(TAG, "Shader compile failed: " + label + "\n" + log);
            throw new IllegalStateException("Compile failed for " + label + ": " + log);
        }
        return shader;
    }

    public void use() {
        GLES30.glUseProgram(program);
    }

    public int uniform(String uniformName) {
        Integer cached = uniforms.get(uniformName);
        if (cached != null) {
            return cached;
        }
        int location = GLES30.glGetUniformLocation(program, uniformName);
        uniforms.put(uniformName, location);
        return location;
    }

    public void set(String uniformName, int value) {
        GLES30.glUniform1i(uniform(uniformName), value);
    }

    public void set(String uniformName, float value) {
        GLES30.glUniform1f(uniform(uniformName), value);
    }

    public void set(String uniformName, float x, float y) {
        GLES30.glUniform2f(uniform(uniformName), x, y);
    }

    public void set(String uniformName, float x, float y, float z) {
        GLES30.glUniform3f(uniform(uniformName), x, y, z);
    }

    public void set(String uniformName, float x, float y, float z, float w) {
        GLES30.glUniform4f(uniform(uniformName), x, y, z, w);
    }

    public void setMatrix(String uniformName, float[] matrix) {
        GLES30.glUniformMatrix4fv(uniform(uniformName), 1, false, matrix, 0);
    }

    public void bindTexture(String uniformName, int unit, int target, int texture) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit);
        GLES30.glBindTexture(target, texture);
        set(uniformName, unit);
    }

    public String name() {
        return name;
    }

    public void delete() {
        GLES30.glDeleteProgram(program);
    }
}
