package io.epiphaneia.domain.repository;

import io.epiphaneia.domain.entity.DataSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DataSourceRepository extends JpaRepository<DataSource, UUID> {

    List<DataSource> findByTypeOrderByNameAsc(String type);

    Optional<DataSource> findByName(String name);
}
