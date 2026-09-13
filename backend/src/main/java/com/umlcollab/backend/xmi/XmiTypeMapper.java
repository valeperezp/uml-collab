package com.umlcollab.backend.xmi;

import com.umlcollab.backend.model.DataType;
import com.umlcollab.backend.model.RelationshipType;
import com.umlcollab.backend.model.Visibility;

import java.util.LinkedHashMap;
import java.util.Map;

/** Traduce entre nuestros enums y los nombres/valores que espera un XMI 2.1 tipo Enterprise Architect. */
final class XmiTypeMapper {

    private XmiTypeMapper() {
    }

    static final Map<DataType, String> DATATYPE_TO_XMI_NAME = new LinkedHashMap<>();
    static final Map<String, DataType> XMI_NAME_TO_DATATYPE = new LinkedHashMap<>();

    static {
        put(DataType.STRING, "String");
        put(DataType.TEXT, "Text");
        put(DataType.INTEGER, "Integer");
        put(DataType.LONG, "Long");
        put(DataType.DOUBLE, "Double");
        put(DataType.DECIMAL, "Decimal");
        put(DataType.BOOLEAN, "Boolean");
        put(DataType.DATE, "Date");
        put(DataType.DATETIME, "DateTime");
        put(DataType.UUID, "UUID");
    }

    private static void put(DataType dataType, String xmiName) {
        DATATYPE_TO_XMI_NAME.put(dataType, xmiName);
        XMI_NAME_TO_DATATYPE.put(xmiName.toLowerCase(), dataType);
    }

    static String toXmiVisibility(Visibility visibility) {
        return switch (visibility) {
            case PUBLIC -> "public";
            case PRIVATE -> "private";
            case PROTECTED -> "protected";
            case PACKAGE -> "package";
        };
    }

    static Visibility fromXmiVisibility(String value) {
        if (value == null) return Visibility.PRIVATE;
        return switch (value.toLowerCase()) {
            case "public" -> Visibility.PUBLIC;
            case "protected" -> Visibility.PROTECTED;
            case "package" -> Visibility.PACKAGE;
            default -> Visibility.PRIVATE;
        };
    }

    /** Convencion usada por el exportador: la agregacion se marca en el extremo "objetivo" (el que es parte/contenido). */
    static String toXmiAggregation(RelationshipType type) {
        return switch (type) {
            case COMPOSITION -> "composite";
            case AGGREGATION -> "shared";
            default -> "none";
        };
    }

    static RelationshipType fromXmiAggregation(String aggregation) {
        if (aggregation == null) return RelationshipType.ASSOCIATION;
        return switch (aggregation.toLowerCase()) {
            case "composite" -> RelationshipType.COMPOSITION;
            case "shared" -> RelationshipType.AGGREGATION;
            default -> RelationshipType.ASSOCIATION;
        };
    }

    static DataType fromXmiName(String name) {
        if (name == null) return DataType.STRING;
        return XMI_NAME_TO_DATATYPE.getOrDefault(name.toLowerCase(), DataType.STRING);
    }

    /** "1", "0..1", "*", "1..*", "0..*" -> [lower, upper] en formato UML (upper "*" = ilimitado). */
    static String[] parseMultiplicity(String multiplicity) {
        if (multiplicity == null || multiplicity.isBlank()) return new String[]{"1", "1"};
        String m = multiplicity.trim();
        if (m.equals("*")) return new String[]{"0", "*"};
        if (m.contains("..")) {
            String[] parts = m.split("\\.\\.", 2);
            return new String[]{parts[0], parts[1]};
        }
        return new String[]{m, m};
    }

    static String toMultiplicityString(String lower, String upper) {
        if (lower == null) lower = "1";
        if (upper == null) upper = lower;
        if (lower.equals(upper)) return lower;
        return lower + ".." + upper;
    }
}
