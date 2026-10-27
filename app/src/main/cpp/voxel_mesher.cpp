// Greedy voxel meshing with per-corner ambient occlusion.
//
// The caller passes a volume padded by one voxel on every side so faces on the chunk border
// are culled against the real neighbour data and corner AO stays continuous across chunks.
// Output layout (native endian):
//   int32 vertexCount, int32 indexCount, Vertex[vertexCount], uint32[indexCount]

#include <jni.h>

#include <algorithm>
#include <cstring>
#include <vector>

#include "voxel_common.h"

namespace {

struct Vertex {
    float x, y, z;     // position, already scaled
    float nx, ny, nz;  // flat face normal
    float material;    // palette index, resolved to PBR data in the shader
    float ao;          // 0..1 corner occlusion
};

/// Padded accessor: valid coordinates are -1 .. dim, everything else is air.
struct Padded {
    const uint8_t* data;
    int dx, dy, dz;  // inner dimensions

    inline uint8_t at(int x, int y, int z) const {
        int px = x + 1, py = y + 1, pz = z + 1;
        if (px < 0 || py < 0 || pz < 0 || px > dx + 1 || py > dy + 1 || pz > dz + 1) return 0;
        return data[px + (dx + 2) * (py + static_cast<size_t>(dy + 2) * pz)];
    }
};

inline bool opaque(uint8_t m) { return m != 0 && m < vg::kFirstTransparentMaterial; }
inline bool translucent(uint8_t m) { return m >= vg::kFirstTransparentMaterial; }

/// True when the material at `m` emits a face against neighbour `n` for the requested pass.
inline bool facing(uint8_t m, uint8_t n, int pass) {
    if (pass == 0) return opaque(m) && !opaque(n);
    return translucent(m) && !opaque(n) && n != m;
}

inline int aoLevel(int side1, int side2, int corner) {
    if (side1 && side2) return 0;
    return 3 - (side1 + side2 + corner);
}

/// One entry of the greedy merge mask. Zero material means "no face here".
struct MaskCell {
    uint8_t material = 0;
    int8_t sign = 0;
    uint8_t ao[4] = {3, 3, 3, 3};

    bool operator==(const MaskCell& o) const {
        return material == o.material && sign == o.sign && ao[0] == o.ao[0] && ao[1] == o.ao[1] &&
               ao[2] == o.ao[2] && ao[3] == o.ao[3];
    }
};

/// Corner occlusion of one face cell. `airAxis` is the layer the face opens into.
void computeAo(const Padded& vol, int axis, int u, int v, int uIndex, int vIndex, int airAxis,
               uint8_t out[4]) {
    for (int corner = 0; corner < 4; corner++) {
        const int cu = (corner == 1 || corner == 2) ? 1 : 0;
        const int cv = (corner >= 2) ? 1 : 0;
        const int du = cu ? 1 : -1;
        const int dv = cv ? 1 : -1;

        int side1[3], side2[3], diag[3];
        side1[axis] = side2[axis] = diag[axis] = airAxis;
        side1[u] = uIndex + du;  side1[v] = vIndex;
        side2[u] = uIndex;       side2[v] = vIndex + dv;
        diag[u] = uIndex + du;   diag[v] = vIndex + dv;

        const int a = opaque(vol.at(side1[0], side1[1], side1[2])) ? 1 : 0;
        const int b = opaque(vol.at(side2[0], side2[1], side2[2])) ? 1 : 0;
        const int c = opaque(vol.at(diag[0], diag[1], diag[2])) ? 1 : 0;
        out[corner] = static_cast<uint8_t>(aoLevel(a, b, c));
    }
}

void emitQuad(std::vector<Vertex>& vertices, std::vector<uint32_t>& indices, const int base[3],
              const int du[3], const int dv[3], int axis, int sign, const MaskCell& cell,
              float scale) {
    float n[3] = {0, 0, 0};
    n[axis] = static_cast<float>(sign);

    int corners[4][3];
    for (int k = 0; k < 3; k++) {
        corners[0][k] = base[k];
        corners[1][k] = base[k] + du[k];
        corners[2][k] = base[k] + du[k] + dv[k];
        corners[3][k] = base[k] + dv[k];
    }

    const uint32_t first = static_cast<uint32_t>(vertices.size());
    for (int i = 0; i < 4; i++) {
        Vertex vert{};
        vert.x = corners[i][0] * scale;
        vert.y = corners[i][1] * scale;
        vert.z = corners[i][2] * scale;
        vert.nx = n[0];
        vert.ny = n[1];
        vert.nz = n[2];
        vert.material = static_cast<float>(cell.material);
        vert.ao = 0.35f + 0.65f * (cell.ao[i] / 3.0f);
        vertices.push_back(vert);
    }

    // Flip the diagonal so the interpolated AO gradient does not break along the shared edge.
    const bool flip = (cell.ao[0] + cell.ao[2]) < (cell.ao[1] + cell.ao[3]);
    uint32_t order[6];
    if (flip) {
        order[0] = first + 1; order[1] = first + 2; order[2] = first + 3;
        order[3] = first + 1; order[4] = first + 3; order[5] = first;
    } else {
        order[0] = first; order[1] = first + 1; order[2] = first + 2;
        order[3] = first; order[4] = first + 2; order[5] = first + 3;
    }
    if (sign < 0) {  // keep counter-clockwise winding when the face points down the axis
        std::swap(order[1], order[2]);
        std::swap(order[4], order[5]);
    }
    indices.insert(indices.end(), order, order + 6);
}

void meshVolume(const Padded& vol, int pass, float scale, std::vector<Vertex>& vertices,
                std::vector<uint32_t>& indices) {
    const int dim[3] = {vol.dx, vol.dy, vol.dz};
    const int maskSize = std::max({dim[0] * dim[1], dim[1] * dim[2], dim[0] * dim[2]});
    std::vector<MaskCell> mask(static_cast<size_t>(maskSize));

    for (int axis = 0; axis < 3; axis++) {
        const int u = (axis + 1) % 3;
        const int v = (axis + 2) % 3;
        int q[3] = {0, 0, 0};
        q[axis] = 1;

        int x[3] = {0, 0, 0};
        for (x[axis] = -1; x[axis] < dim[axis];) {
            int n = 0;
            for (x[v] = 0; x[v] < dim[v]; x[v]++) {
                for (x[u] = 0; x[u] < dim[u]; x[u]++, n++) {
                    const uint8_t a = vol.at(x[0], x[1], x[2]);
                    const uint8_t b = vol.at(x[0] + q[0], x[1] + q[1], x[2] + q[2]);
                    MaskCell cell;
                    if (facing(a, b, pass)) {
                        cell.material = a;
                        cell.sign = 1;
                        computeAo(vol, axis, u, v, x[u], x[v], x[axis] + 1, cell.ao);
                    } else if (facing(b, a, pass)) {
                        cell.material = b;
                        cell.sign = -1;
                        computeAo(vol, axis, u, v, x[u], x[v], x[axis], cell.ao);
                    }
                    mask[n] = cell;
                }
            }

            x[axis]++;  // the quad plane sits between the two sampled layers
            n = 0;
            for (int j = 0; j < dim[v]; j++) {
                for (int i = 0; i < dim[u];) {
                    const MaskCell cell = mask[n];
                    if (cell.material == 0) {
                        i++;
                        n++;
                        continue;
                    }
                    int w = 1;
                    while (i + w < dim[u] && mask[n + w] == cell) w++;
                    int h = 1;
                    bool blocked = false;
                    while (j + h < dim[v] && !blocked) {
                        for (int k = 0; k < w; k++) {
                            if (!(mask[n + k + h * dim[u]] == cell)) {
                                blocked = true;
                                break;
                            }
                        }
                        if (!blocked) h++;
                    }

                    x[u] = i;
                    x[v] = j;
                    int du[3] = {0, 0, 0}, dv[3] = {0, 0, 0};
                    du[u] = w;
                    dv[v] = h;
                    emitQuad(vertices, indices, x, du, dv, axis, cell.sign, cell, scale);

                    for (int l = 0; l < h; l++)
                        for (int k = 0; k < w; k++) mask[n + k + l * dim[u]] = MaskCell{};
                    i += w;
                    n += w;
                }
            }
        }
    }
}

jbyteArray pack(JNIEnv* env, const std::vector<Vertex>& vertices,
                const std::vector<uint32_t>& indices) {
    const int32_t vc = static_cast<int32_t>(vertices.size());
    const int32_t ic = static_cast<int32_t>(indices.size());
    const size_t bytes = 8 + vertices.size() * sizeof(Vertex) + indices.size() * sizeof(uint32_t);

    jbyteArray out = env->NewByteArray(static_cast<jsize>(bytes));
    if (out == nullptr) return nullptr;
    std::vector<uint8_t> packed(bytes);
    std::memcpy(packed.data(), &vc, 4);
    std::memcpy(packed.data() + 4, &ic, 4);
    if (vc > 0) std::memcpy(packed.data() + 8, vertices.data(), vertices.size() * sizeof(Vertex));
    if (ic > 0) {
        std::memcpy(packed.data() + 8 + vertices.size() * sizeof(Vertex), indices.data(),
                    indices.size() * sizeof(uint32_t));
    }
    env->SetByteArrayRegion(out, 0, static_cast<jsize>(bytes),
                            reinterpret_cast<const jbyte*>(packed.data()));
    return out;
}

}  // namespace

extern "C" JNIEXPORT jbyteArray JNICALL Java_com_ccs_voxelstrike_voxel_NativeBridge_greedyMesh(
        JNIEnv* env, jclass, jbyteArray padded, jint sx, jint sy, jint sz, jfloat scale, jint pass) {
    jbyte* raw = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(padded, nullptr));
    if (raw == nullptr) return nullptr;

    Padded vol{reinterpret_cast<const uint8_t*>(raw), sx, sy, sz};
    std::vector<Vertex> vertices;
    std::vector<uint32_t> indices;
    vertices.reserve(4096);
    indices.reserve(6144);
    meshVolume(vol, pass, scale, vertices, indices);

    env->ReleasePrimitiveArrayCritical(padded, raw, JNI_ABORT);
    return pack(env, vertices, indices);
}
