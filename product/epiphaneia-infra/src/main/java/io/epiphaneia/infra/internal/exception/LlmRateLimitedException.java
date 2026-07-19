package io.epiphaneia.infra.internal.exception;

public class LlmRateLimitedException extends EpiphaneiaException {
    public LlmRateLimitedException(String message) { super("LLM_RATE_LIMITED", message); }
}
