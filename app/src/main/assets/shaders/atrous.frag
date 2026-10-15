// Edge-aware a-trous wavelet filter. Bounded 3x3 iterations with growing stride give a wide blur
// while depth and normal weights keep the traced lighting from leaking across surfaces.
#include "common.glsl"

in vec2 vUv;
layout(location = 0) out vec4 outColor;

uniform sampler2D uLight;
uniform sampler2D uLinearDepth;
uniform sampler2D uSurface;
uniform vec2 uTexelSize;
uniform float uStride;
uniform float uDepthSigma;
uniform float uNormalSigma;

const float KERNEL[2] = float[2](0.5, 0.25);

void main() {
    vec4 center = texture(uLight, vUv);
    float centerDepth = texture(uLinearDepth, vUv).r;
    if (centerDepth > 50000.0) {
        outColor = center;
        return;
    }
    vec3 centerNormal = decodeNormal(texture(uSurface, vUv).rg);

    vec4 sum = vec4(0.0);
    float weightSum = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 offset = vec2(float(x), float(y)) * uStride * uTexelSize;
            vec2 uv = vUv + offset;
            if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) {
                continue;
            }
            float sampleDepth = texture(uLinearDepth, uv).r;
            if (sampleDepth > 50000.0) {
                continue;
            }
            vec3 sampleNormal = decodeNormal(texture(uSurface, uv).rg);

            float depthWeight = exp(-abs(sampleDepth - centerDepth)
                                    / (uDepthSigma * max(centerDepth, 1.0)));
            float normalWeight = pow(max(dot(centerNormal, sampleNormal), 0.0), uNormalSigma);
            float kernel = KERNEL[abs(x)] * KERNEL[abs(y)];
            float weight = kernel * depthWeight * normalWeight;

            sum += texture(uLight, uv) * weight;
            weightSum += weight;
        }
    }
    outColor = weightSum > 0.0 ? sum / weightSum : center;
}
