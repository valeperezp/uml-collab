package com.umlcollab.backend.repository;

import com.umlcollab.backend.model.UmlClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UmlClassRepository extends JpaRepository<UmlClass, UUID> {
    List<UmlClass> findByDiagramIdOrderByCreatedAtAsc(UUID diagramId);

    @Modifying
    @Query("DELETE FROM UmlClass c WHERE c.diagram.id = :diagramId")
    void deleteByDiagramId(@Param("diagramId") UUID diagramId);
}
