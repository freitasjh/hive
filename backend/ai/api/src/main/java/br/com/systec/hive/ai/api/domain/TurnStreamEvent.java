package br.com.systec.hive.ai.api.domain;

import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

import java.util.UUID;

/**
 * One event per provider callback (ADR-001 D3). The events are ephemeral: no text is persisted and
 * no frame is emitted for a callback the provider never made.
 *
 * <p>{@link Failed#cause()} stays in memory for the caller's classification. It is never rendered:
 * the wire layer uses {@link Failed#code()} only (F1).
 */
public sealed interface TurnStreamEvent permits TurnStreamEvent.Reasoning, TurnStreamEvent.Text,
        TurnStreamEvent.Done, TurnStreamEvent.Failed {

    record Reasoning(String text) implements TurnStreamEvent { }

    record Text(String text) implements TurnStreamEvent { }

    /**
     * {@code sessionId} is optional: the 2-arg constructor preserves every existing caller (and
     * the public wire, which must never expose it).
     */
    record Done(FinishReason finishReason, TokenUsage usage, UUID sessionId) implements TurnStreamEvent {
        public Done(FinishReason finishReason, TokenUsage usage) {
            this(finishReason, usage, null);
        }
    }

    /**
     * Only the closed-set code is rendered. The cause stays in memory for classification; its
     * message can interpolate thinking text or a credential (security F1).
     */
    record Failed(String code, Throwable cause) implements TurnStreamEvent {
        @Override
        public String toString() {
            return "Failed[code=" + code + "]";
        }
    }

    /**
     * Wire-facing projections. Transport modules see {@code ai/api} but not the {@code provided}
     * langchain4j classes in {@code Done}'s signature, so they read it through here instead of
     * naming {@code FinishReason}/{@code TokenUsage} directly.
     */
    static String finishReasonName(Done done) {
        FinishReason reason = done.finishReason();
        return reason == null ? null : reason.name();
    }

    /** {@code {input, output, total}}, or {@code null} when the provider reported no usage. */
    static Integer[] tokenCounts(Done done) {
        TokenUsage usage = done.usage();
        if (usage == null) {
            return null;
        }
        return new Integer[]{usage.inputTokenCount(), usage.outputTokenCount(), usage.totalTokenCount()};
    }
}
