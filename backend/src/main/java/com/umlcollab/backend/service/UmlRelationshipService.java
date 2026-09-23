package com.umlcollab.backend.service;

import com.umlcollab.backend.dto.ClassDto;
import com.umlcollab.backend.dto.RelationshipDto;
import com.umlcollab.backend.dto.requests.CreateClassRequest;
import com.umlcollab.backend.dto.requests.RelationshipRequest;
import com.umlcollab.backend.exception.NotFoundException;
import com.umlcollab.backend.model.Diagram;
import com.umlcollab.backend.model.EditLock;
import com.umlcollab.backend.model.RelationshipType;
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

        String srcMult = normalize(request.getSourceMultiplicity());
        String tgtMult = normalize(request.getTargetMultiplicity());

        // Si es una asociacion Muchos a Muchos (* a *) entre clases distintas, se descompone automaticamente creando una clase intermedia
        if (!source.getId().equals(target.getId()) && request.getType() == RelationshipType.ASSOCIATION && isMany(srcMult) && isMany(tgtMult)) {
            String interClassName = generateIntermediateClassName(diagramId, source.getName(), target.getName());
            double interX = Math.round((source.getX() + target.getX()) / 2.0);
            double interY = Math.round((source.getY() + target.getY()) / 2.0);

            // Si quedan muy cerca, se le da un leve desplazamiento vertical
            if (Math.abs(source.getX() - target.getX()) < 80 && Math.abs(source.getY() - target.getY()) < 80) {
                interY += 70;
            }

            CreateClassRequest createClassReq = new CreateClassRequest();
            createClassReq.setName(interClassName);
            createClassReq.setX(interX);
            createClassReq.setY(interY);
            createClassReq.setAbstract(false);

            ClassDto interClassDto = classService.create(diagramId, createClassReq, userId, displayName);
            UmlClass interClass = classService.getEntity(interClassDto.getId());

            // Relacion 1: source (1) -> intermediate (*)
            UmlRelationship rel1 = UmlRelationship.builder()
                    .diagram(diagram)
                    .sourceClass(source)
                    .targetClass(interClass)
                    .type(RelationshipType.ASSOCIATION)
                    .sourceMultiplicity(srcMult.startsWith("0") ? "0..1" : "1")
                    .targetMultiplicity("*")
                    .build();
            rel1 = relationshipRepository.save(rel1);
            RelationshipDto dto1 = mapper.toDto(rel1);
            broadcast(diagramId, DiagramEvent.DiagramEventType.RELATIONSHIP_CREATED, dto1, userId, displayName);

            // Relacion 2: target (1) -> intermediate (*)
            UmlRelationship rel2 = UmlRelationship.builder()
                    .diagram(diagram)
                    .sourceClass(target)
                    .targetClass(interClass)
                    .type(RelationshipType.ASSOCIATION)
                    .sourceMultiplicity(tgtMult.startsWith("0") ? "0..1" : "1")
                    .targetMultiplicity("*")
                    .build();
            rel2 = relationshipRepository.save(rel2);
            RelationshipDto dto2 = mapper.toDto(rel2);
            broadcast(diagramId, DiagramEvent.DiagramEventType.RELATIONSHIP_CREATED, dto2, userId, displayName);

            return dto1;
        }

        UmlRelationship relationship = UmlRelationship.builder()
                .diagram(diagram)
                .sourceClass(source)
                .targetClass(target)
                .type(request.getType())
                .sourceMultiplicity(srcMult)
                .targetMultiplicity(tgtMult)
                .sourceRoleName(request.getSourceRoleName())
                .targetRoleName(request.getTargetRoleName())
                .label(request.getLabel())
                .build();
        relationship = relationshipRepository.save(relationship);
        diagram.getRelationships().add(relationship);

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
        lockService.release(diagramId, EditLock.LockedElementType.RELATIONSHIP, relationshipId, userId);
        broadcast(diagramId, DiagramEvent.DiagramEventType.RELATIONSHIP_DELETED, relationshipId, userId, displayName);
    }

    @Transactional(readOnly = true)
    public UmlRelationship getEntity(UUID relationshipId) {
        return relationshipRepository.findById(relationshipId)
                .orElseThrow(() -> new NotFoundException("Relacion no encontrada: " + relationshipId));
    }

    private String normalize(String multiplicity) {
        if (multiplicity == null || multiplicity.isBlank()) return "";
        String m = multiplicity.trim();
        if (m.equalsIgnoreCase("none") || m.equalsIgnoreCase("ninguno") || m.equalsIgnoreCase("(ninguno)") || m.equals("-")) return "";
        return m.equals("N") || m.equals("n") ? "*" : m;
    }

    private boolean isMany(String multiplicity) {
        return multiplicity != null && multiplicity.contains("*");
    }

    private String generateIntermediateClassName(UUID diagramId, String sourceName, String targetName) {
        String base = sourceName + targetName;
        String candidate = base;
        int i = 2;
        while (classService.isNameTaken(diagramId, candidate)) {
            candidate = base + i++;
        }
        return candidate;
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
