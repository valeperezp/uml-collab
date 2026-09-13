package com.umlcollab.backend.service;

import com.umlcollab.backend.dto.AttributeDto;
import com.umlcollab.backend.dto.ClassDto;
import com.umlcollab.backend.dto.requests.AttributeRequest;
import com.umlcollab.backend.dto.requests.CreateClassRequest;
import com.umlcollab.backend.dto.requests.UpdateClassRequest;
import com.umlcollab.backend.exception.BadRequestException;
import com.umlcollab.backend.exception.NotFoundException;
import com.umlcollab.backend.model.*;
import com.umlcollab.backend.repository.UmlAttributeRepository;
import com.umlcollab.backend.repository.UmlClassRepository;
import com.umlcollab.backend.websocket.DiagramBroadcastService;
import com.umlcollab.backend.websocket.DiagramEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UmlClassService {

    private final UmlClassRepository classRepository;
    private final UmlAttributeRepository attributeRepository;
    private final DiagramService diagramService;
    private final LockService lockService;
    private final DiagramBroadcastService broadcastService;
    private final DiagramMapper mapper;

    public UmlClassService(UmlClassRepository classRepository, UmlAttributeRepository attributeRepository,
                            DiagramService diagramService, LockService lockService,
                            DiagramBroadcastService broadcastService, DiagramMapper mapper) {
        this.classRepository = classRepository;
        this.attributeRepository = attributeRepository;
        this.diagramService = diagramService;
        this.lockService = lockService;
        this.broadcastService = broadcastService;
        this.mapper = mapper;
    }

    @Transactional
    public ClassDto create(UUID diagramId, CreateClassRequest request, UUID userId, String displayName) {
        Diagram diagram = diagramService.getEntity(diagramId);
        assertUniqueName(diagramId, request.getName(), null);
        UmlClass umlClass = UmlClass.builder()
                .diagram(diagram)
                .name(request.getName())
                .stereotype(request.getStereotype())
                .isAbstract(request.isAbstract())
                .x(request.getX())
                .y(request.getY())
                .build();
        umlClass = classRepository.save(umlClass);
        ClassDto dto = mapper.toDto(umlClass);
        broadcast(diagramId, DiagramEvent.DiagramEventType.CLASS_CREATED, dto, userId, displayName);
        return dto;
    }

    @Transactional
    public ClassDto update(UUID diagramId, UUID classId, UpdateClassRequest request, UUID userId, String displayName) {
        UmlClass umlClass = getEntity(classId);
        lockService.acquire(diagramId, EditLock.LockedElementType.CLASS, classId, userId, displayName);

        if (request.getName() != null && !request.getName().isBlank()) {
            assertUniqueName(diagramId, request.getName(), classId);
            umlClass.setName(request.getName());
        }
        if (request.getStereotype() != null) umlClass.setStereotype(request.getStereotype());
        if (request.getIsAbstract() != null) umlClass.setAbstract(request.getIsAbstract());
        if (request.getX() != null) umlClass.setX(request.getX());
        if (request.getY() != null) umlClass.setY(request.getY());

        umlClass = classRepository.save(umlClass);
        ClassDto dto = mapper.toDto(umlClass);
        broadcast(diagramId, DiagramEvent.DiagramEventType.CLASS_UPDATED, dto, userId, displayName);
        return dto;
    }

    @Transactional
    public void delete(UUID diagramId, UUID classId, UUID userId, String displayName) {
        UmlClass umlClass = getEntity(classId);
        lockService.acquire(diagramId, EditLock.LockedElementType.CLASS, classId, userId, displayName);
        classRepository.delete(umlClass);
        broadcast(diagramId, DiagramEvent.DiagramEventType.CLASS_DELETED, classId, userId, displayName);
    }

    @Transactional
    public AttributeDto addAttribute(UUID diagramId, UUID classId, AttributeRequest request, UUID userId, String displayName) {
        UmlClass umlClass = getEntity(classId);
        lockService.acquire(diagramId, EditLock.LockedElementType.CLASS, classId, userId, displayName);

        int nextIndex = umlClass.getAttributes().size();
        UmlAttribute attribute = UmlAttribute.builder()
                .umlClass(umlClass)
                .name(request.getName())
                .dataType(request.getDataType())
                .visibility(request.getVisibility() == null ? Visibility.PRIVATE : request.getVisibility())
                .isPrimaryKey(request.isPrimaryKey())
                .nullable(request.isNullable())
                .unique(request.isUnique())
                .orderIndex(nextIndex)
                .build();
        attribute = attributeRepository.save(attribute);
        umlClass.getAttributes().add(attribute);

        ClassDto dto = mapper.toDto(umlClass);
        broadcast(diagramId, DiagramEvent.DiagramEventType.CLASS_UPDATED, dto, userId, displayName);
        return mapper.toDto(attribute);
    }

    @Transactional
    public AttributeDto updateAttribute(UUID diagramId, UUID classId, UUID attributeId, AttributeRequest request, UUID userId, String displayName) {
        UmlClass umlClass = getEntity(classId);
        lockService.acquire(diagramId, EditLock.LockedElementType.CLASS, classId, userId, displayName);

        UmlAttribute attribute = attributeRepository.findById(attributeId)
                .orElseThrow(() -> new NotFoundException("Atributo no encontrado: " + attributeId));
        attribute.setName(request.getName());
        attribute.setDataType(request.getDataType());
        attribute.setVisibility(request.getVisibility() == null ? attribute.getVisibility() : request.getVisibility());
        attribute.setPrimaryKey(request.isPrimaryKey());
        attribute.setNullable(request.isNullable());
        attribute.setUnique(request.isUnique());
        attribute = attributeRepository.save(attribute);

        broadcast(diagramId, DiagramEvent.DiagramEventType.CLASS_UPDATED, mapper.toDto(umlClass), userId, displayName);
        return mapper.toDto(attribute);
    }

    @Transactional
    public void removeAttribute(UUID diagramId, UUID classId, UUID attributeId, UUID userId, String displayName) {
        UmlClass umlClass = getEntity(classId);
        lockService.acquire(diagramId, EditLock.LockedElementType.CLASS, classId, userId, displayName);

        UmlAttribute attribute = attributeRepository.findById(attributeId)
                .orElseThrow(() -> new NotFoundException("Atributo no encontrado: " + attributeId));
        umlClass.getAttributes().remove(attribute);
        attributeRepository.delete(attribute);

        broadcast(diagramId, DiagramEvent.DiagramEventType.CLASS_UPDATED, mapper.toDto(umlClass), userId, displayName);
    }

    @Transactional(readOnly = true)
    public UmlClass getEntity(UUID classId) {
        return classRepository.findById(classId)
                .orElseThrow(() -> new NotFoundException("Clase no encontrada: " + classId));
    }

    private void assertUniqueName(UUID diagramId, String name, UUID excludeClassId) {
        boolean clash = classRepository.findByDiagramIdOrderByCreatedAtAsc(diagramId).stream()
                .anyMatch(c -> c.getName().equalsIgnoreCase(name) && !c.getId().equals(excludeClassId));
        if (clash) {
            throw new BadRequestException("Ya existe una clase llamada '" + name + "' en este diagrama");
        }
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
