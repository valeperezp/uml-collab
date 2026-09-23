package com.umlcollab.backend.repository;

import com.umlcollab.backend.model.UmlRelationship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UmlRelationshipRepository extends JpaRepository<UmlRelationship, UUID> {
    List<UmlRelationship> findByDiagramId(UUID diagramId);

    @Modifying
    @Query("DELETE FROM UmlRelationship r WHERE r.diagram.id = :diagramId")
    void deleteByDiagramId(@Param("diagramId") UUID diagramId);

    @Modifying
    @Query("DELETE FROM UmlRelationship r WHERE r.sourceClass.id = :classId OR r.targetClass.id = :classId")
    void deleteByClassId(@Param("classId") UUID classId);
}
