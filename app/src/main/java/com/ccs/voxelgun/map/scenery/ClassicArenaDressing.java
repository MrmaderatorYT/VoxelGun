package com.ccs.voxelgun.map.scenery;

import com.ccs.voxelgun.map.MapBuilder;
import com.ccs.voxelgun.voxel.Materials;

/**
 * Authored streets, work areas and courtyard furniture for the original eight arenas.
 * Large silhouettes belong to the voxel world; only shallow trim belongs to the visual layer.
 * Each layout deliberately leaves the original deployment pockets and stair approaches open.
 */
public final class ClassicArenaDressing {
    private static final int G = MapBuilder.GROUND;
    private ClassicArenaDressing() { }

    /** Called by the generator before its spawn list is authored. */
    public static void geometry(MapBuilder b, String id) {
        switch (id) {
            case "dust_yard" -> desert(b);
            case "warehouse" -> warehouse(b);
            case "neon_arena" -> arena(b);
            case "village" -> village(b);
            case "facility" -> facility(b);
            case "pirate_cove" -> pirate(b);
            case "sky_islands" -> sky(b);
            case "candy_castle" -> castle(b);
            default -> { }
        }
    }

    /** Surface detail applied after existing theme dressing. */
    public static void build(MapBuilder b, String id) {
        MapScenery s = b.scenery();
        switch (id) {
            case "dust_yard" -> {
                s.landmark("Bazaar", 64, 15, 24);
                s.landmark("Sun Court", 64, 64, 22);
                s.landmark("Caravan Yard", 66, 114, 22);
                desertDetails(b);
            }
            case "warehouse" -> warehouseDetails(b);
            case "neon_arena" -> {
                s.landmark("Reactor Bridge", 64, 64, 17);
                s.landmark("North Gallery", 64, 30, 18);
                s.landmark("South Gallery", 64, 98, 18);
                arenaDetails(b);
            }
            case "village" -> villageDetails(b);
            case "facility" -> facilityDetails(b);
            case "pirate_cove" -> {
                s.landmark("Galleon", 67, 52, 27);
                s.landmark("Fish Market", 40, 83, 19);
                s.landmark("Sailmakers", 85, 35, 16);
                pirateDetails(b);
            }
            case "sky_islands" -> {
                s.landmark("Sky Temple", 64, 64, 22);
                s.landmark("Blue Windmill", 30, 30, 19);
                s.landmark("Coral Windmill", 98, 98, 19);
                skyDetails(b);
            }
            case "candy_castle" -> {
                s.landmark("Great Hall", 64, 64, 22);
                s.landmark("Sugar Gardens", 64, 20, 23);
                s.landmark("Bakery Lane", 85, 113, 20);
                castleDetails(b);
            }
            default -> { }
        }
    }

    private static void desert(MapBuilder b) {
        // A continuous village edge replaces the empty strip behind the long tunnel.
        shop(b, 8, 8, 18, 11, 11, Materials.BRICK_TAN, Materials.TRIM_BLUE);
        shop(b, 42, 7, 15, 12, 9, Materials.SANDSTONE, Materials.TRIM_RED);
        shop(b, 71, 7, 15, 12, 12, Materials.BRICK_TAN, Materials.TRIM_BLUE);
        shop(b, 101, 8, 18, 11, 10, Materials.SANDSTONE, Materials.TRIM_RED);
        shop(b, 8, 114, 17, 7, 8, Materials.SANDSTONE, Materials.TRIM_RED);
        shop(b, 103, 114, 16, 7, 11, Materials.BRICK_TAN, Materials.TRIM_BLUE);
        for (int x : new int[]{7, 113}) {
            canopy(b, x, G, 91, 8, 12, Materials.TRIM_RED, Materials.WOOD_DARK);
            counter(b, x + 1, G, 100, 6, Materials.WOOD, Materials.SANDSTONE);
        }
        // Side courts have a purpose: clay merchants and shaded loading pads.
        for (int x : new int[]{9, 116}) {
            for (int z : new int[]{27, 34, 41}) {
                b.pillar(x, G, z, 3, 1, Materials.CORAL);
                b.disc(x, G + 3, z, 1, Materials.SANDSTONE);
                b.set(x, G + 3, z, Materials.WOOD_DARK);
            }
        }
        for (int x : new int[]{49, 74}) {
            canopy(b, x, G, 116, 7, 6, Materials.CANVAS, Materials.WOOD_DARK);
            counter(b, x + 1, G, 119, 5, Materials.WOOD, Materials.WOOD_LIGHT);
        }
        // Strong coping and recessed pilasters make the old spawn fortresses readable.
        for (int side = 0; side < 2; side++) {
            int lo = side == 0 ? 10 : 97, hi = side == 0 ? 30 : 117;
            b.trimBand(lo, 48, hi, 80, G + 8, G + 8, Materials.BRICK_TAN);
            for (int z : new int[]{48, 80}) for (int x : new int[]{lo, hi})
                b.fill(x, G, z, x, G + 9, z, Materials.BRICK_TAN);
            b.fill(lo + 2, G + 8, 48, hi - 2, G + 9, 48,
                    side == 0 ? Materials.TRIM_BLUE : Materials.TRIM_RED);
        }
        b.tiledFloor(42, 19, 86, 21, G - 1, Materials.SANDSTONE, Materials.BRICK_TAN, 7);
        b.tiledFloor(43, 113, 87, 115, G - 1, Materials.SANDSTONE, Materials.BRICK_TAN, 7);
    }

    private static void warehouse(MapBuilder b) {
        // Loading bays fill the unoccupied north wall, leaving the cross-aisle at z=16.
        for (int x : new int[]{22, 38, 80, 98}) {
            b.fill(x, G, 6, x + 10, G + 8, 7, Materials.PANEL_GREY);
            b.fill(x, G + 9, 6, x + 10, G + 10, 8, Materials.TRIM_BLUE);
            for (int yy = G + 1; yy < G + 8; yy += 2)
                b.fill(x + 1, yy, 8, x + 9, yy, 8, Materials.STEEL);
            b.fill(x, G, 9, x + 10, G, 12, Materials.CONCRETE_DARK);
            b.fill(x, G, 12, x + 10, G, 12, Materials.HAZARD);
        }
        // Two unmistakable industrial silhouettes instead of more anonymous crates.
        machine(b, 27, G, 43, 9, 6, Materials.PAINT_RED);
        machine(b, 82, G, 99, 8, 7, Materials.PAINT_BLUE);
        rack(b, 24, G, 105, 12, 4, Materials.CRATE);
        rack(b, 116, G, 32, 5, 13, Materials.PANEL_GREY);
        rack(b, 116, G, 70, 5, 13, Materials.CRATE);
        // Perimeter safety islands with space to pass around both ends.
        counter(b, 68, G, 112, 12, Materials.STEEL, Materials.PANEL_GREY);
        b.fill(68, G + 2, 113, 79, G + 4, 114, Materials.GRATE);
        b.fill(69, G + 3, 112, 72, G + 4, 112, Materials.SCREEN);
        // Upper office is now a furnished operations room, clear of both stair flights.
        counter(b, 89, G + 9, 36, 9, Materials.PLASTIC_WHITE, Materials.WOOD_LIGHT);
        b.fill(91, G + 11, 36, 94, G + 12, 36, Materials.SCREEN);
        b.fill(102, G + 9, 39, 104, G + 12, 43, Materials.PANEL_GREY);
        b.fill(91, G, 42, 100, G + 1, 44, Materials.PAINT_BLUE);
        b.fill(91, G + 2, 44, 100, G + 3, 44, Materials.PAINT_BLUE);
        // Beam spans are overhead and retain every existing stair's four-block clearance.
        for (int z : new int[]{10, 38, 90, 116}) {
            b.fill(6, G + 18, z, 121, G + 19, z + 1, Materials.STEEL);
            for (int x = 16; x <= 112; x += 24)
                b.fill(x, G + 17, z - 1, x + 5, G + 17, z + 2, Materials.LAMP);
        }
    }

    private static void arena(MapBuilder b) {
        // Stepped galleries create a stadium silhouette inside the old bare drum.
        for (int z : new int[]{24, 100}) {
            boolean north = z < 64;
            for (int row = 0; row < 3; row++) {
                int rz = z + (north ? row * 2 : -row * 2);
                b.fill(52, G, rz, 59, G + 3 - row, rz + 1, Materials.CONCRETE_DARK);
                b.fill(69, G, rz, 76, G + 3 - row, rz + 1, Materials.CONCRETE_DARK);
                b.fill(53, G + 4 - row, rz, 58, G + 4 - row, rz,
                        north ? Materials.PAINT_BLUE : Materials.PAINT_RED);
                b.fill(70, G + 4 - row, rz, 75, G + 4 - row, rz,
                        north ? Materials.PAINT_BLUE : Materials.PAINT_RED);
            }
        }
        for (int x : new int[]{25, 99}) {
            int accent = x < 64 ? Materials.NEON_CYAN : Materials.NEON_MAGENTA;
            // Long team bench/technical booth, five clear blocks behind the spawn line.
            b.fill(x, G, 56, x + 3, G + 2, 72, Materials.CONCRETE);
            b.fill(x, G + 3, 56, x, G + 7, 72, Materials.PANEL_GREY);
            b.fill(x, G + 8, 55, x + 4, G + 8, 73, Materials.CONCRETE_DARK);
            b.fill(x + 1, G + 8, 56, x + 3, G + 8, 72, accent);
            for (int z : new int[]{56, 72}) b.fill(x, G, z, x + 1, G + 8, z, Materials.STEEL);
        }
        // Pylon pairs carry the scoreboards and frame the north/south running routes.
        for (int z : new int[]{27, 99}) {
            for (int x : new int[]{47, 79}) {
                b.fill(x, G, z, x + 1, G + 14, z + 1, Materials.PANEL_GREY);
                b.fill(x, G + 3, z, x, G + 12, z,
                        z < 64 ? Materials.NEON_CYAN : Materials.NEON_MAGENTA);
            }
            b.fill(49, G + 12, z, 78, G + 16, z + 1, Materials.CONCRETE_DARK);
            b.fill(51, G + 13, z, 76, G + 15, z, Materials.SCREEN);
        }
        for (int x : new int[]{45, 81}) for (int z : new int[]{45, 81}) {
            b.fill(x, G + 5, z, x + 2, G + 6, z + 2, Materials.PANEL_GREY);
            b.fill(x, G + 7, z, x + 2, G + 7, z + 2, Materials.PAINT_BLUE);
        }
    }

    private static void village(MapBuilder b) {
        // The former empty quadrants become a bakery lane, workshop and two small gardens.
        shop(b, 39, 8, 14, 12, 10, Materials.BRICK_ORANGE, Materials.TRIM_RED);
        shop(b, 77, 108, 14, 12, 9, Materials.BRICK_TAN, Materials.TRIM_BLUE);
        shop(b, 77, 35, 8, 13, 8, Materials.BRICK_WHITE, Materials.TRIM_BLUE);
        shop(b, 8, 97, 15, 10, 8, Materials.BRICK_TAN, Materials.TRIM_RED);
        canopy(b, 43, G, 31, 9, 10, Materials.CANVAS, Materials.WOOD_DARK);
        counter(b, 44, G, 36, 7, Materials.WOOD, Materials.WOOD_LIGHT);
        planter(b, 43, G, 44, 9, 4, Materials.BRICK_WHITE);
        planter(b, 76, G, 96, 8, 4, Materials.BRICK_TAN);
        b.tiledFloor(40, 21, 52, 27, G - 1, Materials.TILE_FLOOR, Materials.TILE_GROUT, 6);
        b.tiledFloor(76, 101, 89, 106, G - 1, Materials.TILE_FLOOR, Materials.TILE_GROUT, 6);
        // Furnishing stays along the back walls, leaving each house's door-to-door axis open.
        counter(b, 20, G + 1, 84, 8, Materials.WOOD_DARK, Materials.WOOD_LIGHT);
        counter(b, 39, G + 1, 92, 7, Materials.WOOD_DARK, Materials.WOOD_LIGHT);
        counter(b, 82, G + 1, 26, 8, Materials.WOOD_DARK, Materials.WOOD_LIGHT);
        counter(b, 102, G + 1, 44, 8, Materials.WOOD_DARK, Materials.WOOD_LIGHT);
        counter(b, 18, G + 1, 46, 8, Materials.WOOD_DARK, Materials.WOOD_LIGHT);
        counter(b, 99, G + 1, 86, 9, Materials.WOOD_DARK, Materials.WOOD_LIGHT);
        // Church benches form two banks around the clear central aisle and side door.
        for (int z : new int[]{99, 108}) {
            b.fill(32, G, z, 34, G + 1, z + 1, Materials.WOOD_DARK);
            b.fill(38, G, z, 40, G + 1, z + 1, Materials.WOOD_DARK);
            b.fill(32, G + 2, z + 1, 34, G + 2, z + 1, Materials.WOOD);
            b.fill(38, G + 2, z + 1, 40, G + 2, z + 1, Materials.WOOD);
        }
    }

    private static void facility(MapBuilder b) {
        // Paint only solid rock at room edges, preserving every carved connector.
        int[][] rooms = {{12,12,46,46},{82,82,116,116},{14,84,44,114},{84,14,114,44}};
        for (int[] room : rooms) {
            for (int x = room[0] - 1; x <= room[2] + 1; x++)
                for (int y = G; y <= G + 10; y++) {
                    panelRock(b, x, y, room[1] - 1, y < G + 3 ? Materials.TRIM_BLUE : Materials.PLASTIC_WHITE);
                    panelRock(b, x, y, room[3] + 1, y < G + 3 ? Materials.TRIM_BLUE : Materials.PLASTIC_WHITE);
                }
            for (int z = room[1] - 1; z <= room[3] + 1; z++)
                for (int y = G; y <= G + 10; y++) {
                    panelRock(b, room[0] - 1, y, z, y < G + 3 ? Materials.TRIM_BLUE : Materials.PLASTIC_WHITE);
                    panelRock(b, room[2] + 1, y, z, y < G + 3 ? Materials.TRIM_BLUE : Materials.PLASTIC_WHITE);
                }
        }
        // Medical wing: beds, imaging machines and equipment cabinets fill the huge west room.
        medicalBed(b, 17, 88);
        medicalBed(b, 35, 106);
        machine(b, 35, G, 86, 7, 5, Materials.PLASTIC_WHITE);
        cabinet(b, 15, G, 103, 5, 9, Materials.PAINT_BLUE);
        counter(b, 24, G, 111, 10, Materials.PLASTIC_WHITE, Materials.STEEL);
        // Test workshop: two distinct working islands with a generous cross aisle.
        machine(b, 105, G, 17, 7, 8, Materials.PANEL_GREY);
        counter(b, 85, G, 37, 9, Materials.PLASTIC_WHITE, Materials.STEEL);
        counter(b, 98, G, 37, 12, Materials.PLASTIC_WHITE, Materials.STEEL);
        b.fill(89, G + 2, 38, 91, G + 3, 39, Materials.SCREEN);
        b.fill(103, G + 2, 38, 106, G + 3, 39, Materials.SCREEN);
        cabinet(b, 13, G, 14, 2, 17, Materials.PAINT_BLUE);
        cabinet(b, 114, G, 85, 2, 12, Materials.PAINT_RED);
        b.fill(38, G, 34, 43, G + 2, 35, Materials.PLASTIC_WHITE);
        b.fill(85, G, 109, 90, G + 2, 111, Materials.PLASTIC_WHITE);
        // Glass coolant tanks are solid cover with capped bases, not floor clutter.
        for (int[] p : new int[][]{{56,91},{69,103}}) {
            int x = p[0], z = p[1];
            b.fill(x, G, z, x + 3, G + 1, z + 2, Materials.STEEL);
            b.fill(x + 1, G + 2, z, x + 2, G + 6, z + 2, Materials.NEON_CYAN);
            b.fill(x, G + 7, z, x + 3, G + 7, z + 2, Materials.STEEL);
        }
    }

    private static void pirate(MapBuilder b) {
        // Working harbours give both otherwise empty lagoon flanks a readable destination.
        b.deck(34, 76, 47, 90, G);
        canopy(b, 35, G + 1, 77, 11, 11, Materials.CANVAS, Materials.WOOD_DARK);
        counter(b, 36, G + 1, 85, 8, Materials.WOOD_DARK, Materials.WOOD_LIGHT);
        b.fill(35, G, 77, 35, G - 1, 88, Materials.WOOD_DARK);
        b.deck(78, 30, 92, 40, G);
        canopy(b, 80, G + 1, 31, 11, 8, Materials.CORAL, Materials.WOOD_DARK);
        counter(b, 82, G + 1, 36, 7, Materials.WOOD_DARK, Materials.WOOD_LIGHT);
        // Angular rocks are large enough to interrupt lagoon sightlines without closing a dock.
        rock(b, 18, G, 86, 8, 9, 7);
        rock(b, 99, G, 17, 8, 9, 8);
        rock(b, 9, G, 49, 7, 9, 5);
        rock(b, 110, G, 71, 7, 7, 6);
        rowboat(b, 13, 73);
        rowboat(b, 96, 45);
        // Cannon carriages belong to the ship's wide side decks, away from the central hatch.
        for (int x : new int[]{54, 68}) for (int z : new int[]{46, 57}) {
            b.fill(x, G + 7, z, x + 2, G + 7, z + 1, Materials.WOOD_DARK);
            b.fill(x + 1, G + 8, z - 1, x + 1, G + 8, z + 2, Materials.STEEL);
        }
        for (int x : new int[]{36, 42}) {
            b.barrel(x, G + 1, 79, Materials.WOOD_DARK);
            b.set(x, G + 4, 79, Materials.WOOD_LIGHT);
        }
    }

    private static void sky(MapBuilder b) {
        // Fragmented temple walls create pockets of cover on the four plateau diagonals.
        for (int x : new int[]{52, 72}) for (int z : new int[]{52, 72}) {
            b.fill(x, 27, z, x + 4, 27, z + 4, Materials.STONE_LIGHT);
            b.fill(x, 28, z, x + 4, 30, z, Materials.STONE_LIGHT);
            b.fill(x, 28, z, x, 33, z + 3, Materials.STONE_LIGHT);
            b.fill(x, 34, z, x + 4, 34, z, Materials.MARBLE);
            b.fill(x + 4, 28, z, x + 4, 33, z, Materials.STONE_LIGHT);
            b.set(x + 2, 35, z, Materials.GOLD);
        }
        // Small stone gardens on the cardinal lookout islands, outside their door approaches.
        planter(b, 17, 19, 61, 3, 7, Materials.STONE_LIGHT);
        planter(b, 109, 19, 61, 3, 7, Materials.STONE_LIGHT);
        b.fill(19, 19, 57, 23, 20, 58, Materials.STONE_LIGHT);
        b.fill(105, 19, 70, 109, 20, 71, Materials.STONE_LIGHT);
        // Home cottage roof chimneys and corner trims survive without altering spawn floors.
        for (int[] home : new int[][]{{25,26},{93,94}}) {
            int x = home[0], z = home[1];
            b.fill(x + 1, 26, z + 6, x + 2, 32, z + 7, Materials.BRICK_WHITE);
            b.fill(x, 33, z + 5, x + 3, 33, z + 8, Materials.STONE_LIGHT);
            b.trimBand(x, z, x + 11, z + 9, 24, 24, Materials.FRAME_LIGHT);
        }
        // Outpost tents now have small stone terraces that follow the existing island cap.
        for (int[] p : new int[][]{{30,98},{98,30}}) {
            int x = p[0], z = p[1];
            b.fill(x - 7, 18, z - 7, x - 3, 18, z - 3, Materials.STONE_LIGHT);
            b.fill(x - 7, 19, z - 7, x - 3, 20, z - 7, Materials.STONE_LIGHT);
            b.fill(x - 7, 19, z - 6, x - 7, 21, z - 3, Materials.STONE_LIGHT);
            b.set(x - 7, 22, z - 7, Materials.LANTERN);
        }
    }

    private static void castle(MapBuilder b) {
        // Four small confectionery buildings turn the broad outer lawn into themed streets.
        shop(b, 42, 8, 14, 12, 9, Materials.CANDY_PINK, Materials.TRIM_RED);
        shop(b, 75, 108, 16, 12, 10, Materials.MINT, Materials.TRIM_BLUE);
        shop(b, 8, 36, 14, 12, 9, Materials.SUNNY, Materials.TRIM_BLUE);
        shop(b, 106, 81, 14, 13, 9, Materials.SKY_BLUE, Materials.TRIM_RED);
        canopy(b, 70, G, 33, 8, 8, Materials.CANDY_PINK, Materials.WOOD_DARK);
        counter(b, 71, G, 37, 6, Materials.WOOD_DARK, Materials.WOOD_LIGHT);
        canopy(b, 48, G, 90, 9, 7, Materials.MINT, Materials.WOOD_DARK);
        counter(b, 49, G, 93, 6, Materials.WOOD_DARK, Materials.WOOD_LIGHT);
        planter(b, 73, G, 17, 14, 5, Materials.STONE_LIGHT);
        planter(b, 43, G, 108, 12, 5, Materials.STONE_LIGHT);
        // Pilasters and a continuous pastel cornice give the keep a clear architectural scale.
        for (int x : new int[]{44, 56, 72, 84}) {
            for (int z : new int[]{44, 84}) {
                b.fill(x, G + 6, z, x, G + 9, z, Materials.CANDY_PINK);
                b.fill(x, G + 12, z, x, G + 16, z, Materials.CANDY_PINK);
            }
        }
        b.trimBand(44, 44, 84, 84, G + 9, G + 9, Materials.CANDY_PINK);
        b.trimBand(44, 44, 84, 84, G + 16, G + 16, Materials.SKY_BLUE);
        // Upstairs library and reading tables occupy the previously empty upper great hall.
        rack(b, 48, G + 11, 57, 11, 3, Materials.WOOD_DARK, 5);
        rack(b, 47, G + 11, 66, 3, 9, Materials.WOOD_DARK, 5);
        counter(b, 63, G + 11, 55, 9, Materials.WOOD_DARK, Materials.WOOD_LIGHT);
        counter(b, 63, G + 11, 70, 9, Materials.WOOD_DARK, Materials.WOOD_LIGHT);
        b.fill(77, G + 11, 59, 81, G + 12, 62, Materials.CANDY_PINK);
        b.fill(80, G + 13, 59, 81, G + 15, 62, Materials.CANDY_PINK);
        b.fill(46, G, 57, 48, G + 3, 59, Materials.WOOD_DARK);
        b.fill(81, G, 69, 83, G + 3, 72, Materials.WOOD_DARK);
    }

    /** Small open-ended shop with a framed facade, shaded window and usable interior. */
    private static void shop(MapBuilder b, int x, int z, int width, int depth, int height,
                             int wall, int trim) {
        int x1 = x + width - 1, z1 = z + depth - 1, top = G + height;
        b.fill(x, G - 1, z, x1, G - 1, z1, Materials.CONCRETE);
        b.walls(x, G, z, x1, top - 1, z1, wall);
        b.fill(x, top, z, x1, top, z1, Materials.CONCRETE);
        b.trimBand(x, z, x1, z1, top + 1, top + 1, trim);
        b.quoins(x, z, x1, z1, G, top - 1, Materials.FRAME_LIGHT);
        // Both ends open: these little buildings add routes as well as cover.
        b.doorway(x + width / 2, G, z, 1, 3, 4);
        b.doorway(x + width / 2, G, z1, 1, 3, 4);
        b.framedWindow(x, G + 2, z + depth / 2, 0, 3, 2, true);
        b.framedWindow(x1, G + 2, z + depth / 2, 0, 3, 2, true);
        // Both street faces are designed: perimeter buildings are usually seen from the south.
        // A side window leaves the central through-door and its full frame clear.
        if (width >= 14) {
            b.framedWindow(x + 3, G + 2, z, 1, 3, 2, true);
            b.framedWindow(x + 3, G + 2, z1, 1, 3, 2, true);
        }
        if (height >= 10) b.framedWindow(x1 - 3, G + 6, z1, 1, 3, 2, true);
        b.fill(x + 1, G + 5, z - 1, x1 - 1, G + 5, z - 1, trim);
        b.fill(x + 1, G + 5, z1 + 1, x1 - 1, G + 5, z1 + 1, trim);
        for (int px = x + 2; px < x1; px += 3)
            b.set(px, G + 5, z1 + 1, Materials.FRAME_LIGHT);
        b.fill(x + 1, G + 6, z, x1 - 1, G + 6, z, trim);
        b.fill(x + 1, G, z + depth - 4, x + 2, G + 1, z + depth - 2, Materials.WOOD);
        b.fill(x1 - 2, G, z + 2, x1 - 1, G + 3, z + 3, Materials.WOOD_DARK);
        b.fill(x + 2, top + 1, z + 2, x + 4, top + 3, z + 4, Materials.SANDSTONE);
        b.set(x + 3, top + 4, z + 3, Materials.STEEL);
    }

    private static void canopy(MapBuilder b, int x, int y, int z, int width, int depth,
                               int cloth, int post) {
        for (int px : new int[]{x, x + width - 1}) for (int pz : new int[]{z, z + depth - 1})
            b.fill(px, y, pz, px, y + 5, pz, post);
        b.fill(x, y + 6, z, x + width - 1, y + 6, z + depth - 1, cloth);
        for (int px = x + 1; px < x + width - 1; px += 3)
            b.fill(px, y + 6, z, px, y + 6, z + depth - 1, Materials.CANVAS);
    }

    private static void counter(MapBuilder b, int x, int y, int z, int width, int body, int top) {
        b.fill(x, y, z, x + width - 1, y + 1, z + 1, body);
        b.fill(x, y + 2, z, x + width - 1, y + 2, z + 2, top);
    }

    private static void planter(MapBuilder b, int x, int y, int z, int width, int depth, int rim) {
        b.fill(x, y, z, x + width - 1, y + 1, z + depth - 1, rim);
        b.fill(x + 1, y + 2, z + 1, x + width - 2, y + 2, z + depth - 2, Materials.LEAVES);
    }

    private static void rack(MapBuilder b, int x, int y, int z, int width, int depth, int cargo) {
        rack(b, x, y, z, width, depth, cargo, 7);
    }

    private static void rack(MapBuilder b, int x, int y, int z, int width, int depth, int cargo,
                             int height) {
        int middle = (height + 1) / 2;
        for (int px : new int[]{x, x + width - 1}) for (int pz : new int[]{z, z + depth - 1})
            b.fill(px, y, pz, px, y + height, pz, Materials.STEEL);
        for (int level : new int[]{0, middle, height})
            b.fill(x, y + level, z, x + width - 1, y + level, z + depth - 1, Materials.STEEL);
        if (width >= depth) {
            for (int px = x + 1; px < x + width - 2; px += 4) {
                b.fill(px, y + 1, z + 1, px + 1, y + middle - 1, z + depth - 2, cargo);
                b.fill(px, y + middle + 1, z + 1, px + 2, y + height - 1, z + depth - 2, cargo);
            }
        } else {
            for (int pz = z + 1; pz < z + depth - 2; pz += 4) {
                b.fill(x + 1, y + 1, pz, x + width - 2, y + middle - 1, pz + 1, cargo);
                b.fill(x + 1, y + middle + 1, pz, x + width - 2, y + height - 1, pz + 2, cargo);
            }
        }
    }

    private static void machine(MapBuilder b, int x, int y, int z, int width, int depth, int body) {
        b.fill(x, y, z, x + width - 1, y + 1, z + depth - 1, Materials.STEEL);
        b.fill(x + 1, y + 2, z + 1, x + width - 2, y + 4, z + depth - 2, body);
        b.fill(x + 2, y + 5, z + 2, x + width - 3, y + 5, z + depth - 3, Materials.PANEL_GREY);
        b.fill(x + 2, y + 3, z, x + width - 3, y + 4, z, Materials.SCREEN);
        b.fill(x, y + 2, z + depth - 2, x, y + 5, z + depth - 2, Materials.STEEL);
        b.set(x, y + 6, z + depth - 2, Materials.EMERGENCY);
    }

    private static void cabinet(MapBuilder b, int x, int y, int z, int width, int depth, int color) {
        b.fill(x, y, z, x + width - 1, y + 5, z + depth - 1, color);
        b.fill(x, y + 6, z, x + width - 1, y + 6, z + depth - 1, Materials.STEEL);
        for (int pz = z; pz < z + depth; pz += 3)
            b.fill(x + width - 1, y + 2, pz, x + width - 1, y + 3, pz, Materials.PANEL_GREY);
    }

    private static void panelRock(MapBuilder b, int x, int y, int z, int material) {
        if (b.world().get(x, y, z) == Materials.STONE) b.set(x, y, z, material);
    }

    private static void medicalBed(MapBuilder b, int x, int z) {
        b.fill(x, G, z, x + 5, G, z + 3, Materials.STEEL);
        b.fill(x, G + 1, z, x + 5, G + 1, z + 3, Materials.PLASTIC_WHITE);
        b.fill(x + 1, G + 2, z, x + 5, G + 2, z + 3, Materials.PAINT_BLUE);
        b.fill(x, G + 2, z, x, G + 3, z + 3, Materials.PLASTIC_WHITE);
        b.fill(x + 6, G, z, x + 7, G + 4, z + 1, Materials.PANEL_GREY);
        b.set(x + 6, G + 4, z - 1, Materials.SCREEN);
    }

    private static void rock(MapBuilder b, int x, int y, int z, int width, int depth, int height) {
        b.fill(x, y, z, x + width - 1, y + height / 2, z + depth - 1, Materials.STONE_LIGHT);
        b.fill(x + 1, y + height / 2 + 1, z + 1, x + width - 2, y + height - 1,
                z + depth - 2, Materials.SANDSTONE);
        b.fill(x + 2, y + height, z + 2, x + width - 3, y + height, z + depth - 3, Materials.GRASS_BRIGHT);
        b.palm(x + width / 2, y + height + 1, z + depth / 2, 6);
    }

    private static void rowboat(MapBuilder b, int x, int z) {
        b.fill(x + 1, G, z, x + 9, G, z + 3, Materials.WOOD_DARK);
        b.fill(x, G + 1, z, x + 10, G + 1, z, Materials.WOOD);
        b.fill(x, G + 1, z + 3, x + 10, G + 1, z + 3, Materials.WOOD);
        b.fill(x, G, z + 1, x, G + 2, z + 2, Materials.WOOD);
        b.fill(x + 10, G, z + 1, x + 10, G + 2, z + 2, Materials.WOOD);
        for (int px : new int[]{x + 3, x + 7}) b.fill(px, G + 1, z, px, G + 1, z + 3, Materials.WOOD_LIGHT);
    }

    private static void desertDetails(MapBuilder b) {
        MapScenery s = b.scenery();
        for (int[] shop : new int[][]{{8,8,18},{42,7,15},{71,7,15},{101,8,18},{8,114,17},{103,114,16}}) {
            DecorRecipes.awning(s, shop[0] + 1, G + 5.1f, shop[1] - 1.8f, shop[2] - 2, Materials.TRIM_RED);
            DecorRecipes.sign(s, shop[0] + 3, G + 7, shop[1] - .15f, "BAZAAR", .22f, Materials.WOOD_DARK);
            shutters(s, shop[0] + 1, G + 7, shop[1] - .15f, shop[2] - 2, Materials.TRIM_BLUE);
        }
        for (int x : new int[]{50, 75}) goods(s, x, G + 3, 119, Materials.SUNNY);
        for (int x : new int[]{10, 115}) goods(s, x, G + 3, 100, Materials.CORAL);
    }

    private static void warehouseDetails(MapBuilder b) {
        MapScenery s = b.scenery();
        for (int[] machine : new int[][]{{27,43},{82,99}}) {
            DecorRecipes.vent(s, machine[0] + 2, G + 2.3f, machine[1] - .12f);
            DecorRecipes.pipe(s, machine[0] + 1, G + 5.2f, machine[1] + 1, 5, true, Materials.COPPER);
        }
        for (int x : new int[]{22,38,80,98})
            DecorRecipes.sign(s, x + 2, G + 10.4f, 5.85f, "BAY", .3f, Materials.TRIM_BLUE);
        // Wall panels, floor bay markings and container corner castings use clean flat tones.
        for (int x = 18; x < 110; x += 24) for (int z : new int[]{30,56,82}) {
            s.box(x + .1f, G + 1, z - .07f, .5f, 3.5f, .15f, Materials.FRAME_LIGHT);
            s.box(x + 12.4f, G + 1, z - .07f, .5f, 3.5f, .15f, Materials.FRAME_LIGHT);
            s.box(x + 2, G + .02f, z - 3, 10, .03f, .18f, Materials.HAZARD);
        }
        for (int x : new int[]{90,94}) deskItems(s, x, G + 12, 37);
    }

    private static void arenaDetails(MapBuilder b) {
        MapScenery s = b.scenery();
        for (int z : new int[]{27,99}) {
            DecorRecipes.sign(s, 57, G + 13, z - .16f, "ARENA", .42f, Materials.CONCRETE_DARK);
            s.box(49, G + 11.7f, z - .12f, 30, .2f, .2f,
                    z < 64 ? Materials.NEON_CYAN : Materials.NEON_MAGENTA);
        }
        for (int x : new int[]{25,99}) {
            for (int z = 58; z <= 70; z += 4)
                s.box(x + 1.5f, G + 3.05f, z, 1.2f, .3f, 1.6f, Materials.RUBBER);
        }
        // Inlaid lane dashes lead around the pit, with the hazardous centre kept visually quiet.
        for (int x = 38; x <= 88; x += 5) for (int z : new int[]{35,93})
            s.box(x, G + .02f, z, 2.5f, .03f, .18f, Materials.FRAME_LIGHT);
        for (int x : new int[]{45,81}) for (int z : new int[]{45,81})
            DecorRecipes.vent(s, x + .6f, G + 5.4f, z - .12f);
    }

    private static void villageDetails(MapBuilder b) {
        MapScenery s = b.scenery();
        int[][] shops = {{39,8,14},{77,108,14},{77,35,8},{8,97,15}};
        String[] names = {"BAKERY","WORKSHOP","GARDEN","CAFE"};
        for (int i = 0; i < shops.length; i++) {
            int[] p = shops[i];
            DecorRecipes.awning(s, p[0] + 1, G + 5.2f, p[1] - 1.4f, p[2] - 2, Materials.TRIM_RED);
            DecorRecipes.sign(s, p[0] + 2, G + 7, p[1] - .15f, names[i], .19f, Materials.WOOD_DARK);
        }
        for (int[] p : new int[][]{{20,84},{39,92},{82,26},{102,44},{18,46},{99,86}})
            goods(s, p[0] + 1, G + 4.1f, p[1] + .2f, Materials.SUNNY);
        goods(s, 44, G + 3.1f, 36, Materials.CORAL);
        for (int x : new int[]{43,77}) DecorRecipes.flowers(s, x + 1, G + 3, x == 43 ? 45 : 97, Materials.CANDY_PINK);
    }

    private static void facilityDetails(MapBuilder b) {
        MapScenery s = b.scenery();
        DecorRecipes.sign(s, 18, 10, 83.85f, "MEDICAL", .3f, Materials.TRIM_BLUE);
        DecorRecipes.sign(s, 89, 10, 13.85f, "TEST LAB", .27f, Materials.TRIM_BLUE);
        for (int[] p : new int[][]{{17,88},{35,106}}) {
            s.box(p[0] + .15f, G + 2.04f, p[1] + .5f, .75f, .25f, 2.8f, Materials.FRAME_LIGHT);
            s.box(p[0] + 6.25f, G + 4.2f, p[1] - .05f, .45f, .12f, .08f, Materials.NEON_CYAN);
        }
        for (int x : new int[]{87,100,106}) deskItems(s, x, G + 3.1f, 37);
        for (int z = 17; z <= 29; z += 3) DecorRecipes.vent(s, 13.1f, G + 4, z - .12f);
        for (int x = 18; x <= 38; x += 5)
            s.box(x, G + .02f, 84, 2.7f, .02f, .2f, Materials.TRIM_BLUE);
    }

    private static void pirateDetails(MapBuilder b) {
        MapScenery s = b.scenery();
        DecorRecipes.sign(s, 37, G + 6, 76.85f, "FISH", .3f, Materials.ROOF_TEAL);
        DecorRecipes.sign(s, 81, G + 6, 30.85f, "SAILS", .28f, Materials.WOOD_DARK);
        goods(s, 37, G + 4.1f, 85.3f, Materials.BARREL_TEAL);
        goods(s, 83, G + 4.1f, 36.2f, Materials.CANVAS);
        for (int[] hut : new int[][]{{16,20},{30,14},{14,34},{100,96},{108,108},{92,104}}) {
            int x = hut[0], z = hut[1];
            shutters(s, x + 1, G + 4.5f, z - .12f, 7, Materials.WOOD_LIGHT);
            s.box(x - .12f, G, z, .18f, 5.1f, .18f, Materials.WOOD_DARK);
            s.box(x + 9, G, z, .18f, 5.1f, .18f, Materials.WOOD_DARK);
        }
        for (int x : new int[]{58,70,80}) {
            s.box(x, G + 9, 44.7f, 1.7f, .15f, .2f, Materials.GOLD);
            s.box(x, G + 9, 58.8f, 1.7f, .15f, .2f, Materials.GOLD);
        }
    }

    private static void skyDetails(MapBuilder b) {
        MapScenery s = b.scenery();
        for (int[] home : new int[][]{{25,26},{93,94}}) {
            int x = home[0], z = home[1];
            DecorRecipes.awning(s, x + 2, 23.5f, z - 1, 7, Materials.ROOF_TEAL);
            shutters(s, x + 1, 24.5f, z - .15f, 9, Materials.FRAME_LIGHT);
            s.box(x, 19, z - .1f, .3f, 5.5f, .2f, Materials.WOOD_DARK);
            s.box(x + 10.7f, 19, z - .1f, .3f, 5.5f, .2f, Materials.WOOD_DARK);
        }
        for (int x : new int[]{52,72}) for (int z : new int[]{52,72}) {
            s.box(x + .1f, 30, z - .1f, 4.8f, .25f, .15f, Materials.ROOF_TEAL);
            s.box(x + .15f, 28.2f, z - .1f, .3f, 1.3f, .15f, Materials.GOLD);
        }
        DecorRecipes.flowers(s, 17, 22, 63, Materials.SUNNY);
        DecorRecipes.flowers(s, 109, 22, 63, Materials.CANDY_PINK);
    }

    private static void castleDetails(MapBuilder b) {
        MapScenery s = b.scenery();
        for (int[] p : new int[][]{{42,8,14},{75,108,16},{8,36,14},{106,81,14}}) {
            DecorRecipes.awning(s, p[0] + 1, G + 5.1f, p[1] - 1.4f, p[2] - 2, Materials.CANDY_PINK);
            DecorRecipes.sign(s, p[0] + 3, G + 7, p[1] - .15f, "SWEETS", .24f, Materials.ROOF_TEAL);
        }
        // Book spines are shallow attachments to the real upper-hall shelving.
        int[] colors = {Materials.CANDY_PINK, Materials.ROOF_TEAL, Materials.SUNNY, Materials.GRAPE};
        for (int i = 0; i < 19; i++) for (int y : new int[]{G + 12, G + 15})
            s.box(49 + i * .45f, y, 56.92f, .3f, 1.6f, .1f, colors[i % colors.length]);
        for (int x : new int[]{64,68}) for (int z : new int[]{55,70}) goods(s, x, G + 14.1f, z, Materials.CANDY_PINK);
        goods(s, 72, G + 3.1f, 37, Materials.SUNNY);
        goods(s, 49, G + 3.1f, 93, Materials.CANDY_PINK);
        DecorRecipes.flowers(s, 74, G + 3, 18, Materials.CORAL);
        DecorRecipes.flowers(s, 44, G + 3, 109, Materials.SUNNY);
    }

    /** Upper facade shutters: low frequency colour blocks with shallow slats. */
    private static void shutters(MapScenery s, float x, float y, float z, int width, int color) {
        for (float px : new float[]{x, x + width - 2}) {
            s.box(px, y, z, 1.5f, 1.8f, .15f, color);
            for (int i = 0; i < 4; i++) s.box(px + .1f, y + .2f + i * .4f, z - .06f,
                    1.3f, .08f, .06f, Materials.WOOD_DARK);
        }
    }

    /** Display trays sit on solid counters, with deliberately chunky readable contents. */
    private static void goods(MapScenery s, float x, float y, float z, int color) {
        for (int i = 0; i < 3; i++) {
            s.box(x + i * 1.5f, y, z, 1.2f, .15f, 1.5f, Materials.WOOD_DARK);
            s.box(x + .12f + i * 1.5f, y + .15f, z + .12f, .95f, .45f, 1.2f, color);
        }
    }

    private static void deskItems(MapScenery s, float x, float y, float z) {
        s.box(x, y, z + .4f, 1.3f, .13f, .8f, Materials.RUBBER);
        s.box(x + .5f, y, z + 1.4f, .18f, .8f, .2f, Materials.STEEL);
        s.box(x, y + .5f, z + 1.5f, 1.3f, .9f, .13f, Materials.CONCRETE_DARK);
        s.box(x + .1f, y + .6f, z + 1.46f, 1.1f, .65f, .03f, Materials.SCREEN);
    }
}
