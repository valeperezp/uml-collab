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

import java.util.List;
import java.util.UUID;

/**
 * El "segundo tipo de interaccion" que pide el enunciado: un agente que edita
 * el diagrama por comando de texto o voz (la voz se transcribe en el
 * navegador con la Web Speech API y llega aqui como texto). El modelo NUNCA
 * genera el diagrama a partir del problema completo: solo interpreta UNA
 * instruccion puntual del usuario (crear esta clase, agregar este atributo,
 * relacionar esto con esto, etc) y la traduce a operaciones concretas.
 */
@Service
public class AiCommandService {

    private static final String SYSTEM_PROMPT = """
            Sos el asistente de edicion de un editor colaborativo de diagramas de clases UML \
            (una herramienta tipo Enterprise Architect, pero colaborativa). Tu unico trabajo es \
            traducir UNA instruccion del usuario (que puede venir de texto o de un comando de voz \
            ya transcrito) a operaciones estructuradas sobre el diagrama actual, usando la \
            herramienta apply_diagram_operations.

            Reglas importantes:
            - NUNCA generes un diagrama completo a partir de un problema o requerimiento: el \
              usuario es quien esta disenando el diagrama, vos solo ejecutas lo que pide.
            - Si el usuario pide crear una clase, usa CREATE_CLASS. Si pide agregar un atributo a \
              una clase que ya existe, usa ADD_ATTRIBUTE contra esa clase (no crees la clase de nuevo).
            - Para relaciones, identifica bien cual es la clase origen y cual la destino segun lo \
              que diga el usuario, y elegi el tipo (ASSOCIATION, AGGREGATION, COMPOSITION, \
              GENERALIZATION para herencia, REALIZATION) y las multiplicidades mas razonables si \
              el usuario no las dice explicitamente (por defecto "1" a "1").
            - Los nombres de clase deben quedar en PascalCase y los de atributo en camelCase, \
              como se esperaria en un backend Java, aunque el usuario los diga de otra forma.
            - Si la instruccion es ambigua o falta informacion imprescindible (por ejemplo, pide \
              una relacion pero no queda claro con que clase), elegi la interpretacion mas \
              razonable dado el estado actual del diagrama en vez de no hacer nada.
            - Devuelve SIEMPRE al menos un mensaje breve en assistantMessage confirmando en \
              espanol lo que hiciste.
            """;

    private final AiClient aiClient;
    private final DiagramOperationApplier applier;
    private final DiagramService diagramService;
    private final DiagramBroadcastService broadcastService;

    public AiCommandService(AiClient aiClient, DiagramOperationApplier applier,
                             DiagramService diagramService, DiagramBroadcastService broadcastService) {
        this.aiClient = aiClient;
        this.applier = applier;
        this.diagramService = diagramService;
        this.broadcastService = broadcastService;
    }

    public AiCommandResponse handleCommand(UUID diagramId, String command, UUID userId, String displayName) {
        DiagramDetailDto currentState = diagramService.getDetail(diagramId);

        String prompt = "Estado actual del diagrama (JSON):\n" + toJson(currentState) +
                "\n\nInstruccion del usuario:\n" + command;

        JsonNode toolInput = aiClient.callTool(
                SYSTEM_PROMPT,
                aiClient.textContent(prompt),
                AiToolSchemas.applyOperationsTool(),
                "apply_diagram_operations");

        List<DiagramOperation> operations = parseOperations(toolInput);
        String assistantMessage = toolInput.path("assistantMessage").asText("Listo.");

        List<DiagramOperation> applied = applier.apply(diagramId, operations, userId, displayName);
        DiagramDetailDto updated = diagramService.getDetail(diagramId);

        broadcastService.broadcast(diagramId, DiagramEvent.builder()
                .type(DiagramEvent.DiagramEventType.AI_OPERATIONS_APPLIED)
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

    private List<DiagramOperation> parseOperations(JsonNode toolInput) {
        return aiClient.mapper().convertValue(toolInput.path("operations"), new TypeReference<List<DiagramOperation>>() {
        });
    }

    private String toJson(Object value) {
        try {
            return aiClient.mapper().writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
