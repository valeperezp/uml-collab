package com.umlcollab.backend.dto.ai;

import com.umlcollab.backend.model.DataType;
import com.umlcollab.backend.model.RelationshipType;
import com.umlcollab.backend.model.Visibility;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Representacion generica de "una accion sobre el diagrama". Es el unico
 * vocabulario que entiende tanto el editor clasico (los botones de la UI la
 * arman con datos exactos) como el agente de IA (el modelo devuelve una
 * lista de estas via tool-use). Todas las referencias a clases/relaciones
 * pueden venir por id (si ya se conoce) o por nombre (asi el modelo de IA no
 * necesita conocer los UUID internos, solo los nombres que ve en el diagrama
 * o que el usuario menciono).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiagramOperation {

    private OperationType type;

    // --- Clases ---
    private UUID classId;
    private String className;      // nombre de la clase objetivo (para resolver por nombre)
    private String newName;        // nuevo nombre (RENAME_CLASS) o nombre a crear (CREATE_CLASS)
    private Boolean isAbstract;
    private Double x;
    private Double y;

    // --- Atributos ---
    private String attributeName;
    private String newAttributeName;
    private DataType dataType;
    private Visibility visibility;
    private Boolean isPrimaryKey;
    private Boolean nullable;
    private Boolean unique;

    // --- Relaciones ---
    private UUID relationshipId;
    private String sourceClassName;
    private String targetClassName;
    private RelationshipType relationshipType;
    private String sourceMultiplicity;
    private String targetMultiplicity;
    private String sourceRoleName;
    private String targetRoleName;

    /** Explicacion breve en lenguaje natural de por que se aplico esta operacion (la llena la IA). */
    private String rationale;
}
