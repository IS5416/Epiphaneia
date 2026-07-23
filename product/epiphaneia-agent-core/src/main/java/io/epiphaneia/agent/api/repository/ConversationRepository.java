package io.epiphaneia.agent.api.repository;

import io.epiphaneia.domain.internal.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findByApplicationIdOrderByUpdatedAtDesc(UUID applicationId);

    @Query("SELECT c FROM Conversation c WHERE LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY c.updatedAt DESC")
    List<Conversation> searchByTitle(String keyword);
}
