package com.ccs.voxelgun.map.scenery;

import com.ccs.voxelgun.map.MapBuilder;
import com.ccs.voxelgun.voxel.Materials;

/**
 * Authored streets, workshops and gardens around the fifteen expansion arenas.
 * Large silhouettes are collidable voxels; only thin facade fittings are scenery.
 * Architecture is laid down before the arena's spawn and objective clearance pass.
 */
public final class ExpansionDressing {
    private static final int G = MapBuilder.GROUND;
    private static final String[] IDS = {"harbor_yard", "arctic_station", "metro_junction",
            "canyon_outpost", "reactor_core", "rooftop_district", "bamboo_temple",
            "stone_quarry", "orbital_dock", "flooded_plaza", "train_depot", "volcano_forge",
            "ice_citadel", "solar_farm", "bunker_13"};
    private ExpansionDressing() {}

    public static void buildArchitecture(MapBuilder b, int layout) {
        switch (layout) {
            case 0 -> harbor(b);
            case 1 -> arctic(b);
            case 2 -> metro(b);
            case 3 -> canyon(b);
            case 4 -> reactor(b);
            case 5 -> rooftops(b);
            case 6 -> garden(b);
            case 7 -> quarry(b);
            case 8 -> orbital(b);
            case 9 -> plaza(b);
            case 10 -> railway(b);
            case 11 -> forge(b);
            case 12 -> citadel(b);
            case 13 -> solar(b);
            default -> bunker(b);
        }
    }

    /** Preserve each map's earlier details, then add fittings attached to the new architecture. */
    public static void build(MapBuilder b, String id) {
        int layout = -1;
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) layout = i;
        if (layout < 0) return;
        if (layout == 5) UrbanDressing.build(b, false);
        else if (layout == 0 || layout == 4 || layout == 8 || layout == 10
                || layout == 11 || layout == 14) IndustrialDressing.build(b, id);

        MapScenery s = b.scenery();
        // The original named combat landmarks are retained. Additional names identify routes.
        switch (layout) {
            case 0 -> {
                callouts(s, "Customs House", "Shipwright", "Quayside", 21, 23, 109, 108, 20, 98);
                facade(s, 12, 10, 10, 15, Materials.TRIM_BLUE, "CUSTOMS");
                facade(s, 102, 10, 96, 13, Materials.TRIM_RED, "REPAIR");
                cargoDetails(s, 36, 74, 16); cargoDetails(s, 70, 82, 16);
                for (int z : new int[]{14, 102}) {
                    DecorRecipes.pipe(s, 12, 13.2f, z, 15, true, Materials.COPPER);
                }
            }
            case 1 -> {
                callouts(s, "Weather Lab", "Polar Garage", "Ice Crossing", 20, 22, 108, 105, 64, 64);
                facade(s, 12, 10, 10, 14, Materials.TRIM_BLUE, "WEATHER");
                facade(s, 103, 10, 98, 12, Materials.TRIM_RED, "POLAR");
                for (int x : new int[]{38, 74}) {
                    DecorRecipes.vent(s, x, 7, x == 38 ? 33.8f : 75.8f);
                    DecorRecipes.pipe(s, x, 10.8f, x == 38 ? 34 : 76, 15, true, Materials.COPPER);
                }
                panel(s, 6.8f, 7, 20, false, Materials.TRIM_BLUE);
                panel(s, 120.05f, 7, 104, false, Materials.TRIM_RED);
            }
            case 2 -> {
                callouts(s, "Ticket Office", "Cafe", "North Gallery", 20, 22, 108, 105, 64, 12);
                facade(s, 12, 9, 10, 13, Materials.TRIM_BLUE, "TICKETS");
                facade(s, 102, 9, 98, 13, Materials.TRIM_RED, "CAFE");
                for (int z : new int[]{7, 119}) for (int x = 32; x < 100; x += 16) {
                    panel(s, x, 5.4f, z == 7 ? 7.9f : 118.8f, true, Materials.TRIM_BLUE);
                }
                DecorRecipes.sign(s, 55, 9.5f, 35.8f, "PLATFORM", .23f, Materials.TRIM_BLUE);
                DecorRecipes.sign(s, 55, 9.5f, 92.8f, "PLATFORM", .23f, Materials.TRIM_RED);
            }
            case 3 -> {
                callouts(s, "Market", "Adobe Quarter", "South Arch", 20, 24, 108, 105, 64, 115);
                facade(s, 12, 10, 10, 13, Materials.ROOF_TEAL, "MARKET");
                facade(s, 103, 10, 98, 12, Materials.TRIM_RED, "OUTPOST");
                DecorRecipes.awning(s, 10, 8.6f, 29, 18, Materials.ROOF_TEAL);
                DecorRecipes.awning(s, 100, 8.6f, 116, 18, Materials.TRIM_RED);
                for (int x : new int[]{12, 106}) {
                    DecorRecipes.flowers(s, x, 4, x == 12 ? 32 : 95, Materials.CORAL);
                }
            }
            case 4 -> {
                callouts(s, "Turbine Hall", "Pump House", "Cable Trench", 20, 22, 108, 105, 62, 14);
                facade(s, 12, 10, 10, 14, Materials.TRIM_BLUE, "TURBINE");
                facade(s, 103, 10, 98, 12, Materials.TRIM_RED, "PUMPS");
                for (int z : new int[]{14, 111}) {
                    DecorRecipes.pipe(s, 36, 8.7f, z, 56, true, Materials.COPPER);
                    DecorRecipes.pipe(s, 36, 9.4f, z, 56, true, Materials.STEEL);
                }
                for (int z = 30; z <= 96; z += 22) {
                    panel(s, 7.9f, 6, z, false, Materials.TRIM_BLUE);
                    panel(s, 119.8f, 6, z, false, Materials.TRIM_RED);
                }
            }
            case 5 -> {
                callouts(s, "Corner Cafe", "Apartments", "Service Alley", 20, 22, 108, 22, 20, 108);
                facade(s, 12, 12, 10, 14, Materials.BRICK_TEAL, "CAFE");
                facade(s, 102, 14, 10, 14, Materials.BRICK_BURGUNDY, "HOTEL");
                DecorRecipes.awning(s, 11, 8.5f, 28, 16, Materials.ROOF_TEAL);
                DecorRecipes.awning(s, 101, 8.5f, 28, 16, Materials.TRIM_RED);
                DecorRecipes.flowers(s, 12, 7, 9.7f, Materials.CANDY_PINK);
                DecorRecipes.flowers(s, 102, 9, 9.7f, Materials.SUNNY);
            }
            case 6 -> {
                callouts(s, "Tea House", "Garden Pavilion", "Lantern Walk", 20, 22, 108, 105, 22, 96);
                facade(s, 12, 9, 10, 13, Materials.WOOD_DARK, "TEA HOUSE");
                facade(s, 102, 9, 98, 13, Materials.WOOD_DARK, "GARDEN");
                for (int z : new int[]{18, 100}) for (int x : new int[]{12, 102}) {
                    DecorRecipes.flowers(s, x, 3.7f, z + 13, Materials.CORAL);
                    DecorRecipes.fence(s, x, 3, z + 16, 12, Materials.WOOD_DARK);
                }
            }
            case 7 -> {
                callouts(s, "Crusher", "Dispatch", "Aggregate Yard", 20, 22, 108, 105, 66, 114);
                facade(s, 12, 10, 10, 13, Materials.HAZARD, "CRUSHER");
                facade(s, 103, 10, 103, 12, Materials.TRIM_BLUE, "DISPATCH");
                for (int x : new int[]{13, 101}) {
                    DecorRecipes.vent(s, x, 7, 9.8f);
                    DecorRecipes.pipe(s, x, 10, 10, 14, false, Materials.RUST);
                }
                DecorRecipes.sign(s, 54, 11, 9.8f, "AGGREGATE", .26f, Materials.HAZARD);
            }
            case 8 -> {
                callouts(s, "Flight Control", "Fuel Annex", "Docking Tower", 20, 22, 108, 105, 64, 64);
                facade(s, 12, 11, 10, 13, Materials.TRIM_BLUE, "CONTROL");
                facade(s, 103, 11, 100, 12, Materials.TRIM_RED, "FUEL");
                for (int z : new int[]{18, 92}) for (int x : new int[]{30, 76}) {
                    panel(s, x + 2, 7.5f, z - .15f, true, Materials.TRIM_BLUE);
                    DecorRecipes.vent(s, x + 16, 8, z - .15f);
                }
                DecorRecipes.sign(s, 56, 12.2f, 9.8f, "LAUNCH", .3f, Materials.TRIM_BLUE);
            }
            case 9 -> {
                callouts(s, "Canal Cafe", "Clock House", "Fountain", 20, 22, 108, 105, 64, 64);
                facade(s, 12, 12, 10, 13, Materials.BRICK_TEAL, "CAFE");
                facade(s, 102, 12, 98, 13, Materials.BRICK_BURGUNDY, "CLOCK");
                for (int x : new int[]{10, 100}) {
                    DecorRecipes.awning(s, x + 1, 8.5f, 28, 16, Materials.ROOF_TEAL);
                    DecorRecipes.awning(s, x + 1, 8.5f, 116, 16, Materials.TRIM_RED);
                    DecorRecipes.flowers(s, x + 2, 7, 9.7f, Materials.CANDY_PINK);
                    DecorRecipes.flowers(s, x + 2, 7, 97.7f, Materials.CORAL);
                }
            }
            case 10 -> {
                callouts(s, "Signal House", "Engine Shed", "Rail Junction", 20, 18, 108, 106, 64, 64);
                facade(s, 12, 11, 8, 13, Materials.TRIM_BLUE, "SIGNAL");
                facade(s, 102, 11, 102, 13, Materials.TRIM_RED, "ENGINES");
                for (int z : new int[]{28, 48, 78, 98}) {
                    DecorRecipes.sign(s, 8, 9.3f, z - .2f, "RAIL", .18f, Materials.HAZARD);
                }
                cargoDetails(s, 32, 28, 18); cargoDetails(s, 82, 78, 18);
            }
            case 11 -> {
                callouts(s, "Casting House", "Slag Yard", "Foundry", 20, 22, 108, 106, 64, 64);
                facade(s, 12, 11, 10, 13, Materials.TRIM_RED, "CASTING");
                facade(s, 102, 11, 103, 13, Materials.HAZARD, "SLAG");
                for (int x : new int[]{12, 101}) {
                    DecorRecipes.pipe(s, x, 12, 14, 15, false, Materials.COPPER);
                    DecorRecipes.vent(s, x + 3, 8, 9.8f);
                }
                DecorRecipes.sign(s, 53, 10.2f, 48.8f, "FOUNDRY", .3f, Materials.TRIM_RED);
            }
            case 12 -> {
                callouts(s, "West Lodge", "East Lodge", "Frozen Keep", 20, 22, 108, 106, 64, 64);
                facade(s, 12, 12, 10, 13, Materials.TRIM_BLUE, "LODGE");
                facade(s, 102, 12, 98, 13, Materials.TRIM_RED, "GUARD");
                for (int x : new int[]{43, 84}) for (int z : new int[]{43, 83}) {
                    s.box(x, 9, z, 1.2f, 5, .15f, Materials.TRIM_BLUE);
                    s.box(x + .2f, 9.2f, z - .02f, .8f, .35f, .16f, Materials.SUNNY);
                }
            }
            case 13 -> {
                callouts(s, "Power House", "Maintenance", "Panel Field", 20, 22, 108, 106, 64, 90);
                facade(s, 12, 10, 10, 13, Materials.TRIM_BLUE, "POWER");
                facade(s, 102, 10, 99, 13, Materials.TRIM_RED, "SERVICE");
                for (int x : new int[]{9, 111}) for (int z : new int[]{35, 91}) {
                    panel(s, x, 5.5f, z - .2f, true, Materials.TRIM_BLUE);
                }
                DecorRecipes.sign(s, 56, 9, 47.8f, "CONTROL", .25f, Materials.TRIM_BLUE);
            }
            default -> {
                callouts(s, "Barracks", "Generator Annex", "Supply Court", 20, 22, 108, 106, 64, 115);
                facade(s, 12, 10, 10, 13, Materials.TRIM_BLUE, "BARRACKS");
                facade(s, 102, 10, 100, 13, Materials.TRIM_RED, "GENERATOR");
                for (int z : new int[]{30, 72, 98}) {
                    DecorRecipes.pipe(s, 31, 9.9f, z, 8, false, Materials.COPPER);
                    DecorRecipes.pipe(s, 88, 9.9f, z, 8, false, Materials.COPPER);
                }
            }
        }
        spawnFittings(s, layout);
    }

    private static void harbor(MapBuilder b) {
        workshop(b, 10, 10, 19, 23, 9, Materials.BRICK_WHITE, Materials.TRIM_BLUE);
        workshop(b, 100, 96, 19, 22, 9, Materials.BRICK_WHITE, Materials.TRIM_RED);
        workshop(b, 101, 10, 18, 23, 7, Materials.PAINT_BLUE, Materials.PANEL_GREY);
        canopy(b, 10, 96, 20, 21, 8, Materials.STEEL, Materials.PAINT_RED);
        b.container(12, G, 102, true, 14, Materials.PAINT_BLUE);
        b.container(63, G, 9, true, 24, Materials.PAINT_RED);
        b.container(32, G, 111, true, 23, Materials.PAINT_BLUE);
        b.fill(9, G - 1, 34, 28, G - 1, 44, Materials.ASPHALT);
        b.fill(99, G - 1, 84, 119, G - 1, 94, Materials.ASPHALT);
        forklift(b, 16, 37, Materials.SUNNY);
        forklift(b, 107, 86, Materials.CORAL);
        for (int x = 34; x < 95; x += 12) {
            b.fill(x, G, 31, x + 1, G + 2, 31, Materials.STEEL);
            b.fill(x, G, 97, x + 1, G + 2, 97, Materials.STEEL);
        }
        tank(b, 110, 40, 3, 7, Materials.PANEL_GREY, Materials.TRIM_BLUE);
        beamGate(b, 57, 112, 18, 10, Materials.STEEL, Materials.HAZARD);
    }

    private static void arctic(MapBuilder b) {
        workshop(b, 10, 10, 19, 21, 9, Materials.SNOW, Materials.TRIM_BLUE);
        workshop(b, 100, 98, 19, 20, 9, Materials.SNOW, Materials.TRIM_RED);
        workshop(b, 100, 10, 19, 22, 7, Materials.SNOW, Materials.ROOF_TEAL);
        workshop(b, 10, 98, 19, 20, 7, Materials.SNOW, Materials.ROOF_TEAL);
        b.fill(8, G - 1, 33, 29, G - 1, 43, Materials.CONCRETE);
        b.fill(99, G - 1, 85, 120, G - 1, 95, Materials.CONCRETE);
        trackedVehicle(b, 12, 36, Materials.CORAL);
        trackedVehicle(b, 105, 87, Materials.PAINT_BLUE);
        for (int x : new int[]{34, 83}) {
            tank(b, x + 5, 10, 3, 5, Materials.SNOW, Materials.TRIM_BLUE);
            tank(b, x + 5, 117, 3, 5, Materials.SNOW, Materials.TRIM_RED);
        }
        for (int[] p : new int[][]{{7,38},{115,38},{7,89},{115,89}}) {
            rocks(b, p[0], p[1], Materials.ICE, Materials.SNOW);
        }
        antenna(b, 19, 21, 14, Materials.TRIM_BLUE);
        antenna(b, 109, 108, 14, Materials.TRIM_RED);
        bench(b, 39, 48, 10, Materials.WOOD_LIGHT, Materials.STEEL);
        bench(b, 79, 80, 10, Materials.WOOD_LIGHT, Materials.STEEL);
    }

    private static void metro(MapBuilder b) {
        workshop(b, 10, 10, 19, 20, 8, Materials.BRICK_WHITE, Materials.TRIM_BLUE);
        workshop(b, 100, 98, 19, 20, 8, Materials.BRICK_WHITE, Materials.TRIM_RED);
        workshop(b, 100, 10, 19, 20, 8, Materials.BRICK_TEAL, Materials.FRAME_LIGHT);
        workshop(b, 10, 98, 19, 20, 8, Materials.BRICK_BURGUNDY, Materials.FRAME_LIGHT);
        arcade(b, 32, 7, 64, 9, 9, Materials.PLASTER, Materials.TRIM_BLUE);
        arcade(b, 32, 113, 64, 9, 9, Materials.PLASTER, Materials.TRIM_RED);
        for (int x : new int[]{34, 74}) {
            bench(b, x, 24, 15, Materials.PAINT_BLUE, Materials.STEEL);
            bench(b, x, 101, 15, Materials.PAINT_RED, Materials.STEEL);
        }
        beamGate(b, 56, 35, 19, 9, Materials.STEEL, Materials.TRIM_BLUE);
        beamGate(b, 56, 92, 19, 9, Materials.STEEL, Materials.TRIM_RED);
        for (int z : new int[]{36, 85}) {
            b.fill(4, G - 1, z + 1, 123, G - 1, z + 1, Materials.STEEL);
            b.fill(4, G - 1, z + 5, 123, G - 1, z + 5, Materials.STEEL);
            for (int x = 7; x < 122; x += 4) b.fill(x, G - 1, z, x, G - 1, z + 6, Materials.WOOD_DARK);
        }
        for (int z : new int[]{18, 96}) {
            cabinet(b, 7, z, Materials.PAINT_BLUE); cabinet(b, 116, z, Materials.PAINT_RED);
        }
    }

    private static void canyon(MapBuilder b) {
        workshop(b, 10, 10, 19, 22, 9, Materials.SAND_LIGHT, Materials.ROOF_TEAL);
        workshop(b, 100, 98, 19, 20, 9, Materials.SAND_LIGHT, Materials.TRIM_RED);
        workshop(b, 100, 10, 19, 22, 7, Materials.BRICK_TAN, Materials.WOOD_DARK);
        workshop(b, 10, 98, 19, 20, 7, Materials.BRICK_TAN, Materials.WOOD_DARK);
        canopy(b, 8, 34, 19, 9, 6, Materials.WOOD_DARK, Materials.ROOF_TEAL);
        canopy(b, 101, 85, 19, 9, 6, Materials.WOOD_DARK, Materials.TRIM_RED);
        bench(b, 11, 39, 11, Materials.WOOD_LIGHT, Materials.WOOD_DARK);
        bench(b, 105, 89, 11, Materials.WOOD_LIGHT, Materials.WOOD_DARK);
        beamGate(b, 55, 114, 19, 10, Materials.SANDSTONE, Materials.WOOD_DARK);
        for (int x : new int[]{34, 82}) {
            rocks(b, x, 7, Materials.SANDSTONE, Materials.SAND_LIGHT);
            rocks(b, x, 113, Materials.SANDSTONE, Materials.SAND_LIGHT);
        }
        for (int[] p : new int[][]{{7,42},{119,42},{7,86},{119,86}}) {
            b.palm(p[0], G, p[1], 10);
        }
        b.fill(54, G, 33, 57, G + 2, 37, Materials.WOOD_DARK);
        b.fill(71, G, 44, 75, G + 2, 48, Materials.WOOD_DARK);
        b.fill(52, G + 8, 28, 76, G + 8, 28, Materials.ROOF_TEAL);
    }

    private static void reactor(MapBuilder b) {
        workshop(b, 10, 10, 19, 22, 9, Materials.PANEL_GREY, Materials.TRIM_BLUE);
        workshop(b, 100, 98, 19, 20, 9, Materials.PANEL_GREY, Materials.TRIM_RED);
        workshop(b, 100, 10, 19, 22, 7, Materials.CONCRETE, Materials.TRIM_BLUE);
        workshop(b, 10, 98, 19, 20, 7, Materials.CONCRETE, Materials.TRIM_RED);
        for (int x : new int[]{36, 84}) {
            tank(b, x, 12, 4, 9, Materials.PANEL_GREY, Materials.ROOF_TEAL);
            tank(b, x + 5, 116, 4, 9, Materials.PANEL_GREY, Materials.TRIM_RED);
        }
        for (int z : new int[]{35, 88}) {
            cabinet(b, 12, z, Materials.TRIM_BLUE); cabinet(b, 109, z, Materials.TRIM_RED);
        }
        beamGate(b, 52, 11, 24, 11, Materials.STEEL, Materials.HAZARD);
        beamGate(b, 52, 115, 24, 11, Materials.STEEL, Materials.HAZARD);
        for (int z = 8; z < 120; z += 14) {
            b.fill(5, G, z, 7, G + 10, z + 2, Materials.PANEL_GREY);
            b.fill(120, G, z, 122, G + 10, z + 2, Materials.PANEL_GREY);
        }
        b.fill(36, G, 25, 39, G + 2, 29, Materials.PANEL_GREY);
        b.fill(84, G, 99, 89, G + 2, 101, Materials.PANEL_GREY);
    }

    private static void rooftops(MapBuilder b) {
        workshop(b, 10, 10, 19, 20, 11, Materials.BRICK_TEAL, Materials.PLASTER);
        workshop(b, 100, 10, 19, 20, 13, Materials.BRICK_BURGUNDY, Materials.PLASTER);
        workshop(b, 10, 100, 19, 18, 10, Materials.BRICK_ORANGE, Materials.PLASTER);
        for (int x : new int[]{32, 78}) {
            b.fill(x, G - 1, 9, x + 18, G - 1, 23, Materials.ASPHALT);
            car(b, x + 2, 12, x == 32 ? Materials.CORAL : Materials.SKY_BLUE);
            planter(b, x + 1, 105, 8, 5, Materials.BRICK_WHITE);
        }
        b.fill(8, G - 1, 32, 26, G - 1, 45, Materials.TILE_FLOOR);
        b.fill(101, G - 1, 32, 119, G - 1, 45, Materials.TILE_FLOOR);
        planter(b, 10, 34, 8, 7, Materials.PLASTER);
        planter(b, 110, 35, 8, 7, Materials.PLASTER);
        canopy(b, 53, 7, 21, 5, 9, Materials.PLASTER, Materials.ROOF_TEAL);
        b.fill(34, G - 1, 112, 73, G - 1, 117, Materials.ASPHALT);
        car(b, 46, 113, Materials.PAINT_RED);
        for (int[] p : new int[][]{{36,36},{84,36},{36,78},{84,78}}) {
            b.fill(p[0], G, p[1], p[0] + 3, G + 1, p[1] + 2, Materials.WOOD_DARK);
        }
    }

    private static void garden(MapBuilder b) {
        pavilion(b, 10, 10, 19, 22, Materials.WOOD_LIGHT, Materials.ROOF_TEAL);
        pavilion(b, 100, 98, 19, 20, Materials.WOOD_LIGHT, Materials.ROOF_TEAL);
        pavilion(b, 100, 10, 19, 22, Materials.PLASTER, Materials.ROOF_TILE);
        pavilion(b, 10, 98, 19, 20, Materials.PLASTER, Materials.ROOF_TILE);
        for (int[] p : new int[][]{{9,36},{108,37},{10,88},{107,88},{42,10},{79,113}}) {
            planter(b, p[0], p[1], 10, 6, Materials.STONE_LIGHT);
            bambooGrove(b, p[0] + 2, p[1] + 2);
        }
        beamGate(b, 54, 12, 20, 9, Materials.PAINT_RED, Materials.WOOD_DARK);
        beamGate(b, 54, 114, 20, 9, Materials.PAINT_RED, Materials.WOOD_DARK);
        bench(b, 34, 108, 14, Materials.WOOD_LIGHT, Materials.STONE_LIGHT);
        bench(b, 80, 17, 14, Materials.WOOD_LIGHT, Materials.STONE_LIGHT);
        for (int[] p : new int[][]{{35,73},{90,76},{57,94},{71,26}}) {
            rocks(b, p[0], p[1], Materials.STONE_LIGHT, Materials.STONE);
        }
    }

    private static void quarry(MapBuilder b) {
        workshop(b, 10, 10, 15, 20, 9, Materials.PANEL_GREY, Materials.HAZARD);
        workshop(b, 103, 103, 16, 15, 9, Materials.PANEL_GREY, Materials.TRIM_BLUE);
        canopy(b, 103, 10, 16, 27, 10, Materials.STEEL, Materials.HAZARD);
        canopy(b, 10, 90, 15, 28, 10, Materials.STEEL, Materials.HAZARD);
        b.fill(106, G, 18, 115, G + 5, 25, Materials.CONCRETE_DARK);
        b.fill(13, G, 104, 21, G + 5, 112, Materials.CONCRETE_DARK);
        b.fill(106, G + 6, 16, 115, G + 7, 27, Materials.HAZARD);
        b.fill(13, G + 6, 102, 21, G + 7, 114, Materials.HAZARD);
        for (int x : new int[]{36, 76}) {
            rocks(b, x, 7, Materials.STONE, Materials.STONE_LIGHT);
            rocks(b, x, 113, Materials.STONE_LIGHT, Materials.SANDSTONE);
        }
        beamGate(b, 52, 10, 22, 11, Materials.STEEL, Materials.HAZARD);
        trackedVehicle(b, 11, 37, Materials.SUNNY);
        trackedVehicle(b, 106, 83, Materials.SUNNY);
        b.fill(36, G - 1, 113, 96, G - 1, 122, Materials.DIRT);
        for (int x = 42; x < 90; x += 12) {
            b.fill(x, G, 117, x + 7, G + 2, 120, Materials.STONE_LIGHT);
        }
    }

    private static void orbital(MapBuilder b) {
        workshop(b, 10, 10, 17, 23, 10, Materials.PLASTER, Materials.TRIM_BLUE);
        workshop(b, 101, 100, 18, 18, 10, Materials.PLASTER, Materials.TRIM_RED);
        workshop(b, 101, 10, 18, 23, 8, Materials.PANEL_GREY, Materials.ROOF_TEAL);
        workshop(b, 10, 98, 17, 20, 8, Materials.PANEL_GREY, Materials.ROOF_TEAL);
        beamGate(b, 53, 10, 22, 12, Materials.PLASTER, Materials.TRIM_BLUE);
        beamGate(b, 53, 117, 22, 12, Materials.PLASTER, Materials.TRIM_RED);
        for (int x : new int[]{34, 88}) {
            tank(b, x, 10, 3, 7, Materials.PLASTER, Materials.TRIM_BLUE);
            tank(b, x, 117, 3, 7, Materials.PLASTER, Materials.TRIM_RED);
        }
        for (int[] p : new int[][]{{13,35},{105,35},{13,88},{105,91}}) {
            cabinet(b, p[0], p[1], Materials.ROOF_TEAL);
        }
        // Dock arms have real recessed service carts and alignment strips.
        for (int[] p : new int[][]{{34,22},{80,22},{34,96},{80,96}}) {
            b.fill(p[0], G, p[1], p[0] + 4, G + 1, p[1] + 3, Materials.PANEL_GREY);
            b.fill(p[0], G + 2, p[1], p[0] + 4, G + 2, p[1], Materials.PAINT_BLUE);
        }
        b.fill(8, G - 1, 34, 119, G - 1, 34, Materials.SUNNY);
        b.fill(8, G - 1, 91, 119, G - 1, 91, Materials.SUNNY);
    }

    private static void plaza(MapBuilder b) {
        workshop(b, 10, 10, 19, 20, 11, Materials.BRICK_TEAL, Materials.PLASTER);
        workshop(b, 100, 98, 19, 20, 11, Materials.BRICK_BURGUNDY, Materials.PLASTER);
        workshop(b, 100, 10, 19, 20, 9, Materials.BRICK_ORANGE, Materials.PLASTER);
        workshop(b, 10, 98, 19, 20, 9, Materials.BRICK_TAN, Materials.PLASTER);
        arcade(b, 34, 7, 60, 7, 8, Materials.PLASTER, Materials.ROOF_TILE);
        arcade(b, 34, 115, 60, 7, 8, Materials.PLASTER, Materials.ROOF_TILE);
        for (int[] p : new int[][]{{10,35},{108,35},{10,87},{108,87}}) {
            planter(b, p[0], p[1], 10, 7, Materials.PLASTER);
        }
        bench(b, 36, 22, 16, Materials.WOOD_LIGHT, Materials.STONE_LIGHT);
        bench(b, 76, 104, 16, Materials.WOOD_LIGHT, Materials.STONE_LIGHT);
        for (int x : new int[]{53, 73}) {
            b.fill(x, G, 55, x + 2, G + 2, 57, Materials.STONE_LIGHT);
            b.fill(x, G, 71, x + 2, G + 2, 73, Materials.STONE_LIGHT);
        }
        b.fill(108, G + 12, 105, 112, G + 20, 109, Materials.PLASTER);
        b.fill(108, G + 17, 104, 112, G + 20, 104, Materials.TRIM_BLUE);
        b.set(110, G + 19, 103, Materials.SUNNY);
    }

    private static void railway(MapBuilder b) {
        workshop(b, 10, 8, 19, 16, 10, Materials.BRICK_TAN, Materials.TRIM_BLUE);
        workshop(b, 100, 102, 19, 17, 10, Materials.BRICK_TAN, Materials.TRIM_RED);
        workshop(b, 100, 8, 19, 16, 8, Materials.BRICK_WHITE, Materials.ROOF_TEAL);
        workshop(b, 10, 106, 19, 13, 8, Materials.BRICK_WHITE, Materials.ROOF_TEAL);
        canopy(b, 34, 8, 20, 12, 7, Materials.STEEL, Materials.TRIM_BLUE);
        canopy(b, 74, 109, 20, 12, 7, Materials.STEEL, Materials.TRIM_RED);
        for (int z : new int[]{28, 48, 78, 98}) {
            for (int x : new int[]{7, 118}) {
                b.fill(x, G, z, x + 1, G + 8, z + 1, Materials.STEEL);
                b.fill(x - 1, G + 8, z, x + 2, G + 9, z + 1, Materials.HAZARD);
            }
            b.fill(4, G - 1, z + 1, 123, G - 1, z + 1, Materials.STEEL);
            b.fill(4, G - 1, z + 4, 123, G - 1, z + 4, Materials.STEEL);
        }
        forklift(b, 37, 12, Materials.SUNNY);
        forklift(b, 80, 114, Materials.SUNNY);
        bench(b, 34, 67, 12, Materials.WOOD_LIGHT, Materials.STEEL);
        bench(b, 80, 67, 12, Materials.WOOD_LIGHT, Materials.STEEL);
        cabinet(b, 12, 36, Materials.PAINT_BLUE);
        cabinet(b, 110, 85, Materials.PAINT_RED);
    }

    private static void forge(MapBuilder b) {
        workshop(b, 10, 10, 19, 20, 10, Materials.STONE, Materials.TRIM_RED);
        workshop(b, 100, 103, 19, 15, 10, Materials.STONE, Materials.HAZARD);
        workshop(b, 100, 10, 19, 20, 8, Materials.CONCRETE_DARK, Materials.HAZARD);
        workshop(b, 10, 103, 19, 15, 8, Materials.CONCRETE_DARK, Materials.TRIM_RED);
        for (int x : new int[]{36, 83}) {
            tank(b, x + 4, 12, 4, 10, Materials.STEEL, Materials.RUST);
            tank(b, x + 4, 115, 4, 10, Materials.STEEL, Materials.RUST);
        }
        for (int x : new int[]{13, 108}) {
            b.fill(x, G + 8, 16, x + 3, G + 23, 19, Materials.BRICK);
            b.fill(x - 1, G + 23, 15, x + 4, G + 24, 20, Materials.STEEL);
        }
        for (int[] p : new int[][]{{7,42},{114,42},{7,84},{114,84}}) {
            rocks(b, p[0], p[1], Materials.STONE, Materials.CONCRETE_DARK);
        }
        beamGate(b, 56, 13, 17, 11, Materials.STEEL, Materials.HAZARD);
        beamGate(b, 56, 114, 17, 11, Materials.STEEL, Materials.HAZARD);
        b.fill(52, G + 7, 49, 76, G + 7, 49, Materials.TRIM_RED);
        b.fill(52, G + 7, 79, 76, G + 7, 79, Materials.TRIM_RED);
    }

    private static void citadel(MapBuilder b) {
        workshop(b, 10, 10, 19, 22, 11, Materials.STONE_LIGHT, Materials.TRIM_BLUE);
        workshop(b, 100, 98, 19, 20, 11, Materials.STONE_LIGHT, Materials.TRIM_RED);
        pavilion(b, 100, 10, 19, 22, Materials.SNOW, Materials.ROOF_BLUE);
        pavilion(b, 10, 98, 19, 20, Materials.SNOW, Materials.ROOF_BLUE);
        for (int[] p : new int[][]{{7,36},{115,36},{7,87},{115,87}}) {
            rocks(b, p[0], p[1], Materials.ICE, Materials.SNOW);
        }
        beamGate(b, 54, 12, 20, 12, Materials.STONE_LIGHT, Materials.TRIM_BLUE);
        beamGate(b, 54, 114, 20, 12, Materials.STONE_LIGHT, Materials.TRIM_RED);
        for (int x : new int[]{34, 87}) {
            b.pillar(x, G, 12, 10, 3, Materials.ICE);
            b.cone(x, G + 10, 12, 4, 5, Materials.ROOF_BLUE);
            b.pillar(x, G, 115, 10, 3, Materials.ICE);
            b.cone(x, G + 10, 115, 4, 5, Materials.ROOF_BLUE);
        }
        bench(b, 44, 23, 11, Materials.WOOD_LIGHT, Materials.STONE_LIGHT);
        bench(b, 74, 103, 11, Materials.WOOD_LIGHT, Materials.STONE_LIGHT);
        for (int x = 46; x < 85; x += 6) {
            b.fill(x, G + 11, 38, x + 2, G + 12, 39, Materials.STONE_LIGHT);
            b.fill(x, G + 11, 89, x + 2, G + 12, 90, Materials.STONE_LIGHT);
        }
    }

    private static void solar(MapBuilder b) {
        workshop(b, 10, 10, 19, 22, 9, Materials.PLASTER, Materials.TRIM_BLUE);
        workshop(b, 100, 99, 19, 19, 9, Materials.PLASTER, Materials.TRIM_RED);
        canopy(b, 100, 10, 19, 22, 8, Materials.STEEL, Materials.ROOF_TEAL);
        canopy(b, 10, 99, 19, 19, 8, Materials.STEEL, Materials.ROOF_TEAL);
        for (int x : new int[]{9, 111}) for (int z : new int[]{35, 91}) {
            cabinet(b, x, z, Materials.TRIM_BLUE);
        }
        tank(b, 19, 110, 4, 6, Materials.PLASTER, Materials.TRIM_BLUE);
        tank(b, 109, 21, 4, 6, Materials.PLASTER, Materials.TRIM_RED);
        car(b, 13, 36, Materials.CORAL); car(b, 103, 85, Materials.SKY_BLUE);
        for (int x : new int[]{37, 80}) {
            rocks(b, x, 7, Materials.SANDSTONE, Materials.SAND_LIGHT);
            rocks(b, x, 116, Materials.SANDSTONE, Materials.SAND_LIGHT);
        }
        beamGate(b, 53, 10, 22, 10, Materials.PLASTER, Materials.TRIM_BLUE);
        beamGate(b, 53, 117, 22, 10, Materials.PLASTER, Materials.TRIM_RED);
        for (int z = 22; z <= 106; z += 14) for (int x : new int[]{32, 72}) {
            b.fill(x, G - 1, z - 1, x + 24, G - 1, z - 1, Materials.CONCRETE);
            b.fill(x, G, z + 2, x + 24, G, z + 2, Materials.STEEL);
        }
    }

    private static void bunker(MapBuilder b) {
        workshop(b, 10, 10, 17, 23, 9, Materials.CONCRETE, Materials.TRIM_BLUE);
        workshop(b, 101, 100, 18, 18, 9, Materials.CONCRETE, Materials.TRIM_RED);
        workshop(b, 101, 10, 18, 23, 7, Materials.CONCRETE_DARK, Materials.HAZARD);
        workshop(b, 10, 98, 17, 20, 7, Materials.CONCRETE_DARK, Materials.HAZARD);
        for (int z : new int[]{35, 88}) {
            cabinet(b, 12, z, Materials.TRIM_BLUE); cabinet(b, 109, z, Materials.TRIM_RED);
        }
        for (int z : new int[]{31, 51, 72, 92}) {
            b.fill(28, G, z, 30, G + 7, z + 1, Materials.STEEL);
            b.fill(98, G, z, 100, G + 7, z + 1, Materials.STEEL);
            b.fill(29, G + 7, z, 43, G + 7, z + 1, Materials.STEEL);
            b.fill(84, G + 7, z, 99, G + 7, z + 1, Materials.STEEL);
        }
        canopy(b, 34, 7, 18, 9, 7, Materials.STEEL, Materials.CANVAS);
        canopy(b, 76, 112, 18, 9, 7, Materials.STEEL, Materials.CANVAS);
        b.sandbags(34, G, 14, true, 18, 2);
        b.sandbags(76, G, 112, true, 18, 2);
        trackedVehicle(b, 38, 8, Materials.CANVAS);
        trackedVehicle(b, 79, 115, Materials.CANVAS);
        bench(b, 32, 78, 9, Materials.WOOD_LIGHT, Materials.STEEL);
        bench(b, 88, 46, 8, Materials.WOOD_LIGHT, Materials.STEEL);
    }

    /** The back edge and shoulders frame the team court while the forward face stays open. */
    public static void spawnCourt(MapBuilder b, int layout, int x0, int x1, int team) {
        boolean west = x0 < 64;
        int edge = west ? x0 : x1;
        int start = west ? x0 : x1 - 5;
        int wall = switch (layout) {
            case 1, 12 -> Materials.SNOW;
            case 3, 13 -> Materials.SAND_LIGHT;
            case 6 -> Materials.WOOD_LIGHT;
            case 5, 9 -> Materials.PLASTER;
            default -> Materials.PANEL_GREY;
        };
        int cap = switch (layout) {
            case 3, 6, 9 -> Materials.WOOD_DARK;
            default -> Materials.STEEL;
        };
        // Solid side walls have broad openings; the canopy only covers the back of the court.
        b.fill(edge, G, 47, edge, G + 8, 81, wall);
        b.clear(edge, G + 2, 55, edge, G + 5, 59);
        b.clear(edge, G + 2, 70, edge, G + 5, 74);
        b.fill(start, G + 8, 47, start + 5, G + 8, 81, cap);
        b.fill(edge, G + 6, 47, edge, G + 7, 81, team);
        int shoulder = west ? x0 : x1 - 13;
        b.fill(shoulder, G, 46, shoulder + 13, G + 5, 46, wall);
        b.fill(shoulder, G, 82, shoulder + 13, G + 5, 82, wall);
        b.fill(shoulder, G + 6, 46, shoulder + 13, G + 6, 46, team);
        b.fill(shoulder, G + 6, 82, shoulder + 13, G + 6, 82, team);
        int island = west ? 17 : 108;
        for (int z : new int[]{55, 72}) {
            b.fill(island, G, z, island + 2, G + 1, z + 1, wall);
            b.fill(island, G + 2, z, island + 2, G + 2, z + 1, team);
        }
        int furniture = west ? 10 : 116;
        for (int z : new int[]{49, 78}) {
            b.fill(furniture, G, z, furniture + 1, G + 1, z + 1, cap);
        }
    }

    private static void workshop(MapBuilder b, int x, int z, int w, int d, int h, int wall, int trim) {
        int x1 = x + w - 1, z1 = z + d - 1;
        b.fill(x - 1, G - 1, z - 1, x1 + 1, G - 1, z1 + 1, Materials.CONCRETE);
        b.walls(x, G, z, x1, G + h, z1, wall);
        b.fill(x, G + h, z, x1, G + h, z1, trim);
        b.fill(x - 1, G + h + 1, z - 1, x1 + 1, G + h + 1, z1 + 1, wall);
        b.fill(x, G + h + 2, z, x1, G + h + 2, z, trim);
        b.fill(x, G + h + 2, z1, x1, G + h + 2, z1, trim);
        for (int corner : new int[]{x, x1}) {
            b.fill(corner, G, z, corner, G + h - 1, z, Materials.PLASTER);
            b.fill(corner, G, z1, corner, G + h - 1, z1, Materials.PLASTER);
        }
        // Two wide, level doors make each annex a route rather than a dead-end painted box.
        int cx = x + w / 2;
        b.fill(cx - 4, G, z, cx + 4, G + 6, z, trim);
        b.fill(cx - 4, G, z1, cx + 4, G + 6, z1, trim);
        b.doorway(cx, G, z, 1, 7, 5);
        b.doorway(cx, G, z1, 1, 7, 5);
        b.window(x, G + 3, z + d / 2 - 2, x, G + 5, z + d / 2 + 2, true);
        b.window(x1, G + 3, z + d / 2 - 2, x1, G + 5, z + d / 2 + 2, true);
        // Wall-mounted equipment and a workbench leave the central doorway axis clear.
        b.fill(x + 2, G, z + 3, x + 4, G + 1, z + 7, Materials.WOOD_DARK);
        b.fill(x + 2, G + 2, z + 3, x + 4, G + 2, z + 7, trim);
        b.fill(x1 - 3, G, z1 - 5, x1 - 2, G + 3, z1 - 2, Materials.PANEL_GREY);
        b.fill(x1 - 3, G + 2, z1 - 5, x1 - 2, G + 2, z1 - 5, Materials.SCREEN);
    }

    private static void pavilion(MapBuilder b, int x, int z, int w, int d, int wall, int roof) {
        workshop(b, x, z, w, d, 8, wall, Materials.WOOD_DARK);
        b.roof(x - 1, z - 1, x + w, z + d, G + 10, roof);
        b.clear(x + 4, G + 2, z, x + w - 5, G + 5, z);
        b.clear(x + 4, G + 2, z + d - 1, x + w - 5, G + 5, z + d - 1);
        b.fill(x + 3, G, z + 4, x + 3, G + 1, z + d - 5, Materials.WOOD_LIGHT);
    }

    private static void canopy(MapBuilder b, int x, int z, int w, int d, int h, int post, int roof) {
        b.fill(x, G - 1, z, x + w - 1, G - 1, z + d - 1, Materials.CONCRETE);
        for (int px : new int[]{x, x + w - 1}) for (int pz : new int[]{z, z + d - 1}) {
            b.fill(px, G, pz, px, G + h, pz, post);
        }
        b.fill(x - 1, G + h, z - 1, x + w, G + h, z + d, roof);
        b.fill(x, G + h - 1, z, x + w - 1, G + h - 1, z, post);
        b.fill(x, G + h - 1, z + d - 1, x + w - 1, G + h - 1, z + d - 1, post);
    }

    private static void arcade(MapBuilder b, int x, int z, int w, int d, int h, int wall, int trim) {
        canopy(b, x, z, w, d, h, wall, trim);
        int back = z < 64 ? z : z + d - 1;
        b.fill(x, G, back, x + w - 1, G + h, back, wall);
        b.fill(x, G + 3, back, x + w - 1, G + 4, back, trim);
        int front = z < 64 ? z + d - 1 : z;
        for (int px = x; px < x + w; px += 12) b.fill(px, G, front, px + 1, G + h, front, wall);
    }

    private static void beamGate(MapBuilder b, int x, int z, int width, int height, int post, int cap) {
        for (int px : new int[]{x, x + width}) b.fill(px, G, z, px + 1, G + height, z + 1, post);
        b.fill(x - 1, G + height, z - 1, x + width + 2, G + height + 1, z + 2, cap);
    }

    private static void bench(MapBuilder b, int x, int z, int width, int seat, int base) {
        b.fill(x, G, z, x + 1, G, z + 1, base);
        b.fill(x + width - 2, G, z, x + width - 1, G, z + 1, base);
        b.fill(x, G + 1, z, x + width - 1, G + 1, z + 1, seat);
        b.fill(x, G + 2, z + 1, x + width - 1, G + 2, z + 1, seat);
    }

    private static void planter(MapBuilder b, int x, int z, int w, int d, int rim) {
        b.fill(x, G, z, x + w - 1, G, z + d - 1, rim);
        b.fill(x + 1, G + 1, z + 1, x + w - 2, G + 1, z + d - 2, Materials.GRASS_BRIGHT);
        int cx = x + w / 2, cz = z + d / 2;
        b.fill(cx, G + 2, cz, cx, G + 5, cz, Materials.TRUNK);
        b.fill(cx - 2, G + 5, cz - 2, cx + 2, G + 8, cz + 2, Materials.LEAVES);
        b.fill(cx - 1, G + 9, cz - 1, cx + 1, G + 9, cz + 1, Materials.GRASS_BRIGHT);
    }

    private static void rocks(MapBuilder b, int x, int z, int base, int light) {
        b.fill(x, G, z, x + 5, G + 3, z + 4, base);
        b.fill(x + 1, G + 4, z + 1, x + 4, G + 6, z + 3, light);
        b.fill(x + 5, G, z + 2, x + 8, G + 2, z + 6, light);
    }

    private static void bambooGrove(MapBuilder b, int x, int z) {
        for (int i = 0; i < 3; i++) {
            int px = x + i * 2, pz = z + i % 2;
            b.fill(px, G + 2, pz, px, G + 10 + i, pz, Materials.WOOD_LIGHT);
            b.fill(px - 1, G + 8 + i, pz - 1, px + 1, G + 10 + i, pz + 1, Materials.LEAVES);
        }
    }

    private static void cabinet(MapBuilder b, int x, int z, int trim) {
        b.fill(x, G, z, x + 6, G + 4, z + 3, Materials.PANEL_GREY);
        b.fill(x, G, z, x + 6, G, z + 3, trim);
        b.fill(x + 1, G + 2, z, x + 2, G + 3, z, Materials.SCREEN);
        b.fill(x + 4, G + 1, z, x + 5, G + 3, z, Materials.DOOR_DARK);
        b.fill(x, G + 5, z, x + 6, G + 5, z + 3, trim);
    }

    private static void tank(MapBuilder b, int x, int z, int r, int height, int wall, int trim) {
        b.pillar(x, G, z, height, r, wall);
        b.disc(x, G + 1, z, r + 1, trim);
        b.disc(x, G + height, z, r + 1, trim);
        b.fill(x, G + height + 1, z, x, G + height + 3, z, Materials.STEEL);
    }

    private static void antenna(MapBuilder b, int x, int z, int y, int trim) {
        b.fill(x, G + 11, z, x, G + y + 7, z, Materials.STEEL);
        b.fill(x - 4, G + y + 5, z, x + 4, G + y + 5, z, trim);
        b.fill(x - 2, G + y + 7, z, x + 2, G + y + 7, z, Materials.SNOW);
    }

    private static void car(MapBuilder b, int x, int z, int color) {
        b.fill(x, G + 1, z, x + 10, G + 2, z + 4, color);
        b.fill(x + 3, G + 3, z, x + 7, G + 4, z + 4, color);
        b.fill(x + 3, G + 3, z, x + 7, G + 3, z, Materials.VISOR);
        b.fill(x + 3, G + 3, z + 4, x + 7, G + 3, z + 4, Materials.VISOR);
        for (int px : new int[]{x + 1, x + 8}) for (int pz : new int[]{z, z + 4}) {
            b.fill(px, G, pz, px + 1, G + 1, pz, Materials.RUBBER);
        }
    }

    private static void trackedVehicle(MapBuilder b, int x, int z, int color) {
        b.fill(x, G, z, x + 9, G + 1, z + 1, Materials.RUBBER);
        b.fill(x, G, z + 5, x + 9, G + 1, z + 6, Materials.RUBBER);
        b.fill(x + 1, G + 2, z + 1, x + 8, G + 3, z + 5, color);
        b.fill(x + 3, G + 4, z + 1, x + 6, G + 5, z + 5, color);
        b.fill(x + 3, G + 4, z, x + 6, G + 4, z, Materials.VISOR);
    }

    private static void forklift(MapBuilder b, int x, int z, int color) {
        b.fill(x, G, z, x + 5, G + 1, z + 4, Materials.RUBBER);
        b.fill(x, G + 2, z, x + 4, G + 3, z + 4, color);
        b.fill(x + 1, G + 4, z, x + 1, G + 6, z + 4, Materials.STEEL);
        b.fill(x + 4, G + 4, z, x + 4, G + 6, z + 4, Materials.STEEL);
        b.fill(x, G + 7, z, x + 5, G + 7, z + 4, color);
        b.fill(x + 6, G, z + 1, x + 9, G, z + 1, Materials.STEEL);
        b.fill(x + 6, G, z + 3, x + 9, G, z + 3, Materials.STEEL);
    }

    private static void facade(MapScenery s, int x, int y, int z, int width, int trim, String name) {
        s.box(x - 1, y, z - .18f, width + 1, .3f, .3f, trim);
        s.box(x - 1, y + .3f, z - .08f, .22f, 2, .18f, Materials.FRAME_LIGHT);
        s.box(x + width - .3f, y + .3f, z - .08f, .22f, 2, .18f, Materials.FRAME_LIGHT);
        DecorRecipes.sign(s, x + 2, y + .45f, z - .24f, name, .2f, trim);
        DecorRecipes.vent(s, x, y - 2.4f, z - .2f);
        DecorRecipes.vent(s, x + width - 2, y - 2.4f, z - .2f);
    }

    private static void panel(MapScenery s, float x, float y, float z, boolean alongX, int trim) {
        s.box(x, y, z, alongX ? 6 : .15f, 3, alongX ? .15f : 6, trim);
        for (int i = 0; i < 4; i++) {
            s.box(x + (alongX ? .5f : -.02f), y + .45f + i * .5f, z + (alongX ? -.02f : .5f),
                    alongX ? 4 : .17f, .13f, alongX ? .17f : 4, Materials.FRAME_LIGHT);
        }
    }

    private static void cargoDetails(MapScenery s, int x, int z, int length) {
        for (int i = 2; i < length; i += 3) {
            s.box(x + i, G + .5f, z - .1f, .12f, 4, .12f, Materials.PANEL_SEAM);
        }
        s.box(x + 2, G + 1.4f, z - .16f, 2.5f, 1.4f, .12f, Materials.PLASTER);
        s.box(x + 2.3f, G + 1.7f, z - .19f, 1.8f, .2f, .06f, Materials.TRIM_BLUE);
    }

    private static void callouts(MapScenery s, String a, String b, String c,
                                 int ax, int az, int bx, int bz, int cx, int cz) {
        s.landmark(a, ax, az, 15); s.landmark(b, bx, bz, 15); s.landmark(c, cx, cz, 18);
    }

    private static void spawnFittings(MapScenery s, int layout) {
        for (boolean west : new boolean[]{true, false}) {
            float x = west ? 9.98f : 118.85f;
            int trim = west ? Materials.TRIM_BLUE : Materials.TRIM_RED;
            for (int z : new int[]{49, 77}) {
                s.box(x, G + 4.2f, z, .16f, .5f, 3, Materials.LAMP);
                if (layout == 3 || layout == 6 || layout == 9 || layout == 12) {
                    s.box(x, G + 2, z + 1, .14f, 3.5f, 1.7f, trim);
                    s.box(x - .025f, G + 4.6f, z + 1.3f, .19f, .3f, 1.1f, Materials.SUNNY);
                } else panel(s, x, G + 2, z + 1, false, trim);
            }
            float capX = west ? 10 : 113;
            if (layout == 3 || layout == 6 || layout == 9 || layout == 12) {
                DecorRecipes.flowers(s, capX, G + 9.05f, 50, Materials.CORAL);
                DecorRecipes.flowers(s, capX, G + 9.05f, 75, Materials.SUNNY);
            } else {
                DecorRecipes.pipe(s, capX, G + 7.25f, 48, 31, false, Materials.COPPER);
            }
            // Small floor chevrons mark the clear middle exit, without making an obstacle.
            for (int i = 0; i < 3; i++) {
                s.box(west ? 24 + i * 2 : 102 - i * 2, G + .015f, 63,
                        .7f, .015f, 3, trim);
            }
        }
    }
}
