package com.umlcollab.backend.controller;

import com.umlcollab.backend.dto.LockDto;
import com.umlcollab.backend.model.EditLock;
import com.umlcollab.backend.security.CurrentUser;
import com.umlcollab.backend.service.LockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/diagrams/{diagramId}/locks")
public class LockController {

    private final LockService lockService;

    public LockController(LockService lockService) {
        this.lockService = lockService;
    }

    @GetMapping
    public ResponseEntity<List<LockDto>> active(@PathVariable UUID diagramId) {
        return ResponseEntity.ok(lockService.activeLocks(diagramId));
    }

    @PostMapping("/{elementType}/{elementId}/acquire")
    public ResponseEntity<LockDto> acquire(@PathVariable UUID diagramId, @PathVariable EditLock.LockedElementType elementType,
                                            @PathVariable UUID elementId) {
        return ResponseEntity.ok(lockService.acquire(diagramId, elementType, elementId, CurrentUser.id(), CurrentUser.get().getUsername()));
    }

    @PostMapping("/{elementType}/{elementId}/heartbeat")
    public ResponseEntity<Void> heartbeat(@PathVariable UUID diagramId, @PathVariable EditLock.LockedElementType elementType,
                                           @PathVariable UUID elementId) {
        lockService.heartbeat(elementType, elementId, CurrentUser.id());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{elementType}/{elementId}/release")
    public ResponseEntity<Void> release(@PathVariable UUID diagramId, @PathVariable EditLock.LockedElementType elementType,
                                         @PathVariable UUID elementId) {
        lockService.release(diagramId, elementType, elementId, CurrentUser.id());
        return ResponseEntity.noContent().build();
    }
}
