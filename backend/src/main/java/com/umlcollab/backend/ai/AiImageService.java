package com.umlcollab.backend.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.umlcollab.backend.dto.DiagramDetailDto;
import com.umlcollab.backend.dto.ai.AiCommandResponse;
import com.umlcollab.backend.dto.ai.DiagramOperation;
import com.umlcollab.backend.service.DiagramService;
import com.umlcollab.backend.websocket.DiagramBroadcastService;
import com.umlcollab.backend.websocket.DiagramEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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

    private static final Logger log = LoggerFactory.getLogger(AiImageService.class);

    private static final String SYSTEM_PROMPT = """
            Sos un asistente experto en reconocimiento de diagramas de clases UML a partir de imagenes \
            (pizarras, bocetos a mano, capturas de pantalla de herramientas de modelado o diagramas impresos). \
            Tu objetivo es extraer TODAS las clases, atributos y relaciones visibles y convertirlos en una lista \
            ordenada de operaciones estructuradas usando la herramienta extract_diagram_from_image.

            Reglas cruciales:
            1. IDENTIFICAR Y CREAR TODAS LAS CLASES PRIMERO (CREATE_CLASS):
               - Para cada clase/caja en el diagrama, crea una operacion CREATE_CLASS con el nombre en "className" (ej: "Cliente", "Pedido", "Usuario").
               - NUNCA uses tipos de datos primitivos (como "BOOLEAN", "STRING", "INTEGER", "DATE", "UUID") como nombres de clase.
               - Asigna coordenadas (x, y) aproximadas pero bien distribuidas (ej: x entre 80 y 900, y entre 80 y 700 con al menos 240px de separacion entre cajas) para conservar el layout del diagrama.

            2. AGREGAR ATRIBUTOS (ADD_ATTRIBUTE):
               - Por cada atributo listado dentro de una clase, crea una operacion ADD_ATTRIBUTE.
               - El campo "className" DEBE ser el nombre exacto de la clase a la que pertenece el atributo (ej: "Cliente"). NUNCA pongas el tipo de dato en "className".
               - El campo "attributeName" es el nombre del atributo (ej: "id", "nombre", "fechaCreacion", "activo", "saldo").
               - El campo "dataType" es el tipo de dato UML/Java: STRING, TEXT, INTEGER, LONG, DOUBLE, DECIMAL, BOOLEAN, DATE, DATETIME, UUID.
               - Infiere si es clave primaria (isPrimaryKey: true si se llama 'id' o tiene subrayado / <<PK>>), visibilidad (PUBLIC '+', PRIVATE '-', PROTECTED '#', PACKAGE '~'), etc.

            3. CREAR RELACIONES (CREATE_RELATIONSHIP):
               - Conecta las clases visibles mediante CREATE_RELATIONSHIP.
               - "sourceClassName" y "targetClassName" DEBEN ser nombres de clases creadas previamente (ej: sourceClassName: "Cliente", targetClassName: "Pedido").
               - NUNCA uses tipos de datos primitivos como "BOOLEAN", "STRING" o "INTEGER" en sourceClassName ni targetClassName. Las relaciones solo existen entre clases.
               - Tipo de relacion (relationshipType):
                 * ASSOCIATION: Linea simple o flecha abierta.
                 * AGGREGATION: Rombo hueco (lado source).
                 * COMPOSITION: Rombo relleno (lado source).
                 * GENERALIZATION: Herencia / triangulo hueco (lado target).
                 * REALIZATION: Implementacion con linea discontinua y triangulo.
                 * DEPENDENCY: Linea discontinua con flecha.
               - Multiplicidades legibles o inferidas: '1', '0..1', '*', '1..*', '0..*'.
               - NOMBRE / VERBO DE RELACION (label):
                 * Extrae el texto o verbo escrito sobre la linea de relacion (ej: "vende", "compra", "tiene", "pertenece", "supervisa", "incluye") y colócalo en el campo "label".
               - MULTIPLES RELACIONES PARALELAS ENTRE LAS MISMAS CLASES:
                 * Si en la imagen observas DOS O MAS relaciones entre el mismo par de clases (ejemplo: entre 'PERSONA' y 'NOTAVENTA' hay una linea que dice 'vende' con '1' a '1..*' y otra linea paralela que dice 'compra' con '1' a '1..*'):
                 * DEBES generar una operacion CREATE_RELATIONSHIP INDEPENDIENTE para CADA una de las relaciones visibles, asignando el "label" correspondiente ("vende", "compra") y sus multiplicidades exactas.
               - RELACIONES RECURSIVAS (A SI MISMA):
                 * Si una linea de relacion sale y vuelve a entrar en la misma clase (ej: "TIPO" con multiplicidad '1' a '1..*'):
                 * Genera CREATE_RELATIONSHIP con sourceClassName y targetClassName iguales al nombre de esa clase.

            4. MENSAJE RESUMEN:
               - En "assistantMessage", escribe un mensaje breve y cordial en espanol resumiendo que clases y relaciones se reconocieron.
            """;

    private final AiClient aiClient;
    private final DiagramOperationApplier applier;
    private final DiagramService diagramService;
    private final DiagramBroadcastService broadcastService;
    private final com.umlcollab.backend.repository.UserRepository userRepository;

    public AiImageService(AiClient aiClient, DiagramOperationApplier applier,
                           DiagramService diagramService, DiagramBroadcastService broadcastService,
                           com.umlcollab.backend.repository.UserRepository userRepository) {
        this.aiClient = aiClient;
        this.applier = applier;
        this.diagramService = diagramService;
        this.broadcastService = broadcastService;
        this.userRepository = userRepository;
    }

    public AiCommandResponse handleImage(UUID diagramId, byte[] imageBytes, String mediaType, UUID userId, String displayName) {
        byte[] optimized = optimizeImage(imageBytes);
        String finalMediaType = (optimized.length != imageBytes.length) ? "image/jpeg" : (mediaType != null ? mediaType : "image/jpeg");
        String base64 = Base64.getEncoder().encodeToString(optimized);
        String prompt = "Convertir esta imagen de un diagrama de clases en operaciones estructuradas completas sobre el diagrama.";

        com.umlcollab.backend.model.User user = (userId != null) ? userRepository.findById(userId).orElse(null) : null;
        AiConfig userConfig = aiClient.resolveConfigForUser(user);

        JsonNode toolInput = aiClient.callTool(
                SYSTEM_PROMPT,
                aiClient.textAndImageContent(prompt, base64, finalMediaType),
                AiToolSchemas.extractDiagramFromImageTool(),
                "extract_diagram_from_image",
                userConfig);

        List<DiagramOperation> operations = aiClient.mapper().convertValue(
                toolInput.path("operations"), new TypeReference<List<DiagramOperation>>() {
                });
        String assistantMessage = toolInput.path("assistantMessage").asText("Diagrama reconocido exitosamente.");

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

    private byte[] optimizeImage(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) return imageBytes;
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
            BufferedImage img = ImageIO.read(bais);
            if (img == null) return imageBytes;

            int w = img.getWidth();
            int h = img.getHeight();
            int maxDim = 1600;

            if (w <= maxDim && h <= maxDim && imageBytes.length < 1024 * 1024) {
                return imageBytes;
            }

            int newW = w;
            int newH = h;
            if (w > h && w > maxDim) {
                newW = maxDim;
                newH = (int) Math.round((double) h / w * maxDim);
            } else if (h > maxDim) {
                newH = maxDim;
                newW = (int) Math.round((double) w / h * maxDim);
            }

            BufferedImage resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = resized.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(img, 0, 0, newW, newH, null);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(resized, "jpg", baos);
            byte[] result = baos.toByteArray();
            log.info("Imagen optimizada: {}x{} ({} KB) -> {}x{} ({} KB)", w, h, imageBytes.length / 1024, newW, newH, result.length / 1024);
            return result;
        } catch (Exception e) {
            log.warn("No se pudo optimizar la imagen, usando original: {}", e.getMessage());
            return imageBytes;
        }
    }
}
