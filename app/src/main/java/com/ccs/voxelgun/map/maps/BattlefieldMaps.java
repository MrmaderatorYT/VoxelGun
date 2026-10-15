package com.ccs.voxelgun.map.maps;

import com.ccs.voxelgun.map.GameMap;
import com.ccs.voxelgun.map.MapBuilder;
import com.ccs.voxelgun.map.MapEnvironment;

/** Three full-size, individually composed battlefields with shared deployment capacity. */
public final class BattlefieldMaps {
    public static final int COUNT = 3;
    public static final int BATTLEFIELD_WIDTH = 640;
    public static final int BATTLEFIELD_DEPTH = 576;

    private static final String[][] DATA = {
            {"battlefield_city", "Battlefield: City",
                    "City districts · supermarket, circular plaza and park",
                    "Battle Field / Supermarket / Civic Square / Park / Old Town"},
            {"battlefield_farmland", "Battlefield: Farmland",
                    "Rural front · village market, orchards and canal crossings",
                    "Battle Field / Market / Orchard / Canal / Windmill"},
            {"battlefield_alps", "Battlefield: Alps",
                    "Mountain resort · alpine village, lake and high trails",
                    "Battle Field / Resort / Mountain Station / Lake / Forest Trails"},
    };

    private BattlefieldMaps() {}

    public static GameMap create(int index) {
        return switch (index) {
            case 0 -> new City();
            case 1 -> new Farmland();
            case 2 -> new Alps();
            default -> throw new IllegalArgumentException("Unknown battlefield map " + index);
        };
    }

    public static String tags(int index) {
        return DATA[Math.max(0, Math.min(COUNT - 1, index))][3];
    }

    private abstract static class BattlefieldMap extends GameMap {
        BattlefieldMap(int theme) {
            super(DATA[theme][0], DATA[theme][1], DATA[theme][2],
                    BATTLEFIELD_WIDTH, 64, BATTLEFIELD_DEPTH);
        }

        protected abstract void buildLandscape(MapBuilder builder);

        @Override
        protected final void generate(MapBuilder b) {
            // Each landscape owns its streets, interiors and cover. Copying a small district
            // across the volume erased each location's identity and duplicated every building.
            buildLandscape(b);
            for (int row = 0; row < 10; row++) {
                int z = BATTLEFIELD_DEPTH / 2 - 25 + row * 5;
                for (int column = 0; column < 5; column++) {
                    int x = 8 + column * 3;
                    blueSpawn(x, z, 90);
                    redSpawn(BATTLEFIELD_WIDTH - 1 - x, z, 270);
                }
            }
        }
    }

    private static final class City extends BattlefieldMap {
        City() { super(0); }

        @Override public MapEnvironment environment() {
            return MapEnvironment.builder()
                    .sun(-0.62f, 0.62f, 0.48f)
                    .sunColor(1.0f, 0.87f, 0.72f, 4.4f)
                    .sunAngle(0.035f)
                    .sky(0.19f, 0.34f, 0.62f, 0.72f, 0.69f, 0.66f, 1.0f)
                    .fog(0.0021f).exposure(0.96f).build();
        }

        @Override protected void buildLandscape(MapBuilder b) { CityBattlefield.build(b); }
    }

    private static final class Farmland extends BattlefieldMap {
        Farmland() { super(1); }

        @Override public MapEnvironment environment() {
            return MapEnvironment.builder()
                    .sun(-0.52f, 0.74f, 0.42f)
                    .sunColor(1.0f, 0.92f, 0.76f, 4.8f)
                    .sunAngle(0.032f)
                    .sky(0.22f, 0.48f, 0.82f, 0.73f, 0.79f, 0.76f, 1.16f)
                    .fog(0.0018f).exposure(1.02f).build();
        }

        @Override protected void buildLandscape(MapBuilder b) { FarmlandBattlefield.build(b); }
    }

    private static final class Alps extends BattlefieldMap {
        Alps() { super(2); }

        @Override public MapEnvironment environment() {
            return MapEnvironment.builder()
                    .sun(-0.52f, 0.74f, 0.42f)
                    .sunColor(1.0f, 0.94f, 0.86f, 4.7f)
                    .sunAngle(0.024f)
                    .sky(0.18f, 0.42f, 0.82f, 0.76f, 0.84f, 0.91f, 1.22f)
                    .fog(0.0015f).exposure(1.04f).build();
        }

        @Override protected void buildLandscape(MapBuilder b) { AlpineBattlefield.build(b); }
    }
}
