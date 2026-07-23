package io.epiphaneia.domain.internal.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "message")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(nullable = false, length = 10)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "diagnosis_state", length = 20)
    private String diagnosisState;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @Column(name = "risk_impact", columnDefinition = "TEXT")
    private String riskImpact;

    @Column(name = "risk_urgency", columnDefinition = "TEXT")
    private String riskUrgency;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("collectedAt ASC")
    private List<Evidence> evidence = new ArrayList<>();

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("rank ASC")
    private List<RootCauseHypothesis> hypotheses = new ArrayList<>();

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<FixSuggestion> suggestions = new ArrayList<>();

    public Message() {}

    public UUID getId() { return id; }

    public Conversation getConversation() { return conversation; }
    public void setConversation(Conversation c) { this.conversation = c; }

    public String getRole() { return role; }
    public void setRole(String r) { this.role = r; }

    public String getContent() { return content; }
    public void setContent(String c) { this.content = c; }

    public String getDiagnosisState() { return diagnosisState; }
    public void setDiagnosisState(String s) { this.diagnosisState = s; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String r) { this.failureReason = r; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String r) { this.riskLevel = r; }

    public String getRiskImpact() { return riskImpact; }
    public void setRiskImpact(String r) { this.riskImpact = r; }

    public String getRiskUrgency() { return riskUrgency; }
    public void setRiskUrgency(String r) { this.riskUrgency = r; }

    public Integer getTokenCount() { return tokenCount; }
    public void setTokenCount(Integer t) { this.tokenCount = t; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant t) { this.completedAt = t; }

    public List<Evidence> getEvidence() { return evidence; }
    public List<RootCauseHypothesis> getHypotheses() { return hypotheses; }
    public List<FixSuggestion> getSuggestions() { return suggestions; }
}
