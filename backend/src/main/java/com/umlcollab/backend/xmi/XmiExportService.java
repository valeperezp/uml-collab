package com.umlcollab.backend.xmi;

import com.umlcollab.backend.dto.AttributeDto;
import com.umlcollab.backend.dto.ClassDto;
import com.umlcollab.backend.dto.DiagramDetailDto;
import com.umlcollab.backend.dto.RelationshipDto;
import com.umlcollab.backend.model.DataType;
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
import java.util.*;

/**
 * Exporta un diagrama a XMI 2.1, el formato que Enterprise Architect sabe
 * importar. Cubre clases, atributos (con tipo, visibilidad y las banderas
 * de PK/nullable/unique como extension propia en el namespace uc:), y
 * relaciones (asociacion / agregacion / composicion / generalizacion) con
 * sus multiplicidades y roles.
 */
@Service
public class XmiExportService {

    private static final String XMI_NS = "http://schema.omg.org/spec/XMI/2.1";
    private static final String UML_NS = "http://schema.omg.org/spec/UML/2.1";
    private static final String UC_NS = "http://umlcollab.com/xmi-ext";

    private final DiagramService diagramService;

    public XmiExportService(DiagramService diagramService) {
        this.diagramService = diagramService;
    }

    public byte[] export(UUID diagramId) {
        DiagramDetailDto diagram = diagramService.getDetail(diagramId);
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

            Element root = doc.createElementNS(XMI_NS, "xmi:XMI");
            root.setAttribute("xmi:version", "2.1");
            root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xmi", XMI_NS);
            root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:uml", UML_NS);
            root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:uc", UC_NS);
            doc.appendChild(root);

            Element model = doc.createElement("uml:Model");
            model.setAttribute("xmi:type", "uml:Model");
            model.setAttribute("xmi:id", "model-" + diagram.getId());
            model.setAttribute("name", diagram.getName());
            root.appendChild(model);

            // Tipos primitivos usados, declarados localmente para que el XMI sea autocontenido.
            Set<DataType> usedTypes = new LinkedHashSet<>();
            diagram.getClasses().forEach(c -> c.getAttributes().forEach(a -> usedTypes.add(a.getDataType())));
            Map<DataType, String> typeIds = new LinkedHashMap<>();
            for (DataType type : usedTypes) {
                String id = "type-" + type.name();
                typeIds.put(type, id);
                Element typeEl = doc.createElement("packagedElement");
                typeEl.setAttribute("xmi:type", "uml:PrimitiveType");
                typeEl.setAttribute("xmi:id", id);
                typeEl.setAttribute("name", XmiTypeMapper.DATATYPE_TO_XMI_NAME.get(type));
                model.appendChild(typeEl);
            }

            Map<UUID, String> classIds = new LinkedHashMap<>();
            for (ClassDto c : diagram.getClasses()) {
                String classXmiId = "class-" + c.getId();
                classIds.put(c.getId(), classXmiId);

                Element classEl = doc.createElement("packagedElement");
                classEl.setAttribute("xmi:type", "uml:Class");
                classEl.setAttribute("xmi:id", classXmiId);
                classEl.setAttribute("name", c.getName());
                classEl.setAttribute("isAbstract", String.valueOf(c.isAbstract()));
                classEl.setAttributeNS(UC_NS, "uc:x", String.valueOf(c.getX()));
                classEl.setAttributeNS(UC_NS, "uc:y", String.valueOf(c.getY()));
                if (c.getStereotype() != null && !c.getStereotype().isBlank()) {
                    classEl.setAttributeNS(UC_NS, "uc:stereotype", c.getStereotype());
                }

                for (AttributeDto a : c.getAttributes()) {
                    Element attrEl = doc.createElement("ownedAttribute");
                    attrEl.setAttribute("xmi:id", "attr-" + a.getId());
                    attrEl.setAttribute("name", a.getName());
                    attrEl.setAttribute("visibility", XmiTypeMapper.toXmiVisibility(a.getVisibility()));
                    attrEl.setAttributeNS(UC_NS, "uc:primaryKey", String.valueOf(a.isPrimaryKey()));
                    attrEl.setAttributeNS(UC_NS, "uc:nullable", String.valueOf(a.isNullable()));
                    attrEl.setAttributeNS(UC_NS, "uc:unique", String.valueOf(a.isUnique()));

                    Element typeRef = doc.createElement("type");
                    typeRef.setAttribute("xmi:idref", typeIds.get(a.getDataType()));
                    attrEl.appendChild(typeRef);

                    appendMultiplicity(doc, attrEl, a.isNullable() ? "0" : "1", "1");
                    classEl.appendChild(attrEl);
                }
                model.appendChild(classEl);
            }

            int relCounter = 0;
            for (RelationshipDto r : diagram.getRelationships()) {
                relCounter++;
                if (r.getType() == com.umlcollab.backend.model.RelationshipType.GENERALIZATION) {
                    Element sourceClassEl = findClassElement(model, classIds.get(r.getSourceClassId()));
                    if (sourceClassEl != null) {
                        Element gen = doc.createElement("generalization");
                        gen.setAttribute("xmi:id", "gen-" + r.getId());
                        gen.setAttribute("general", classIds.get(r.getTargetClassId()));
                        sourceClassEl.appendChild(gen);
                    }
                    continue;
                }

                String assocId = "assoc-" + r.getId();
                Element assoc = doc.createElement("packagedElement");
                assoc.setAttribute("xmi:type", "uml:Association");
                assoc.setAttribute("xmi:id", assocId);
                assoc.setAttribute("name", r.getLabel() == null ? ("rel" + relCounter) : r.getLabel());

                Element sourceEnd = doc.createElement("ownedEnd");
                sourceEnd.setAttribute("xmi:id", assocId + "-src");
                sourceEnd.setAttribute("type", classIds.get(r.getSourceClassId()));
                sourceEnd.setAttribute("association", assocId);
                sourceEnd.setAttribute("aggregation", "none");
                if (r.getSourceRoleName() != null) sourceEnd.setAttribute("name", r.getSourceRoleName());
                String[] srcMult = XmiTypeMapper.parseMultiplicity(r.getSourceMultiplicity());
                appendMultiplicity(doc, sourceEnd, srcMult[0], srcMult[1]);
                assoc.appendChild(sourceEnd);

                Element targetEnd = doc.createElement("ownedEnd");
                targetEnd.setAttribute("xmi:id", assocId + "-tgt");
                targetEnd.setAttribute("type", classIds.get(r.getTargetClassId()));
                targetEnd.setAttribute("association", assocId);
                targetEnd.setAttribute("aggregation", XmiTypeMapper.toXmiAggregation(r.getType()));
                if (r.getTargetRoleName() != null) targetEnd.setAttribute("name", r.getTargetRoleName());
                String[] tgtMult = XmiTypeMapper.parseMultiplicity(r.getTargetMultiplicity());
                appendMultiplicity(doc, targetEnd, tgtMult[0], tgtMult[1]);
                assoc.appendChild(targetEnd);

                model.appendChild(assoc);
            }

            return serialize(doc);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo generar el XMI: " + e.getMessage(), e);
        }
    }

    private Element findClassElement(Element model, String classXmiId) {
        var children = model.getElementsByTagName("packagedElement");
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
        lowerEl.setAttribute("value", lower.equals("*") ? "0" : lower);
        parent.appendChild(lowerEl);

        Element upperEl = doc.createElement("upperValue");
        upperEl.setAttribute("xmi:type", "uml:LiteralUnlimitedNatural");
        upperEl.setAttribute("value", upper);
        parent.appendChild(upperEl);
    }

    private byte[] serialize(Document doc) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new StreamResult(out));
        return out.toByteArray();
    }
}
