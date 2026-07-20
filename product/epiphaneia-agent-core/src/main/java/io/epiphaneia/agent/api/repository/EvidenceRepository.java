package io.epiphaneia.agent.api.repository;

import io.epiphaneia.agent.api.model.Evidence;
import io.epiphaneia.agent.api.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EvidenceRepository extends JpaRepository<Evidence, UUID> {

    List<Evidence> findByMessageOrderByCollectedAtAsc(Message message);
}
