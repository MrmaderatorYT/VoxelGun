package com.ccs.voxelstrike.render;

/** Soft GL upload budget; always admit one mesh so a large chunk can never starve loading. */
public final class ChunkUploadBudget {
    public static final long MAX_BYTES = 2L * 1024 * 1024;
    public static final long MAX_NANOS = 2_000_000L;
    private long start, bytes;
    private boolean uploaded;
    public void begin(long now) { start = now; bytes = 0; uploaded = false; }
    public boolean canUpload(long nextBytes, long now) {
        return nextBytes == 0 || !uploaded
                || (bytes + nextBytes <= MAX_BYTES && now - start < MAX_NANOS);
    }
    public void record(long count) { bytes += count; if (count > 0) uploaded = true; }
}
