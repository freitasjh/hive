package br.com.systec.hive.integrationtest;

import br.com.systec.hive.ai.impl.SessionGateRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** AT-01: refcounted retain/remove; no two gates for one @MemoryId, entry gone after last turn. */
class SessionGateEvictionIT {

    @Test
    void when_manyTurnsRetainSameSession_then_singleGateAndEvictionAfterLastRelease() throws Exception {
        SessionGateRegistry registry = new SessionGateRegistry();
        UUID session = UUID.randomUUID();
        int turns = 16;
        CountDownLatch allRetained = new CountDownLatch(turns);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<Thread> workers = new ArrayList<>();

        // One anchor holds the permit while every worker retains and waits on the same fair gate.
        registry.acquire(session);
        Semaphore held = registry.gateFor(session);
        assertThat(held.isFair()).isTrue();

        for (int i = 0; i < turns; i++) {
            Thread turn = new Thread(() -> {
                try {
                    Semaphore observed = registry.gateFor(session); // inspection adds no ref
                    assertThat(observed).as("one semaphore per @MemoryId").isSameAs(held);
                    allRetained.countDown();
                    registry.acquire(session); // retain, then wait for the anchor's permit
                    registry.release(session);
                } catch (Throwable t) {
                    failure.set(t);
                    allRetained.countDown();
                }
            });
            workers.add(turn);
            turn.start();
        }

        assertThat(allRetained.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(failure.get()).isNull();
        assertThat(entryCount(registry)).isEqualTo(1);
        assertThat(held.availablePermits()).isZero();

        registry.release(session);
        for (Thread worker : workers) {
            worker.join(3_000);
            assertThat(worker.isAlive()).isFalse();
        }
        assertThat(failure.get()).isNull();
        assertThat(entryCount(registry)).as("entry removed when the last turn finishes").isZero();
    }

    @Test
    void when_interruptedBeforeAcquire_then_noRefIsLeftBehind() throws Exception {
        SessionGateRegistry registry = new SessionGateRegistry();
        UUID session = UUID.randomUUID();
        CountDownLatch waiting = new CountDownLatch(1);
        Thread waiter = new Thread(() -> {
            waiting.countDown();
            try {
                registry.acquire(session);
            } catch (InterruptedException expected) {
                // registry discards its ref; the flag is restored by the caller in production
            }
        });
        registry.acquire(session); // hold the single permit
        waiter.start();
        assertThat(waiting.await(1, TimeUnit.SECONDS)).isTrue();
        waiter.interrupt();
        waiter.join(1_000);
        registry.release(session);

        assertThat(entryCount(registry)).isZero();
    }

    private static int entryCount(SessionGateRegistry registry) throws Exception {
        Field field = SessionGateRegistry.class.getDeclaredField("gates");
        field.setAccessible(true);
        return ((Map<?, ?>) field.get(registry)).size();
    }
}
