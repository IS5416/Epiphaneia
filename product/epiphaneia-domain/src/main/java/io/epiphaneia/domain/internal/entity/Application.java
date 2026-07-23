package io.epiphaneia.domain.internal.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "application")
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "actuator_url", length = 500)
    private String actuatorUrl;

    @Column(name = "prometheus_label", nullable = false, length = 200)
    private String prometheusLabel;

    @Column(columnDefinition = "jsonb")
    private String tags = "[]";

    @Column(name = "actuator_info", columnDefinition = "jsonb")
    private String actuatorInfo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("updatedAt DESC")
    private List<Conversation> conversations = new ArrayList<>();

    public Application() {}

    public UUID getId() { return id; }

    public String getName() { return name; }
    public void setName(String n) { this.name = n; }

    public String getActuatorUrl() { return actuatorUrl; }
    public void setActuatorUrl(String u) { this.actuatorUrl = u; }

    public String getPrometheusLabel() { return prometheusLabel; }
    public void setPrometheusLabel(String l) { this.prometheusLabel = l; }

    public String getTags() { return tags; }
    public void setTags(String t) { this.tags = t; }

    public String getActuatorInfo() { return actuatorInfo; }
    public void setActuatorInfo(String a) { this.actuatorInfo = a; }

    public Instant getCreatedAt() { return createdAt; }

    public List<Conversation> getConversations() { return conversations; }
}
