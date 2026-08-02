package io.epiphaneia.domain.repository;

import io.epiphaneia.domain.entity.Conversation;
import io.epiphaneia.domain.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByConversationOrderByCreatedAtAsc(Conversation conversation);

    Optional<Message> findTopByConversationOrderByCreatedAtDesc(Conversation conversation);

    /**
     * O(1)-ish existence check for in-flight diagnoses — avoids loading all messages.
     * Callers must pass a non-empty {@code diagnosisStates} (Hibernate cannot render
     * an empty IN clause).
     */
    boolean existsByConversationAndDiagnosisStateIn(Conversation conversation,
            java.util.Collection<String> diagnosisStates);

    @Query("SELECT m FROM Message m WHERE m.diagnosisState IN ('CREATED','PLANNING','QUERYING','ANALYZING')")
    List<Message> findActiveDiagnoses();

    @Query("SELECT m FROM Message m WHERE m.diagnosisState IN ('CREATED','PLANNING','QUERYING','ANALYZING') AND m.createdAt < :cutoff")
    List<Message> findStaleDiagnoses(java.time.Instant cutoff);
}
