package com.umlcollab.backend.ai;

/**
 * Definicion (JSON Schema, formato "tool" de la API de mensajes de Anthropic)
 * del unico vocabulario que el modelo puede usar para tocar el diagrama:
 * una lista de operaciones estructuradas. El modelo NUNCA dibuja el
 * diagrama el mismo ni devuelve HTML/SVG/lo que sea; solo devuelve esta
 * lista, y es el backend el que la aplica contra la base de datos real,
 * validando y transmitiendo cada cambio por WebSocket igual que si lo
 * hubiera hecho un humano desde el editor clasico.
 */
public final class AiToolSchemas {

    private AiToolSchemas() {
    }

    public static final String OPERATION_TYPES =
            "[\"CREATE_CLASS\",\"RENAME_CLASS\",\"DELETE_CLASS\",\"MOVE_CLASS\"," +
            "\"ADD_ATTRIBUTE\",\"UPDATE_ATTRIBUTE\",\"REMOVE_ATTRIBUTE\"," +
            "\"CREATE_RELATIONSHIP\",\"UPDATE_RELATIONSHIP\",\"DELETE_RELATIONSHIP\"]";

    public static final String DATA_TYPES =
            "[\"STRING\",\"TEXT\",\"INTEGER\",\"LONG\",\"DOUBLE\",\"DECIMAL\",\"BOOLEAN\",\"DATE\",\"DATETIME\",\"UUID\"]";

    public static final String VISIBILITIES = "[\"PUBLIC\",\"PRIVATE\",\"PROTECTED\",\"PACKAGE\"]";

    public static final String RELATIONSHIP_TYPES =
            "[\"ASSOCIATION\",\"AGGREGATION\",\"COMPOSITION\",\"GENERALIZATION\",\"REALIZATION\",\"DEPENDENCY\"]";

    /** Herramienta para comandos incrementales por texto/voz sobre un diagrama que ya existe. */
    public static String applyOperationsTool() {
        return """
        {
          "name": "apply_diagram_operations",
          "description": "Aplica una lista de operaciones estructuradas sobre el diagrama de clases UML actual. Esta es la UNICA forma de modificar el diagrama: nunca describas el diagrama en prosa como si lo hubieras dibujado, siempre devuelve las operaciones exactas a aplicar.",
          "input_schema": {
            "type": "object",
            "properties": {
              "assistantMessage": {
                "type": "string",
                "description": "Mensaje breve en espanol, en tono natural, confirmando al usuario que se hizo (se le muestra y se le puede leer en voz alta)."
              },
              "operations": {
                "type": "array",
                "items": %s
              }
            },
            "required": ["assistantMessage", "operations"]
          }
        }
        """.formatted(operationItemSchema());
    }

    /** Herramienta para extraer un diagrama completo a partir de una foto (pizarra, boceto, diagrama impreso, etc). */
    public static String extractDiagramFromImageTool() {
        return """
        {
          "name": "extract_diagram_from_image",
          "description": "Devuelve el diagrama de clases UML completo que aparece en la imagen, como una lista de operaciones CREATE_CLASS, ADD_ATTRIBUTE y CREATE_RELATIONSHIP (en ese orden logico: primero todas las clases, luego sus atributos, luego las relaciones).",
          "input_schema": {
            "type": "object",
            "properties": {
              "assistantMessage": {
                "type": "string",
                "description": "Resumen breve en espanol de lo que se reconocio en la imagen."
              },
              "operations": {
                "type": "array",
                "items": %s
              }
            },
            "required": ["assistantMessage", "operations"]
          }
        }
        """.formatted(operationItemSchema());
    }

    private static String operationItemSchema() {
        return """
        {
          "type": "object",
          "properties": {
            "type": { "type": "string", "enum": %s },
            "className": { "type": "string", "description": "Nombre de la clase a crear (para CREATE_CLASS) o nombre de la clase objetivo (para ADD_ATTRIBUTE, DELETE_CLASS, MOVE_CLASS). Ejemplo: 'Cliente', 'Producto', 'Pedido'." },
            "newName": { "type": "string", "description": "Solo para RENAME_CLASS: nuevo nombre que tendra la clase." },
            "isAbstract": { "type": "boolean" },
            "x": { "type": "number", "description": "Posicion X en el lienzo (ej: 100, 350, 600)." },
            "y": { "type": "number", "description": "Posicion Y en el lienzo (ej: 100, 300)." },
            "attributeName": { "type": "string", "description": "Nombre del atributo (ej: 'id', 'nombre', 'precio', 'email')." },
            "newAttributeName": { "type": "string", "description": "Solo para UPDATE_ATTRIBUTE: nuevo nombre del atributo." },
            "dataType": { "type": "string", "enum": %s, "description": "Tipo de dato: STRING, INTEGER, LONG, DOUBLE, DECIMAL, BOOLEAN, DATE, DATETIME, UUID." },
            "visibility": { "type": "string", "enum": %s, "description": "Visibilidad del atributo (PUBLIC, PRIVATE, PROTECTED, PACKAGE)." },
            "isPrimaryKey": { "type": "boolean", "description": "True si este atributo es clave primaria (ej: id)." },
            "nullable": { "type": "boolean" },
            "unique": { "type": "boolean" },
            "sourceClassName": { "type": "string", "description": "Nombre de la clase origen de la relacion (ej: 'Cliente'). NUNCA tipos de datos como BOOLEAN o STRING." },
            "targetClassName": { "type": "string", "description": "Nombre de la clase destino de la relacion (ej: 'Pedido'). NUNCA tipos de datos como BOOLEAN o STRING." },
            "relationshipType": { "type": "string", "enum": %s, "description": "Tipo de relacion: ASSOCIATION, AGGREGATION, COMPOSITION, GENERALIZATION, REALIZATION, DEPENDENCY." },
            "sourceMultiplicity": { "type": "string", "description": "Multiplicidad UML del lado origen: '1', '0..1', '*', '1..*', '0..*'." },
            "targetMultiplicity": { "type": "string", "description": "Multiplicidad UML del lado destino: '1', '0..1', '*', '1..*', '0..*'." },
            "sourceRoleName": { "type": "string" },
            "targetRoleName": { "type": "string" },
            "label": { "type": "string", "description": "Nombre, verbo o rol de la relacion (ej: 'vende', 'compra', 'tiene', 'supervisa', 'pertenece')." },
            "rationale": { "type": "string", "description": "Por que se hizo esta operacion." }
          },
          "required": ["type"]
        }
        """.formatted(OPERATION_TYPES, DATA_TYPES, VISIBILITIES, RELATIONSHIP_TYPES);
    }
}
