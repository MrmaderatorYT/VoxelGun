package com.ccs.voxelstrike;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.ccs.voxelstrike.game.Combatant;
import com.ccs.voxelstrike.game.Physics;
import com.ccs.voxelstrike.map.GameMap;
import com.ccs.voxelstrike.map.Maps;
import com.ccs.voxelstrike.map.StairFlight;
import com.ccs.voxelstrike.math.Vec3;
import com.ccs.voxelstrike.voxel.NativeBridge;
import com.ccs.voxelstrike.voxel.VoxelWorld;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Runs authored access routes with Android's loaded native world and collision mirror. */
@RunWith(AndroidJUnit4.class)
public class MapTraversalInstrumentedTest {
    @Test public void classicStairsAndLandingsRemainTraversableOnAndroid() {
        check("dust_yard", "warehouse", "village", "pirate_cove", "candy_castle");
    }

    @Test public void expansionRoofAndPlatformRoutesRemainTraversableOnAndroid() {
        check("metro_junction", "rooftop_district", "reactor_core", "ice_citadel");
    }

    private void check(String... ids) {
        assertTrue("Native library must actually be loaded", NativeBridge.available());
        for (String id : ids) {
            GameMap map = Maps.create(Maps.indexOf(id));
            VoxelWorld world = map.build(2026L);
            try {
                assertFalse(id + " has no stairs", map.stairFlights().isEmpty());
                for (StairFlight f : map.stairFlights()) {
                    int dx = f.direction == 0 ? 1 : f.direction == 1 ? -1 : 0;
                    int dz = f.direction == 2 ? 1 : f.direction == 3 ? -1 : 0;
                    Vec3 p = new Vec3(f.centerX(0), f.feetAt(0), f.centerZ(0));
                    Vec3 v = new Vec3();
                    float tx = f.centerX(f.steps - 1) + dx;
                    float tz = f.centerZ(f.steps - 1) + dz;
                    String route = id + " " + f.x + "," + f.y + "," + f.z;
                    for (int tick = 0; tick < (f.steps + 3) * 50; tick++) {
                        if ((tx - p.x) * dx + (tz - p.z) * dz <= .04f) break;
                        v.x = dx * 3.8f; v.z = dz * 3.8f; v.y += Physics.GRAVITY / 120f;
                        Physics.move(world, p, v, 1f / 120f,
                                Combatant.RADIUS, Combatant.HEIGHT, true);
                        assertFalse(route, NativeBridge.boxCollides(world.nativeHandle(),
                                p.x, p.y, p.z, Combatant.RADIUS, Combatant.HEIGHT));
                    }
                    assertEquals(route + " blocked X", tx, p.x, .07f);
                    assertEquals(route + " blocked Z", tz, p.z, .07f);
                    // Do not accept a body merely crossing an unsupported end before it falls.
                    v.x = v.z = 0;
                    for (int tick = 0; tick < 60; tick++) {
                        v.y += Physics.GRAVITY / 120f;
                        Physics.move(world, p, v, 1f / 120f,
                                Combatant.RADIUS, Combatant.HEIGHT, true);
                    }
                    assertTrue(route + " ends below destination deck",
                            p.y >= f.feetAt(f.steps - 1) - 1.01f);
                    assertTrue(route + " ends without support", Physics.onGround(world, p,
                            Combatant.RADIUS, .12f));
                }
            } finally { world.release(); }
        }
    }
}
