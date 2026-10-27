package com.ccs.voxelstrike.audio;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public class SoundBankTest {
    @Test public void repeatedPreparationSchedulesOneBankAndKeepsEveryVariant() {
        List<Runnable> jobs = new ArrayList<>();
        AtomicInteger renders = new AtomicInteger();
        SoundBank bank = new SoundBank(jobs::add, (kind, variant) -> {
            renders.incrementAndGet();
            return new short[]{(short) kind, (short) variant};
        });
        for (int lifecycle = 0; lifecycle < 20; lifecycle++) bank.prepare();
        assertEquals("menu, match and resumes share one preparation", 1, jobs.size());
        assertEquals("requesting the bank must not synthesize on the caller", 0, renders.get());
        assertNull(bank.clip(Sfx.UI, 0));
        jobs.get(0).run();
        assertEquals(Sfx.COUNT * SoundBank.VARIANTS, renders.get());
        for (int kind = 0; kind < Sfx.COUNT; kind++) {
            for (int variant = 0; variant < SoundBank.VARIANTS; variant++) {
                assertArrayEquals(new short[]{(short) kind, (short) variant}, bank.clip(kind, variant));
            }
        }
        short[] cached = bank.clip(Sfx.SHOT_AK15, 2);
        bank.prepare();
        assertEquals(1, jobs.size());
        assertSame(cached, bank.clip(Sfx.SHOT_AK15, 2));
    }

    @Test public void uiClipPublishesWhileSlowSynthesisContinuesOffTheCaller() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        CountDownLatch enteredSlowClip = new CountDownLatch(1), finishSlowClip = new CountDownLatch(1);
        AtomicReference<Future<?>> pending = new AtomicReference<>();
        AtomicReference<Thread> synthesisThread = new AtomicReference<>();
        Thread caller = Thread.currentThread();
        short[] ui = new short[]{123, 456};
        SoundBank bank = new SoundBank(task -> pending.set(worker.submit(task)), (kind, variant) -> {
            synthesisThread.set(Thread.currentThread());
            if (kind == Sfx.RIFLE && variant == 0) {
                enteredSlowClip.countDown();
                try { assertTrue(finishSlowClip.await(2, TimeUnit.SECONDS)); }
                catch (InterruptedException error) { throw new AssertionError(error); }
            }
            return kind == Sfx.UI && variant == 0 ? ui : new short[]{1, 2};
        });
        try {
            bank.prepare();
            assertTrue(enteredSlowClip.await(2, TimeUnit.SECONDS));
            assertNotSame(caller, synthesisThread.get());
            assertFalse("slow synthesis cannot delay the caller", pending.get().isDone());
            assertSame(ui, bank.clip(Sfx.UI, 0));
            assertSame("missing variants temporarily use the first published clip", ui, bank.clip(Sfx.UI, 2));
            assertNull(bank.clip(Sfx.RIFLE, 0));
            bank.prepare();
            finishSlowClip.countDown();
            pending.get().get(2, TimeUnit.SECONDS);
            assertNotNull(bank.clip(Sfx.RIFLE, 2));
        } finally {
            finishSlowClip.countDown();
            worker.shutdownNow();
        }
    }
}
