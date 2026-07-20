package io.epiphaneia.agent.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evidence")
public class Evidence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(name = "query_text", nullable = false, columnDefinition = "TEXT")
    private String queryText;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "anomaly_start")
    private Instant anomalyStart;

    @Column(name = "anomaly_end")
    private Instant anomalyEnd;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt = Instant.now();

    public Evidence() {}

    public UUID getId() { return id; }

    public Message getMessage() { return message; }
    public void setMessage(Message m) { this.message = m; }

    public String getSource() { return source; }
    public void setSource(String s) { this.source = s; }

    public String getQueryText() { return queryText; }
    public void setQueryText(String q) { this.queryText = q; }

    public String getSummary() { return summary; }
    public void setSummary(String s) { this.summary = s; }

    public Instant getAnomalyStart() { return anomalyStart; }
    public void setAnomalyStart(Instant a) { this.anomalyStart = a; }

    public Instant getAnomalyEnd() { return anomalyEnd; }
    public void setAnomalyEnd(Instant a) { this.anomalyEnd = a; }

    public Instant getCollectedAt() { return collectedAt; }
    public void setCollectedAt(Instant c) { this.collectedAt = c; }
}
