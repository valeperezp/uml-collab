package com.umlcollab.backend.dto.requests;

import com.umlcollab.backend.model.RelationshipType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class RelationshipRequest {
    @NotNull
    private UUID sourceClassId;
    @NotNull
    private UUID targetClassId;
    @NotNull
    private RelationshipType type;
    private String sourceMultiplicity = "1";
    private String targetMultiplicity = "1";
    private String sourceRoleName;
    private String targetRoleName;
    private String label;
}
