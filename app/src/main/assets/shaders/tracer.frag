precision highp float;

in float vHeat;
in float vEdge;

layout(location = 0) out vec4 outColor;

void main() {
    float core = 1.0 - smoothstep(0.0, 1.0, abs(vEdge) * 2.0);
    vec3 color = mix(vec3(1.0, 0.55, 0.16), vec3(1.0, 0.94, 0.72), core) * (2.4 + vHeat * 3.0);
    outColor = vec4(color * core, core);
}
