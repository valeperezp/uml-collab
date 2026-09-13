package com.umlcollab.backend.config;

import com.umlcollab.backend.websocket.JwtHandshakeInterceptor;
import com.umlcollab.backend.websocket.UserHandshakeHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Canal de colaboracion en tiempo real: STOMP sobre WebSocket (con fallback SockJS).
 * Los clientes se suscriben a /topic/diagrams/{diagramId}/events para recibir cada
 * cambio (clase creada/movida/borrada, atributo agregado, relacion creada, bloqueo
 * tomado/liberado) apenas ocurre, sin necesidad de refrescar ni hacer polling.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final UserHandshakeHandler userHandshakeHandler;

    public WebSocketConfig(JwtHandshakeInterceptor jwtHandshakeInterceptor, UserHandshakeHandler userHandshakeHandler) {
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
        this.userHandshakeHandler = userHandshakeHandler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(jwtHandshakeInterceptor)
                .setHandshakeHandler(userHandshakeHandler)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
