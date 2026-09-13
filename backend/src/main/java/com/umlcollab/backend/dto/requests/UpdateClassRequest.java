package com.umlcollab.backend.dto.requests;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateClassRequest {
    private String name;
    private String stereotype;
    private Boolean isAbstract;
    private Double x;
    private Double y;
}
