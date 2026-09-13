package com.umlcollab.backend.repository;

import com.umlcollab.backend.model.UmlClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UmlClassRepository extends JpaRepository<UmlClass, UUID> {
    List<UmlClass> findByDiagramIdOrderByCreatedAtAsc(UUID diagramId);
}
