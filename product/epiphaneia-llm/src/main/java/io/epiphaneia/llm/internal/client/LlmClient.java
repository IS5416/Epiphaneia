package io.epiphaneia.llm.internal.client;

import io.epiphaneia.domain.internal.entity.LlmProvider;
import io.epiphaneia.llm.internal.routing.ModelRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * LLM invocation wrapper. Provider-specific configuration (API key, base URL,
 * model name) is handled by Spring AI auto-configuration via environment
 * variables ({@code DEEPSEEK_API_KEY}, {@code OPENAI_API_KEY}, etc.).
 * <p>
 * Multi-provider runtime switching is deferred — the current single-admin
 * model uses one active provider configured through environment or Web UI.
 * When multi-provider is needed, pre-register named
 * {@link ChatClient.Builder} beans and select by provider key.
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final ChatClient.Builder chatClientBuilder;
    private final ModelRouter modelRouter;

    public LlmClient(ChatClient.Builder chatClientBuilder, ModelRouter modelRouter) {
        this.chatClientBuilder = chatClientBuilder;
        this.modelRouter = modelRouter;
    }

    /**
     * Send a system + user prompt to the LLM.
     */
    public String call(String systemPrompt, String userPrompt, LlmProvider provider) {
        modelRouter.validateProvider(provider.getProvider());
        log.debug("LLM call: provider={}, model={}", provider.getProvider(), provider.getModelName());
        return chatClientBuilder.build()
                .prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
    }

    /**
     * Send a combined prompt to the LLM.
     */
    public String call(String prompt, LlmProvider provider) {
        if (provider != null) {
            modelRouter.validateProvider(provider.getProvider());
        }
        log.debug("LLM call: provider={}",
                provider != null ? provider.getProvider() : "default");
        return chatClientBuilder.build()
                .prompt()
                .user(prompt)
                .call()
                .content();
    }

    /** Convenience overload using the auto-configured default ChatClient. */
    public String call(String prompt) {
        return chatClientBuilder.build()
                .prompt()
                .user(prompt)
                .call()
                .content();
    }

    /**
     * Test connectivity for a provider configuration.
     * Sends a "ping" prompt and returns true if the LLM responds.
     */
    public boolean testConnection(LlmProvider provider) {
        try {
            modelRouter.validateProvider(provider.getProvider());
            String response = chatClientBuilder.build()
                    .prompt()
                    .user("Respond with exactly the word: OK")
                    .call()
                    .content();
            log.info("LLM connection test for {}: success", provider.getProvider());
            return response != null && response.contains("OK");
        } catch (Exception e) {
            log.info("LLM connection test for {} failed: {}",
                    provider.getProvider(), e.getMessage());
            return false;
        }
    }
}
