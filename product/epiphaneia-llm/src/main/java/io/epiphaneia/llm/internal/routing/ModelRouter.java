package io.epiphaneia.llm.internal.routing;

import io.epiphaneia.llm.api.LlmProviderValidator;
import io.epiphaneia.domain.internal.entity.LlmProvider;
import io.epiphaneia.infra.api.EncryptionService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Routes LLM provider configuration to the correct Spring AI {@code ChatClient}
 * or LangChain4j {@code ChatLanguageModel}.
 * <p>
 * This component validates provider type and decrypts API keys before use.
 * The actual {@code ChatClient} bean selection is deferred to the {@code AiConfig}
 * configuration class which creates named beans for each provider.
 * <p>
 * Supported providers: OPENAI, ANTHROPIC, DEEPSEEK, OLLAMA, CUSTOM
 */
@Component
public class ModelRouter implements LlmProviderValidator {

    private static final Set<String> SUPPORTED_PROVIDERS = Set.of(
            "OPENAI", "ANTHROPIC", "DEEPSEEK", "OLLAMA", "CUSTOM");

    private final EncryptionService encryptionService;

    public ModelRouter(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }

    /**
     * Validate that a provider is supported.
     *
     * @throws IllegalArgumentException if provider is not in the supported set
     */
    public void validateProvider(String provider) {
        if (provider == null || !SUPPORTED_PROVIDERS.contains(provider.toUpperCase())) {
            throw new IllegalArgumentException(
                    "Unsupported LLM provider: " + provider
                    + ". Supported: " + SUPPORTED_PROVIDERS);
        }
    }

    /**
     * Decrypt the stored API key for use in LLM client configuration.
     *
     * @param provider the LLM provider entity
     * @return decrypted API key, or null if no key is stored
     */
    public String decryptApiKey(LlmProvider provider) {
        if (provider.getApiKeyEncrypted() == null) {
            return null;
        }
        return encryptionService.decrypt(provider.getApiKeyEncrypted());
    }

    /**
     * Resolve the effective base URL for a provider.
     * Falls back to well-known defaults if not explicitly configured.
     */
    public String resolveBaseUrl(LlmProvider provider) {
        if (provider.getBaseUrl() != null && !provider.getBaseUrl().isBlank()) {
            return provider.getBaseUrl();
        }
        return defaultBaseUrl(provider.getProvider());
    }

    @Override
    public Set<String> getSupportedProviders() {
        return SUPPORTED_PROVIDERS;
    }

    private static String defaultBaseUrl(String provider) {
        return switch (provider.toUpperCase()) {
            case "OPENAI" -> "https://api.openai.com";
            case "ANTHROPIC" -> "https://api.anthropic.com";
            case "DEEPSEEK" -> "https://api.deepseek.com";
            case "OLLAMA" -> "http://localhost:11434";
            case "CUSTOM" -> "";
            default -> throw new IllegalArgumentException("Unknown provider: " + provider);
        };
    }
}
