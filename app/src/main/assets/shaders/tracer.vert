// Bullet tracers: a screen-facing ribbon between two world points.
precision highp float;

layout(location = 0) in vec2 aCorner;
layout(location = 1) in vec3 aStart;
layout(location = 2) in float aWidth;
layout(location = 3) in vec3 aEnd;
layout(location = 4) in float aHeat;

uniform mat4 uViewProjection;
uniform vec3 uCameraPosition;

out float vHeat;
out float vEdge;

void main() {
    vec3 along = aEnd - aStart;
    vec3 center = mix(aStart, aEnd, aCorner.x + 0.5);
    vec3 toCamera = normalize(uCameraPosition - center);
    vec3 side = normalize(cross(normalize(along), toCamera));
    vec3 world = center + side * aCorner.y * aWidth;
    vHeat = aHeat;
    vEdge = aCorner.y;
    gl_Position = uViewProjection * vec4(world, 1.0);
}
