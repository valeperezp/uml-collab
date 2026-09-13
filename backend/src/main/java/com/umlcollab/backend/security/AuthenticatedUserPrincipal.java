package com.umlcollab.backend.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/** Principal ligero puesto en el SecurityContext tras validar el JWT: evita otra consulta a la BD por request. */
@Getter
@AllArgsConstructor
public class AuthenticatedUserPrincipal {
    private final UUID userId;
    private final String username;
}
