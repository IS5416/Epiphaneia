package io.epiphaneia.agent.api.repository;

import io.epiphaneia.domain.internal.entity.Conversation;
import io.epiphaneia.domain.internal.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByConversationOrderByCreatedAtAsc(Conversation conversation);

    Optional<Message> findTopByConversationOrderByCreatedAtDesc(Conversation conversation);

    @Query("SELECT m FROM Message m WHERE m.diagnosisState IN ('CREATED','PLANNING','QUERYING','ANALYZING')")
    List<Message> findActiveDiagnoses();

    @Query("SELECT m FROM Message m WHERE m.diagnosisState IN ('CREATED','PLANNING','QUERYING','ANALYZING') AND m.createdAt < :cutoff")
    List<Message> findStaleDiagnoses(java.time.Instant cutoff);
}
