package io.epiphaneia.domain.repository;

import io.epiphaneia.domain.entity.LlmProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LlmProviderRepository extends JpaRepository<LlmProvider, UUID> {

    Optional<LlmProvider> findByProvider(String provider);
}
