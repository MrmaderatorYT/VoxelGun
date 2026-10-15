package com.ccs.voxelgun.map;

import com.ccs.voxelgun.voxel.VoxelWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

/** One playable arena: geometry, lighting and spawn layout. */
public abstract class GameMap {
    public static final int SIZE_X = 128;
    public static final int SIZE_Y = 48;
    public static final int SIZE_Z = 128;

    private final String id;
    private final String displayName;
    private final String subtitle;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final List<SpawnPoint> blueSpawns = new ArrayList<>();
    private final List<SpawnPoint> redSpawns = new ArrayList<>();
    private final List<SpawnPoint> freeSpawns = new ArrayList<>();
    private List<StairFlight> stairFlights = Collections.emptyList();

    public final List<StairFlight> stairFlights() { return stairFlights; }
    private final com.ccs.voxelgun.map.scenery.MapScenery scenery = new com.ccs.voxelgun.map.scenery.MapScenery();

    public final com.ccs.voxelgun.map.scenery.MapScenery scenery() { return scenery; }

    protected GameMap(String id, String displayName, String subtitle) {
        this(id, displayName, subtitle, SIZE_X, SIZE_Y, SIZE_Z);
    }

    /**
     * Creates a map with its own volume dimensions.
     *
     * <p>The original arenas keep using the three-argument constructor and therefore retain their
     * 128x48x128 layout. Large modes can opt into a wider/taller volume without changing any of
     * those coordinates or catalog indices.
     */
    protected GameMap(String id, String displayName, String subtitle,
                      int sizeX, int sizeY, int sizeZ) {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException("Map dimensions must be positive");
        }
        this.id = id;
        this.displayName = displayName;
        this.subtitle = subtitle;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String subtitle() {
        return subtitle;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public abstract MapEnvironment environment();

    protected abstract void generate(MapBuilder builder);

    /** Builds the volume and mirrors it into native memory. */
    public VoxelWorld build(long seed) {
        VoxelWorld world = new VoxelWorld(sizeX, sizeY, sizeZ);
        blueSpawns.clear();
        redSpawns.clear();
        freeSpawns.clear();
        scenery.clear();
        MapBuilder builder = new MapBuilder(world, seed, scenery);
        generate(builder);
        com.ccs.voxelgun.map.scenery.MapDressing.apply(this, builder);
        builder.finishTraversal();
        stairFlights = builder.stairFlights();
        world.commit();
        return world;
    }

    protected void blueSpawn(float x, float z, float yaw) {
        blueSpawn(x, MapBuilder.GROUND, z, yaw);
    }

    protected void blueSpawn(float x, float y, float z, float yaw) {
        blueSpawns.add(new SpawnPoint(x + 0.5f, y, z + 0.5f, yaw));
    }

    protected void redSpawn(float x, float z, float yaw) {
        redSpawn(x, MapBuilder.GROUND, z, yaw);
    }

    protected void redSpawn(float x, float y, float z, float yaw) {
        redSpawns.add(new SpawnPoint(x + 0.5f, y, z + 0.5f, yaw));
    }

    protected void freeSpawn(float x, float z, float yaw) {
        freeSpawn(x, MapBuilder.GROUND, z, yaw);
    }

    protected void freeSpawn(float x, float y, float z, float yaw) {
        freeSpawns.add(new SpawnPoint(x + 0.5f, y, z + 0.5f, yaw));
    }

    public List<SpawnPoint> blueSpawns() {
        return blueSpawns;
    }

    public List<SpawnPoint> redSpawns() {
        return redSpawns;
    }

    /** Free-for-all spawns fall back to the team spawns when a map does not define extras. */
    public List<SpawnPoint> freeSpawns() {
        if (freeSpawns.isEmpty()) {
            List<SpawnPoint> all = new ArrayList<>(blueSpawns);
            all.addAll(redSpawns);
            return all;
        }
        return freeSpawns;
    }
}
