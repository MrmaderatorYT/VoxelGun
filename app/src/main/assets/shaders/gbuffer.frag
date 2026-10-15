// Packs surface data for the deferred and ray traced passes:
//   rg = octahedral normal, b = material id, a = baked corner occlusion.
// The second target holds the screen-space motion vector in UV units.
#include "common.glsl"

in vec3 vWorldPosition;
in vec3 vNormal;
in float vMaterial;
in float vOcclusion;
in vec4 vCurrentClip;
in vec4 vPreviousClip;
uniform float uCharacterTeam;
uniform float uViewModel;

layout(location = 0) out vec4 outSurface;
layout(location = 1) out vec2 outVelocity;

void main() {
    vec3 normal = normalize(vNormal);
    // Values below the world AO range are tags consumed by the composite pass. Characters use
    // 0.025/0.050 for their team outline. Reserve 0.085..0.115 for the first-person rig,
    // retaining its corner AO so small rails, grip grooves and panels remain legible.
    float ao = uViewModel > 0.5 ? 0.085 + 0.030 * clamp(vOcclusion, 0.15, 1.0)
            : uCharacterTeam > 0.5 ? uCharacterTeam * 0.025
            : clamp(vOcclusion, 0.15, 1.0);
    outSurface = vec4(encodeNormal(normal), clamp(vMaterial / 255.0, 0.0, 1.0), ao);

    vec2 velocity = vec2(0.0);
    if (vCurrentClip.w > 1e-5 && vPreviousClip.w > 1e-5) {
        vec2 current = vCurrentClip.xy / vCurrentClip.w;
        vec2 previous = vPreviousClip.xy / vPreviousClip.w;
        velocity = (current - previous) * 0.5;  // NDC delta to UV delta
    }
    outVelocity = velocity;
}
