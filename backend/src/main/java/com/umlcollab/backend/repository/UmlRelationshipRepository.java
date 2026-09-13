package com.umlcollab.backend.repository;

import com.umlcollab.backend.model.UmlRelationship;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UmlRelationshipRepository extends JpaRepository<UmlRelationship, UUID> {
    List<UmlRelationship> findByDiagramId(UUID diagramId);
}
