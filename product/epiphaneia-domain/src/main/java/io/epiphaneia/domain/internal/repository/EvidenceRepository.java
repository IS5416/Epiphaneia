package io.epiphaneia.domain.internal.repository;

import io.epiphaneia.domain.internal.entity.Evidence;
import io.epiphaneia.domain.internal.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EvidenceRepository extends JpaRepository<Evidence, UUID> {

    List<Evidence> findByMessageOrderByCollectedAtAsc(Message message);
}
