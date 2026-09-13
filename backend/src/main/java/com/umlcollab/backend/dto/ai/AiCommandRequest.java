package com.umlcollab.backend.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiCommandRequest {
    /** Texto del comando (ya transcrito, si vino de voz, por el reconocimiento de voz del navegador). */
    @NotBlank
    private String command;
}
