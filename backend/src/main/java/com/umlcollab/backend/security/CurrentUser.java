package com.umlcollab.backend.security;

import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/** Acceso rapido al usuario autenticado desde cualquier capa, sin inyectar el SecurityContext a mano en cada sitio. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthenticatedUserPrincipal get() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof AuthenticatedUserPrincipal p) {
            return p;
        }
        throw new IllegalStateException("No hay un usuario autenticado en el contexto de seguridad");
    }

    public static UUID id() {
        return get().getUserId();
    }
}
