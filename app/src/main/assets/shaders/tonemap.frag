// Final image: bloom mix, exposure, ACES tone mapping, hit/damage overlays, vignette, grain
// and a light sharpen that puts the voxel edges back after the temporal passes.
precision highp float;

in vec2 vUv;
layout(location = 0) out vec4 outColor;

uniform sampler2D uScene;
uniform sampler2D uBloom;
uniform vec2 uTexelSize;
uniform float uExposure;
uniform float uBrightness;  // player brightness trim, multiplied into the exposure
uniform float uContrast;    // log contrast around middle grey; 1.0 leaves the image alone
uniform float uGamma;       // relative midtone adjustment, independent of exposure
uniform float uBloomStrength;
uniform float uVignette;
uniform float uGrain;
uniform float uTime;
uniform float uSharpen;
uniform float uDamage;      // red pulse when the player is hit
uniform float uFlash;       // flashbang whiteout
uniform float uLowHealth;   // desaturation and red edges when close to death
uniform float uScopeMask;   // 1 while a scoped weapon is aimed
uniform float uAspect;      // width / height, so the lens stays circular

vec3 aces(vec3 x) {
    const float a = 2.51;
    const float b = 0.03;
    const float c = 2.43;
    const float d = 0.59;
    const float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    vec3 color = texture(uScene, vUv).rgb;

    if (uSharpen > 0.0) {
        vec3 blur = texture(uScene, vUv + vec2(uTexelSize.x, 0.0)).rgb
                + texture(uScene, vUv - vec2(uTexelSize.x, 0.0)).rgb
                + texture(uScene, vUv + vec2(0.0, uTexelSize.y)).rgb
                + texture(uScene, vUv - vec2(0.0, uTexelSize.y)).rgb;
        color += (color - blur * 0.25) * uSharpen;
    }

    // Avoid a full-resolution texture read on the low/medium presets where bloom is disabled.
    if (uBloomStrength > 0.0) {
        color += texture(uBloom, vUv).rgb * uBloomStrength;
    }
    color *= uExposure * uBrightness;

    // Contrast is applied around middle grey in log space rather than by pushing the result away
    // from 0.5, so it deepens the shadows without shifting hues or clipping the bright voxels;
    // the ACES curve below then rolls the stretched highlights back in.
    if (uContrast != 1.0) {
        const float mid = 0.18;
        color = mid * pow(max(color, vec3(1e-4)) / mid, vec3(uContrast));
    }
    color = aces(color);
    color = pow(max(color, vec3(0.0)), vec3(1.0 / clamp(uGamma, 0.5, 2.0)));

    float radius = length(vUv - 0.5) * 2.0;

    // Glass falloff inside the optic. The HUD paints the hard black surround; this only darkens
    // the image toward the lens edge so the two meet without a visible seam.
    if (uScopeMask > 0.0) {
        vec2 lens = (vUv - 0.5) * vec2(uAspect, 1.0);
        float lensRadius = length(lens) * 2.0;
        float falloff = smoothstep(0.55, 0.92, lensRadius);
        color = mix(color, vec3(0.0), falloff * uScopeMask);
    }

    color *= mix(1.0, 1.0 - uVignette, smoothstep(0.35, 1.35, radius));

    if (uLowHealth > 0.0) {
        float grey = dot(color, vec3(0.299, 0.587, 0.114));
        color = mix(color, vec3(grey * 1.05, grey * 0.72, grey * 0.72), uLowHealth * 0.55);
        color = mix(color, vec3(0.35, 0.03, 0.03),
                    uLowHealth * smoothstep(0.55, 1.3, radius) * 0.55);
    }
    if (uDamage > 0.0) {
        color = mix(color, vec3(0.55, 0.05, 0.05), uDamage * 0.42);
    }
    if (uFlash > 0.0) {
        color = mix(color, vec3(1.0), clamp(uFlash, 0.0, 1.0));
    }

    if (uGrain > 0.0) {
        float noise = hash12(vUv * 1024.0 + uTime * 60.0) - 0.5;
        color += noise * uGrain;
    }

    outColor = vec4(color, 1.0);
}
