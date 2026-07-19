package io.epiphaneia.agent.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "api_token")
public class ApiToken {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "admin_id") private Admin admin;
    @Column(nullable = false) private String name;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(nullable = false, length = 12) private String prefix;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    public ApiToken() {}
    public boolean isValid() { return revokedAt == null; }
    public UUID getId() { return id; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String h) { this.tokenHash = h; }
    public String getPrefix() { return prefix; }
    public void setPrefix(String p) { this.prefix = p; }
    public void setRevokedAt(Instant t) { this.revokedAt = t; }
}
