package io.epiphaneia.agent.api.repository;

import io.epiphaneia.agent.api.model.ApiToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiTokenRepository extends JpaRepository<ApiToken, UUID> {

    Optional<ApiToken> findByTokenHash(String tokenHash);

    List<ApiToken> findByAdminIdOrderByCreatedAtDesc(UUID adminId);

    @Query("SELECT t FROM ApiToken t WHERE t.admin.id = :adminId AND t.revokedAt IS NULL")
    List<ApiToken> findActiveTokensByAdminId(UUID adminId);
}
