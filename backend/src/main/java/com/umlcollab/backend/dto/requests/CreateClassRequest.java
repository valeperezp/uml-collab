package com.umlcollab.backend.dto.requests;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateClassRequest {
    @NotBlank
    private String name;
    private String stereotype;

    // Sin esto Jackson leeria el JSON como "abstract" en vez de "isAbstract"
    // (Lombok genera isAbstract()/setAbstract() para un boolean primitivo
    // llamado asi, y Jackson deriva el nombre de propiedad del accessor).
    @Getter(onMethod_ = @__(@JsonProperty("isAbstract")))
    @Setter(onMethod_ = @__(@JsonProperty("isAbstract")))
    private boolean isAbstract;

    private double x;
    private double y;
}
