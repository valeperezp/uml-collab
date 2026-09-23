package com.umlcollab.backend.repository;

import com.umlcollab.backend.model.Diagram;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiagramRepository extends JpaRepository<Diagram, UUID> {
    List<Diagram> findByOwnerId(UUID ownerId);
    Optional<Diagram> findByJoinCode(String joinCode);

    @Query("SELECT DISTINCT d FROM Diagram d LEFT JOIN d.collaborators c " +
           "WHERE d.ownerId = :userId OR c.userId = :userId " +
           "ORDER BY d.updatedAt DESC")
    List<Diagram> findAllAccessibleByUser(@Param("userId") UUID userId);
}
