package com.umlcollab.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Relacion UML entre dos clases del mismo diagrama. La multiplicidad se
 * guarda como texto UML estandar ("1", "0..1", "*", "1..*", "0..*") tal
 * como se veria en Enterprise Architect.
 */
@Entity
@Table(name = "uml_relationship")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UmlRelationship {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diagram_id", nullable = false)
    private Diagram diagram;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_class_id", nullable = false)
    private UmlClass sourceClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_class_id", nullable = false)
    private UmlClass targetClass;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RelationshipType type;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String sourceMultiplicity = "1";

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String targetMultiplicity = "1";

    private String sourceRoleName;

    private String targetRoleName;

    private String label;
}
