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

        // Alias comunes de Enterprise Architect, Visual Paradigm, SQL y Java
        addAlias("int", DataType.INTEGER);
        addAlias("integer", DataType.INTEGER);
        addAlias("int4", DataType.INTEGER);
        addAlias("smallint", DataType.INTEGER);
        addAlias("tinyint", DataType.INTEGER);
        addAlias("serial", DataType.INTEGER);

        addAlias("bigint", DataType.LONG);
        addAlias("long", DataType.LONG);
        addAlias("int8", DataType.LONG);
        addAlias("bigserial", DataType.LONG);

        addAlias("float", DataType.DOUBLE);
        addAlias("double", DataType.DOUBLE);
        addAlias("real", DataType.DOUBLE);
        addAlias("float8", DataType.DOUBLE);
        addAlias("number", DataType.DOUBLE);

        addAlias("decimal", DataType.DECIMAL);
        addAlias("numeric", DataType.DECIMAL);
        addAlias("money", DataType.DECIMAL);

        addAlias("bool", DataType.BOOLEAN);
        addAlias("boolean", DataType.BOOLEAN);
        addAlias("bit", DataType.BOOLEAN);

        addAlias("str", DataType.STRING);
        addAlias("string", DataType.STRING);
        addAlias("varchar", DataType.STRING);
        addAlias("nvarchar", DataType.STRING);
        addAlias("char", DataType.STRING);
        addAlias("character", DataType.STRING);

        addAlias("text", DataType.TEXT);
        addAlias("clob", DataType.TEXT);
        addAlias("longtext", DataType.TEXT);

        addAlias("date", DataType.DATE);

        addAlias("datetime", DataType.DATETIME);
        addAlias("timestamp", DataType.DATETIME);
        addAlias("timestamptz", DataType.DATETIME);
        addAlias("time", DataType.DATETIME);

        addAlias("uuid", DataType.UUID);
        addAlias("guid", DataType.UUID);
        addAlias("uniqueidentifier", DataType.UUID);
    }

    private static void put(DataType dataType, String xmiName) {
        DATATYPE_TO_XMI_NAME.put(dataType, xmiName);
        XMI_NAME_TO_DATATYPE.put(xmiName.toLowerCase(), dataType);
    }

    private static void addAlias(String alias, DataType dataType) {
        XMI_NAME_TO_DATATYPE.put(alias.toLowerCase(), dataType);
    }

    static String toXmiVisibility(Visibility visibility) {
        if (visibility == null) return "private";
        return switch (visibility) {
            case PUBLIC -> "public";
            case PRIVATE -> "private";
            case PROTECTED -> "protected";
            case PACKAGE -> "package";
        };
    }

    static Visibility fromXmiVisibility(String value) {
        if (value == null || value.isBlank()) return Visibility.PRIVATE;
        return switch (value.trim().toLowerCase()) {
            case "public", "+" -> Visibility.PUBLIC;
            case "protected", "#" -> Visibility.PROTECTED;
            case "package", "~" -> Visibility.PACKAGE;
            default -> Visibility.PRIVATE;
        };
    }

    /** Convencion usada por el exportador: la agregacion se marca en el extremo "objetivo" (el que es parte/contenido). */
    static String toXmiAggregation(RelationshipType type) {
        if (type == null) return "none";
        return switch (type) {
            case COMPOSITION -> "composite";
            case AGGREGATION -> "shared";
            default -> "none";
        };
    }

    static RelationshipType fromXmiAggregation(String aggregation) {
        if (aggregation == null || aggregation.isBlank()) return RelationshipType.ASSOCIATION;
        return switch (aggregation.trim().toLowerCase()) {
            case "composite" -> RelationshipType.COMPOSITION;
            case "shared" -> RelationshipType.AGGREGATION;
            default -> RelationshipType.ASSOCIATION;
        };
    }

    static DataType fromXmiName(String name) {
        if (name == null || name.isBlank()) return DataType.STRING;
        String clean = name.trim();
        // Si viene como URI/href tipo "http://schema.omg.org/spec/UML/2.1/uml.xml#String"
        if (clean.contains("#")) {
            clean = clean.substring(clean.lastIndexOf('#') + 1);
        }
        return XMI_NAME_TO_DATATYPE.getOrDefault(clean.toLowerCase(), DataType.STRING);
    }

    /** "1", "0..1", "*", "1..*", "0..*" -> [lower, upper] en formato UML (upper "*" = ilimitado). */
    static String[] parseMultiplicity(String multiplicity) {
        if (multiplicity == null || multiplicity.isBlank()) return new String[]{"1", "1"};
        String m = multiplicity.trim();
        if (m.equals("*")) return new String[]{"0", "*"};
        if (m.contains("..")) {
            String[] parts = m.split("\\.\\.", 2);
            return new String[]{parts[0].trim(), parts[1].trim()};
        }
        return new String[]{m, m};
    }

    static String toMultiplicityString(String lower, String upper) {
        if (lower == null || lower.isBlank()) lower = "1";
        if (upper == null || upper.isBlank()) upper = lower;
        lower = lower.trim();
        upper = upper.trim();
        if (lower.equals("0") && (upper.equals("*") || upper.equals("-1"))) return "0..*";
        if (lower.equals("1") && (upper.equals("*") || upper.equals("-1"))) return "1..*";
        if (upper.equals("-1")) upper = "*";
        if (lower.equals(upper)) return lower;
        return lower + ".." + upper;
    }
}
