package com.ccs.voxelstrike.audio;

import java.util.Random;

/**
 * Bridge to the native procedural sound synthesiser, with a small Java fallback so the game is
 * never silent if the shared library cannot be loaded.
 */
public final class NativeSynth {
    private static final boolean AVAILABLE = load();

    private static final int STYLE_PISTOL = 0;
    private static final int STYLE_SMG = 1;
    private static final int STYLE_SHOTGUN = 2;
    private static final int STYLE_RIFLE = 3;
    private static final int STYLE_SNIPER = 4;
    private static final int STYLE_LMG = 5;
    private static final int STYLE_LAUNCHER = 6;

    /**
     * One timbre per firearm, in the exact order of {@link Sfx#FIRST_WEAPON_SHOT}. Values describe
     * clip length, chamber resonance, muzzle brightness, transient/body balance, action noise and
     * room tail. The bank still creates several seeded variants of every profile.
     */
    private static final ShotProfile[] WEAPON_PROFILES = {
            // Pistols: service, suppressed, machine-pistol, heavy, .50 and revolver-like.
            new ShotProfile(STYLE_PISTOL, 0.22f, 150f, 7200f, 0.95f, 0.50f, 0.20f, 0.16f), // P9
            new ShotProfile(STYLE_PISTOL, 0.18f, 185f, 3000f, 0.38f, 0.34f, 0.34f, 0.04f), // Talon-S
            new ShotProfile(STYLE_PISTOL, 0.15f, 178f, 6700f, 0.76f, 0.34f, 0.34f, 0.07f), // Auto-9
            new ShotProfile(STYLE_PISTOL, 0.27f, 128f, 6100f, 1.00f, 0.68f, 0.24f, 0.24f), // Mag-5
            new ShotProfile(STYLE_PISTOL, 0.34f, 92f, 7900f, 1.18f, 0.96f, 0.28f, 0.36f), // Ranger .50
            new ShotProfile(STYLE_PISTOL, 0.40f, 78f, 7000f, 1.12f, 1.08f, 0.38f, 0.44f), // Sidewinder

            // SMGs stay short enough to remain readable during long automatic bursts.
            new ShotProfile(STYLE_SMG, 0.18f, 185f, 5900f, 0.74f, 0.34f, 0.34f, 0.08f), // Spectre
            new ShotProfile(STYLE_SMG, 0.19f, 170f, 7300f, 0.90f, 0.42f, 0.28f, 0.12f), // Hornet
            new ShotProfile(STYLE_SMG, 0.16f, 202f, 6500f, 0.82f, 0.31f, 0.47f, 0.08f), // Uzi-K
            new ShotProfile(STYLE_SMG, 0.15f, 232f, 8400f, 0.96f, 0.28f, 0.40f, 0.07f), // MP7-K
            new ShotProfile(STYLE_SMG, 0.14f, 252f, 7700f, 0.84f, 0.25f, 0.54f, 0.05f), // Vector-9

            // Shotguns carry more low-frequency pressure and a clearly audible cycling action.
            new ShotProfile(STYLE_SHOTGUN, 0.55f, 62f, 4400f, 1.10f, 1.10f, 0.56f, 0.68f), // Pumper-8
            new ShotProfile(STYLE_SHOTGUN, 0.50f, 68f, 5100f, 1.15f, 1.00f, 0.43f, 0.60f), // Breaker-12
            new ShotProfile(STYLE_SHOTGUN, 0.37f, 78f, 5300f, 0.94f, 0.72f, 0.40f, 0.38f), // Auto-Slug
            new ShotProfile(STYLE_SHOTGUN, 0.33f, 72f, 4700f, 1.00f, 0.82f, 0.50f, 0.32f), // Drum-12

            // Assault rifles vary from a bright Famas crack to the heavier AK and Scar reports.
            new ShotProfile(STYLE_RIFLE, 0.27f, 112f, 8100f, 1.10f, 0.55f, 0.26f, 0.26f), // Famas-B
            new ShotProfile(STYLE_RIFLE, 0.30f, 102f, 6800f, 0.98f, 0.66f, 0.32f, 0.33f), // Bullpup-A
            new ShotProfile(STYLE_RIFLE, 0.36f, 82f, 6200f, 1.08f, 0.92f, 0.38f, 0.48f), // AK-15
            new ShotProfile(STYLE_RIFLE, 0.28f, 118f, 8600f, 1.16f, 0.54f, 0.28f, 0.25f), // Vektor-AR
            new ShotProfile(STYLE_RIFLE, 0.31f, 106f, 7400f, 1.02f, 0.62f, 0.30f, 0.34f), // M4 Falcon
            new ShotProfile(STYLE_RIFLE, 0.40f, 76f, 7000f, 1.20f, 1.00f, 0.42f, 0.56f), // Scar-H

            // Snipers have a longer pressure wave, supersonic crack and late bolt movement.
            new ShotProfile(STYLE_SNIPER, 0.48f, 78f, 8600f, 1.18f, 0.82f, 0.42f, 0.62f), // Scout-V
            new ShotProfile(STYLE_SNIPER, 0.52f, 68f, 7600f, 1.15f, 1.00f, 0.45f, 0.68f), // Dragunov-S
            new ShotProfile(STYLE_SNIPER, 0.67f, 52f, 9400f, 1.35f, 1.25f, 0.56f, 0.88f), // AWM-X
            new ShotProfile(STYLE_SNIPER, 0.76f, 42f, 8800f, 1.40f, 1.50f, 0.64f, 1.00f), // Barrett-M

            // Belt-fed actions add a stronger metallic layer beneath the report.
            new ShotProfile(STYLE_LMG, 0.36f, 82f, 6300f, 1.05f, 0.94f, 0.56f, 0.50f), // Bulldog
            new ShotProfile(STYLE_LMG, 0.33f, 88f, 7100f, 1.10f, 0.85f, 0.68f, 0.45f), // Negev-M

            // Launch pop and motor wash; the projectile impact still uses EXPLOSION separately.
            new ShotProfile(STYLE_LAUNCHER, 0.72f, 48f, 3400f, 0.55f, 1.30f, 0.22f, 0.86f) // RPG-V
    };

    private NativeSynth() {}

    private static boolean load() {
        try {
            System.loadLibrary("voxel_native");
            return true;
        } catch (UnsatisfiedLinkError error) {
            return false;
        }
    }

    private static native short[] render(int kind, int sampleRate, int seed, float gain);

    public static short[] synthesize(int kind, int sampleRate, int seed, float gain) {
        if (Sfx.weaponShot(kind)) return weaponShot(kind, sampleRate, seed, gain);
        if (kind >= Sfx.ARMOR_HIT && kind <= Sfx.ROUND_LOSS) {
            return detail(kind, sampleRate, seed, gain);
        }
        if (AVAILABLE) {
            try {
                short[] pcm = render(kind, sampleRate, seed, gain);
                if (pcm != null && pcm.length > 0) {
                    return pcm;
                }
            } catch (UnsatisfiedLinkError ignored) {
                // Fall through to the Java version.
            }
        }
        return fallback(kind, sampleRate, seed, gain);
    }

    /** Layered procedural report used by the per-model weapon profiles above. */
    private static short[] weaponShot(int kind, int rate, int seed, float gain) {
        int profileIndex = kind - Sfx.FIRST_WEAPON_SHOT;
        if (profileIndex < 0 || profileIndex >= WEAPON_PROFILES.length) {
            return fallback(Sfx.RIFLE, rate, seed, gain);
        }
        ShotProfile profile = WEAPON_PROFILES[profileIndex];
        int frames = Math.max(16, (int) (rate * profile.duration));
        float[] buffer = new float[frames];
        Random random = new Random(seed * 2654435761L + kind * 7919L);

        float tuning = 0.965f + random.nextFloat() * 0.07f;
        double phase = random.nextDouble() * Math.PI * 2.0;
        float brightFiltered = 0f;
        float lowFiltered = 0f;
        float rumble = 0f;

        float fastDecay;
        float bodyDecay;
        float mechanismAt;
        float mechanismDecay;
        float echoSeconds;
        switch (profile.style) {
            case STYLE_SMG:
                fastDecay = 92f; bodyDecay = 30f; mechanismAt = 0.038f;
                mechanismDecay = 78f; echoSeconds = 0.035f; break;
            case STYLE_SHOTGUN:
                fastDecay = 36f; bodyDecay = 7.5f; mechanismAt = 0.20f;
                mechanismDecay = 28f; echoSeconds = 0.065f; break;
            case STYLE_RIFLE:
                fastDecay = 60f; bodyDecay = 14f; mechanismAt = 0.070f;
                mechanismDecay = 58f; echoSeconds = 0.050f; break;
            case STYLE_SNIPER:
                fastDecay = 42f; bodyDecay = 6.5f; mechanismAt = 0.27f;
                mechanismDecay = 25f; echoSeconds = 0.085f; break;
            case STYLE_LMG:
                fastDecay = 54f; bodyDecay = 11f; mechanismAt = 0.065f;
                mechanismDecay = 48f; echoSeconds = 0.052f; break;
            case STYLE_LAUNCHER:
                fastDecay = 20f; bodyDecay = 4.2f; mechanismAt = 0.12f;
                mechanismDecay = 22f; echoSeconds = 0.10f; break;
            case STYLE_PISTOL:
            default:
                fastDecay = 74f; bodyDecay = 22f; mechanismAt = 0.055f;
                mechanismDecay = 66f; echoSeconds = 0.043f; break;
        }
        mechanismAt = Math.min(mechanismAt, profile.duration * 0.56f);

        for (int i = 0; i < frames; i++) {
            float t = i / (float) rate;
            float progress = i / (float) frames;
            float white = random.nextFloat() * 2f - 1f;

            // A closing muzzle filter makes the initial crack bright while preserving a weighty
            // low-frequency tail. Lower brightness values produce a convincingly muffled report.
            float cutoff = profile.brightness * (0.52f + 0.48f * (float) Math.exp(-t * 13f));
            float brightAlpha = 1f - (float) Math.exp(-2f * Math.PI * cutoff / rate);
            float lowAlpha = 1f - (float) Math.exp(-2f * Math.PI * 520f / rate);
            brightFiltered += (white - brightFiltered) * brightAlpha;
            lowFiltered += (white - lowFiltered) * lowAlpha;
            rumble = rumble * 0.965f + white * 0.035f;

            float attack = Math.min(1f, t * 1800f);
            float fastEnvelope = attack * (float) Math.exp(-t * fastDecay);
            float bodyEnvelope = attack * (float) Math.exp(-t * bodyDecay);
            float band = brightFiltered - lowFiltered;
            float brightnessMix = Math.min(1f, profile.brightness / 9000f);
            float crackNoise = brightFiltered * 0.82f
                    + (white - brightFiltered) * brightnessMix * 0.18f;

            float frequency = profile.bodyHz * tuning
                    * (1f + 0.72f * (float) Math.exp(-t * 24f));
            phase += Math.PI * 2.0 * frequency / rate;
            double bodyTone = Math.sin(phase) + 0.31 * Math.sin(phase * 2.03)
                    + 0.12 * Math.sin(phase * 0.51);
            double sample = crackNoise * fastEnvelope * profile.crack;
            sample += (lowFiltered * 0.72f + band * 0.46f + bodyTone * 0.42f)
                    * bodyEnvelope * profile.body;

            switch (profile.style) {
                case STYLE_SMG:
                    sample += band * fastEnvelope * 0.24f;
                    break;
                case STYLE_SHOTGUN:
                    sample += (rumble * 2.6f + lowFiltered * 0.8f)
                            * Math.exp(-t * 5.2f) * profile.body;
                    break;
                case STYLE_RIFLE:
                    sample += band * Math.exp(-t * 22f) * 0.26f;
                    break;
                case STYLE_SNIPER:
                    double shockFrequency = 2300.0 - progress * 850.0;
                    sample += Math.sin(Math.PI * 2.0 * shockFrequency * t)
                            * Math.exp(-t * 44f) * profile.crack * 0.18f;
                    sample += rumble * Math.exp(-t * 4.8f) * profile.body * 1.25f;
                    break;
                case STYLE_LMG:
                    sample += (rumble * 1.7f + band * 0.30f)
                            * Math.exp(-t * 9.5f) * profile.body;
                    break;
                case STYLE_LAUNCHER:
                    double motorEnvelope = (1.0 - Math.exp(-t * 70.0)) * Math.exp(-t * 4.0);
                    sample = sample * 0.45
                            + (rumble * 3.0 + lowFiltered * 1.4) * motorEnvelope * profile.body
                            + Math.sin(phase * 0.62) * Math.exp(-t * 3.8f) * 0.65;
                    break;
                case STYLE_PISTOL:
                default:
                    sample += band * Math.exp(-t * 31f) * 0.16f;
                    break;
            }

            float mechanismAge = t - mechanismAt;
            if (mechanismAge >= 0f) {
                float mechanismEnvelope = (1f - (float) Math.exp(-mechanismAge * 1000f))
                        * (float) Math.exp(-mechanismAge * mechanismDecay);
                double metal = Math.sin(Math.PI * 2.0 * (1550f + profileIndex * 37f)
                        * mechanismAge);
                sample += (band * 0.72f + metal * 0.28f)
                        * mechanismEnvelope * profile.mechanism;
            }
            buffer[i] = (float) sample;
        }

        // A short feedback path supplies an outdoor/concrete reflection without baking location
        // into the clip. Positional attenuation and wall occlusion are still applied at playback.
        int echoDelay = Math.max(1, (int) (rate * echoSeconds));
        float feedback = profile.tail * 0.42f;
        for (int i = echoDelay; i < frames; i++) {
            float life = 1f - 0.35f * i / frames;
            buffer[i] += buffer[i - echoDelay] * feedback * life;
        }

        float peak = 0.0001f;
        for (float sample : buffer) peak = Math.max(peak, Math.abs(sample));
        float scale = gain / peak;
        int fadeFrames = Math.min(frames / 4, Math.max(1, rate / 250));
        short[] pcm = new short[frames];
        for (int i = 0; i < frames; i++) {
            float sample = (float) Math.tanh(buffer[i] * scale * 1.3f);
            if (i >= frames - fadeFrames) {
                sample *= (frames - i) / (float) fadeFrames;
            }
            pcm[i] = (short) (Math.max(-1f, Math.min(1f, sample)) * 30000f);
        }
        return pcm;
    }

    /** Small layered clips shared by native and fallback installations. */
    private static short[] detail(int kind, int rate, int seed, float gain) {
        boolean stinger = kind == Sfx.ROUND_WIN || kind == Sfx.ROUND_LOSS;
        float duration = stinger ? 1.1f : kind == Sfx.HEARTBEAT ? 0.42f : 0.2f;
        short[] pcm = new short[(int) (rate * duration)];
        Random random = new Random(seed);
        float filtered = 0f;
        for (int i = 0; i < pcm.length; i++) {
            float t = i / (float) rate;
            float noise = random.nextFloat() * 2f - 1f;
            filtered += (noise - filtered) * 0.1f;
            double sample;
            if (stinger) {
                int note = Math.min(2, (int) (t / 0.2f));
                double f = kind == Sfx.ROUND_WIN ? (note == 0 ? 523.25 : note == 1 ? 659.25 : 783.99)
                        : (note == 0 ? 392 : note == 1 ? 349.23 : 261.63);
                double age = t - note * 0.2;
                sample = (Math.sin(t * f * Math.PI * 2) + 0.3 * Math.sin(t * f * Math.PI * 4))
                        * Math.exp(-age * 6) * 0.4;
            } else if (kind == Sfx.HEARTBEAT) {
                double beat = Math.exp(-t * 36) + (t > 0.16 ? 0.65 * Math.exp(-(t - 0.16) * 42) : 0);
                sample = Math.sin(t * 68 * Math.PI * 2) * beat * 0.8;
            } else {
                float f = kind == Sfx.ARMOR_HIT ? 2800f : kind == Sfx.STEP_METAL ? 720f
                        : kind == Sfx.STEP_WOOD ? 190f : kind == Sfx.BOLT ? 1500f : 110f;
                double tone = Math.sin(t * f * Math.PI * 2) * Math.exp(-t * 30);
                double impact = (kind == Sfx.STEP_SOFT ? filtered : noise * 0.35) * Math.exp(-t * 45);
                sample = tone * (kind == Sfx.STEP_SOFT ? 0.1 : 0.6) + impact;
            }
            double fade = Math.min(1, t * 1500) * Math.min(1, (duration - t) * 120);
            pcm[i] = (short) (Math.max(-1, Math.min(1, sample * gain * fade)) * 30000);
        }
        return pcm;
    }

    /** Noise-and-envelope approximation of the native bank. */
    private static short[] fallback(int kind, int sampleRate, int seed, float gain) {
        float[] durations = {0.34f, 0.24f, 0.46f, 0.62f, 0.22f, 1.30f, 0.10f, 0.07f, 0.16f, 0.09f,
                0.16f, 0.20f, 0.18f, 0.14f, 0.13f, 0.06f, 0.10f, 0.55f, 0.70f, 0.12f, 0.40f};
        int index = kind >= 0 && kind < durations.length ? kind : 16;
        int frames = Math.max(16, (int) (sampleRate * durations[index]));
        short[] pcm = new short[frames];
        Random random = new Random(seed * 2654435761L + kind);
        boolean tonal = index == 9 || index == 10 || index == 16 || index == 17;
        float frequency = 660f + index * 90f;
        float lowpass = 0f;
        for (int i = 0; i < frames; i++) {
            float t = (float) i / sampleRate;
            float envelope = (float) Math.exp(-t * (tonal ? 18f : 26f));
            float sample;
            if (tonal) {
                sample = (float) Math.sin(2 * Math.PI * frequency * t) * envelope;
            } else {
                lowpass += (random.nextFloat() * 2f - 1f - lowpass) * 0.35f;
                sample = lowpass * envelope;
            }
            pcm[i] = (short) (Math.max(-1f, Math.min(1f, sample * gain)) * 30000f);
        }
        return pcm;
    }

    private static final class ShotProfile {
        final int style;
        final float duration;
        final float bodyHz;
        final float brightness;
        final float crack;
        final float body;
        final float mechanism;
        final float tail;

        ShotProfile(int style, float duration, float bodyHz, float brightness, float crack,
                float body, float mechanism, float tail) {
            this.style = style;
            this.duration = duration;
            this.bodyHz = bodyHz;
            this.brightness = brightness;
            this.crack = crack;
            this.body = body;
            this.mechanism = mechanism;
            this.tail = tail;
        }
    }
}
