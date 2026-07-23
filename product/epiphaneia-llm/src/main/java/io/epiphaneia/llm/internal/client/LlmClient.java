package io.epiphaneia.llm.internal.client;

import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import io.epiphaneia.domain.internal.entity.LlmProvider;
import io.epiphaneia.llm.internal.routing.ModelRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * LLM invocation wrapper.
 * <p>
 * Provider-specific configuration (API key, base URL, model name) is read from
 * the {@link LlmProvider} entity — API keys are decrypted at call time and used
 * to build a dedicated {@link ChatClient} per request for the specified provider.
 * The auto-configured {@link ChatClient.Builder} (from env vars) is used as a
 * fallback for the no-provider convenience overloads.
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final ModelRouter modelRouter;

    public LlmClient(ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
                      ModelRouter modelRouter) {
        this.chatClientBuilderProvider = chatClientBuilderProvider;
        this.modelRouter = modelRouter;
    }

    // ── provider-specific (uses DB-stored API key + base URL) ──────────────

    public String call(String systemPrompt, String userPrompt, LlmProvider provider) {
        modelRouter.validateProvider(provider.getProvider());
        log.debug("LLM call: provider={}, model={}", provider.getProvider(), provider.getModelName());
        return buildClient(provider).prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
    }

    public String call(String prompt, LlmProvider provider) {
        modelRouter.validateProvider(provider.getProvider());
        log.debug("LLM call: provider={}", provider.getProvider());
        return buildClient(provider).prompt()
                .user(prompt)
                .call()
                .content();
    }

    public boolean testConnection(LlmProvider provider) {
        try {
            modelRouter.validateProvider(provider.getProvider());
            String response = buildClient(provider).prompt()
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

    // ── fallback (uses auto-configured ChatClient.Builder from env vars) ───

    public String call(String prompt) {
        return getBuilder().build().prompt()
                .user(prompt)
                .call()
                .content();
    }

    // ── internal ───────────────────────────────────────────────────────────

    private ChatClient buildClient(LlmProvider provider) {
        String apiKey = modelRouter.decryptApiKey(provider);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "No API key stored for provider: " + provider.getProvider());
        }
        String baseUrl = modelRouter.resolveBaseUrl(provider);

        var clientOptions = new ClientOptions.Builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
        var openAiClient = new OpenAIClientImpl(clientOptions);
        var chatModel = OpenAiChatModel.builder()
                .openAiClient(openAiClient)
                .options(OpenAiChatOptions.builder()
                        .model(provider.getModelName())
                        .build())
                .build();
        return ChatClient.builder(chatModel).build();
    }

    private ChatClient.Builder getBuilder() {
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        if (builder == null) {
            throw new IllegalStateException(
                    "No LLM API key configured. Set OPENAI_API_KEY or DEEPSEEK_API_KEY "
                    + "environment variable, or configure via Web UI Settings page.");
        }
        return builder;
    }
}
