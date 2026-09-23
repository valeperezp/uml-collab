package com.umlcollab.backend.repository;

import com.umlcollab.backend.model.SystemAiConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SystemAiConfigRepository extends JpaRepository<SystemAiConfig, UUID> {
    Optional<SystemAiConfig> findTopByOrderByUpdatedAtDesc();
}
