package com.ccs.voxelstrike.math;

import android.opengl.Matrix;

/** Column-major 4x4 helpers operating on caller-owned float arrays. */
public final class Mat4 {
    private Mat4() {}

    public static float[] create() {
        float[] m = new float[16];
        Matrix.setIdentityM(m, 0);
        return m;
    }

    public static void identity(float[] out) {
        Matrix.setIdentityM(out, 0);
    }

    public static void perspective(float[] out, float fovDegrees, float aspect, float near,
                                   float far) {
        Matrix.perspectiveM(out, 0, fovDegrees, aspect, near, far);
    }

    public static void lookAlong(float[] out, float x, float y, float z, float yaw, float pitch) {
        float cp = (float) Math.cos(pitch);
        float sp = (float) Math.sin(pitch);
        float sy = (float) Math.sin(yaw);
        float cy = (float) Math.cos(yaw);
        Matrix.setLookAtM(out, 0, x, y, z, x + sy * cp, y + sp, z - cy * cp, 0f, 1f, 0f);
    }

    public static void multiply(float[] out, float[] a, float[] b) {
        Matrix.multiplyMM(out, 0, a, 0, b, 0);
    }

    public static boolean invert(float[] out, float[] source) {
        return Matrix.invertM(out, 0, source, 0);
    }

    /**
     * Places a model that faces local -Z.
     *
     * <p>Game yaw is measured so that forward is {@code (sin yaw, 0, -cos yaw)}, while a positive
     * rotation about Y sends local -Z to {@code (-sin a, 0, -cos a)}. The rotation therefore has
     * to be applied with the opposite sign, which {@link #yawDegrees} does.
     */
    public static void model(float[] out, float x, float y, float z, float yawRadians, float scale) {
        Matrix.setIdentityM(out, 0);
        Matrix.translateM(out, 0, x, y, z);
        Matrix.rotateM(out, 0, yawDegrees(yawRadians), 0f, 1f, 0f);
        Matrix.scaleM(out, 0, scale, scale, scale);
    }

    /** Converts a game yaw into the rotation angle a -Z facing model needs. */
    public static float yawDegrees(float yawRadians) {
        return (float) Math.toDegrees(-yawRadians);
    }

    public static void translate(float[] out, float x, float y, float z) {
        Matrix.translateM(out, 0, x, y, z);
    }

    public static void rotate(float[] out, float degrees, float x, float y, float z) {
        Matrix.rotateM(out, 0, degrees, x, y, z);
    }

    public static void scale(float[] out, float x, float y, float z) {
        Matrix.scaleM(out, 0, x, y, z);
    }

    public static void copy(float[] out, float[] source) {
        System.arraycopy(source, 0, out, 0, 16);
    }

    public static void ortho(float[] out, float left, float right, float bottom, float top,
                             float near, float far) {
        Matrix.orthoM(out, 0, left, right, bottom, top, near, far);
    }
}
