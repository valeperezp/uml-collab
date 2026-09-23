package com.umlcollab.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/** Visibilidad UML de un atributo/operacion, usada tambien para generar el modificador Java. */
public enum Visibility {
    PUBLIC("+"),
    PRIVATE("-"),
    PROTECTED("#"),
    PACKAGE("~");

    private final String umlSymbol;

    Visibility(String umlSymbol) {
        this.umlSymbol = umlSymbol;
    }

    public String umlSymbol() {
        return umlSymbol;
    }

    @JsonCreator
    public static Visibility from(Object raw) {
        if (raw == null) return PRIVATE;
        String s = raw.toString().trim().toUpperCase();
        if (s.isEmpty()) return PRIVATE;

        return switch (s) {
            case "+", "PUBLIC", "PUB" -> PUBLIC;
            case "-", "PRIVATE", "PRIV" -> PRIVATE;
            case "#", "PROTECTED", "PROT" -> PROTECTED;
            case "~", "PACKAGE", "DEFAULT" -> PACKAGE;
            default -> {
                for (Visibility v : values()) {
                    if (v.name().equalsIgnoreCase(s)) yield v;
                }
                yield PRIVATE;
            }
        };
    }
}
