package com.umlcollab.backend.dto.ai;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class DiagramOperation {

    private OperationType type;

    // --- Clases ---
    private UUID classId;

    @JsonAlias({"name", "class_name", "targetClass", "target_class", "className", "title"})
    private String className;      // nombre de la clase objetivo o a crear

    @JsonAlias({"new_name", "renameTo", "newName"})
    private String newName;        // nuevo nombre (RENAME_CLASS)

    @JsonAlias({"is_abstract", "abstract"})
    private Boolean isAbstract;

    private Double x;
    private Double y;

    // --- Atributos ---
    @JsonAlias({"name", "attribute_name", "attrName", "attr_name", "attributeName"})
    private String attributeName;

    @JsonAlias({"new_attribute_name", "newAttrName", "newAttributeName"})
    private String newAttributeName;

    @JsonAlias({"data_type", "type_name", "datatype", "type"})
    private DataType dataType;

    private Visibility visibility;

    @JsonAlias({"is_primary_key", "primaryKey", "primary_key", "pk"})
    private Boolean isPrimaryKey;

    private Boolean nullable;
    private Boolean unique;

    // --- Relaciones ---
    private UUID relationshipId;

    @JsonAlias({"source_class_name", "sourceClass", "source", "from", "sourceClassName"})
    private String sourceClassName;

    @JsonAlias({"target_class_name", "targetClass", "target", "to", "targetClassName"})
    private String targetClassName;

    @JsonAlias({"relationship_type", "relType", "rel_type", "relationshipType"})
    private RelationshipType relationshipType;

    @JsonAlias({"source_multiplicity", "sourceMult", "source_mult", "sourceMultiplicity"})
    private String sourceMultiplicity;

    @JsonAlias({"target_multiplicity", "targetMult", "target_mult", "targetMultiplicity"})
    private String targetMultiplicity;

    @JsonAlias({"source_role_name", "sourceRole", "source_role", "sourceRoleName"})
    private String sourceRoleName;

    @JsonAlias({"target_role_name", "targetRole", "target_role", "targetRoleName"})
    private String targetRoleName;

    @JsonAlias({"label", "relation_name", "relationName", "verb", "name", "role"})
    private String label;

    /** Explicacion breve en lenguaje natural de por que se aplico esta operacion (la llena la IA). */
    private String rationale;
}
