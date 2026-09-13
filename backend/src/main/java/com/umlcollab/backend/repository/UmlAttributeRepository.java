package com.umlcollab.backend.repository;

import com.umlcollab.backend.model.UmlAttribute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UmlAttributeRepository extends JpaRepository<UmlAttribute, UUID> {
    List<UmlAttribute> findByUmlClassIdOrderByOrderIndexAsc(UUID umlClassId);
}
