package io.epiphaneia.agent.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "llm_provider")
public class LlmProvider {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, length = 50) private String provider;
    @Column(name = "model_name", nullable = false) private String modelName;
    @Column(name = "api_key_encrypted", columnDefinition = "TEXT") private String apiKeyEncrypted;
    @Column(name = "base_url", length = 500) private String baseUrl;
    @Column(name = "is_connected") private boolean connected;
    @Column(name = "updated_at") private Instant updatedAt;
    public LlmProvider() {}
    public UUID getId() { return id; }
    public String getProvider() { return provider; }
    public String getModelName() { return modelName; }
    public String getApiKeyEncrypted() { return apiKeyEncrypted; }
    public void setApiKeyEncrypted(String k) { this.apiKeyEncrypted = k; }
    public String getBaseUrl() { return baseUrl; }
    public boolean isConnected() { return connected; }
}
