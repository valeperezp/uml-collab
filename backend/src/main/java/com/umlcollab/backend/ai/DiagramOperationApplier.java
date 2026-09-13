package com.umlcollab.backend.ai;

import com.umlcollab.backend.dto.ClassDto;
import com.umlcollab.backend.dto.RelationshipDto;
import com.umlcollab.backend.dto.ai.DiagramOperation;
import com.umlcollab.backend.dto.requests.AttributeRequest;
import com.umlcollab.backend.dto.requests.CreateClassRequest;
import com.umlcollab.backend.dto.requests.RelationshipRequest;
import com.umlcollab.backend.dto.requests.UpdateClassRequest;
import com.umlcollab.backend.exception.BadRequestException;
import com.umlcollab.backend.model.DataType;
import com.umlcollab.backend.model.UmlAttribute;
import com.umlcollab.backend.model.UmlClass;
import com.umlcollab.backend.model.Visibility;
import com.umlcollab.backend.service.DiagramService;
import com.umlcollab.backend.service.UmlClassService;
import com.umlcollab.backend.service.UmlRelationshipService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        Map<String, UUID> classIdsByName = new HashMap<>();
        diagramService.getEntity(diagramId).getClasses()
                .forEach(c -> classIdsByName.put(key(c.getName()), c.getId()));

        int createdSoFar = classIdsByName.size();

        for (DiagramOperation op : operations) {
            switch (op.getType()) {
                case CREATE_CLASS -> {
                    String name = firstNonBlank(op.getNewName(), op.getClassName());
                    if (name == null) throw new BadRequestException("CREATE_CLASS necesita un nombre de clase");
                    CreateClassRequest req = new CreateClassRequest();
                    req.setName(name);
                    req.setAbstract(Boolean.TRUE.equals(op.getIsAbstract()));
                    double[] pos = autoPosition(op, createdSoFar);
                    req.setX(pos[0]);
                    req.setY(pos[1]);
                    ClassDto created = classService.create(diagramId, req, userId, displayName);
                    classIdsByName.put(key(created.getName()), created.getId());
                    createdSoFar++;
                }
                case RENAME_CLASS -> {
                    UUID classId = resolveClassId(classIdsByName, op);
                    UpdateClassRequest req = new UpdateClassRequest();
                    req.setName(op.getNewName());
                    ClassDto updated = classService.update(diagramId, classId, req, userId, displayName);
                    classIdsByName.remove(key(op.getClassName()));
                    classIdsByName.put(key(updated.getName()), updated.getId());
                }
                case MOVE_CLASS -> {
                    UUID classId = resolveClassId(classIdsByName, op);
                    UpdateClassRequest req = new UpdateClassRequest();
                    req.setX(op.getX());
                    req.setY(op.getY());
                    classService.update(diagramId, classId, req, userId, displayName);
                }
                case DELETE_CLASS -> {
                    UUID classId = resolveClassId(classIdsByName, op);
                    classService.delete(diagramId, classId, userId, displayName);
                    classIdsByName.remove(key(op.getClassName()));
                }
                case ADD_ATTRIBUTE -> {
                    UUID classId = resolveClassId(classIdsByName, op);
                    if (op.getAttributeName() == null) throw new BadRequestException("ADD_ATTRIBUTE necesita attributeName");
                    AttributeRequest req = new AttributeRequest();
                    req.setName(op.getAttributeName());
                    req.setDataType(op.getDataType() == null ? DataType.STRING : op.getDataType());
                    req.setVisibility(op.getVisibility() == null ? Visibility.PRIVATE : op.getVisibility());
                    req.setPrimaryKey(Boolean.TRUE.equals(op.getIsPrimaryKey()));
                    req.setNullable(!Boolean.FALSE.equals(op.getNullable()));
                    req.setUnique(Boolean.TRUE.equals(op.getUnique()));
                    classService.addAttribute(diagramId, classId, req, userId, displayName);
                }
                case UPDATE_ATTRIBUTE -> {
                    UUID classId = resolveClassId(classIdsByName, op);
                    UmlClass umlClass = classService.getEntity(classId);
                    UmlAttribute attribute = findAttribute(umlClass, op.getAttributeName());
                    AttributeRequest req = new AttributeRequest();
                    req.setName(firstNonBlank(op.getNewAttributeName(), attribute.getName()));
                    req.setDataType(op.getDataType() == null ? attribute.getDataType() : op.getDataType());
                    req.setVisibility(op.getVisibility() == null ? attribute.getVisibility() : op.getVisibility());
                    req.setPrimaryKey(op.getIsPrimaryKey() == null ? attribute.isPrimaryKey() : op.getIsPrimaryKey());
                    req.setNullable(op.getNullable() == null ? attribute.isNullable() : op.getNullable());
                    req.setUnique(op.getUnique() == null ? attribute.isUnique() : op.getUnique());
                    classService.updateAttribute(diagramId, classId, attribute.getId(), req, userId, displayName);
                }
                case REMOVE_ATTRIBUTE -> {
                    UUID classId = resolveClassId(classIdsByName, op);
                    UmlClass umlClass = classService.getEntity(classId);
                    UmlAttribute attribute = findAttribute(umlClass, op.getAttributeName());
                    classService.removeAttribute(diagramId, classId, attribute.getId(), userId, displayName);
                }
                case CREATE_RELATIONSHIP -> {
                    UUID sourceId = classIdsByName.get(key(op.getSourceClassName()));
                    UUID targetId = classIdsByName.get(key(op.getTargetClassName()));
                    if (sourceId == null || targetId == null) {
                        throw new BadRequestException("No se encontraron las clases de la relacion: " +
                                op.getSourceClassName() + " -> " + op.getTargetClassName());
                    }
                    RelationshipRequest req = new RelationshipRequest();
                    req.setSourceClassId(sourceId);
                    req.setTargetClassId(targetId);
                    req.setType(op.getRelationshipType() == null ? com.umlcollab.backend.model.RelationshipType.ASSOCIATION : op.getRelationshipType());
                    req.setSourceMultiplicity(firstNonBlank(op.getSourceMultiplicity(), "1"));
                    req.setTargetMultiplicity(firstNonBlank(op.getTargetMultiplicity(), "1"));
                    req.setSourceRoleName(op.getSourceRoleName());
                    req.setTargetRoleName(op.getTargetRoleName());
                    relationshipService.create(diagramId, req, userId, displayName);
                }
                case UPDATE_RELATIONSHIP, DELETE_RELATIONSHIP -> {
                    UUID relationshipId = op.getRelationshipId() != null
                            ? op.getRelationshipId()
                            : resolveRelationshipId(diagramId, op);
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
                        relationshipService.update(diagramId, relationshipId, req, userId, displayName);
                    }
                }
            }
        }
        return operations;
    }

    private UUID resolveRelationshipId(UUID diagramId, DiagramOperation op) {
        List<RelationshipDto> relationships = diagramService.getDetail(diagramId).getRelationships();
        return relationships.stream()
                .filter(r -> matches(r.getSourceClassName(), op.getSourceClassName()) && matches(r.getTargetClassName(), op.getTargetClassName()))
                .map(RelationshipDto::getId)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("No se encontro la relacion " + op.getSourceClassName() + " -> " + op.getTargetClassName()));
    }

    private boolean matches(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private UUID resolveClassId(Map<String, UUID> classIdsByName, DiagramOperation op) {
        UUID id = op.getClassId() != null ? op.getClassId() : classIdsByName.get(key(op.getClassName()));
        if (id == null) {
            throw new BadRequestException("No se encontro la clase '" + op.getClassName() + "' en el diagrama");
        }
        return id;
    }

    private UmlAttribute findAttribute(UmlClass umlClass, String attributeName) {
        return umlClass.getAttributes().stream()
                .filter(a -> a.getName().equalsIgnoreCase(attributeName))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("La clase '" + umlClass.getName() + "' no tiene un atributo '" + attributeName + "'"));
    }

    private double[] autoPosition(DiagramOperation op, int index) {
        if (op.getX() != null && op.getY() != null) {
            return new double[]{op.getX(), op.getY()};
        }
        int col = index % 4;
        int row = index / 4;
        return new double[]{80 + col * 260, 80 + row * 220};
    }

    private String key(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }

    private String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }
}
