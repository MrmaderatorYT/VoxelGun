// Forward pass for translucent voxels (glass, grates, water). Shaded analytically and blended
// over the resolved HDR image, with a fresnel sky reflection so panes read as glass.
#include "common.glsl"

in vec3 vWorldPosition;
in vec3 vNormal;
in float vMaterial;
in float vOcclusion;

layout(location = 0) out vec4 outColor;

uniform float uSunIntensity;

void main() {
    vec3 normal = normalize(vNormal);
    Material material = readMaterial(uint(vMaterial + 0.5));
    vec3 view = normalize(uCameraPosition - vWorldPosition);
    if (dot(normal, view) < 0.0) {
        normal = -normal;
    }

    float ndl = max(dot(normal, uSunDirection), 0.0);
    vec3 diffuse = material.albedo * (skyColor(normal) * 0.6
            + uSunColor * uSunIntensity * ndl * 0.35);

    vec3 reflected = reflect(-view, normal);
    float fresnel = pow(clamp(1.0 - max(dot(normal, view), 0.0), 0.0, 1.0), 4.0);
    vec3 specular = sunAndSky(reflected) * mix(0.06, 0.85, fresnel);

    float alpha = mix(0.18, 0.62, fresnel) + material.metallic * 0.3;
    outColor = vec4((diffuse + specular + material.emissive) * vOcclusion, clamp(alpha, 0.1, 0.9));
}
