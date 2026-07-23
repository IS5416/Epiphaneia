package io.epiphaneia.domain.internal.entity;

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
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    @Column(name = "revoked_at") private Instant revokedAt;
    public ApiToken() {}
    public boolean isValid() { return revokedAt == null; }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String n) { this.name = n; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String h) { this.tokenHash = h; }
    public String getPrefix() { return prefix; }
    public void setPrefix(String p) { this.prefix = p; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant t) { this.revokedAt = t; }
    public Admin getAdmin() { return admin; }
    public void setAdmin(Admin a) { this.admin = a; }
}
