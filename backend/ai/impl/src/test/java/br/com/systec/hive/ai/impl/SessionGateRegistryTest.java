package br.com.systec.hive.ai.impl;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** GATE-06 — one fair, owner-free semaphore per session, shared by sync and streaming. */
class SessionGateRegistryTest {

    private final SessionGateRegistry registry = new SessionGateRegistry();

    @Test
    void when_gateForSameSession_then_sameFairSemaphore() {
        UUID sessionId = UUID.randomUUID();
        Semaphore first = registry.gateFor(sessionId);

        assertThat(registry.gateFor(sessionId)).isSameAs(first);
        assertThat(first.isFair()).isTrue();
        assertThat(first.availablePermits()).isEqualTo(1);
        assertThat(registry.gateFor(UUID.randomUUID())).isNotSameAs(first);
    }

    @Test
    void when_terminalRunsOnProviderThread_then_releaseDoesNotThrow() throws Exception {
        UUID sessionId = UUID.randomUUID();
        Semaphore gate = registry.gateFor(sessionId);
        gate.acquire();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread providerThread = new Thread(() -> {
            try {
                gate.release();
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        providerThread.start();
        providerThread.join(1_000);

        assertThat(failure.get()).isNull();
        assertThat(gate.availablePermits()).isEqualTo(1);
    }

    @Test
    void when_secondTurnStartsWhileFirstHoldsGate_then_itWaitsUntilRelease() throws Exception {
        Semaphore gate = registry.gateFor(UUID.randomUUID());
        gate.acquire();
        CountDownLatch acquired = new CountDownLatch(1);
        AtomicBoolean secondEntered = new AtomicBoolean(false);

        Thread second = new Thread(() -> {
            try {
                gate.acquire();
                secondEntered.set(true);
                acquired.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        second.start();

        assertThat(acquired.await(50, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(secondEntered).isFalse();
        gate.release();
        assertThat(acquired.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(secondEntered).isTrue();
    }

    @Test
    void when_acquireInterrupted_then_flagRestoredAndNoExtraPermit() throws Exception {
        Semaphore gate = registry.gateFor(UUID.randomUUID());
        gate.acquire();
        CountDownLatch waiting = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();

        Thread waiter = new Thread(() -> {
            waiting.countDown();
            try {
                gate.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // WF6 contract: gateway restores the flag
                interrupted.set(Thread.currentThread().isInterrupted());
            }
        });
        waiter.start();
        assertThat(waiting.await(1, TimeUnit.SECONDS)).isTrue();
        waiter.interrupt();
        waiter.join(1_000);

        assertThat(interrupted).isTrue();
        assertThatCode(gate::release).doesNotThrowAnyException();
        assertThat(gate.availablePermits()).isEqualTo(1);
    }

    @Test
    void when_manyTurnsRetainConcurrently_then_oneGatePerSessionAndEntryRemovedOnLastRelease() throws Exception {
        UUID sessionId = UUID.randomUUID();
        int turns = 12;
        CountDownLatch allRetained = new CountDownLatch(turns);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Semaphore> anchor = new AtomicReference<>();
        java.util.List<Thread> workers = new java.util.ArrayList<>();

        // The anchor holds the only permit while every worker retains (and blocks) on the same gate.
        registry.acquire(sessionId);
        Semaphore held = registry.gateFor(sessionId);
        anchor.set(held);

        for (int i = 0; i < turns; i++) {
            Thread turn = new Thread(() -> {
                try {
                    Semaphore observed = registry.gateFor(sessionId); // inspection, no ref
                    assertThat(observed).as("one semaphore per session").isSameAs(anchor.get());
                    allRetained.countDown();
                    registry.acquire(sessionId); // retain, then wait for the anchor's permit
                    assertThat(registry.gateFor(sessionId)).isSameAs(held);
                    registry.release(sessionId);
                } catch (Throwable t) {
                    failure.set(t);
                    allRetained.countDown();
                }
            });
            workers.add(turn);
            turn.start();
        }

        assertThat(allRetained.await(3, TimeUnit.SECONDS)).isTrue();
        awaitRefCount(turns + 1);
        assertThat(failure.get()).isNull();
        assertThat(entryCount()).isEqualTo(1);
        assertThat(held.availablePermits()).isZero();

        registry.release(sessionId);
        for (Thread worker : workers) {
            worker.join(3_000);
            assertThat(worker.isAlive()).isFalse();
        }
        assertThat(failure.get()).isNull();
        assertThat(entryCount()).as("entry evicted when the last turn releases").isZero();
    }

    private void awaitRefCount(int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (refCount() != expected && System.nanoTime() < deadline) {
            Thread.yield();
        }
        assertThat(refCount()).isEqualTo(expected);
    }

    private int refCount() throws Exception {
        Field gatesField = SessionGateRegistry.class.getDeclaredField("gates");
        gatesField.setAccessible(true);
        Map<?, ?> map = (Map<?, ?>) gatesField.get(registry);
        Object gate = map.values().stream().findFirst().orElseThrow();
        Field refs = gate.getClass().getDeclaredField("refs");
        refs.setAccessible(true);
        return ((java.util.concurrent.atomic.AtomicInteger) refs.get(gate)).get();
    }

    private int entryCount() throws Exception {
        Field field = SessionGateRegistry.class.getDeclaredField("gates");
        field.setAccessible(true);
        return ((Map<?, ?>) field.get(registry)).size();
    }
}
