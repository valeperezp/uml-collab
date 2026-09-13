package com.umlcollab.backend.codegen;

/** Un campo de asociacion (referencia a otra entidad o coleccion) calculado a partir de una UmlRelationship. */
public class RelationPlan {

    public enum Kind { ONE_TO_ONE, ONE_TO_MANY, MANY_TO_ONE, MANY_TO_MANY }

    public String fieldName;         // nombre del campo Java en esta clase
    public String targetClassName;   // clase Java al otro lado
    public boolean collection;       // true si el campo es List<Target>
    public Kind kind;
    public boolean owningSide;       // true si esta clase tiene el @JoinColumn / @JoinTable
    public String joinColumnName;    // solo cuando owningSide && !collection-owning por FK (MANY_TO_ONE / ONE_TO_ONE owning)
    public String mappedBy;          // nombre del campo en la otra clase, cuando NO es el lado dueno
    public String joinTableName;     // solo MANY_TO_MANY, lado dueno
    public String joinColumnSelf;    // MANY_TO_MANY: columna que apunta a esta clase
    public String joinColumnOther;   // MANY_TO_MANY: columna que apunta a la otra clase
    public boolean cascadeAll;       // true para composicion (dueno del ciclo de vida)
}
