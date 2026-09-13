package com.umlcollab.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Estado completo de un diagrama: lo que reciben el editor Angular y el agente de IA como contexto. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiagramDetailDto {
    private UUID id;
    private String name;
    private String description;
    private UUID ownerId;
    private String joinCode;
    private Instant updatedAt;
    private List<ClassDto> classes;
    private List<RelationshipDto> relationships;
}
