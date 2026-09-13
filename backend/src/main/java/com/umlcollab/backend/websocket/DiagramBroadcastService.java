package com.umlcollab.backend.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DiagramBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;

    public DiagramBroadcastService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcast(UUID diagramId, DiagramEvent event) {
        messagingTemplate.convertAndSend("/topic/diagrams/" + diagramId + "/events", event);
    }

    public void broadcastPresence(UUID diagramId, Object presenceSnapshot) {
        messagingTemplate.convertAndSend("/topic/diagrams/" + diagramId + "/presence", presenceSnapshot);
    }
}
