package com.umlcollab.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Un diagrama de clases (modelo conceptual) sobre el que colaboran varios
 * ingenieros a la vez. Cualquier usuario que conozca el {@link #joinCode}
 * puede unirse a editarlo (modelo simplificado de colaboracion, pensado
 * para equipos de trabajo internos, no para acceso publico).
 */
@Entity
@Table(name = "diagram")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Diagram {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private UUID ownerId;

    @Column(nullable = false, unique = true, length = 12)
    private String joinCode;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Builder.Default
    @OneToMany(mappedBy = "diagram", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UmlClass> classes = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "diagram", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UmlRelationship> relationships = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "diagram", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DiagramCollaborator> collaborators = new ArrayList<>();

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (joinCode == null) {
            joinCode = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
