# CLAUDE.md

Este archivo guía a Claude Code al trabajar en este repositorio.

## Visión del proyecto

Plataforma de **detección de fraude en transacciones en tiempo real** para el sector financiero. El sistema recibe transacciones, las evalúa mediante reglas configurables + un modelo de scoring, y decide **aprobar / bloquear / marcar para revisión manual**, exponiendo además un dashboard de explicabilidad y métricas de negocio.

Este es un proyecto de portafolio orientado a roles **Backend + IA**. El objetivo no es solo "que funcione", sino demostrar:
- Diseño de arquitectura backend robusta (no solo un CRUD).
- Manejo de flujos asíncronos y volumen simulado realista.
- Integración seria de un modelo de ML en un sistema productivo (no un notebook aislado).
- Explicabilidad y trazabilidad de decisiones (clave en fintech).

## Stack técnico

- **Backend:** Java 21 + Spring Boot 3.x
  - Spring Web (API REST)
  - Spring Data JPA
  - Spring Security (JWT) para autenticación de la API
  - Spring Validation
- **Base de datos:** PostgreSQL (histórico de transacciones, usuarios, decisiones)
- **Mensajería / colas:** Kafka (o RabbitMQ si se prioriza simplicidad) para simular flujo de eventos en tiempo real
- **Cache:** Redis (velocity checks, rate limiting, features en caliente)
- **Servicio de ML:** Python (FastAPI) como microservicio separado que expone `/score`, consumido por el backend Java vía HTTP o mensajería
  - Modelo: arrancar con algo interpretable (Logistic Regression / XGBoost) antes de complejizar
  - Explicabilidad: SHAP values sobre cada predicción
- **Contenedores:** Docker + docker-compose para levantar todo el stack localmente
- **Testing:** JUnit 5 + Mockito (backend), Testcontainers para tests de integración con Postgres/Kafka reales
- **Observabilidad:** Spring Actuator + Micrometer, logs estructurados (JSON)

> Nota: el modelo de ML vive en un microservicio Python separado, no embebido en Java. Esto refleja cómo se hace en la industria real (equipos de ML y backend desacoplados) y evita meter librerías de ML pesadas dentro de la JVM.

## Arquitectura (alto nivel)

```
[Cliente/API Gateway]
        │
        ▼
[Spring Boot API] ──► [PostgreSQL] (histórico, usuarios, decisiones)
        │
        ├──► [Redis] (velocity checks, cache de features)
        │
        ├──► [Kafka: topic "transactions"] ──► [Consumer: motor de reglas]
        │
        └──► [ML Service (FastAPI)] ──► [Modelo + SHAP]
```

Flujo de una transacción:
1. Llega vía API REST (`POST /transactions`).
2. Se validan datos y se enriquecen (features de velocidad, geolocalización, historial del usuario desde Redis/Postgres).
3. Se publica el evento en Kafka para procesamiento asíncrono y se evalúa contra reglas configurables (ej. monto atípico, país distinto al habitual).
4. Se consulta al servicio de ML para un score de riesgo.
5. Se combina score + reglas → decisión final (aprobar / bloquear / revisión manual).
6. Se persiste la decisión con su explicación (reglas activadas + SHAP values) para auditoría.

## Convenciones de código

- Paquetes por **feature/dominio**, no por capa técnica genérica (evitar `controller/`, `service/`, `repository/` como primer nivel; preferir `transaction/`, `fraud/`, `user/`, cada uno con su controller/service/repo interno).
- DTOs separados de entidades JPA — nunca exponer entidades directamente en la API.
- Usar `record` de Java para DTOs inmutables.
- Manejo de errores centralizado con `@ControllerAdvice`.
- Toda decisión de fraude debe quedar persistida con su justificación (nunca una decisión "silenciosa").
- Configuración de reglas de negocio externalizada (no hardcodeada) — pensar en un `application.yml` o tabla en BD para umbrales configurables.

## Fases del proyecto (roadmap sugerido)

1. **Fase 1 — Esqueleto backend:** CRUD de transacciones y usuarios, autenticación JWT, persistencia en Postgres.
2. **Fase 2 — Motor de reglas:** reglas simples configurables (monto, frecuencia, geolocalización) sin ML todavía.
3. **Fase 3 — Integración Kafka:** desacoplar ingestión de procesamiento, simular volumen con un generador de transacciones.
4. **Fase 4 — Servicio de ML:** entrenar modelo base con dataset público (ej. Kaggle Credit Card Fraud), exponerlo vía FastAPI, integrarlo al flujo.
5. **Fase 5 — Explicabilidad y dashboard:** SHAP values, métricas de negocio (falsos positivos, fraude no detectado, tiempo de decisión).
6. **Fase 6 — Pulido:** tests de integración con Testcontainers, documentación, despliegue con docker-compose, README con diagramas.

**Empezar por la Fase 1.** No avanzar a ML sin tener el backend base sólido y testeado.

## Qué evitar

- No meter lógica de scoring dentro del controller — debe vivir en el motor de reglas/servicio dedicado.
- No mezclar el microservicio de ML con el backend Java en el mismo proceso.
- No usar el modelo como caja negra: cada decisión debe ser explicable.
- No hardcodear credenciales — usar variables de entorno / `.env` (ya en `.gitignore`).

## Comandos útiles

```bash
docker-compose up -d          # levanta Postgres, Redis, Kafka
./mvnw spring-boot:run         # corre el backend
./mvnw test                    # corre tests
```

(Actualizar esta sección a medida que se agreguen scripts reales del proyecto.)