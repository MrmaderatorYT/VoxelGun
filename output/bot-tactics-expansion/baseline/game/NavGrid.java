package com.ccs.voxelgun.game;

import com.ccs.voxelgun.voxel.Materials;
import com.ccs.voxelgun.voxel.VoxelWorld;

import java.util.Random;

/**
 * Multi-level walkable surface graph.
 *
 * <p>A flat height map is not enough here: the maps have roofs, balconies, catwalks and tunnels,
 * and a top-surface-only grid would happily route bots across a warehouse roof or refuse to walk
 * through a door that has a balcony above it. Instead every column stores each standable level —
 * a solid block with two blocks of clearance above it — and nodes connect when the height step
 * is something a body can actually take.
 */
public final class NavGrid {
    /** Standable levels tracked per column, counted from the bottom of the map. */
    public static final int MAX_LEVELS = 4;
    private static final int CLEARANCE = 2;
    private static final int MAX_STEP_UP = 1;
    private static final int MAX_DROP = 4;

    private final int width;
    private final int depth;
    private final byte[] levelCount;
    private final short[] levelY;
    private final int[] openNodes;
    private final int[] openIndex;
    private int openCount;

    /** Refresh changed columns without a full-map rebuild. Stable column ids invalidate paths. */
    public void refreshArea(VoxelWorld world, int minX, int minZ, int maxX, int maxZ) {
        for (int z = Math.max(0, minZ); z <= Math.min(depth - 1, maxZ); z++) {
            for (int x = Math.max(0, minX); x <= Math.min(width - 1, maxX); x++) {
                int column = x + width * z;
                for (int level = 0; level < levelCount[column]; level++) {
                    int old = column * MAX_LEVELS + level;
                    int slot = openIndex[old];
                    int last = openNodes[--openCount];
                    openNodes[slot] = last; openIndex[last] = slot; openIndex[old] = -1;
                }
                int found = 0;
                for (int y = 1; y < world.sizeY() - CLEARANCE && found < MAX_LEVELS; y++) {
                    if (!world.solid(x, y - 1, z) || world.get(x, y - 1, z) == Materials.LAVA) continue;
                    if (world.solid(x, y, z) || world.solid(x, y + 1, z)) continue;
                    levelY[column * MAX_LEVELS + found++] = (short) y;
                }
                levelCount[column] = (byte) found;
                for (int level = 0; level < found; level++) {
                    int n = column * MAX_LEVELS + level;
                    openIndex[n] = openCount; openNodes[openCount++] = n;
                }
            }
        }
    }

    public NavGrid(VoxelWorld world) {
        this.width = world.sizeX();
        this.depth = world.sizeZ();
        this.levelCount = new byte[width * depth];
        this.levelY = new short[width * depth * MAX_LEVELS];
        this.openNodes = new int[width * depth * MAX_LEVELS];
        this.openIndex = new int[openNodes.length];
        java.util.Arrays.fill(openIndex, -1);
        rebuild(world);
    }

    public int width() {
        return width;
    }

    public int depth() {
        return depth;
    }

    public int nodeCapacity() {
        return width * depth * MAX_LEVELS;
    }

    public int openCount() {
        return openCount;
    }

    private void rebuild(VoxelWorld world) {
        openCount = 0;
        int ceiling = world.sizeY() - CLEARANCE - 1;
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                int column = x + width * z;
                int found = 0;
                for (int y = 1; y <= ceiling && found < MAX_LEVELS; y++) {
                    if (!world.solid(x, y - 1, z)) {
                        continue;
                    }
                    if (world.get(x, y - 1, z) == Materials.LAVA) {
                        continue;  // never route bots into the hazard
                    }
                    boolean clear = true;
                    for (int head = 0; head < CLEARANCE; head++) {
                        if (world.solid(x, y + head, z)) {
                            clear = false;
                            break;
                        }
                    }
                    if (!clear) {
                        continue;
                    }
                    levelY[column * MAX_LEVELS + found] = (short) y;
                    int n = column * MAX_LEVELS + found;
                    openIndex[n] = openCount;
                    openNodes[openCount++] = n;
                    found++;
                }
                levelCount[column] = (byte) found;
            }
        }
    }

    public boolean inside(int x, int z) {
        return x >= 0 && z >= 0 && x < width && z < depth;
    }

    public int levelsAt(int x, int z) {
        return inside(x, z) ? levelCount[x + width * z] : 0;
    }

    public int node(int x, int z, int level) {
        return (x + width * z) * MAX_LEVELS + level;
    }

    public int cellX(int node) {
        return (node / MAX_LEVELS) % width;
    }

    public int cellZ(int node) {
        return (node / MAX_LEVELS) / width;
    }

    /** Feet height of the node in world voxels. */
    public int nodeY(int node) {
        return levelY[node];
    }

    public boolean valid(int node) {
        if (node < 0 || node >= openNodes.length) {
            return false;
        }
        int column = node / MAX_LEVELS;
        return node % MAX_LEVELS < levelCount[column];
    }

    /** True when a body can walk straight from one node to an adjacent one. */
    public boolean connected(int fromNode, int toNode) {
        if (!valid(fromNode) || !valid(toNode)) {
            return false;
        }
        int fromX = cellX(fromNode);
        int fromZ = cellZ(fromNode);
        int toX = cellX(toNode);
        int toZ = cellZ(toNode);
        int dx = toX - fromX;
        int dz = toZ - fromZ;
        if (Math.abs(dx) > 1 || Math.abs(dz) > 1 || (dx == 0 && dz == 0)) {
            return false;
        }
        int rise = nodeY(toNode) - nodeY(fromNode);
        if (rise > MAX_STEP_UP || rise < -MAX_DROP) {
            return false;
        }
        if (dx != 0 && dz != 0) {
            // Diagonals may not cut a corner: both orthogonal columns need a level in between.
            return hasLevelNear(fromX, toZ, nodeY(fromNode)) && hasLevelNear(toX, fromZ,
                    nodeY(fromNode));
        }
        return true;
    }

    private boolean hasLevelNear(int x, int z, int y) {
        int levels = levelsAt(x, z);
        for (int level = 0; level < levels; level++) {
            if (Math.abs(levelY[node(x, z, level)] - y) <= MAX_STEP_UP) {
                return true;
            }
        }
        return false;
    }

    /** Nodes adjacent to {@code node}, written into {@code out}; returns how many. */
    public int neighbours(int node, int[] out) {
        int count = 0;
        int x = cellX(node);
        int z = cellZ(node);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int nx = x + dx;
                int nz = z + dz;
                if (!inside(nx, nz)) {
                    continue;
                }
                int levels = levelsAt(nx, nz);
                for (int level = 0; level < levels; level++) {
                    int candidate = node(nx, nz, level);
                    if (connected(node, candidate)) {
                        out[count++] = candidate;
                    }
                }
            }
        }
        return count;
    }

    /** The standable node closest to a world position, searched in expanding rings. */
    public int nearestNode(float worldX, float worldY, float worldZ) {
        int cx = VoxelWorld.floor(worldX);
        int cz = VoxelWorld.floor(worldZ);
        int best = -1;
        float bestScore = Float.MAX_VALUE;
        for (int radius = 0; radius <= 10; radius++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    int x = cx + dx;
                    int z = cz + dz;
                    int levels = levelsAt(x, z);
                    for (int level = 0; level < levels; level++) {
                        int candidate = node(x, z, level);
                        float dy = levelY[candidate] - worldY;
                        // Prefer the level the body is actually standing on.
                        float score = dx * dx + dz * dz + dy * dy * 4f;
                        if (score < bestScore) {
                            bestScore = score;
                            best = candidate;
                        }
                    }
                }
            }
            if (best >= 0 && radius >= 1) {
                return best;
            }
        }
        return best;
    }

    public int randomNode(Random random) {
        return openCount == 0 ? -1 : openNodes[random.nextInt(openCount)];
    }

    /** A random node at least {@code minDistance} away in the horizontal plane. */
    public int randomNodeAwayFrom(Random random, float x, float z, float minDistance) {
        float minSquared = minDistance * minDistance;
        int fallback = -1;
        for (int attempt = 0; attempt < 32; attempt++) {
            int node = randomNode(random);
            if (node < 0) {
                return -1;
            }
            fallback = node;
            float dx = cellX(node) + 0.5f - x;
            float dz = cellZ(node) + 0.5f - z;
            if (dx * dx + dz * dz >= minSquared) {
                return node;
            }
        }
        return fallback;
    }
}
