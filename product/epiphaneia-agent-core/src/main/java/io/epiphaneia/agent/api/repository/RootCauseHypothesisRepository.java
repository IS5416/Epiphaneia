package io.epiphaneia.agent.api.repository;

import io.epiphaneia.agent.api.model.Message;
import io.epiphaneia.agent.api.model.RootCauseHypothesis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RootCauseHypothesisRepository extends JpaRepository<RootCauseHypothesis, UUID> {

    List<RootCauseHypothesis> findByMessageOrderByRankAsc(Message message);
}
