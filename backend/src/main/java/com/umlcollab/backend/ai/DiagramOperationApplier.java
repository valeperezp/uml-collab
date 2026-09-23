package com.umlcollab.backend.ai;

import com.umlcollab.backend.dto.ClassDto;
import com.umlcollab.backend.dto.RelationshipDto;
import com.umlcollab.backend.dto.ai.DiagramOperation;
import com.umlcollab.backend.dto.requests.AttributeRequest;
import com.umlcollab.backend.dto.requests.CreateClassRequest;
import com.umlcollab.backend.dto.requests.RelationshipRequest;
import com.umlcollab.backend.dto.requests.UpdateClassRequest;
import com.umlcollab.backend.model.DataType;
import com.umlcollab.backend.model.UmlAttribute;
import com.umlcollab.backend.model.UmlClass;
import com.umlcollab.backend.model.Visibility;
import com.umlcollab.backend.service.DiagramService;
import com.umlcollab.backend.service.UmlClassService;
import com.umlcollab.backend.service.UmlRelationshipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Traduce operaciones estructuradas (vengan del agente de IA o de cualquier
 * otro flujo, como la extraccion desde foto o la importacion XMI) en
 * llamadas reales sobre los servicios del diagrama. Es el unico camino que
 * existe para tocar el modelo por fuera del editor clasico, asi que
 * respeta exactamente las mismas reglas: validaciones, exclusion mutua y
 * difusion por WebSocket a todos los colaboradores conectados.
 */
@Component
public class DiagramOperationApplier {

    private static final Logger log = LoggerFactory.getLogger(DiagramOperationApplier.class);

    private static final Set<String> PRIMITIVE_TYPES = Set.of(
            "BOOLEAN", "BOOL", "BIT", "STRING", "STR", "VARCHAR", "CHAR", "TEXT",
            "INTEGER", "INT", "SMALLINT", "TINYINT", "LONG", "BIGINT", "DOUBLE",
            "FLOAT", "DECIMAL", "DATE", "DATETIME", "TIMESTAMP", "TIME", "UUID", "VOID", "OBJECT"
    );

    private final DiagramService diagramService;
    private final UmlClassService classService;
    private final UmlRelationshipService relationshipService;

    public DiagramOperationApplier(DiagramService diagramService, UmlClassService classService,
                                    UmlRelationshipService relationshipService) {
        this.diagramService = diagramService;
        this.classService = classService;
        this.relationshipService = relationshipService;
    }

    @Transactional
    public List<DiagramOperation> apply(UUID diagramId, List<DiagramOperation> operations, UUID userId, String displayName) {
        if (operations == null || operations.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, UUID> classIdsByName = new HashMap<>();
        diagramService.getDetail(diagramId).getClasses()
                .forEach(c -> classIdsByName.put(key(c.getName()), c.getId()));

        int createdSoFar = classIdsByName.size();
        String lastCreatedClassName = null;
        UUID lastCreatedClassId = null;

        List<DiagramOperation> successfulOps = new ArrayList<>();

        for (DiagramOperation op : operations) {
            if (op == null || op.getType() == null) continue;

            try {
                switch (op.getType()) {
                    case CREATE_CLASS -> {
                        String name = firstNonBlank(op.getClassName(), op.getNewName());
                        if (name == null || isPrimitiveType(name)) {
                            // Si no trajo nombre o confundio un tipo primitivo como clase
                            if (op.getRationale() != null && !op.getRationale().isBlank()) {
                                name = sanitizeClassName(op.getRationale());
                            } else {
                                name = "Clase" + (createdSoFar + 1);
                            }
                        }

                        UUID existingId = findClassIdFuzzy(classIdsByName, name);
                        if (existingId != null) {
                            lastCreatedClassId = existingId;
                            lastCreatedClassName = name;
                            successfulOps.add(op);
                            break;
                        }

                        CreateClassRequest req = new CreateClassRequest();
                        req.setName(name);
                        req.setAbstract(Boolean.TRUE.equals(op.getIsAbstract()));
                        double[] pos = autoPosition(op, createdSoFar);
                        req.setX(pos[0]);
                        req.setY(pos[1]);
                        ClassDto created = classService.create(diagramId, req, userId, displayName);
                        classIdsByName.put(key(created.getName()), created.getId());
                        lastCreatedClassId = created.getId();
                        lastCreatedClassName = created.getName();
                        createdSoFar++;
                        successfulOps.add(op);
                    }
                    case RENAME_CLASS -> {
                        UUID classId = resolveClassId(diagramId, classIdsByName, op.getClassName(), lastCreatedClassId, userId, displayName, createdSoFar);
                        if (classId == null || op.getNewName() == null || op.getNewName().isBlank()) break;
                        UpdateClassRequest req = new UpdateClassRequest();
                        req.setName(op.getNewName());
                        ClassDto updated = classService.update(diagramId, classId, req, userId, displayName);
                        classIdsByName.remove(key(op.getClassName()));
                        classIdsByName.put(key(updated.getName()), updated.getId());
                        lastCreatedClassId = updated.getId();
                        lastCreatedClassName = updated.getName();
                        successfulOps.add(op);
                    }
                    case MOVE_CLASS -> {
                        UUID classId = resolveClassId(diagramId, classIdsByName, op.getClassName(), lastCreatedClassId, userId, displayName, createdSoFar);
                        if (classId == null) break;
                        UpdateClassRequest req = new UpdateClassRequest();
                        req.setX(op.getX());
                        req.setY(op.getY());
                        classService.update(diagramId, classId, req, userId, displayName);
                        successfulOps.add(op);
                    }
                    case DELETE_CLASS -> {
                        UUID classId = resolveClassId(diagramId, classIdsByName, op.getClassName(), null, userId, displayName, createdSoFar);
                        if (classId == null) break;
                        classService.delete(diagramId, classId, userId, displayName);
                        classIdsByName.remove(key(op.getClassName()));
                        successfulOps.add(op);
                    }
                    case ADD_ATTRIBUTE -> {
                        String targetClassName = op.getClassName();

                        // Si el modelo puso un tipo primitivo en className (ej: className: "BOOLEAN", attributeName: "activo")
                        if (isPrimitiveType(targetClassName)) {
                            if (op.getDataType() == null || op.getDataType() == DataType.STRING) {
                                op.setDataType(DataType.from(targetClassName));
                            }
                            targetClassName = lastCreatedClassName;
                        }

                        if (targetClassName == null || targetClassName.isBlank()) {
                            targetClassName = lastCreatedClassName;
                        }

                        UUID classId = resolveClassId(diagramId, classIdsByName, targetClassName, lastCreatedClassId, userId, displayName, createdSoFar);
                        if (classId == null) {
                            log.warn("No se pudo determinar la clase para el atributo: {}", op.getAttributeName());
                            break;
                        }

                        String rawAttrName = firstNonBlank(op.getAttributeName(), op.getNewAttributeName());
                        final String attrName = (rawAttrName == null || rawAttrName.isBlank())
                                ? "campo" + (System.currentTimeMillis() % 1000)
                                : rawAttrName.trim();

                        UmlClass umlClass = classService.getEntity(classId);
                        boolean attrExists = umlClass.getAttributes().stream()
                                .anyMatch(a -> a.getName().equalsIgnoreCase(attrName));
                        if (attrExists) {
                            break;
                        }

                        AttributeRequest req = new AttributeRequest();
                        req.setName(attrName);
                        req.setDataType(op.getDataType() == null ? DataType.STRING : op.getDataType());
                        req.setVisibility(op.getVisibility() == null ? Visibility.PRIVATE : op.getVisibility());
                        req.setPrimaryKey(Boolean.TRUE.equals(op.getIsPrimaryKey()));
                        req.setNullable(!Boolean.FALSE.equals(op.getNullable()));
                        req.setUnique(Boolean.TRUE.equals(op.getUnique()));
                        classService.addAttribute(diagramId, classId, req, userId, displayName);
                        successfulOps.add(op);
                    }
                    case UPDATE_ATTRIBUTE -> {
                        UUID classId = resolveClassId(diagramId, classIdsByName, op.getClassName(), lastCreatedClassId, userId, displayName, createdSoFar);
                        if (classId == null || op.getAttributeName() == null) break;
                        UmlClass umlClass = classService.getEntity(classId);
                        UmlAttribute attribute = findAttribute(umlClass, op.getAttributeName());
                        if (attribute == null) break;

                        AttributeRequest req = new AttributeRequest();
                        req.setName(firstNonBlank(op.getNewAttributeName(), attribute.getName()));
                        req.setDataType(op.getDataType() == null ? attribute.getDataType() : op.getDataType());
                        req.setVisibility(op.getVisibility() == null ? attribute.getVisibility() : op.getVisibility());
                        req.setPrimaryKey(op.getIsPrimaryKey() == null ? attribute.isPrimaryKey() : op.getIsPrimaryKey());
                        req.setNullable(op.getNullable() == null ? attribute.isNullable() : op.getNullable());
                        req.setUnique(op.getUnique() == null ? attribute.isUnique() : op.getUnique());
                        classService.updateAttribute(diagramId, classId, attribute.getId(), req, userId, displayName);
                        successfulOps.add(op);
                    }
                    case REMOVE_ATTRIBUTE -> {
                        UUID classId = resolveClassId(diagramId, classIdsByName, op.getClassName(), lastCreatedClassId, userId, displayName, createdSoFar);
                        if (classId == null || op.getAttributeName() == null) break;
                        UmlClass umlClass = classService.getEntity(classId);
                        UmlAttribute attribute = findAttribute(umlClass, op.getAttributeName());
                        if (attribute == null) break;
                        classService.removeAttribute(diagramId, classId, attribute.getId(), userId, displayName);
                        successfulOps.add(op);
                    }
                    case CREATE_RELATIONSHIP -> {
                        String srcName = op.getSourceClassName();
                        String tgtName = op.getTargetClassName();

                        // Omitir si alguno de los extremos es un tipo primitivo
                        if (isPrimitiveType(srcName) || isPrimitiveType(tgtName)) {
                            log.info("Omitiendo relacion con tipo primitivo: {} -> {}", srcName, tgtName);
                            break;
                        }

                        UUID sourceId = resolveClassId(diagramId, classIdsByName, srcName, null, userId, displayName, createdSoFar);
                        if (sourceId != null && !classIdsByName.containsValue(sourceId)) {
                            classIdsByName.put(key(srcName), sourceId);
                            createdSoFar++;
                        }

                        UUID targetId = resolveClassId(diagramId, classIdsByName, tgtName, null, userId, displayName, createdSoFar);
                        if (targetId != null && !classIdsByName.containsValue(targetId)) {
                            classIdsByName.put(key(tgtName), targetId);
                            createdSoFar++;
                        }

                        if (sourceId == null || targetId == null) {
                            log.warn("No se pudieron resolver las clases para la relacion: {} -> {}", srcName, tgtName);
                            break;
                        }

                        RelationshipRequest req = new RelationshipRequest();
                        req.setSourceClassId(sourceId);
                        req.setTargetClassId(targetId);
                        req.setType(op.getRelationshipType() == null ? com.umlcollab.backend.model.RelationshipType.ASSOCIATION : op.getRelationshipType());
                        req.setSourceMultiplicity(firstNonBlank(op.getSourceMultiplicity(), "1"));
                        req.setTargetMultiplicity(firstNonBlank(op.getTargetMultiplicity(), "1"));
                        req.setSourceRoleName(op.getSourceRoleName());
                        req.setTargetRoleName(op.getTargetRoleName());
                        req.setLabel(firstNonBlank(op.getLabel(), op.getSourceRoleName(), op.getTargetRoleName()));
                        relationshipService.create(diagramId, req, userId, displayName);
                        successfulOps.add(op);
                    }
                    case UPDATE_RELATIONSHIP, DELETE_RELATIONSHIP -> {
                        UUID relationshipId = op.getRelationshipId() != null
                                ? op.getRelationshipId()
                                : resolveRelationshipId(diagramId, op);
                        if (relationshipId == null) break;

                        if (op.getType() == com.umlcollab.backend.dto.ai.OperationType.DELETE_RELATIONSHIP) {
                            relationshipService.delete(diagramId, relationshipId, userId, displayName);
                        } else {
                            RelationshipRequest req = new RelationshipRequest();
                            req.setSourceClassId(null);
                            req.setTargetClassId(null);
                            req.setType(op.getRelationshipType());
                            req.setSourceMultiplicity(op.getSourceMultiplicity());
                            req.setTargetMultiplicity(op.getTargetMultiplicity());
                            req.setSourceRoleName(op.getSourceRoleName());
                            req.setTargetRoleName(op.getTargetRoleName());
                            req.setLabel(op.getLabel());
                            relationshipService.update(diagramId, relationshipId, req, userId, displayName);
                        }
                        successfulOps.add(op);
                    }
                }
            } catch (Exception ex) {
                log.warn("Error aplicando operacion individual {} ({}): {}", op.getType(), op.getClassName(), ex.getMessage());
            }
        }
        return successfulOps;
    }

    private UUID resolveClassId(UUID diagramId, Map<String, UUID> classIdsByName, String className,
                                 UUID fallbackId, UUID userId, String displayName, int createdSoFar) {
        if (className != null && !className.isBlank()) {
            UUID id = findClassIdFuzzy(classIdsByName, className);
            if (id != null) return id;

            // Si es un nombre valido y no es tipo primitivo, auto-crear la clase para no perder atributos/relaciones
            if (!isPrimitiveType(className) && isValidClassName(className)) {
                try {
                    CreateClassRequest req = new CreateClassRequest();
                    req.setName(capitalize(className.trim()));
                    double[] pos = autoPosition(new DiagramOperation(), createdSoFar);
                    req.setX(pos[0]);
                    req.setY(pos[1]);
                    ClassDto created = classService.create(diagramId, req, userId, displayName);
                    classIdsByName.put(key(created.getName()), created.getId());
                    return created.getId();
                } catch (Exception e) {
                    log.warn("No se pudo auto-crear la clase {}: {}", className, e.getMessage());
                }
            }
        }
        return fallbackId;
    }

    private UUID findClassIdFuzzy(Map<String, UUID> classIdsByName, String name) {
        if (name == null || name.isBlank()) return null;
        String k = key(name);
        if (classIdsByName.containsKey(k)) {
            return classIdsByName.get(k);
        }
        String clean = cleanKey(name);
        for (Map.Entry<String, UUID> entry : classIdsByName.entrySet()) {
            if (cleanKey(entry.getKey()).equals(clean)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean isPrimitiveType(String name) {
        if (name == null) return false;
        return PRIMITIVE_TYPES.contains(name.trim().toUpperCase());
    }

    private boolean isValidClassName(String name) {
        if (name == null || name.isBlank()) return false;
        String trimmed = name.trim();
        return trimmed.length() >= 2 && trimmed.length() <= 50 && Character.isLetter(trimmed.charAt(0));
    }

    private String capitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private String sanitizeClassName(String text) {
        String cleaned = text.replaceAll("[^a-zA-Z0-9_]", "");
        return cleaned.isBlank() ? "Clase" : capitalize(cleaned);
    }

    private UUID resolveRelationshipId(UUID diagramId, DiagramOperation op) {
        List<RelationshipDto> relationships = diagramService.getDetail(diagramId).getRelationships();
        if (op.getLabel() != null && !op.getLabel().isBlank()) {
            Optional<RelationshipDto> matchByLabel = relationships.stream()
                    .filter(r -> (matches(r.getSourceClassName(), op.getSourceClassName()) && matches(r.getTargetClassName(), op.getTargetClassName()))
                              || (matches(r.getSourceClassName(), op.getTargetClassName()) && matches(r.getTargetClassName(), op.getSourceClassName())))
                    .filter(r -> op.getLabel().equalsIgnoreCase(r.getLabel()))
                    .findFirst();
            if (matchByLabel.isPresent()) return matchByLabel.get().getId();
        }
        return relationships.stream()
                .filter(r -> (matches(r.getSourceClassName(), op.getSourceClassName()) && matches(r.getTargetClassName(), op.getTargetClassName()))
                          || (matches(r.getSourceClassName(), op.getTargetClassName()) && matches(r.getTargetClassName(), op.getSourceClassName())))
                .map(RelationshipDto::getId)
                .findFirst()
                .orElse(null);
    }

    private boolean matches(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private UmlAttribute findAttribute(UmlClass umlClass, String attributeName) {
        if (attributeName == null) return null;
        return umlClass.getAttributes().stream()
                .filter(a -> a.getName().equalsIgnoreCase(attributeName))
                .findFirst()
                .orElse(null);
    }

    private double[] autoPosition(DiagramOperation op, int index) {
        if (op.getX() != null && op.getY() != null && op.getX() > 0 && op.getY() > 0) {
            return new double[]{op.getX(), op.getY()};
        }
        int col = index % 4;
        int row = index / 4;
        return new double[]{80 + col * 260, 80 + row * 220};
    }

    private String key(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }

    private String cleanKey(String name) {
        return name == null ? "" : name.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
