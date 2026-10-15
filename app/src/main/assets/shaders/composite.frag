// Resolves the G-buffer against the traced lighting into an HDR image.
#include "common.glsl"
#include "surface_material.glsl"

in vec2 vUv;
layout(location = 0) out vec4 outColor;

uniform sampler2D uSurface;
uniform sampler2D uDepth;
uniform sampler2D uLight;        // half resolution: rgb indirect, a sun visibility
uniform sampler2D uRtDepth;      // half resolution linear depth, for the bilateral upsample
uniform sampler2D uReflection;
uniform vec2 uLightTexelSize;
uniform float uSunIntensity;
uniform float uFogDensity;
uniform float uRayTracing;       // 0 = analytic fallback, 1 = traced lighting
uniform float uReflectionStrength;
uniform float uPlayerHighlights;
uniform mat4 uView;

#ifndef RASTER_ONLY
/** Depth-aware upsample of the half resolution lighting buffer. */
vec4 upsampleLight(vec2 uv, float centerDepth) {
    vec2 pixel = uv / uLightTexelSize - 0.5;
    vec2 base = floor(pixel);
    vec2 fraction = fract(pixel);
    vec4 result = vec4(0.0);
    float weightSum = 0.0;
    // Four depth-weighted bilinear taps instead of nine equally weighted neighbours.
    for (int y = 0; y < 2; y++) {
        for (int x = 0; x < 2; x++) {
            vec2 sampleUv = (base + vec2(float(x), float(y)) + 0.5) * uLightTexelSize;
            float spatial = (x == 0 ? 1.0 - fraction.x : fraction.x)
                    * (y == 0 ? 1.0 - fraction.y : fraction.y);
            float sampleDepth = texture(uRtDepth, sampleUv).r;
            float weight = spatial / (1.0 + abs(sampleDepth - centerDepth) * 8.0);
            result += texture(uLight, sampleUv) * weight;
            weightSum += weight;
        }
    }
    return weightSum > 0.0 ? result / weightSum : texture(uLight, uv);
}

vec3 fresnelSchlick(vec3 f0, float cosTheta) {
    return f0 + (1.0 - f0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
}
#endif

/** GGX specular for the direct sun term. */
float specularGgx(vec3 normal, vec3 view, vec3 light, float roughness) {
    vec3 half3 = normalize(view + light);
    float ndh = max(dot(normal, half3), 0.0);
    float ndv = max(dot(normal, view), 1e-4);
    float ndl = max(dot(normal, light), 0.0);
    float alpha = max(roughness * roughness, 0.002);
    float alpha2 = alpha * alpha;
    float denominator = ndh * ndh * (alpha2 - 1.0) + 1.0;
    float distribution = alpha2 / (PI * denominator * denominator);
    float k = alpha * 0.5;
    float visibility = 1.0 / ((ndl * (1.0 - k) + k) * (ndv * (1.0 - k) + k) + 1e-4);
    return distribution * visibility * 0.25;
}

void main() {
    float depth = texture(uDepth, vUv).r;
    vec3 viewRay = normalize(worldFromDepth(vUv, 1.0) - uCameraPosition);

    vec3 world = worldFromDepth(vUv, depth);
    // Evaluate derivatives before divergent branches (sky, models, different materials).
    float footprint = max(length(dFdx(world)), length(dFdy(world)));
    if (depth >= 0.99999) {
        outColor = vec4(sunAndSky(viewRay), 1.0);
        return;
    }

    vec4 surface = texture(uSurface, vUv);
    vec3 normal = decodeNormal(surface.rg);
    uint materialId = uint(surface.b * 255.0 + 0.5);
    bool character = surface.a > 0.01 && surface.a < 0.08;
    bool viewModel = surface.a > 0.08 && surface.a < 0.12;
    float occlusion = viewModel ? clamp((surface.a - 0.085) / 0.030, 0.15, 1.0)
            : character ? 1.0 : surface.a;
    Material material;
    if (character || viewModel) {
        material = readMaterial(materialId);
    } else {
        material = readSurfaceMaterial(materialId, world, normal, footprint);
    }
    vec3 view = normalize(uCameraPosition - world);
    float linear = linearizeDepth(depth);

    vec3 indirect;
    float sunVisibility;
#ifndef RASTER_ONLY
    if (uRayTracing > 0.5) {
        vec4 light = upsampleLight(vUv, linear);
        indirect = max(light.rgb, vec3(0.0));
        sunVisibility = clamp(light.a, 0.0, 1.0);
    } else
#endif
    {
        // Analytic fallback for the low preset: sky dome plus baked corner occlusion.
        indirect = skyColor(normal) * 0.65;
        sunVisibility = smoothstep(-0.05, 0.35, dot(normal, uSunDirection));
    }

    float ndl = max(dot(normal, uSunDirection), 0.0);
    vec3 f0 = mix(vec3(0.04), material.albedo, material.metallic);
    vec3 diffuseAlbedo = material.albedo * (1.0 - material.metallic);

    vec3 direct = diffuseAlbedo * uSunColor * uSunIntensity * ndl * sunVisibility;
    vec3 specular = f0 * uSunColor * uSunIntensity * ndl * sunVisibility
            * specularGgx(normal, view, uSunDirection, material.roughness);

    vec3 ambient = diffuseAlbedo * indirect * occlusion;

    vec3 color = direct + specular + ambient + material.emissive;

    // A carried weapon is always close enough to be lit by the player's side of the scene, even
    // when the ray-traced room happens to be completely black. Keep the PBR result, then add only
    // the missing minimum key/rim light. This preserves metal, wood and equipped skin colours
    // without turning the view model into an unlit HUD sprite.
    if (viewModel) {
        vec3 cameraKey = normalize(vec3(-0.45, 0.72, 0.52));
        vec3 cameraNormal = mat3(uView) * normal;
        float key = 0.40 + max(dot(cameraNormal, cameraKey), 0.0) * 0.46;
        float rim = pow(1.0 - max(dot(normal, view), 0.0), 2.5);
        vec3 readable = (material.albedo * key + vec3(0.05, 0.065, 0.08) * rim)
                * mix(0.55, 1.0, occlusion);
        color = max(color, readable + material.emissive);
    }

#ifndef RASTER_ONLY
    if (uReflectionStrength > 0.0 && material.roughness < 0.55) {
        vec4 reflection = texture(uReflection, vUv);
        if (reflection.a > 0.0) {
            vec3 fresnel = fresnelSchlick(f0, max(dot(normal, view), 0.0));
            float gloss = 1.0 - material.roughness / 0.55;
            color += reflection.rgb * fresnel * gloss * uReflectionStrength;
        }
    }
#endif

    // Exponential distance fog tinted by the sky in the view direction.
    float fog = 1.0 - exp(-linear * uFogDensity);
    color = mix(color, skyColor(-view) * 0.9, clamp(fog, 0.0, 0.92));
    if (character && uPlayerHighlights > 0.5) {
        vec3 teamColor = surface.a < 0.038 ? vec3(0.15, 0.65, 1.0) : vec3(1.0, 0.20, 0.12);
        vec2 texel = 1.0 / vec2(textureSize(uSurface, 0));
        float edge = 0.0;
        for (int axis = 0; axis < 4; axis++) {
            vec2 offset = axis == 0 ? vec2(texel.x, 0.0) : axis == 1 ? vec2(-texel.x, 0.0)
                : axis == 2 ? vec2(0.0, texel.y) : vec2(0.0, -texel.y);
            float other = texture(uSurface, vUv + offset).a;
            edge = max(edge, abs(other - surface.a) > 0.012 ? 1.0 : 0.0);
        }
        float rim = pow(1.0 - max(dot(normal, view), 0.0), 3.0);
        color = max(color, material.albedo * 0.28) + teamColor * (rim * 0.4 + edge * 0.75);
    }

    outColor = vec4(color, 1.0);
}
