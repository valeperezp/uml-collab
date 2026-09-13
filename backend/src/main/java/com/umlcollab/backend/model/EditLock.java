package com.umlcollab.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Bloqueo de exclusion mutua sobre un elemento del diagrama (clase o
 * relacion). Mientras un usuario lo tiene tomado, el resto puede ver el
 * elemento pero no editarlo. Se libera explicitamente o expira solo si el
 * cliente deja de mandar heartbeat (desconexion, cierre de pestana, etc).
 */
@Entity
@Table(name = "edit_lock", uniqueConstraints = @UniqueConstraint(columnNames = {"element_type", "element_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EditLock {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID diagramId;

    @Enumerated(EnumType.STRING)
    @Column(name = "element_type", nullable = false, length = 20)
    private LockedElementType elementType;

    @Column(name = "element_id", nullable = false)
    private UUID elementId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String userDisplayName;

    @Column(nullable = false)
    private Instant acquiredAt;

    @Column(nullable = false)
    private Instant lastHeartbeatAt;

    public enum LockedElementType {
        CLASS,
        RELATIONSHIP
    }
}
