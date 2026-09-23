package com.umlcollab.backend.codegen;

import com.umlcollab.backend.dto.AttributeDto;
import com.umlcollab.backend.dto.ClassDto;
import com.umlcollab.backend.dto.DiagramDetailDto;
import com.umlcollab.backend.dto.RelationshipDto;
import com.umlcollab.backend.model.DataType;
import com.umlcollab.backend.model.RelationshipType;
import com.umlcollab.backend.service.DiagramService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CodeGenServiceTest {

    @Test
    void testCodeGenWithParallelAndRecursiveRelationships(@TempDir Path tempDir) throws Exception {
        DiagramDetailDto diagram = new DiagramDetailDto();
        UUID diagramId = UUID.randomUUID();
        diagram.setId(diagramId);
        diagram.setName("SistemaVentas");

        // Clases: Persona, NotaVenta, Empleado (recursiva)
        UUID personaId = UUID.randomUUID();
        ClassDto persona = new ClassDto();
        persona.setId(personaId);
        persona.setName("Persona");
        AttributeDto ci = new AttributeDto();
        ci.setName("ci");
        ci.setDataType(DataType.STRING);
        ci.setPrimaryKey(true);

        AttributeDto nombre = new AttributeDto();
        nombre.setName("nombre");
        nombre.setDataType(DataType.STRING);
        persona.setAttributes(new java.util.ArrayList<>(List.of(ci, nombre)));

        UUID notaVentaId = UUID.randomUUID();
        ClassDto notaVenta = new ClassDto();
        notaVenta.setId(notaVentaId);
        notaVenta.setName("NotaVenta");
        AttributeDto nro = new AttributeDto();
        nro.setName("nro");
        nro.setDataType(DataType.INTEGER);
        nro.setPrimaryKey(true);

        AttributeDto monto = new AttributeDto();
        monto.setName("total");
        monto.setDataType(DataType.DOUBLE);
        notaVenta.setAttributes(new java.util.ArrayList<>(List.of(nro, monto)));

        UUID empleadoId = UUID.randomUUID();
        ClassDto empleado = new ClassDto();
        empleado.setId(empleadoId);
        empleado.setName("Empleado");
        AttributeDto empCod = new AttributeDto();
        empCod.setName("codigo");
        empCod.setDataType(DataType.STRING);
        empCod.setPrimaryKey(true);
        empleado.setAttributes(new java.util.ArrayList<>(List.of(empCod)));

        diagram.setClasses(List.of(persona, notaVenta, empleado));

        // Relacion 1: Persona (1) -> NotaVenta (1..*) con rol "vende"
        RelationshipDto relVende = new RelationshipDto();
        relVende.setId(UUID.randomUUID());
        relVende.setSourceClassId(personaId);
        relVende.setSourceClassName("Persona");
        relVende.setTargetClassId(notaVentaId);
        relVende.setTargetClassName("NotaVenta");
        relVende.setSourceMultiplicity("1");
        relVende.setTargetMultiplicity("1..*");
        relVende.setType(RelationshipType.ASSOCIATION);
        relVende.setLabel("vende");

        // Relacion 2: Persona (1) -> NotaVenta (1..*) con rol "compra" (Paralela!)
        RelationshipDto relCompra = new RelationshipDto();
        relCompra.setId(UUID.randomUUID());
        relCompra.setSourceClassId(personaId);
        relCompra.setSourceClassName("Persona");
        relCompra.setTargetClassId(notaVentaId);
        relCompra.setTargetClassName("NotaVenta");
        relCompra.setSourceMultiplicity("1");
        relCompra.setTargetMultiplicity("1..*");
        relCompra.setType(RelationshipType.ASSOCIATION);
        relCompra.setLabel("compra");

        // Relacion 3: Empleado (1) -> Empleado (0..*) Recursiva
        RelationshipDto relRecursiva = new RelationshipDto();
        relRecursiva.setId(UUID.randomUUID());
        relRecursiva.setSourceClassId(empleadoId);
        relRecursiva.setSourceClassName("Empleado");
        relRecursiva.setTargetClassId(empleadoId);
        relRecursiva.setTargetClassName("Empleado");
        relRecursiva.setSourceMultiplicity("1");
        relRecursiva.setTargetMultiplicity("0..*");
        relRecursiva.setType(RelationshipType.ASSOCIATION);
        relRecursiva.setLabel("supervisa");

        diagram.setRelationships(List.of(relVende, relCompra, relRecursiva));

        DiagramService diagramService = mock(DiagramService.class);
        when(diagramService.getDetail(diagramId)).thenReturn(diagram);

        CodeGenPlanner planner = new CodeGenPlanner();
        JavaSourceRenderer renderer = new JavaSourceRenderer();
        ProjectScaffoldRenderer scaffold = new ProjectScaffoldRenderer();
        CodeGenService codeGenService = new CodeGenService(diagramService, planner, renderer, scaffold);

        byte[] zipBytes = codeGenService.generateBackendZip(diagramId);
        assertNotNull(zipBytes);
        assertTrue(zipBytes.length > 0);

        // Descomprimir el ZIP en tempDir para verificar archivos generados
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File file = tempDir.resolve(entry.getName()).toFile();
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    file.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        zis.transferTo(fos);
                    }
                }
                zis.closeEntry();
            }
        }

        // Verificar existencia de archivos clave
        assertTrue(tempDir.resolve("pom.xml").toFile().exists());
        assertTrue(tempDir.resolve("README.md").toFile().exists());
        assertTrue(tempDir.resolve("src/main/resources/application.properties").toFile().exists());
        assertTrue(tempDir.resolve("src/main/java/com/generated/sistemaventas/Application.java").toFile().exists());

        Path personaModel = tempDir.resolve("src/main/java/com/generated/sistemaventas/model/Persona.java");
        Path notaVentaModel = tempDir.resolve("src/main/java/com/generated/sistemaventas/model/NotaVenta.java");
        Path empleadoModel = tempDir.resolve("src/main/java/com/generated/sistemaventas/model/Empleado.java");

        assertTrue(personaModel.toFile().exists());
        assertTrue(notaVentaModel.toFile().exists());
        assertTrue(empleadoModel.toFile().exists());

        String personaCode = java.nio.file.Files.readString(personaModel);
        String notaVentaCode = java.nio.file.Files.readString(notaVentaModel);
        String empleadoCode = java.nio.file.Files.readString(empleadoModel);

        // Validar que NotaVenta tiene ambos ManyToOne paralelos bien nombrados
        assertTrue(notaVentaCode.contains("private Persona vende;"), "NotaVenta debe tener campo vende");
        assertTrue(notaVentaCode.contains("private Persona compra;"), "NotaVenta debe tener campo compra");
        assertTrue(notaVentaCode.contains("@JoinColumn(name = \"vende_id\")"));
        assertTrue(notaVentaCode.contains("@JoinColumn(name = \"compra_id\")"));

        // Validar que Persona tiene las colecciones OneToMany correspondientes
        assertTrue(personaCode.contains("private List<NotaVenta>"), "Persona debe tener colecciones de NotaVenta");

        // Validar que Empleado tiene relacion recursiva (auto-referencia)
        assertTrue(empleadoCode.contains("private Empleado supervisa;"), "Empleado debe tener self-reference ManyToOne");
        assertTrue(empleadoCode.contains("private List<Empleado>"), "Empleado debe tener self-reference OneToMany");

        // Guardar una copia en target/test-generated-backend para inspeccion
        Path sampleExportDir = Path.of("target/test-generated-backend");
        sampleExportDir.toFile().mkdirs();
        try (ZipInputStream zis2 = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis2.getNextEntry()) != null) {
                File file = sampleExportDir.resolve(entry.getName()).toFile();
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    file.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        zis2.transferTo(fos);
                    }
                }
                zis2.closeEntry();
            }
        }
    }
}
