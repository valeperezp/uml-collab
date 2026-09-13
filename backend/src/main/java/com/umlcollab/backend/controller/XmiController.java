package com.umlcollab.backend.controller;

import com.umlcollab.backend.dto.ai.AiCommandResponse;
import com.umlcollab.backend.exception.BadRequestException;
import com.umlcollab.backend.security.CurrentUser;
import com.umlcollab.backend.xmi.XmiExportService;
import com.umlcollab.backend.xmi.XmiImportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/** Integracion con Enterprise Architect: exportar/importar el diagrama como XMI 2.1. */
@RestController
@RequestMapping("/api/diagrams/{diagramId}/xmi")
public class XmiController {

    private final XmiExportService exportService;
    private final XmiImportService importService;

    public XmiController(XmiExportService exportService, XmiImportService importService) {
        this.exportService = exportService;
        this.importService = importService;
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@PathVariable UUID diagramId) {
        byte[] xml = exportService.export(diagramId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("diagrama-" + diagramId + ".xmi").build().toString())
                .body(xml);
    }

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public ResponseEntity<AiCommandResponse> importXmi(@PathVariable UUID diagramId, @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("No se recibio ningun archivo");
        }
        try {
            return ResponseEntity.ok(importService.importXmi(diagramId, file.getBytes(), CurrentUser.id(), CurrentUser.get().getUsername()));
        } catch (IOException e) {
            throw new BadRequestException("No se pudo leer el archivo: " + e.getMessage());
        }
    }
}
