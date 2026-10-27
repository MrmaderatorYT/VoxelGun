package com.ccs.voxelstrike.math;

/** Mutable vector used in allocation-free hot paths. */
public final class Vec3 {
    public float x;
    public float y;
    public float z;

    public Vec3() {}

    public Vec3(float x, float y, float z) {
        set(x, y, z);
    }

    public Vec3 set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public Vec3 set(Vec3 other) {
        return set(other.x, other.y, other.z);
    }

    public Vec3 add(float dx, float dy, float dz) {
        x += dx;
        y += dy;
        z += dz;
        return this;
    }

    public Vec3 addScaled(Vec3 other, float scale) {
        x += other.x * scale;
        y += other.y * scale;
        z += other.z * scale;
        return this;
    }

    public Vec3 scale(float scale) {
        x *= scale;
        y *= scale;
        z *= scale;
        return this;
    }

    public float length() {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    public float lengthSquared() {
        return x * x + y * y + z * z;
    }

    public Vec3 normalize() {
        float length = length();
        if (length > 1e-6f) {
            scale(1f / length);
        }
        return this;
    }

    public float dot(Vec3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public static float distance(Vec3 a, Vec3 b) {
        return (float) Math.sqrt(distanceSquared(a, b));
    }

    public static float distanceSquared(Vec3 a, Vec3 b) {
        float dx = a.x - b.x;
        float dy = a.y - b.y;
        float dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public static float horizontalDistance(Vec3 a, Vec3 b) {
        float dx = a.x - b.x;
        float dz = a.z - b.z;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }
}
