// Next event estimation over the map's emissive blocks.
//
// Bounce rays alone find neon strips and lamps only by luck, which is exactly why the emissive
// maps look grainy. Here every shaded point instead picks a light directly. Candidates are drawn
// with resampled importance sampling (RIS): a handful are scored by their unshadowed
// contribution, one is kept in proportion to that score, and only that one pays for a shadow ray.

uniform highp sampler2D uLights;  // 2 texels per light: (position, radius), (colour, unused)
uniform int uLightCount;
uniform float uLightRange;

const int LIGHT_CANDIDATES = 4;

struct LightSample {
    vec3 direction;
    float distance;
    /// How far the shadow ray may travel before it would strike the emitter itself.
    float maxTrace;
    vec3 contribution;  // unshadowed irradiance
};

/// Unshadowed contribution of light `index` at a surface point.
LightSample evaluateLight(int index, vec3 position, vec3 normal) {
    LightSample result;
    result.direction = vec3(0.0, 1.0, 0.0);
    result.distance = 0.0;
    result.maxTrace = 0.0;
    result.contribution = vec3(0.0);

    vec4 sphere = texelFetch(uLights, ivec2(index * 2, 0), 0);
    vec4 colour = texelFetch(uLights, ivec2(index * 2 + 1, 0), 0);

    vec3 toLight = sphere.xyz - position;
    float distanceSquared = dot(toLight, toLight);
    if (distanceSquared > uLightRange * uLightRange || distanceSquared < 1e-6) {
        return result;
    }
    float distance = length(toLight);
    vec3 direction = toLight / distance;
    float ndl = dot(normal, direction);
    if (ndl <= 0.0) {
        return result;
    }
    // Treat the cluster as a small sphere so nearby blocks do not blow up.
    float radius = max(sphere.w, 0.5);
    float attenuation = 1.0 / (1.0 + distance * distance / (radius * radius));

    result.direction = direction;
    result.distance = distance;
    result.maxTrace = max(distance - max(radius, 0.75), 0.05);
    result.contribution = colour.rgb * ndl * attenuation;
    return result;
}

float luminance(vec3 colour) {
    return dot(colour, vec3(0.2126, 0.7152, 0.0722));
}

/// Irradiance arriving at `position` from the emissive blocks, with one traced shadow ray.
vec3 sampleLights(vec3 position, vec3 normal, inout uint seed, int shadowSteps) {
    if (uLightCount <= 0) {
        return vec3(0.0);
    }

    LightSample chosen;
    chosen.contribution = vec3(0.0);
    chosen.direction = vec3(0.0, 1.0, 0.0);
    chosen.distance = 0.0;
    chosen.maxTrace = 0.0;
    float weightSum = 0.0;
    float chosenTarget = 0.0;

    for (int i = 0; i < LIGHT_CANDIDATES; i++) {
        int index = int(randomFloat(seed) * float(uLightCount));
        index = clamp(index, 0, uLightCount - 1);
        LightSample candidate = evaluateLight(index, position, normal);
        float target = luminance(candidate.contribution);
        if (target <= 0.0) {
            continue;
        }
        // Source pdf is uniform (1/count), so the resampling weight is target * count.
        float weight = target * float(uLightCount);
        weightSum += weight;
        if (randomFloat(seed) * weightSum < weight) {
            chosen = candidate;
            chosenTarget = target;
        }
    }

    if (chosenTarget <= 0.0 || weightSum <= 0.0) {
        return vec3(0.0);
    }
    if (traceShadow(position, chosen.direction, chosen.maxTrace, shadowSteps)) {
        return vec3(0.0);
    }
    float estimator = (weightSum / float(LIGHT_CANDIDATES)) / chosenTarget;
    return chosen.contribution * estimator;
}
