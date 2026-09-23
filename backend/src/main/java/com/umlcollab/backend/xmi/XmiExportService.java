package com.umlcollab.backend.xmi;

import com.umlcollab.backend.dto.AttributeDto;
import com.umlcollab.backend.dto.ClassDto;
import com.umlcollab.backend.dto.DiagramDetailDto;
import com.umlcollab.backend.dto.RelationshipDto;
import com.umlcollab.backend.model.DataType;
import com.umlcollab.backend.model.RelationshipType;
import com.umlcollab.backend.service.DiagramService;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Exporta un diagrama a XMI 2.1 para Enterprise Architect (EA).
 *
 * Estructura generada:
 * 1. Root <xmi:XMI xmi:version="2.1" xmlns:uml="http://schema.omg.org/spec/UML/2.1" xmlns:xmi="http://schema.omg.org/spec/XMI/2.1">
 * 2. <uml:Model> con <packagedElement xmi:type="uml:Package">
 *    - Tipos primitivos (<packagedElement xmi:type="uml:PrimitiveType">)
 *    - Clases (<packagedElement xmi:type="uml:Class">) con <ownedAttribute> (sin namespace uc ni uc:*)
 *    - Generalizaciones (<generalization>) y Asociaciones (<packagedElement xmi:type="uml:Association">)
 *    - Bloque <diagrams> DENTRO del paquete (justo después de las relaciones) con <element geometry="Left=X;Top=Y;Right=X2;Bottom=Y2;" subject="EAID_..."/>
 * 3. <xmi:Extension extender="Enterprise Architect" extenderID="6.5">
 *    - Secciones <elements> y <connectors> (SIN <diagrams>)
 */
@Service
public class XmiExportService {

    private static final String XMI_NS = "http://schema.omg.org/spec/XMI/2.1";
    private static final String UML_NS = "http://schema.omg.org/spec/UML/2.1";

    private final DiagramService diagramService;

    public XmiExportService(DiagramService diagramService) {
        this.diagramService = diagramService;
    }

    public byte[] export(UUID diagramId) {
        DiagramDetailDto diagram = diagramService.getDetail(diagramId);
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String diagramName = (diagram.getName() != null && !diagram.getName().isBlank()) ? diagram.getName() : "Diagrama UML";
            String safeDiagId = sanitizeId(diagram.getId().toString());

            // 1. Root XMI 2.1 (sin namespace uc)
            Element root = doc.createElementNS(XMI_NS, "xmi:XMI");
            root.setAttribute("xmi:version", "2.1");
            root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xmi", XMI_NS);
            root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:uml", UML_NS);
            doc.appendChild(root);

            // 2. Documentation
            Element documentation = doc.createElement("xmi:Documentation");
            documentation.setAttribute("exporter", "Enterprise Architect");
            documentation.setAttribute("exporterVersion", "6.5");
            root.appendChild(documentation);

            // 3. Model
            Element model = doc.createElement("uml:Model");
            model.setAttribute("xmi:type", "uml:Model");
            model.setAttribute("name", "EA_Model");
            model.setAttribute("xmi:id", "EAID_MODEL_" + safeDiagId);
            root.appendChild(model);

            // 4. Package
            String packageXmiId = "EAPK_" + safeDiagId;
            Element pkg = doc.createElement("packagedElement");
            pkg.setAttribute("xmi:type", "uml:Package");
            pkg.setAttribute("xmi:id", packageXmiId);
            pkg.setAttribute("name", diagramName);
            model.appendChild(pkg);

            // 5. Tipos primitivos usados
            Set<DataType> usedTypes = new LinkedHashSet<>();
            diagram.getClasses().forEach(c -> c.getAttributes().forEach(a -> usedTypes.add(a.getDataType())));
            Map<DataType, String> typeIds = new LinkedHashMap<>();
            for (DataType type : usedTypes) {
                String typeName = XmiTypeMapper.DATATYPE_TO_XMI_NAME.getOrDefault(type, "String");
                String id = "eaxmiid_" + type.name();
                typeIds.put(type, id);

                Element typeEl = doc.createElement("packagedElement");
                typeEl.setAttribute("xmi:type", "uml:PrimitiveType");
                typeEl.setAttribute("xmi:id", id);
                typeEl.setAttribute("name", typeName);
                pkg.appendChild(typeEl);
            }

            // 6. Clases (sin atributos uc:x, uc:y, uc:primaryKey, uc:nullable, uc:unique)
            Map<UUID, String> classIds = new LinkedHashMap<>();
            for (ClassDto c : diagram.getClasses()) {
                String classXmiId = "EAID_CLASS_" + sanitizeId(c.getId().toString());
                classIds.put(c.getId(), classXmiId);

                Element classEl = doc.createElement("packagedElement");
                classEl.setAttribute("xmi:type", "uml:Class");
                classEl.setAttribute("xmi:id", classXmiId);
                classEl.setAttribute("name", c.getName());
                classEl.setAttribute("isAbstract", String.valueOf(c.isAbstract()));

                for (AttributeDto a : c.getAttributes()) {
                    Element attrEl = doc.createElement("ownedAttribute");
                    String attrXmiId = "EAID_ATTR_" + sanitizeId(a.getId().toString());
                    attrEl.setAttribute("xmi:type", "uml:Property");
                    attrEl.setAttribute("xmi:id", attrXmiId);
                    attrEl.setAttribute("name", a.getName());
                    attrEl.setAttribute("visibility", XmiTypeMapper.toXmiVisibility(a.getVisibility()));

                    Element typeRef = doc.createElement("type");
                    typeRef.setAttribute("xmi:idref", typeIds.getOrDefault(a.getDataType(), "eaxmiid_STRING"));
                    attrEl.appendChild(typeRef);

                    appendMultiplicity(doc, attrEl, a.isNullable() ? "0" : "1", "1");
                    classEl.appendChild(attrEl);
                }
                pkg.appendChild(classEl);
            }

            // 7. Generalizaciones
            for (RelationshipDto r : diagram.getRelationships()) {
                if (r.getType() == RelationshipType.GENERALIZATION) {
                    String srcId = classIds.get(r.getSourceClassId());
                    String tgtId = classIds.get(r.getTargetClassId());
                    if (srcId != null && tgtId != null) {
                        Element srcClassEl = findClassElement(pkg, srcId);
                        if (srcClassEl != null) {
                            Element gen = doc.createElement("generalization");
                            gen.setAttribute("xmi:type", "uml:Generalization");
                            gen.setAttribute("xmi:id", "EAID_GEN_" + sanitizeId(r.getId().toString()));
                            gen.setAttribute("general", tgtId);
                            srcClassEl.appendChild(gen);
                        }
                    }
                }
            }

            // 8. Relaciones y Asociaciones
            int relCounter = 0;
            for (RelationshipDto r : diagram.getRelationships()) {
                if (r.getType() == RelationshipType.GENERALIZATION) continue;
                relCounter++;
                String relXmiId = "EAID_REL_" + sanitizeId(r.getId().toString());
                String srcClassId = classIds.get(r.getSourceClassId());
                String tgtClassId = classIds.get(r.getTargetClassId());
                if (srcClassId == null || tgtClassId == null) continue;

                String relName = (r.getLabel() != null && !r.getLabel().isBlank()) ? r.getLabel() : ("rel_" + relCounter);

                if (r.getType() == RelationshipType.DEPENDENCY || r.getType() == RelationshipType.REALIZATION) {
                    Element dep = doc.createElement("packagedElement");
                    dep.setAttribute("xmi:type", r.getType() == RelationshipType.REALIZATION ? "uml:Realization" : "uml:Dependency");
                    dep.setAttribute("xmi:id", relXmiId);
                    dep.setAttribute("name", relName);
                    dep.setAttribute("client", srcClassId);
                    dep.setAttribute("supplier", tgtClassId);
                    pkg.appendChild(dep);
                    continue;
                }

                Element assoc = doc.createElement("packagedElement");
                assoc.setAttribute("xmi:type", "uml:Association");
                assoc.setAttribute("xmi:id", relXmiId);
                assoc.setAttribute("name", relName);

                Element sourceEnd = doc.createElement("ownedEnd");
                sourceEnd.setAttribute("xmi:type", "uml:Property");
                sourceEnd.setAttribute("xmi:id", relXmiId + "_src");
                sourceEnd.setAttribute("type", srcClassId);
                sourceEnd.setAttribute("association", relXmiId);
                sourceEnd.setAttribute("aggregation", "none");
                if (r.getSourceRoleName() != null && !r.getSourceRoleName().isBlank()) {
                    sourceEnd.setAttribute("name", r.getSourceRoleName());
                }
                String[] srcMult = XmiTypeMapper.parseMultiplicity(r.getSourceMultiplicity());
                appendMultiplicity(doc, sourceEnd, srcMult[0], srcMult[1]);
                assoc.appendChild(sourceEnd);

                Element targetEnd = doc.createElement("ownedEnd");
                targetEnd.setAttribute("xmi:type", "uml:Property");
                targetEnd.setAttribute("xmi:id", relXmiId + "_tgt");
                targetEnd.setAttribute("type", tgtClassId);
                targetEnd.setAttribute("association", relXmiId);
                targetEnd.setAttribute("aggregation", XmiTypeMapper.toXmiAggregation(r.getType()));
                if (r.getTargetRoleName() != null && !r.getTargetRoleName().isBlank()) {
                    targetEnd.setAttribute("name", r.getTargetRoleName());
                }
                String[] tgtMult = XmiTypeMapper.parseMultiplicity(r.getTargetMultiplicity());
                appendMultiplicity(doc, targetEnd, tgtMult[0], tgtMult[1]);
                assoc.appendChild(targetEnd);

                pkg.appendChild(assoc);
            }

            // 9. Bloque <diagrams> DENTRO del <packagedElement> del paquete (justo después de las relaciones)
            Element diagrams = doc.createElement("diagrams");
            Element diag = doc.createElement("diagram");
            String diagXmiId = "EAID_DIAG_" + safeDiagId;
            diag.setAttribute("xmi:id", diagXmiId);

            Element modelRef = doc.createElement("model");
            modelRef.setAttribute("package", packageXmiId);
            modelRef.setAttribute("owner", packageXmiId);
            diag.appendChild(modelRef);

            Element props = doc.createElement("properties");
            props.setAttribute("name", diagramName);
            props.setAttribute("type", "Logical");
            diag.appendChild(props);

            Element proj = doc.createElement("project");
            proj.setAttribute("author", "UMLCollab");
            proj.setAttribute("version", "1.0");
            proj.setAttribute("created", timestamp);
            proj.setAttribute("modified", timestamp);
            diag.appendChild(proj);

            Element diagElements = doc.createElement("elements");
            int seq = 1;
            for (ClassDto c : diagram.getClasses()) {
                String classXmiId = classIds.get(c.getId());
                int left = (int) Math.round(c.getX());
                int top = (int) Math.round(c.getY());
                int width = 160;
                int height = Math.max(70, 40 + (c.getAttributes() != null ? c.getAttributes().size() : 0) * 18);
                int right = left + width;
                int bottom = top + height;

                Element diagElem = doc.createElement("element");
                diagElem.setAttribute("geometry", String.format("Left=%d;Top=%d;Right=%d;Bottom=%d;", left, top, right, bottom));
                diagElem.setAttribute("subject", classXmiId);
                diagElem.setAttribute("seqno", String.valueOf(seq++));
                String duid = safeDiagId.length() >= 8 ? safeDiagId.substring(0, 8) : "EA000000";
                if (classXmiId != null && classXmiId.length() >= 19) {
                    duid = classXmiId.substring(11, Math.min(19, classXmiId.length()));
                }
                diagElem.setAttribute("style", "DUID=" + duid + ";");
                diagElements.appendChild(diagElem);
            }
            diag.appendChild(diagElements);
            diagrams.appendChild(diag);
            pkg.appendChild(diagrams);

            // 10. Bloque <xmi:Extension> solo para <elements> y <connectors> (SIN <diagrams>)
            Element extension = doc.createElement("xmi:Extension");
            extension.setAttribute("extender", "Enterprise Architect");
            extension.setAttribute("extenderID", "6.5");

            Element extElements = doc.createElement("elements");
            for (ClassDto c : diagram.getClasses()) {
                String classXmiId = classIds.get(c.getId());
                Element el = doc.createElement("element");
                el.setAttribute("xmi:idref", classXmiId);
                el.setAttribute("name", c.getName());
                el.setAttribute("type", "Class");
                el.setAttribute("scope", "public");

                Element elProps = doc.createElement("properties");
                elProps.setAttribute("isSpecification", "false");
                elProps.setAttribute("sType", "Class");
                elProps.setAttribute("nType", "0");
                elProps.setAttribute("scope", "public");
                elProps.setAttribute("isAbstract", String.valueOf(c.isAbstract()));
                el.appendChild(elProps);

                Element elProj = doc.createElement("project");
                elProj.setAttribute("author", "UMLCollab");
                elProj.setAttribute("version", "1.0");
                elProj.setAttribute("package", packageXmiId);
                elProj.setAttribute("status", "Proposed");
                elProj.setAttribute("created", timestamp);
                elProj.setAttribute("modified", timestamp);
                el.appendChild(elProj);

                Element elExtProps = doc.createElement("extendedProperties");
                elExtProps.setAttribute("package_name", diagramName);
                el.appendChild(elExtProps);

                if (!c.getAttributes().isEmpty()) {
                    Element extAttrs = doc.createElement("attributes");
                    for (AttributeDto a : c.getAttributes()) {
                        String attrXmiId = "EAID_ATTR_" + sanitizeId(a.getId().toString());
                        Element extAttr = doc.createElement("attribute");
                        extAttr.setAttribute("xmi:idref", attrXmiId);
                        extAttr.setAttribute("name", a.getName());
                        extAttr.setAttribute("scope", XmiTypeMapper.toXmiVisibility(a.getVisibility()));

                        Element extAttrProps = doc.createElement("properties");
                        extAttrProps.setAttribute("type", XmiTypeMapper.DATATYPE_TO_XMI_NAME.getOrDefault(a.getDataType(), "String"));
                        extAttr.appendChild(extAttrProps);

                        Element extAttrTags = doc.createElement("tags");
                        if (a.isPrimaryKey()) {
                            appendTag(doc, extAttrTags, "isPrimaryKey", "true");
                        }
                        appendTag(doc, extAttrTags, "nullable", String.valueOf(a.isNullable()));
                        appendTag(doc, extAttrTags, "unique", String.valueOf(a.isUnique()));
                        extAttr.appendChild(extAttrTags);

                        extAttrs.appendChild(extAttr);
                    }
                    el.appendChild(extAttrs);
                }

                extElements.appendChild(el);
            }
            extension.appendChild(extElements);

            Element extConnectors = doc.createElement("connectors");
            for (RelationshipDto r : diagram.getRelationships()) {
                String relXmiId = "EAID_REL_" + sanitizeId(r.getId().toString());
                String srcClassId = classIds.get(r.getSourceClassId());
                String tgtClassId = classIds.get(r.getTargetClassId());
                if (srcClassId == null || tgtClassId == null) continue;

                Element conn = doc.createElement("connector");
                conn.setAttribute("xmi:idref", relXmiId);

                Element src = doc.createElement("source");
                src.setAttribute("xmi:idref", srcClassId);
                Element srcModel = doc.createElement("model");
                srcModel.setAttribute("name", r.getSourceClassName() != null ? r.getSourceClassName() : "");
                src.appendChild(srcModel);
                Element srcType = doc.createElement("type");
                srcType.setAttribute("multiplicity", r.getSourceMultiplicity() != null ? r.getSourceMultiplicity() : "1");
                src.appendChild(srcType);
                conn.appendChild(src);

                Element tgt = doc.createElement("target");
                tgt.setAttribute("xmi:idref", tgtClassId);
                Element tgtModel = doc.createElement("model");
                tgtModel.setAttribute("name", r.getTargetClassName() != null ? r.getTargetClassName() : "");
                tgt.appendChild(tgtModel);
                Element tgtType = doc.createElement("type");
                tgtType.setAttribute("multiplicity", r.getTargetMultiplicity() != null ? r.getTargetMultiplicity() : "1");
                tgt.appendChild(tgtType);
                conn.appendChild(tgt);

                Element connProps = doc.createElement("properties");
                connProps.setAttribute("ea_type", r.getType().name().substring(0, 1).toUpperCase() + r.getType().name().substring(1).toLowerCase());
                connProps.setAttribute("direction", "Source -> Destination");
                conn.appendChild(connProps);

                extConnectors.appendChild(conn);
            }
            extension.appendChild(extConnectors);
            root.appendChild(extension);

            return serialize(doc);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo generar el XMI: " + e.getMessage(), e);
        }
    }

    private void appendTag(Document doc, Element parent, String name, String value) {
        Element tag = doc.createElement("tag");
        tag.setAttribute("name", name);
        tag.setAttribute("value", value);
        parent.appendChild(tag);
    }

    private Element findClassElement(Element container, String classXmiId) {
        var children = container.getElementsByTagName("packagedElement");
        for (int i = 0; i < children.getLength(); i++) {
            Element el = (Element) children.item(i);
            if (classXmiId != null && classXmiId.equals(el.getAttribute("xmi:id"))) {
                return el;
            }
        }
        return null;
    }

    private void appendMultiplicity(Document doc, Element parent, String lower, String upper) {
        Element lowerEl = doc.createElement("lowerValue");
        lowerEl.setAttribute("xmi:type", "uml:LiteralInteger");
        lowerEl.setAttribute("value", "*".equals(lower) ? "0" : (lower != null ? lower : "1"));
        parent.appendChild(lowerEl);

        Element upperEl = doc.createElement("upperValue");
        upperEl.setAttribute("xmi:type", "uml:LiteralUnlimitedNatural");
        upperEl.setAttribute("value", upper != null ? upper : "1");
        parent.appendChild(upperEl);
    }

    private String sanitizeId(String uuid) {
        if (uuid == null) return UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return uuid.replace("-", "").toUpperCase();
    }

    private byte[] serialize(Document doc) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.setOutputProperty(OutputKeys.ENCODING, "windows-1252");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new StreamResult(out));
        return out.toByteArray();
    }
}

