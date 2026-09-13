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

@Entity
@Table(name = "uml_class")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UmlClass {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diagram_id", nullable = false)
    private Diagram diagram;

    @Column(nullable = false)
    private String name;

    private String stereotype;

    @Column(nullable = false)
    private boolean isAbstract;

    /** Posicion en el lienzo colaborativo, para que todos los clientes vean el mismo layout. */
    @Column(nullable = false)
    private double x;

    @Column(nullable = false)
    private double y;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Builder.Default
    @OneToMany(mappedBy = "umlClass", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<UmlAttribute> attributes = new ArrayList<>();

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
