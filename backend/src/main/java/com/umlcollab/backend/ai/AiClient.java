package com.umlcollab.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.umlcollab.backend.dto.ai.AiTestResponse;
import com.umlcollab.backend.model.SystemAiConfig;
import com.umlcollab.backend.model.User;
import com.umlcollab.backend.repository.SystemAiConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Cliente para proveedores de IA: Google Gemini, DeepSeek, Anthropic Claude, OpenAI, Groq, OpenRouter y Locales.
 * Soporta entrada multimodal (texto, comandos de voz y fotos de diagramas),
 * function calling estructurado y configuración dinámica por usuario o sistema.
 */
@Component
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final SystemAiConfigRepository systemAiConfigRepository;
    private final AiConfig defaultConfig;

    public AiClient(SystemAiConfigRepository systemAiConfigRepository,
                    @Value("${OPENAI_API_KEY:}") String openaiKey,
                    @Value("${OPENAI_BASE_URL:}") String openaiBaseUrl,
                    @Value("${OPENAI_MODEL:}") String openaiModel,
                    @Value("${AI_API_KEY:}") String aiApiKey,
                    @Value("${AI_BASE_URL:}") String aiBaseUrl,
                    @Value("${AI_MODEL:}") String aiModel,
                    @Value("${AI_PROVIDER:}") String aiProvider,
                    @Value("${GEMINI_API_KEY:}") String geminiKey,
                    @Value("${GEMINI_MODEL:}") String geminiModel,
                    @Value("${GEMINI_BASE_URL:}") String geminiBaseUrl,
                    @Value("${DEEPSEEK_API_KEY:}") String deepseekKey,
                    @Value("${DEEPSEEK_BASE_URL:}") String deepseekBaseUrl,
                    @Value("${DEEPSEEK_MODEL:}") String deepseekModel,
                    @Value("${ANTHROPIC_API_KEY:}") String anthropicKey,
                    @Value("${ANTHROPIC_MODEL:}") String anthropicModel,
                    @Value("${AI_ENABLED:true}") boolean enabled) {

        this.systemAiConfigRepository = systemAiConfigRepository;

        String resolvedKey = firstNonBlank(openaiKey, aiApiKey, geminiKey, deepseekKey, anthropicKey);
        String resolvedBaseUrl = firstNonBlank(openaiBaseUrl, aiBaseUrl, deepseekBaseUrl, geminiBaseUrl);
        String resolvedModel = firstNonBlank(openaiModel, aiModel, geminiModel, deepseekModel, anthropicModel, "gpt-4o-mini");

        String provider;
        if (aiProvider != null && !aiProvider.isBlank()) {
            provider = aiProvider.trim().toLowerCase();
        } else if ((openaiKey != null && !openaiKey.isBlank()) || (openaiBaseUrl != null && !openaiBaseUrl.isBlank()) || (aiApiKey != null && !aiApiKey.isBlank())) {
            provider = "openai";
        } else if ((geminiKey != null && geminiKey.startsWith("AIzaSy")) || (resolvedKey != null && resolvedKey.startsWith("AIzaSy"))) {
            provider = "gemini";
        } else if ((anthropicKey != null && !anthropicKey.isBlank()) || (resolvedKey != null && resolvedKey.startsWith("sk-ant-"))) {
            provider = "anthropic";
        } else if (resolvedBaseUrl != null && (resolvedBaseUrl.contains("11434") || resolvedBaseUrl.contains("ollama") || resolvedBaseUrl.contains("localhost"))) {
            provider = "custom";
        } else {
            provider = "custom";
        }

        this.defaultConfig = AiConfig.builder()
                .provider(provider)
                .apiKey(resolvedKey)
                .model(resolvedModel)
                .baseUrl(resolvedBaseUrl)
                .enabled(enabled)
                .build();

        log.info("Proveedor de IA del sistema (fallback env): {} | Modelo: {} | BaseURL: {} (configurado: {})",
                this.defaultConfig.getProvider(), this.defaultConfig.getModel(), this.defaultConfig.getBaseUrl(), this.defaultConfig.isConfigured());
    }

    public AiConfig getDefaultConfig() {
        return getEffectiveSystemConfig();
    }

    public AiConfig getEffectiveSystemConfig() {
        if (systemAiConfigRepository != null) {
            try {
                var opt = systemAiConfigRepository.findTopByOrderByUpdatedAtDesc();
                if (opt.isPresent()) {
                    SystemAiConfig sys = opt.get();
                    if (sys.isEnabled()) {
                        AiConfig cfg = AiConfig.builder()
                                .provider(sys.getProvider() != null && !sys.getProvider().isBlank() ? sys.getProvider().trim().toLowerCase() : "custom")
                                .apiKey(sys.getApiKey())
                                .model(sys.getModel())
                                .baseUrl(sys.getBaseUrl())
                                .enabled(sys.isEnabled())
                                .build();
                        if (cfg.isConfigured()) {
                            return cfg;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("No se pudo leer la configuración de IA del sistema de la BD: {}", e.getMessage());
            }
        }
        return this.defaultConfig;
    }

    public AiConfig resolveConfigForUser(User user) {
        if (user != null && user.isAiCustomEnabled()) {
            boolean hasCustomKey = user.getAiApiKey() != null && !user.getAiApiKey().isBlank();
            boolean hasCustomBaseUrl = user.getAiBaseUrl() != null && !user.getAiBaseUrl().isBlank();
            if (hasCustomKey || hasCustomBaseUrl) {
                String prov = user.getAiProvider();
                if (prov == null || prov.isBlank()) {
                    if (user.getAiApiKey() != null && user.getAiApiKey().startsWith("AIzaSy")) {
                        prov = "gemini";
                    } else if (user.getAiApiKey() != null && user.getAiApiKey().startsWith("sk-ant-")) {
                        prov = "anthropic";
                    } else if (user.getAiApiKey() != null && user.getAiApiKey().startsWith("gsk_")) {
                        prov = "groq";
                    } else {
                        prov = "openai";
                    }
                }
                return AiConfig.builder()
                        .provider(prov.trim().toLowerCase())
                        .apiKey(user.getAiApiKey())
                        .model(user.getAiModel())
                        .baseUrl(user.getAiBaseUrl())
                        .enabled(true)
                        .build();
            }
        }
        return getEffectiveSystemConfig();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }

    public boolean isConfigured() {
        return getEffectiveSystemConfig().isConfigured();
    }

    public JsonNode callTool(String systemPrompt, ArrayNode userContent, String toolJson, String toolName) {
        return callTool(systemPrompt, userContent, toolJson, toolName, this.defaultConfig);
    }

    public JsonNode callTool(String systemPrompt, ArrayNode userContent, String toolJson, String toolName, AiConfig config) {
        AiConfig cfg = (config != null) ? config : this.defaultConfig;
        if (!cfg.isConfigured()) {
            throw new AiUnavailableException(
                    "El asistente de IA no está configurado. Por favor ingresa tu API Key en la configuración de la interfaz o define las variables en el servidor.");
        }

        String p = (cfg.getProvider() != null) ? cfg.getProvider().toLowerCase() : "";
        String k = cfg.getApiKey();
        String u = cfg.getBaseUrl();

        if ("gemini".equals(p) || (k != null && k.startsWith("AIzaSy") && (u == null || u.isBlank() || u.contains("googleapis.com")))) {
            return callGeminiTool(systemPrompt, userContent, toolJson, toolName, cfg);
        } else if ("anthropic".equals(p) || (k != null && k.startsWith("sk-ant-") && (u == null || u.isBlank() || u.contains("anthropic.com")))) {
            return callAnthropicTool(systemPrompt, userContent, toolJson, toolName, cfg);
        } else {
            return callOpenAiCompatibleTool(systemPrompt, userContent, toolJson, toolName, cfg);
        }
    }

    /**
     * Prueba la conexión a la IA con una configuración dada para verificar credenciales y latencia.
     */
    public AiTestResponse testConnection(AiConfig config) {
        if (!config.isConfigured()) {
            return AiTestResponse.builder()
                    .success(false)
                    .message("No se proporcionó una clave de API válida ni un endpoint local.")
                    .provider(config.getProvider())
                    .model(config.getModel())
                    .build();
        }

        Instant start = Instant.now();
        String effectiveModel = config.resolveEffectiveModel();
        String provider = (config.getProvider() != null) ? config.getProvider().toLowerCase() : "openai";

        try {
            String testPrompt = "Responde únicamente con una palabra: OK";
            ArrayNode userContent = textContent("Ping");
            String toolJson = "{\"name\":\"ping\",\"description\":\"Ping test\",\"parameters\":{\"type\":\"object\",\"properties\":{\"status\":{\"type\":\"string\"}},\"required\":[\"status\"]}}";

            JsonNode result = callTool("Devuelve status='OK'", userContent, toolJson, "ping", config);
            long durationMs = Duration.between(start, Instant.now()).toMillis();

            String statusVal = result.path("status").asText("OK");
            return AiTestResponse.builder()
                    .success(true)
                    .message("¡Conexión exitosa con " + provider.toUpperCase() + "! (" + effectiveModel + ", " + durationMs + "ms)")
                    .latencyMs(durationMs)
                    .provider(provider)
                    .model(effectiveModel)
                    .build();
        } catch (Exception e) {
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            String error = e.getMessage();
            if (error != null && error.contains("AiUnavailableException")) {
                error = error.replace("com.umlcollab.backend.ai.AiUnavailableException: ", "");
            }
            return AiTestResponse.builder()
                    .success(false)
                    .message(error != null ? error : "Error desconocido al contactar el proveedor de IA")
                    .latencyMs(durationMs)
                    .provider(provider)
                    .model(effectiveModel)
                    .build();
        }
    }

    /**
     * Llamada a Google Gemini API (soporta texto, voz e imágenes / fotos de diagramas).
     */
    private JsonNode callGeminiTool(String systemPrompt, ArrayNode userContent, String toolJson, String toolName, AiConfig cfg) {
        try {
            ObjectNode toolNode = (ObjectNode) objectMapper.readTree(toolJson);
            String toolDesc = toolNode.path("description").asText();
            JsonNode inputSchema = toolNode.path("input_schema");
            JsonNode parameters = inputSchema.isMissingNode() ? toolNode.path("parameters") : inputSchema;

            // 1. Function declaration para Gemini
            ObjectNode funcDecl = objectMapper.createObjectNode();
            funcDecl.put("name", toolName);
            if (!toolDesc.isBlank()) {
                funcDecl.put("description", toolDesc);
            }
            funcDecl.set("parameters", parameters);

            ArrayNode funcDecls = objectMapper.createArrayNode().add(funcDecl);
            ObjectNode toolsObj = objectMapper.createObjectNode();
            toolsObj.set("function_declarations", funcDecls);

            // 2. System instruction
            ObjectNode sysInstruction = objectMapper.createObjectNode();
            ArrayNode sysParts = objectMapper.createArrayNode();
            ObjectNode sysTextPart = objectMapper.createObjectNode();
            sysTextPart.put("text", systemPrompt);
            sysParts.add(sysTextPart);
            sysInstruction.set("parts", sysParts);

            // 3. User parts (texto e imagen si existe)
            ArrayNode userParts = objectMapper.createArrayNode();
            for (JsonNode block : userContent) {
                String blockType = block.path("type").asText();
                if ("text".equals(blockType)) {
                    ObjectNode part = objectMapper.createObjectNode();
                    part.put("text", block.path("text").asText());
                    userParts.add(part);
                } else if ("image".equals(blockType)) {
                    JsonNode source = block.path("source");
                    ObjectNode part = objectMapper.createObjectNode();
                    ObjectNode inlineData = objectMapper.createObjectNode();
                    inlineData.put("mime_type", source.path("media_type").asText("image/png"));
                    inlineData.put("data", source.path("data").asText());
                    part.set("inline_data", inlineData);
                    userParts.add(part);
                }
            }

            ObjectNode userContentObj = objectMapper.createObjectNode();
            userContentObj.put("role", "user");
            userContentObj.set("parts", userParts);

            // 4. Tool config para forzar el uso de la función
            ObjectNode toolConfig = objectMapper.createObjectNode();
            ObjectNode funcCallingConfig = objectMapper.createObjectNode();
            funcCallingConfig.put("mode", "ANY");
            ArrayNode allowedNames = objectMapper.createArrayNode().add(toolName);
            funcCallingConfig.set("allowed_function_names", allowedNames);
            toolConfig.set("function_calling_config", funcCallingConfig);

            // 5. Body completo
            ObjectNode body = objectMapper.createObjectNode();
            body.set("system_instruction", sysInstruction);
            body.set("contents", objectMapper.createArrayNode().add(userContentObj));
            body.set("tools", objectMapper.createArrayNode().add(toolsObj));
            body.set("tool_config", toolConfig);

            ObjectNode genConfig = objectMapper.createObjectNode();
            genConfig.put("temperature", 0.1);
            body.set("generationConfig", genConfig);

            String primaryModel = (cfg.getModel() != null && !cfg.getModel().isBlank()
                    && !cfg.getModel().equals("deepseek-chat") && !cfg.getModel().startsWith("claude") && !cfg.getModel().startsWith("gpt"))
                    ? cfg.getModel().trim() : "gemini-2.5-flash";

            List<String> candidateModels = List.of(
                    primaryModel,
                    "gemini-2.5-flash",
                    "gemini-2.0-flash",
                    "gemini-1.5-flash"
            );

            HttpResponse<String> response = null;
            JsonNode responseBody = null;
            String lastErrorMessage = null;
            int lastStatusCode = 500;

            for (String currentModel : candidateModels.stream().distinct().toList()) {
                String url = (cfg.getBaseUrl() != null && !cfg.getBaseUrl().isBlank() && cfg.getBaseUrl().contains("googleapis.com"))
                        ? cfg.getBaseUrl() : ("https://generativelanguage.googleapis.com/v1beta/models/" + currentModel + ":generateContent?key=" + cfg.getApiKey());

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(45))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                        .build();

                try {
                    response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    responseBody = objectMapper.readTree(response.body());

                    if (response.statusCode() == 200) {
                        break;
                    }

                    lastStatusCode = response.statusCode();
                    lastErrorMessage = responseBody.path("error").path("message").asText(response.body());
                    log.warn("Gemini model {} returned status {}: {}. Trying next fallback...", currentModel, lastStatusCode, lastErrorMessage);
                } catch (Exception ex) {
                    log.warn("Error calling Gemini model {}: {}", currentModel, ex.getMessage());
                    lastErrorMessage = ex.getMessage();
                }
            }

            if (response == null || response.statusCode() >= 400 || responseBody == null) {
                throw new AiUnavailableException("La API de Google Gemini respondió con error (" + lastStatusCode + "): " + lastErrorMessage + ". Verifica tu API Key o configura Ollama en la Configuración de IA.");
            }

            JsonNode candidates = responseBody.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray()) {
                    for (JsonNode part : parts) {
                        JsonNode functionCall = part.path("functionCall");
                        if (!functionCall.isMissingNode()) {
                            JsonNode args = functionCall.path("args");
                            if (!args.isMissingNode()) {
                                return args;
                            }
                        }
                        String text = part.path("text").asText("");
                        if (!text.isBlank()) {
                            String extracted = extractJson(text);
                            if (extracted != null) {
                                try {
                                    return objectMapper.readTree(extracted);
                                } catch (Exception ignored) {
                                }
                            }

                            ObjectNode fallbackNode = objectMapper.createObjectNode();
                            fallbackNode.put("assistantMessage", text.trim());
                            fallbackNode.set("operations", objectMapper.createArrayNode());
                            return fallbackNode;
                        }
                    }
                }
            }

            throw new AiUnavailableException("El modelo no devolvió una respuesta válida");
        } catch (AiUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error llamando a la API de Gemini", e);
            throw new AiUnavailableException("No se pudo contactar a Google Gemini: " + e.getMessage());
        }
    }

    /**
     * Llamada estándar para OpenAI y cualquier proveedor compatible
     * (Groq, DeepSeek, OpenRouter, Together AI, Ollama, vLLM, LMStudio, LocalAI, Azure OpenAI, etc.).
     */
    private JsonNode callOpenAiCompatibleTool(String systemPrompt, ArrayNode userContent, String toolJson, String toolName, AiConfig cfg) {
        try {
            ObjectNode toolNode = (ObjectNode) objectMapper.readTree(toolJson);
            String toolDesc = toolNode.path("description").asText();
            JsonNode inputSchema = toolNode.path("input_schema");

            ObjectNode functionDef = objectMapper.createObjectNode();
            functionDef.put("name", toolName);
            if (!toolDesc.isBlank()) {
                functionDef.put("description", toolDesc);
            }
            functionDef.set("parameters", inputSchema.isMissingNode() ? toolNode.path("parameters") : inputSchema);

            ObjectNode openAiTool = objectMapper.createObjectNode();
            openAiTool.put("type", "function");
            openAiTool.set("function", functionDef);

            // Multimodal content support (texto + imágenes estándar OpenAI image_url)
            boolean hasImages = false;
            ArrayNode userContentArray = objectMapper.createArrayNode();
            StringBuilder textOnly = new StringBuilder();

            for (JsonNode block : userContent) {
                String type = block.path("type").asText();
                if ("text".equals(type)) {
                    String text = block.path("text").asText();
                    if (textOnly.length() > 0) textOnly.append("\n");
                    textOnly.append(text);

                    ObjectNode textPart = objectMapper.createObjectNode();
                    textPart.put("type", "text");
                    textPart.put("text", text);
                    userContentArray.add(textPart);
                } else if ("image".equals(type)) {
                    hasImages = true;
                    JsonNode source = block.path("source");
                    String mediaType = source.path("media_type").asText("image/jpeg");
                    String base64Data = source.path("data").asText();

                    ObjectNode imgPart = objectMapper.createObjectNode();
                    imgPart.put("type", "image_url");
                    ObjectNode urlObj = objectMapper.createObjectNode();
                    urlObj.put("url", "data:" + mediaType + ";base64," + base64Data);
                    imgPart.set("image_url", urlObj);
                    userContentArray.add(imgPart);
                }
            }

            ObjectNode sysMsg = objectMapper.createObjectNode();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);

            ObjectNode userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");
            if (hasImages) {
                userMsg.set("content", userContentArray);
            } else {
                userMsg.put("content", textOnly.toString());
            }

            ArrayNode messages = objectMapper.createArrayNode();
            messages.add(sysMsg);
            messages.add(userMsg);

            String modelToUse = cfg.resolveEffectiveModel();

            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", modelToUse);
            body.put("max_tokens", 4096);
            body.put("temperature", 0.1);
            body.set("messages", messages);
            body.set("tools", objectMapper.createArrayNode().add(openAiTool));

            ObjectNode toolChoice = objectMapper.createObjectNode();
            toolChoice.put("type", "function");
            ObjectNode funcChoice = objectMapper.createObjectNode();
            funcChoice.put("name", toolName);
            toolChoice.set("function", funcChoice);
            body.set("tool_choice", toolChoice);

            String url = resolveOpenAiUrl(cfg.getBaseUrl(), cfg.getProvider());
            String bodyJson = objectMapper.writeValueAsString(body);

            HttpResponse<String> response = null;
            try {
                HttpRequest request = buildOpenAiRequest(url, bodyJson, cfg.getApiKey());
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception ex) {
                // Si falló localhost / 127.0.0.1 dentro de un contenedor Docker, reintentar con host.docker.internal
                if (url.contains("localhost") || url.contains("127.0.0.1")) {
                    String fallbackUrl = url.replace("localhost", "host.docker.internal").replace("127.0.0.1", "host.docker.internal");
                    log.info("No se pudo conectar a {} ({}). Intentando fallback con {}...", url, getExceptionDetail(ex), fallbackUrl);
                    try {
                        HttpRequest retryReq = buildOpenAiRequest(fallbackUrl, bodyJson, cfg.getApiKey());
                        response = httpClient.send(retryReq, HttpResponse.BodyHandlers.ofString());
                        url = fallbackUrl;
                    } catch (Exception ex2) {
                        log.warn("Fallo fallback con {}: {}", fallbackUrl, getExceptionDetail(ex2));
                        throw formatOpenAiConnectionException(url, ex);
                    }
                } else if (url.contains("host.docker.internal")) {
                    String fallbackUrl = url.replace("host.docker.internal", "localhost");
                    try {
                        HttpRequest retryReq = buildOpenAiRequest(fallbackUrl, bodyJson, cfg.getApiKey());
                        response = httpClient.send(retryReq, HttpResponse.BodyHandlers.ofString());
                        url = fallbackUrl;
                    } catch (Exception ex2) {
                        throw formatOpenAiConnectionException(url, ex);
                    }
                } else {
                    throw formatOpenAiConnectionException(url, ex);
                }
            }

            if (response.statusCode() >= 400) {
                String errorMsg = "";
                try {
                    JsonNode errNode = objectMapper.readTree(response.body());
                    errorMsg = errNode.path("error").path("message").asText(response.body());
                } catch (Exception parseEx) {
                    errorMsg = response.body();
                }
                throw new AiUnavailableException("La API (" + url + ") respondió con error (" + response.statusCode() + "): " + errorMsg);
            }

            JsonNode responseBody;
            try {
                responseBody = objectMapper.readTree(response.body());
            } catch (Exception ex) {
                throw new AiUnavailableException("La respuesta del modelo de IA no es un JSON válido: " + response.body());
            }

            JsonNode choices = responseBody.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode messageNode = choices.get(0).path("message");
                JsonNode toolCalls = messageNode.path("tool_calls");
                if (toolCalls.isArray() && !toolCalls.isEmpty()) {
                    for (JsonNode call : toolCalls) {
                        if (toolName.equals(call.path("function").path("name").asText())) {
                            String args = call.path("function").path("arguments").asText();
                            return objectMapper.readTree(args);
                        }
                    }
                    String args = toolCalls.get(0).path("function").path("arguments").asText();
                    return objectMapper.readTree(args);
                }

                // Fallback para modelos legacy function_call
                JsonNode functionCall = messageNode.path("function_call");
                if (!functionCall.isMissingNode()) {
                    String args = functionCall.path("arguments").asText();
                    return objectMapper.readTree(args);
                }

                String content = messageNode.path("content").asText("");
                if (!content.isBlank()) {
                    String extractedJson = extractJson(content);
                    if (extractedJson != null) {
                        try {
                            return objectMapper.readTree(extractedJson);
                        } catch (Exception ignored) {
                        }
                    }

                    // Si el modelo devolvió texto conversacional (ej: pidiendo aclaración o respondiendo una duda)
                    ObjectNode fallbackNode = objectMapper.createObjectNode();
                    fallbackNode.put("assistantMessage", content.trim());
                    fallbackNode.set("operations", objectMapper.createArrayNode());
                    return fallbackNode;
                }
            }

            throw new AiUnavailableException("El modelo no devolvió una respuesta válida");
        } catch (AiUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error llamando a la API OpenAI compatible", e);
            throw new AiUnavailableException("No se pudo contactar al proveedor OpenAI compatible: " + getExceptionDetail(e));
        }
    }

    private HttpRequest buildOpenAiRequest(String url, String bodyJson, String apiKey) {
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json");

        if (apiKey != null && !apiKey.isBlank()) {
            reqBuilder.header("Authorization", "Bearer " + apiKey.trim());
        }

        return reqBuilder.POST(HttpRequest.BodyPublishers.ofString(bodyJson)).build();
    }

    private String getExceptionDetail(Throwable t) {
        if (t == null) return "Error desconocido";
        if (t.getMessage() != null && !t.getMessage().isBlank()) {
            return t.getMessage();
        }
        if (t.getCause() != null && t.getCause().getMessage() != null && !t.getCause().getMessage().isBlank()) {
            return t.getCause().getMessage();
        }
        return t.getClass().getSimpleName();
    }

    private AiUnavailableException formatOpenAiConnectionException(String url, Exception e) {
        String detail = getExceptionDetail(e);
        if (detail.contains("Connection refused") || detail.contains("ConnectException") || detail.contains("ConnectTimeout")) {
            return new AiUnavailableException("No se pudo conectar con el endpoint de IA (" + url + "). "
                    + "Verifica que el servicio (ej. Ollama) esté encendido. Si ejecutas en Docker, usa 'http://host.docker.internal:11434/v1'.");
        }
        return new AiUnavailableException("No se pudo contactar al proveedor de IA en " + url + ": " + detail);
    }

    private String resolveOpenAiUrl(String rawBaseUrl, String provider) {
        if (rawBaseUrl != null && !rawBaseUrl.isBlank()) {
            String clean = rawBaseUrl.trim();
            while (clean.endsWith("/")) {
                clean = clean.substring(0, clean.length() - 1);
            }
            if (clean.endsWith("/chat/completions")) {
                return clean;
            }
            if (clean.endsWith("/v1")) {
                return clean + "/chat/completions";
            }
            if (clean.equalsIgnoreCase("https://api.openai.com") || clean.matches("https?://[^/]+(:\\d+)?")) {
                return clean + "/v1/chat/completions";
            }
            return clean + "/chat/completions";
        }

        String p = (provider != null) ? provider.toLowerCase() : "";
        return switch (p) {
            case "deepseek" -> "https://api.deepseek.com/v1/chat/completions";
            case "groq" -> "https://api.groq.com/openai/v1/chat/completions";
            case "openrouter" -> "https://openrouter.ai/api/v1/chat/completions";
            default -> "https://api.openai.com/v1/chat/completions";
        };
    }

    /**
     * Llamada para Anthropic Claude API.
     */
    private JsonNode callAnthropicTool(String systemPrompt, ArrayNode userContent, String toolJson, String toolName, AiConfig cfg) {
        try {
            ObjectNode tool = (ObjectNode) objectMapper.readTree(toolJson);

            ObjectNode message = objectMapper.createObjectNode();
            message.put("role", "user");
            message.set("content", userContent);

            String modelToUse = (cfg.getModel() != null && !cfg.getModel().isBlank()) ? cfg.getModel().trim() : "claude-3-5-sonnet-20241022";

            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", modelToUse);
            body.put("max_tokens", 4096);
            body.put("system", systemPrompt);
            body.set("messages", objectMapper.createArrayNode().add(message));
            body.set("tools", objectMapper.createArrayNode().add(tool));
            ObjectNode toolChoice = objectMapper.createObjectNode();
            toolChoice.put("type", "tool");
            toolChoice.put("name", toolName);
            body.set("tool_choice", toolChoice);

            String url = (cfg.getBaseUrl() != null && !cfg.getBaseUrl().isBlank()) ? cfg.getBaseUrl() : "https://api.anthropic.com/v1/messages";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("content-type", "application/json")
                    .header("x-api-key", cfg.getApiKey() != null ? cfg.getApiKey().trim() : "")
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode responseBody = objectMapper.readTree(response.body());

            if (response.statusCode() >= 400) {
                String message2 = responseBody.path("error").path("message").asText(response.body());
                throw new AiUnavailableException("La API de Anthropic respondió con error: " + message2);
            }

            for (JsonNode block : responseBody.path("content")) {
                if ("tool_use".equals(block.path("type").asText()) && toolName.equals(block.path("name").asText())) {
                    return block.path("input");
                }
            }
            throw new AiUnavailableException("El modelo Anthropic no devolvió una llamada a la herramienta esperada");
        } catch (AiUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error llamando a la API de Anthropic", e);
            throw new AiUnavailableException("No se pudo contactar al proveedor de IA: " + e.getMessage());
        }
    }

    private String extractJson(String text) {
        text = text.trim();
        if (text.startsWith("```json")) {
            text = text.substring(7);
        } else if (text.startsWith("```")) {
            text = text.substring(3);
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }
        text = text.trim();
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1);
        }
        return null;
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
