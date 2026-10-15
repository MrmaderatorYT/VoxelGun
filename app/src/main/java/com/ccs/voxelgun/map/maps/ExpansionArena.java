package com.ccs.voxelgun.map.maps;

import com.ccs.voxelgun.game.GameMode;
import com.ccs.voxelgun.map.GameMap;
import com.ccs.voxelgun.map.MapBuilder;
import com.ccs.voxelgun.map.MapEnvironment;
import com.ccs.voxelgun.map.scenery.ExpansionDressing;
import com.ccs.voxelgun.voxel.Materials;

/** Fifteen compact arenas with distinct routes, landmarks, palettes and combat ranges. */
public final class ExpansionArena extends GameMap {
    private static final int G = MapBuilder.GROUND;
    private static final int CENTRE = 64;

    private static final String[][] DATA = {
            {"harbor_yard", "Harbor Yard", "Coast · docks, cranes and containers",
                    "Mixed range / Docks / Containers"},
            {"arctic_station", "Arctic Station", "Polar base · labs and ice lanes",
                    "Mid range / Ice lanes / Labs"},
            {"metro_junction", "Metro Junction", "Underground · trains and platforms",
                    "Close quarters / Platforms / Cross routes"},
            {"canyon_outpost", "Canyon Outpost", "Desert cliffs · fort and flank gullies",
                    "Long range / Canyon / Fort"},
            {"reactor_core", "Reactor Core", "Industrial · reactor ring and service halls",
                    "Close quarters / Reactor / Ring routes"},
            {"rooftop_district", "Rooftop District", "City block · streets and roof access",
                    "Vertical / Streets / Rooftops"},
            {"bamboo_temple", "Bamboo Temple", "Garden · shrine, ponds and woodland lanes",
                    "Mixed range / Temple / Gardens"},
            {"stone_quarry", "Stone Quarry", "Excavation · terraces and heavy machinery",
                    "Vertical / Terraces / Open mid"},
            {"orbital_dock", "Orbital Dock", "Spaceport · hangars and docking arms",
                    "Mid range / Hangars / Symmetric"},
            {"flooded_plaza", "Flooded Plaza", "Old town · canals, bridges and arcades",
                    "Mixed range / Water / Courtyards"},
            {"train_depot", "Train Depot", "Rail yard · carriages and service sheds",
                    "Long lanes / Trains / Flanking"},
            {"volcano_forge", "Volcano Forge", "Caldera · lava channels and foundry",
                    "Hazards / Bridges / Foundry"},
            {"ice_citadel", "Ice Citadel", "Frozen fortress · gates and battlements",
                    "Vertical / Fortress / Four gates"},
            {"solar_farm", "Solar Farm", "Badlands · panel rows and control station",
                    "Mid range / Panel rows / Cover"},
            {"bunker_13", "Bunker 13", "Military complex · trenches and blast doors",
                    "Close quarters / Bunker / Corridors"},
    };

    private final int layout;

    private ExpansionArena(int layout) {
        super(DATA[layout][0], DATA[layout][1], DATA[layout][2]);
        this.layout = layout;
    }

    public static int count() {
        return DATA.length;
    }

    public static ExpansionArena create(int index) {
        if (index < 0 || index >= DATA.length) {
            throw new IllegalArgumentException("Unknown expansion map " + index);
        }
        return new ExpansionArena(index);
    }

    public static String tags(int index) {
        return DATA[Math.max(0, Math.min(DATA.length - 1, index))][3];
    }

    public static GameMode recommended(int index) {
        return switch (index) {
            case 2, 4, 8, 14 -> GameMode.KING_OF_THE_HILL;
            case 3, 5, 10 -> GameMode.BOMB_PLANT;
            case 1, 7, 11 -> GameMode.CRYSTAL_HEIST;
            case 6, 9, 12 -> GameMode.CAPTURE_POINT;
            default -> GameMode.TEAM_DEATHMATCH;
        };
    }

    @Override
    public MapEnvironment environment() {
        return switch (layout) {
            case 1, 12 -> coldEnvironment();
            case 2, 4, 8, 14 -> industrialEnvironment();
            case 3, 11, 13 -> sunsetEnvironment();
            case 6, 9 -> gardenEnvironment();
            default -> daylightEnvironment();
        };
    }

    @Override
    protected void generate(MapBuilder b) {
        int floor = floorMaterial();
        int accent = accentMaterial();
        // Broad, quiet surfaces let the coloured architecture and combat silhouettes read.
        b.ground(floor, accent, 0.025f);
        addBoundary(b);

        switch (layout) {
            case 0 -> harborYard(b);
            case 1 -> arcticStation(b);
            case 2 -> metroJunction(b);
            case 3 -> canyonOutpost(b);
            case 4 -> reactorCore(b);
            case 5 -> rooftopDistrict(b);
            case 6 -> bambooTemple(b);
            case 7 -> stoneQuarry(b);
            case 8 -> orbitalDock(b);
            case 9 -> floodedPlaza(b);
            case 10 -> trainDepot(b);
            case 11 -> volcanoForge(b);
            case 12 -> iceCitadel(b);
            case 13 -> solarFarm(b);
            default -> bunkerThirteen(b);
        }

        ExpansionDressing.buildArchitecture(b, layout);
        addSafeSpawns(b, floor);
    }

    private int floorMaterial() {
        return switch (layout) {
            case 0, 2, 5, 8, 10, 14 -> Materials.CONCRETE;
            case 1, 12 -> Materials.SNOW;
            case 3, 13 -> Materials.SAND;
            case 6 -> Materials.GRASS_BRIGHT;
            case 7, 11 -> Materials.STONE;
            case 9 -> Materials.TILE_FLOOR;
            default -> Materials.CONCRETE_DARK;
        };
    }

    private int accentMaterial() {
        return switch (layout) {
            case 0, 2, 5, 8, 10, 14 -> Materials.ASPHALT;
            case 1, 12 -> Materials.ICE;
            case 3, 13 -> Materials.SANDSTONE;
            case 6 -> Materials.GRASS;
            case 7, 11 -> Materials.CONCRETE_DARK;
            case 9 -> Materials.TILE_GROUT;
            default -> Materials.STEEL;
        };
    }

    private void addBoundary(MapBuilder b) {
        switch (layout) {
            case 1, 12 -> b.boundary(3, 10, Materials.ICE);
            case 3, 13 -> b.boundary(3, 12, Materials.SANDSTONE);
            case 6 -> b.boundary(3, 10, Materials.STONE_LIGHT);
            case 9 -> b.boundary(3, 10, Materials.BRICK_WHITE, Materials.BRICK_WHITE_ALT,
                    Materials.TRIM_BLUE);
            case 5 -> b.boundary(3, 12, Materials.BRICK_ORANGE,
                    Materials.BRICK_ORANGE_ALT, Materials.TRIM_GREY);
            default -> b.boundary(3, 12, Materials.CONCRETE_DARK, Materials.CONCRETE,
                    Materials.STEEL);
        }
    }

    private void harborYard(MapBuilder b) {
        b.pool(34, 12, 52, 30, G - 1, 2);
        b.pool(76, 98, 94, 116, G - 1, 2);
        b.deck(30, 32, 98, 39, G - 1);
        b.deck(30, 89, 98, 96, G - 1);

        int[] colors = {Materials.PAINT_BLUE, Materials.PAINT_RED, Materials.RUST};
        for (int[] cargo : new int[][]{{36, 74}, {70, 82}, {44, 98}, {80, 102}}) {
            b.container(cargo[0], G, cargo[1], true, 16, colors[cargo[0] % 3]);
        }
        b.container(60, G, 90, false, 14, Materials.RUST);
        b.panelCrate(56, G, 74, 4);
        b.panelCrate(86, G, 68, 4);
        b.deck(32, 31, 96, 42, G - 1);

        b.fill(58, G, 46, 59, G + 14, 47, Materials.STEEL);
        b.fill(72, G, 46, 73, G + 14, 47, Materials.STEEL);
        b.catwalk(58, G + 12, 46, true, 16, 4);
        b.landing(66, G + 12, 46, 69, 53, Materials.GRATE);
        b.stairs(54, G, 50, 0, 13, 4, Materials.STEEL);
        b.fill(72, G + 13, 34, 72, G + 13, 46, Materials.STEEL);
        b.set(72, G + 14, 34, Materials.LAMP);

        for (int x = 34; x <= 94; x += 15) {
            b.oilDrum(x, G, 84);
            b.crate(x, G, 40, 3, Materials.CRATE, Materials.WOOD_DARK);
        }
        b.scenery().landmark("Crane", 66, 48, 17);
        b.scenery().landmark("North Quay", 48, 34, 20);
        b.scenery().landmark("Cargo Maze", 70, 94, 24);
    }

    private void arcticStation(MapBuilder b) {
        b.tiledFloor(30, 10, 98, 118, G - 1, Materials.SNOW, Materials.ICE, 9);
        b.building(36, 34, 22, 18, 7, Materials.BRICK_WHITE,
                Materials.BRICK_WHITE_ALT, Materials.TRIM_BLUE);
        b.building(70, 76, 22, 18, 7, Materials.BRICK_WHITE,
                Materials.BRICK_WHITE_ALT, Materials.TRIM_RED);

        b.fill(52, G, 18, 55, G + 6, 42, Materials.ICE);
        b.fill(72, G, 86, 75, G + 6, 110, Materials.ICE);
        b.fill(34, G, 62, 52, G + 4, 65, Materials.ICE);
        b.fill(76, G, 62, 94, G + 4, 65, Materials.ICE);
        b.clear(42, G, 62, 46, G + 3, 65);
        b.clear(82, G, 62, 86, G + 3, 65);

        for (int x = 38; x <= 92; x += 18) {
            b.panelCrate(x, G, 58, 5);
            b.lamp(x + 2, G + 7, 60, Materials.STEEL);
        }
        b.pillar(64, G, 64, 8, 3, Materials.ICE);
        b.clear(62, G, 62, 66, G + 4, 66);
        b.set(64, G + 7, 64, Materials.NEON_CYAN);
    }

    private void metroJunction(MapBuilder b) {
        b.tiledFloor(6, 6, 121, 121, G - 1, Materials.TILE_DARK,
                Materials.TILE_GROUT, 6);
        b.fill(8, G - 1, 36, 119, G - 1, 43, Materials.ASPHALT);
        b.fill(8, G - 1, 85, 119, G - 1, 92, Materials.ASPHALT);
        for (int x = 8; x < 120; x += 5) {
            b.set(x, G - 1, 39, Materials.STEEL);
            b.set(x, G - 1, 89, Materials.STEEL);
        }

        b.container(30, G, 36, true, 20, Materials.PAINT_BLUE);
        b.container(78, G, 85, true, 20, Materials.PAINT_RED);
        b.fill(28, G, 46, 100, G + 2, 50, Materials.CONCRETE);
        b.fill(28, G, 78, 100, G + 2, 82, Materials.CONCRETE);
        for (int z : new int[]{46, 78}) {
            b.stairs(32, G, z, 0, 3, 5, Materials.CONCRETE);
            b.stairs(96, G, z, 1, 3, 5, Materials.CONCRETE);
            b.fill(28, G + 2, z, 100, G + 2, z, Materials.HAZARD);
        }

        // An open concourse with offset ticket banks, not another four-door central box.
        b.fill(52, G, 55, 55, G + 2, 61, Materials.PANEL_GREY);
        b.fill(73, G, 67, 76, G + 2, 73, Materials.PANEL_GREY);
        b.set(54, G + 3, 58, Materials.SCREEN);
        b.set(74, G + 3, 70, Materials.SCREEN);
        b.fill(51, G + 9, 52, 77, G + 9, 76, Materials.GRATE);
        b.fill(51, G, 52, 51, G + 8, 52, Materials.STEEL);
        b.fill(77, G, 76, 77, G + 8, 76, Materials.STEEL);
        b.checker(54, 54, 74, 74, G - 1, Materials.MARBLE, Materials.TILE_DARK, 4);

        for (int i = 0; i < 6; i++) {
            b.lamp(34 + i * 12, G + 8, 48, Materials.STEEL);
            b.lamp(34 + i * 12, G + 8, 80, Materials.STEEL);
        }
        b.neonStrip(54, G + 5, 52, true, 21, Materials.NEON_CYAN);
        b.neonStrip(54, G + 5, 76, true, 21, Materials.NEON_MAGENTA);
        b.scenery().landmark("Concourse", 64, 64, 18);
        b.scenery().landmark("Blue Platform", 48, 48, 17);
        b.scenery().landmark("Red Platform", 80, 80, 17);
    }

    private void canyonOutpost(MapBuilder b) {
        b.speckle(6, G - 1, 6, 121, G - 1, 121, Materials.SAND, Materials.DIRT, 0.025f);
        for (int z = 12; z <= 104; z += 23) {
            b.fill(34, G, z, 40, G + 7 + z % 4, z + 12, Materials.SANDSTONE);
            b.fill(88, G, 116 - z, 94, G + 8 + z % 3, 128 - z, Materials.SANDSTONE);
        }

        b.walls(50, G, 28, 78, G + 8, 52, Materials.BRICK_TAN);
        b.doorway(50, G, 40, 0, 7, 5);
        b.doorway(78, G, 40, 0, 7, 5);
        b.doorway(64, G, 28, 1, 5, 5);
        b.doorway(64, G, 52, 1, 5, 5);
        b.fill(51, G + 9, 29, 77, G + 9, 51, Materials.WOOD);
        b.landing(50, G + 9, 50, 53, 53, Materials.WOOD);
        b.stairs(40, G, 50, 0, 10, 4, Materials.SANDSTONE);
        b.fill(54, G, 58, 59, G + 2, 62, Materials.SANDSTONE);
        b.fill(54, G, 62, 56, G + 2, 68, Materials.SANDSTONE);
        b.fill(70, G, 74, 76, G + 2, 78, Materials.SANDSTONE);
        b.fill(74, G, 78, 76, G + 2, 84, Materials.SANDSTONE);
        b.fill(32, G - 1, 96, 55, G - 1, 103, Materials.DIRT);
        b.fill(50, G - 1, 88, 71, G - 1, 97, Materials.DIRT);
        b.fill(66, G - 1, 84, 98, G - 1, 91, Materials.DIRT);

        for (int z = 30; z <= 98; z += 17) {
            b.sandbags(44, G, z, true, 10, 2);
            b.sandbags(76, G, 126 - z, true, 10, 2);
        }
        b.pillar(64, G, 40, 13, 2, Materials.WOOD_DARK);
        b.set(64, G + 13, 40, Materials.LANTERN);
        b.crateStack(46, G, 82, 2, Materials.CRATE, Materials.WOOD_DARK);
        b.crateStack(78, G, 42, 2, Materials.CRATE, Materials.WOOD_DARK);
        b.scenery().landmark("North Fort", 64, 40, 20);
        b.scenery().landmark("Broken Rocks", 64, 68, 18);
        b.scenery().landmark("Dry Gully", 62, 96, 22);
    }

    private void reactorCore(MapBuilder b) {
        b.fill(20, G - 1, 20, 108, G - 1, 108, Materials.CONCRETE_DARK);
        b.fill(22, G - 1, 43, 106, G - 1, 47, Materials.PANEL_SEAM);
        b.fill(22, G - 1, 81, 106, G - 1, 85, Materials.PANEL_SEAM);
        for (int[] room : new int[][]{{32, 24}, {74, 86}}) {
            b.walls(room[0], G, room[1], room[0] + 18, G + 7, room[1] + 16,
                    Materials.CONCRETE);
            b.doorway(room[0] + 9, G, room[1] + 16, 1, 5, 5);
            b.doorway(room[0] + 9, G, room[1], 1, 5, 5);
            b.doorway(room[0] + 18, G, room[1] + 8, 0, 5, 5);
            b.fill(room[0] + 2, G + 6, room[1] + 2, room[0] + 16, G + 6,
                    room[1] + 14, Materials.GRATE);
            b.set(room[0] + 9, G + 5, room[1] + 8, Materials.LAMP);
        }
        b.landing(34, G + 6, 28, 37, 32, Materials.GRATE);
        b.stairs(27, G, 28, 0, 7, 4, Materials.STEEL);
        b.landing(87, G + 6, 90, 90, 94, Materials.GRATE);
        b.stairs(97, G, 90, 1, 7, 4, Materials.STEEL);

        b.disc(CENTRE, G, CENTRE, 14, Materials.STEEL);
        b.pillar(CENTRE, G + 1, CENTRE, 14, 6, Materials.GLASS_TINTED);
        b.pillar(CENTRE, G + 1, CENTRE, 13, 3, Materials.LAVA);
        b.disc(CENTRE, G + 15, CENTRE, 8, Materials.CHROME);
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            int x = CENTRE + (int) Math.round(Math.cos(angle) * 17);
            int z = CENTRE + (int) Math.round(Math.sin(angle) * 17);
            b.pillar(x, G, z, 8, 1, Materials.STEEL);
            b.set(x, G + 8, z, i % 2 == 0 ? Materials.NEON_CYAN : Materials.NEON_MAGENTA);
        }
        b.bridge(36, G, 62, true, 20, 5, Materials.GRATE, Materials.STEEL);
        b.bridge(73, G, 62, true, 20, 5, Materials.GRATE, Materials.STEEL);
        b.bridge(62, G, 36, false, 20, 5, Materials.GRATE, Materials.STEEL);
        b.bridge(62, G, 73, false, 20, 5, Materials.GRATE, Materials.STEEL);
        b.scenery().landmark("Reactor Ring", 64, 64, 22);
        b.scenery().landmark("Coolant Bay", 40, 32, 14);
        b.scenery().landmark("Control Bay", 84, 94, 14);
    }

    private void rooftopDistrict(MapBuilder b) {
        b.fill(6, G - 1, 57, 121, G - 1, 71, Materials.ASPHALT);
        b.fill(57, G - 1, 6, 71, G - 1, 121, Materials.ASPHALT);
        b.tiledFloor(50, 50, 78, 78, G - 1, Materials.TILE_FLOOR,
                Materials.TILE_GROUT, 4);

        b.building(30, 34, 20, 18, 8, Materials.BRICK_ORANGE,
                Materials.BRICK_ORANGE_ALT, Materials.TRIM_RED);
        b.building(78, 34, 20, 18, 10, Materials.BRICK_WHITE,
                Materials.BRICK_WHITE_ALT, Materials.TRIM_BLUE);
        b.building(30, 76, 20, 18, 10, Materials.BRICK_WHITE,
                Materials.BRICK_WHITE_ALT, Materials.TRIM_GREY);
        b.building(78, 76, 20, 18, 8, Materials.BRICK_TAN,
                Materials.BRICK_TAN_ALT, Materials.TRIM_RED);

        b.landing(30, G + 8, 38, 33, 41, Materials.CONCRETE);
        b.landing(94, G + 10, 38, 97, 41, Materials.CONCRETE);
        b.landing(30, G + 10, 82, 33, 85, Materials.CONCRETE);
        b.landing(94, G + 8, 82, 97, 85, Materials.CONCRETE);
        b.stairs(21, G, 38, 0, 9, 4, Materials.CONCRETE);
        b.stairs(108, G, 38, 1, 11, 4, Materials.CONCRETE);
        b.stairs(19, G, 82, 0, 11, 4, Materials.CONCRETE);
        b.stairs(106, G, 82, 1, 9, 4, Materials.CONCRETE);
        b.catwalk(49, G + 10, 44, true, 29, 4);
        b.catwalk(49, G + 10, 82, true, 29, 4);
        b.catwalk(62, G + 10, 44, false, 41, 4);
        b.landing(58, G + 10, 44, 68, 47, Materials.GRATE);
        b.landing(58, G + 10, 82, 68, 85, Materials.GRATE);
        b.landing(78, G + 10, 44, 81, 47, Materials.CONCRETE);
        b.landing(46, G + 10, 82, 49, 85, Materials.CONCRETE);
        b.landing(78, G + 10, 82, 78, 85, Materials.GRATE);
        b.stairs(47, G + 9, 44, 0, 2, 4, Materials.STEEL);
        b.stairs(80, G + 9, 82, 1, 2, 4, Materials.STEEL);

        b.kerb(53, 53, 60, 60, G - 1, Materials.GRASS_BRIGHT);
        b.kerb(68, 68, 75, 75, G - 1, Materials.GRASS_BRIGHT);
        b.panelCrate(54, G, 70, 5);
        b.oilDrum(74, G, 56);
    }

    private void bambooTemple(MapBuilder b) {
        b.tiledFloor(10, 60, 118, 68, G - 1, Materials.STONE_LIGHT,
                Materials.TILE_GROUT, 4);
        b.tiledFloor(60, 10, 68, 118, G - 1, Materials.STONE_LIGHT,
                Materials.TILE_GROUT, 4);
        b.pool(42, 70, 54, 90, G - 1, 2);
        b.pool(74, 72, 86, 94, G - 1, 2);

        b.walls(52, G, 30, 76, G + 7, 54, Materials.WOOD_LIGHT);
        b.doorway(52, G, 42, 0, 7, 5);
        b.doorway(76, G, 42, 0, 7, 5);
        b.doorway(64, G, 30, 1, 7, 5);
        b.doorway(64, G, 54, 1, 7, 5);
        b.roof(49, 27, 79, 57, G + 8, Materials.ROOF_TEAL);
        b.fill(59, G, 37, 69, G + 2, 47, Materials.STONE_LIGHT);
        b.clear(61, G, 39, 67, G + 2, 45);
        b.set(64, G + 3, 42, Materials.LANTERN);

        for (int i = 0; i < 10; i++) {
            int z = 18 + i * 10;
            bamboo(b, 34 + i % 2 * 6, z);
            bamboo(b, 94 - i % 2 * 6, 116 - z);
        }
        b.bridge(41, G, 78, true, 15, 4, Materials.WOOD_LIGHT, Materials.WOOD_DARK);
        b.bridge(73, G, 84, true, 15, 4, Materials.WOOD_LIGHT, Materials.WOOD_DARK);
        b.tiledFloor(32, 98, 96, 104, G - 1, Materials.STONE_LIGHT, Materials.TILE_GROUT, 4);
        b.tiledFloor(28, 70, 34, 102, G - 1, Materials.STONE_LIGHT, Materials.TILE_GROUT, 4);
        b.tiledFloor(94, 70, 100, 102, G - 1, Materials.STONE_LIGHT, Materials.TILE_GROUT, 4);
        b.scenery().landmark("North Shrine", 64, 42, 19);
        b.scenery().landmark("Garden Bridges", 64, 82, 24);
        b.scenery().landmark("Bamboo Walk", 64, 102, 18);
    }

    private void stoneQuarry(MapBuilder b) {
        b.fill(30, G, 20, 48, G + 3, 44, Materials.STONE);
        b.fill(34, G + 4, 24, 48, G + 7, 40, Materials.STONE_LIGHT);
        b.stairs(26, G, 30, 0, 8, 6, Materials.STONE_LIGHT);

        b.fill(80, G, 84, 98, G + 3, 108, Materials.STONE);
        b.fill(80, G + 4, 88, 94, G + 7, 104, Materials.STONE_LIGHT);
        b.stairs(102, G, 96, 1, 8, 6, Materials.STONE_LIGHT);

        b.fill(78, G, 20, 100, G + 5, 36, Materials.CONCRETE_DARK);
        b.clear(82, G, 24, 96, G + 3, 36);
        b.fill(28, G, 92, 50, G + 5, 108, Materials.CONCRETE_DARK);
        b.clear(32, G, 92, 46, G + 3, 104);

        b.disc(64, G, 64, 15, Materials.CONCRETE);
        b.disc(64, G + 1, 64, 9, Materials.STONE_LIGHT);
        b.stairs(48, G, 61, 0, 1, 7, Materials.STONE_LIGHT);
        b.stairs(80, G, 61, 1, 1, 7, Materials.STONE_LIGHT);
        b.pillar(64, G + 2, 64, 8, 2, Materials.STEEL);
        b.fill(64, G + 9, 50, 64, G + 9, 64, Materials.STEEL);
        b.set(64, G + 10, 50, Materials.HAZARD);

        for (int x = 38; x <= 92; x += 18) {
            b.crateStack(x, G, 52 + x % 3 * 10, 2, Materials.STONE_LIGHT,
                    Materials.CONCRETE_DARK);
        }
    }

    private void orbitalDock(MapBuilder b) {
        b.fill(8, G - 1, 8, 119, G - 1, 119, Materials.CONCRETE_DARK);
        b.fill(8, G - 1, 36, 119, G - 1, 48, Materials.PANEL_SEAM);
        b.fill(8, G - 1, 78, 119, G - 1, 90, Materials.PANEL_SEAM);
        for (int[] hangar : new int[][]{{30, 18}, {76, 18}, {30, 92}, {76, 92}}) {
            b.walls(hangar[0], G, hangar[1], hangar[0] + 22, G + 8, hangar[1] + 16,
                    Materials.PANEL_GREY);
            b.doorway(hangar[0] + 11, G, hangar[1] + 16, 1, 9, 6);
            b.fill(hangar[0], G + 9, hangar[1], hangar[0] + 22, G + 9,
                    hangar[1] + 16, Materials.STEEL);
            b.neonStrip(hangar[0] + 2, G + 7, hangar[1] + 1, true, 19,
                    Materials.NEON_CYAN);
        }

        b.fill(48, G, 55, 80, G + 2, 73, Materials.CHROME);
        b.clear(54, G + 1, 59, 74, G + 2, 69);
        b.fill(60, G + 3, 59, 68, G + 7, 69, Materials.GLASS_TINTED);
        b.clear(62, G + 3, 61, 66, G + 6, 67);
        b.cone(64, G + 8, 64, 6, 8, Materials.STEEL);

        b.catwalk(18, G + 5, 42, true, 92, 5);
        b.catwalk(18, G + 5, 82, true, 92, 5);
        b.stairs(12, G, 42, 0, 6, 4, Materials.STEEL);
        b.stairs(115, G, 82, 1, 6, 4, Materials.STEEL);
        for (int x = 30; x <= 98; x += 17) {
            b.set(x, G + 8, 44, Materials.LAMP);
            b.set(x, G + 8, 84, Materials.LAMP);
            b.panelCrate(x, G, 48 + x % 2 * 32, 4);
        }
    }

    private void floodedPlaza(MapBuilder b) {
        b.tiledFloor(8, 8, 119, 119, G - 1, Materials.TILE_FLOOR,
                Materials.TILE_GROUT, 5);
        b.pool(32, 34, 52, 54, G - 1, 2);
        b.pool(76, 74, 96, 94, G - 1, 2);
        b.pool(76, 34, 96, 48, G - 1, 2);
        b.pool(32, 80, 52, 94, G - 1, 2);

        b.bridge(31, G, 43, true, 23, 4, Materials.DECK_WOOD, Materials.WOOD_DARK);
        b.bridge(75, G, 83, true, 23, 4, Materials.DECK_WOOD, Materials.WOOD_DARK);
        b.bridge(84, G, 33, false, 17, 4, Materials.DECK_WOOD, Materials.WOOD_DARK);
        b.bridge(40, G, 79, false, 17, 4, Materials.DECK_WOOD, Materials.WOOD_DARK);

        b.disc(64, G, 64, 13, Materials.MARBLE);
        b.pillar(64, G + 1, 64, 4, 5, Materials.POOL_EDGE);
        b.pillar(64, G + 2, 64, 3, 2, Materials.WATER);
        b.set(64, G + 5, 64, Materials.LANTERN);

        b.building(28, 58, 18, 14, 6, Materials.BRICK_WHITE,
                Materials.BRICK_WHITE_ALT, Materials.TRIM_BLUE);
        b.building(82, 56, 18, 14, 6, Materials.BRICK_ORANGE,
                Materials.BRICK_ORANGE_ALT, Materials.TRIM_RED);
        b.kerb(54, 30, 61, 37, G - 1, Materials.GRASS_BRIGHT);
        b.kerb(67, 91, 74, 98, G - 1, Materials.GRASS_BRIGHT);
    }

    private void trainDepot(MapBuilder b) {
        for (int z : new int[]{28, 48, 78, 98}) {
            b.fill(8, G - 1, z, 119, G - 1, z + 5, Materials.ASPHALT);
            for (int x = 10; x < 118; x += 4) {
                b.set(x, G - 1, z + 1, Materials.STEEL);
                b.set(x, G - 1, z + 4, Materials.STEEL);
            }
        }
        b.container(32, G, 28, true, 18, Materials.PAINT_BLUE);
        b.container(72, G, 28, true, 22, Materials.RUST);
        b.container(44, G, 48, true, 24, Materials.PAINT_RED);
        b.container(82, G, 78, true, 18, Materials.PAINT_BLUE);
        b.container(30, G, 98, true, 22, Materials.RUST);
        b.container(66, G, 98, true, 26, Materials.PAINT_RED);

        b.fill(24, G, 59, 104, G, 69, Materials.CONCRETE);
        b.fill(24, G + 5, 59, 104, G + 5, 61, Materials.STEEL);
        b.fill(24, G + 5, 67, 104, G + 5, 69, Materials.STEEL);
        for (int x = 28; x <= 100; x += 12) {
            b.fill(x, G + 2, 60, x, G + 5, 60, Materials.STEEL);
            b.set(x, G + 6, 60, x % 24 == 4 ? Materials.EMERGENCY : Materials.LAMP);
        }
        b.building(82, 42, 20, 14, 6, Materials.BRICK_TAN,
                Materials.BRICK_TAN_ALT, Materials.TRIM_GREY);
        b.panelCrate(54, G, 72, 5);
        b.oilDrum(72, G, 54);
    }

    private void volcanoForge(MapBuilder b) {
        b.speckle(6, G - 1, 6, 121, G - 1, 121, Materials.STONE,
                Materials.CONCRETE_DARK, 0.035f);
        lavaChannel(b, 35);
        lavaChannel(b, 91);

        b.walls(50, G, 49, 78, G + 9, 79, Materials.CONCRETE_DARK);
        b.doorway(50, G, 64, 0, 7, 6);
        b.doorway(78, G, 64, 0, 7, 6);
        b.doorway(64, G, 49, 1, 7, 6);
        b.doorway(64, G, 79, 1, 7, 6);
        b.fill(51, G + 10, 50, 77, G + 10, 78, Materials.STEEL);
        b.clear(58, G + 10, 58, 70, G + 10, 70);

        b.pillar(64, G, 64, 8, 6, Materials.STONE);
        b.pillar(64, G + 1, 64, 7, 3, Materials.LAVA);
        b.disc(64, G + 8, 64, 7, Materials.STEEL);
        for (int i = 0; i < 4; i++) {
            double angle = Math.PI / 4 + i * Math.PI / 2;
            int x = 64 + (int) Math.round(Math.cos(angle) * 12);
            int z = 64 + (int) Math.round(Math.sin(angle) * 12);
            b.pillar(x, G, z, 10, 1, Materials.COPPER);
            b.set(x, G + 10, z, Materials.LAVA);
        }
        b.crateStack(38, G, 52, 2, Materials.RUST, Materials.STEEL);
        b.crateStack(86, G, 76, 2, Materials.RUST, Materials.STEEL);
    }

    private void lavaChannel(MapBuilder b, int z) {
        b.fill(4, G - 1, z, 123, G - 1, z + 5, Materials.LAVA);
        b.clear(4, G, z, 123, G + 2, z + 5);
        for (int x : new int[]{24, 47, 62, 82, 103}) {
            b.fill(x, G, z - 1, x + 5, G, z + 6, Materials.STEEL);
            b.fill(x, G + 1, z - 1, x + 5, G + 1, z - 1, Materials.HAZARD);
            b.fill(x, G + 1, z + 6, x + 5, G + 1, z + 6, Materials.HAZARD);
        }
    }

    private void iceCitadel(MapBuilder b) {
        b.fill(38, G - 1, 34, 90, G - 1, 94, Materials.ICE);
        b.walls(42, G, 38, 86, G + 10, 90, Materials.STONE_LIGHT);
        b.doorway(42, G, 64, 0, 9, 6);
        b.doorway(86, G, 64, 0, 9, 6);
        b.doorway(64, G, 38, 1, 9, 6);
        b.doorway(64, G, 90, 1, 9, 6);

        for (int[] tower : new int[][]{{42, 38}, {86, 38}, {42, 90}, {86, 90}}) {
            b.pillar(tower[0], G, tower[1], 16, 4, Materials.ICE);
            b.pillar(tower[0], G + 1, tower[1], 13, 3, Materials.AIR);
            b.cone(tower[0], G + 16, tower[1], 6, 6, Materials.ROOF_BLUE);
            b.set(tower[0], G + 22, tower[1], Materials.LANTERN);
        }

        b.walls(54, G, 52, 74, G + 8, 76, Materials.ICE);
        b.doorway(54, G, 64, 0, 5, 5);
        b.doorway(74, G, 64, 0, 5, 5);
        b.fill(55, G + 9, 53, 73, G + 9, 75, Materials.STONE_LIGHT);
        b.fill(42, G + 10, 38, 86, G + 10, 40, Materials.STONE_LIGHT);
        b.fill(42, G + 10, 88, 86, G + 10, 90, Materials.STONE_LIGHT);
        b.fill(42, G + 10, 38, 44, G + 10, 90, Materials.STONE_LIGHT);
        b.fill(84, G + 10, 38, 86, G + 10, 90, Materials.STONE_LIGHT);
        b.landing(50, G + 10, 38, 53, 40, Materials.STONE_LIGHT);
        b.landing(76, G + 10, 88, 79, 90, Materials.STONE_LIGHT);
        b.stairs(50, G, 51, 3, 11, 4, Materials.STONE_LIGHT);
        b.stairs(76, G, 77, 2, 11, 4, Materials.STONE_LIGHT);
        b.landing(62, G + 9, 40, 65, 53, Materials.STONE_LIGHT);
        b.landing(62, G + 9, 75, 65, 88, Materials.STONE_LIGHT);

        b.pool(28, 48, 36, 80, G - 1, 2);
        b.pool(92, 48, 100, 80, G - 1, 2);
        b.bridge(27, G, 62, true, 11, 5, Materials.WOOD_LIGHT, Materials.WOOD_DARK);
        b.bridge(91, G, 62, true, 11, 5, Materials.WOOD_LIGHT, Materials.WOOD_DARK);
    }

    private void solarFarm(MapBuilder b) {
        b.fill(8, G - 1, 58, 119, G - 1, 70, Materials.ASPHALT);
        for (int z = 22; z <= 106; z += 14) {
            solarRow(b, 32, z, 25);
            solarRow(b, 72, z, 25);
        }

        b.building(52, 48, 24, 18, 7, Materials.BRICK_WHITE,
                Materials.BRICK_WHITE_ALT, Materials.TRIM_BLUE);
        b.doorway(52, G, 57, 0, 5, 5);
        b.doorway(76, G, 57, 0, 5, 5);
        b.fill(58, G + 2, 50, 70, G + 5, 50, Materials.SCREEN);
        b.fill(58, G + 2, 64, 70, G + 5, 64, Materials.SCREEN);

        b.pillar(64, G + 8, 74, 16, 1, Materials.STEEL);
        for (int direction = -1; direction <= 1; direction += 2) {
            b.fill(64, G + 20, 74, 64 + direction * 8, G + 20, 74, Materials.STEEL);
            b.set(64 + direction * 8, G + 20, 74, Materials.NEON_CYAN);
        }
        b.panelCrate(44, G, 72, 5);
        b.panelCrate(80, G, 52, 5);
    }

    private void solarRow(MapBuilder b, int x, int z, int length) {
        for (int i = 0; i < length; i++) {
            int y = G + i % 3;
            b.set(x + i, y, z, Materials.SCREEN);
            b.set(x + i, y, z + 1, Materials.SCREEN);
            if (i % 6 == 0) {
                b.fill(x + i, G, z, x + i, y, z + 1, Materials.STEEL);
            }
        }
    }

    private void bunkerThirteen(MapBuilder b) {
        b.tiledFloor(6, 6, 121, 121, G - 1, Materials.CONCRETE_DARK,
                Materials.TILE_DARK, 8);
        b.fill(28, G + 8, 16, 100, G + 8, 112, Materials.CONCRETE_DARK);
        b.fill(44, G, 28, 47, G + 5, 100, Materials.CONCRETE);
        b.fill(80, G, 28, 83, G + 5, 100, Materials.CONCRETE);
        b.clear(44, G, 39, 47, G + 3, 46);
        b.clear(44, G, 81, 47, G + 3, 88);
        b.clear(80, G, 55, 83, G + 3, 62);
        b.clear(80, G, 94, 83, G + 3, 101);
        b.fill(58, G, 53, 61, G + 2, 59, Materials.STEEL);
        b.fill(67, G, 69, 70, G + 2, 75, Materials.STEEL);
        b.fill(28, G - 1, 19, 100, G - 1, 24, Materials.HAZARD);
        b.fill(28, G - 1, 106, 100, G - 1, 110, Materials.HAZARD);
        b.neonStrip(48, G + 6, 42, true, 29, Materials.EMERGENCY);
        b.neonStrip(48, G + 6, 85, true, 29, Materials.NEON_CYAN);

        for (int z = 22; z <= 106; z += 14) {
            b.lamp(28, G + 6, z, Materials.STEEL);
            b.lamp(100, G + 6, z, Materials.STEEL);
        }
        b.sandbags(44, G, 58, false, 12, 2);
        b.sandbags(82, G, 58, false, 12, 2);
        b.panelCrate(60, G, 34, 5);
        b.panelCrate(66, G, 88, 5);
        b.scenery().landmark("Blast Hall", 64, 64, 24);
        b.scenery().landmark("Service Tunnel", 64, 22, 20);
        b.scenery().landmark("Armory", 91, 96, 17);
    }

    private void bamboo(MapBuilder b, int x, int z) {
        for (int offset = 0; offset < 3; offset++) {
            int height = 7 + offset;
            b.fill(x + offset * 2, G, z + offset % 2, x + offset * 2,
                    G + height, z + offset % 2, Materials.TRUNK);
            b.set(x + offset * 2, G + height + 1, z + offset % 2, Materials.LEAVES);
        }
    }

    /** Clears generous team rooms and ten independent FFA/objective arrival lanes. */
    private void addSafeSpawns(MapBuilder b, int floor) {
        prepareTeamRoom(b, 9, 31, floor, Materials.TEAM_BLUE);
        prepareTeamRoom(b, 97, 119, floor, Materials.TEAM_RED);
        openRoute(b, 28, 64, 40, 64, floor);
        openRoute(b, 100, 64, 88, 64, floor);

        int[][] blue = {
                {14, 52}, {14, 60}, {14, 68}, {14, 76}, {22, 52},
                {22, 60}, {22, 68}, {22, 76}, {28, 56}, {28, 72},
        };
        for (int[] spawn : blue) {
            safePoint(b, spawn[0], spawn[1], floor, Materials.TEAM_BLUE);
            blueSpawn(spawn[0], spawn[1], 90);
        }

        for (int[] spawn : blue) {
            int x = SIZE_X - 1 - spawn[0];
            int z = spawn[1];
            safePoint(b, x, z, floor, Materials.TEAM_RED);
            redSpawn(x, z, 270);
        }

        int[][] free = {
                {64, 18, 180}, {64, 110, 0}, {40, 30, 135}, {88, 30, 225},
                {40, 98, 45}, {88, 98, 315}, {46, 64, 90}, {82, 64, 270},
                {64, 44, 180}, {64, 84, 0},
        };
        for (int[] spawn : free) {
            openTowardCentre(b, spawn[0], spawn[1], floor);
            safePoint(b, spawn[0], spawn[1], floor, Materials.HAZARD);
            freeSpawn(spawn[0], spawn[1], spawn[2]);
        }
    }

    private void prepareTeamRoom(MapBuilder b, int x0, int x1, int floor, int accent) {
        b.fill(x0, G - 1, 47, x1, G - 1, 81, floor);
        b.clear(x0, G, 47, x1, G + 5, 81);
        b.fill(x0, G - 1, 62, x1, G - 1, 66, accent);
        // Spawn courts are usable rooms with a sheltered back, furniture and low islands.
        // The islands fit strictly between the 5x5 spawn pads; all three exits stay open.
        ExpansionDressing.spawnCourt(b, layout, x0, x1, accent);
    }

    private void safePoint(MapBuilder b, int x, int z, int floor, int accent) {
        b.fill(x - 2, G - 1, z - 2, x + 2, G - 1, z + 2, floor);
        b.clear(x - 2, G, z - 2, x + 2, G + 4, z + 2);
        b.fill(x - 1, G - 1, z - 1, x + 1, G - 1, z + 1, accent);
    }

    private void openTowardCentre(MapBuilder b, int x, int z, int floor) {
        int dx = Integer.compare(CENTRE, x);
        int dz = Integer.compare(CENTRE, z);
        int endX = x + dx * 12;
        int endZ = z + dz * 12;
        openRoute(b, x, z, endX, endZ, floor);
    }

    private void openRoute(MapBuilder b, int x0, int z0, int x1, int z1, int floor) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
        for (int step = 0; step <= steps; step++) {
            float t = steps == 0 ? 0f : step / (float) steps;
            int x = Math.round(x0 + (x1 - x0) * t);
            int z = Math.round(z0 + (z1 - z0) * t);
            b.fill(x - 1, G - 1, z - 1, x + 1, G - 1, z + 1, floor);
            b.clear(x - 1, G, z - 1, x + 1, G + 4, z + 1);
        }
    }

    private static MapEnvironment daylightEnvironment() {
        return MapEnvironment.builder()
                .sun(-0.45f, 0.78f, 0.44f)
                .sunColor(1.00f, 0.94f, 0.82f, 4.8f)
                .sunAngle(0.03f)
                .sky(0.24f, 0.50f, 0.86f, 0.80f, 0.82f, 0.86f, 1.2f)
                .fog(0.008f)
                .exposure(1.0f)
                .build();
    }

    private static MapEnvironment coldEnvironment() {
        return MapEnvironment.builder()
                .sun(-0.34f, 0.72f, 0.55f)
                .sunColor(0.78f, 0.88f, 1.00f, 4.4f)
                .sunAngle(0.045f)
                .sky(0.22f, 0.42f, 0.68f, 0.72f, 0.82f, 0.94f, 1.1f)
                .fog(0.014f)
                .exposure(1.08f)
                .build();
    }

    private static MapEnvironment industrialEnvironment() {
        return MapEnvironment.builder()
                .sun(-0.25f, 0.90f, 0.35f)
                .sunColor(0.74f, 0.82f, 1.00f, 1.5f)
                .sunAngle(0.10f)
                .sky(0.05f, 0.07f, 0.12f, 0.18f, 0.22f, 0.30f, 0.55f)
                .fog(0.017f)
                .exposure(1.28f)
                .indoor(true)
                .build();
    }

    private static MapEnvironment sunsetEnvironment() {
        return MapEnvironment.builder()
                .sun(-0.76f, 0.38f, 0.52f)
                .sunColor(1.00f, 0.66f, 0.38f, 4.6f)
                .sunAngle(0.038f)
                .sky(0.16f, 0.28f, 0.54f, 0.92f, 0.52f, 0.34f, 1.05f)
                .fog(0.012f)
                .exposure(0.98f)
                .build();
    }

    private static MapEnvironment gardenEnvironment() {
        return MapEnvironment.builder()
                .sun(-0.52f, 0.80f, 0.38f)
                .sunColor(1.00f, 0.96f, 0.84f, 5.0f)
                .sunAngle(0.032f)
                .sky(0.26f, 0.58f, 0.88f, 0.78f, 0.90f, 0.82f, 1.25f)
                .fog(0.009f)
                .exposure(1.03f)
                .build();
    }
}
