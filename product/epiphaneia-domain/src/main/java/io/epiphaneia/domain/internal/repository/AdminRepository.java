package io.epiphaneia.domain.internal.repository;

import io.epiphaneia.domain.internal.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AdminRepository extends JpaRepository<Admin, UUID> {

    Optional<Admin> findByUsername(String username);

    boolean existsByUsername(String username);
}
