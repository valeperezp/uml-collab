package com.umlcollab.backend.controller;

import com.umlcollab.backend.dto.AttributeDto;
import com.umlcollab.backend.dto.ClassDto;
import com.umlcollab.backend.dto.requests.AttributeRequest;
import com.umlcollab.backend.dto.requests.CreateClassRequest;
import com.umlcollab.backend.dto.requests.UpdateClassRequest;
import com.umlcollab.backend.security.CurrentUser;
import com.umlcollab.backend.service.UmlClassService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/diagrams/{diagramId}/classes")
public class UmlClassController {

    private final UmlClassService classService;

    public UmlClassController(UmlClassService classService) {
        this.classService = classService;
    }

    @PostMapping
    public ResponseEntity<ClassDto> create(@PathVariable UUID diagramId, @Valid @RequestBody CreateClassRequest request) {
        return ResponseEntity.ok(classService.create(diagramId, request, CurrentUser.id(), CurrentUser.get().getUsername()));
    }

    @PutMapping("/{classId}")
    public ResponseEntity<ClassDto> update(@PathVariable UUID diagramId, @PathVariable UUID classId,
                                            @RequestBody UpdateClassRequest request) {
        return ResponseEntity.ok(classService.update(diagramId, classId, request, CurrentUser.id(), CurrentUser.get().getUsername()));
    }

    @DeleteMapping("/{classId}")
    public ResponseEntity<Void> delete(@PathVariable UUID diagramId, @PathVariable UUID classId) {
        classService.delete(diagramId, classId, CurrentUser.id(), CurrentUser.get().getUsername());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{classId}/attributes")
    public ResponseEntity<AttributeDto> addAttribute(@PathVariable UUID diagramId, @PathVariable UUID classId,
                                                       @Valid @RequestBody AttributeRequest request) {
        return ResponseEntity.ok(classService.addAttribute(diagramId, classId, request, CurrentUser.id(), CurrentUser.get().getUsername()));
    }

    @PutMapping("/{classId}/attributes/{attributeId}")
    public ResponseEntity<AttributeDto> updateAttribute(@PathVariable UUID diagramId, @PathVariable UUID classId,
                                                          @PathVariable UUID attributeId, @Valid @RequestBody AttributeRequest request) {
        return ResponseEntity.ok(classService.updateAttribute(diagramId, classId, attributeId, request, CurrentUser.id(), CurrentUser.get().getUsername()));
    }

    @DeleteMapping("/{classId}/attributes/{attributeId}")
    public ResponseEntity<Void> removeAttribute(@PathVariable UUID diagramId, @PathVariable UUID classId, @PathVariable UUID attributeId) {
        classService.removeAttribute(diagramId, classId, attributeId, CurrentUser.id(), CurrentUser.get().getUsername());
        return ResponseEntity.noContent().build();
    }
}
