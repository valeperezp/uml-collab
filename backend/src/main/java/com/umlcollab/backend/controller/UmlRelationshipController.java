package com.umlcollab.backend.controller;

import com.umlcollab.backend.dto.RelationshipDto;
import com.umlcollab.backend.dto.requests.RelationshipRequest;
import com.umlcollab.backend.security.CurrentUser;
import com.umlcollab.backend.service.UmlRelationshipService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/diagrams/{diagramId}/relationships")
public class UmlRelationshipController {

    private final UmlRelationshipService relationshipService;

    public UmlRelationshipController(UmlRelationshipService relationshipService) {
        this.relationshipService = relationshipService;
    }

    @PostMapping
    public ResponseEntity<RelationshipDto> create(@PathVariable UUID diagramId, @Valid @RequestBody RelationshipRequest request) {
        return ResponseEntity.ok(relationshipService.create(diagramId, request, CurrentUser.id(), CurrentUser.get().getUsername()));
    }

    @PutMapping("/{relationshipId}")
    public ResponseEntity<RelationshipDto> update(@PathVariable UUID diagramId, @PathVariable UUID relationshipId,
                                                    @RequestBody RelationshipRequest request) {
        return ResponseEntity.ok(relationshipService.update(diagramId, relationshipId, request, CurrentUser.id(), CurrentUser.get().getUsername()));
    }

    @DeleteMapping("/{relationshipId}")
    public ResponseEntity<Void> delete(@PathVariable UUID diagramId, @PathVariable UUID relationshipId) {
        relationshipService.delete(diagramId, relationshipId, CurrentUser.id(), CurrentUser.get().getUsername());
        return ResponseEntity.noContent().build();
    }
}
