package io.epiphaneia.domain.internal.repository;

import io.epiphaneia.domain.internal.entity.Message;
import io.epiphaneia.domain.internal.entity.RootCauseHypothesis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RootCauseHypothesisRepository extends JpaRepository<RootCauseHypothesis, UUID> {

    List<RootCauseHypothesis> findByMessageOrderByRankAsc(Message message);
}
