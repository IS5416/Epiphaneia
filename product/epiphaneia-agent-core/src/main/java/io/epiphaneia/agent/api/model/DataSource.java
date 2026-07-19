package io.epiphaneia.agent.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "data_source")
public class DataSource {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, length = 50) private String type;
    @Column(nullable = false) private String name;
    @Column(nullable = false, length = 500) private String url;
    @Column(name = "auth_type", length = 20) private String authType = "NONE";
    @Column(name = "auth_config", columnDefinition = "jsonb") private String authConfig;
    @Column(name = "is_connected") private boolean connected;
    @Column(columnDefinition = "jsonb") private String metadata;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;
    public DataSource() {}
    public UUID getId() { return id; }
    public String getType() { return type; }
    public String getUrl() { return url; }
    public String getAuthType() { return authType; }
    public String getAuthConfig() { return authConfig; }
    public String getMetadata() { return metadata; }
}
