// Karis-average 13-tap downsample; the first level also applies the bright pass.
precision highp float;

in vec2 vUv;
layout(location = 0) out vec4 outColor;

uniform sampler2D uSource;
uniform vec2 uTexelSize;
uniform float uThreshold;
uniform float uFirstLevel;

vec3 fetch(vec2 uv) {
    return texture(uSource, uv).rgb;
}

void main() {
    vec2 t = uTexelSize;
    vec3 a = fetch(vUv + vec2(-2.0, 2.0) * t);
    vec3 b = fetch(vUv + vec2(0.0, 2.0) * t);
    vec3 c = fetch(vUv + vec2(2.0, 2.0) * t);
    vec3 d = fetch(vUv + vec2(-2.0, 0.0) * t);
    vec3 e = fetch(vUv);
    vec3 f = fetch(vUv + vec2(2.0, 0.0) * t);
    vec3 g = fetch(vUv + vec2(-2.0, -2.0) * t);
    vec3 h = fetch(vUv + vec2(0.0, -2.0) * t);
    vec3 i = fetch(vUv + vec2(2.0, -2.0) * t);
    vec3 j = fetch(vUv + vec2(-1.0, 1.0) * t);
    vec3 k = fetch(vUv + vec2(1.0, 1.0) * t);
    vec3 l = fetch(vUv + vec2(-1.0, -1.0) * t);
    vec3 m = fetch(vUv + vec2(1.0, -1.0) * t);

    vec3 color = e * 0.125;
    color += (a + c + g + i) * 0.03125;
    color += (b + d + f + h) * 0.0625;
    color += (j + k + l + m) * 0.125;

    if (uFirstLevel > 0.5) {
        float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
        float contribution = max(luminance - uThreshold, 0.0) / max(luminance, 1e-4);
        color *= contribution;
    }
    outColor = vec4(color, 1.0);
}
