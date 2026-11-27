package br.com.systec.hive.ai.impl;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The single mutual-exclusion registry for a {@code @MemoryId} (ADR-001 D6, Q5, AT-01).
 *
 * <p>A fair binary semaphore is owner-free: the terminal callback runs on a provider thread and can
 * release a permit acquired by the caller, which a {@code ReentrantLock} cannot do. Sync and stream
 * paths must use this same registry, never a second lock. The process-wide {@link #shared()} instance
 * is what makes that sharing true without wiring a second bean into either gateway.
 *
 * <p><b>AT-01 eviction:</b> one entry per live turn, not per session forever. A {@link Gate} carries
 * a refcount; {@link #acquire(UUID)} retains and {@link #release(UUID)} discards. Both use
 * {@link ConcurrentHashMap#compute(Object, java.util.function.BiFunction)}, so retention and the
 * final removal are serialized on the same key. A plain {@code computeIfAbsent} followed by
 * {@code incrementAndGet()} would let the last release remove the entry while a new turn already
 * held a reference to that gate — two semaphores for one {@code @MemoryId} and concurrent
 * {@code ChatMemory} use. {@link #gateFor(UUID)} stays as the read/inspection view: it creates no
 * refcount and therefore cannot keep an unused entry alive.
 */
public final class SessionGateRegistry {

    private static final SessionGateRegistry SHARED = new SessionGateRegistry();

    private final ConcurrentHashMap<UUID, Gate> gates = new ConcurrentHashMap<>();

    /** The one registry used by both the sync and the streaming turn gateways. */
    public static SessionGateRegistry shared() {
        return SHARED;
    }

    /** Read/inspection view; the caller holds no turn and this call adds no refcount. */
    public Semaphore gateFor(UUID sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        return gates.computeIfAbsent(sessionId, ignored -> new Gate()).semaphore;
    }

    /** Retains the gate for one turn, then acquires its single permit. */
    public void acquire(UUID sessionId) throws InterruptedException {
        Objects.requireNonNull(sessionId, "sessionId");
        Gate gate = gates.compute(sessionId, (ignored, current) -> {
            Gate value = current == null ? new Gate() : current;
            value.refs.incrementAndGet();
            return value;
        });
        try {
            gate.semaphore.acquire();
        } catch (InterruptedException e) {
            discard(sessionId); // never leave an orphan ref for a permit that was not acquired
            throw e;
        }
    }

    /** Releases the permit, then drops the ref; the last turn removes the entry. */
    public void release(UUID sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        Gate gate = gates.get(sessionId);
        if (gate == null) {
            throw new IllegalStateException("no gate held for session " + sessionId);
        }
        // Release before discard: a retained waiter may acquire in between and keep the entry alive.
        gate.semaphore.release();
        discard(sessionId);
    }

    private void discard(UUID sessionId) {
        gates.computeIfPresent(sessionId, (ignored, gate) ->
                gate.refs.decrementAndGet() == 0 ? null : gate);
    }

    private static final class Gate {
        private final Semaphore semaphore = new Semaphore(1, true); // the one occurrence in ai/impl
        private final AtomicInteger refs = new AtomicInteger();
    }
}
