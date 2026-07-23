package io.epiphaneia.llm.api;

import java.util.UUID;

/**
 * Callback for emitting Server-Sent Events during diagnosis.
 * <p>
 * The orchestrator calls these methods at each state transition so the
 * server layer can push real-time progress to connected SSE clients.
 * Implemented in the server module (SseEmitterManager).
 */
public interface DiagnosisSseEventPublisher {

    /** No-op instance for use when no SSE client is connected. */
    DiagnosisSseEventPublisher NOOP = new DiagnosisSseEventPublisher() {
        @Override public void state(UUID cid, UUID mid, String s) {}
        @Override public void step(UUID cid, UUID mid, String d) {}
        @Override public void token(UUID cid, UUID mid, String t) {}
        @Override public void done(UUID cid, UUID mid) {}
        @Override public void error(UUID cid, UUID mid, String r) {}
        @Override public void close(UUID cid) {}
    };

    /** Emit a state transition event. */
    void state(UUID conversationId, UUID messageId, String newState);

    /** Emit an intermediate step (e.g. "Querying Prometheus for error rate..."). */
    void step(UUID conversationId, UUID messageId, String description);

    /** Stream a token of LLM output. */
    void token(UUID conversationId, UUID messageId, String token);

    /** Diagnosis completed successfully. */
    void done(UUID conversationId, UUID messageId);

    /** Diagnosis completed with errors or partial results. */
    void error(UUID conversationId, UUID messageId, String reason);

    /** SSE stream should close (sentinel event). */
    void close(UUID conversationId);
}
