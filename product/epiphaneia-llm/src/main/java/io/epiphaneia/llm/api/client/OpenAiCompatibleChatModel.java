package io.epiphaneia.llm.api.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal {@link ChatModel} that calls any OpenAI-compatible Chat Completions API
 * (OpenAI, DeepSeek, Ollama, Groq, etc.) via Spring's {@link RestClient}.
 * <p>
 * Zero dependency on the OpenAI Java SDK — uses only {@code spring-web} (included
 * with {@code spring-boot-starter-web}) and {@code spring-ai-model}.
 */
public class OpenAiCompatibleChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleChatModel.class);

    private final RestClient restClient;
    private final String modelName;

    public OpenAiCompatibleChatModel(String baseUrl, String apiKey, String modelName) {
        // ponytail: strip trailing /v1 or / — DeepSeek and OpenAI base URLs often include it
        String cleanUrl = baseUrl.replaceAll("/v1/?$", "").replaceAll("/+$", "");
        this.restClient = RestClient.builder()
                .baseUrl(cleanUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
        this.modelName = modelName;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        List<Message> messages = prompt.getInstructions();
        Map<String, Object> body = buildRequestBody(messages);

        log.debug("OpenAI-compatible call: model={}, url={}/v1/chat/completions, messages={}",
                modelName, restClient.toString(), messages.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        return parseResponse(response);
    }

    // ── request building ──────────────────────────────────────────────────

    private Map<String, Object> buildRequestBody(List<Message> messages) {
        List<Map<String, String>> msgList = new ArrayList<>();
        for (Message msg : messages) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("role", toOpenAiRole(msg.getMessageType()));
            m.put("content", msg.getText() != null ? msg.getText() : "");
            msgList.add(m);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("messages", msgList);
        body.put("stream", false);
        return body;
    }

    private static String toOpenAiRole(MessageType type) {
        return switch (type) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case TOOL -> "tool";
        };
    }

    // ── response parsing ──────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private ChatResponse parseResponse(Map<String, Object> response) {
        List<Map<String, Object>> choices =
                (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("No choices in response: " + response);
        }

        List<Generation> generations = new ArrayList<>();
        for (Map<String, Object> choice : choices) {
            Map<String, Object> msg = (Map<String, Object>) choice.get("message");
            String content = msg != null ? (String) msg.get("content") : "";
            if (content == null) content = "";

            AssistantMessage assistant = new AssistantMessage(content);
            generations.add(new Generation(assistant));
        }

        return ChatResponse.builder().generations(generations).build();
    }
}
