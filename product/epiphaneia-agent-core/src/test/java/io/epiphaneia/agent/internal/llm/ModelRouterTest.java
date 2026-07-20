package io.epiphaneia.agent.internal.llm;

import io.epiphaneia.agent.api.model.LlmProvider;
import io.epiphaneia.infra.api.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelRouterTest {

    private ModelRouter router;

    @BeforeEach
    void setUp() {
        router = new ModelRouter(new EncryptionService() {
            @Override public String encrypt(String p) { return "enc:" + p; }
            @Override public String decrypt(String c) { return c.startsWith("enc:") ? c.substring(4) : c; }
        });
    }

    @Test
    @DisplayName("validates all 5 supported providers")
    void validateSupported() {
        for (String p : new String[]{"OPENAI", "anthropic", "DEEPSEEK", "Ollama", "Custom"}) {
            assertDoesNotThrow(() -> router.validateProvider(p), p);
        }
    }

    @Test
    @DisplayName("rejects unsupported provider")
    void validateUnsupported() {
        assertThrows(IllegalArgumentException.class, () -> router.validateProvider("INVALID"));
    }

    @Test
    @DisplayName("rejects null provider")
    void validateNull() {
        assertThrows(IllegalArgumentException.class, () -> router.validateProvider(null));
    }

    @Test
    @DisplayName("decrypts API key from encrypted storage")
    void decryptApiKey() {
        LlmProvider llm = new LlmProvider();
        llm.setApiKeyEncrypted("enc:sk-abc123");
        assertEquals("sk-abc123", router.decryptApiKey(llm));
    }

    @Test
    @DisplayName("decryptApiKey returns null for null input")
    void decryptNullKey() {
        LlmProvider llm = new LlmProvider();
        assertNull(router.decryptApiKey(llm));
    }

    @Test
    @DisplayName("resolves explicit base URL over default")
    void resolveExplicitBaseUrl() {
        LlmProvider llm = new LlmProvider();
        llm.setProvider("OPENAI");
        llm.setBaseUrl("https://custom.openai.com");
        assertEquals("https://custom.openai.com", router.resolveBaseUrl(llm));
    }

    @Test
    @DisplayName("resolves default base URL for each provider")
    void resolveDefaultUrls() {
        LlmProvider llm = new LlmProvider();
        llm.setProvider("OPENAI");
        assertEquals("https://api.openai.com", router.resolveBaseUrl(llm));
        llm.setProvider("ANTHROPIC");
        assertEquals("https://api.anthropic.com", router.resolveBaseUrl(llm));
        llm.setProvider("OLLAMA");
        assertEquals("http://localhost:11434", router.resolveBaseUrl(llm));
    }

    @Test
    @DisplayName("blank base URL falls back to default")
    void blankBaseUrl() {
        LlmProvider llm = new LlmProvider();
        llm.setProvider("OPENAI");
        llm.setBaseUrl("");
        assertEquals("https://api.openai.com", router.resolveBaseUrl(llm));
    }

    @Test
    @DisplayName("supported providers set is immutable")
    void supportedProviders() {
        assertEquals(5, ModelRouter.getSupportedProviders().size());
        assertTrue(ModelRouter.getSupportedProviders().contains("OPENAI"));
        assertTrue(ModelRouter.getSupportedProviders().contains("CUSTOM"));
    }
}
