package com.ccs.voxelgun.map;

/** Shared, versioned course coordinates for geometry, timing and tests. */
public final class CourseLayout {
    public static final int VERSION = 1;
    private static final int[] X = {14, 30, 48, 66, 84, 104, 114};
    private static final int[] Z = {32, 32, 48, 48, 32, 32, 32};
    public static final int JUMP_GATE = 2;
    public static final int SLIDE_GATE = 4;
    private CourseLayout() {}
    public static int count() { return X.length; }
    public static int x(int gate) { return X[gate]; }
    public static int z(int gate) { return Z[gate]; }
}
