package com.umlcollab.backend.controller;

import com.umlcollab.backend.ai.AiCommandService;
import com.umlcollab.backend.ai.AiImageService;
import com.umlcollab.backend.dto.ai.AiCommandRequest;
import com.umlcollab.backend.dto.ai.AiCommandResponse;
import com.umlcollab.backend.exception.BadRequestException;
import com.umlcollab.backend.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/diagrams/{diagramId}/ai")
public class AiController {

    private final AiCommandService aiCommandService;
    private final AiImageService aiImageService;

    public AiController(AiCommandService aiCommandService, AiImageService aiImageService) {
        this.aiCommandService = aiCommandService;
        this.aiImageService = aiImageService;
    }

    /** Comando por texto o por voz (ya transcrito en el navegador con la Web Speech API). */
    @PostMapping("/command")
    public ResponseEntity<AiCommandResponse> command(@PathVariable UUID diagramId, @Valid @RequestBody AiCommandRequest request) {
        AiCommandResponse response = aiCommandService.handleCommand(
                diagramId, request.getCommand(), CurrentUser.id(), CurrentUser.get().getUsername());
        return ResponseEntity.ok(response);
    }

    /** Foto de un diagrama (pizarra, papel, otra herramienta) que se convierte en operaciones sobre el diagrama. */
    @PostMapping(value = "/image", consumes = "multipart/form-data")
    public ResponseEntity<AiCommandResponse> image(@PathVariable UUID diagramId, @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("No se recibio ninguna imagen");
        }
        String mediaType = file.getContentType();
        if (mediaType == null || !(mediaType.equals("image/png") || mediaType.equals("image/jpeg") || mediaType.equals("image/webp"))) {
            throw new BadRequestException("Formato de imagen no soportado (usa PNG, JPEG o WEBP)");
        }
        try {
            AiCommandResponse response = aiImageService.handleImage(
                    diagramId, file.getBytes(), mediaType, CurrentUser.id(), CurrentUser.get().getUsername());
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            throw new BadRequestException("No se pudo leer la imagen: " + e.getMessage());
        }
    }
}
