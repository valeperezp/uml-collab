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
            Sos el asistente experto en modelado de software, bases de datos y diagramas de clases UML en un editor colaborativo en tiempo real. \
            Tu trabajo es:
            1. Interpretar las instrucciones del usuario (texto o voz) y traducirlas a operaciones estructuradas sobre el diagrama actual mediante apply_diagram_operations.
            2. Analizar el estado actual del diagrama (clases, atributos y relaciones presentes en el JSON), diagnosticar problemas o dudas conceptuales, y CORREGIR activamente cualquier error o incongruencia.

            Reglas fundamentales de modelado UML y funcionamiento del sistema:

            1. REUTILIZAR CLASES EXISTENTES:
               - Revisa la lista de clases actuales en el JSON del diagrama que recibes.
               - Si el usuario menciona una clase que YA EXISTE en el diagrama (ej. 'Cliente', 'Sucursal', 'Persona'), NUNCA uses CREATE_CLASS para esa clase. Solo referenciala por su nombre en className, sourceClassName o targetClassName.
               - Solo usa CREATE_CLASS para clases NUEVAS que no existan todavia en el diagrama.

            2. CREACION DE CLASES Y ATRIBUTOS:
               - Si el usuario pide crear una clase (ej. 'crea la clase Sucursal'):
                 a) Genera la operacion CREATE_CLASS con el nombre en PascalCase (ej: 'Sucursal').
                 b) Genera operaciones ADD_ATTRIBUTE para los atributos esenciales de esa entidad con sus tipos \
                    (ej: para Sucursal -> id: UUID (isPrimaryKey: true), nombre: STRING, direccion: STRING, telefono: STRING; \
                    para Cliente -> id: UUID (isPrimaryKey: true), nombre: STRING, email: STRING, ci: STRING; \
                    para Factura -> id: UUID (isPrimaryKey: true), numero: STRING, fecha: DATE, total: DECIMAL; \
                    para Producto -> id: UUID (isPrimaryKey: true), nombre: STRING, precio: DECIMAL, stock: INTEGER).
                 c) Nombres de atributos en camelCase. Tipos permitidos: STRING, TEXT, INTEGER, LONG, DOUBLE, DECIMAL, BOOLEAN, DATE, DATETIME, UUID.
               - Si el usuario pide crear una clase pero NO especifica el nombre (ej. 'crea una nueva clase', 'agrega una clase'):
                 Crea una clase por defecto como 'Usuario' o 'Entidad' con id: UUID (isPrimaryKey: true), nombre: STRING y fechaCreacion: DATETIME, y explica en assistantMessage que la has creado con ese nombre por defecto.

            3. REGLAS DE MULTIPLICIDADES Y RELACIONES (CRUCIAL):
               - Por defecto, las relaciones de negocio son de UNO A MUCHOS (1 a 0..* o 1 a *) o UNO A UNO (1 a 0..1 o 1 a 1):
                 * Sucursal (1) a Cliente (0..*): Una sucursal atiende a muchos clientes (o un cliente pertenece a 1 sucursal).
                 * Cliente (1) a Factura (0..* o 1..*): Un cliente posee muchas facturas.
                 * Empresa (1) a Empleado (1..*): Una empresa tiene muchos empleados.
                 * Categoria (1) a Producto (0..*): Una categoria agrupa muchos productos.
                 * Factura (1) a DetalleFactura (1..*): Composicion 1 a 1..*.
               - RELACIONES RECURSIVAS / REFLEXIVAS:
                 * Una clase puede relacionarse consigo misma (ej: Categoria padre e hija 0..1 a 0..*, Empleado supervisor 1 a 0..*, Tipo relacionado consigo mismo 1 a 1..*).
                 * En las relaciones recursivas, tanto sourceClassName como targetClassName corresponden a la misma clase.
               - MULTIPLES RELACIONES PARALELAS ENTRE LAS MISMAS CLASES:
                 * Pueden existir dos o mas relaciones entre el mismo par de clases con distintos roles o nombres (ej: 'PERSONA' vende 'NOTAVENTA' 1 a 1..* y 'PERSONA' compra 'NOTAVENTA' 1 a 1..*).
                 * En esos casos, genera operaciones CREATE_RELATIONSHIP independientes para cada una, colocando el verbo/nombre en "label" (ej: label: "vende", label: "compra").
               - IMPORTANTE - COMPORTAMIENTO DE TABLAS INTERMEDIAS:
                 * En este sistema, cuando una asociacion entre dos clases distintas tiene multiplicidad de muchos en ambos lados ('*' a '*', '1..*' a '0..*', '0..*' a '1..*'), el motor genera AUTOMATICAMENTE una clase/tabla intermedia (ej: 'SucursalCliente').
                 * Por lo tanto, NUNCA uses multiplicidades de muchos en ambos extremos a menos que el usuario pida explicitamente 'muchos a muchos' o sea un caso genuino (ej. Estudiante a Curso, Producto a Etiqueta, Rol a Permiso).

            4. DIAGNOSTICO, CORRECCION DE ERRORES Y CONSULTAS DEL USUARIO:
               - Si el usuario dice 'por que aparecio la tabla intermedia?', 'la relacion no es de muchos a muchos', 'esta mal', 'elimina la tabla intermedia', 'corrige la cardinalidad', o cualquier consulta/queja similar:
                 a) DIAGNOSTICO: Explica con claridad en assistantMessage por que sucedio (ej: 'Aparecio porque la relacion previa se habia establecido con multiplicidad de muchos a muchos (* a *), lo que provoco que el sistema creara automaticamente una tabla asociativa intermedia. Ya he eliminado la clase intermedia y conectado las clases directamente con multiplicidad 1 a muchos (1 a 0..*).').
                 b) CORRECCION AUTOMATICA OBLIGATORIA: DEBES incluir de inmediato en 'operations' las acciones para arreglarlo:
                    - DELETE_CLASS: Con el nombre de la clase intermedia que sobra (ej: 'SucursalCliente'). Al borrarla, sus conexiones intermedias se eliminan solas.
                    - CREATE_RELATIONSHIP: Conectando directamente las dos clases reales del dominio (ej. sourceClassName: 'Sucursal', sourceMultiplicity: '1', targetClassName: 'Cliente', targetMultiplicity: '0..*', relationshipType: 'ASSOCIATION').
                 c) NUNCA des solo una explicacion teorica pasiva si hay entidades en el diagrama que requieren arreglo.
               - Si el usuario consulta 'que esta mal en mi diagrama?' o pide validar el modelo:
                 a) Revisa clases sin atributos, nombres duplicados, tipos primitivos usados como clases, relaciones redundantes o cardinalidades incoherentes.
                 b) Explica los hallazgos en assistantMessage y aplica las operaciones de correccion oportunas.

            5. MENSAJE AL USUARIO (assistantMessage):
               - Devuelve SIEMPRE una respuesta clara, profesional y cordial en espanol resumiendo exactamente lo diagnosticado y las operaciones realizadas.
            """;

    private final AiClient aiClient;
    private final DiagramOperationApplier applier;
    private final DiagramService diagramService;
    private final DiagramBroadcastService broadcastService;
    private final com.umlcollab.backend.repository.UserRepository userRepository;

    public AiCommandService(AiClient aiClient, DiagramOperationApplier applier,
                             DiagramService diagramService, DiagramBroadcastService broadcastService,
                             com.umlcollab.backend.repository.UserRepository userRepository) {
        this.aiClient = aiClient;
        this.applier = applier;
        this.diagramService = diagramService;
        this.broadcastService = broadcastService;
        this.userRepository = userRepository;
    }

    public AiCommandResponse handleCommand(UUID diagramId, String command, UUID userId, String displayName) {
        String quickReply = getQuickConversationalResponse(command);
        if (quickReply != null) {
            DiagramDetailDto current = diagramService.getDetail(diagramId);
            return AiCommandResponse.builder()
                    .assistantMessage(quickReply)
                    .appliedOperations(List.of())
                    .diagram(current)
                    .build();
        }

        DiagramDetailDto currentState = diagramService.getDetail(diagramId);

        StringBuilder summary = new StringBuilder();
        summary.append("Resumen del diagrama actual:\n");
        if (currentState.getClasses() == null || currentState.getClasses().isEmpty()) {
            summary.append("- No hay clases creadas aún.\n");
        } else {
            summary.append("- Clases (").append(currentState.getClasses().size()).append("): ");
            for (int i = 0; i < currentState.getClasses().size(); i++) {
                if (i > 0) summary.append(", ");
                summary.append(currentState.getClasses().get(i).getName());
            }
            summary.append("\n");
        }
        if (currentState.getRelationships() == null || currentState.getRelationships().isEmpty()) {
            summary.append("- No hay relaciones creadas aún.\n");
        } else {
            summary.append("- Relaciones (").append(currentState.getRelationships().size()).append("):\n");
            for (com.umlcollab.backend.dto.RelationshipDto r : currentState.getRelationships()) {
                summary.append("  * ").append(r.getSourceClassName())
                        .append(" (").append(r.getSourceMultiplicity() == null ? "" : r.getSourceMultiplicity()).append(") ")
                        .append(r.getType()).append(" -> ")
                        .append(r.getTargetClassName())
                        .append(" (").append(r.getTargetMultiplicity() == null ? "" : r.getTargetMultiplicity()).append(")\n");
            }
        }

        String prompt = summary + "\nDetalle del diagrama (JSON):\n" + toJson(currentState) +
                "\n\nInstruccion del usuario:\n" + command;

        com.umlcollab.backend.model.User user = (userId != null) ? userRepository.findById(userId).orElse(null) : null;
        AiConfig userConfig = aiClient.resolveConfigForUser(user);

        JsonNode toolInput = aiClient.callTool(
                SYSTEM_PROMPT,
                aiClient.textContent(prompt),
                AiToolSchemas.applyOperationsTool(),
                "apply_diagram_operations",
                userConfig);

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

    private String getQuickConversationalResponse(String command) {
        if (command == null) return null;
        String text = command.trim().toLowerCase()
                .replaceAll("^[¡!¿?\\.,;\\s]+", "")
                .replaceAll("[¡!¿?\\.,;\\s]+$", "")
                .trim();

        // Saludos
        if (text.matches("^(hola|buenas|buen dia|buenos dias|buenas tardes|buenas noches|hey|hi|hello|saludos|que tal|como estas|como te va)( [a-z0-9]+)?$")) {
            return "¡Hola! 👋 Soy tu asistente de modelado UML en tiempo real.\n\n" +
                    "Puedes pedirme que edite el diagrama mediante texto o voz:\n" +
                    "• *'Crea la clase Usuario con nombre, email y clave'*\n" +
                    "• *'Agrega el atributo telefono a Cliente'*\n" +
                    "• *'Relaciona Pedido y Cliente (muchos a uno)'*\n" +
                    "• O sube una foto de una pizarra para digitalizarla.\n\n" +
                    "¿Qué deseas modelar hoy?";
        }

        // Agradecimientos
        if (text.matches("^(gracias|muchas gracias|mil gracias|thank you|thanks)( [a-z0-9]+)?$")) {
            return "¡Con gusto! 😊 Si necesitas agregar más clases, atributos o relaciones al diagrama, solo dímelo.";
        }

        // Ayuda y capacidades
        if (text.matches("^(ayuda|help|que puedes hacer|como funciona|comandos|que sabes hacer|instrucciones|opciones)$")) {
            return "📋 **Guía de comandos UML:**\n\n" +
                    "1. **Crear clases:** *'Crea la clase Factura con numero, fecha y total'*\n" +
                    "2. **Agregar atributos:** *'Agrega saldo: DECIMAL a Cuenta'*\n" +
                    "3. **Relaciones y multiplicidades:** *'Conecta Factura con DetalleFactura como composición 1 a *'*\n" +
                    "4. **Herencia:** *'Estudiante hereda de Persona'*\n" +
                    "5. **Eliminar:** *'Elimina la clase Temporal'*\n" +
                    "6. **Foto de pizarra:** Usa el botón de imagen arriba para digitalizar un diagrama dibujado.";
        }

        // Confirmaciones / ok
        if (text.matches("^(ok|vale|listo|entendido|perfecto|genial|bien|excelente|de acuerdo|va)$")) {
            return "¡Perfecto! Dime qué clase o relación quieres agregar o modificar a continuación.";
        }

        return null;
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
