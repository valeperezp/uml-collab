package com.umlcollab.backend.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class AuthResponse {
    private String token;
    private UUID userId;
    private String username;
    private String displayName;
    private String colorHex;
}
