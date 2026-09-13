# UML Collab

Herramienta colaborativa de diseño de bases de datos: un editor de diagramas de clases (modelo conceptual) en tiempo real, con un agente de IA que edita el diagrama por comandos de texto o voz, reconocimiento de diagramas a partir de una foto, integración de importación/exportación con Enterprise Architect (XMI 2.1), y un generador que produce el backend Spring Boot completo (5 capas: modelo, repositorio, servicio, controlador y DTO) a partir del diagrama.

Este es el software que el equipo usa para **diseñar** el modelo de datos de cualquier sistema de forma colaborativa; no es el sistema final. El día de la presentación, a partir del backend que esta herramienta genera, se arma un frontend móvil (Flutter) de prueba — eso queda fuera del alcance de este repo, según lo acordado.

## Estructura del repo

```
backend/     Spring Boot 3.3 (Java 21) + PostgreSQL — la herramienta en sí
frontend/    Angular 18 (standalone components) — el editor colaborativo
docs/        Notas de arquitectura
```

## Cómo correrlo

### 1. Base de datos

```bash
docker run --name uml-collab-db -e POSTGRES_USER=uml_collab -e POSTGRES_PASSWORD=uml_collab \
  -e POSTGRES_DB=uml_collab -p 5442:5432 -d postgres:16
```

> Usamos el puerto host `5442` (en vez del `5432` por defecto de Postgres) para no chocar con otros proyectos que puedan tener su propio Postgres en `5432`. Si en tu máquina el `5432` está libre, podés usarlo y pasar `DB_URL=jdbc:postgresql://localhost:5432/uml_collab` al backend.

### 2. Backend

```bash
cd backend
export ANTHROPIC_API_KEY=sk-ant-...        # necesario para el agente de IA (texto/voz y foto)
export JWT_SECRET=cambia-esto-en-serio      # cualquier string largo random
mvn spring-boot:run
```

El backend escucha en `http://localhost:8080`. Variables de entorno relevantes (todas con default razonable para desarrollo local, ver `application.yml`):

| Variable | Para qué |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Conexión a Postgres |
| `JWT_SECRET` | Firma de los tokens de sesión |
| `ANTHROPIC_API_KEY` | Habilita el agente de IA (comandos y foto→diagrama). Sin esta variable, el resto de la app funciona normal, pero `/ai/command` y `/ai/image` devuelven error explicando que falta configurar la IA |
| `ANTHROPIC_MODEL` | Modelo a usar (default: un Claude reciente con soporte de tool-use y visión) |
| `CORS_ALLOWED_ORIGINS` | Default `http://localhost:4210` |

### 3. Frontend

```bash
cd frontend
npm install
npm start
```

Abre `http://localhost:4210` (puerto fijado en `angular.json` para no chocar con otros proyectos que usen el `4200` por defecto de Angular). Registrate con un usuario, creá un diagrama, y compartí el **código de invitación** (se ve en la barra superior) con tus compañeros para que se unan al mismo diagrama desde `Unirme a un diagrama` en el dashboard.

## Qué cubre cada parte del enunciado

- **Colaborativo, con exclusión mutua**: WebSocket (STOMP/SockJS) difunde cada cambio a todos los que tienen el diagrama abierto; un sistema de locks (`EditLock`) impide que dos personas editen la misma clase/relación a la vez — el que no tiene el lock ve el elemento pero no puede tocarlo, y se libera solo (o al ratito de inactividad) para que nadie se quede bloqueando el diagrama.
- **Dos formas de editar**: la clásica (crear/mover clases, agregar atributos, trazar relaciones a mano) y el agente de IA (`components/ai-panel`) por texto o por voz (Web Speech API del navegador transcribe, el backend interpreta el comando con tool-use y aplica operaciones concretas — nunca "le doy el problema y me arma el diagrama solo").
- **Foto → diagrama**: subís una imagen del pizarrón/boceto/otra herramienta y el mismo mecanismo de operaciones estructuradas puebla el diagrama.
- **Generador de backend**: botón "Generar backend" en la barra de herramientas descarga un `.zip` con un proyecto Maven Spring Boot listo (`mvn spring-boot:run`), con las 5 capas por cada clase del diagrama, mapeando las relaciones a `@OneToMany`/`@ManyToOne`/`@OneToOne`/`@ManyToMany` según las multiplicidades, y la herencia a `@Inheritance(SINGLE_TABLE)`.
- **Integración con Enterprise Architect**: exportar/importar XMI 2.1 (`/xmi/export`, `/xmi/import`) — subset funcional básico (clases, atributos con tipo/visibilidad, asociaciones/agregación/composición/herencia con multiplicidades).

## Limitaciones conocidas (léelo antes de la demo)

- El generador de backend deja `TODO`s en el `Service.create/update` donde haría falta resolver un ID de relación al objeto real (evita adivinar cómo querés resolver esas referencias).
- La integración XMI cubre un subconjunto razonable, no el 100% de lo que Enterprise Architect puede exportar; si importás un XMI muy complejo de EA, revisá el resultado.
- No se armó la app móvil todavía (a propósito, según lo conversado): el generador de backend es el entregable de esta herramienta.
- El código no se compiló en este entorno (sandbox sin acceso a Maven Central / npm registry) — se revisó a mano con mucho cuidado, pero corré `mvn compile` y `npm install && npm run build` apenas lo bajes, antes de confiarte del todo.
