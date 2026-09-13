package com.umlcollab.backend.dto.requests;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.umlcollab.backend.model.DataType;
import com.umlcollab.backend.model.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AttributeRequest {
    @NotBlank
    private String name;
    @NotNull
    private DataType dataType;
    private Visibility visibility;

    // Ver el comentario equivalente en CreateClassRequest: fijamos el nombre
    // JSON a mano para que sea "isPrimaryKey" y no "primaryKey".
    @Getter(onMethod_ = @__(@JsonProperty("isPrimaryKey")))
    @Setter(onMethod_ = @__(@JsonProperty("isPrimaryKey")))
    private boolean isPrimaryKey;

    private boolean nullable = true;
    private boolean unique = false;
}
