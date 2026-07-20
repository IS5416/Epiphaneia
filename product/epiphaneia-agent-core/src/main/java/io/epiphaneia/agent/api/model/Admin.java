package io.epiphaneia.agent.api.model;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name = "admin")
public class Admin {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true, length = 50) private String username = "admin";
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(name = "must_change_password", nullable = false) private boolean mustChangePassword = true;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    public Admin() {}
    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean b) { this.mustChangePassword = b; }
    public Instant getCreatedAt() { return createdAt; }
}
