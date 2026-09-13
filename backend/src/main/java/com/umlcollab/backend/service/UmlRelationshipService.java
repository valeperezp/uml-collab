package com.umlcollab.backend.service;

import com.umlcollab.backend.dto.RelationshipDto;
import com.umlcollab.backend.dto.requests.RelationshipRequest;
import com.umlcollab.backend.exception.NotFoundException;
import com.umlcollab.backend.model.Diagram;
import com.umlcollab.backend.model.EditLock;
import com.umlcollab.backend.model.UmlClass;
import com.umlcollab.backend.model.UmlRelationship;
import com.umlcollab.backend.repository.UmlRelationshipRepository;
import com.umlcollab.backend.websocket.DiagramBroadcastService;
import com.umlcollab.backend.websocket.DiagramEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UmlRelationshipService {

    private final UmlRelationshipRepository relationshipRepository;
    private final UmlClassService classService;
    private final DiagramService diagramService;
    private final LockService lockService;
    private final DiagramBroadcastService broadcastService;
    private final DiagramMapper mapper;

    public UmlRelationshipService(UmlRelationshipRepository relationshipRepository, UmlClassService classService,
                                   DiagramService diagramService, LockService lockService,
                                   DiagramBroadcastService broadcastService, DiagramMapper mapper) {
        this.relationshipRepository = relationshipRepository;
        this.classService = classService;
        this.diagramService = diagramService;
        this.lockService = lockService;
        this.broadcastService = broadcastService;
        this.mapper = mapper;
    }

    @Transactional
    public RelationshipDto create(UUID diagramId, RelationshipRequest request, UUID userId, String displayName) {
        Diagram diagram = diagramService.getEntity(diagramId);
        UmlClass source = classService.getEntity(request.getSourceClassId());
        UmlClass target = classService.getEntity(request.getTargetClassId());

        // Exclusion mutua: para trazar la relacion hay que tener (momentaneamente) la clase origen.
        lockService.acquire(diagramId, EditLock.LockedElementType.CLASS, source.getId(), userId, displayName);

        UmlRelationship relationship = UmlRelationship.builder()
                .diagram(diagram)
                .sourceClass(source)
                .targetClass(target)
                .type(request.getType())
                .sourceMultiplicity(normalize(request.getSourceMultiplicity()))
                .targetMultiplicity(normalize(request.getTargetMultiplicity()))
                .sourceRoleName(request.getSourceRoleName())
                .targetRoleName(request.getTargetRoleName())
                .label(request.getLabel())
                .build();
        relationship = relationshipRepository.save(relationship);

        RelationshipDto dto = mapper.toDto(relationship);
        broadcast(diagramId, DiagramEvent.DiagramEventType.RELATIONSHIP_CREATED, dto, userId, displayName);
        return dto;
    }

    @Transactional
    public RelationshipDto update(UUID diagramId, UUID relationshipId, RelationshipRequest request, UUID userId, String displayName) {
        UmlRelationship relationship = getEntity(relationshipId);
        lockService.acquire(diagramId, EditLock.LockedElementType.RELATIONSHIP, relationshipId, userId, displayName);

        if (request.getType() != null) relationship.setType(request.getType());
        if (request.getSourceMultiplicity() != null) relationship.setSourceMultiplicity(normalize(request.getSourceMultiplicity()));
        if (request.getTargetMultiplicity() != null) relationship.setTargetMultiplicity(normalize(request.getTargetMultiplicity()));
        relationship.setSourceRoleName(request.getSourceRoleName());
        relationship.setTargetRoleName(request.getTargetRoleName());
        relationship.setLabel(request.getLabel());

        relationship = relationshipRepository.save(relationship);
        RelationshipDto dto = mapper.toDto(relationship);
        broadcast(diagramId, DiagramEvent.DiagramEventType.RELATIONSHIP_UPDATED, dto, userId, displayName);
        return dto;
    }

    @Transactional
    public void delete(UUID diagramId, UUID relationshipId, UUID userId, String displayName) {
        UmlRelationship relationship = getEntity(relationshipId);
        lockService.acquire(diagramId, EditLock.LockedElementType.RELATIONSHIP, relationshipId, userId, displayName);
        relationshipRepository.delete(relationship);
        broadcast(diagramId, DiagramEvent.DiagramEventType.RELATIONSHIP_DELETED, relationshipId, userId, displayName);
    }

    @Transactional(readOnly = true)
    public UmlRelationship getEntity(UUID relationshipId) {
        return relationshipRepository.findById(relationshipId)
                .orElseThrow(() -> new NotFoundException("Relacion no encontrada: " + relationshipId));
    }

    private String normalize(String multiplicity) {
        if (multiplicity == null || multiplicity.isBlank()) return "1";
        String m = multiplicity.trim();
        return m.equals("N") || m.equals("n") ? "*" : m;
    }

    private void broadcast(UUID diagramId, DiagramEvent.DiagramEventType type, Object payload, UUID userId, String displayName) {
        broadcastService.broadcast(diagramId, DiagramEvent.builder()
                .type(type)
                .actorUserId(userId)
                .actorDisplayName(displayName)
                .payload(payload)
                .build());
    }
}
