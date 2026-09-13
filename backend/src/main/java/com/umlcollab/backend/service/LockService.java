package com.umlcollab.backend.service;

import com.umlcollab.backend.dto.LockDto;
import com.umlcollab.backend.exception.LockConflictException;
import com.umlcollab.backend.model.EditLock;
import com.umlcollab.backend.repository.EditLockRepository;
import com.umlcollab.backend.websocket.DiagramBroadcastService;
import com.umlcollab.backend.websocket.DiagramEvent;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Exclusion mutua sobre elementos del diagrama: mientras un usuario tiene el
 * lock de una clase o relacion, cualquier otro que intente editarla recibe
 * un 409. El lock se libera explicitamente o expira solo si el cliente deja
 * de mandar heartbeat (se cerro la pestana, perdio la conexion, etc), para
 * que nadie se quede bloqueando el diagrama para siempre.
 */
@Service
public class LockService {

    private final EditLockRepository lockRepository;
    private final DiagramBroadcastService broadcastService;
    private final long ttlSeconds;

    public LockService(EditLockRepository lockRepository,
                        DiagramBroadcastService broadcastService,
                        @Value("${umlcollab.lock.ttl-seconds}") long ttlSeconds) {
        this.lockRepository = lockRepository;
        this.broadcastService = broadcastService;
        this.ttlSeconds = ttlSeconds;
    }

    @Transactional
    public LockDto acquire(UUID diagramId, EditLock.LockedElementType type, UUID elementId, UUID userId, String userDisplayName) {
        var existing = lockRepository.findByElementTypeAndElementId(type, elementId);
        Instant now = Instant.now();

        if (existing.isPresent()) {
            EditLock lock = existing.get();
            boolean expired = lock.getLastHeartbeatAt().isBefore(now.minusSeconds(ttlSeconds));
            if (!expired && !lock.getUserId().equals(userId)) {
                throw new LockConflictException(lock.getUserDisplayName());
            }
            if (!expired && lock.getUserId().equals(userId)) {
                lock.setLastHeartbeatAt(now);
                lockRepository.save(lock);
                return toDto(lock, true);
            }
            // expirado: el que pide ahora se lo queda
            lock.setUserId(userId);
            lock.setUserDisplayName(userDisplayName);
            lock.setAcquiredAt(now);
            lock.setLastHeartbeatAt(now);
            lockRepository.save(lock);
            broadcastAcquired(diagramId, type, elementId, userId, userDisplayName);
            return toDto(lock, true);
        }

        EditLock lock = EditLock.builder()
                .diagramId(diagramId)
                .elementType(type)
                .elementId(elementId)
                .userId(userId)
                .userDisplayName(userDisplayName)
                .acquiredAt(now)
                .lastHeartbeatAt(now)
                .build();
        lockRepository.save(lock);
        broadcastAcquired(diagramId, type, elementId, userId, userDisplayName);
        return toDto(lock, true);
    }

    @Transactional
    public void heartbeat(EditLock.LockedElementType type, UUID elementId, UUID userId) {
        lockRepository.findByElementTypeAndElementId(type, elementId).ifPresent(lock -> {
            if (lock.getUserId().equals(userId)) {
                lock.setLastHeartbeatAt(Instant.now());
                lockRepository.save(lock);
            }
        });
    }

    @Transactional
    public void release(UUID diagramId, EditLock.LockedElementType type, UUID elementId, UUID userId) {
        lockRepository.findByElementTypeAndElementId(type, elementId).ifPresent(lock -> {
            if (lock.getUserId().equals(userId)) {
                lockRepository.delete(lock);
                broadcastService.broadcast(diagramId, DiagramEvent.builder()
                        .type(DiagramEvent.DiagramEventType.LOCK_RELEASED)
                        .actorUserId(userId)
                        .payload(LockDto.builder().elementType(type).elementId(elementId).userId(userId).granted(false).build())
                        .build());
            }
        });
    }

    /** Lanza LockConflictException si el elemento esta tomado por otro usuario (no valida/toma el lock, solo verifica). */
    public void assertNotLockedByOther(EditLock.LockedElementType type, UUID elementId, UUID userId) {
        lockRepository.findByElementTypeAndElementId(type, elementId).ifPresent(lock -> {
            boolean expired = lock.getLastHeartbeatAt().isBefore(Instant.now().minusSeconds(ttlSeconds));
            if (!expired && !lock.getUserId().equals(userId)) {
                throw new LockConflictException(lock.getUserDisplayName());
            }
        });
    }

    public List<LockDto> activeLocks(UUID diagramId) {
        return lockRepository.findByDiagramId(diagramId).stream()
                .filter(l -> l.getLastHeartbeatAt().isAfter(Instant.now().minusSeconds(ttlSeconds)))
                .map(l -> toDto(l, true))
                .toList();
    }

    @Transactional
    public void releaseAllForUser(UUID userId) {
        lockRepository.deleteByUserId(userId);
    }

    /** Corre periodicamente para liberar locks huerfanos (clientes que se desconectaron sin avisar). */
    @Scheduled(fixedDelayString = "${umlcollab.lock.ttl-seconds}000")
    @Transactional
    public void expireStaleLocks() {
        Instant threshold = Instant.now().minusSeconds(ttlSeconds);
        List<EditLock> stale = lockRepository.findByLastHeartbeatAtBefore(threshold);
        for (EditLock lock : stale) {
            lockRepository.delete(lock);
            broadcastService.broadcast(lock.getDiagramId(), DiagramEvent.builder()
                    .type(DiagramEvent.DiagramEventType.LOCK_RELEASED)
                    .actorUserId(lock.getUserId())
                    .payload(LockDto.builder().elementType(lock.getElementType()).elementId(lock.getElementId()).userId(lock.getUserId()).granted(false).build())
                    .build());
        }
    }

    private void broadcastAcquired(UUID diagramId, EditLock.LockedElementType type, UUID elementId, UUID userId, String displayName) {
        broadcastService.broadcast(diagramId, DiagramEvent.builder()
                .type(DiagramEvent.DiagramEventType.LOCK_ACQUIRED)
                .actorUserId(userId)
                .actorDisplayName(displayName)
                .payload(LockDto.builder().elementType(type).elementId(elementId).userId(userId).userDisplayName(displayName).granted(true).build())
                .build());
    }

    private LockDto toDto(EditLock lock, boolean granted) {
        return LockDto.builder()
                .elementType(lock.getElementType())
                .elementId(lock.getElementId())
                .userId(lock.getUserId())
                .userDisplayName(lock.getUserDisplayName())
                .acquiredAt(lock.getAcquiredAt())
                .granted(granted)
                .build();
    }
}
