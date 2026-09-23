package com.umlcollab.backend.service;

import com.umlcollab.backend.dto.DiagramDetailDto;
import com.umlcollab.backend.dto.DiagramSummaryDto;
import com.umlcollab.backend.dto.requests.CreateDiagramRequest;
import com.umlcollab.backend.exception.BadRequestException;
import com.umlcollab.backend.exception.NotFoundException;
import com.umlcollab.backend.model.Diagram;
import com.umlcollab.backend.model.DiagramCollaborator;
import com.umlcollab.backend.model.UmlClass;
import com.umlcollab.backend.model.UmlRelationship;
import com.umlcollab.backend.model.User;
import com.umlcollab.backend.repository.DiagramCollaboratorRepository;
import com.umlcollab.backend.repository.DiagramRepository;
import com.umlcollab.backend.repository.UmlAttributeRepository;
import com.umlcollab.backend.repository.UmlClassRepository;
import com.umlcollab.backend.repository.UmlRelationshipRepository;
import com.umlcollab.backend.repository.UserRepository;
import com.umlcollab.backend.websocket.DiagramBroadcastService;
import com.umlcollab.backend.websocket.DiagramEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DiagramService {

    private final DiagramRepository diagramRepository;
    private final DiagramCollaboratorRepository collaboratorRepository;
    private final UserRepository userRepository;
    private final UmlClassRepository classRepository;
    private final UmlAttributeRepository attributeRepository;
    private final UmlRelationshipRepository relationshipRepository;
    private final DiagramMapper mapper;
    private final LockService lockService;
    private final DiagramBroadcastService broadcastService;

    public DiagramService(DiagramRepository diagramRepository,
                          DiagramCollaboratorRepository collaboratorRepository,
                          UserRepository userRepository,
                          UmlClassRepository classRepository,
                          UmlAttributeRepository attributeRepository,
                          UmlRelationshipRepository relationshipRepository,
                          DiagramMapper mapper,
                          LockService lockService,
                          DiagramBroadcastService broadcastService) {
        this.diagramRepository = diagramRepository;
        this.collaboratorRepository = collaboratorRepository;
        this.userRepository = userRepository;
        this.classRepository = classRepository;
        this.attributeRepository = attributeRepository;
        this.relationshipRepository = relationshipRepository;
        this.mapper = mapper;
        this.lockService = lockService;
        this.broadcastService = broadcastService;
    }

    @Transactional
    public DiagramSummaryDto create(UUID ownerId, CreateDiagramRequest request) {
        Diagram diagram = Diagram.builder()
                .name(request.getName())
                .description(request.getDescription())
                .ownerId(ownerId)
                .build();
        diagram = diagramRepository.save(diagram);
        String ownerName = userRepository.findById(ownerId)
                .map(u -> u.getDisplayName() != null ? u.getDisplayName() : u.getUsername())
                .orElse("Tú");
        return mapper.toSummaryDto(diagram, ownerId, ownerName);
    }

    @Transactional(readOnly = true)
    public List<DiagramSummaryDto> listOwnedBy(UUID ownerId) {
        return listForUser(ownerId);
    }

    @Transactional(readOnly = true)
    public List<DiagramSummaryDto> listForUser(UUID userId) {
        List<Diagram> diagrams = diagramRepository.findAllAccessibleByUser(userId);
        
        Set<UUID> ownerIds = diagrams.stream().map(Diagram::getOwnerId).collect(Collectors.toSet());
        Map<UUID, String> ownerNames = new HashMap<>();
        if (!ownerIds.isEmpty()) {
            userRepository.findAllById(ownerIds).forEach(u ->
                ownerNames.put(u.getId(), u.getDisplayName() != null ? u.getDisplayName() : u.getUsername())
            );
        }

        return diagrams.stream()
                .map(d -> mapper.toSummaryDto(d, userId, ownerNames.get(d.getOwnerId())))
                .toList();
    }

    @Transactional
    public DiagramDetailDto getDetail(UUID diagramId, UUID currentUserId) {
        Diagram diagram = getEntity(diagramId);
        if (currentUserId != null) {
            registerCollaboratorIfPresent(diagram, currentUserId);
        }
        List<UmlClass> classes = classRepository.findByDiagramIdOrderByCreatedAtAsc(diagramId);
        List<UmlRelationship> relationships = relationshipRepository.findByDiagramId(diagramId);
        return DiagramDetailDto.builder()
                .id(diagram.getId())
                .name(diagram.getName())
                .description(diagram.getDescription())
                .ownerId(diagram.getOwnerId())
                .joinCode(diagram.getJoinCode())
                .updatedAt(diagram.getUpdatedAt())
                .classes(classes.stream().map(mapper::toDto).toList())
                .relationships(relationships.stream().map(mapper::toDto).toList())
                .build();
    }

    @Transactional(readOnly = true)
    public DiagramDetailDto getDetail(UUID diagramId) {
        return getDetail(diagramId, null);
    }

    @Transactional(readOnly = true)
    public Diagram getEntity(UUID diagramId) {
        return diagramRepository.findById(diagramId)
                .orElseThrow(() -> new NotFoundException("Diagrama no encontrado: " + diagramId));
    }

    @Transactional
    public DiagramSummaryDto joinByCode(String joinCode, UUID currentUserId) {
        Diagram diagram = diagramRepository.findByJoinCode(joinCode.toUpperCase())
                .orElseThrow(() -> new BadRequestException("Codigo de invitacion invalido"));
        
        if (currentUserId != null) {
            registerCollaboratorIfPresent(diagram, currentUserId);
        }

        String ownerName = userRepository.findById(diagram.getOwnerId())
                .map(u -> u.getDisplayName() != null ? u.getDisplayName() : u.getUsername())
                .orElse("Compañero");

        return mapper.toSummaryDto(diagram, currentUserId, ownerName);
    }

    @Transactional
    public DiagramSummaryDto joinByCode(String joinCode) {
        return joinByCode(joinCode, null);
    }

    @Transactional
    public void registerCollaboratorIfPresent(Diagram diagram, UUID userId) {
        if (userId == null || diagram.getOwnerId().equals(userId)) {
            return;
        }
        if (!collaboratorRepository.existsByDiagramIdAndUserId(diagram.getId(), userId)) {
            DiagramCollaborator collaborator = DiagramCollaborator.builder()
                    .diagram(diagram)
                    .userId(userId)
                    .build();
            collaboratorRepository.save(collaborator);
        }
    }

    @Transactional
    public void leaveDiagram(UUID diagramId, UUID userId) {
        collaboratorRepository.deleteByDiagramIdAndUserId(diagramId, userId);
    }

    @Transactional
    public void touch(UUID diagramId) {
        Diagram diagram = getEntity(diagramId);
        diagramRepository.save(diagram); // dispara @PreUpdate -> updatedAt
    }

    @Transactional
    public void delete(UUID diagramId, UUID requesterId) {
        Diagram diagram = getEntity(diagramId);
        if (!diagram.getOwnerId().equals(requesterId)) {
            throw new BadRequestException("Solo el dueno del diagrama puede eliminarlo");
        }
        lockService.releaseAllForDiagram(diagramId);
        collaboratorRepository.deleteByDiagramId(diagramId);
        relationshipRepository.deleteByDiagramId(diagramId);
        attributeRepository.deleteByDiagramId(diagramId);
        classRepository.deleteByDiagramId(diagramId);
        diagramRepository.delete(diagram);
    }

    @Transactional
    public DiagramDetailDto clear(UUID diagramId, UUID userId, String displayName) {
        Diagram diagram = getEntity(diagramId);
        lockService.releaseAllForDiagram(diagramId);
        relationshipRepository.deleteByDiagramId(diagramId);
        attributeRepository.deleteByDiagramId(diagramId);
        classRepository.deleteByDiagramId(diagramId);
        diagram.getRelationships().clear();
        diagram.getClasses().clear();
        diagram = diagramRepository.save(diagram);

        DiagramDetailDto dto = mapper.toDetailDto(diagram);
        broadcastService.broadcast(diagramId, DiagramEvent.builder()
                .type(DiagramEvent.DiagramEventType.DIAGRAM_REPLACED)
                .actorUserId(userId)
                .actorDisplayName(displayName)
                .payload(dto)
                .build());
        return dto;
    }
}
