package br.com.systec.hive.ai.impl;

import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import br.com.systec.hive.ai.api.port.StreamingTurnHandle;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.response.StreamingHandle;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Disconnect and terminal bookkeeping for one streaming turn (ADR-001 D7'/Emenda 1 + Emenda 2).
 *
 * <p>Two independent {@link AtomicBoolean} flags: {@code sinkClosed} silences callbacks, while
 * {@code gateReleased} makes the release idempotent. A degraded {@code cancel()} never releases the
 * gate here — the provider is still generating (S6) and a second turn must not enter its
 * {@code ChatMemory}. A <em>supported</em> cancel releases at once: langchain4j 1.19.0 suppresses
 * {@code onCompleteResponse}/{@code onError} after cancellation (S24,
 * {@code StreamingRequestExecutor.java:149}), so waiting for a terminal would leak the permit.
 */
public final class StreamingTurnHandleImpl implements StreamingTurnHandle {

    static final long CANCEL_DEGRADED_RELEASE_WINDOW_MS = 5_000L;

    private final Consumer<TurnStreamEvent> sink;
    private final Runnable releaseGate;
    private final StreamingMetrics metrics;
    private final ScheduledExecutorService scheduler;
    private final long releaseWindowMillis;

    private final AtomicReference<StreamingHandle> providerHandle = new AtomicReference<>();
    private final AtomicBoolean sinkClosed = new AtomicBoolean();
    private final AtomicBoolean gateReleased = new AtomicBoolean();

    public StreamingTurnHandleImpl(Consumer<TurnStreamEvent> sink, Runnable releaseGate,
            StreamingMetrics metrics, ScheduledExecutorService scheduler) {
        this(sink, releaseGate, metrics, scheduler, CANCEL_DEGRADED_RELEASE_WINDOW_MS);
    }

    StreamingTurnHandleImpl(Consumer<TurnStreamEvent> sink, Runnable releaseGate,
            StreamingMetrics metrics, ScheduledExecutorService scheduler, long releaseWindowMillis) {
        this.sink = sink;
        this.releaseGate = releaseGate;
        this.metrics = metrics;
        this.scheduler = scheduler;
        this.releaseWindowMillis = releaseWindowMillis;
    }

    /** Set from the context-aware partial-response callback; absent on providers without it (S6). */
    void providerHandle(StreamingHandle handle) {
        providerHandle.set(handle);
    }

    /** Emits one event; a terminal releases the gate even when the transport's sink throws (CR-02). */
    void emit(TurnStreamEvent event) {
        boolean terminal = event instanceof TurnStreamEvent.Done || event instanceof TurnStreamEvent.Failed;
        if (terminal) {
            try {
                // A terminal after cancel must still hand the gate back, even though the sink is muted.
                if (!sinkClosed.getAndSet(true)) {
                    if (event instanceof TurnStreamEvent.Failed failed) {
                        metrics.increment("timeout".equals(failed.code()) ? StreamingMetrics.TIMEOUT : StreamingMetrics.FAILED);
                    }
                    sink.accept(event);
                }
            } finally {
                releaseGateOnce();
            }
            return;
        }
        if (!sinkClosed.get()) {
            sink.accept(event);
        }
    }

    @Override
    public void cancel() {
        if (!sinkClosed.compareAndSet(false, true)) {
            return; // idempotent: a second cancel is a no-op
        }
        StreamingHandle handle = providerHandle.get();
        if (handle == null) {
            metrics.increment(StreamingMetrics.CLIENT_ABORT); // AT-04: abort happened, even without a handle
            scheduleGateRelease();
            return;
        }
        try {
            handle.cancel();
            metrics.increment(StreamingMetrics.CLIENT_ABORT);
            releaseGateOnce(); // Emenda 2: no terminal will arrive (S24); the CAS absorbs a late one
        } catch (UnsupportedFeatureException ignored) {
            // isCancelled()==false: the provider keeps generating. Hold the gate until its terminal
            // or the short window — never hand a live ChatMemory to the next turn (F3).
            metrics.increment(StreamingMetrics.CANCEL_UNSUPPORTED);
            scheduleGateRelease();
        }
    }

    private void scheduleGateRelease() {
        scheduler.schedule(this::releaseGateOnce, releaseWindowMillis, TimeUnit.MILLISECONDS);
    }

    private void releaseGateOnce() {
        if (gateReleased.compareAndSet(false, true)) {
            releaseGate.run();
        }
    }
}
