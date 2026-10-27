package com.ccs.voxelstrike;

import com.ccs.voxelstrike.render.ChunkUploadBudget;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChunkUploadBudgetTest {
    @Test public void oversizedFirstMeshMakesProgressButCannotBeFollowedByAnother() {
        ChunkUploadBudget budget = new ChunkUploadBudget();
        budget.begin(10);
        long huge = ChunkUploadBudget.MAX_BYTES * 2;
        assertTrue(budget.canUpload(huge, 10 + ChunkUploadBudget.MAX_NANOS));
        budget.record(huge);
        assertFalse(budget.canUpload(1, 20));
        assertTrue("empty results require no driver upload", budget.canUpload(0, 10000000));
    }
    @Test public void bytesAndTimeBoundSubsequentUploadsAndBeginResetsBoth() {
        ChunkUploadBudget budget = new ChunkUploadBudget();
        budget.begin(100);
        budget.record(1024);
        assertTrue(budget.canUpload(1024, 200));
        assertFalse(budget.canUpload(ChunkUploadBudget.MAX_BYTES, 200));
        assertFalse(budget.canUpload(1024, 100 + ChunkUploadBudget.MAX_NANOS));
        budget.begin(20000000);
        assertTrue(budget.canUpload(ChunkUploadBudget.MAX_BYTES, 20000000));
    }
}
