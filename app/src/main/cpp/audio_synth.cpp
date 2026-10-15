// Procedural audio. The game ships no sound files: every gunshot, impact and UI blip is
// synthesised here once at load time and then played back from a pooled AudioTrack.

#include <jni.h>

#include <algorithm>
#include <cmath>
#include <vector>

namespace {

constexpr float kPi = 3.14159265358979323846f;

struct Rng {
    uint32_t state;
    explicit Rng(uint32_t seed) : state(seed ? seed : 0x9E3779B9u) {}
    inline float next() {  // white noise in [-1, 1)
        state = state * 1664525u + 1013904223u;
        return static_cast<float>(static_cast<int32_t>(state >> 8)) * (1.0f / 8388608.0f) - 1.0f;
    }
};

/// Transposed direct form II resonant low pass (RBJ cookbook).
struct Biquad {
    float b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0, z1 = 0, z2 = 0;

    void lowPass(float cutoff, float q, float sampleRate) {
        cutoff = std::max(20.0f, std::min(cutoff, sampleRate * 0.45f));
        const float w = 2.0f * kPi * cutoff / sampleRate;
        const float alpha = std::sin(w) / (2.0f * std::max(0.05f, q));
        const float cosw = std::cos(w);
        const float a0 = 1.0f + alpha;
        b0 = (1.0f - cosw) * 0.5f / a0;
        b1 = (1.0f - cosw) / a0;
        b2 = b0;
        a1 = -2.0f * cosw / a0;
        a2 = (1.0f - alpha) / a0;
    }
    void highPass(float cutoff, float q, float sampleRate) {
        cutoff = std::max(20.0f, std::min(cutoff, sampleRate * 0.45f));
        const float w = 2.0f * kPi * cutoff / sampleRate;
        const float alpha = std::sin(w) / (2.0f * std::max(0.05f, q));
        const float cosw = std::cos(w);
        const float a0 = 1.0f + alpha;
        b0 = (1.0f + cosw) * 0.5f / a0;
        b1 = -(1.0f + cosw) / a0;
        b2 = b0;
        a1 = -2.0f * cosw / a0;
        a2 = (1.0f - alpha) / a0;
    }
    inline float process(float x) {
        const float y = b0 * x + z1;
        z1 = b1 * x - a1 * y + z2;
        z2 = b2 * x - a2 * y;
        return y;
    }
};

inline float softClip(float x) { return std::tanh(x * 1.35f); }

/// Short feedback delay that stands in for the map's concrete reverb tail.
void applyTail(std::vector<float>& buffer, int sampleRate, float amount, float seconds) {
    if (amount <= 0.0f) return;
    const int delay = std::max(1, static_cast<int>(sampleRate * seconds));
    const float feedback = 0.45f * amount;
    for (size_t i = delay; i < buffer.size(); i++) {
        buffer[i] += buffer[i - delay] * feedback;
    }
}

/// Weapon report: transient crack, filtered body, mechanical click and a room tail.
void renderShot(std::vector<float>& out, int sampleRate, Rng& rng, float bright, float body,
                float decay, float tail) {
    Biquad lp, hp;
    hp.highPass(1800.0f, 0.7f, static_cast<float>(sampleRate));
    const float n = static_cast<float>(out.size());
    for (size_t i = 0; i < out.size(); i++) {
        const float t = static_cast<float>(i) / sampleRate;
        const float progress = static_cast<float>(i) / n;
        lp.lowPass(bright * std::exp(-progress * 9.0f) + 300.0f, 1.1f, static_cast<float>(sampleRate));

        const float noise = rng.next();
        const float envFast = std::exp(-t * (60.0f * decay));
        const float envBody = std::exp(-t * (11.0f * decay));

        float sample = lp.process(noise) * envBody * 0.9f;
        sample += hp.process(noise) * envFast * 0.7f;
        sample += std::sin(2.0f * kPi * body * t * (1.0f + 0.6f * std::exp(-t * 40.0f))) *
                  std::exp(-t * 26.0f) * 0.55f;
        out[i] = sample;
    }
    applyTail(out, sampleRate, tail, 0.055f);
}

void renderExplosion(std::vector<float>& out, int sampleRate, Rng& rng) {
    Biquad lp;
    float brown = 0.0f;
    const float n = static_cast<float>(out.size());
    for (size_t i = 0; i < out.size(); i++) {
        const float t = static_cast<float>(i) / sampleRate;
        const float progress = static_cast<float>(i) / n;
        lp.lowPass(1400.0f * std::exp(-progress * 3.4f) + 60.0f, 0.9f, static_cast<float>(sampleRate));
        brown = brown * 0.982f + rng.next() * 0.09f;
        const float env = std::exp(-t * 3.1f) * (1.0f - std::exp(-t * 320.0f));
        float sample = lp.process(brown * 6.0f) * env;
        sample += std::sin(2.0f * kPi * 42.0f * t) * std::exp(-t * 4.5f) * 0.5f;
        sample += rng.next() * std::exp(-t * 40.0f) * 0.35f;
        out[i] = softClip(sample * 1.4f);
    }
    applyTail(out, sampleRate, 0.8f, 0.13f);
}

void renderStep(std::vector<float>& out, int sampleRate, Rng& rng, float pitch) {
    Biquad lp;
    lp.lowPass(900.0f * pitch, 1.4f, static_cast<float>(sampleRate));
    for (size_t i = 0; i < out.size(); i++) {
        const float t = static_cast<float>(i) / sampleRate;
        const float env = std::exp(-t * 46.0f) * (1.0f - std::exp(-t * 900.0f));
        out[i] = lp.process(rng.next()) * env * 1.6f;
    }
}

/// Metallic click used for bolt, magazine and dry fire; `ring` adds a tuned partial.
void renderClick(std::vector<float>& out, int sampleRate, Rng& rng, float pitch, float ring) {
    Biquad bp;
    bp.highPass(1100.0f * pitch, 2.2f, static_cast<float>(sampleRate));
    for (size_t i = 0; i < out.size(); i++) {
        const float t = static_cast<float>(i) / sampleRate;
        const float env = std::exp(-t * 90.0f);
        float sample = bp.process(rng.next()) * env;
        sample += std::sin(2.0f * kPi * 2400.0f * pitch * t) * std::exp(-t * 55.0f) * ring;
        out[i] = sample * 1.2f;
    }
}

/// Two-tone confirmation blip (hit marker, headshot, UI).
void renderTone(std::vector<float>& out, int sampleRate, float freqA, float freqB, float decay) {
    for (size_t i = 0; i < out.size(); i++) {
        const float t = static_cast<float>(i) / sampleRate;
        const float env = std::exp(-t * decay) * (1.0f - std::exp(-t * 700.0f));
        out[i] = (std::sin(2.0f * kPi * freqA * t) * 0.6f + std::sin(2.0f * kPi * freqB * t) * 0.4f) * env;
    }
}

void renderSwing(std::vector<float>& out, int sampleRate, Rng& rng) {
    Biquad bp;
    const float n = static_cast<float>(out.size());
    for (size_t i = 0; i < out.size(); i++) {
        const float progress = static_cast<float>(i) / n;
        bp.lowPass(600.0f + 5200.0f * std::sin(progress * kPi), 3.0f, static_cast<float>(sampleRate));
        out[i] = bp.process(rng.next()) * std::sin(progress * kPi) * 0.9f;
    }
}

void renderWhizz(std::vector<float>& out, int sampleRate, Rng& rng) {
    Biquad bp;
    const float n = static_cast<float>(out.size());
    for (size_t i = 0; i < out.size(); i++) {
        const float progress = static_cast<float>(i) / n;
        bp.lowPass(2600.0f - 1600.0f * progress, 6.0f, static_cast<float>(sampleRate));
        const float env = std::sin(progress * kPi);
        out[i] = bp.process(rng.next()) * env * env * 1.5f;
    }
}

}  // namespace

/// kind: 0 rifle, 1 pistol, 2 shotgun, 3 sniper, 4 smg, 5 explosion, 6 step, 7 click,
///       8 magazine, 9 hit marker, 10 headshot, 11 swing, 12 stab, 13 land, 14 whizz,
///       15 dry fire, 16 ui, 17 round start, 18 death, 19 pin pull, 20 lmg
extern "C" JNIEXPORT jshortArray JNICALL Java_com_ccs_voxelgun_audio_NativeSynth_render(
        JNIEnv* env, jclass, jint kind, jint sampleRate, jint seed, jfloat gain) {
    static const float kDurations[21] = {0.34f, 0.24f, 0.46f, 0.62f, 0.22f, 1.30f, 0.10f,
                                         0.07f, 0.16f, 0.09f, 0.16f, 0.20f, 0.18f, 0.14f,
                                         0.13f, 0.06f, 0.10f, 0.55f, 0.70f, 0.12f, 0.40f};
    const int index = (kind >= 0 && kind < 21) ? kind : 16;
    const int frames = std::max(16, static_cast<int>(sampleRate * kDurations[index]));

    std::vector<float> buffer(static_cast<size_t>(frames), 0.0f);
    Rng rng(static_cast<uint32_t>(seed) * 2654435761u + 12345u);

    switch (index) {
        case 0: renderShot(buffer, sampleRate, rng, 7200.0f, 96.0f, 1.00f, 0.55f); break;
        case 1: renderShot(buffer, sampleRate, rng, 5200.0f, 128.0f, 1.55f, 0.35f); break;
        case 2: renderShot(buffer, sampleRate, rng, 4200.0f, 68.0f, 0.72f, 0.75f); break;
        case 3: renderShot(buffer, sampleRate, rng, 8600.0f, 74.0f, 0.58f, 0.95f); break;
        case 4: renderShot(buffer, sampleRate, rng, 6200.0f, 150.0f, 1.75f, 0.30f); break;
        case 5: renderExplosion(buffer, sampleRate, rng); break;
        case 6: renderStep(buffer, sampleRate, rng, 1.0f); break;
        case 7: renderClick(buffer, sampleRate, rng, 1.0f, 0.25f); break;
        case 8: renderClick(buffer, sampleRate, rng, 0.55f, 0.45f); break;
        case 9: renderTone(buffer, sampleRate, 1650.0f, 2400.0f, 46.0f); break;
        case 10: renderTone(buffer, sampleRate, 2100.0f, 3150.0f, 24.0f); break;
        case 11: renderSwing(buffer, sampleRate, rng); break;
        case 12: renderShot(buffer, sampleRate, rng, 2600.0f, 190.0f, 3.20f, 0.10f); break;
        case 13: renderStep(buffer, sampleRate, rng, 0.55f); break;
        case 14: renderWhizz(buffer, sampleRate, rng); break;
        case 15: renderClick(buffer, sampleRate, rng, 1.6f, 0.10f); break;
        case 16: renderTone(buffer, sampleRate, 880.0f, 1320.0f, 40.0f); break;
        case 17: renderTone(buffer, sampleRate, 523.25f, 783.99f, 5.0f); break;
        case 18: renderShot(buffer, sampleRate, rng, 1600.0f, 58.0f, 2.10f, 0.65f); break;
        case 19: renderClick(buffer, sampleRate, rng, 2.1f, 0.55f); break;
        case 20: renderShot(buffer, sampleRate, rng, 6600.0f, 88.0f, 0.85f, 0.60f); break;
        default: renderTone(buffer, sampleRate, 880.0f, 1320.0f, 40.0f); break;
    }

    // Normalise, apply the caller gain and fade the last 4 ms so playback never clicks.
    float peak = 1e-4f;
    for (float sample : buffer) peak = std::max(peak, std::fabs(sample));
    const float scale = gain / peak;
    const int fade = std::min(frames / 4, sampleRate / 250);

    std::vector<int16_t> pcm(static_cast<size_t>(frames));
    for (int i = 0; i < frames; i++) {
        float sample = softClip(buffer[static_cast<size_t>(i)] * scale);
        if (i >= frames - fade) sample *= static_cast<float>(frames - i) / static_cast<float>(fade);
        pcm[static_cast<size_t>(i)] =
                static_cast<int16_t>(std::max(-32000.0f, std::min(32000.0f, sample * 32000.0f)));
    }

    jshortArray out = env->NewShortArray(frames);
    if (out == nullptr) return nullptr;
    env->SetShortArrayRegion(out, 0, frames, pcm.data());
    return out;
}
