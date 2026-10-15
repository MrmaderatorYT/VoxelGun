// Bullet holes and scorch marks: quads aligned to the surface they were sprayed on.
precision highp float;

layout(location = 0) in vec2 aCorner;
layout(location = 1) in vec3 aCenter;
layout(location = 2) in float aSize;
layout(location = 3) in vec3 aNormal;
layout(location = 4) in float aAlpha;

uniform mat4 uViewProjection;

out float vAlpha;
out vec2 vCorner;

void main() {
    vec3 normal = normalize(aNormal);
    vec3 up = abs(normal.y) < 0.92 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 tangent = normalize(cross(up, normal));
    vec3 bitangent = cross(normal, tangent);
    vec3 world = aCenter + (tangent * aCorner.x + bitangent * aCorner.y) * aSize;
    vAlpha = aAlpha;
    vCorner = aCorner;
    gl_Position = uViewProjection * vec4(world, 1.0);
}
