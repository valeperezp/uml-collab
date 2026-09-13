package com.umlcollab.backend.codegen;

import com.umlcollab.backend.dto.DiagramDetailDto;
import com.umlcollab.backend.service.DiagramService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Orquesta la generacion completa: arma el plan de todas las clases del
 * diagrama, renderiza el codigo Java de las 5 capas para cada una, y
 * empaqueta un proyecto Maven Spring Boot + Postgres listo para abrir en un
 * IDE y correr (mvn spring-boot:run), tal como pide el enunciado.
 */
@Service
public class CodeGenService {

    private final DiagramService diagramService;
    private final CodeGenPlanner planner;
    private final JavaSourceRenderer renderer;
    private final ProjectScaffoldRenderer scaffold;

    public CodeGenService(DiagramService diagramService, CodeGenPlanner planner,
                           JavaSourceRenderer renderer, ProjectScaffoldRenderer scaffold) {
        this.diagramService = diagramService;
        this.planner = planner;
        this.renderer = renderer;
        this.scaffold = scaffold;
    }

    public byte[] generateBackendZip(UUID diagramId) {
        DiagramDetailDto diagram = diagramService.getDetail(diagramId);
        List<ClassPlan> plans = planner.plan(diagram);

        String artifactId = sanitizeArtifactId(diagram.getName());
        String basePackage = "com.generated." + artifactId.replace("-", "");

        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
                String srcRoot = "src/main/java/" + basePackage.replace('.', '/') + "/";

                writeEntry(zip, "pom.xml", scaffold.pomXml(artifactId, basePackage));
                writeEntry(zip, "src/main/resources/application.properties", scaffold.applicationProperties(artifactId));
                writeEntry(zip, srcRoot + "Application.java", scaffold.applicationClass(basePackage, artifactId));
                writeEntry(zip, "README.md", scaffold.readme(diagram.getName(), plans));
                writeEntry(zip, ".gitignore", scaffold.gitignore());

                for (ClassPlan plan : plans) {
                    writeEntry(zip, srcRoot + "model/" + plan.className + ".java", renderer.renderEntity(plan, basePackage, plans));
                    writeEntry(zip, srcRoot + "dto/" + plan.className + "Dto.java", renderer.renderDto(plan, basePackage));
                    writeEntry(zip, srcRoot + "repository/" + plan.className + "Repository.java", renderer.renderRepository(plan, basePackage));
                    writeEntry(zip, srcRoot + "service/" + plan.className + "Service.java", renderer.renderService(plan, basePackage));
                    writeEntry(zip, srcRoot + "controller/" + plan.className + "Controller.java", renderer.renderController(plan, basePackage));
                }
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("No se pudo generar el proyecto: " + e.getMessage(), e);
        }
    }

    public String suggestedFileName(UUID diagramId) {
        DiagramDetailDto diagram = diagramService.getDetail(diagramId);
        return sanitizeArtifactId(diagram.getName()) + "-backend.zip";
    }

    private void writeEntry(ZipOutputStream zip, String path, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String sanitizeArtifactId(String name) {
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return slug.isBlank() ? "diagrama" : slug;
    }
}
