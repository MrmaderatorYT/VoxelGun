package com.ccs.voxelstrike;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ccs.voxelstrike.voxel.Materials;

import org.junit.Test;

public class MaterialsTest {
    @Test
    public void opaqueAndTranslucentAreClassifiedByRange() {
        assertTrue(Materials.opaque(Materials.CONCRETE));
        assertTrue(Materials.opaque(Materials.CHROME));
        assertFalse(Materials.opaque(Materials.AIR));
        assertFalse(Materials.opaque(Materials.GLASS));
        assertTrue(Materials.translucent(Materials.GLASS));
        assertTrue(Materials.translucent(Materials.WATER));
    }

    @Test
    public void waterDoesNotBlockMovementButConcreteDoes() {
        assertFalse(Materials.solid(Materials.WATER));
        assertFalse(Materials.solid(Materials.AIR));
        assertTrue(Materials.solid(Materials.CONCRETE));
        assertTrue(Materials.solid(Materials.GLASS));
    }

    @Test
    public void translucentIdsFoldIntoTheTailOfThePalette() {
        assertEquals(1, Materials.slot(Materials.CONCRETE));
        assertEquals(63, Materials.slot(Materials.ICE));
        assertEquals(64, Materials.slot(Materials.GLASS));
        assertEquals(67, Materials.slot(Materials.WATER));
        // The opaque range must not run into the folded translucent range.
        assertTrue(Materials.slot(Materials.ICE) < Materials.slot(Materials.GLASS));
        assertTrue(Materials.slot(Materials.WATER) * 8 + 8 <= Materials.table().length);
    }

    @Test
    public void emissiveMaterialsCarryLight() {
        assertTrue(Materials.emissive(Materials.LAMP) > 1f);
        assertTrue(Materials.emissive(Materials.NEON_CYAN) > 1f);
        assertEquals(0f, Materials.emissive(Materials.CONCRETE), 1e-5f);
    }
}
