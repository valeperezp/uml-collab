package com.umlcollab.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user", uniqueConstraints = @UniqueConstraint(columnNames = "username"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true, length = 60)
    private String username;

    @Column(nullable = false)
    private String displayName;

    /** Hash BCrypt, nunca la contrasena en texto plano. */
    @Column(nullable = false)
    private String passwordHash;

    /** Color hexadecimal asignado para identificar al usuario en el editor colaborativo (cursor, bloqueos, etc). */
    @Column(nullable = false, length = 7)
    private String colorHex;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (colorHex == null) {
            colorHex = randomColor();
        }
    }

    private static String randomColor() {
        String[] palette = {"#2563eb", "#dc2626", "#16a34a", "#d97706", "#7c3aed", "#0891b2", "#db2777", "#65a30d"};
        return palette[Math.abs(UUID.randomUUID().hashCode()) % palette.length];
    }
}
