package io.epiphaneia.agent.api.repository;

import io.epiphaneia.agent.api.model.LlmProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LlmProviderRepository extends JpaRepository<LlmProvider, UUID> {

    Optional<LlmProvider> findByProvider(String provider);
}
