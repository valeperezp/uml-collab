package com.umlcollab.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "uml_attribute")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UmlAttribute {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uml_class_id", nullable = false)
    private UmlClass umlClass;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DataType dataType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Visibility visibility = Visibility.PRIVATE;

    @Column(nullable = false)
    private boolean isPrimaryKey;

    @Column(nullable = false)
    @Builder.Default
    private boolean nullable = true;

    @Column(name = "is_unique", nullable = false)
    @Builder.Default
    private boolean unique = false;

    @Column(nullable = false)
    @Builder.Default
    private int orderIndex = 0;
}
