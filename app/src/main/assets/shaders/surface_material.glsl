// Original, code-generated arena textures. World-space projection preserves greedy meshing:
// one large face still has the same texel density as a small voxel. No atlas or extra draw calls.
// footprint is world units per screen pixel; small detail fades before it can shimmer.
#ifdef CACHED_SURFACE_NOISE
uniform sampler2D uSurfaceNoise;
#endif
float surfaceHash(vec2 cell) {
#ifdef CACHED_SURFACE_NOISE
    return texelFetch(uSurfaceNoise, ivec2(cell) & ivec2(255), 0).r;
#else
    vec3 p = fract(vec3(cell.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
#endif
}

float surfaceNoise(vec2 uv) {
    vec2 cell = floor(uv), f = fract(uv);
    f = f * f * (3.0 - 2.0 * f);
#ifdef CACHED_SURFACE_NOISE
    // Hardware bilinear interpolation replaces four hash evaluations/texture reads.
    return texture(uSurfaceNoise, (cell + f + 0.5) / 256.0).r;
#else
    return mix(mix(surfaceHash(cell), surfaceHash(cell + vec2(1, 0)), f.x),
               mix(surfaceHash(cell + vec2(0, 1)), surfaceHash(cell + vec2(1, 1)), f.x), f.y);
#endif
}

float surfaceGrain(vec2 uv, float detail) {
    if (detail <= 0.0) return 0.5;
    return mix(0.5, surfaceHash(floor(uv * 8.0)), detail);
}

float surfacePatches(vec2 uv, float footprint) {
    float weight = 1.0 - smoothstep(0.5, 2.0, footprint);
    if (weight <= 0.0) return 0.5;
    return mix(0.5, surfaceNoise(uv * 0.65), weight);
}

// A filtered seam around a repeating cell edge. Avoid high-frequency hard step patterns.
float surfaceSeam(float coordinate, float width, float footprint) {
    float distanceToEdge = min(fract(coordinate), 1.0 - fract(coordinate));
    float aa = max(0.002, footprint);
    return 1.0 - smoothstep(width - aa, width + aa, distanceToEdge);
}

bool brickMaterial(uint id) {
    return id == 12u || (id >= 68u && id <= 73u) || (id >= 91u && id <= 95u)
            || id == 4u;
}

bool woodMaterial(uint id) {
    return id == 5u || id == 6u || id == 7u || id == 34u || id == 56u
            || id == 83u || id == 84u;
}

Material readSurfaceMaterial(uint id, vec3 position, vec3 normal, float footprint) {
    Material m = readMaterial(id);
    // Models/UI use the plain palette. Only callers shading world surfaces use this function.
    vec3 axis = abs(normal);
    vec2 uv = axis.y >= max(axis.x, axis.z) ? position.xz
            : axis.x > axis.z ? position.zy : position.xy;
    float detail = 1.0 - smoothstep(0.04, 0.18, footprint);
    float structure = 1.0 - smoothstep(0.12, 0.65, footprint);
    if (id == 14u || id == 54u) {
        float grain = surfaceGrain(uv, detail);
        float patches = surfacePatches(uv, footprint);
        // Top = green turf. Vertical faces = earth with a narrow root/turf edge.
        vec3 earth = vec3(0.38, 0.255, 0.14) * (0.86 + grain * 0.28);
        vec3 turf = m.albedo * (0.76 + patches * 0.34 + grain * 0.18);
        vec2 bladeCell = floor(uv * 7.0);
        vec2 blade = fract(uv * 7.0);
        float lean = surfaceHash(bladeCell) * 0.44 - 0.22;
        float stroke = 1.0 - smoothstep(0.08, 0.22,
                abs(blade.x - 0.5 - (blade.y - 0.5) * lean));
        stroke *= smoothstep(0.12, 0.3, blade.y) * (1.0 - smoothstep(0.6, 0.94, blade.y));
        turf = mix(turf, m.albedo * vec3(1.08, 1.14, 0.83), stroke * detail * 0.42);
        float turfCover = normal.y > 0.5 ? 1.0 : normal.y < -0.5 ? 0.0
                : smoothstep(0.74, 0.88, fract(position.y - 0.001));
        m.albedo = mix(earth, turf, turfCover);
    } else if (brickMaterial(id)) {
        float grain = surfaceGrain(uv, detail);
        float row = floor(uv.y / 0.38);
        vec2 brickUv = vec2(uv.x / 1.15 + mod(row, 2.0) * 0.5, uv.y / 0.38);
        float mortar = max(surfaceSeam(brickUv.x, 0.025, footprint / 1.15),
                           surfaceSeam(brickUv.y, 0.045, footprint / 0.38));
        float variation = surfaceHash(floor(brickUv));
        vec3 brick = m.albedo * (0.88 + variation * 0.20 + (grain - 0.5) * 0.09);
        // Warm, low-contrast mortar, not metre-wide alternating voxel blocks.
        vec3 grout = mix(m.albedo, vec3(0.70, 0.67, 0.59), 0.55) * 0.72;
        m.albedo = mix(m.albedo, mix(brick, grout, mortar), structure);
    } else if (woodMaterial(id)) {
        vec2 boardUv = vec2(uv.x / 3.6, uv.y / 0.52);
        if (axis.y > 0.5) boardUv = vec2(uv.y / 3.6, uv.x / 0.52);
        if (id == 34u) boardUv = vec2(uv.x / 0.32, uv.y / 4.0); // vertical bark
        float seam = max(surfaceSeam(boardUv.y, 0.035, footprint / 0.52),
                        surfaceSeam(boardUv.x + mod(floor(boardUv.y), 2.0) * 0.5,
                                    0.009, footprint / 3.6));
        float fibers = surfaceNoise(vec2(boardUv.x * 3.0, boardUv.y * 18.0));
        float shade = 0.88 + surfaceHash(floor(boardUv)) * 0.20;
        m.albedo *= mix(1.0, shade * (0.89 + fibers * 0.20) * (1.0 - seam * 0.32), structure);
        m.roughness = max(m.roughness, 0.78);
    } else if (id == 13u) {
        float patches = surfacePatches(uv, footprint), grain = surfaceGrain(uv, detail);
        m.albedo *= 0.83 + patches * 0.20 + grain * 0.17;
        m.roughness = 0.96;
    } else if (id == 3u || id == 55u || id == 15u || id == 57u) {
        float patches = surfacePatches(uv, footprint), grain = surfaceGrain(uv, detail);
        m.albedo *= 0.83 + patches * 0.24 + grain * 0.12;
    } else if (id == 16u || id == 58u || id == 1u || id == 2u || id == 74u) {
        float patches = surfacePatches(uv, footprint), grain = surfaceGrain(uv, detail);
        m.albedo *= 0.88 + patches * 0.19 + grain * 0.07;
    } else if (id == 80u || id == 81u || id == 17u || id == 22u) {
        float seam = max(surfaceSeam(uv.x / 1.5, 0.015, footprint / 1.5),
                         surfaceSeam(uv.y / 1.5, 0.015, footprint / 1.5));
        m.albedo *= 1.0 - seam * structure * 0.28;
    } else if (id == 32u || id == 59u || id == 60u || id == 89u) {
        float grain = surfaceGrain(uv, detail);
        float seam = max(surfaceSeam(uv.x / 0.55, 0.035, footprint / 0.55),
                         surfaceSeam(uv.y / 0.8, 0.03, footprint / 0.8));
        m.albedo *= mix(1.0, (0.86 + grain * 0.18) * (1.0 - seam * 0.27), structure);
    } else if (id == 30u) {
        float patches = surfacePatches(uv, footprint), grain = surfaceGrain(uv, detail);
        m.albedo *= 0.95 + patches * 0.06 + grain * 0.025;
    } else if (id == 33u) {
        float patches = surfacePatches(uv, footprint), grain = surfaceGrain(uv, detail);
        m.albedo *= 0.78 + patches * 0.26 + grain * 0.20;
    } else if (id == 8u || id == 85u || id == 86u) {
        float seam = surfaceSeam(uv.x / 0.8, 0.016, footprint / 0.8);
        m.albedo *= 1.0 - seam * structure * 0.18;
    }
    return m;
}
