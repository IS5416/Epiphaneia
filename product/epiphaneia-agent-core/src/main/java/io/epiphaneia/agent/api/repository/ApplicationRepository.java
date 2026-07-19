package io.epiphaneia.agent.api.repository;

import io.epiphaneia.agent.api.model.Application;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {
    Optional<Application> findByPrometheusLabel(String label);
}
