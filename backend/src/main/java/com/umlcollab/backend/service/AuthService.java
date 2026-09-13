package com.umlcollab.backend.service;

import com.umlcollab.backend.dto.auth.AuthResponse;
import com.umlcollab.backend.dto.auth.LoginRequest;
import com.umlcollab.backend.dto.auth.RegisterRequest;
import com.umlcollab.backend.exception.BadRequestException;
import com.umlcollab.backend.model.User;
import com.umlcollab.backend.repository.UserRepository;
import com.umlcollab.backend.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Ya existe un usuario con ese nombre de usuario");
        }
        User user = User.builder()
                .username(request.getUsername())
                .displayName(request.getDisplayName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();
        user = userRepository.save(user);
        return toAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadRequestException("Usuario o contrasena incorrectos"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Usuario o contrasena incorrectos");
        }
        return toAuthResponse(user);
    }

    private AuthResponse toAuthResponse(User user) {
        String token = jwtService.generateToken(user.getId(), user.getUsername());
        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .colorHex(user.getColorHex())
                .build();
    }
}
