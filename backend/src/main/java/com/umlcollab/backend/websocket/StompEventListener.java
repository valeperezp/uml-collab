package com.umlcollab.backend.websocket;

import com.umlcollab.backend.repository.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cuando un cliente se suscribe a /topic/diagrams/{id}/presence lo registramos
 * como "presente" en ese diagrama y avisamos al resto; cuando se desconecta,
 * lo sacamos y volvemos a avisar. Asi cada colaborador ve en vivo quien mas
 * esta trabajando en el mismo diagrama (y con que color, para los bloqueos).
 */
@Component
public class StompEventListener {

    private static final Pattern PRESENCE_DESTINATION = Pattern.compile("^/topic/diagrams/([0-9a-fA-F-]{36})/presence$");

    private final PresenceService presenceService;
    private final DiagramBroadcastService broadcastService;
    private final UserRepository userRepository;

    public StompEventListener(PresenceService presenceService, DiagramBroadcastService broadcastService, UserRepository userRepository) {
        this.presenceService = presenceService;
        this.broadcastService = broadcastService;
        this.userRepository = userRepository;
    }

    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        if (destination == null) return;
        Matcher matcher = PRESENCE_DESTINATION.matcher(destination);
        if (!matcher.matches()) return;

        UUID diagramId = UUID.fromString(matcher.group(1));
        WebSocketPrincipal principal = (WebSocketPrincipal) accessor.getUser();
        if (principal == null) return;

        userRepository.findById(principal.getUserId()).ifPresent(user -> {
            var snapshot = presenceService.join(diagramId, accessor.getSessionId(), user.getId(), user.getUsername(), user.getDisplayName(), user.getColorHex());
            broadcastService.broadcastPresence(diagramId, snapshot);
        });
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        presenceService.leave(accessor.getSessionId()).ifPresent(diagramId ->
                broadcastService.broadcastPresence(diagramId, presenceService.snapshot(diagramId)));
    }
}
