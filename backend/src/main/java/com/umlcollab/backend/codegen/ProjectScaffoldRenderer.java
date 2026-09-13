package com.umlcollab.backend.codegen;

import org.springframework.stereotype.Component;

import java.util.List;

/** Los archivos "de alrededor" del backend generado: pom.xml, properties, clase main, README. */
@Component
public class ProjectScaffoldRenderer {

    public String pomXml(String artifactId, String basePackage) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>

                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.3.4</version>
                        <relativePath/>
                    </parent>

                    <groupId>com.generated</groupId>
                    <artifactId>%s</artifactId>
                    <version>0.1.0</version>
                    <name>%s</name>
                    <description>Backend generado automaticamente por la herramienta colaborativa de diseno de BD a partir del diagrama de clases</description>

                    <properties>
                        <java.version>21</java.version>
                    </properties>

                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-data-jpa</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-validation</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.postgresql</groupId>
                            <artifactId>postgresql</artifactId>
                            <scope>runtime</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <optional>true</optional>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-test</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>

                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                                <configuration>
                                    <excludes>
                                        <exclude>
                                            <groupId>org.projectlombok</groupId>
                                            <artifactId>lombok</artifactId>
                                        </exclude>
                                    </excludes>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """.formatted(artifactId, artifactId);
    }

    public String applicationProperties(String artifactId) {
        return """
                spring.application.name=%s

                spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/%s}
                spring.datasource.username=${DB_USERNAME:postgres}
                spring.datasource.password=${DB_PASSWORD:postgres}
                spring.datasource.driver-class-name=org.postgresql.Driver

                spring.jpa.hibernate.ddl-auto=update
                spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
                spring.jpa.show-sql=true

                server.port=${SERVER_PORT:8081}
                """.formatted(artifactId, artifactId.replace("-", "_"));
    }

    public String applicationClass(String basePackage, String artifactId) {
        return """
                package %s;

                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;

                /**
                 * Backend generado automaticamente a partir del diagrama de clases "%s"
                 * por la herramienta colaborativa de diseno de base de datos.
                 * Capas: model (entidades JPA) / repository / service / controller / dto.
                 */
                @SpringBootApplication
                public class Application {
                    public static void main(String[] args) {
                        SpringApplication.run(Application.class, args);
                    }
                }
                """.formatted(basePackage, artifactId);
    }

    public String gitignore() {
        return "target/\n*.class\n.idea/\n*.iml\n.vscode/\n.DS_Store\n";
    }

    public String readme(String diagramName, List<ClassPlan> plans) {
        StringBuilder classList = new StringBuilder();
        for (ClassPlan p : plans) {
            classList.append("- `").append(p.className).append("` -> tabla `").append(p.tableName).append("`");
            if (p.superClassName != null) classList.append(" (extiende `").append(p.superClassName).append("`)");
            classList.append(", endpoints en `").append(p.restBasePath()).append("`\n");
        }
        return """
                # Backend generado: %s

                Este backend fue generado automaticamente por la herramienta colaborativa de
                diseno de bases de datos a partir del diagrama de clases "%s". No lo edites a
                mano si vas a seguir iterando el diagrama y regenerando: cualquier cambio manual
                se perderia en la proxima generacion.

                ## Como correrlo

                1. Tene una base Postgres corriendo (por ejemplo con Docker):
                   `docker run --name pg -e POSTGRES_PASSWORD=postgres -p 5432:5432 -d postgres:16`
                2. Crea la base de datos (el nombre por defecto esta en `application.properties`).
                3. `mvn spring-boot:run` (o abrilo en tu IDE y correr `Application`).
                4. El backend queda escuchando en `http://localhost:8081`.

                ## Capas generadas

                Cada clase del diagrama genero sus 5 capas: `model` (entidad JPA), `repository`
                (Spring Data JPA), `service` (logica + mapeo a DTO), `controller` (REST) y `dto`
                (para no exponerle la entidad completa al frontend).

                %s

                ## Notas

                - Las relaciones se mapearon a JPA segun las multiplicidades del diagrama
                  (`@OneToMany`/`@ManyToOne`/`@OneToOne`/`@ManyToMany`), y la composicion se
                  tradujo en cascada + `orphanRemoval` del lado "todo".
                - La herencia (generalizacion) se genero con `@Inheritance(SINGLE_TABLE)`.
                - Los DTOs solo devuelven los IDs de las relaciones, nunca la entidad completa,
                  para evitar ciclos de serializacion; en los `Service.create/update` hay un
                  `TODO` donde corresponde resolver esos IDs a la entidad relacionada real.
                """.formatted(diagramName, diagramName, classList);
    }
}
