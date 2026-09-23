package com.umlcollab.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;

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

    @JsonCreator
    public static DataType from(Object raw) {
        if (raw == null) return STRING;
        String s = raw.toString().trim().toUpperCase();
        if (s.isEmpty()) return STRING;

        return switch (s) {
            case "TEXT", "CLOB", "MEMO" -> TEXT;
            case "INT", "INTEGER", "SMALLINT", "TINYINT", "NUMBER", "NUMERIC", "SERIAL" -> INTEGER;
            case "LONG", "BIGINT", "BIGINTEGER", "BIGSERIAL" -> LONG;
            case "DOUBLE", "FLOAT", "REAL" -> DOUBLE;
            case "DECIMAL", "MONEY", "CURRENCY", "BIGDECIMAL", "PRICE", "AMOUNT" -> DECIMAL;
            case "BOOLEAN", "BOOL", "BIT" -> BOOLEAN;
            case "DATE" -> DATE;
            case "DATETIME", "TIMESTAMP", "TIME", "LOCALDATETIME", "INSTANT" -> DATETIME;
            case "UUID", "GUID", "ID" -> UUID;
            case "STRING", "VARCHAR", "CHAR", "CHARACTER", "NVARCHAR", "TEXTO", "CADENA", "STR" -> STRING;
            default -> {
                for (DataType dt : values()) {
                    if (dt.name().equalsIgnoreCase(s)) yield dt;
                }
                yield STRING;
            }
        };
    }
}
