package com.ccs.voxelgun.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/**
 * Software mixer over a single streaming {@link AudioTrack}.
 *
 * <p>Clips are synthesised once per process on a background worker. Playback requests come from the simulation
 * thread, are turned into voices with distance attenuation and stereo panning relative to the
 * listener, and are mixed by a dedicated thread. No audio files ship with the game.
 */
public final class AudioEngine {
    private static final int SAMPLE_RATE = 44100;
    private static final int MAX_VOICES = 28;
    private static final int MIX_FRAMES = 512;
    /** Variants per sound so repeated gunshots do not sound identical. */
    private static final int VARIANTS = SoundBank.VARIANTS;
    private static final SoundBank BANK = new SoundBank();

    private final Voice[] voices = new Voice[MAX_VOICES];

    private AudioTrack track;
    private Thread mixerThread;
    private volatile boolean running;
    private volatile int generation;
    private volatile float masterVolume = 0.8f;

    private volatile float listenerX;
    private volatile float listenerY;
    private volatile float listenerZ;
    private volatile float listenerYaw;

    private int voiceCursor;
    private int seedCursor;

    public AudioEngine() {
        for (int i = 0; i < voices.length; i++) {
            voices[i] = new Voice();
        }
    }

    /** Requests audio asynchronously. Repeated lifecycle calls do not rebuild the sound bank. */
    public synchronized void start() {
        if (running) return;
        BANK.prepare();
        running = true;
        int session = ++generation;
        mixerThread = new Thread(() -> runAudio(session), "voxel-audio");
        mixerThread.setDaemon(true);
        mixerThread.setPriority(Thread.MAX_PRIORITY - 1);
        mixerThread.start();
    }

    private void runAudio(int session) {
        AudioTrack current = null;
        try {
            if (!active(session)) return;
            current = createTrack();
            current.play();
            synchronized (this) {
                if (!active(session)) return;
                track = current;
            }
            mixLoop(session, current);
        } catch (IllegalArgumentException | IllegalStateException unavailable) {
            // Audio output can disappear while a device is being suspended or disconnected.
        } finally {
            if (current != null) {
                try { current.stop(); } catch (IllegalStateException ignored) { }
                current.release();
            }
            synchronized (this) {
                if (mixerThread == Thread.currentThread()) {
                    track = null;
                    mixerThread = null;
                    running = false;
                }
            }
        }
    }

    private boolean active(int session) { return running && generation == session; }

    private AudioTrack createTrack() {
        int minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT);
        int bufferBytes = Math.max(minBuffer, MIX_FRAMES * 2 * 2 * 4);
        return new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build())
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
    }

    public void setMasterVolume(float volume) {
        masterVolume = Math.max(0f, Math.min(1f, volume));
    }

    public void setListener(float x, float y, float z, float yaw) {
        listenerX = x;
        listenerY = y;
        listenerZ = z;
        listenerYaw = yaw;
    }

    /** Plays a clip at a world position; volume falls off with distance and pans stereo. */
    public void play(int kind, float x, float y, float z, float volume, float pitch) {
        play(kind, x, y, z, volume, pitch, false);
    }

    public void play(int kind, float x, float y, float z, float volume, float pitch, boolean occluded) {
        if (!running || kind < 0 || kind >= Sfx.COUNT) {
            return;
        }
        float dx = x - listenerX;
        float dy = y - listenerY;
        float dz = z - listenerZ;
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float attenuation = 1f / (1f + distance * 0.085f + distance * distance * 0.0016f);
        float gain = volume * attenuation;
        if (gain < 0.004f) {
            return;
        }

        // Pan by the angle between the listener facing and the source.
        float pan = 0f;
        if (distance > 0.3f) {
            float rightX = (float) Math.cos(listenerYaw);
            float rightZ = (float) Math.sin(listenerYaw);
            pan = Math.max(-1f, Math.min(1f, (dx * rightX + dz * rightZ) / distance));
        }
        float left = gain * (1f - Math.max(0f, pan) * 0.65f);
        float right = gain * (1f + Math.min(0f, pan) * 0.65f);

        // Distant shots lose their high end; approximate with a slight downward pitch shift.
        float shaped = pitch * (1f - Math.min(0.18f, distance * 0.0035f));
        startVoice(kind, left * (occluded ? 0.42f : 1f), right * (occluded ? 0.42f : 1f), shaped,
                occluded ? 0.08f : 1f);
    }

    /** Plays without positioning, used for UI and for the local player's own actions. */
    public void playUi(int kind, float volume, float pitch) {
        if (!running || kind < 0 || kind >= Sfx.COUNT) {
            return;
        }
        startVoice(kind, volume, volume, pitch);
    }

    private synchronized void startVoice(int kind, float left, float right, float pitch) {
        startVoice(kind, left, right, pitch, 1f);
    }

    private synchronized void startVoice(int kind, float left, float right, float pitch, float filter) {
        if (!running) return;
        int variant = (seedCursor++ & 0x7FFFFFFF) % VARIANTS;
        short[] pcm = BANK.clip(kind, variant);
        if (pcm == null) {
            return;
        }
        Voice chosen = null;
        for (int i = 0; i < voices.length; i++) {
            Voice voice = voices[(voiceCursor + i) % voices.length];
            if (!voice.active) {
                chosen = voice;
                voiceCursor = (voiceCursor + i + 1) % voices.length;
                break;
            }
        }
        if (chosen == null) {
            // Steal the quietest voice so new gunfire always cuts through.
            float quietest = Float.MAX_VALUE;
            for (Voice voice : voices) {
                float loudness = voice.gainLeft + voice.gainRight;
                if (loudness < quietest) {
                    quietest = loudness;
                    chosen = voice;
                }
            }
        }
        if (chosen == null) {
            return;
        }
        chosen.pcm = pcm;
        chosen.position = 0f;
        chosen.gainLeft = left;
        chosen.gainRight = right;
        chosen.pitch = Math.max(0.35f, Math.min(3f, pitch));
        chosen.filter = filter;
        chosen.filtered = 0f;
        chosen.active = true;
    }

    private void mixLoop(int session, AudioTrack current) {
        // Sessions own their buffers: a quick pause/resume cannot race an old in-flight write.
        float[] mixBuffer = new float[MIX_FRAMES * 2];
        short[] outputBuffer = new short[MIX_FRAMES * 2];
        while (active(session)) {
            java.util.Arrays.fill(mixBuffer, 0f);
            synchronized (this) {
                if (!active(session)) return;
                for (Voice voice : voices) {
                    if (!voice.active) {
                        continue;
                    }
                    mixVoice(voice, mixBuffer);
                }
            }
            float volume = masterVolume;
            for (int i = 0; i < mixBuffer.length; i++) {
                float sample = mixBuffer[i] * volume;
                // Soft knee limiter keeps dense firefights from clipping.
                if (sample > 0.85f) {
                    sample = 0.85f + (sample - 0.85f) / (1f + (sample - 0.85f) * 4f);
                } else if (sample < -0.85f) {
                    sample = -0.85f + (sample + 0.85f) / (1f - (sample + 0.85f) * 4f);
                }
                outputBuffer[i] = (short) (Math.max(-1f, Math.min(1f, sample)) * 32000f);
            }
            if (!active(session)) return;
            current.write(outputBuffer, 0, outputBuffer.length);
        }
    }

    private void mixVoice(Voice voice, float[] mixBuffer) {
        short[] pcm = voice.pcm;
        float position = voice.position;
        float step = voice.pitch;
        for (int frame = 0; frame < MIX_FRAMES; frame++) {
            int index = (int) position;
            if (index >= pcm.length - 1) {
                voice.active = false;
                voice.gainLeft = 0f;
                voice.gainRight = 0f;
                return;
            }
            float fraction = position - index;
            float sample = (pcm[index] * (1f - fraction) + pcm[index + 1] * fraction) / 32768f;
            voice.filtered += (sample - voice.filtered) * voice.filter;
            sample = voice.filtered;
            mixBuffer[frame * 2] += sample * voice.gainLeft;
            mixBuffer[frame * 2 + 1] += sample * voice.gainRight;
            position += step;
        }
        voice.position = position;
    }

    /** The owning worker closes its track; lifecycle callbacks never wait for audio I/O. */
    public synchronized void release() {
        running = false;
        generation++;
        track = null;
        for (Voice voice : voices) {
            voice.active = false;
        }
    }

    /** One playing clip. */
    private static final class Voice {
        short[] pcm;
        float position;
        float gainLeft;
        float gainRight;
        float pitch = 1f;
        float filter = 1f, filtered;
        volatile boolean active;
    }

}
