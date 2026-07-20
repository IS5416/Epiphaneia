package io.epiphaneia.agent.api.repository;

import io.epiphaneia.agent.api.model.DataSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DataSourceRepository extends JpaRepository<DataSource, UUID> {

    List<DataSource> findByTypeOrderByNameAsc(String type);

    Optional<DataSource> findByName(String name);
}
