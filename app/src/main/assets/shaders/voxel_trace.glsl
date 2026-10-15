// Voxel ray tracing core.
//
// Traversal is a DDA over the material volume, accelerated by a brick-resolution Chebyshev
// empty-space distance field: when the current brick reports distance d, the whole (2d-1)^3
// neighbourhood is empty and the ray jumps straight to the exit of that box.

uniform highp usampler3D uVoxels;
uniform highp usampler3D uBricks;
uniform vec3 uVolumeSize;
uniform vec3 uBrickSize;

const int BRICK_SHIFT = 3;      // 8 voxels per brick, matches VoxelGpuScene.BRICK
const float BRICK_SCALE = 8.0;

struct TraceHit {
    float t;
    vec3 position;
    vec3 normal;
    uint material;
};

// Slab test against the volume bounds.
bool intersectVolume(vec3 origin, vec3 invDir, out float tMin, out float tMax) {
    vec3 t0 = (vec3(0.0) - origin) * invDir;
    vec3 t1 = (uVolumeSize - origin) * invDir;
    vec3 lo = min(t0, t1);
    vec3 hi = max(t0, t1);
    tMin = max(max(lo.x, lo.y), lo.z);
    tMax = min(min(hi.x, hi.y), hi.z);
    return tMax > max(tMin, 0.0);
}

/**
 * Traces a ray through the volume.
 *
 * @param includeTranslucent when false, glass and water are passed through (bullets, sight)
 */
bool traceVoxels(vec3 origin, vec3 direction, float maxDistance, int maxSteps,
                 bool includeTranslucent, out TraceHit hit) {
    vec3 safeDir = direction;
    safeDir.x = abs(safeDir.x) < 1e-6 ? 1e-6 : safeDir.x;
    safeDir.y = abs(safeDir.y) < 1e-6 ? 1e-6 : safeDir.y;
    safeDir.z = abs(safeDir.z) < 1e-6 ? 1e-6 : safeDir.z;
    vec3 invDir = 1.0 / safeDir;
    vec3 positiveStep = step(0.0, safeDir);

    float tEnter;
    float tExit;
    if (!intersectVolume(origin, invDir, tEnter, tExit)) {
        return false;
    }
    float t = max(tEnter + 1e-3, 0.0);
    float limit = min(maxDistance, tExit);
    if (t > limit) {
        return false;
    }

    // The entry face and every skipped box use an axis normal, just like a single DDA step.
    vec3 entry = min(-origin * invDir, (uVolumeSize - origin) * invDir);
    int axisIndex = entry.x >= entry.y && entry.x >= entry.z ? 0 : entry.y >= entry.z ? 1 : 2;
    vec3 lastAxis = vec3(axisIndex == 0 ? -sign(safeDir.x) : 0.0,
                        axisIndex == 1 ? -sign(safeDir.y) : 0.0,
                        axisIndex == 2 ? -sign(safeDir.z) : 0.0);

    for (int i = 0; i < maxSteps; i++) {
        if (t > limit) {
            return false;
        }
        vec3 point = origin + safeDir * t;
        ivec3 voxel = ivec3(floor(point));
        voxel = clamp(voxel, ivec3(0), ivec3(uVolumeSize) - 1);

        ivec3 brick = voxel >> BRICK_SHIFT;
        uint skip = texelFetch(uBricks, brick, 0).r;
        if (skip > 0u) {
            // The empty box spans bricks [brick - (skip-1), brick + (skip-1)].
            float radius = float(skip - 1u);
            vec3 boxMin = (vec3(brick) - radius) * BRICK_SCALE;
            vec3 boxMax = (vec3(brick) + radius + 1.0) * BRICK_SCALE;
            vec3 planes = mix(boxMin, boxMax, positiveStep);
            vec3 distances = (planes - origin) * invDir;
            float jump = min(min(distances.x, distances.y), distances.z);
            axisIndex = distances.x <= distances.y && distances.x <= distances.z ? 0
                    : distances.y <= distances.z ? 1 : 2;
            lastAxis = vec3(axisIndex == 0 ? -sign(safeDir.x) : 0.0,
                            axisIndex == 1 ? -sign(safeDir.y) : 0.0,
                            axisIndex == 2 ? -sign(safeDir.z) : 0.0);
            t = max(t + 1e-3, jump + 1e-3);
            continue;
        }

        uint material = texelFetch(uVoxels, voxel, 0).r;
        bool blocking = material != 0u && (includeTranslucent || material < FIRST_TRANSLUCENT);
        if (blocking) {
            hit.t = t;
            hit.position = point;
            hit.material = material;
            hit.normal = lastAxis;
            return true;
        }

        // Step to the next voxel boundary.
        vec3 boundary = vec3(voxel) + positiveStep;
        vec3 distances = (boundary - point) * invDir;
        float step_t = min(min(distances.x, distances.y), distances.z);
        if (distances.x <= distances.y && distances.x <= distances.z) {
            axisIndex = 0;
        } else if (distances.y <= distances.z) {
            axisIndex = 1;
        } else {
            axisIndex = 2;
        }
        lastAxis = vec3(axisIndex == 0 ? -sign(safeDir.x) : 0.0,
                        axisIndex == 1 ? -sign(safeDir.y) : 0.0,
                        axisIndex == 2 ? -sign(safeDir.z) : 0.0);
        t += max(step_t, 1e-4) + 1e-4;
    }
    return false;
}

/** Occlusion-only variant: cheaper because it stops at the first blocker. */
bool traceShadow(vec3 origin, vec3 direction, float maxDistance, int maxSteps) {
    TraceHit hit;
    return traceVoxels(origin, direction, maxDistance, maxSteps, false, hit);
}

/** Perturbs the sun direction inside its angular radius for soft traced shadows. */
vec3 jitterSun(vec3 direction, float angle, inout uint seed) {
    if (angle <= 0.0005) {
        return direction;
    }
    vec3 up = abs(direction.y) < 0.92 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 tangent = normalize(cross(up, direction));
    vec3 bitangent = cross(direction, tangent);
    float theta = randomFloat(seed) * 2.0 * PI;
    float radius = sqrt(randomFloat(seed)) * angle;
    return normalize(direction + (tangent * cos(theta) + bitangent * sin(theta)) * tan(radius));
}
