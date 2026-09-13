package com.umlcollab.backend.xmi;

import com.umlcollab.backend.ai.DiagramOperationApplier;
import com.umlcollab.backend.dto.DiagramDetailDto;
import com.umlcollab.backend.dto.ai.AiCommandResponse;
import com.umlcollab.backend.dto.ai.DiagramOperation;
import com.umlcollab.backend.dto.ai.OperationType;
import com.umlcollab.backend.exception.BadRequestException;
import com.umlcollab.backend.model.DataType;
import com.umlcollab.backend.model.RelationshipType;
import com.umlcollab.backend.model.Visibility;
import com.umlcollab.backend.service.DiagramService;
import com.umlcollab.backend.websocket.DiagramBroadcastService;
import com.umlcollab.backend.websocket.DiagramEvent;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.*;

/**
 * Importa un archivo XMI 2.1 (por ejemplo, exportado desde Enterprise
 * Architect o desde este mismo backend) y lo convierte en operaciones sobre
 * el diagrama, reusando el mismo aplicador que usan el agente de IA y la
 * foto-a-diagrama: asi todo cambio, venga de donde venga, respeta las
 * mismas validaciones, la exclusion mutua y la difusion en tiempo real.
 */
@Service
public class XmiImportService {

    private static final String UC_NS = "http://umlcollab.com/xmi-ext";

    private final DiagramOperationApplier applier;
    private final DiagramService diagramService;
    private final DiagramBroadcastService broadcastService;

    public XmiImportService(DiagramOperationApplier applier, DiagramService diagramService, DiagramBroadcastService broadcastService) {
        this.applier = applier;
        this.diagramService = diagramService;
        this.broadcastService = broadcastService;
    }

    public AiCommandResponse importXmi(UUID diagramId, byte[] xmlBytes, UUID userId, String displayName) {
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xmlBytes));
            doc.getDocumentElement().normalize();

            Map<String, DataType> typesById = parsePrimitiveTypes(doc);
            Map<String, String> classNameById = new LinkedHashMap<>();
            List<Element> classElements = elementsByTagAndType(doc, "packagedElement", "uml:Class");
            for (Element classEl : classElements) {
                classNameById.put(classEl.getAttribute("xmi:id"), classEl.getAttribute("name"));
            }

            List<DiagramOperation> operations = new ArrayList<>();

            for (Element classEl : classElements) {
                String name = classEl.getAttribute("name");
                operations.add(DiagramOperation.builder()
                        .type(OperationType.CREATE_CLASS)
                        .newName(name)
                        .isAbstract(Boolean.parseBoolean(classEl.getAttribute("isAbstract")))
                        .x(parseDoubleOrNull(classEl.getAttributeNS(UC_NS, "x")))
                        .y(parseDoubleOrNull(classEl.getAttributeNS(UC_NS, "y")))
                        .build());

                for (Element attrEl : directChildren(classEl, "ownedAttribute")) {
                    String typeId = attrIdRef(attrEl);
                    DataType dataType = typesById.getOrDefault(typeId, DataType.STRING);
                    String lowerValue = childAttr(attrEl, "lowerValue", "value");
                    boolean nullable = !"1".equals(lowerValue); // lower=0 (o ausente) => opcional; lower=1 => obligatorio
                    operations.add(DiagramOperation.builder()
                            .type(OperationType.ADD_ATTRIBUTE)
                            .className(name)
                            .attributeName(attrEl.getAttribute("name"))
                            .dataType(dataType)
                            .visibility(XmiTypeMapper.fromXmiVisibility(attrEl.getAttribute("visibility")))
                            .isPrimaryKey(Boolean.parseBoolean(attrEl.getAttributeNS(UC_NS, "primaryKey")))
                            .nullable(nullable)
                            .unique(Boolean.parseBoolean(attrEl.getAttributeNS(UC_NS, "unique")))
                            .build());
                }

                for (Element genEl : directChildren(classEl, "generalization")) {
                    String generalId = genEl.getAttribute("general");
                    String targetName = classNameById.get(generalId);
                    if (targetName != null) {
                        operations.add(DiagramOperation.builder()
                                .type(OperationType.CREATE_RELATIONSHIP)
                                .sourceClassName(name)
                                .targetClassName(targetName)
                                .relationshipType(RelationshipType.GENERALIZATION)
                                .sourceMultiplicity("1")
                                .targetMultiplicity("1")
                                .build());
                    }
                }
            }

            for (Element assocEl : elementsByTagAndType(doc, "packagedElement", "uml:Association")) {
                List<Element> ends = directChildren(assocEl, "ownedEnd");
                if (ends.size() < 2) continue;
                Element end0 = ends.get(0);
                Element end1 = ends.get(1);

                String type0Id = end0.getAttribute("type");
                String type1Id = end1.getAttribute("type");
                String agg0 = end0.getAttribute("aggregation");
                String agg1 = end1.getAttribute("aggregation");

                String sourceId, targetId, sourceRole, targetRole, sourceMult, targetMult;
                RelationshipType relType;

                if (!agg0.isBlank() && !agg0.equals("none") && (agg1.isBlank() || agg1.equals("none"))) {
                    sourceId = type1Id; targetId = type0Id;
                    sourceRole = end1.getAttribute("name"); targetRole = end0.getAttribute("name");
                    sourceMult = readMultiplicity(end1); targetMult = readMultiplicity(end0);
                    relType = XmiTypeMapper.fromXmiAggregation(agg0);
                } else {
                    sourceId = type0Id; targetId = type1Id;
                    sourceRole = end0.getAttribute("name"); targetRole = end1.getAttribute("name");
                    sourceMult = readMultiplicity(end0); targetMult = readMultiplicity(end1);
                    relType = XmiTypeMapper.fromXmiAggregation(agg1);
                }

                String sourceName = classNameById.get(sourceId);
                String targetName = classNameById.get(targetId);
                if (sourceName == null || targetName == null) continue;

                operations.add(DiagramOperation.builder()
                        .type(OperationType.CREATE_RELATIONSHIP)
                        .sourceClassName(sourceName)
                        .targetClassName(targetName)
                        .relationshipType(relType)
                        .sourceMultiplicity(sourceMult)
                        .targetMultiplicity(targetMult)
                        .sourceRoleName(sourceRole.isBlank() ? null : sourceRole)
                        .targetRoleName(targetRole.isBlank() ? null : targetRole)
                        .build());
            }

            if (classElements.isEmpty()) {
                throw new BadRequestException("El XMI no tiene ninguna clase (packagedElement xmi:type=\"uml:Class\")");
            }

            List<DiagramOperation> applied = applier.apply(diagramId, operations, userId, displayName);
            DiagramDetailDto updated = diagramService.getDetail(diagramId);

            broadcastService.broadcast(diagramId, DiagramEvent.builder()
                    .type(DiagramEvent.DiagramEventType.DIAGRAM_REPLACED)
                    .actorUserId(userId)
                    .actorDisplayName(displayName)
                    .payload(updated)
                    .build());

            return AiCommandResponse.builder()
                    .assistantMessage("Se importaron " + classElements.size() + " clases desde el archivo XMI.")
                    .appliedOperations(applied)
                    .diagram(updated)
                    .build();
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("No se pudo importar el XMI: " + e.getMessage());
        }
    }

    private Map<String, DataType> parsePrimitiveTypes(Document doc) {
        Map<String, DataType> result = new LinkedHashMap<>();
        for (Element el : elementsByTagAndType(doc, "packagedElement", "uml:PrimitiveType")) {
            result.put(el.getAttribute("xmi:id"), XmiTypeMapper.fromXmiName(el.getAttribute("name")));
        }
        return result;
    }

    private List<Element> elementsByTagAndType(Document doc, String tag, String xmiType) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = doc.getElementsByTagName(tag);
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            if (xmiType.equals(el.getAttribute("xmi:type"))) {
                result.add(el);
            }
        }
        return result;
    }

    private List<Element> directChildren(Element parent, String tag) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tag.equals(node.getNodeName())) {
                result.add((Element) node);
            }
        }
        return result;
    }

    private String attrIdRef(Element attrEl) {
        for (Element typeEl : directChildren(attrEl, "type")) {
            String idref = typeEl.getAttribute("xmi:idref");
            if (!idref.isBlank()) return idref;
        }
        return null;
    }

    private String childAttr(Element parent, String childTag, String attrName) {
        List<Element> children = directChildren(parent, childTag);
        return children.isEmpty() ? null : children.get(0).getAttribute(attrName);
    }

    private String readMultiplicity(Element end) {
        String lower = childAttr(end, "lowerValue", "value");
        String upper = childAttr(end, "upperValue", "value");
        return XmiTypeMapper.toMultiplicityString(lower == null ? "1" : lower, upper == null ? "1" : upper);
    }

    private Double parseDoubleOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
