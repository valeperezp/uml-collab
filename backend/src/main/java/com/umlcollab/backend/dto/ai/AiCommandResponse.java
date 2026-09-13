package com.umlcollab.backend.dto.ai;

import com.umlcollab.backend.dto.DiagramDetailDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class AiCommandResponse {
    /** Mensaje en lenguaje natural que el asistente le devuelve al usuario (para mostrar/leer en voz alta). */
    private String assistantMessage;
    private List<DiagramOperation> appliedOperations;
    private DiagramDetailDto diagram;
}
