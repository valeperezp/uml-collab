package com.umlcollab.backend.ai;

/** Se lanza cuando no hay una API key de IA configurada o el proveedor esta deshabilitado. */
public class AiUnavailableException extends RuntimeException {
    public AiUnavailableException(String message) {
        super(message);
    }
}
