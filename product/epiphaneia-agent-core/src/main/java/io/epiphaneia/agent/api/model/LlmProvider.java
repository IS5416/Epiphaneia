package io.epiphaneia.agent.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "llm_provider")
public class LlmProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "model_name", nullable = false, length = 200)
    private String modelName;

    @Column(name = "api_key_encrypted", columnDefinition = "TEXT")
    private String apiKeyEncrypted;

    @Column(name = "base_url", length = 500)
    private String baseUrl;

    @Column(name = "is_connected")
    private boolean connected;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public LlmProvider() {}

    public UUID getId() { return id; }

    public String getProvider() { return provider; }
    public void setProvider(String p) { this.provider = p; }

    public String getModelName() { return modelName; }
    public void setModelName(String m) { this.modelName = m; }

    public String getApiKeyEncrypted() { return apiKeyEncrypted; }
    public void setApiKeyEncrypted(String k) { this.apiKeyEncrypted = k; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String u) { this.baseUrl = u; }

    public boolean isConnected() { return connected; }
    public void setConnected(boolean c) { this.connected = c; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant t) { this.updatedAt = t; }
}
