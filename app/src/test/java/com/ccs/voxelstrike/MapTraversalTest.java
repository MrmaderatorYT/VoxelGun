package com.ccs.voxelstrike;

import com.ccs.voxelstrike.game.Combatant;
import com.ccs.voxelstrike.game.Physics;
import com.ccs.voxelstrike.map.GameMap;
import com.ccs.voxelstrike.map.MapBuilder;
import com.ccs.voxelstrike.map.Maps;
import com.ccs.voxelstrike.map.StairFlight;
import com.ccs.voxelstrike.math.Vec3;
import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.voxel.VoxelWorld;
import org.junit.Test;
import static org.junit.Assert.*;

/** Validates the finished, dressed geometry rather than just the stair primitive. */
public class MapTraversalTest {
    @Test public void everyAuthoredTreadHasStandingAndJumpingHeadroom() {
        int checked = 0;
        for (int index = 0; index < Maps.count(); index++) {
            GameMap map = Maps.create(index);
            VoxelWorld world = map.build(2026L);
            try {
                for (StairFlight f : map.stairFlights()) {
                    for (int i = 0; i < f.steps; i++) {
                        for (int lane = 0; lane < f.width; lane++) {
                            float x = f.xAt(i) + .5f + (f.direction > 1 ? lane : 0);
                            float z = f.zAt(i) + .5f + (f.direction <= 1 ? lane : 0);
                            assertTrue(label(map, f), world.solid((int)x, f.feetAt(i) - 1, (int)z));
                            assertFalse(label(map, f), world.boxCollides(x, f.feetAt(i), z,
                                    Combatant.RADIUS, Combatant.HEIGHT + 1.2f));
                            checked++;
                        }
                    }
                }
            } finally { world.release(); }
        }
        assertTrue("Validation must include a broad sample of authored stair treads", checked > 500);
    }

    @Test public void classicFlightsCanBeClimbedAtWalkingSpeedWithoutJumpSpam() {
        int checked = 0;
        for (int index : new int[]{0, 1, 3, 4, 5, 6, 7}) {
            GameMap map = Maps.create(index);
            VoxelWorld world = map.build(2026L);
            try {
                assertFalse(map.id() + " lost all authored stairs", map.stairFlights().isEmpty());
                for (StairFlight f : map.stairFlights()) {
                    walkFlight(map, world, f, false);
                    checked++;
                }
            } finally { world.release(); }
        }
        assertTrue(checked > 25);
    }

    @Test public void repairedRoofDeckAndWallwalkRoutesJoinRealLandings() {
        String[] maps = {"dust_yard", "warehouse", "village", "facility", "pirate_cove", "candy_castle",
                "harbor_yard", "metro_junction", "canyon_outpost", "reactor_core", "rooftop_district", "orbital_dock",
                "ice_citadel"};
        for (String id : maps) {
            GameMap map = Maps.create(Maps.indexOf(id));
            VoxelWorld world = map.build(2026L);
            try {
                assertEquals("Unknown fixture map", id, map.id());
                assertFalse(id + " lost its access routes", map.stairFlights().isEmpty());
                for (StairFlight f : map.stairFlights()) walkFlight(map, world, f, true);
            } finally { world.release(); }
        }
    }

    @Test public void lateCeilingsAreCutButRemovedRoadStairsAreNotResurrected() {
        VoxelWorld world = new VoxelWorld(24, 20, 16);
        try {
            MapBuilder b = new MapBuilder(world, 1L);
            b.fill(0, 0, 0, 23, 2, 15, Materials.STONE);
            b.stairs(4, 3, 4, 0, 5, 3, Materials.STONE);
            b.fill(4, 6, 4, 8, 6, 6, Materials.STONE);
            b.finishTraversal();
            assertFalse(world.boxCollides(6.5f, 6f, 5.5f, .34f, 1.8f));
            b.clear(4, 3, 4, 4, 3, 6);
            b.finishTraversal();
            assertTrue(b.stairFlights().isEmpty());
            assertFalse(world.solid(4, 3, 5));
        } finally { world.release(); }
    }

    @Test public void newLayoutsExposeDistinctCombatLandmarks() {
        String[][] fixtures = {{"harbor_yard", "Crane", "Cargo Maze"},
                {"metro_junction", "Concourse", "Blue Platform"},
                {"canyon_outpost", "North Fort", "Dry Gully"},
                {"reactor_core", "Coolant Bay", "Control Bay"},
                {"bamboo_temple", "North Shrine", "Garden Bridges"},
                {"bunker_13", "Blast Hall", "Service Tunnel"}};
        for (String[] fixture : fixtures) {
            GameMap map = Maps.create(Maps.indexOf(fixture[0]));
            VoxelWorld world = map.build(2026L);
            try {
                assertEquals(fixture[0], map.id());
                for (int i = 1; i < fixture.length; i++) {
                    String wanted = fixture[i];
                    assertTrue(fixture[0] + " missing " + wanted,
                            map.scenery().landmarks().stream().anyMatch(l -> l.name.equals(wanted)));
                }
            } finally { world.release(); }
        }
    }

    @Test public void switchbackTowersCanBeClimbedAllTheWayFromTheirDoors() {
        for (String id : new String[]{"village", "pirate_cove"}) {
            GameMap map = Maps.create(Maps.indexOf(id));
            VoxelWorld world = map.build(2026L);
            try {
                boolean village = id.equals("village");
                float left = village ? 45.5f : 61.5f;
                float right = village ? 51.5f : 67.5f;
                float north = village ? 100 : 98;
                float south = village ? 105 : 103;
                Vec3 p = new Vec3(left, 4, north), v = new Vec3();
                int levels = village ? 5 : 4;
                for (int level = 0; level < levels; level++) {
                    boolean east = level % 2 == 0;
                    float end = east ? left + 3 : right - (level == levels - 1 && !village ? 4 : 3);
                    walkTo(world, p, v, end, east ? north : south, id);
                    if (level + 1 < levels) {
                        // Turn into the next flight's FIRST tread, not its two-block-high second.
                        float turning = east ? right : left;
                        walkTo(world, p, v, turning, east ? north : south, id);
                        walkTo(world, p, v, turning, east ? south : north, id);
                        walkTo(world, p, v, east ? right : left, east ? south : north, id);
                    }
                }
                assertEquals(id + " failed to reach lookout", village ? 23f : 20f, p.y, .05f);
            } finally { world.release(); }
        }
    }

    private static void walkTo(VoxelWorld world, Vec3 p, Vec3 v, float x, float z, String label) {
        for (int tick = 0; tick < 700; tick++) {
            float dx = x - p.x, dz = z - p.z;
            float distance = (float)Math.sqrt(dx * dx + dz * dz);
            if (distance < .04f) return;
            float speed = Math.min(3.8f, distance * 120);
            v.x = dx / distance * speed; v.z = dz / distance * speed;
            v.y += Physics.GRAVITY / 120f;
            Physics.move(world, p, v, 1f / 120f, Combatant.RADIUS, Combatant.HEIGHT, true);
        }
        fail(label + " switchback blocked at " + p.x + "," + p.y + "," + p.z);
    }

    private static void walkFlight(GameMap map, VoxelWorld world, StairFlight f, boolean landing) {
        int dx = f.direction == 0 ? 1 : f.direction == 1 ? -1 : 0;
        int dz = f.direction == 2 ? 1 : f.direction == 3 ? -1 : 0;
        Vec3 p = new Vec3(f.centerX(0), f.feetAt(0), f.centerZ(0));
        Vec3 v = new Vec3();
        float tx = f.centerX(f.steps - 1) + (landing ? dx : 0);
        float tz = f.centerZ(f.steps - 1) + (landing ? dz : 0);
        for (int tick = 0; tick < (f.steps + 3) * 50; tick++) {
            if ((tx - p.x) * dx + (tz - p.z) * dz <= .04f) break;
            v.x = dx * 3.8f; v.z = dz * 3.8f; v.y += Physics.GRAVITY / 120f;
            Physics.move(world, p, v, 1f / 120f, Combatant.RADIUS, Combatant.HEIGHT, true);
            assertFalse(label(map, f) + " body penetrated geometry",
                    world.boxCollides(p.x, p.y, p.z, Combatant.RADIUS, Combatant.HEIGHT));
        }
        assertEquals(label(map, f) + " blocked X", tx, p.x, .07f);
        assertEquals(label(map, f) + " blocked Z", tz, p.z, .07f);
        assertTrue(label(map, f) + " destination is below the staircase: " + p.y,
                p.y >= f.feetAt(f.steps - 1) - 1.01f);
        assertTrue(label(map, f) + " ends in midair",
                Physics.onGround(world, p, Combatant.RADIUS, .12f));
    }

    private static String label(GameMap map, StairFlight f) {
        return map.id() + " stairs " + f.x + "," + f.y + "," + f.z + " dir=" + f.direction;
    }
}
