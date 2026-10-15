// Shared helpers: material palette, normal packing, sky model and depth reconstruction.

precision highp float;
precision highp int;
precision highp sampler2D;
precision highp usampler3D;

const float PI = 3.14159265359;
const uint FIRST_TRANSLUCENT = 200u;

struct Material {
    vec3 albedo;
    float roughness;
    vec3 emissive;
    float metallic;
};

uniform sampler2D uPalette;

const int TRANSLUCENT_SLOT = 64;  // must match Materials.TRANSLUCENT_SLOT

int paletteSlot(uint id) {
    return id >= FIRST_TRANSLUCENT ? TRANSLUCENT_SLOT + int(id - FIRST_TRANSLUCENT) : int(id);
}

Material readMaterial(uint id) {
    int slot = paletteSlot(id);
    vec4 a = texelFetch(uPalette, ivec2(slot * 2, 0), 0);
    vec4 b = texelFetch(uPalette, ivec2(slot * 2 + 1, 0), 0);
    Material m;
    m.albedo = a.rgb;
    m.roughness = a.a;
    m.emissive = b.rgb;
    m.metallic = b.a;
    return m;
}

// Octahedral normal encoding: two channels, no seams, plenty of precision for voxel faces.
vec2 encodeNormal(vec3 n) {
    n /= (abs(n.x) + abs(n.y) + abs(n.z));
    vec2 encoded = n.xz;
    if (n.y < 0.0) {
        encoded = (1.0 - abs(encoded.yx)) * vec2(encoded.x >= 0.0 ? 1.0 : -1.0,
                                                 encoded.y >= 0.0 ? 1.0 : -1.0);
    }
    return encoded * 0.5 + 0.5;
}

vec3 decodeNormal(vec2 encoded) {
    vec2 f = encoded * 2.0 - 1.0;
    vec3 n = vec3(f.x, 1.0 - abs(f.x) - abs(f.y), f.y);
    float t = max(-n.y, 0.0);
    n.x += n.x >= 0.0 ? -t : t;
    n.z += n.z >= 0.0 ? -t : t;
    return normalize(n);
}

// --------------------------------------------------------------------- sky

uniform vec3 uSunDirection;
uniform vec3 uSunColor;
uniform vec3 uSkyZenith;
uniform vec3 uSkyHorizon;
uniform float uSkyIntensity;
uniform float uSunAngle;

vec3 skyColor(vec3 direction) {
    float height = clamp(direction.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 gradient = mix(uSkyHorizon, uSkyZenith, pow(height, 0.62));
    // Ground bounce keeps downward rays from going black indoors.
    float ground = smoothstep(0.0, -0.25, direction.y);
    gradient = mix(gradient, uSkyHorizon * 0.32, ground);
    return gradient * uSkyIntensity;
}

vec3 sunAndSky(vec3 direction) {
    float cosAngle = dot(direction, uSunDirection);
    float disc = smoothstep(cos(uSunAngle * 3.0), cos(uSunAngle * 0.6), cosAngle);
    float glow = pow(max(cosAngle, 0.0), 220.0);
    return skyColor(direction) + uSunColor * (disc * 12.0 + glow * 1.5);
}

// --------------------------------------------------------------------- randomness

uint hashUint(uint x) {
    x ^= x >> 16;
    x *= 0x7feb352du;
    x ^= x >> 15;
    x *= 0x846ca68bu;
    x ^= x >> 16;
    return x;
}

float randomFloat(inout uint seed) {
    seed = hashUint(seed);
    return float(seed & 0x00FFFFFFu) / 16777216.0;
}

// Cosine-weighted hemisphere sample around a normal.
vec3 sampleCosine(vec3 normal, inout uint seed) {
    float u1 = randomFloat(seed);
    float u2 = randomFloat(seed);
    float r = sqrt(u1);
    float theta = 2.0 * PI * u2;
    vec3 up = abs(normal.y) < 0.92 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 tangent = normalize(cross(up, normal));
    vec3 bitangent = cross(normal, tangent);
    return normalize(tangent * (r * cos(theta)) + bitangent * (r * sin(theta))
                     + normal * sqrt(max(0.0, 1.0 - u1)));
}

// --------------------------------------------------------------------- depth

uniform mat4 uInverseViewProjection;
uniform vec3 uCameraPosition;
uniform vec2 uDepthPlanes;  // near, far

vec3 worldFromDepth(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 world = uInverseViewProjection * clip;
    return world.xyz / world.w;
}

float linearizeDepth(float depth) {
    float near = uDepthPlanes.x;
    float far = uDepthPlanes.y;
    float ndc = depth * 2.0 - 1.0;
    return (2.0 * near * far) / (far + near - ndc * (far - near));
}
