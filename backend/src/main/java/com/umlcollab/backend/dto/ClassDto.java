package com.umlcollab.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassDto {
    private UUID id;
    private String name;
    private String stereotype;

    // Campo primitivo "isAbstract": sin este @JsonProperty explicito, Jackson
    // derivaria el nombre de propiedad JSON como "abstract" (le saca el "is"
    // al nombre del getter/setter generado por Lombok), rompiendo el contrato
    // con el frontend. Lo fijamos a mano en el getter y el setter generados.
    @Getter(onMethod_ = @__(@JsonProperty("isAbstract")))
    @Setter(onMethod_ = @__(@JsonProperty("isAbstract")))
    private boolean isAbstract;

    private double x;
    private double y;
    private List<AttributeDto> attributes;
}
