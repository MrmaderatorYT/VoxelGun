package com.ccs.voxelgun.game;

/**
 * Allocation-free A* over the multi-level {@link NavGrid}. One instance is shared by all bots;
 * a per-search stamp avoids clearing the score arrays between runs.
 */
public final class PathFinder {
    private static final float DIAGONAL = 1.41421356f;
    /**
     * Weighted A*. Slightly overestimating the remaining distance keeps the open set small on
     * wide outdoor maps, at the cost of paths a few percent longer than optimal.
     */
    private static final float HEURISTIC_WEIGHT = 1.25f;

    private final NavGrid grid;
    private final float[] gScore;
    private final float[] fScore;
    private final int[] cameFrom;
    private final int[] stamp;
    private final boolean[] closed;
    private final int[] heap;
    private final int[] neighbours = new int[8 * NavGrid.MAX_LEVELS];
    private int heapSize;
    private int currentStamp;
    private final int maxExpansions;

    public PathFinder(NavGrid grid) {
        this.grid = grid;
        int nodes = grid.nodeCapacity();
        gScore = new float[nodes];
        fScore = new float[nodes];
        cameFrom = new int[nodes];
        stamp = new int[nodes];
        closed = new boolean[nodes];
        heap = new int[nodes + 1];
        // A 128x128 map needs nothing like the whole node set: a search that runs away is one
        // that cannot reach its goal, and expanding 24k nodes to discover that cost 80ms.
        maxExpansions = Math.min(nodes, 6000);
    }

    /**
     * Searches between two navigation nodes.
     *
     * @return true when {@code out} received a path, start first and goal last
     */
    /**
     * Searches left in this simulation step.
     *
     * <p>Bots repath independently, so several can land on the same tick; one bot looking for
     * cover used to fire two dozen searches by itself. The budget spreads that cost over frames
     * instead of spiking one: a refused search simply means the bot steers directly this tick and
     * tries again on the next, which at a 0.85s repath interval is invisible.
     */
    private int budget = Integer.MAX_VALUE;

    /**
     * Called once per simulation step to refill the search budget.
     *
     * <p>Unbudgeted until a caller opts in, so tools and tests that ask the grid direct questions
     * — can these two spawns reach each other? — are never answered "no" because a frame had
     * already spent its allowance.
     */
    public void beginStep(int searches) {
        budget = searches;
    }

    public boolean canSearch() { return budget > 0; }

    public boolean find(int startNode, int goalNode, Path out) {
        return find(startNode, goalNode, out, null);
    }

    public boolean find(int startNode, int goalNode, Path out, BotRouteMemory memory) {
        if (budget <= 0) {
            // Deferred, not failed: the caller may still be following this usable route.
            return false;
        }
        budget--;
        out.clear();
        if (!grid.valid(startNode) || !grid.valid(goalNode)) {
            return false;
        }
        if (startNode == goalNode) {
            out.push(startNode);
            return true;
        }

        currentStamp++;
        heapSize = 0;
        touch(startNode);
        gScore[startNode] = 0f;
        fScore[startNode] = heuristic(startNode, goalNode) * HEURISTIC_WEIGHT;
        push(startNode);

        int expansions = 0;
        while (heapSize > 0 && expansions++ < maxExpansions) {
            int current = pop();
            if (current == goalNode) {
                return reconstruct(startNode, goalNode, out);
            }
            closed[current] = true;

            int count = grid.neighbours(current, neighbours);
            for (int i = 0; i < count; i++) {
                int neighbour = neighbours[i];
                touch(neighbour);
                if (closed[neighbour]) {
                    continue;
                }
                int dx = Math.abs(grid.cellX(neighbour) - grid.cellX(current));
                int dz = Math.abs(grid.cellZ(neighbour) - grid.cellZ(current));
                float step = (dx != 0 && dz != 0) ? DIAGONAL : 1f;
                int rise = grid.nodeY(neighbour) - grid.nodeY(current);
                if (rise > 0) {
                    step += 0.6f;  // climbing is slower than walking
                } else if (rise < -1) {
                    step += 0.25f;
                }
                float tentative = gScore[current] + step
                        + (memory == null || neighbour == goalNode ? 0f : memory.cost(neighbour));
                if (tentative >= gScore[neighbour]) {
                    continue;
                }
                cameFrom[neighbour] = current;
                gScore[neighbour] = tentative;
                fScore[neighbour] = tentative + heuristic(neighbour, goalNode) * HEURISTIC_WEIGHT;
                push(neighbour);
            }
        }
        return false;
    }

    private boolean reconstruct(int startNode, int goalNode, Path out) {
        int cursor = goalNode;
        int guard = 0;
        while (cursor != startNode && guard++ < 8192) {
            if (out.length == out.nodes.length || cursor < 0) { out.clear(); return false; }
            out.push(cursor);
            cursor = cameFrom[cursor];
        }
        if (cursor != startNode || out.length == out.nodes.length) { out.clear(); return false; }
        out.push(startNode);
        out.reverse();
        return out.length() > 0;
    }

    private void touch(int node) {
        if (stamp[node] != currentStamp) {
            stamp[node] = currentStamp;
            gScore[node] = Float.MAX_VALUE;
            fScore[node] = Float.MAX_VALUE;
            cameFrom[node] = -1;
            closed[node] = false;
        }
    }

    private float heuristic(int from, int to) {
        int dx = Math.abs(grid.cellX(from) - grid.cellX(to));
        int dz = Math.abs(grid.cellZ(from) - grid.cellZ(to));
        int dy = Math.abs(grid.nodeY(from) - grid.nodeY(to));
        int min = Math.min(dx, dz);
        return (dx + dz - 2 * min) + DIAGONAL * min + dy * 0.5f;
    }

    private void push(int node) {
        int index = ++heapSize;
        heap[index] = node;
        while (index > 1) {
            int parent = index >> 1;
            if (fScore[heap[parent]] <= fScore[heap[index]]) {
                break;
            }
            int swap = heap[parent];
            heap[parent] = heap[index];
            heap[index] = swap;
            index = parent;
        }
    }

    private int pop() {
        int top = heap[1];
        heap[1] = heap[heapSize--];
        int index = 1;
        while (true) {
            int left = index << 1;
            int right = left + 1;
            int best = index;
            if (left <= heapSize && fScore[heap[left]] < fScore[heap[best]]) {
                best = left;
            }
            if (right <= heapSize && fScore[heap[right]] < fScore[heap[best]]) {
                best = right;
            }
            if (best == index) {
                break;
            }
            int swap = heap[best];
            heap[best] = heap[index];
            heap[index] = swap;
            index = best;
        }
        return top;
    }

    /** Fixed-capacity list of navigation nodes forming a route. */
    public static final class Path {
        private final int[] nodes;
        private int length;
        private int cursor;

        public Path(int capacity) {
            nodes = new int[capacity];
        }

        public void clear() {
            length = 0;
            cursor = 0;
        }

        void push(int node) {
            if (length < nodes.length) {
                nodes[length++] = node;
            }
        }

        void reverse() {
            for (int i = 0, j = length - 1; i < j; i++, j--) {
                int swap = nodes[i];
                nodes[i] = nodes[j];
                nodes[j] = swap;
            }
        }

        public int length() {
            return length;
        }

        public int node(int index) {
            return nodes[index];
        }

        public boolean finished() {
            return cursor >= length;
        }

        public int currentNode() {
            return finished() ? -1 : nodes[cursor];
        }

        public void advance() {
            cursor++;
        }
    }
}
