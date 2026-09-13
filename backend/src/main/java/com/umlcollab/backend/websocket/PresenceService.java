package com.umlcollab.backend.websocket;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro en memoria de quien esta viendo/editando cada diagrama ahora mismo.
 * No necesita persistirse: si el proceso se reinicia, cada cliente se
 * reconecta y se vuelve a registrar solo.
 */
@Service
public class PresenceService {

    // diagramId -> (sessionId -> info del usuario en esa sesion)
    private final Map<UUID, Map<String, PresenceInfo>> presenceByDiagram = new ConcurrentHashMap<>();
    // sessionId -> diagramId, para poder limpiar rapido al desconectarse
    private final Map<String, UUID> diagramBySession = new ConcurrentHashMap<>();

    public Collection<PresenceInfo> join(UUID diagramId, String sessionId, UUID userId, String username, String displayName, String colorHex) {
        presenceByDiagram.computeIfAbsent(diagramId, d -> new ConcurrentHashMap<>())
                .put(sessionId, new PresenceInfo(userId, username, displayName, colorHex));
        diagramBySession.put(sessionId, diagramId);
        return snapshot(diagramId);
    }

    public Optional<UUID> leave(String sessionId) {
        UUID diagramId = diagramBySession.remove(sessionId);
        if (diagramId == null) {
            return Optional.empty();
        }
        Map<String, PresenceInfo> sessions = presenceByDiagram.get(diagramId);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                presenceByDiagram.remove(diagramId);
            }
        }
        return Optional.of(diagramId);
    }

    public Collection<PresenceInfo> snapshot(UUID diagramId) {
        Map<String, PresenceInfo> sessions = presenceByDiagram.get(diagramId);
        if (sessions == null) {
            return List.of();
        }
        // de-duplicar por usuario (un mismo usuario puede tener 2 pestanas abiertas)
        Map<UUID, PresenceInfo> byUser = new LinkedHashMap<>();
        sessions.values().forEach(p -> byUser.put(p.getUserId(), p));
        return byUser.values();
    }

    @Getter
    @AllArgsConstructor
    public static class PresenceInfo {
        private UUID userId;
        private String username;
        private String displayName;
        private String colorHex;
    }
}
