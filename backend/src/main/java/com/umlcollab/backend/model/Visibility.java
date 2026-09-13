package com.umlcollab.backend.model;

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
}
