package io.epiphaneia.agent.api.repository;

import io.epiphaneia.agent.api.model.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    Optional<Application> findByPrometheusLabel(String label);

    @Query("SELECT a FROM Application a WHERE LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY a.createdAt DESC")
    List<Application> searchByName(String keyword);
}
