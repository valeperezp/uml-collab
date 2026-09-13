package com.umlcollab.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiagramSummaryDto {
    private UUID id;
    private String name;
    private String description;
    private UUID ownerId;
    private String joinCode;
    private Instant createdAt;
    private Instant updatedAt;
    private int classCount;
}
