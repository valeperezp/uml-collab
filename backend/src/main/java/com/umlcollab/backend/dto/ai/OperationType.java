package com.umlcollab.backend.dto.ai;

import com.fasterxml.jackson.annotation.JsonCreator;

/** Cada operacion que el agente de IA (o el editor clasico) puede aplicar sobre un diagrama. */
public enum OperationType {
    CREATE_CLASS,
    RENAME_CLASS,
    DELETE_CLASS,
    MOVE_CLASS,
    ADD_ATTRIBUTE,
    UPDATE_ATTRIBUTE,
    REMOVE_ATTRIBUTE,
    CREATE_RELATIONSHIP,
    UPDATE_RELATIONSHIP,
    DELETE_RELATIONSHIP;

    @JsonCreator
    public static OperationType from(Object raw) {
        if (raw == null) return CREATE_CLASS;
        String s = raw.toString().trim().toUpperCase().replace("-", "_").replace(" ", "_");
        if (s.isEmpty()) return CREATE_CLASS;

        return switch (s) {
            case "CREATE_CLASS", "NEW_CLASS", "ADD_CLASS" -> CREATE_CLASS;
            case "RENAME_CLASS", "EDIT_CLASS_NAME" -> RENAME_CLASS;
            case "DELETE_CLASS", "REMOVE_CLASS", "DROP_CLASS" -> DELETE_CLASS;
            case "MOVE_CLASS", "UPDATE_POSITION" -> MOVE_CLASS;
            case "ADD_ATTRIBUTE", "CREATE_ATTRIBUTE", "NEW_ATTRIBUTE" -> ADD_ATTRIBUTE;
            case "UPDATE_ATTRIBUTE", "EDIT_ATTRIBUTE", "RENAME_ATTRIBUTE" -> UPDATE_ATTRIBUTE;
            case "REMOVE_ATTRIBUTE", "DELETE_ATTRIBUTE", "DROP_ATTRIBUTE" -> REMOVE_ATTRIBUTE;
            case "CREATE_RELATIONSHIP", "ADD_RELATIONSHIP", "CONNECT_CLASSES" -> CREATE_RELATIONSHIP;
            case "UPDATE_RELATIONSHIP", "EDIT_RELATIONSHIP" -> UPDATE_RELATIONSHIP;
            case "DELETE_RELATIONSHIP", "REMOVE_RELATIONSHIP" -> DELETE_RELATIONSHIP;
            default -> {
                for (OperationType t : values()) {
                    if (t.name().equalsIgnoreCase(s)) yield t;
                }
                yield CREATE_CLASS;
            }
        };
    }
}
