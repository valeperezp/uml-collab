package com.umlcollab.backend.repository;

import com.umlcollab.backend.model.EditLock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EditLockRepository extends JpaRepository<EditLock, UUID> {
    Optional<EditLock> findByElementTypeAndElementId(EditLock.LockedElementType elementType, UUID elementId);
    List<EditLock> findByDiagramId(UUID diagramId);
    void deleteByElementTypeAndElementIdAndUserId(EditLock.LockedElementType elementType, UUID elementId, UUID userId);
    List<EditLock> findByLastHeartbeatAtBefore(Instant threshold);
    void deleteByUserId(UUID userId);
}
