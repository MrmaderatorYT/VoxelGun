precision highp float;
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aNormal;
uniform mat4 uViewProjection;
uniform mat4 uModel;
out vec3 vNormal;
void main() {
    vNormal = normalize(mat3(uModel) * aNormal);
    gl_Position = uViewProjection * uModel * vec4(aPosition, 1.0);
}
