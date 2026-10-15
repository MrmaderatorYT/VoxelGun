// Temporal anti-aliasing over the resolved HDR image.
//
// The projection is jittered by a sub-pixel offset each frame; this pass folds those samples
// together by following the motion vector back into the previous frame. The history is clamped
// to the neighbourhood colour box, which is what keeps disocclusions and fast movement from
// leaving trails behind.
precision highp float;

in vec2 vUv;
layout(location = 0) out vec4 outColor;

uniform sampler2D uCurrent;
uniform sampler2D uHistory;
uniform sampler2D uVelocity;
uniform sampler2D uDepth;
uniform vec2 uTexelSize;
uniform float uBlend;

void main() {
    vec3 current = texture(uCurrent, vUv).rgb;
    if (uBlend <= 0.0) {
        outColor = vec4(current, 1.0);
        return;
    }

    // Use the closest fragment in the neighbourhood for the velocity lookup; this keeps
    // silhouettes from dragging the background with them.
    vec2 velocityUv = vUv;
    float closest = texture(uDepth, vUv).r;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 offset = vec2(float(x), float(y)) * uTexelSize;
            float sampleDepth = texture(uDepth, vUv + offset).r;
            if (sampleDepth < closest) {
                closest = sampleDepth;
                velocityUv = vUv + offset;
            }
        }
    }
    vec2 velocity = texture(uVelocity, velocityUv).rg;
    vec2 previousUv = vUv - velocity;

    if (any(lessThan(previousUv, vec2(0.0))) || any(greaterThan(previousUv, vec2(1.0)))) {
        outColor = vec4(current, 1.0);
        return;
    }

    vec3 minimum = current;
    vec3 maximum = current;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec3 neighbour = texture(uCurrent, vUv + vec2(float(x), float(y)) * uTexelSize).rgb;
            minimum = min(minimum, neighbour);
            maximum = max(maximum, neighbour);
        }
    }

    vec3 history = clamp(texture(uHistory, previousUv).rgb, minimum, maximum);

    // Blend less where the image is moving quickly, so trails cannot build up.
    float motion = clamp(length(velocity / max(uTexelSize, vec2(1e-5))) / 24.0, 0.0, 1.0);
    float blend = mix(uBlend, 0.5, motion);
    outColor = vec4(mix(current, history, blend), 1.0);
}
