package com.umlcollab.backend.controller;

import com.umlcollab.backend.dto.DiagramDetailDto;
import com.umlcollab.backend.dto.DiagramSummaryDto;
import com.umlcollab.backend.dto.requests.CreateDiagramRequest;
import com.umlcollab.backend.security.CurrentUser;
import com.umlcollab.backend.service.DiagramService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/diagrams")
public class DiagramController {

    private final DiagramService diagramService;

    public DiagramController(DiagramService diagramService) {
        this.diagramService = diagramService;
    }

    @PostMapping
    public ResponseEntity<DiagramSummaryDto> create(@Valid @RequestBody CreateDiagramRequest request) {
        return ResponseEntity.ok(diagramService.create(CurrentUser.id(), request));
    }

    @GetMapping
    public ResponseEntity<List<DiagramSummaryDto>> listMine() {
        return ResponseEntity.ok(diagramService.listForUser(CurrentUser.id()));
    }

    @GetMapping("/{diagramId}")
    public ResponseEntity<DiagramDetailDto> getDetail(@PathVariable UUID diagramId) {
        return ResponseEntity.ok(diagramService.getDetail(diagramId, CurrentUser.id()));
    }

    @GetMapping("/join/{joinCode}")
    public ResponseEntity<DiagramSummaryDto> joinByCode(@PathVariable String joinCode) {
        return ResponseEntity.ok(diagramService.joinByCode(joinCode, CurrentUser.id()));
    }

    @PostMapping("/{diagramId}/clear")
    public ResponseEntity<DiagramDetailDto> clear(@PathVariable UUID diagramId) {
        return ResponseEntity.ok(diagramService.clear(diagramId, CurrentUser.id(), CurrentUser.get().getUsername()));
    }

    @DeleteMapping("/{diagramId}/leave")
    public ResponseEntity<Void> leave(@PathVariable UUID diagramId) {
        diagramService.leaveDiagram(diagramId, CurrentUser.id());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{diagramId}")
    public ResponseEntity<Void> delete(@PathVariable UUID diagramId) {
        diagramService.delete(diagramId, CurrentUser.id());
        return ResponseEntity.noContent().build();
    }
}
