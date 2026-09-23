package com.umlcollab.backend.repository;

import com.umlcollab.backend.model.UmlAttribute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UmlAttributeRepository extends JpaRepository<UmlAttribute, UUID> {
    List<UmlAttribute> findByUmlClassIdOrderByOrderIndexAsc(UUID umlClassId);

    @Modifying
    @Query("DELETE FROM UmlAttribute a WHERE a.umlClass.diagram.id = :diagramId")
    void deleteByDiagramId(@Param("diagramId") UUID diagramId);
}
