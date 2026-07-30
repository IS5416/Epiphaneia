package io.epiphaneia.domain.repository;

import io.epiphaneia.domain.entity.FixSuggestion;
import io.epiphaneia.domain.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FixSuggestionRepository extends JpaRepository<FixSuggestion, UUID> {

    List<FixSuggestion> findByMessage(Message message);
}
