// Instanced camera-facing quads. Instance layout: centre, size, colour, alpha.
precision highp float;

layout(location = 0) in vec2 aCorner;
layout(location = 1) in vec3 aCenter;
layout(location = 2) in float aSize;
layout(location = 3) in vec3 aColor;
layout(location = 4) in float aAlpha;

uniform mat4 uViewProjection;
uniform vec3 uCameraRight;
uniform vec3 uCameraUp;

out vec3 vColor;
out float vAlpha;
out vec2 vCorner;

void main() {
    vec3 world = aCenter + (uCameraRight * aCorner.x + uCameraUp * aCorner.y) * aSize;
    vColor = aColor;
    vAlpha = aAlpha;
    vCorner = aCorner;
    gl_Position = uViewProjection * vec4(world, 1.0);
}
