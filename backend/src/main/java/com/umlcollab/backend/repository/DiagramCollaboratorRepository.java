package com.umlcollab.backend.repository;

import com.umlcollab.backend.model.DiagramCollaborator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiagramCollaboratorRepository extends JpaRepository<DiagramCollaborator, UUID> {
    boolean existsByDiagramIdAndUserId(UUID diagramId, UUID userId);

    Optional<DiagramCollaborator> findByDiagramIdAndUserId(UUID diagramId, UUID userId);

    List<DiagramCollaborator> findByUserId(UUID userId);

    @Modifying
    @Query("DELETE FROM DiagramCollaborator dc WHERE dc.diagram.id = :diagramId")
    void deleteByDiagramId(@Param("diagramId") UUID diagramId);

    @Modifying
    @Query("DELETE FROM DiagramCollaborator dc WHERE dc.diagram.id = :diagramId AND dc.userId = :userId")
    void deleteByDiagramIdAndUserId(@Param("diagramId") UUID diagramId, @Param("userId") UUID userId);
}
