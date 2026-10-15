#pragma once

#include <cstdint>
#include <cstddef>

namespace vg {

/// Dense voxel volume owned by native code. Material 0 is air.
struct Volume {
    int sx = 0, sy = 0, sz = 0;
    uint8_t* data = nullptr;

    inline size_t count() const { return static_cast<size_t>(sx) * sy * sz; }
    inline bool inside(int x, int y, int z) const {
        return x >= 0 && y >= 0 && z >= 0 && x < sx && y < sy && z < sz;
    }
    inline uint8_t at(int x, int y, int z) const {
        if (!inside(x, y, z)) return 0;
        return data[static_cast<size_t>(x) + static_cast<size_t>(sx) * (y + static_cast<size_t>(sy) * z)];
    }
    inline void put(int x, int y, int z, uint8_t m) {
        if (!inside(x, y, z)) return;
        data[static_cast<size_t>(x) + static_cast<size_t>(sx) * (y + static_cast<size_t>(sy) * z)] = m;
    }
};

/// Materials above this id do not block bullets or sight (glass panes, foliage, water).
constexpr uint8_t kFirstTransparentMaterial = 200;
inline bool blocksSight(uint8_t m) { return m != 0 && m < kFirstTransparentMaterial; }
inline bool blocksMovement(uint8_t m) { return m != 0 && m != 203 /* water */; }

}  // namespace vg
