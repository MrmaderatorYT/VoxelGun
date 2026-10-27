package com.ccs.voxelstrike;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ccs.voxelstrike.game.Combatant;
import com.ccs.voxelstrike.game.GameMode;
import com.ccs.voxelstrike.game.Match;
import com.ccs.voxelstrike.game.MatchConfig;
import com.ccs.voxelstrike.map.GameMap;
import com.ccs.voxelstrike.map.Maps;
import com.ccs.voxelstrike.voxel.VoxelWorld;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class MapCatalogTest {
    private static final int CLASSIC_MAPS = 8;

    @Test
    public void catalogContainsEighteenNewDistinctMaps() {
        assertEquals(26, Maps.count());
        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        Set<Long> newGeometry = new HashSet<>();

        for (int index = 0; index < Maps.count(); index++) {
            GameMap map = Maps.create(index);
            assertTrue("duplicate map id " + map.id(), ids.add(map.id()));
            assertTrue("duplicate map name " + map.displayName(), names.add(map.displayName()));
            assertEquals(index, Maps.indexOf(map.id()));
            assertTrue("missing tags for " + map.id(), !Maps.tags(index).isEmpty());

            if (index >= CLASSIC_MAPS) {
                VoxelWorld world = map.build(700L);
                try {
                    assertTrue(map.id() + " duplicates another map's geometry",
                            newGeometry.add(geometrySignature(world)));
                    assertTrue(map.id() + " needs more blue spawns",
                            map.blueSpawns().size() >= 10);
                    assertTrue(map.id() + " needs more red spawns",
                            map.redSpawns().size() >= 10);
                    assertTrue(map.id() + " needs more FFA spawns",
                            map.freeSpawns().size() >= 10);
                } finally {
                    world.release();
                }
            }
        }
    }

    @Test
    public void initialTeamSpawnsDoNotOverlap() {
        float minimum = Combatant.RADIUS * 2f;
        float minimumSquared = minimum * minimum;

        for (int mapIndex = 0; mapIndex < Maps.count(); mapIndex++) {
            MatchConfig config = new MatchConfig();
            config.mode = GameMode.TEAM_DEATHMATCH;
            config.mapIndex = mapIndex;
            config.botCount = 7;
            config.seed = 9000L + mapIndex;
            Match match = new Match(config);
            try {
                for (int first = 0; first < match.combatants().size(); first++) {
                    Combatant a = match.combatants().get(first);
                    for (int second = first + 1; second < match.combatants().size(); second++) {
                        Combatant b = match.combatants().get(second);
                        float dx = a.position.x - b.position.x;
                        float dz = a.position.z - b.position.z;
                        assertTrue(Maps.name(mapIndex) + " overlaps " + a.name + " and " + b.name,
                                dx * dx + dz * dz >= minimumSquared);
                    }
                }
            } finally {
                match.release();
            }
        }
    }

    private long geometrySignature(VoxelWorld world) {
        long hash = 0xcbf29ce484222325L;
        for (int z = 0; z < world.sizeZ(); z++) {
            for (int y = 0; y < world.sizeY(); y++) {
                for (int x = 0; x < world.sizeX(); x++) {
                    hash ^= world.get(x, y, z) & 0xffL;
                    hash *= 0x100000001b3L;
                }
            }
        }
        return hash;
    }
}
