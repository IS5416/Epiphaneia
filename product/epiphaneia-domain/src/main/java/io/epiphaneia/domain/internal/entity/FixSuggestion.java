package io.epiphaneia.domain.internal.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "fix_suggestion")
public class FixSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "auto_execution_allowed", nullable = false)
    private boolean autoExecutionAllowed;

    public FixSuggestion() {}

    public UUID getId() { return id; }
    public Message getMessage() { return message; }
    public void setMessage(Message m) { this.message = m; }
    public String getDescription() { return description; }
    public void setDescription(String d) { this.description = d; }
    public boolean isAutoExecutionAllowed() { return autoExecutionAllowed; }
    public void setAutoExecutionAllowed(boolean a) { this.autoExecutionAllowed = a; }
}
