precision highp float;

in float vAlpha;
in vec2 vCorner;

layout(location = 0) out vec4 outColor;

void main() {
    float radius = length(vCorner) * 2.0;
    float hole = 1.0 - smoothstep(0.35, 1.0, radius);
    if (hole <= 0.002) {
        discard;
    }
    // Dark crater with a slightly lighter rim.
    float rim = smoothstep(0.25, 0.65, radius) * 0.35;
    outColor = vec4(vec3(0.02 + rim * 0.08), hole * vAlpha);
}
