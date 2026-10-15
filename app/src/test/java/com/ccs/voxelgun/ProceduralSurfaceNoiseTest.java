package com.ccs.voxelgun;
import com.ccs.voxelgun.render.ProceduralSurfaceNoise;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProceduralSurfaceNoiseTest {
    @Test public void hashTileIsDeterministicFiniteAndHasUsefulVariation() {
        float min = 1f, max = 0f;
        for (int y = 0; y < ProceduralSurfaceNoise.SIZE; y++) {
            for (int x = 0; x < ProceduralSurfaceNoise.SIZE; x++) {
                float value = ProceduralSurfaceNoise.hash(x, y);
                assertTrue(Float.isFinite(value)); assertTrue(value >= 0f && value < 1f);
                assertEquals(value, ProceduralSurfaceNoise.hash(x, y), 0f);
                min = Math.min(min, value); max = Math.max(max, value);
            }
        }
        assertTrue(max - min > .9f);
    }
}
