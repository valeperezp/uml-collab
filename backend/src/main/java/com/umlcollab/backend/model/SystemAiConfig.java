package com.umlcollab.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "system_ai_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemAiConfig {

    @Id
    @GeneratedValue
    private UUID id;

    /** Proveedor de IA por defecto del sistema (gemini, openai, anthropic, deepseek, groq, openrouter, custom/ollama). */
    @Column(length = 50)
    private String provider;

    /** Clave de API de IA global opcional. */
    @Column(length = 1000)
    private String apiKey;

    /** Modelo de IA preferido para el sistema (ej. gemma4:31b, llama3.2, gpt-4o-mini). */
    @Column(length = 100)
    private String model;

    /** URL base del endpoint (ej. http://localhost:11434/v1 o endpoint en la red). */
    @Column(length = 500)
    private String baseUrl;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
