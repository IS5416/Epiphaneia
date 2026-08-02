package io.epiphaneia.llm.api.client;

import io.epiphaneia.infra.api.exception.LlmRateLimitedException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiCompatibleChatModelTest {

    private MockWebServer server;
    private OpenAiCompatibleChatModel model;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        model = new OpenAiCompatibleChatModel(server.url("/").toString(), "test-key", "test-model");
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("successful call parses content from response")
    void successfulCall() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"choices":[{"message":{"role":"assistant","content":"Hello there"}}]}
                        """));

        ChatResponse response = model.call(new Prompt(List.of(new UserMessage("hi"))));

        assertEquals("Hello there", response.getResult().getOutput().getText());
        // request path must be the OpenAI-compatible completions endpoint
        assertEquals("/v1/chat/completions", server.takeRequest().getPath());
    }

    @Test
    @DisplayName("HTTP 429 maps to LlmRateLimitedException with Retry-After")
    void rateLimitMapped() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(429)
                .setHeader("Retry-After", "30")
                .setBody("{\"error\":{\"message\":\"rate limited\"}}"));

        LlmRateLimitedException ex = assertThrows(LlmRateLimitedException.class,
                () -> model.call(new Prompt(List.of(new UserMessage("hi")))));
        assertTrue(ex.getMessage().contains("Retry-After: 30"));
    }

    @Test
    @DisplayName("HTTP 401 maps to IllegalArgumentException with status code")
    void unauthorizedMapped() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(401)
                .setBody("{\"error\":{\"message\":\"invalid key\"}}"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> model.call(new Prompt(List.of(new UserMessage("hi")))));
        assertTrue(ex.getMessage().contains("401"));
    }

    @Test
    @DisplayName("HTTP 500 maps to IllegalStateException with status code")
    void serverErrorMapped() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> model.call(new Prompt(List.of(new UserMessage("hi")))));
        assertTrue(ex.getMessage().contains("500"));
    }

    @Test
    @DisplayName("response without choices throws descriptive error")
    void noChoicesThrows() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"choices\":[]}"));

        assertThrows(IllegalStateException.class,
                () -> model.call(new Prompt(List.of(new UserMessage("hi")))));
    }

    @Test
    @DisplayName("baseUrl with trailing /v1/ is normalized before request")
    void trailingV1Normalized() throws Exception {
        // model was built with server.url() (no /v1); rebuild with a /v1/ suffix
        model = new OpenAiCompatibleChatModel(server.url("/v1/").toString(), "k", "m");
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}"));

        model.call(new Prompt(List.of(new UserMessage("hi"))));

        assertEquals("/v1/chat/completions", server.takeRequest().getPath());
    }
}
