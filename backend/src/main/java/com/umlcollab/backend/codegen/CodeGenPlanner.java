package com.umlcollab.backend.codegen;

import com.umlcollab.backend.dto.AttributeDto;
import com.umlcollab.backend.dto.ClassDto;
import com.umlcollab.backend.dto.DiagramDetailDto;
import com.umlcollab.backend.dto.RelationshipDto;
import com.umlcollab.backend.model.RelationshipType;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Convierte el modelo persistido (clases, atributos, relaciones) en un plan
 * de generacion: que campos y que asociaciones JPA le tocan a cada clase.
 * Esta es la parte "inteligente" del generador de codigo: interpreta
 * multiplicidades y tipos de relacion UML para decidir @OneToMany vs
 * @ManyToOne vs @ManyToMany vs @OneToOne, y quien es el lado dueno.
 */
@Component
public class CodeGenPlanner {

    public List<ClassPlan> plan(DiagramDetailDto diagram) {
        Map<UUID, ClassPlan> plansById = new LinkedHashMap<>();
        Map<UUID, ClassDto> classesById = new LinkedHashMap<>();

        for (ClassDto c : diagram.getClasses()) {
            classesById.put(c.getId(), c);
            ClassPlan plan = new ClassPlan();
            plan.originalName = c.getName();
            plan.className = NameUtils.pascalCase(c.getName());
            plan.tableName = NameUtils.snakeCase(c.getName());
            plan.isAbstract = c.isAbstract();

            for (AttributeDto a : c.getAttributes()) {
                FieldPlan field = FieldPlan.of(
                        NameUtils.camelCase(a.getName()),
                        a.getDataType().simpleJavaType(),
                        a.getDataType().importIfNeeded(),
                        a.isNullable(),
                        a.isUnique(),
                        a.isPrimaryKey(),
                        false);
                plan.fields.add(field);
                if (field.isId && plan.idField == null) {
                    plan.idField = field;
                }
            }
            if (plan.idField == null) {
                FieldPlan generatedId = FieldPlan.of("id", "UUID", "java.util.UUID", false, true, true, true);
                plan.fields.add(0, generatedId);
                plan.idField = generatedId;
            }
            plansById.put(c.getId(), plan);
        }

        // Herencia: para cada GENERALIZATION, la clase origen extiende la clase destino en Java.
        for (RelationshipDto r : diagram.getRelationships()) {
            if (r.getType() == RelationshipType.GENERALIZATION) {
                ClassPlan child = plansById.get(r.getSourceClassId());
                ClassPlan parent = plansById.get(r.getTargetClassId());
                if (child != null && parent != null) {
                    child.superClassName = parent.className;
                }
            }
        }

        // Asociaciones (incluye agregacion/composicion, tratadas igual estructuralmente + cascada en composicion).
        int anonCounter = 0;
        for (RelationshipDto r : diagram.getRelationships()) {
            if (r.getType() == RelationshipType.GENERALIZATION) continue;
            ClassPlan sourcePlan = plansById.get(r.getSourceClassId());
            ClassPlan targetPlan = plansById.get(r.getTargetClassId());
            if (sourcePlan == null || targetPlan == null) continue;

            boolean srcMany = isMany(r.getSourceMultiplicity());
            boolean tgtMany = isMany(r.getTargetMultiplicity());
            boolean composition = r.getType() == RelationshipType.COMPOSITION;
            anonCounter++;

            if (!srcMany && !tgtMany) {
                addOneToOne(sourcePlan, targetPlan, r, composition);
            } else if (!srcMany && tgtMany) {
                addOneToMany(sourcePlan, targetPlan, r, composition);
            } else if (srcMany && !tgtMany) {
                addOneToMany(targetPlan, sourcePlan, invert(r), composition);
            } else {
                addManyToMany(sourcePlan, targetPlan, r, anonCounter);
            }
        }

        return new ArrayList<>(plansById.values());
    }

    private void addOneToOne(ClassPlan owningSideOne, ClassPlan otherSide, RelationshipDto r, boolean composition) {
        // Por convencion, el lado "source" es el dueno (tiene la FK).
        RelationPlan owning = new RelationPlan();
        owning.kind = RelationPlan.Kind.ONE_TO_ONE;
        owning.collection = false;
        owning.owningSide = true;
        owning.targetClassName = otherSide.className;
        owning.fieldName = uniqueFieldName(owningSideOne, NameUtils.camelCase(otherSide.originalName));
        owning.joinColumnName = NameUtils.snakeCase(owning.fieldName) + "_id";
        owning.cascadeAll = composition;
        owningSideOne.relations.add(owning);

        RelationPlan inverse = new RelationPlan();
        inverse.kind = RelationPlan.Kind.ONE_TO_ONE;
        inverse.collection = false;
        inverse.owningSide = false;
        inverse.targetClassName = owningSideOne.className;
        inverse.fieldName = uniqueFieldName(otherSide, NameUtils.camelCase(owningSideOne.originalName));
        inverse.mappedBy = owning.fieldName;
        otherSide.relations.add(inverse);
    }

    /** "one" tiene la coleccion (OneToMany, mappedBy), "many" tiene la FK (ManyToOne, dueno). */
    private void addOneToMany(ClassPlan one, ClassPlan many, RelationshipDto r, boolean composition) {
        RelationPlan manyToOne = new RelationPlan();
        manyToOne.kind = RelationPlan.Kind.MANY_TO_ONE;
        manyToOne.collection = false;
        manyToOne.owningSide = true;
        manyToOne.targetClassName = one.className;
        manyToOne.fieldName = uniqueFieldName(many, NameUtils.camelCase(one.originalName));
        manyToOne.joinColumnName = NameUtils.snakeCase(manyToOne.fieldName) + "_id";
        many.relations.add(manyToOne);

        RelationPlan oneToMany = new RelationPlan();
        oneToMany.kind = RelationPlan.Kind.ONE_TO_MANY;
        oneToMany.collection = true;
        oneToMany.owningSide = false;
        oneToMany.targetClassName = many.className;
        oneToMany.fieldName = uniqueFieldName(one, NameUtils.pluralize(NameUtils.camelCase(many.originalName)));
        oneToMany.mappedBy = manyToOne.fieldName;
        oneToMany.cascadeAll = composition;
        one.relations.add(oneToMany);
    }

    private void addManyToMany(ClassPlan source, ClassPlan target, RelationshipDto r, int counter) {
        RelationPlan owning = new RelationPlan();
        owning.kind = RelationPlan.Kind.MANY_TO_MANY;
        owning.collection = true;
        owning.owningSide = true;
        owning.targetClassName = target.className;
        owning.fieldName = uniqueFieldName(source, NameUtils.pluralize(NameUtils.camelCase(target.originalName)));
        owning.joinTableName = source.tableName + "_" + target.tableName;
        owning.joinColumnSelf = source.tableName + "_id";
        owning.joinColumnOther = target.tableName + "_id";
        source.relations.add(owning);

        RelationPlan inverse = new RelationPlan();
        inverse.kind = RelationPlan.Kind.MANY_TO_MANY;
        inverse.collection = true;
        inverse.owningSide = false;
        inverse.targetClassName = source.className;
        inverse.fieldName = uniqueFieldName(target, NameUtils.pluralize(NameUtils.camelCase(source.originalName)));
        inverse.mappedBy = owning.fieldName;
        target.relations.add(inverse);
    }

    private RelationshipDto invert(RelationshipDto r) {
        RelationshipDto inverted = new RelationshipDto();
        inverted.setSourceClassId(r.getTargetClassId());
        inverted.setSourceClassName(r.getTargetClassName());
        inverted.setTargetClassId(r.getSourceClassId());
        inverted.setTargetClassName(r.getSourceClassName());
        inverted.setSourceMultiplicity(r.getTargetMultiplicity());
        inverted.setTargetMultiplicity(r.getSourceMultiplicity());
        inverted.setType(r.getType());
        return inverted;
    }

    private boolean isMany(String multiplicity) {
        return multiplicity != null && multiplicity.contains("*");
    }

    private String uniqueFieldName(ClassPlan plan, String candidate) {
        String name = candidate;
        int suffix = 2;
        Set<String> used = new HashSet<>();
        plan.fields.forEach(f -> used.add(f.name));
        plan.relations.forEach(r -> used.add(r.fieldName));
        while (used.contains(name)) {
            name = candidate + suffix++;
        }
        return name;
    }
}
