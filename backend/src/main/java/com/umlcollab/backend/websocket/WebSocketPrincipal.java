package com.umlcollab.backend.websocket;

import lombok.Getter;

import java.security.Principal;
import java.util.UUID;

@Getter
public class WebSocketPrincipal implements Principal {
    private final UUID userId;
    private final String username;
    private final String name; // requerido por Principal.getName(), usamos un id unico de sesion+usuario

    public WebSocketPrincipal(UUID userId, String username) {
        this.userId = userId;
        this.username = username;
        this.name = userId.toString();
    }
}
