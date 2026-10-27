package com.ccs.voxelstrike.voxel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The map's emissive blocks, gathered into a light list the ray tracer can sample directly.
 *
 * <p>Neon strips and lamp panels are hundreds of individual voxels, so they are clustered into
 * cells first: one light per cell, positioned at the centre of mass of the blocks inside it and
 * brightened by how many there were. Only the strongest {@link #MAX_LIGHTS} survive, which keeps
 * the lookup texture small enough to sample per bounce.
 */
public final class EmissiveLights {
    public static final int MAX_LIGHTS = 384;
    /** Floats per light: position + radius, then colour + padding. */
    public static final int FLOATS_PER_LIGHT = 8;

    private static final int CELL = 4;
    private static final float GAIN = 0.6f;

    private final float[] data;
    private final int count;

    private EmissiveLights(float[] data, int count) {
        this.data = data;
        this.count = count;
    }

    public float[] data() {
        return data;
    }

    public int count() {
        return count;
    }

    public static EmissiveLights build(VoxelWorld world) {
        Map<Integer, Cluster> clusters = new HashMap<>();
        int cellsX = (world.sizeX() + CELL - 1) / CELL;
        int cellsY = (world.sizeY() + CELL - 1) / CELL;

        for (int z = 0; z < world.sizeZ(); z++) {
            for (int y = 0; y < world.sizeY(); y++) {
                for (int x = 0; x < world.sizeX(); x++) {
                    int material = world.get(x, y, z) & 0xFF;
                    if (material == 0 || Materials.emissive(material) <= 0f) {
                        continue;
                    }
                    // Only blocks with an exposed face actually light the room.
                    if (!hasOpenFace(world, x, y, z)) {
                        continue;
                    }
                    int key = (x / CELL) + cellsX * ((y / CELL) + cellsY * (z / CELL));
                    Cluster cluster = clusters.get(key);
                    if (cluster == null) {
                        cluster = new Cluster();
                        clusters.put(key, cluster);
                    }
                    cluster.add(x + 0.5f, y + 0.5f, z + 0.5f, material);
                }
            }
        }

        List<Cluster> all = new ArrayList<>(clusters.values());
        Collections.sort(all, (a, b) -> Float.compare(b.power(), a.power()));
        int kept = Math.min(all.size(), MAX_LIGHTS);

        float[] data = new float[MAX_LIGHTS * FLOATS_PER_LIGHT];
        for (int i = 0; i < kept; i++) {
            Cluster cluster = all.get(i);
            int base = i * FLOATS_PER_LIGHT;
            data[base] = cluster.sumX / cluster.count;
            data[base + 1] = cluster.sumY / cluster.count;
            data[base + 2] = cluster.sumZ / cluster.count;
            // A denser cluster behaves like a physically larger emitter.
            data[base + 3] = 0.9f + 0.35f * (float) Math.cbrt(cluster.count);

            float boost = (float) Math.sqrt(cluster.count) * GAIN;
            data[base + 4] = cluster.sumR / cluster.count * boost;
            data[base + 5] = cluster.sumG / cluster.count * boost;
            data[base + 6] = cluster.sumB / cluster.count * boost;
            data[base + 7] = 0f;
        }
        return new EmissiveLights(data, kept);
    }

    /** True when at least one neighbour is not opaque, so the block can radiate. */
    private static boolean hasOpenFace(VoxelWorld world, int x, int y, int z) {
        return !world.opaqueAt(x + 1, y, z) || !world.opaqueAt(x - 1, y, z)
                || !world.opaqueAt(x, y + 1, z) || !world.opaqueAt(x, y - 1, z)
                || !world.opaqueAt(x, y, z + 1) || !world.opaqueAt(x, y, z - 1);
    }

    /** Running totals for one cell of emissive blocks. */
    private static final class Cluster {
        float sumX;
        float sumY;
        float sumZ;
        float sumR;
        float sumG;
        float sumB;
        int count;

        void add(float x, float y, float z, int material) {
            int slot = Materials.slot(material) * 8;
            float[] table = Materials.table();
            sumX += x;
            sumY += y;
            sumZ += z;
            sumR += table[slot + 4];
            sumG += table[slot + 5];
            sumB += table[slot + 6];
            count++;
        }

        float power() {
            float luminance = 0.2126f * sumR + 0.7152f * sumG + 0.0722f * sumB;
            return luminance;
        }
    }
}
