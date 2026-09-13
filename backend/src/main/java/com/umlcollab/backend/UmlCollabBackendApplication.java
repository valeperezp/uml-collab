package com.umlcollab.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de entrada de la herramienta colaborativa de diseno de bases de datos.
 *
 * Este backend NO es el software final del cliente: es la HERRAMIENTA que el
 * equipo de ingenieros usa para disenar, de forma colaborativa y asistida por
 * IA, el modelo conceptual (diagrama de clases) de cualquier sistema, y a
 * partir de ese modelo generar el backend Spring Boot correspondiente.
 */
@SpringBootApplication
@EnableScheduling
public class UmlCollabBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(UmlCollabBackendApplication.class, args);
    }
}
