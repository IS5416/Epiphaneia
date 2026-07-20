package io.epiphaneia.agent.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "data_source")
public class DataSource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(name = "auth_type", nullable = false, length = 20)
    private String authType = "NONE";

    @Column(name = "auth_config", columnDefinition = "jsonb")
    private String authConfig;

    @Column(name = "is_connected")
    private boolean connected;

    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public DataSource() {}

    public UUID getId() { return id; }

    public String getType() { return type; }
    public void setType(String t) { this.type = t; }

    public String getName() { return name; }
    public void setName(String n) { this.name = n; }

    public String getUrl() { return url; }
    public void setUrl(String u) { this.url = u; }

    public String getAuthType() { return authType; }
    public void setAuthType(String a) { this.authType = a; }

    public String getAuthConfig() { return authConfig; }
    public void setAuthConfig(String a) { this.authConfig = a; }

    public boolean isConnected() { return connected; }
    public void setConnected(boolean c) { this.connected = c; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String m) { this.metadata = m; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant t) { this.updatedAt = t; }
}
