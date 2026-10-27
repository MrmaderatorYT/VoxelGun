package com.ccs.voxelstrike.render;

import com.ccs.voxelstrike.math.Frustum;
import com.ccs.voxelstrike.math.Mat4;
import com.ccs.voxelstrike.voxel.MeshData;
import com.ccs.voxelstrike.voxel.VoxelMesher;
import com.ccs.voxelstrike.voxel.VoxelWorld;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Meshes the static map on worker threads and uploads the results a few chunks per frame, so
 * loading never blocks the render thread for long.
 */
public final class ChunkRenderer {
    private static final int UPLOADS_PER_FRAME = 6;

    private final VoxelWorld world;
    private final int chunksX;
    private final int chunksY;
    private final int chunksZ;
    private final GpuMesh[] opaque;
    private final GpuMesh[] translucent;
    private final float[] modelMatrix = new float[16];
    private static final int MAX_IN_FLIGHT = 24;
    private final ArrayBlockingQueue<Result> completed = new ArrayBlockingQueue<>(MAX_IN_FLIGHT);
    private final boolean[] queued;
    private final boolean[] resident;
    private final int[] uploadedVersion;
    private final int[] loadOrder;
    private int inFlight;
    private int nextLoad;
    private int observedRevision;
    private int dirtyCursor;
    private volatile boolean stopped;
    private final ChunkCuller culler;
    private final ThreadLocal<VoxelMesher.ChunkSnapshot> snapshots =
            ThreadLocal.withInitial(VoxelMesher.ChunkSnapshot::new);
    private final ChunkUploadBudget uploadBudget = new ChunkUploadBudget();
    private final ExecutorService executor;
    private final int totalChunks;
    private int uploaded;
    private final java.util.concurrent.atomic.AtomicLong meshNanos = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong meshCount = new java.util.concurrent.atomic.AtomicLong();
    /** Worker CPU/wall service time per chunk, not GL upload time or GPU execution. */
    public int averageMeshingMicros() {
        long count = meshCount.get();
        return count == 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, meshNanos.get() / count / 1000L);
    }

    public ChunkRenderer(VoxelWorld world, float focusX, float focusZ) {
        this.world = world;
        chunksX = world.chunksX();
        chunksY = world.chunksY();
        chunksZ = world.chunksZ();
        totalChunks = chunksX * chunksY * chunksZ;
        opaque = new GpuMesh[totalChunks];
        translucent = new GpuMesh[totalChunks];
        culler = new ChunkCuller(chunksX, chunksY, chunksZ);
        Mat4.identity(modelMatrix); // Chunk transforms are translation-only, fixed unit scale.
        queued = new boolean[totalChunks];
        resident = new boolean[totalChunks];
        uploadedVersion = new int[totalChunks];
        loadOrder = new int[totalChunks];
        // Startup-only sort: finish nearby terrain first, no sorting or boxing during frames.
        Integer[] order = new Integer[totalChunks];
        for (int i = 0; i < totalChunks; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Float.compare(distance(a, focusX, focusZ),
                distance(b, focusX, focusZ)));
        for (int i = 0; i < totalChunks; i++) loadOrder[i] = order[i];
        dirtyCursor = totalChunks;

        int threads = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
        executor = Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "voxel-mesher");
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            thread.setDaemon(true);
            return thread;
        });
        schedule();
    }

    private float distance(int index, float x, float z) {
        float dx = (index % chunksX) * VoxelWorld.CHUNK + 8 - x;
        float dz = (index / (chunksX * chunksY)) * VoxelWorld.CHUNK + 8 - z;
        return dx * dx + dz * dz;
    }

    /** At most 24 tasks/results exist, instead of thousands of unbounded queued meshes. */
    private void schedule() {
        if (stopped) return;
        // Dirty resident chunks take precedence over distant initial loads.
        if (world.revision() != observedRevision) {
            observedRevision = world.revision(); dirtyCursor = 0;
        }
        while (dirtyCursor < totalChunks && inFlight < MAX_IN_FLIGHT) {
            int i = dirtyCursor++;
            if (resident[i] && !queued[i] && uploadedVersion[i] != world.chunkVersion(i)) enqueue(i);
        }
        while (nextLoad < totalChunks && inFlight < MAX_IN_FLIGHT) enqueue(loadOrder[nextLoad++]);
    }

    private void enqueue(int index) {
        if (stopped || queued[index]) return;
        queued[index] = true; inFlight++;
        try {
            executor.execute(() -> meshChunk(index));
        } catch (java.util.concurrent.RejectedExecutionException error) {
            queued[index] = false; inFlight--;
            if (!stopped) throw error;
        }
    }

    private void meshChunk(int index) {
        long started = System.nanoTime();
        int x = index % chunksX;
        int y = (index / chunksX) % chunksY;
        int z = index / (chunksX * chunksY);
        VoxelMesher.ChunkSnapshot snapshot = snapshots.get();
        snapshot.capture(world, x, y, z);
        int version = snapshot.version();
        MeshData solid = VoxelMesher.meshChunk(snapshot, VoxelMesher.PASS_OPAQUE);
        MeshData glass = VoxelMesher.meshChunk(snapshot, VoxelMesher.PASS_TRANSLUCENT);
        meshNanos.addAndGet(System.nanoTime() - started);
        meshCount.incrementAndGet();
        if (stopped) return;
        if (!completed.offer(new Result(index, version, solid, glass)))
            throw new IllegalStateException("Chunk queue invariant exceeded");
    }

    /** Uploads a bounded number of finished chunks; call once per frame on the GL thread. */
    public void uploadPending() {
        int gpuUploads = 0;
        uploadBudget.begin(System.nanoTime());
        // Empty sky chunks should not each consume a GPU-upload slot on a large battlefield.
        for (int i = 0; i < 96 && gpuUploads < UPLOADS_PER_FRAME; i++) {
            Result result = completed.peek();
            if (result == null) {
                break;
            }
            long bytes = (long) (result.opaque.vertexCount() + result.translucent.vertexCount())
                    * MeshData.FLOATS_PER_VERTEX * Float.BYTES
                    + (long) (result.opaque.indexCount() + result.translucent.indexCount()) * Integer.BYTES;
            if (!uploadBudget.canUpload(bytes, System.nanoTime())) break;
            completed.poll(); // Single GL consumer: the peeked head cannot be replaced by workers.
            queued[result.index] = false;
            inFlight--;
            if (result.version != world.chunkVersion(result.index)) {
                // An edit raced the snapshot: never display that obsolete geometry.
                enqueue(result.index);
                continue;
            }
            if (opaque[result.index] != null) opaque[result.index].release();
            if (translucent[result.index] != null) translucent[result.index].release();
            opaque[result.index] = null; translucent[result.index] = null;
            if (!result.opaque.isEmpty()) {
                opaque[result.index] = new GpuMesh(result.opaque);
            }
            if (!result.translucent.isEmpty()) {
                translucent[result.index] = new GpuMesh(result.translucent);
            }
            culler.setMeshes(result.index, opaque[result.index] != null, translucent[result.index] != null);
            if (!result.opaque.isEmpty() || !result.translucent.isEmpty()) {
                gpuUploads++;
                uploadBudget.record(bytes);
            }
            uploadedVersion[result.index] = result.version;
            if (!resident[result.index]) { resident[result.index] = true; uploaded++; }
        }
        schedule();
    }

    public boolean ready() {
        return uploaded >= totalChunks;
    }

    public float progress() {
        return totalChunks == 0 ? 1f : (float) uploaded / totalChunks;
    }

    /** Rebuilds the visible chunk lists for this frame. */
    public void cull(Frustum frustum, float cameraX, float cameraY, float cameraZ,
                     float viewDistance) {
        culler.cull(frustum, cameraX, cameraY, cameraZ, viewDistance);
    }

    public void drawOpaque(Shader shader) {
        draw(shader, culler.opaqueIndices(), culler.opaqueCount(), opaque);
    }

    public void drawTranslucent(Shader shader) {
        draw(shader, culler.translucentIndices(), culler.translucentCount(), translucent);
    }

    private void draw(Shader shader, int[] indices, int count, GpuMesh[] meshes) {
        int size = VoxelWorld.CHUNK;
        for (int i = 0; i < count; i++) {
            int index = indices[i];
            GpuMesh mesh = meshes[index];
            if (mesh == null) {
                continue;
            }
            int cx = index % chunksX;
            int cy = (index / chunksX) % chunksY;
            int cz = index / (chunksX * chunksY);
            modelMatrix[12] = cx * size;
            modelMatrix[13] = cy * size;
            modelMatrix[14] = cz * size;
            shader.setMatrix("uModel", modelMatrix);
            // The map is static, so last frame's transform is this frame's transform.
            shader.setMatrix("uPreviousModel", modelMatrix);
            mesh.draw();
        }
    }

    public int visibleChunkCount() {
        return culler.opaqueCount();
    }

    /** Stops the background meshers without touching GL state. */
    public void stopWorkers() {
        stopped = true;
        executor.shutdownNow();
    }

    public void release() {
        stopped = true;
        executor.shutdownNow();
        for (GpuMesh mesh : opaque) {
            if (mesh != null) {
                mesh.release();
            }
        }
        for (GpuMesh mesh : translucent) {
            if (mesh != null) {
                mesh.release();
            }
        }
        completed.clear();
    }

    /** A finished background mesh waiting for GL upload. */
    private static final class Result {
        final int index;
        final int version;
        final MeshData opaque;
        final MeshData translucent;

        Result(int index, int version, MeshData opaque, MeshData translucent) {
            this.index = index;
            this.version = version;
            this.opaque = opaque;
            this.translucent = translucent;
        }
    }
}
