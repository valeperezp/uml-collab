package com.umlcollab.backend.dto;

import com.umlcollab.backend.model.RelationshipType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RelationshipDto {
    private UUID id;
    private UUID sourceClassId;
    private String sourceClassName;
    private UUID targetClassId;
    private String targetClassName;
    private RelationshipType type;
    private String sourceMultiplicity;
    private String targetMultiplicity;
    private String sourceRoleName;
    private String targetRoleName;
    private String label;
}
