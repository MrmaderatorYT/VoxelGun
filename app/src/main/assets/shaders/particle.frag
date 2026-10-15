precision highp float;

in vec3 vColor;
in float vAlpha;
in vec2 vCorner;

layout(location = 0) out vec4 outColor;

void main() {
    // Soft round sprite; square corners would read as obvious billboards.
    float falloff = 1.0 - smoothstep(0.55, 1.0, length(vCorner) * 2.0);
    if (falloff <= 0.001) {
        discard;
    }
    outColor = vec4(vColor * falloff, vAlpha * falloff);
}
