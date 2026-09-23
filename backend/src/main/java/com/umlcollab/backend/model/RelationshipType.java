package com.umlcollab.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/** Tipos de relacion UML soportados entre dos clases del diagrama. */
public enum RelationshipType {
    ASSOCIATION,
    AGGREGATION,
    COMPOSITION,
    GENERALIZATION,
    REALIZATION,
    DEPENDENCY;

    @JsonCreator
    public static RelationshipType from(Object raw) {
        if (raw == null) return ASSOCIATION;
        String s = raw.toString().trim().toUpperCase();
        if (s.isEmpty()) return ASSOCIATION;

        return switch (s) {
            case "AGGREGATION", "AGREGACION", "AGREGACIÓN" -> AGGREGATION;
            case "COMPOSITION", "COMPOSICION", "COMPOSICIÓN" -> COMPOSITION;
            case "GENERALIZATION", "GENERALIZACION", "GENERALIZACIÓN", "INHERITANCE", "HERENCIA", "EXTENDS" -> GENERALIZATION;
            case "REALIZATION", "REALIZACION", "REALIZACIÓN", "IMPLEMENTATION", "IMPLEMENTS" -> REALIZATION;
            case "DEPENDENCY", "DEPENDENCIA", "DEPENDS" -> DEPENDENCY;
            case "ASSOCIATION", "ASOCIACION", "ASOCIACIÓN" -> ASSOCIATION;
            default -> {
                for (RelationshipType t : values()) {
                    if (t.name().equalsIgnoreCase(s)) yield t;
                }
                yield ASSOCIATION;
            }
        };
    }
}
