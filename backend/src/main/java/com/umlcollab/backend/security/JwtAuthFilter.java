package com.umlcollab.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String token = null;
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7);
        } else {
            // Permite pasar el token como query param para el handshake de WebSocket (SockJS no manda headers custom).
            token = request.getParameter("access_token");
        }

        if (token != null && jwtService.isValid(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            UUID userId = jwtService.extractUserId(token);
            String username = jwtService.extractUsername(token);
            AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(userId, username);
            var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
