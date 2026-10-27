package com.ccs.voxelstrike.math;

/** Six normalised clip planes extracted from a view-projection matrix without allocating. */
public final class Frustum {
    private final float[] planes = new float[24];

    public void update(float[] m) {
        plane(0, m[3] + m[0], m[7] + m[4], m[11] + m[8], m[15] + m[12]);
        plane(1, m[3] - m[0], m[7] - m[4], m[11] - m[8], m[15] - m[12]);
        plane(2, m[3] + m[1], m[7] + m[5], m[11] + m[9], m[15] + m[13]);
        plane(3, m[3] - m[1], m[7] - m[5], m[11] - m[9], m[15] - m[13]);
        plane(4, m[3] + m[2], m[7] + m[6], m[11] + m[10], m[15] + m[14]);
        plane(5, m[3] - m[2], m[7] - m[6], m[11] - m[10], m[15] - m[14]);
    }

    private void plane(int index, float a, float b, float c, float d) {
        float length = (float) Math.sqrt(a * a + b * b + c * c);
        if (length < 1e-8f) {
            length = 1f;
        }
        int offset = index * 4;
        planes[offset] = a / length;
        planes[offset + 1] = b / length;
        planes[offset + 2] = c / length;
        planes[offset + 3] = d / length;
    }

    public boolean containsBox(float minX, float minY, float minZ, float maxX, float maxY,
                               float maxZ) {
        for (int i = 0; i < 24; i += 4) {
            float x = planes[i] >= 0 ? maxX : minX;
            float y = planes[i + 1] >= 0 ? maxY : minY;
            float z = planes[i + 2] >= 0 ? maxZ : minZ;
            if (planes[i] * x + planes[i + 1] * y + planes[i + 2] * z + planes[i + 3] < 0) {
                return false;
            }
        }
        return true;
    }

    public boolean containsSphere(float x, float y, float z, float radius) {
        for (int i = 0; i < 24; i += 4) {
            if (planes[i] * x + planes[i + 1] * y + planes[i + 2] * z + planes[i + 3] < -radius) {
                return false;
            }
        }
        return true;
    }
}
