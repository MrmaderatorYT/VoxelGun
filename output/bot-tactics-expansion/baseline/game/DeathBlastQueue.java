package com.ccs.voxelgun.game;

/** Fixed ring prevents recursive death cascades and allocations during combat. */
final class DeathBlastQueue {
    private static final int CAPACITY = 128; // max 100 living combatants in the current game
    private final Combatant[] owners = new Combatant[CAPACITY];
    private final float[] xs = new float[CAPACITY], ys = new float[CAPACITY], zs = new float[CAPACITY];
    private int read, write, size;
    Combatant owner;
    float x, y, z;
    void add(Combatant c) {
        if (size == CAPACITY) return; // fail closed if a future mode exceeds the bounded budget
        owners[write] = c;
        xs[write] = c.position.x; ys[write] = c.position.y + c.height() * 0.5f; zs[write] = c.position.z;
        write = (write + 1) % CAPACITY;
        size++;
    }
    boolean poll() {
        if (size == 0) return false;
        owner = owners[read]; x = xs[read]; y = ys[read]; z = zs[read];
        owners[read] = null;
        read = (read + 1) % CAPACITY;
        size--;
        return true;
    }
}
