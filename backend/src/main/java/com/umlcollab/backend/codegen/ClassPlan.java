package com.umlcollab.backend.codegen;

import java.util.ArrayList;
import java.util.List;

/** Todo lo que hace falta saber de una UmlClass para generar sus 5 capas (Entity/DTO/Repository/Service/Controller/DTO). */
public class ClassPlan {
    public String className;      // PascalCase, nombre de la entidad Java
    public String originalName;   // nombre tal cual estaba en el diagrama
    public String tableName;      // snake_case
    public boolean isAbstract;
    public String superClassName; // null si no hereda de otra clase del diagrama
    public List<FieldPlan> fields = new ArrayList<>();
    public FieldPlan idField;
    public List<RelationPlan> relations = new ArrayList<>();

    public String restBasePath() {
        return "/api/" + NameUtils.pluralize(NameUtils.camelCase(className));
    }
}
