package com.umlcollab.backend.dto.requests;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateDiagramRequest {
    @NotBlank
    private String name;
    private String description;
}
