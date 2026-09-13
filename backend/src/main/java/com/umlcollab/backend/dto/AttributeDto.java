package com.umlcollab.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.umlcollab.backend.model.DataType;
import com.umlcollab.backend.model.Visibility;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttributeDto {
    private UUID id;
    private String name;
    private DataType dataType;
    private Visibility visibility;

    // Ver el comentario equivalente en ClassDto: sin fijar el nombre a mano,
    // Jackson expondria esto como "primaryKey" en vez de "isPrimaryKey".
    @Getter(onMethod_ = @__(@JsonProperty("isPrimaryKey")))
    @Setter(onMethod_ = @__(@JsonProperty("isPrimaryKey")))
    private boolean isPrimaryKey;

    private boolean nullable;
    private boolean unique;
    private int orderIndex;
}
