package com.umlcollab.backend.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@Component
public class UserHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(org.springframework.http.server.ServerHttpRequest request,
                                       WebSocketHandler wsHandler,
                                       Map<String, Object> attributes) {
        UUID userId = (UUID) attributes.get("userId");
        String username = (String) attributes.get("username");
        if (userId == null) {
            // No deberia pasar: el handshake interceptor ya rechazo la conexion sin token valido.
            userId = UUID.randomUUID();
            username = "anonimo";
        }
        return new WebSocketPrincipal(userId, username);
    }
}
