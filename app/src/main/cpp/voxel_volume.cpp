// Native voxel volume: the authoritative copy used for ray casting, line of sight and for
// building the GPU acceleration structure consumed by the ray tracer.
//
// Java keeps its own byte[] mirror for cheap single-voxel reads; every query that touches many
// voxels (bullets, AI sight, grenade flight, brick field) runs here to amortise the JNI call.

#include <jni.h>

#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <vector>

#include "voxel_common.h"

namespace {

using vg::Volume;

Volume* handleOf(jlong handle) { return reinterpret_cast<Volume*>(handle); }

struct Hit {
    float t;
    int bx, by, bz;
    float nx, ny, nz;
    uint8_t material;
};

/// Amanatides & Woo voxel traversal. `mask` selects which materials stop the ray.
bool traverse(const Volume& vol, float ox, float oy, float oz, float dx, float dy, float dz,
              float maxDist, bool stopOnTransparent, Hit& hit) {
    const float len = std::sqrt(dx * dx + dy * dy + dz * dz);
    if (len < 1e-6f) return false;
    dx /= len;
    dy /= len;
    dz /= len;

    int ix = static_cast<int>(std::floor(ox));
    int iy = static_cast<int>(std::floor(oy));
    int iz = static_cast<int>(std::floor(oz));

    const uint8_t start = vol.at(ix, iy, iz);
    if (start != 0 && (stopOnTransparent || vg::blocksSight(start))) {
        hit = {0.0f, ix, iy, iz, -dx, -dy, -dz, start};
        return true;
    }

    const int stepX = dx > 0 ? 1 : -1;
    const int stepY = dy > 0 ? 1 : -1;
    const int stepZ = dz > 0 ? 1 : -1;

    const float invX = dx != 0 ? std::fabs(1.0f / dx) : 1e30f;
    const float invY = dy != 0 ? std::fabs(1.0f / dy) : 1e30f;
    const float invZ = dz != 0 ? std::fabs(1.0f / dz) : 1e30f;

    float tMaxX = dx != 0 ? ((dx > 0 ? (ix + 1 - ox) : (ox - ix)) * invX) : 1e30f;
    float tMaxY = dy != 0 ? ((dy > 0 ? (iy + 1 - oy) : (oy - iy)) * invY) : 1e30f;
    float tMaxZ = dz != 0 ? ((dz > 0 ? (iz + 1 - oz) : (oz - iz)) * invZ) : 1e30f;

    float t = 0.0f;
    for (int guard = 0; guard < 2048; guard++) {
        int axis;
        if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
            t = tMaxX;
            tMaxX += invX;
            ix += stepX;
            axis = 0;
        } else if (tMaxY <= tMaxZ) {
            t = tMaxY;
            tMaxY += invY;
            iy += stepY;
            axis = 1;
        } else {
            t = tMaxZ;
            tMaxZ += invZ;
            iz += stepZ;
            axis = 2;
        }
        if (t > maxDist) return false;

        // Once the ray has passed the volume along its travel direction it can never re-enter.
        if ((ix < 0 && stepX < 0) || (ix >= vol.sx && stepX > 0)) return false;
        if ((iy < 0 && stepY < 0) || (iy >= vol.sy && stepY > 0)) return false;
        if ((iz < 0 && stepZ < 0) || (iz >= vol.sz && stepZ > 0)) return false;
        if (!vol.inside(ix, iy, iz)) continue;  // still outside, but heading back in

        const uint8_t m = vol.at(ix, iy, iz);
        if (m == 0) continue;
        if (!stopOnTransparent && !vg::blocksSight(m)) continue;

        hit.t = t;
        hit.bx = ix;
        hit.by = iy;
        hit.bz = iz;
        hit.material = m;
        hit.nx = axis == 0 ? static_cast<float>(-stepX) : 0.0f;
        hit.ny = axis == 1 ? static_cast<float>(-stepY) : 0.0f;
        hit.nz = axis == 2 ? static_cast<float>(-stepZ) : 0.0f;
        return true;
    }
    return false;
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_com_ccs_voxelgun_voxel_NativeBridge_createVolume(JNIEnv*, jclass,
                                                                             jint sx, jint sy,
                                                                             jint sz) {
    if (sx <= 0 || sy <= 0 || sz <= 0) return 0;
    Volume* vol = new Volume();
    vol->sx = sx;
    vol->sy = sy;
    vol->sz = sz;
    vol->data = static_cast<uint8_t*>(std::calloc(vol->count(), 1));
    if (vol->data == nullptr) {
        delete vol;
        return 0;
    }
    return reinterpret_cast<jlong>(vol);
}

JNIEXPORT void JNICALL Java_com_ccs_voxelgun_voxel_NativeBridge_destroyVolume(JNIEnv*, jclass,
                                                                             jlong handle) {
    Volume* vol = handleOf(handle);
    if (vol == nullptr) return;
    std::free(vol->data);
    delete vol;
}

JNIEXPORT void JNICALL Java_com_ccs_voxelgun_voxel_NativeBridge_uploadVolume(JNIEnv* env, jclass,
                                                                            jlong handle,
                                                                            jbyteArray data) {
    Volume* vol = handleOf(handle);
    if (vol == nullptr) return;
    const jsize len = env->GetArrayLength(data);
    const size_t copy = std::min(static_cast<size_t>(len), vol->count());
    void* raw = env->GetPrimitiveArrayCritical(data, nullptr);
    if (raw == nullptr) return;
    std::memcpy(vol->data, raw, copy);
    env->ReleasePrimitiveArrayCritical(data, raw, JNI_ABORT);
}

JNIEXPORT void JNICALL Java_com_ccs_voxelgun_voxel_NativeBridge_setVoxel(JNIEnv*, jclass,
                                                                        jlong handle, jint x,
                                                                        jint y, jint z,
                                                                        jint material) {
    Volume* vol = handleOf(handle);
    if (vol != nullptr) vol->put(x, y, z, static_cast<uint8_t>(material));
}

/// out = { t, hitX, hitY, hitZ, normalX, normalY, normalZ, material, blockX, blockY, blockZ }
JNIEXPORT jboolean JNICALL Java_com_ccs_voxelgun_voxel_NativeBridge_raycast(
        JNIEnv* env, jclass, jlong handle, jfloat ox, jfloat oy, jfloat oz, jfloat dx, jfloat dy,
        jfloat dz, jfloat maxDist, jboolean hitTransparent, jfloatArray out) {
    Volume* vol = handleOf(handle);
    if (vol == nullptr) return JNI_FALSE;
    Hit hit{};
    if (!traverse(*vol, ox, oy, oz, dx, dy, dz, maxDist, hitTransparent == JNI_TRUE, hit)) {
        return JNI_FALSE;
    }
    const float len = std::sqrt(dx * dx + dy * dy + dz * dz);
    const float ux = dx / len, uy = dy / len, uz = dz / len;
    float values[11] = {hit.t,
                        ox + ux * hit.t,
                        oy + uy * hit.t,
                        oz + uz * hit.t,
                        hit.nx,
                        hit.ny,
                        hit.nz,
                        static_cast<float>(hit.material),
                        static_cast<float>(hit.bx),
                        static_cast<float>(hit.by),
                        static_cast<float>(hit.bz)};
    env->SetFloatArrayRegion(out, 0, 11, values);
    return JNI_TRUE;
}

/// Sight test between two points; transparent materials never block.
JNIEXPORT jboolean JNICALL Java_com_ccs_voxelgun_voxel_NativeBridge_lineOfSight(
        JNIEnv*, jclass, jlong handle, jfloat ax, jfloat ay, jfloat az, jfloat bx, jfloat by,
        jfloat bz) {
    Volume* vol = handleOf(handle);
    if (vol == nullptr) return JNI_FALSE;
    const float dx = bx - ax, dy = by - ay, dz = bz - az;
    const float dist = std::sqrt(dx * dx + dy * dy + dz * dz);
    if (dist < 1e-4f) return JNI_TRUE;
    Hit hit{};
    if (!traverse(*vol, ax, ay, az, dx, dy, dz, dist - 0.01f, false, hit)) return JNI_TRUE;
    return JNI_FALSE;
}

/// Axis-aligned capsule-ish box test used by the bot mover and by grenade collision.
JNIEXPORT jboolean JNICALL Java_com_ccs_voxelgun_voxel_NativeBridge_boxCollides(
        JNIEnv*, jclass, jlong handle, jfloat x, jfloat y, jfloat z, jfloat halfWidth,
        jfloat height) {
    Volume* vol = handleOf(handle);
    if (vol == nullptr) return JNI_FALSE;
    const int x0 = static_cast<int>(std::floor(x - halfWidth));
    const int x1 = static_cast<int>(std::floor(x + halfWidth));
    const int y0 = static_cast<int>(std::floor(y));
    const int y1 = static_cast<int>(std::floor(y + height));
    const int z0 = static_cast<int>(std::floor(z - halfWidth));
    const int z1 = static_cast<int>(std::floor(z + halfWidth));
    for (int zi = z0; zi <= z1; zi++) {
        for (int yi = y0; yi <= y1; yi++) {
            for (int xi = x0; xi <= x1; xi++) {
                // No implicit floor below the world: floating island maps rely on the fall.
                if (vg::blocksMovement(vol->at(xi, yi, zi))) return true;
            }
        }
    }
    return JNI_FALSE;
}

/// Chebyshev empty-space distance per brick, in bricks, clamped to 255.
/// The ray tracer uses it to jump over empty regions instead of stepping voxel by voxel.
JNIEXPORT jbyteArray JNICALL Java_com_ccs_voxelgun_voxel_NativeBridge_buildBrickField(
        JNIEnv* env, jclass, jlong handle, jint brick) {
    Volume* vol = handleOf(handle);
    if (vol == nullptr || brick <= 0) return nullptr;

    const int bx = (vol->sx + brick - 1) / brick;
    const int by = (vol->sy + brick - 1) / brick;
    const int bz = (vol->sz + brick - 1) / brick;
    const size_t total = static_cast<size_t>(bx) * by * bz;
    std::vector<uint16_t> dist(total, 0xFFFF);

    auto index = [&](int x, int y, int z) { return static_cast<size_t>(x) + bx * (y + static_cast<size_t>(by) * z); };

    for (int z = 0; z < vol->sz; z++) {
        for (int y = 0; y < vol->sy; y++) {
            for (int x = 0; x < vol->sx; x++) {
                if (vol->at(x, y, z) != 0) dist[index(x / brick, y / brick, z / brick)] = 0;
            }
        }
    }

    // Two-sweep chamfer with 26-neighbourhood yields the exact Chebyshev distance.
    auto relax = [&](size_t self, int nx, int ny, int nz) {
        if (nx < 0 || ny < 0 || nz < 0 || nx >= bx || ny >= by || nz >= bz) return;
        const uint16_t candidate = static_cast<uint16_t>(std::min(0xFFFE, dist[index(nx, ny, nz)] + 1));
        if (candidate < dist[self]) dist[self] = candidate;
    };
    for (int z = 0; z < bz; z++)
        for (int y = 0; y < by; y++)
            for (int x = 0; x < bx; x++) {
                const size_t self = index(x, y, z);
                if (dist[self] == 0) continue;
                for (int dz = -1; dz <= 0; dz++)
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dz == 0 && (dy > 0 || (dy == 0 && dx >= 0))) continue;
                            relax(self, x + dx, y + dy, z + dz);
                        }
            }
    for (int z = bz - 1; z >= 0; z--)
        for (int y = by - 1; y >= 0; y--)
            for (int x = bx - 1; x >= 0; x--) {
                const size_t self = index(x, y, z);
                if (dist[self] == 0) continue;
                for (int dz = 0; dz <= 1; dz++)
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dz == 0 && (dy < 0 || (dy == 0 && dx <= 0))) continue;
                            relax(self, x + dx, y + dy, z + dz);
                        }
            }

    std::vector<uint8_t> packed(total);
    for (size_t i = 0; i < total; i++) {
        packed[i] = static_cast<uint8_t>(std::min<uint16_t>(dist[i], 255));
    }
    jbyteArray out = env->NewByteArray(static_cast<jsize>(total));
    if (out == nullptr) return nullptr;
    env->SetByteArrayRegion(out, 0, static_cast<jsize>(total),
                            reinterpret_cast<const jbyte*>(packed.data()));
    return out;
}

/// Column heights (highest solid voxel + 1) used to build the navigation grid and the minimap.
JNIEXPORT jbyteArray JNICALL Java_com_ccs_voxelgun_voxel_NativeBridge_buildHeightMap(JNIEnv* env,
                                                                                    jclass,
                                                                                    jlong handle) {
    Volume* vol = handleOf(handle);
    if (vol == nullptr) return nullptr;
    const size_t total = static_cast<size_t>(vol->sx) * vol->sz;
    std::vector<uint8_t> heights(total, 0);
    for (int z = 0; z < vol->sz; z++) {
        for (int x = 0; x < vol->sx; x++) {
            int top = 0;
            for (int y = vol->sy - 1; y >= 0; y--) {
                if (vg::blocksMovement(vol->at(x, y, z))) {
                    top = y + 1;
                    break;
                }
            }
            heights[static_cast<size_t>(x) + vol->sx * z] = static_cast<uint8_t>(std::min(top, 255));
        }
    }
    jbyteArray out = env->NewByteArray(static_cast<jsize>(total));
    if (out == nullptr) return nullptr;
    env->SetByteArrayRegion(out, 0, static_cast<jsize>(total),
                            reinterpret_cast<const jbyte*>(heights.data()));
    return out;
}

}  // extern "C"
