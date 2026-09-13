package com.umlcollab.backend.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/** Envoltorio generico de cualquier cambio que se difunde por WebSocket a todos los colaboradores de un diagrama. */
@Getter
@AllArgsConstructor
@Builder
public class DiagramEvent {
    private DiagramEventType type;
    private UUID actorUserId;
    private String actorDisplayName;
    private Object payload;
    @Builder.Default
    private Instant timestamp = Instant.now();

    public enum DiagramEventType {
        CLASS_CREATED, CLASS_UPDATED, CLASS_DELETED,
        RELATIONSHIP_CREATED, RELATIONSHIP_UPDATED, RELATIONSHIP_DELETED,
        LOCK_ACQUIRED, LOCK_RELEASED,
        AI_OPERATIONS_APPLIED,
        DIAGRAM_REPLACED // usado cuando una foto o una importacion XMI reemplaza/rellena el diagrama entero
    }
}
