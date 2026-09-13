package com.umlcollab.backend.repository;

import com.umlcollab.backend.model.Diagram;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiagramRepository extends JpaRepository<Diagram, UUID> {
    List<Diagram> findByOwnerId(UUID ownerId);
    Optional<Diagram> findByJoinCode(String joinCode);
}
