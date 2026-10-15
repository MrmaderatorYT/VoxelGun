// Tent-filter upsample, additively blended onto the next larger mip.
precision highp float;

in vec2 vUv;
layout(location = 0) out vec4 outColor;

uniform sampler2D uSource;
uniform vec2 uTexelSize;
uniform float uRadius;

void main() {
    vec2 t = uTexelSize * uRadius;
    vec3 color = texture(uSource, vUv).rgb * 4.0;
    color += texture(uSource, vUv + vec2(-t.x, 0.0)).rgb * 2.0;
    color += texture(uSource, vUv + vec2(t.x, 0.0)).rgb * 2.0;
    color += texture(uSource, vUv + vec2(0.0, -t.y)).rgb * 2.0;
    color += texture(uSource, vUv + vec2(0.0, t.y)).rgb * 2.0;
    color += texture(uSource, vUv + vec2(-t.x, -t.y)).rgb;
    color += texture(uSource, vUv + vec2(t.x, -t.y)).rgb;
    color += texture(uSource, vUv + vec2(-t.x, t.y)).rgb;
    color += texture(uSource, vUv + vec2(t.x, t.y)).rgb;
    outColor = vec4(color / 16.0, 1.0);
}
