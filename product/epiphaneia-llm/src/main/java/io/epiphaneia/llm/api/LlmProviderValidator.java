package io.epiphaneia.llm.api;

/**
 * Public API for validating LLM provider configurations.
 * Implemented by ModelRouter in the internal layer.
 */
public interface LlmProviderValidator {

    /** Validate that a provider string is supported. */
    void validateProvider(String provider);

    /** Return the set of supported provider names. */
    java.util.Set<String> getSupportedProviders();
}
