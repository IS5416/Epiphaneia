package io.epiphaneia.agent.internal.llm;

import io.epiphaneia.agent.api.model.LlmProvider;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * LLM invocation wrapper — configures the {@link ChatClient} per-provider
 * and delegates prompt execution.
 * <p>
 * Each call accepts a {@link LlmProvider} so that provider-specific
 * settings (API key, model, base URL) are applied for that invocation.
 * Provider validation and key decryption are handled by {@link ModelRouter}.
 */
@Component
public class LlmClient {

    private final ChatClient.Builder chatClientBuilder;
    private final ModelRouter modelRouter;

    public LlmClient(ChatClient.Builder chatClientBuilder, ModelRouter modelRouter) {
        this.chatClientBuilder = chatClientBuilder;
        this.modelRouter = modelRouter;
    }

    /**
     * Send a system + user prompt to the LLM.
     *
     * @param systemPrompt the system-level instructions
     * @param userPrompt   the user-level query
     * @param provider     the LLM provider configuration to use
     * @return the LLM's text response
     */
    public String call(String systemPrompt, String userPrompt, LlmProvider provider) {
        return buildClient(provider)
                .prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
    }

    /**
     * Send a combined prompt (system context already merged) to the LLM.
     *
     * @param prompt   the prompt text
     * @param provider the LLM provider configuration to use
     * @return the LLM's text response
     */
    public String call(String prompt, LlmProvider provider) {
        var p = provider != null ? provider : fallbackProvider();
        return buildClient(p)
                .prompt()
                .user(prompt)
                .call()
                .content();
    }

    /** Convenience overload for when no provider is configured (uses default ChatClient). */
    public String call(String prompt) {
        return chatClientBuilder.build()
                .prompt()
                .user(prompt)
                .call()
                .content();
    }

    private static LlmProvider fallbackProvider() {
        LlmProvider p = new LlmProvider();
        p.setProvider("CUSTOM");
        return p;
    }

    private ChatClient buildClient(LlmProvider provider) {
        // ponytail: per-provider URL/api-key config via AiConfig in server module.
        // LlmClient delegates to the auto-configured ChatClient.Builder directly.
        return chatClientBuilder.build();
    }
}
