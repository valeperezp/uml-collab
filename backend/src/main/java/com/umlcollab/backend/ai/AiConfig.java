package com.umlcollab.backend.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiConfig {
    private String provider;
    private String apiKey;
    private String model;
    private String baseUrl;
    private boolean enabled;

    public boolean isConfigured() {
        if (!enabled) return false;
        if (baseUrl != null && (baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1")
                || baseUrl.contains("ollama") || baseUrl.contains("docker.internal"))) {
            return true;
        }
        return apiKey != null && !apiKey.isBlank();
    }

    public String resolveEffectiveModel() {
        if (model != null && !model.isBlank()) {
            return model.trim();
        }
        String p = (provider != null) ? provider.toLowerCase() : "";
        return switch (p) {
            case "gemini" -> "gemini-2.5-flash";
            case "anthropic" -> "claude-3-5-sonnet-20241022";
            case "deepseek" -> "deepseek-chat";
            case "groq" -> "llama-3.3-70b-versatile";
            case "openrouter" -> "openai/gpt-4o-mini";
            default -> "gpt-4o-mini";
        };
    }
}
