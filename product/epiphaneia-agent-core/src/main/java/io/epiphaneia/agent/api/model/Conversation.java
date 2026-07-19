package io.epiphaneia.agent.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity @Table(name = "conversation")
public class Conversation {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "application_id", nullable = false)
    private Application application;
    @Column(nullable = false, length = 500) private String title;
    @Column(name = "created_at") private Instant createdAt = Instant.now();
    @Column(name = "updated_at") private Instant updatedAt = Instant.now();
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<Message> messages = new ArrayList<>();
    public Conversation() {}
    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String t) { this.title = t; }
    public List<Message> getMessages() { return messages; }
    public void setApplication(Application a) { this.application = a; }
}
