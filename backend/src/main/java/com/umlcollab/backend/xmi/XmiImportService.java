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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.*;

/**
 * Importa archivos XMI tanto en formato XMI 1.1 (UML 1.3 / Enterprise Architect nativo)
 * como en formato XMI 2.1 (UML 2.1 estándar).
 * Extrae todas las clases, atributos, visibilidades, claves primarias, relaciones
 * y las coordenadas del diagrama visual para recrear el diagrama colaborativo con 100% de fidelidad.
 */
@Service
public class XmiImportService {

    private static final Logger log = LoggerFactory.getLogger(XmiImportService.class);
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
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xmlBytes));
            doc.getDocumentElement().normalize();

            // 1. Mapa de geometrias de diagramas visuales (XMI 1.1 <UML:DiagramElement> y XMI 2.1 <element geometry="...">)
            Map<String, double[]> diagramGeometries = parseDiagramGeometries(doc);

            // 2. Mapa de tipos primitivos por ID y nombre
            Map<String, DataType> typesById = parsePrimitiveTypes(doc);

            // 3. Recolectar todas las clases del modelo (XMI 1.1 <UML:Class> y XMI 2.1 <packagedElement xmi:type="uml:Class">)
            List<Element> classElements = findAllElements(doc, "UML:Class", "uml:Class", "Class");
            Map<String, String> classNameById = new LinkedHashMap<>();
            for (Element classEl : classElements) {
                String id = getAttr(classEl, "xmi.id", "xmi:id", "id");
                String name = getAttr(classEl, "name");
                if (id != null && !id.isBlank() && name != null && !name.isBlank()) {
                    classNameById.put(id, name);
                }
            }

            if (classElements.isEmpty()) {
                throw new BadRequestException("El archivo XMI no contiene clases UML reconocibles.");
            }

            List<DiagramOperation> operations = new ArrayList<>();
            int classIndex = 0;

            for (Element classEl : classElements) {
                String classId = getAttr(classEl, "xmi.id", "xmi:id", "id");
                String name = getAttr(classEl, "name");
                if (name == null || name.isBlank()) {
                    name = "Clase" + (classIndex + 1);
                }

                // Posicion visual: primero de extension uc:, luego de geometry del diagrama, luego rejilla automatica
                Double x = parseDoubleOrNull(getAttrWithFallback(classEl, "x"));
                Double y = parseDoubleOrNull(getAttrWithFallback(classEl, "y"));

                if ((x == null || y == null) && classId != null && diagramGeometries.containsKey(classId)) {
                    double[] geom = diagramGeometries.get(classId);
                    x = geom[0];
                    y = geom[1];
                }
                if ((x == null || y == null) && name != null && diagramGeometries.containsKey(name.toLowerCase())) {
                    double[] geom = diagramGeometries.get(name.toLowerCase());
                    x = geom[0];
                    y = geom[1];
                }

                if (x == null || y == null) {
                    int col = classIndex % 3;
                    int row = classIndex / 3;
                    x = 80.0 + col * 280.0;
                    y = 80.0 + row * 240.0;
                }

                String isAbstractStr = getAttr(classEl, "isAbstract", "abstract");
                boolean isAbstract = "true".equalsIgnoreCase(isAbstractStr);
                String stereotype = getAttrWithFallback(classEl, "stereotype");
                if (stereotype == null || stereotype.isBlank()) {
                    stereotype = getTaggedValue(classEl, "stereotype");
                }

                operations.add(DiagramOperation.builder()
                        .type(OperationType.CREATE_CLASS)
                        .newName(name)
                        .isAbstract(isAbstract)
                        .x(x)
                        .y(y)
                        .rationale(stereotype)
                        .build());

                // Atributos de la clase (compatible con XMI 1.1 y XMI 2.1)
                List<Element> attrElements = findDescendantsByLocalNames(classEl, "Attribute", "ownedAttribute", "Property");
                for (Element attrEl : attrElements) {
                    String attrName = getAttr(attrEl, "name");
                    if (attrName == null || attrName.isBlank()) continue;

                    DataType dataType = resolveAttributeDataType(attrEl, typesById);
                    Visibility visibility = XmiTypeMapper.fromXmiVisibility(getAttr(attrEl, "visibility", "scope"));

                    String pkStr = getAttrWithFallback(attrEl, "primaryKey", "isPrimaryKey");
                    if (pkStr == null || pkStr.isBlank()) {
                        pkStr = getTaggedValue(attrEl, "isPrimaryKey", "primaryKey", "PK");
                    }
                    boolean isPrimaryKey = "true".equalsIgnoreCase(pkStr);
                    if (!isPrimaryKey && (attrName.equalsIgnoreCase("id") || attrName.equalsIgnoreCase(name + "Id") || attrName.equalsIgnoreCase(name + "_id"))) {
                        isPrimaryKey = true;
                    }

                    String uniqueStr = getAttrWithFallback(attrEl, "unique", "isUnique");
                    if (uniqueStr == null || uniqueStr.isBlank()) {
                        uniqueStr = getTaggedValue(attrEl, "unique", "isUnique");
                    }
                    boolean unique = "true".equalsIgnoreCase(uniqueStr);

                    String nullableStr = getAttrWithFallback(attrEl, "nullable", "isNullable");
                    if (nullableStr == null || nullableStr.isBlank()) {
                        nullableStr = getTaggedValue(attrEl, "nullable", "isNullable");
                    }
                    boolean nullable;
                    if (nullableStr != null && !nullableStr.isBlank()) {
                        nullable = "true".equalsIgnoreCase(nullableStr);
                    } else {
                        String lowerValue = findChildAttribute(attrEl, "lowerValue", "value");
                        if (lowerValue == null) {
                            lowerValue = findChildAttribute(attrEl, "MultiplicityRange", "lower");
                        }
                        nullable = !"1".equals(lowerValue);
                    }

                    if (isPrimaryKey) {
                        nullable = false;
                    }

                    operations.add(DiagramOperation.builder()
                            .type(OperationType.ADD_ATTRIBUTE)
                            .className(name)
                            .attributeName(attrName)
                            .dataType(dataType)
                            .visibility(visibility)
                            .isPrimaryKey(isPrimaryKey)
                            .nullable(nullable)
                            .unique(unique)
                            .build());
                }

                // Generalizaciones internas (XMI 2.1 child <generalization general="..."/>)
                List<Element> genElements = findDescendantsByLocalNames(classEl, "generalization");
                for (Element genEl : genElements) {
                    String generalId = getAttr(genEl, "general");
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

                classIndex++;
            }

            // Generalizaciones independientes (XMI 1.1 <UML:Generalization subtype="..." supertype="..."/>)
            List<Element> topGeneralizations = findAllElements(doc, "UML:Generalization", "uml:Generalization", "Generalization");
            for (Element genEl : topGeneralizations) {
                String subId = getAttr(genEl, "subtype", "child");
                String supId = getAttr(genEl, "supertype", "parent", "general");
                String sourceName = classNameById.get(subId);
                String targetName = classNameById.get(supId);
                if (sourceName != null && targetName != null) {
                    operations.add(DiagramOperation.builder()
                            .type(OperationType.CREATE_RELATIONSHIP)
                            .sourceClassName(sourceName)
                            .targetClassName(targetName)
                            .relationshipType(RelationshipType.GENERALIZATION)
                            .sourceMultiplicity("1")
                            .targetMultiplicity("1")
                            .build());
                }
            }

            // Asociaciones, Agregaciones y Composiciones
            List<Element> assocElements = findAllElements(doc, "UML:Association", "uml:Association", "Association");
            for (Element assocEl : assocElements) {
                List<Element> ends = findDescendantsByLocalNames(assocEl, "AssociationEnd", "ownedEnd", "memberEnd");
                if (ends.size() < 2) continue;
                Element end0 = ends.get(0);
                Element end1 = ends.get(1);

                String type0Id = getAttr(end0, "type");
                String type1Id = getAttr(end1, "type");
                String agg0 = getAttr(end0, "aggregation");
                String agg1 = getAttr(end1, "aggregation");

                String sourceId, targetId, sourceRole, targetRole, sourceMult, targetMult;
                RelationshipType relType;

                if (isAggregationOrComposition(agg0) && !isAggregationOrComposition(agg1)) {
                    sourceId = type1Id; targetId = type0Id;
                    sourceRole = getAttr(end1, "name"); targetRole = getAttr(end0, "name");
                    sourceMult = readMultiplicity(end1); targetMult = readMultiplicity(end0);
                    relType = XmiTypeMapper.fromXmiAggregation(agg0);
                } else {
                    sourceId = type0Id; targetId = type1Id;
                    sourceRole = getAttr(end0, "name"); targetRole = getAttr(end1, "name");
                    sourceMult = readMultiplicity(end0); targetMult = readMultiplicity(end1);
                    relType = XmiTypeMapper.fromXmiAggregation(agg1);
                }

                String sourceName = classNameById.get(sourceId);
                String targetName = classNameById.get(targetId);
                if (sourceName == null || targetName == null) continue;

                String relName = getAttr(assocEl, "name");
                String label = (relName != null && !relName.isBlank() && !relName.startsWith("rel_") && !relName.startsWith("EAID_")) ? relName : null;

                operations.add(DiagramOperation.builder()
                        .type(OperationType.CREATE_RELATIONSHIP)
                        .sourceClassName(sourceName)
                        .targetClassName(targetName)
                        .relationshipType(relType)
                        .sourceMultiplicity(sourceMult)
                        .targetMultiplicity(targetMult)
                        .sourceRoleName(sourceRole == null || sourceRole.isBlank() ? null : sourceRole)
                        .targetRoleName(targetRole == null || targetRole.isBlank() ? null : targetRole)
                        .label(label)
                        .build());
            }

            // Dependencias
            List<Element> depElements = findAllElements(doc, "UML:Dependency", "uml:Dependency", "Dependency");
            for (Element depEl : depElements) {
                String clientId = getAttr(depEl, "client");
                String supplierId = getAttr(depEl, "supplier");
                String sourceName = classNameById.get(clientId);
                String targetName = classNameById.get(supplierId);
                if (sourceName != null && targetName != null) {
                    operations.add(DiagramOperation.builder()
                            .type(OperationType.CREATE_RELATIONSHIP)
                            .sourceClassName(sourceName)
                            .targetClassName(targetName)
                            .relationshipType(RelationshipType.DEPENDENCY)
                            .sourceMultiplicity("1")
                            .targetMultiplicity("1")
                            .build());
                }
            }

            // Realizaciones
            List<Element> realElements = findAllElements(doc, "UML:Realization", "uml:Realization", "Realization", "uml:InterfaceRealization");
            for (Element realEl : realElements) {
                String clientId = getAttr(realEl, "client");
                String supplierId = getAttr(realEl, "supplier");
                String sourceName = classNameById.get(clientId);
                String targetName = classNameById.get(supplierId);
                if (sourceName != null && targetName != null) {
                    operations.add(DiagramOperation.builder()
                            .type(OperationType.CREATE_RELATIONSHIP)
                            .sourceClassName(sourceName)
                            .targetClassName(targetName)
                            .relationshipType(RelationshipType.REALIZATION)
                            .sourceMultiplicity("1")
                            .targetMultiplicity("1")
                            .build());
                }
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
                    .assistantMessage("Se importaron exitosamente " + classElements.size() + " clases y sus relaciones desde el archivo XMI.")
                    .appliedOperations(applied)
                    .diagram(updated)
                    .build();
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error importando XMI", e);
            throw new BadRequestException("No se pudo importar el archivo XMI: " + e.getMessage());
        }
    }

    private static boolean isAggregationOrComposition(String agg) {
        if (agg == null || agg.isBlank()) return false;
        String s = agg.trim().toLowerCase();
        return s.equals("composite") || s.equals("shared") || s.equals("aggregate");
    }

    private DataType resolveAttributeDataType(Element attrEl, Map<String, DataType> typesById) {
        // 1. Tagged value <UML:TaggedValue tag="type" value="UUID"/>
        String taggedType = getTaggedValue(attrEl, "type", "ea_type");
        if (taggedType != null && !taggedType.isBlank()) {
            return XmiTypeMapper.fromXmiName(taggedType);
        }

        // 2. Elemento Classifier / type hijo
        List<Element> classifiers = findDescendantsByLocalNames(attrEl, "Classifier", "type");
        for (Element c : classifiers) {
            String idref = getAttr(c, "xmi.idref", "xmi:idref", "idref");
            if (idref != null && typesById.containsKey(idref)) {
                return typesById.get(idref);
            }
            String name = getAttr(c, "name");
            if (name != null && !name.isBlank()) {
                return XmiTypeMapper.fromXmiName(name);
            }
            String href = getAttr(c, "href");
            if (href != null && !href.isBlank()) {
                return XmiTypeMapper.fromXmiName(href);
            }
        }

        // 3. Atributo type="..." directo
        String directType = getAttr(attrEl, "type");
        if (directType != null && !directType.isBlank()) {
            if (typesById.containsKey(directType)) {
                return typesById.get(directType);
            }
            return XmiTypeMapper.fromXmiName(directType);
        }

        return DataType.STRING;
    }

    private Map<String, DataType> parsePrimitiveTypes(Document doc) {
        Map<String, DataType> result = new LinkedHashMap<>();
        List<Element> types = findAllElements(doc, "UML:DataType", "uml:PrimitiveType", "DataType", "PrimitiveType");
        for (Element el : types) {
            String id = getAttr(el, "xmi.id", "xmi:id", "id");
            String name = getAttr(el, "name");
            if (id != null && !id.isBlank() && name != null && !name.isBlank()) {
                result.put(id, XmiTypeMapper.fromXmiName(name));
            }
        }
        return result;
    }

    private Map<String, double[]> parseDiagramGeometries(Document doc) {
        Map<String, double[]> result = new HashMap<>();
        NodeList elements = doc.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element el = (Element) elements.item(i);
            String localName = el.getLocalName() != null ? el.getLocalName() : el.getTagName();
            if ("DiagramElement".equalsIgnoreCase(localName) || "element".equalsIgnoreCase(localName) || "diagramElement".equalsIgnoreCase(localName)) {
                String subject = getAttr(el, "subject", "xmi.idref", "xmi:idref", "idref");
                String geometry = getAttr(el, "geometry");
                if (geometry == null || geometry.isBlank()) {
                    geometry = getAttr(el, "style");
                }
                if (geometry != null && !geometry.isBlank()) {
                    double left = parseEaCoord(geometry, "Left");
                    double top = parseEaCoord(geometry, "Top");
                    if (left >= 0 && top >= 0) {
                        if (subject != null && !subject.isBlank()) {
                            result.put(subject, new double[]{left, top});
                        }
                        String name = getAttr(el, "name");
                        if (name != null && !name.isBlank()) {
                            result.put(name.toLowerCase(), new double[]{left, top});
                        }
                    }
                }
            }
        }
        return result;
    }

    private double parseEaCoord(String geometry, String key) {
        for (String part : geometry.split(";")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].trim().equalsIgnoreCase(key)) {
                try {
                    return Math.abs(Double.parseDouble(kv[1].trim()));
                } catch (NumberFormatException ignored) {}
            }
        }
        return -1;
    }

    private boolean isInsideExtension(Element el) {
        Node parent = el.getParentNode();
        while (parent != null) {
            if (parent.getNodeType() == Node.ELEMENT_NODE) {
                String name = parent.getNodeName();
                if (name.contains("Extension") || name.contains("extension") || name.contains("extensions")) {
                    return true;
                }
            }
            parent = parent.getParentNode();
        }
        return false;
    }

    private List<Element> findAllElements(Document doc, String... matchTypes) {
        Set<String> targetTypes = new HashSet<>(Arrays.asList(matchTypes));
        List<Element> result = new ArrayList<>();
        NodeList allNodes = doc.getElementsByTagName("*");
        for (int i = 0; i < allNodes.getLength(); i++) {
            Element el = (Element) allNodes.item(i);
            if (isInsideExtension(el)) continue;

            String xmiType = el.getAttribute("xmi:type");
            if (xmiType.isBlank()) xmiType = el.getAttribute("xmi.type");
            String localName = el.getLocalName();
            String tagName = el.getTagName();

            if (targetTypes.contains(xmiType) || targetTypes.contains(localName) || targetTypes.contains(tagName)) {
                result.add(el);
            }
        }
        return result;
    }

    private List<Element> findDescendantsByLocalNames(Element parent, String... localNames) {
        Set<String> names = new HashSet<>(Arrays.asList(localNames));
        List<Element> result = new ArrayList<>();
        NodeList nodes = parent.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            String local = el.getLocalName() != null ? el.getLocalName() : el.getTagName();
            String tagName = el.getTagName();
            if (names.contains(local) || names.contains(tagName)) {
                result.add(el);
            }
        }
        return result;
    }

    private String getTaggedValue(Element parent, String... tagNames) {
        Set<String> tags = new HashSet<>(Arrays.asList(tagNames));
        NodeList nodes = parent.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            String local = el.getLocalName() != null ? el.getLocalName() : el.getTagName();
            if ("TaggedValue".equalsIgnoreCase(local) || "taggedValue".equalsIgnoreCase(local)) {
                String tag = getAttr(el, "tag", "name");
                if (tag != null && tags.contains(tag)) {
                    String val = getAttr(el, "value");
                    if (val != null && !val.isBlank()) return val;
                }
            }
        }
        return null;
    }

    private String getAttr(Element el, String... attrNames) {
        for (String name : attrNames) {
            if (el.hasAttribute(name)) {
                String val = el.getAttribute(name);
                if (!val.isBlank()) return val;
            }
        }
        return null;
    }

    private String getAttrWithFallback(Element el, String... localNames) {
        for (String localName : localNames) {
            String val = el.getAttributeNS(UC_NS, localName);
            if (val != null && !val.isBlank()) return val;
            val = el.getAttribute("uc:" + localName);
            if (val != null && !val.isBlank()) return val;
            val = el.getAttribute(localName);
            if (val != null && !val.isBlank()) return val;
        }
        return null;
    }

    private String findChildAttribute(Element parent, String localName, String attrName) {
        List<Element> children = findDescendantsByLocalNames(parent, localName);
        return children.isEmpty() ? null : getAttr(children.get(0), attrName);
    }

    private String readMultiplicity(Element end) {
        String lower = findChildAttribute(end, "MultiplicityRange", "lower");
        String upper = findChildAttribute(end, "MultiplicityRange", "upper");

        if (lower == null && upper == null) {
            lower = findChildAttribute(end, "lowerValue", "value");
            upper = findChildAttribute(end, "upperValue", "value");
        }

        if (lower == null && upper == null) {
            String m = getAttr(end, "multiplicity");
            if (m != null && !m.isBlank()) return m;
        }

        return XmiTypeMapper.toMultiplicityString(lower == null ? "1" : lower, upper == null ? "1" : upper);
    }

    private Double parseDoubleOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
