// The ray tracing pass.
//
// Per pixel it fires a jittered sun shadow ray, samples the emissive blocks directly through
// next event estimation, traces one or more cosine-weighted bounce rays for global illumination,
// and — when REFLECTIONS is defined — a roughness-perturbed mirror ray. Results are accumulated
// over time by following the motion vectors, then cleaned up by the a-trous pass.

#include "common.glsl"
#include "surface_material.glsl"
#include "voxel_trace.glsl"
#include "lights.glsl"

in vec2 vUv;

layout(location = 0) out vec4 outLight;       // rgb: indirect radiance, a: sun visibility
layout(location = 1) out vec4 outDepth;       // r: linear depth (history validation)
#ifdef REFLECTIONS
layout(location = 2) out vec4 outReflection;  // rgb: reflected radiance, a: hit mask
#endif

uniform sampler2D uSurface;
uniform sampler2D uDepth;
uniform sampler2D uVelocity;
uniform sampler2D uHistoryLight;
uniform sampler2D uHistoryDepth;
#ifdef REFLECTIONS
uniform sampler2D uHistoryReflection;
#endif

uniform uint uFrameIndex;
uniform int uGiSamples;
uniform int uShadowSteps;
uniform int uGiSteps;
uniform float uGiRange;
uniform float uHistoryBlend;
uniform float uSunIntensity;

/**
 * Shades a secondary hit: sun with its own shadow ray, the emissive blocks through NEE, and a
 * sky fill. The hit's own emission is deliberately left out — next event estimation already
 * accounts for light arriving from emissive surfaces, and adding both would double count it.
 */
vec3 shadeHit(TraceHit hit, inout uint seed, int shadowSteps) {
    // Coarser secondary-ray footprint suppresses tiny patterns in accumulated GI/reflections.
    Material material = readSurfaceMaterial(hit.material, hit.position, hit.normal, 0.12);
    vec3 origin = hit.position + hit.normal * 0.03;
    vec3 sunDir = jitterSun(uSunDirection, uSunAngle, seed);
    float ndl = max(dot(hit.normal, sunDir), 0.0);
    float visibility = 0.0;
    if (ndl > 0.0) {
        visibility = traceShadow(origin, sunDir, 240.0, shadowSteps) ? 0.0 : 1.0;
    }
    vec3 direct = material.albedo * uSunColor * uSunIntensity * ndl * visibility;
    vec3 local = material.albedo * sampleLights(origin, hit.normal, seed, shadowSteps);
    vec3 ambient = material.albedo * skyColor(hit.normal) * 0.30;
    return direct + local + ambient;
}

void main() {
    float depth = texture(uDepth, vUv).r;
    if (depth >= 0.99999) {
        outLight = vec4(0.0, 0.0, 0.0, 1.0);
        outDepth = vec4(1.0e6, 0.0, 0.0, 1.0);
#ifdef REFLECTIONS
        outReflection = vec4(0.0);
#endif
        return;
    }

    vec4 surface = texture(uSurface, vUv);
    vec3 normal = decodeNormal(surface.rg);
    uint materialId = uint(surface.b * 255.0 + 0.5);
    Material material = readMaterial(materialId);
    vec3 world = worldFromDepth(vUv, depth);
    vec3 origin = world + normal * 0.035;

    uint seed = hashUint(uint(gl_FragCoord.x) * 1973u + uint(gl_FragCoord.y) * 9277u
                         + uFrameIndex * 26699u);

    // Sun visibility.
    vec3 sunDir = jitterSun(uSunDirection, uSunAngle, seed);
    float ndl = dot(normal, sunDir);
    float sunVisibility = 0.0;
    if (ndl > 0.0) {
        sunVisibility = traceShadow(origin, sunDir, 260.0, uShadowSteps) ? 0.0 : 1.0;
    }

    // Direct light from the emissive blocks. This is irradiance, so the composite multiplies it
    // by the albedo along with the bounce term.
    vec3 indirect = sampleLights(origin, normal, seed, max(uShadowSteps, 32));

    // Indirect diffuse.
    vec3 bounce = vec3(0.0);
    int samples = max(uGiSamples, 0);
    for (int i = 0; i < 4; i++) {
        if (i >= samples) {
            break;
        }
        vec3 direction = sampleCosine(normal, seed);
        TraceHit hit;
        if (traceVoxels(origin, direction, uGiRange, uGiSteps, false, hit)) {
            bounce += shadeHit(hit, seed, max(uShadowSteps / 2, 24));
        } else {
            bounce += skyColor(direction);
        }
    }
    if (samples > 0) {
        indirect += bounce / float(samples);
    } else {
        indirect += skyColor(normal) * 0.6;
    }

    vec4 current = vec4(indirect, sunVisibility);

#ifdef REFLECTIONS
    vec4 reflection = vec4(0.0);
    if (material.roughness < 0.55) {
        vec3 view = normalize(world - uCameraPosition);
        vec3 mirror = reflect(view, normal);
        // Roughness cone: rougher surfaces scatter the ray more.
        vec3 scattered = normalize(mirror + sampleCosine(mirror, seed) * material.roughness * 0.65);
        TraceHit hit;
        if (traceVoxels(origin, scattered, 90.0, uGiSteps + 32, true, hit)) {
            Material hitMaterial = readMaterial(hit.material);
            // Reflections do show the emitters themselves, so emission belongs here.
            reflection = vec4(shadeHit(hit, seed, max(uShadowSteps / 2, 24))
                              + hitMaterial.emissive, 1.0);
        } else {
            reflection = vec4(sunAndSky(scattered), 1.0);
        }
    }
#endif

    // Temporal reprojection along the motion vector, so moving bodies stop smearing.
    float linear = linearizeDepth(depth);
    vec2 velocity = texture(uVelocity, vUv).rg;
    vec2 previousUv = vUv - velocity;
    if (all(greaterThanEqual(previousUv, vec2(0.0)))
            && all(lessThanEqual(previousUv, vec2(1.0)))) {
        float previousLinear = texture(uHistoryDepth, previousUv).r;
        if (abs(previousLinear - linear) < max(0.2, linear * 0.05)) {
            current = mix(current, texture(uHistoryLight, previousUv), uHistoryBlend);
#ifdef REFLECTIONS
            if (reflection.a > 0.0) {
                vec4 history = texture(uHistoryReflection, previousUv);
                if (history.a > 0.0) {
                    reflection = mix(reflection, history, uHistoryBlend);
                }
            }
#endif
        }
    }

    outLight = current;
    outDepth = vec4(linear, 0.0, 0.0, 1.0);
#ifdef REFLECTIONS
    outReflection = reflection;
#endif
}
