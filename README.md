# Fraud Detection Platform

Plataforma de **detección de fraude en transacciones en tiempo real** para el sector financiero. Recibe transacciones, las evalúa contra un motor de reglas configurable (y, en fases futuras, un modelo de ML), y decide **aprobar / bloquear / marcar para revisión manual** — siempre dejando registrada la justificación de la decisión.

Proyecto de portafolio orientado a roles **Backend + IA**: el foco no es solo "que funcione", sino demostrar diseño de arquitectura backend robusta, manejo de flujos configurables, y una integración seria de ML en un sistema productivo (no un notebook aislado).

## Estado actual

| Fase | Descripción | Estado |
|---|---|---|
| 1 | Esqueleto backend — CRUD, autenticación JWT, Postgres | ✅ Completa |
| 2 | Motor de reglas configurable (monto, velocidad, geolocalización) | ✅ Completa |
| 3 | Integración Kafka — ingestión asíncrona, generador de volumen | ⏳ Pendiente |
| 4 | Servicio de ML (FastAPI) entrenado con datos reales | ⏳ Pendiente (dataset ya validado, ver [`ml/`](ml/)) |
| 5 | Explicabilidad (SHAP) y dashboard de métricas | ⏳ Pendiente |
| 6 | Tests de integración, documentación, despliegue | 🔶 En progreso |

## Arquitectura

```
[Cliente/API] ──► [Spring Boot API] ──► [PostgreSQL]   (usuarios, transacciones, decisiones)
                        │
                        ├──► [Redis]    (velocity checks del motor de reglas)
                        │
                        ├──► [Kafka]    (declarado en el stack, aún sin uso — Fase 3)
                        │
                        └──► [Motor de reglas] ──► decisión (APPROVED / REVIEW / BLOCKED) + justificación
```

Flujo de una transacción hoy:
1. `POST /transactions` con JWT válido.
2. Se valida y se busca al usuario dueño de la transacción.
3. El **motor de reglas** la evalúa de forma síncrona (`HighAmountRule`, `VelocityRule`, `GeoMismatchRule`).
4. Se persiste con su `status` final y el `reason` que explica la decisión — ninguna decisión queda "silenciosa", incluidas las aprobadas.

## Stack técnico

- **Backend:** Java 21 + Spring Boot 4.1 (Web, Data JPA, Security, Validation, Data Redis)
- **Base de datos:** PostgreSQL, migraciones versionadas con Flyway
- **Cache:** Redis (velocity checks del motor de reglas)
- **Mensajería:** Kafka (declarado en el stack, se integra en la Fase 3)
- **Autenticación:** JWT propio (`jjwt`), passwords con BCrypt
- **Contenedores:** Docker + docker-compose (Postgres, Redis, Kafka)
- **Testing:** JUnit 5 + Testcontainers (Postgres, Redis y Kafka reales en los tests de integración)
- **Validación de datos:** script en Python que valida el backend contra un dataset real de fraude de Hugging Face (ver [`ml/`](ml/))

## Estructura del repo

```
backend/    Spring Boot — API REST, motor de reglas, persistencia
ml/         Script Python de validación con dataset real de Hugging Face
CLAUDE.md   Guía de arquitectura y convenciones del proyecto
```

Dentro de `backend/`, los paquetes están organizados por **dominio** (no por capa técnica):

```
auth/          registro, login, JWT, configuración de Security
user/          entidad de usuario y su CRUD
transaction/   entidad de transacción, DTOs, endpoints
fraud/         motor de reglas (HighAmountRule, VelocityRule, GeoMismatchRule)
common/        manejo de errores centralizado (@ControllerAdvice)
```

## Cómo correrlo localmente

Requisitos: Java 21, Docker.

```bash
cd backend

# Levanta Postgres y Redis (Kafka aún no se usa, no hace falta levantarlo)
docker-compose up -d postgres redis

# Corre la app (los defaults de application.properties ya coinciden con
# los del docker-compose, no hace falta crear un .env para desarrollo local)
./mvnw spring-boot:run
```

La API queda disponible en `http://localhost:8080`.

### Correr los tests

```bash
./mvnw test
```

Requiere Docker activo — los tests levantan Postgres, Redis y Kafka reales vía Testcontainers (no mocks).

## Endpoints

| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| POST | `/auth/register` | Registrar usuario | No |
| POST | `/auth/login` | Login, devuelve JWT | No |
| GET | `/users/{id}` | Detalle de usuario | Sí |
| GET | `/users` | Listar usuarios | Sí |
| DELETE | `/users/{id}` | Eliminar usuario | Sí |
| POST | `/transactions` | Crear transacción (evaluada por el motor de reglas) | Sí |
| GET | `/transactions/{id}` | Detalle de transacción | Sí |
| GET | `/transactions?userId=` | Listar transacciones (filtrable por usuario) | Sí |

### Ejemplo rápido

```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"demo@example.com","password":"password123","fullName":"Demo User"}'

TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"demo@example.com","password":"password123"}' | jq -r .token)

curl -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"userId":"<id-del-usuario>","amount":15000,"currency":"USD","merchant":"Amazon","country":"US"}'
# -> "status": "BLOCKED", "reason": "Monto 15000 supera el umbral de bloqueo 10000"
```

## Motor de reglas

Umbrales configurables en `application.properties` (`fraud.rules.*`), sin necesidad de tocar código:

| Regla | Qué evalúa | Umbral por defecto |
|---|---|---|
| `HighAmountRule` | Monto de la transacción | `> 1000` → revisión, `> 10000` → bloqueo |
| `VelocityRule` | Transacciones del mismo usuario en una ventana de tiempo (Redis) | `> 5` en `300s` → revisión |
| `GeoMismatchRule` | País distinto al de la última transacción del usuario | cualquier cambio → revisión |

Si ninguna regla se activa, la transacción queda `APPROVED` con el motivo `"Ninguna regla activada"` — toda decisión, incluso la positiva, queda justificada.

## Validación con dataset real (`ml/`)

Un script en Python ([`ml/validate_backend.py`](ml/validate_backend.py)) toma una muestra del dataset [`pointe77/credit-card-transaction`](https://huggingface.co/datasets/pointe77/credit-card-transaction) (Hugging Face) y la envía contra la API real, para validar que el modelo de datos tolera transacciones con forma real. El mismo dataset se reutilizará para entrenar el modelo de ML en la Fase 4.

```bash
cd ml
python -m venv .venv
.venv/bin/pip install -r requirements.txt        # Windows: .venv\Scripts\pip install -r requirements.txt
SAMPLE_SIZE=200 .venv/bin/python validate_backend.py   # Windows: .venv\Scripts\python validate_backend.py
```

## Licencia

[GPL-3.0](LICENSE) — cualquier distribución del código (o de derivados) debe mantenerse open source bajo la misma licencia.
