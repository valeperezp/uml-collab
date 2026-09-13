package com.umlcollab.backend.service;

import com.umlcollab.backend.dto.DiagramDetailDto;
import com.umlcollab.backend.dto.DiagramSummaryDto;
import com.umlcollab.backend.dto.requests.CreateDiagramRequest;
import com.umlcollab.backend.exception.BadRequestException;
import com.umlcollab.backend.exception.NotFoundException;
import com.umlcollab.backend.model.Diagram;
import com.umlcollab.backend.repository.DiagramRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DiagramService {

    private final DiagramRepository diagramRepository;
    private final DiagramMapper mapper;

    public DiagramService(DiagramRepository diagramRepository, DiagramMapper mapper) {
        this.diagramRepository = diagramRepository;
        this.mapper = mapper;
    }

    @Transactional
    public DiagramSummaryDto create(UUID ownerId, CreateDiagramRequest request) {
        Diagram diagram = Diagram.builder()
                .name(request.getName())
                .description(request.getDescription())
                .ownerId(ownerId)
                .build();
        diagram = diagramRepository.save(diagram);
        return mapper.toSummaryDto(diagram);
    }

    @Transactional(readOnly = true)
    public List<DiagramSummaryDto> listOwnedBy(UUID ownerId) {
        return diagramRepository.findByOwnerId(ownerId).stream().map(mapper::toSummaryDto).toList();
    }

    @Transactional(readOnly = true)
    public DiagramDetailDto getDetail(UUID diagramId) {
        return mapper.toDetailDto(getEntity(diagramId));
    }

    @Transactional(readOnly = true)
    public Diagram getEntity(UUID diagramId) {
        return diagramRepository.findById(diagramId)
                .orElseThrow(() -> new NotFoundException("Diagrama no encontrado: " + diagramId));
    }

    @Transactional(readOnly = true)
    public DiagramSummaryDto joinByCode(String joinCode) {
        Diagram diagram = diagramRepository.findByJoinCode(joinCode.toUpperCase())
                .orElseThrow(() -> new BadRequestException("Codigo de invitacion invalido"));
        return mapper.toSummaryDto(diagram);
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
        diagramRepository.delete(diagram);
    }
}
