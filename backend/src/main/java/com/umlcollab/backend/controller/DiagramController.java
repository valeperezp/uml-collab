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
        return ResponseEntity.ok(diagramService.listOwnedBy(CurrentUser.id()));
    }

    @GetMapping("/{diagramId}")
    public ResponseEntity<DiagramDetailDto> getDetail(@PathVariable UUID diagramId) {
        return ResponseEntity.ok(diagramService.getDetail(diagramId));
    }

    @GetMapping("/join/{joinCode}")
    public ResponseEntity<DiagramSummaryDto> joinByCode(@PathVariable String joinCode) {
        return ResponseEntity.ok(diagramService.joinByCode(joinCode));
    }

    @DeleteMapping("/{diagramId}")
    public ResponseEntity<Void> delete(@PathVariable UUID diagramId) {
        diagramService.delete(diagramId, CurrentUser.id());
        return ResponseEntity.noContent().build();
    }
}
