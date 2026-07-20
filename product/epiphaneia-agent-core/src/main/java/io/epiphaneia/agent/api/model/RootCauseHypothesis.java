package io.epiphaneia.agent.api.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "root_cause_hypothesis",
       uniqueConstraints = @UniqueConstraint(columnNames = {"message_id", "rank"}))
public class RootCauseHypothesis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @Column(nullable = false)
    private Short rank;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "DOUBLE PRECISION")
    private Double confidence;

    @Column(name = "supporting_evidence_ids", columnDefinition = "jsonb")
    private String supportingEvidenceIds = "[]";

    public RootCauseHypothesis() {}

    public UUID getId() { return id; }
    public Message getMessage() { return message; }
    public void setMessage(Message m) { this.message = m; }
    public Short getRank() { return rank; }
    public void setRank(Short r) { this.rank = r; }
    public String getDescription() { return description; }
    public void setDescription(String d) { this.description = d; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double c) { this.confidence = c; }
    public String getSupportingEvidenceIds() { return supportingEvidenceIds; }
    public void setSupportingEvidenceIds(String s) { this.supportingEvidenceIds = s; }
}
