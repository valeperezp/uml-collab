package com.umlcollab.backend.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.umlcollab.backend.dto.DiagramDetailDto;
import com.umlcollab.backend.dto.ai.AiCommandResponse;
import com.umlcollab.backend.dto.ai.DiagramOperation;
import com.umlcollab.backend.service.DiagramService;
import com.umlcollab.backend.websocket.DiagramBroadcastService;
import com.umlcollab.backend.websocket.DiagramEvent;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Tercera forma de crear un diagrama que pide el enunciado: sacarle una foto
 * (a una pizarra, un boceto en papel, un diagrama de otra herramienta) y que
 * la app lo convierta en un diagrama editable.
 */
@Service
public class AiImageService {

    private static final String SYSTEM_PROMPT = """
            Sos un asistente que reconoce diagramas de clases UML dibujados o \
            fotografiados (pizarras, papel, capturas de otras herramientas como \
            Enterprise Architect) y los convierte en operaciones estructuradas usando \
            la herramienta extract_diagram_from_image.

            Reglas:
            - Primero identifica todas las clases (rectangulos/cajas con nombre), en \
              CREATE_CLASS, dandoles una posicion (x,y) parecida a como estan dispuestas \
              en la imagen para conservar el layout.
            - Despues agrega cada atributo visible de cada clase con ADD_ATTRIBUTE, \
              infiriendo el tipo de dato mas razonable si no esta explicito (STRING por \
              defecto para texto, INTEGER/LONG para numeros enteros, DECIMAL para dinero, \
              BOOLEAN, DATE/DATETIME para fechas, UUID si el atributo se llama id y no hay \
              mas contexto).
            - Por ultimo agrega las relaciones (lineas entre clases) con \
              CREATE_RELATIONSHIP, interpretando el tipo (asociacion, agregacion -rombo \
              hueco-, composicion -rombo relleno-, herencia -flecha triangular-) y las \
              multiplicidades anotadas en los extremos si son legibles.
            - Si algo no se alcanza a leer con confianza, es mejor omitirlo que inventarlo.
            """;

    private final AiClient aiClient;
    private final DiagramOperationApplier applier;
    private final DiagramService diagramService;
    private final DiagramBroadcastService broadcastService;

    public AiImageService(AiClient aiClient, DiagramOperationApplier applier,
                           DiagramService diagramService, DiagramBroadcastService broadcastService) {
        this.aiClient = aiClient;
        this.applier = applier;
        this.diagramService = diagramService;
        this.broadcastService = broadcastService;
    }

    public AiCommandResponse handleImage(UUID diagramId, byte[] imageBytes, String mediaType, UUID userId, String displayName) {
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        String prompt = "Convertir esta imagen de un diagrama de clases en operaciones sobre el diagrama.";

        JsonNode toolInput = aiClient.callTool(
                SYSTEM_PROMPT,
                aiClient.textAndImageContent(prompt, base64, mediaType),
                AiToolSchemas.extractDiagramFromImageTool(),
                "extract_diagram_from_image");

        List<DiagramOperation> operations = aiClient.mapper().convertValue(
                toolInput.path("operations"), new TypeReference<List<DiagramOperation>>() {
                });
        String assistantMessage = toolInput.path("assistantMessage").asText("Diagrama reconocido.");

        List<DiagramOperation> applied = applier.apply(diagramId, operations, userId, displayName);
        DiagramDetailDto updated = diagramService.getDetail(diagramId);

        broadcastService.broadcast(diagramId, DiagramEvent.builder()
                .type(DiagramEvent.DiagramEventType.DIAGRAM_REPLACED)
                .actorUserId(userId)
                .actorDisplayName(displayName)
                .payload(updated)
                .build());

        return AiCommandResponse.builder()
                .assistantMessage(assistantMessage)
                .appliedOperations(applied)
                .diagram(updated)
                .build();
    }
}
