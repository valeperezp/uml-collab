package com.umlcollab.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {
    @NotBlank
    @Size(min = 3, max = 60)
    private String username;

    @NotBlank
    private String displayName;

    @NotBlank
    @Size(min = 6)
    private String password;
}
