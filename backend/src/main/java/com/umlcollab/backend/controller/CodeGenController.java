package com.umlcollab.backend.controller;

import com.umlcollab.backend.codegen.CodeGenService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * El corazon del "generamos codigo desde el modelo" que pide el enunciado:
 * a partir del diagrama de clases actual, arma un proyecto Spring Boot
 * completo (modelo/repositorio/servicio/controlador/DTO) contra Postgres,
 * listo para abrir en un IDE y correr.
 */
@RestController
@RequestMapping("/api/diagrams/{diagramId}/codegen")
public class CodeGenController {

    private final CodeGenService codeGenService;

    public CodeGenController(CodeGenService codeGenService) {
        this.codeGenService = codeGenService;
    }

    @GetMapping("/backend")
    public ResponseEntity<byte[]> generateBackend(@PathVariable UUID diagramId) {
        byte[] zip = codeGenService.generateBackendZip(diagramId);
        String fileName = codeGenService.suggestedFileName(diagramId);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .body(zip);
    }
}
