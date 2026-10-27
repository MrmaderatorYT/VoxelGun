package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.voxel.VoxelWorld;
import org.junit.Test;

import static org.junit.Assert.*;

/** A deferred refresh is not evidence that a previously valid route has disappeared. */
public class PathBudgetDeferralTest {
    @Test public void exhaustedBudgetCanBeDetectedWithoutDiscardingTheCurrentRoute() {
        VoxelWorld world = new VoxelWorld(16, 6, 16);
        try {
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                world.set(x, 0, z, Materials.CONCRETE);
            }
            world.commit();
            NavGrid nav = new NavGrid(world);
            PathFinder finder = new PathFinder(nav);
            PathFinder.Path path = new PathFinder.Path(128);
            int start = nav.nearestNode(2.5f, 1f, 2.5f);
            int goal = nav.nearestNode(13.5f, 1f, 13.5f);
            finder.beginStep(1);
            assertTrue(finder.canSearch());
            assertTrue(finder.find(start, goal, path));
            path.advance();
            int length = path.length();
            int current = path.currentNode();
            finder.beginStep(0);
            assertFalse(finder.canSearch());
            // The bot's deferral guard must skip find entirely, preserving route and cursor.
            if (finder.canSearch()) finder.find(start, goal, path);
            assertFalse(path.finished());
            assertEquals(length, path.length());
            assertEquals(current, path.currentNode());
            finder.beginStep(1);
            assertTrue(finder.canSearch());
            assertTrue(finder.find(start, goal, path));
            assertEquals(goal, path.node(path.length() - 1));
        } finally { world.release(); }
    }
}
