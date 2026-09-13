package com.umlcollab.backend.dto;

import com.umlcollab.backend.model.EditLock;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class LockDto {
    private EditLock.LockedElementType elementType;
    private UUID elementId;
    private UUID userId;
    private String userDisplayName;
    private Instant acquiredAt;
    /** true si la operacion pedida (tomar/soltar) tuvo exito. */
    private boolean granted;
}
