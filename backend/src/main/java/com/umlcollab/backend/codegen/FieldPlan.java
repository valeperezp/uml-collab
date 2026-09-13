package com.umlcollab.backend.codegen;

/** Un campo simple (no-relacion) del entity generado, calcado de un UmlAttribute. */
public class FieldPlan {
    public String name;
    public String javaType;      // tipo simple, ej. "String", "UUID"
    public String javaImport;    // paquete completo a importar, o null si es java.lang/primitivo
    public boolean nullable;
    public boolean unique;
    public boolean isId;
    public boolean generated;    // true si el id fue sintetizado por el generador (la clase no tenia PK explicita)

    public static FieldPlan of(String name, String javaType, String javaImport, boolean nullable, boolean unique, boolean isId, boolean generated) {
        FieldPlan f = new FieldPlan();
        f.name = name;
        f.javaType = javaType;
        f.javaImport = javaImport;
        f.nullable = nullable;
        f.unique = unique;
        f.isId = isId;
        f.generated = generated;
        return f;
    }
}
