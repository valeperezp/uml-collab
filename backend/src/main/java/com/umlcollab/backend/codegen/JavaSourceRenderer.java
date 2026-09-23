package com.umlcollab.backend.codegen;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Genera el codigo fuente Java de las 5 capas (modelo/repositorio/servicio/
 * controlador + DTO) para una clase del diagrama, ya con las anotaciones de
 * JPA que le correspondan segun el plan calculado por {@link CodeGenPlanner}.
 */
@Component
public class JavaSourceRenderer {

    public String renderEntity(ClassPlan plan, String basePackage, List<ClassPlan> allPlans) {
        boolean isRoot = plan.superClassName == null;
        boolean isParent = allPlans.stream().anyMatch(p -> plan.className.equals(p.superClassName));

        TreeSet<String> imports = new TreeSet<>();
        imports.add("jakarta.persistence.*");
        imports.add("lombok.Getter");
        imports.add("lombok.Setter");
        imports.add("lombok.NoArgsConstructor");
        imports.add("lombok.AllArgsConstructor");
        for (FieldPlan f : plan.fields) {
            if (f.javaImport != null) imports.add(f.javaImport);
        }
        boolean hasCollection = plan.relations.stream().anyMatch(r -> r.collection);
        if (hasCollection) {
            imports.add("java.util.List");
            imports.add("java.util.ArrayList");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePackage).append(".model;\n\n");
        for (String imp : imports) sb.append("import ").append(imp).append(";\n");
        sb.append("\n");
        sb.append("@Entity\n");
        sb.append("@Table(name = \"").append(plan.tableName).append("\")\n");
        if (isRoot && isParent) {
            sb.append("@Inheritance(strategy = InheritanceType.JOINED)\n");
        }
        sb.append("@Getter\n@Setter\n@NoArgsConstructor\n@AllArgsConstructor\n");
        sb.append("public ").append(plan.isAbstract ? "abstract " : "").append("class ").append(plan.className);
        if (plan.superClassName != null) sb.append(" extends ").append(plan.superClassName);
        sb.append(" {\n\n");

        for (FieldPlan f : plan.fields) {
            if (f.isId) {
                if (isRoot) {
                    sb.append("    @Id\n");
                    if (f.generated || "Integer".equalsIgnoreCase(f.javaType) || "Long".equalsIgnoreCase(f.javaType) || "UUID".equalsIgnoreCase(f.javaType)) {
                        if ("UUID".equalsIgnoreCase(f.javaType)) {
                            sb.append("    @GeneratedValue(strategy = GenerationType.UUID)\n");
                        } else {
                            sb.append("    @GeneratedValue(strategy = GenerationType.IDENTITY)\n");
                        }
                    }
                    sb.append("    @Column(name = \"").append(NameUtils.snakeCase(f.name)).append("\")\n");
                    sb.append("    private ").append(f.javaType).append(" ").append(f.name).append(";\n\n");
                }
            } else {
                sb.append("    @Column(name = \"").append(NameUtils.snakeCase(f.name)).append("\"")
                        .append(", nullable = ").append(f.nullable)
                        .append(f.unique ? ", unique = true" : "")
                        .append(")\n");
                sb.append("    private ").append(f.javaType).append(" ").append(f.name).append(";\n\n");
            }
        }

        for (RelationPlan r : plan.relations) {
            sb.append(renderRelationField(r, basePackage));
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String renderRelationField(RelationPlan r, String basePackage) {
        StringBuilder sb = new StringBuilder();
        String targetType = basePackage + ".model." + r.targetClassName;
        switch (r.kind) {
            case MANY_TO_ONE -> {
                sb.append("    @ManyToOne(fetch = FetchType.LAZY)\n");
                sb.append("    @JoinColumn(name = \"").append(r.joinColumnName).append("\")\n");
                sb.append("    private ").append(r.targetClassName).append(" ").append(r.fieldName).append(";\n\n");
            }
            case ONE_TO_ONE -> {
                if (r.owningSide) {
                    sb.append("    @OneToOne(fetch = FetchType.LAZY").append(r.cascadeAll ? ", cascade = CascadeType.ALL, orphanRemoval = true" : "").append(")\n");
                    sb.append("    @JoinColumn(name = \"").append(r.joinColumnName).append("\")\n");
                } else {
                    sb.append("    @OneToOne(mappedBy = \"").append(r.mappedBy).append("\"").append(r.cascadeAll ? ", cascade = CascadeType.ALL, orphanRemoval = true" : "").append(")\n");
                }
                sb.append("    private ").append(r.targetClassName).append(" ").append(r.fieldName).append(";\n\n");
            }
            case ONE_TO_MANY -> {
                sb.append("    @OneToMany(mappedBy = \"").append(r.mappedBy).append("\"")
                        .append(r.cascadeAll ? ", cascade = CascadeType.ALL, orphanRemoval = true" : "")
                        .append(")\n");
                sb.append("    private List<").append(r.targetClassName).append("> ").append(r.fieldName).append(" = new ArrayList<>();\n\n");
            }
            case MANY_TO_MANY -> {
                if (r.owningSide) {
                    sb.append("    @ManyToMany\n");
                    sb.append("    @JoinTable(name = \"").append(r.joinTableName).append("\",\n");
                    sb.append("            joinColumns = @JoinColumn(name = \"").append(r.joinColumnSelf).append("\"),\n");
                    sb.append("            inverseJoinColumns = @JoinColumn(name = \"").append(r.joinColumnOther).append("\"))\n");
                } else {
                    sb.append("    @ManyToMany(mappedBy = \"").append(r.mappedBy).append("\")\n");
                }
                sb.append("    private List<").append(r.targetClassName).append("> ").append(r.fieldName).append(" = new ArrayList<>();\n\n");
            }
        }
        return sb.toString();
    }

    public String renderDto(ClassPlan plan, String basePackage, List<ClassPlan> allPlans) {
        TreeSet<String> imports = new TreeSet<>();
        imports.add("lombok.Getter");
        imports.add("lombok.Setter");
        imports.add("lombok.NoArgsConstructor");
        imports.add("lombok.AllArgsConstructor");
        for (FieldPlan f : plan.fields) if (f.javaImport != null) imports.add(f.javaImport);
        boolean hasCollectionRelation = plan.relations.stream().anyMatch(r -> r.collection);
        if (hasCollectionRelation) {
            imports.add("java.util.List");
            imports.add("java.util.ArrayList");
        }
        for (RelationPlan r : plan.relations) {
            if (r.targetIdImport != null) imports.add(r.targetIdImport);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePackage).append(".dto;\n\n");
        for (String imp : imports) sb.append("import ").append(imp).append(";\n");
        sb.append("\n");
        sb.append("/** DTO plano de ").append(plan.className).append(": expone solo los ids de las relaciones, nunca la entidad completa (evita ciclos de serializacion). */\n");
        sb.append("@Getter\n@Setter\n@NoArgsConstructor\n@AllArgsConstructor\n");
        sb.append("public class ").append(plan.className).append("Dto");
        if (plan.superClassName != null) {
            sb.append(" extends ").append(plan.superClassName).append("Dto");
        }
        sb.append(" {\n\n");

        for (FieldPlan f : plan.fields) {
            sb.append("    private ").append(f.javaType).append(" ").append(f.name).append(";\n");
        }
        for (RelationPlan r : plan.relations) {
            String type = r.targetIdType != null ? r.targetIdType : "UUID";
            if (r.collection) {
                sb.append("    private List<").append(type).append("> ").append(r.fieldName).append("Ids = new ArrayList<>();\n");
            } else {
                sb.append("    private ").append(type).append(" ").append(r.fieldName).append("Id;\n");
            }
        }
        sb.append("}\n");
        return sb.toString();
    }

    public String renderRepository(ClassPlan plan, String basePackage) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePackage).append(".repository;\n\n");
        sb.append("import ").append(basePackage).append(".model.").append(plan.className).append(";\n");
        sb.append("import org.springframework.data.jpa.repository.JpaRepository;\n");
        if (plan.idField != null && plan.idField.javaImport != null) {
            sb.append("import ").append(plan.idField.javaImport).append(";\n");
        }
        sb.append("\n");
        sb.append("public interface ").append(plan.className).append("Repository extends JpaRepository<")
                .append(plan.className).append(", ").append(plan.idField.javaType).append("> {\n");
        sb.append("}\n");
        return sb.toString();
    }

    public String renderService(ClassPlan plan, String basePackage, List<ClassPlan> allPlans) {
        List<FieldPlan> allFields = new ArrayList<>();
        ClassPlan curr = plan;
        while (curr != null) {
            allFields.addAll(curr.fields);
            String superName = curr.superClassName;
            curr = superName != null ? allPlans.stream().filter(p -> p.className.equals(superName)).findFirst().orElse(null) : null;
        }

        StringBuilder mapToEntity = new StringBuilder();
        StringBuilder mapToDto = new StringBuilder();
        for (FieldPlan f : allFields) {
            String cap = capitalize(f.name);
            if (!f.isId || !f.generated) {
                mapToEntity.append("        if (dto.get").append(cap).append("() != null) entity.set").append(cap).append("(dto.get").append(cap).append("());\n");
            }
            mapToDto.append("        dto.set").append(cap).append("(entity.get").append(cap).append("());\n");
        }
        List<RelationPlan> allRelations = new ArrayList<>();
        ClassPlan currRel = plan;
        while (currRel != null) {
            allRelations.addAll(currRel.relations);
            String superName = currRel.superClassName;
            currRel = superName != null ? allPlans.stream().filter(p -> p.className.equals(superName)).findFirst().orElse(null) : null;
        }

        StringBuilder relationHandling = new StringBuilder();
        for (RelationPlan r : allRelations) {
            String cap = capitalize(r.fieldName);
            String getter = r.targetIdGetter != null ? r.targetIdGetter : "getId";
            if (!r.collection && r.owningSide) {
                relationHandling.append("        // TODO: resolver y setear ").append(r.fieldName)
                        .append(" a partir de dto.get").append(cap).append("Id() usando su repositorio si corresponde\n");
            }
            if (!r.collection) {
                mapToDto.append("        if (entity.get").append(cap).append("() != null) dto.set").append(cap)
                        .append("Id(entity.get").append(cap).append("().").append(getter).append("());\n");
            } else {
                mapToDto.append("        if (entity.get").append(cap).append("() != null) entity.get").append(cap)
                        .append("().forEach(x -> dto.get").append(cap).append("Ids().add(x.").append(getter).append("()));\n");
            }
        }

        String cn = plan.className;
        String idType = plan.idField.javaType;
        TreeSet<String> imports = new TreeSet<>();
        imports.add(basePackage + ".dto." + cn + "Dto");
        imports.add(basePackage + ".model." + cn);
        imports.add(basePackage + ".repository." + cn + "Repository");
        imports.add("org.springframework.stereotype.Service");
        imports.add("org.springframework.transaction.annotation.Transactional");
        imports.add("java.util.List");
        imports.add("java.util.NoSuchElementException");
        if (plan.idField != null && plan.idField.javaImport != null) {
            imports.add(plan.idField.javaImport);
        }
        for (RelationPlan r : allRelations) {
            if (r.targetIdImport != null) {
                imports.add(r.targetIdImport);
            }
        }

        StringBuilder out = new StringBuilder();
        out.append("package ").append(basePackage).append(".service;\n\n");
        for (String imp : imports) out.append("import ").append(imp).append(";\n");
        out.append("\n");
        out.append("@Service\n");
        out.append("public class ").append(cn).append("Service {\n\n");
        out.append("    private final ").append(cn).append("Repository repository;\n\n");
        out.append("    public ").append(cn).append("Service(").append(cn).append("Repository repository) {\n");
        out.append("        this.repository = repository;\n    }\n\n");

        out.append("    @Transactional(readOnly = true)\n");
        out.append("    public List<").append(cn).append("Dto> findAll() {\n");
        out.append("        return repository.findAll().stream().map(this::toDto).toList();\n    }\n\n");

        out.append("    @Transactional(readOnly = true)\n");
        out.append("    public ").append(cn).append("Dto findById(").append(idType).append(" id) {\n");
        out.append("        return toDto(getEntityOrThrow(id));\n    }\n\n");

        out.append("    @Transactional\n");
        out.append("    public ").append(cn).append("Dto create(").append(cn).append("Dto dto) {\n");
        out.append("        ").append(cn).append(" entity = new ").append(cn).append("();\n");
        out.append("        toEntity(dto, entity);\n");
        out.append(relationHandling);
        out.append("        entity = repository.save(entity);\n");
        out.append("        return toDto(entity);\n    }\n\n");

        out.append("    @Transactional\n");
        out.append("    public ").append(cn).append("Dto update(").append(idType).append(" id, ").append(cn).append("Dto dto) {\n");
        out.append("        ").append(cn).append(" entity = getEntityOrThrow(id);\n");
        out.append("        toEntity(dto, entity);\n");
        out.append(relationHandling);
        out.append("        entity = repository.save(entity);\n");
        out.append("        return toDto(entity);\n    }\n\n");

        out.append("    @Transactional\n");
        out.append("    public void delete(").append(idType).append(" id) {\n");
        out.append("        repository.deleteById(id);\n    }\n\n");

        out.append("    private ").append(cn).append(" getEntityOrThrow(").append(idType).append(" id) {\n");
        out.append("        return repository.findById(id)\n");
        out.append("                .orElseThrow(() -> new NoSuchElementException(\"").append(cn).append(" no encontrado: \" + id));\n    }\n\n");

        out.append("    private void toEntity(").append(cn).append("Dto dto, ").append(cn).append(" entity) {\n");
        out.append(mapToEntity);
        out.append("    }\n\n");

        out.append("    private ").append(cn).append("Dto toDto(").append(cn).append(" entity) {\n");
        out.append("        ").append(cn).append("Dto dto = new ").append(cn).append("Dto();\n");
        out.append(mapToDto);
        out.append("        return dto;\n    }\n");
        out.append("}\n");
        return out.toString();
    }

    public String renderController(ClassPlan plan, String basePackage) {
        String cn = plan.className;
        String idType = plan.idField.javaType;
        TreeSet<String> imports = new TreeSet<>();
        imports.add(basePackage + ".dto." + cn + "Dto");
        imports.add(basePackage + ".service." + cn + "Service");
        imports.add("org.springframework.http.ResponseEntity");
        imports.add("org.springframework.web.bind.annotation.*");
        imports.add("java.util.List");
        if (plan.idField != null && plan.idField.javaImport != null) {
            imports.add(plan.idField.javaImport);
        }

        StringBuilder out = new StringBuilder();
        out.append("package ").append(basePackage).append(".controller;\n\n");
        for (String imp : imports) out.append("import ").append(imp).append(";\n");
        out.append("\n");
        out.append("@RestController\n");
        out.append("@RequestMapping(\"").append(plan.restBasePath()).append("\")\n");
        out.append("public class ").append(cn).append("Controller {\n\n");
        out.append("    private final ").append(cn).append("Service service;\n\n");
        out.append("    public ").append(cn).append("Controller(").append(cn).append("Service service) {\n");
        out.append("        this.service = service;\n    }\n\n");

        out.append("    @GetMapping\n");
        out.append("    public ResponseEntity<List<").append(cn).append("Dto>> findAll() {\n");
        out.append("        return ResponseEntity.ok(service.findAll());\n    }\n\n");

        out.append("    @GetMapping(\"/{id}\")\n");
        out.append("    public ResponseEntity<").append(cn).append("Dto> findById(@PathVariable ").append(idType).append(" id) {\n");
        out.append("        return ResponseEntity.ok(service.findById(id));\n    }\n\n");

        out.append("    @PostMapping\n");
        out.append("    public ResponseEntity<").append(cn).append("Dto> create(@RequestBody ").append(cn).append("Dto dto) {\n");
        out.append("        return ResponseEntity.ok(service.create(dto));\n    }\n\n");

        out.append("    @PutMapping(\"/{id}\")\n");
        out.append("    public ResponseEntity<").append(cn).append("Dto> update(@PathVariable ").append(idType)
                .append(" id, @RequestBody ").append(cn).append("Dto dto) {\n");
        out.append("        return ResponseEntity.ok(service.update(id, dto));\n    }\n\n");

        out.append("    @DeleteMapping(\"/{id}\")\n");
        out.append("    public ResponseEntity<Void> delete(@PathVariable ").append(idType).append(" id) {\n");
        out.append("        service.delete(id);\n");
        out.append("        return ResponseEntity.noContent().build();\n    }\n");
        out.append("}\n");
        return out.toString();
    }

    private String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
