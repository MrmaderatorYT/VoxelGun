package com.ccs.voxelstrike.game;

import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.voxel.VoxelWorld;

import java.util.Random;

/**
 * Transient visuals: particles, bullet tracers and surface decals.
 *
 * <p>Stored as parallel arrays so the renderer can stream them straight into vertex buffers
 * without walking an object graph every frame.
 */
public final class Effects {
    public static final int MAX_PARTICLES = 3072;
    public static final int MAX_TRACERS = 96;
    public static final int MAX_DECALS = 384;

    public static final int KIND_SPARK = 0;
    public static final int KIND_DEBRIS = 1;
    public static final int KIND_BLOOD = 2;
    public static final int KIND_SMOKE = 3;
    public static final int KIND_FIRE = 4;
    public static final int KIND_SHELL = 5;

    public final float[] particleX = new float[MAX_PARTICLES];
    public final float[] particleY = new float[MAX_PARTICLES];
    public final float[] particleZ = new float[MAX_PARTICLES];
    public final float[] particleVx = new float[MAX_PARTICLES];
    public final float[] particleVy = new float[MAX_PARTICLES];
    public final float[] particleVz = new float[MAX_PARTICLES];
    public final float[] particleLife = new float[MAX_PARTICLES];
    public final float[] particleMaxLife = new float[MAX_PARTICLES];
    public final float[] particleSize = new float[MAX_PARTICLES];
    public final float[] particleR = new float[MAX_PARTICLES];
    public final float[] particleG = new float[MAX_PARTICLES];
    public final float[] particleB = new float[MAX_PARTICLES];
    public final float[] particleGlow = new float[MAX_PARTICLES];
    public final int[] particleKind = new int[MAX_PARTICLES];
    private int particleCount;

    public final float[] tracerX0 = new float[MAX_TRACERS];
    public final float[] tracerY0 = new float[MAX_TRACERS];
    public final float[] tracerZ0 = new float[MAX_TRACERS];
    public final float[] tracerX1 = new float[MAX_TRACERS];
    public final float[] tracerY1 = new float[MAX_TRACERS];
    public final float[] tracerZ1 = new float[MAX_TRACERS];
    public final float[] tracerLife = new float[MAX_TRACERS];
    public final float[] tracerHeat = new float[MAX_TRACERS];
    private int tracerCount;

    public final float[] decalX = new float[MAX_DECALS];
    public final float[] decalY = new float[MAX_DECALS];
    public final float[] decalZ = new float[MAX_DECALS];
    public final float[] decalNx = new float[MAX_DECALS];
    public final float[] decalNy = new float[MAX_DECALS];
    public final float[] decalNz = new float[MAX_DECALS];
    public final float[] decalSize = new float[MAX_DECALS];
    public final float[] decalLife = new float[MAX_DECALS];
    private int decalCount;
    private int decalCursor;

    private final Random random = new Random(20260726L);

    public int particleCount() {
        return particleCount;
    }

    public int tracerCount() {
        return tracerCount;
    }

    public int decalCount() {
        return decalCount;
    }

    public void clear() {
        particleCount = 0;
        tracerCount = 0;
        decalCount = 0;
        decalCursor = 0;
    }

    public void update(float dt, VoxelWorld world) {
        for (int i = particleCount - 1; i >= 0; i--) {
            particleLife[i] -= dt;
            if (particleLife[i] <= 0f) {
                removeParticle(i);
                continue;
            }
            int kind = particleKind[i];
            float drag = kind == KIND_SMOKE ? 1.6f : kind == KIND_BLOOD ? 2.2f : 0.9f;
            float gravity = kind == KIND_SMOKE ? 1.6f : kind == KIND_FIRE ? 2.4f : -19f;

            particleVy[i] += gravity * dt;
            float damping = Math.max(0f, 1f - drag * dt);
            particleVx[i] *= damping;
            particleVz[i] *= damping;

            float nx = particleX[i] + particleVx[i] * dt;
            float ny = particleY[i] + particleVy[i] * dt;
            float nz = particleZ[i] + particleVz[i] * dt;

            if (kind == KIND_DEBRIS || kind == KIND_SHELL) {
                if (world.solidAt(nx, ny, nz)) {
                    // Bounce off the surface and lose most of the energy.
                    if (!world.solidAt(nx, particleY[i], particleZ[i])) {
                        particleVx[i] *= -0.35f;
                    } else if (!world.solidAt(particleX[i], ny, particleZ[i])) {
                        particleVy[i] *= -0.35f;
                    } else {
                        particleVz[i] *= -0.35f;
                    }
                    particleLife[i] = Math.min(particleLife[i], 0.6f);
                    continue;
                }
            }
            particleX[i] = nx;
            particleY[i] = ny;
            particleZ[i] = nz;
        }

        for (int i = tracerCount - 1; i >= 0; i--) {
            tracerLife[i] -= dt;
            if (tracerLife[i] <= 0f) {
                tracerCount--;
                if (i != tracerCount) {
                    copyTracer(tracerCount, i);
                }
            }
        }

        for (int i = decalCount - 1; i >= 0; i--) {
            decalLife[i] -= dt;
            if (decalLife[i] <= 0f) {
                decalCount--;
                if (i != decalCount) {
                    copyDecal(decalCount, i);
                }
            }
        }
    }

    private void removeParticle(int index) {
        particleCount--;
        if (index == particleCount) {
            return;
        }
        particleX[index] = particleX[particleCount];
        particleY[index] = particleY[particleCount];
        particleZ[index] = particleZ[particleCount];
        particleVx[index] = particleVx[particleCount];
        particleVy[index] = particleVy[particleCount];
        particleVz[index] = particleVz[particleCount];
        particleLife[index] = particleLife[particleCount];
        particleMaxLife[index] = particleMaxLife[particleCount];
        particleSize[index] = particleSize[particleCount];
        particleR[index] = particleR[particleCount];
        particleG[index] = particleG[particleCount];
        particleB[index] = particleB[particleCount];
        particleGlow[index] = particleGlow[particleCount];
        particleKind[index] = particleKind[particleCount];
    }

    private void copyTracer(int from, int to) {
        tracerX0[to] = tracerX0[from];
        tracerY0[to] = tracerY0[from];
        tracerZ0[to] = tracerZ0[from];
        tracerX1[to] = tracerX1[from];
        tracerY1[to] = tracerY1[from];
        tracerZ1[to] = tracerZ1[from];
        tracerLife[to] = tracerLife[from];
        tracerHeat[to] = tracerHeat[from];
    }

    private void copyDecal(int from, int to) {
        decalX[to] = decalX[from];
        decalY[to] = decalY[from];
        decalZ[to] = decalZ[from];
        decalNx[to] = decalNx[from];
        decalNy[to] = decalNy[from];
        decalNz[to] = decalNz[from];
        decalSize[to] = decalSize[from];
        decalLife[to] = decalLife[from];
    }

    public void spawnParticle(int kind, float x, float y, float z, float vx, float vy, float vz,
                              float life, float size, float r, float g, float b, float glow) {
        if (particleCount >= MAX_PARTICLES) {
            // Recycle the oldest slot rather than dropping the effect entirely.
            removeParticle(0);
        }
        int i = particleCount++;
        particleKind[i] = kind;
        particleX[i] = x;
        particleY[i] = y;
        particleZ[i] = z;
        particleVx[i] = vx;
        particleVy[i] = vy;
        particleVz[i] = vz;
        particleLife[i] = life;
        particleMaxLife[i] = life;
        particleSize[i] = size;
        particleR[i] = r;
        particleG[i] = g;
        particleB[i] = b;
        particleGlow[i] = glow;
    }

    public void tracer(float x0, float y0, float z0, float x1, float y1, float z1, float heat) {
        if (tracerCount >= MAX_TRACERS) {
            tracerCount = MAX_TRACERS - 1;
        }
        int i = tracerCount++;
        tracerX0[i] = x0;
        tracerY0[i] = y0;
        tracerZ0[i] = z0;
        tracerX1[i] = x1;
        tracerY1[i] = y1;
        tracerZ1[i] = z1;
        tracerLife[i] = 0.075f;
        tracerHeat[i] = heat;
    }

    public void decal(float x, float y, float z, float nx, float ny, float nz, float size) {
        int i;
        if (decalCount < MAX_DECALS) {
            i = decalCount++;
        } else {
            i = decalCursor;
            decalCursor = (decalCursor + 1) % MAX_DECALS;
        }
        decalX[i] = x + nx * 0.02f;
        decalY[i] = y + ny * 0.02f;
        decalZ[i] = z + nz * 0.02f;
        decalNx[i] = nx;
        decalNy[i] = ny;
        decalNz[i] = nz;
        decalSize[i] = size;
        decalLife[i] = 22f;
    }

    /** Sparks, dust and chipped voxel debris where a bullet lands. */
    public void bulletImpact(float x, float y, float z, float nx, float ny, float nz, int material) {
        int rgb = Materials.rgb(material);
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        decal(x, y, z, nx, ny, nz, 0.14f + random.nextFloat() * 0.06f);

        for (int i = 0; i < 7; i++) {
            float sx = nx + (random.nextFloat() - 0.5f) * 1.4f;
            float sy = ny + (random.nextFloat() - 0.5f) * 1.4f + 0.4f;
            float sz = nz + (random.nextFloat() - 0.5f) * 1.4f;
            float speed = 2.4f + random.nextFloat() * 4.5f;
            spawnParticle(KIND_DEBRIS, x, y, z, sx * speed, sy * speed, sz * speed,
                    0.45f + random.nextFloat() * 0.5f, 0.055f, r, g, b, 0f);
        }
        for (int i = 0; i < 4; i++) {
            float sx = nx + (random.nextFloat() - 0.5f);
            float sy = ny + (random.nextFloat() - 0.5f) + 0.6f;
            float sz = nz + (random.nextFloat() - 0.5f);
            float speed = 3.5f + random.nextFloat() * 5f;
            spawnParticle(KIND_SPARK, x, y, z, sx * speed, sy * speed, sz * speed,
                    0.12f + random.nextFloat() * 0.14f, 0.035f, 1f, 0.82f, 0.42f, 5f);
        }
        spawnParticle(KIND_SMOKE, x + nx * 0.1f, y + ny * 0.1f, z + nz * 0.1f,
                nx * 0.4f, ny * 0.4f + 0.3f, nz * 0.4f, 0.7f, 0.28f, 0.6f, 0.58f, 0.55f, 0f);
    }

    public void bloodBurst(float x, float y, float z, float dirX, float dirY, float dirZ,
                           float amount) {
        int count = (int) (5 + amount * 8);
        for (int i = 0; i < count; i++) {
            float vx = dirX * 2.2f + (random.nextFloat() - 0.5f) * 4f;
            float vy = dirY * 2.2f + random.nextFloat() * 3.4f;
            float vz = dirZ * 2.2f + (random.nextFloat() - 0.5f) * 4f;
            spawnParticle(KIND_BLOOD, x, y, z, vx, vy, vz, 0.5f + random.nextFloat() * 0.4f,
                    0.06f, 0.62f, 0.05f, 0.06f, 0f);
        }
    }

    public void muzzleSmoke(float x, float y, float z, float dirX, float dirY, float dirZ) {
        for (int i = 0; i < 3; i++) {
            spawnParticle(KIND_SMOKE, x, y, z,
                    dirX * 2.4f + (random.nextFloat() - 0.5f),
                    dirY * 2.4f + random.nextFloat() * 0.6f,
                    dirZ * 2.4f + (random.nextFloat() - 0.5f),
                    0.32f, 0.14f, 0.75f, 0.72f, 0.68f, 0f);
        }
        spawnParticle(KIND_FIRE, x + dirX * 0.2f, y + dirY * 0.2f, z + dirZ * 0.2f,
                dirX * 1.2f, dirY * 1.2f, dirZ * 1.2f, 0.06f, 0.22f, 1f, 0.76f, 0.32f, 14f);
    }

    public void ejectShell(float x, float y, float z, float rightX, float rightZ) {
        spawnParticle(KIND_SHELL, x, y, z, rightX * 2.6f + random.nextFloat(),
                1.8f + random.nextFloat(), rightZ * 2.6f + random.nextFloat(),
                1.1f, 0.045f, 0.85f, 0.68f, 0.28f, 0.4f);
    }

    public void explosion(float x, float y, float z, float radius) {
        for (int i = 0; i < 46; i++) {
            float vx = (random.nextFloat() - 0.5f) * 2f;
            float vy = random.nextFloat() * 1.4f;
            float vz = (random.nextFloat() - 0.5f) * 2f;
            float speed = 4f + random.nextFloat() * 14f;
            spawnParticle(KIND_FIRE, x, y, z, vx * speed, vy * speed, vz * speed,
                    0.22f + random.nextFloat() * 0.35f, 0.34f, 1f, 0.62f, 0.18f, 12f);
        }
        for (int i = 0; i < 30; i++) {
            float vx = (random.nextFloat() - 0.5f) * 2f;
            float vy = random.nextFloat();
            float vz = (random.nextFloat() - 0.5f) * 2f;
            float speed = 2f + random.nextFloat() * 7f;
            spawnParticle(KIND_SMOKE, x, y + 0.4f, z, vx * speed, vy * speed, vz * speed,
                    1.2f + random.nextFloat(), 0.6f, 0.24f, 0.23f, 0.22f, 0f);
        }
        for (int i = 0; i < 22; i++) {
            float vx = (random.nextFloat() - 0.5f) * 2f;
            float vy = random.nextFloat() * 1.6f;
            float vz = (random.nextFloat() - 0.5f) * 2f;
            float speed = 5f + random.nextFloat() * 12f;
            spawnParticle(KIND_DEBRIS, x, y, z, vx * speed, vy * speed, vz * speed,
                    0.8f + random.nextFloat(), 0.09f, 0.35f, 0.33f, 0.30f, 0f);
        }
        decal(x, y - radius * 0.2f, z, 0f, 1f, 0f, radius * 0.5f);
    }
}
