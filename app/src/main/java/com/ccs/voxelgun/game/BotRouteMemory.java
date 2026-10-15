package com.ccs.voxelgun.game;

import java.util.Arrays;

/** Small personal, expiring route costs. A failed route never removes a shared navigation node. */
public final class BotRouteMemory {
    private static final int CAPACITY = 6;
    private final int[] nodes = new int[CAPACITY];
    private final float[] remaining = new float[CAPACITY];
    private final int[] goals = new int[CAPACITY];
    private final int[] failures = new int[CAPACITY];
    private final float[] goalRemaining = new float[CAPACITY];

    public BotRouteMemory() { reset(); }

    public void reset() {
        Arrays.fill(nodes, -1); Arrays.fill(goals, -1);
        Arrays.fill(remaining, 0f); Arrays.fill(goalRemaining, 0f); Arrays.fill(failures, 0);
    }

    public void update(float dt) {
        for (int i = 0; i < CAPACITY; i++) {
            remaining[i] = Math.max(0f, remaining[i] - dt);
            goalRemaining[i] = Math.max(0f, goalRemaining[i] - dt);
        }
    }

    public void avoidNode(int node) {
        if (node < 0) return;
        int slot = oldest(nodes, remaining, node);
        nodes[slot] = node; remaining[slot] = 12f;
    }

    public float cost(int node) {
        for (int i = 0; i < CAPACITY; i++)
            if (nodes[i] == node && remaining[i] > 0f) return 18f;
        return 0f;
    }

    public void failedGoal(int goal) {
        if (goal < 0) return;
        int slot = oldest(goals, goalRemaining, goal);
        failures[slot] = goals[slot] == goal && goalRemaining[slot] > 0f
                ? Math.min(4, failures[slot] + 1) : 1;
        goals[slot] = goal; goalRemaining[slot] = 16f;
    }

    public int failures(int goal) {
        for (int i = 0; i < CAPACITY; i++)
            if (goals[i] == goal && goalRemaining[i] > 0f) return failures[i];
        return 0;
    }

    public void reachedGoal(int goal) {
        for (int i = 0; i < CAPACITY; i++) if (goals[i] == goal) goalRemaining[i] = 0f;
    }

    private static int oldest(int[] ids, float[] ages, int id) {
        int oldest = 0;
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] == id) return i;
            if (ages[i] < ages[oldest]) oldest = i;
        }
        return oldest;
    }
}
