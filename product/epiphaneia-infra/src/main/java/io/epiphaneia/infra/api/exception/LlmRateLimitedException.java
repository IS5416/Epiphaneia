package io.epiphaneia.infra.api.exception;

/** Thrown when an LLM provider returns HTTP 429 — callers may back off and retry. */
public class LlmRateLimitedException extends EpiphaneiaException {
    public LlmRateLimitedException(String message) { super("LLM_RATE_LIMITED", message); }
}
