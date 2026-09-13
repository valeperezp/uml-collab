package com.umlcollab.backend.dto.ai;

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
    DELETE_RELATIONSHIP
}
