package com.umlcollab.backend.model;

/**
 * Tipos de dato soportados en los atributos del diagrama. Cada uno sabe
 * mapearse al tipo Java que usara el generador de codigo.
 */
public enum DataType {
    STRING("String"),
    TEXT("String"),
    INTEGER("Integer"),
    LONG("Long"),
    DOUBLE("Double"),
    DECIMAL("java.math.BigDecimal"),
    BOOLEAN("Boolean"),
    DATE("java.time.LocalDate"),
    DATETIME("java.time.LocalDateTime"),
    UUID("java.util.UUID");

    private final String javaType;

    DataType(String javaType) {
        this.javaType = javaType;
    }

    public String javaType() {
        return javaType;
    }

    /** Nombre simple del tipo (sin el paquete), para usar en imports y en la firma del campo. */
    public String simpleJavaType() {
        int idx = javaType.lastIndexOf('.');
        return idx >= 0 ? javaType.substring(idx + 1) : javaType;
    }

    /** Paquete a importar si el tipo no es del paquete java.lang, o null si no hace falta import. */
    public String importIfNeeded() {
        if (javaType.startsWith("java.lang.") || !javaType.contains(".")) {
            return null;
        }
        return javaType;
    }
}
