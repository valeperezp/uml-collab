package com.umlcollab.backend.service;

import com.umlcollab.backend.dto.AttributeDto;
import com.umlcollab.backend.dto.ClassDto;
import com.umlcollab.backend.dto.DiagramDetailDto;
import com.umlcollab.backend.dto.DiagramSummaryDto;
import com.umlcollab.backend.dto.RelationshipDto;
import com.umlcollab.backend.model.Diagram;
import com.umlcollab.backend.model.UmlAttribute;
import com.umlcollab.backend.model.UmlClass;
import com.umlcollab.backend.model.UmlRelationship;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class DiagramMapper {

    public AttributeDto toDto(UmlAttribute a) {
        return AttributeDto.builder()
                .id(a.getId())
                .name(a.getName())
                .dataType(a.getDataType())
                .visibility(a.getVisibility())
                .isPrimaryKey(a.isPrimaryKey())
                .nullable(a.isNullable())
                .unique(a.isUnique())
                .orderIndex(a.getOrderIndex())
                .build();
    }

    public ClassDto toDto(UmlClass c) {
        List<AttributeDto> attrs = c.getAttributes().stream()
                .sorted(Comparator.comparingInt(UmlAttribute::getOrderIndex))
                .map(this::toDto)
                .toList();
        return ClassDto.builder()
                .id(c.getId())
                .name(c.getName())
                .stereotype(c.getStereotype())
                .isAbstract(c.isAbstract())
                .x(c.getX())
                .y(c.getY())
                .attributes(attrs)
                .build();
    }

    public RelationshipDto toDto(UmlRelationship r) {
        return RelationshipDto.builder()
                .id(r.getId())
                .sourceClassId(r.getSourceClass().getId())
                .sourceClassName(r.getSourceClass().getName())
                .targetClassId(r.getTargetClass().getId())
                .targetClassName(r.getTargetClass().getName())
                .type(r.getType())
                .sourceMultiplicity(r.getSourceMultiplicity())
                .targetMultiplicity(r.getTargetMultiplicity())
                .sourceRoleName(r.getSourceRoleName())
                .targetRoleName(r.getTargetRoleName())
                .label(r.getLabel())
                .build();
    }

    public DiagramSummaryDto toSummaryDto(Diagram d) {
        return DiagramSummaryDto.builder()
                .id(d.getId())
                .name(d.getName())
                .description(d.getDescription())
                .ownerId(d.getOwnerId())
                .joinCode(d.getJoinCode())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .classCount(d.getClasses().size())
                .build();
    }

    public DiagramDetailDto toDetailDto(Diagram d) {
        return DiagramDetailDto.builder()
                .id(d.getId())
                .name(d.getName())
                .description(d.getDescription())
                .ownerId(d.getOwnerId())
                .joinCode(d.getJoinCode())
                .updatedAt(d.getUpdatedAt())
                .classes(d.getClasses().stream().map(this::toDto).toList())
                .relationships(d.getRelationships().stream().map(this::toDto).toList())
                .build();
    }
}
