package io.epiphaneia.agent.api.repository;

import io.epiphaneia.agent.api.model.FixSuggestion;
import io.epiphaneia.agent.api.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FixSuggestionRepository extends JpaRepository<FixSuggestion, UUID> {

    List<FixSuggestion> findByMessage(Message message);
}
