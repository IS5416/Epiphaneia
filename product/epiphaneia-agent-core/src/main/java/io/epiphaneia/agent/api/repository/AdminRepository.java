package io.epiphaneia.agent.api.repository;

import io.epiphaneia.agent.api.model.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AdminRepository extends JpaRepository<Admin, UUID> {

    Optional<Admin> findByUsername(String username);

    boolean existsByUsername(String username);
}
