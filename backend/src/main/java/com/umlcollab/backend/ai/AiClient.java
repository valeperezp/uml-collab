package com.umlcollab.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Cliente minimo (sin dependencias extra, con java.net.http) para la API de
 * mensajes de Anthropic, usando "tool use" / function calling para forzar
 * que la respuesta sea siempre la lista de operaciones estructuradas y
 * nunca texto libre.
 */
@Component
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final boolean enabled;

    public AiClient(@Value("${umlcollab.ai.api-key}") String apiKey,
                     @Value("${umlcollab.ai.model}") String model,
                     @Value("${umlcollab.ai.base-url}") String baseUrl,
                     @Value("${umlcollab.ai.enabled}") boolean enabled) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.enabled = enabled;
    }

    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    /**
     * Llama al modelo forzando que use la herramienta indicada y devuelve el
     * primer bloque tool_use de la respuesta (el input, ya parseado como JSON).
     *
     * @param systemPrompt instrucciones de sistema (rol del asistente, reglas)
     * @param userContent  contenido del mensaje de usuario: puede ser una lista
     *                     de bloques {type: text|image, ...} ya armados como JsonNode
     * @param toolJson     definicion de la tool (JSON Schema) como String
     * @param toolName     nombre de la tool (debe coincidir con el "name" del toolJson)
     */
    public JsonNode callTool(String systemPrompt, ArrayNode userContent, String toolJson, String toolName) {
        if (!isConfigured()) {
            throw new AiUnavailableException(
                    "El asistente de IA no esta configurado: falta ANTHROPIC_API_KEY en el entorno del backend.");
        }
        try {
            ObjectNode tool = (ObjectNode) objectMapper.readTree(toolJson);

            ObjectNode message = objectMapper.createObjectNode();
            message.put("role", "user");
            message.set("content", userContent);

            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("max_tokens", 4096);
            body.put("system", systemPrompt);
            body.set("messages", objectMapper.createArrayNode().add(message));
            body.set("tools", objectMapper.createArrayNode().add(tool));
            ObjectNode toolChoice = objectMapper.createObjectNode();
            toolChoice.put("type", "tool");
            toolChoice.put("name", toolName);
            body.set("tool_choice", toolChoice);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .timeout(Duration.ofSeconds(60))
                    .header("content-type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode responseBody = objectMapper.readTree(response.body());

            if (response.statusCode() >= 400) {
                String message2 = responseBody.path("error").path("message").asText(response.body());
                throw new AiUnavailableException("La API de IA respondio con error: " + message2);
            }

            for (JsonNode block : responseBody.path("content")) {
                if ("tool_use".equals(block.path("type").asText()) && toolName.equals(block.path("name").asText())) {
                    return block.path("input");
                }
            }
            throw new AiUnavailableException("El modelo no devolvio una llamada a la herramienta esperada");
        } catch (AiUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error llamando a la API de IA", e);
            throw new AiUnavailableException("No se pudo contactar al proveedor de IA: " + e.getMessage());
        }
    }

    public ObjectMapper mapper() {
        return objectMapper;
    }

    public ArrayNode textContent(String text) {
        ArrayNode content = objectMapper.createArrayNode();
        ObjectNode block = objectMapper.createObjectNode();
        block.put("type", "text");
        block.put("text", text);
        content.add(block);
        return content;
    }

    public ArrayNode textAndImageContent(String text, String base64Image, String mediaType) {
        ArrayNode content = objectMapper.createArrayNode();

        ObjectNode textBlock = objectMapper.createObjectNode();
        textBlock.put("type", "text");
        textBlock.put("text", text);
        content.add(textBlock);

        ObjectNode imageBlock = objectMapper.createObjectNode();
        imageBlock.put("type", "image");
        ObjectNode source = objectMapper.createObjectNode();
        source.put("type", "base64");
        source.put("media_type", mediaType);
        source.put("data", base64Image);
        imageBlock.set("source", source);
        content.add(imageBlock);

        return content;
    }
}
