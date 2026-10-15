package com.ccs.voxelgun.audio;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceArray;

/** Process-wide PCM cache, published clip by clip without blocking playback or the UI. */
final class SoundBank {
    static final int VARIANTS = 3;
    interface Renderer { short[] render(int kind, int variant); }

    private final AtomicReferenceArray<short[]> clips =
            new AtomicReferenceArray<>(Sfx.COUNT * VARIANTS);
    private final AtomicBoolean requested = new AtomicBoolean();
    private final Executor worker;
    private final Renderer renderer;

    SoundBank() {
        this(Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "voxel-sound-bank");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY + 1);
            return thread;
        }), (kind, variant) -> NativeSynth.synthesize(kind, 44100,
                kind * 977 + variant * 31 + 7, 0.92f));
    }

    SoundBank(Executor worker, Renderer renderer) {
        this.worker = worker;
        this.renderer = renderer;
    }

    void prepare() {
        if (!requested.compareAndSet(false, true)) return;
        worker.execute(() -> {
            // Menu feedback first; make every sound playable before preparing extra variants.
            clips.set(Sfx.UI * VARIANTS, renderer.render(Sfx.UI, 0));
            for (int variant = 0; variant < VARIANTS; variant++) {
                for (int kind = 0; kind < Sfx.COUNT; kind++) {
                    if (variant == 0 && kind == Sfx.UI) continue;
                    clips.set(kind * VARIANTS + variant, renderer.render(kind, variant));
                }
            }
        });
    }

    short[] clip(int kind, int variant) {
        short[] pcm = clips.get(kind * VARIANTS + variant);
        return pcm != null ? pcm : clips.get(kind * VARIANTS);
    }
}
