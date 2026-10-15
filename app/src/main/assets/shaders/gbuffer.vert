precision highp float;

layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in float aMaterial;
layout(location = 3) in float aOcclusion;

uniform mat4 uViewProjection;          // jittered, drives gl_Position
uniform mat4 uViewProjectionStable;    // unjittered, used for motion vectors
uniform mat4 uPreviousViewProjection;  // unjittered, previous frame
uniform mat4 uModel;
uniform mat4 uPreviousModel;

out vec3 vWorldPosition;
out vec3 vNormal;
out float vMaterial;
out float vOcclusion;
out vec4 vCurrentClip;
out vec4 vPreviousClip;

void main() {
    vec4 world = uModel * vec4(aPosition, 1.0);
    vec4 previousWorld = uPreviousModel * vec4(aPosition, 1.0);
    vWorldPosition = world.xyz;
    vNormal = normalize(mat3(uModel) * aNormal);
    vMaterial = aMaterial;
    vOcclusion = aOcclusion;
    // Motion vectors are computed without the temporal jitter, so the jitter does not leak
    // into reprojection.
    vCurrentClip = uViewProjectionStable * world;
    vPreviousClip = uPreviousViewProjection * previousWorld;
    gl_Position = uViewProjection * world;
}
