precision highp float;
in vec3 vNormal;
out vec4 outColor;
void main() {
    float light = 0.6 + 0.4 * max(0.0, dot(normalize(vNormal), normalize(vec3(0.3, 0.8, 0.5))));
    outColor = vec4(vec3(0.06, 1.3, 1.5) * light, 0.32);
}
