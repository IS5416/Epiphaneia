package io.epiphaneia.agent.api.repository;

import io.epiphaneia.agent.api.model.ApiToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApiTokenRepository extends JpaRepository<ApiToken, UUID> {
    Optional<ApiToken> findByTokenHash(String tokenHash);
}
