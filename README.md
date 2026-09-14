# Fraud Detection Platform

Plataforma de **detección de fraude en transacciones en tiempo real** para el sector financiero. Recibe transacciones, las evalúa contra un motor de reglas configurable (y, en fases futuras, un modelo de ML), y decide **aprobar / bloquear / marcar para revisión manual** — siempre dejando registrada la justificación de la decisión.

Proyecto de portafolio orientado a roles **Backend + IA**: el foco no es solo "que funcione", sino demostrar diseño de arquitectura backend robusta, manejo de flujos configurables, y una integración seria de ML en un sistema productivo (no un notebook aislado).

## Estado actual

| Fase | Descripción | Estado |
|---|---|---|
| 1 | Esqueleto backend — CRUD, autenticación JWT, Postgres | ✅ Completa |
| 2 | Motor de reglas configurable (monto, velocidad, geolocalización) | ✅ Completa |
| 3 | Integración Kafka — ingestión asíncrona, generador de volumen | ✅ Completa |
| 4 | Servicio de ML (FastAPI) entrenado con datos reales | ✅ Completa |
| 5 | Explicabilidad (SHAP) y dashboard de métricas | ⏳ Pendiente |
| 6 | Tests de integración, documentación, despliegue | 🔶 En progreso |

## Arquitectura

```
[Cliente/API] ──► [Spring Boot API] ──► [PostgreSQL] (status PENDING)
                        │                     │
                        │                     ▼
                        │            [Kafka: topic "transactions"]
                        │                     │
                        │                     ▼
                        │        [Consumer: motor de reglas] ──► [Redis]           (velocity checks)
                        │                     │               └► [ML Service FastAPI] (risk score)
                        │                     ▼                     │
                        └────────► [PostgreSQL] (status final: APPROVED / REVIEW / BLOCKED + reason)
```

Flujo de una transacción hoy (asíncrono):
1. `POST /transactions` con JWT válido. Se valida y se busca al usuario dueño de la transacción.
2. Se persiste inmediatamente con `status: PENDING` y se responde **`202 Accepted`** — la ingestión queda desacoplada de la evaluación.
3. Tras el commit, se publica un evento al topic Kafka `transactions`.
4. Un **consumer** separado lee el evento y evalúa la transacción contra el motor de reglas: `HighAmountRule`, `VelocityRule`, `GeoMismatchRule` y `MlScoringRule` (que le pide un score de riesgo al servicio de ML). Actualiza `status` y `reason` en Postgres — ninguna decisión queda "silenciosa", incluidas las aprobadas.
5. El cliente consulta `GET /transactions/{id}` para ver el resultado final una vez procesado.

## Stack técnico

- **Backend:** Java 21 + Spring Boot 4.1 (Web, Data JPA, Security, Validation, Data Redis)
- **Base de datos:** PostgreSQL, migraciones versionadas con Flyway
- **Cache:** Redis (velocity checks del motor de reglas)
- **Mensajería:** Kafka — topic `transactions`, desacopla la ingestión de la evaluación del motor de reglas
- **Autenticación:** JWT propio (`jjwt`), passwords con BCrypt
- **Contenedores:** Docker + docker-compose (Postgres, Redis, Kafka, ML service)
- **Testing:** JUnit 5 + Testcontainers (Postgres, Redis y Kafka reales en los tests de integración)
- **Servicio de ML:** Python + FastAPI, `scikit-learn` (LogisticRegression interpretable), entrenado con un dataset real de fraude de Hugging Face (ver [`ml/`](ml/))

## Estructura del repo

```
backend/    Spring Boot — API REST, motor de reglas, persistencia
ml/         Servicio de ML (FastAPI) + entrenamiento + scripts de validación/carga
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

# Levanta Postgres, Redis, Kafka y el servicio de ML (FastAPI)
docker-compose up -d

# Corre la app (los defaults de application.properties ya coinciden con
# los del docker-compose, no hace falta crear un .env para desarrollo local)
./mvnw spring-boot:run
```

La API queda disponible en `http://localhost:8080`. El servicio de ML queda en `http://localhost:8000` (`GET /health`, `POST /score`).

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
| POST | `/transactions` | Crear transacción — responde `202 Accepted` con `status: PENDING`; se evalúa de forma asíncrona (ver `GET /transactions/{id}`) | Sí |
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

TX_ID=$(curl -s -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"userId":"<id-del-usuario>","amount":15000,"currency":"USD","merchant":"Amazon","country":"US"}' | jq -r .id)
# -> "status": "PENDING" (aceptada, aún no evaluada)

sleep 2   # tiempo para que el consumer del motor de reglas la procese

curl http://localhost:8080/transactions/$TX_ID -H "Authorization: Bearer $TOKEN"
# -> "status": "BLOCKED", "reason": "Monto 15000 supera el umbral de bloqueo 10000"
```

## Motor de reglas

Corre de forma **asíncrona**, disparado por el consumer de Kafka (`TransactionFraudConsumer`) al recibir el evento de una transacción nueva — ya no en el hilo del request HTTP. Umbrales configurables en `application.properties` (`fraud.rules.*`), sin necesidad de tocar código:

| Regla | Qué evalúa | Umbral por defecto |
|---|---|---|
| `HighAmountRule` | Monto de la transacción | `> 1000` → revisión, `> 10000` → bloqueo |
| `VelocityRule` | Transacciones del mismo usuario en una ventana de tiempo (Redis) | `> 5` en `300s` → revisión |
| `GeoMismatchRule` | País distinto al de la última transacción del usuario | cualquier cambio → revisión |
| `MlScoringRule` | Score de riesgo devuelto por el [servicio de ML](#servicio-de-ml-ml) (Fase 4) | `>= 0.5` → revisión, `>= 0.85` → bloqueo |

Si ninguna regla se activa, la transacción queda `APPROVED` con el motivo `"Ninguna regla activada"` — toda decisión, incluso la positiva, queda justificada.

`MlScoringRule` es **fail-open**: si el servicio de ML no responde a tiempo (timeout configurable, default 2s) o está caído, se loguea un `WARN` y esa regla simplemente no aporta nada a la decisión — no bloquea el pipeline asíncrono ni tira abajo la evaluación del resto de las reglas.

## Servicio de ML (`ml/`)

Microservicio Python + FastAPI, separado del backend Java (corre en su propio proceso/contenedor), que expone:

- `POST /score` — recibe `{ amount, merchant, country, timestamp }` y devuelve `{ riskScore, modelVersion, topFactors }`. `topFactors` son las features con mayor contribución al score (coeficiente × valor escalado), para no dejar el modelo como caja negra incluso antes de integrar SHAP (Fase 5).
- `GET /health` — chequeo de salud.

**Modelo:** `LogisticRegression` (interpretable, `class_weight="balanced"`) dentro de un `Pipeline` con `StandardScaler`, entrenado con el dataset real [`pointe77/credit-card-transaction`](https://huggingface.co/datasets/pointe77/credit-card-transaction) (mismo dataset ya usado en `validate_backend.py`), usando los splits `train`/`test` reales del dataset. Métricas actuales (ver `ml/model/metadata.json`): ROC-AUC ≈ 0.84, recall de fraude ≈ 0.71.

**Límite de diseño conocido (paridad train/serve):** `POST /transactions` del backend solo captura `amount`, `merchant`, `country` y `currency` — no la categoría, geolocalización ni demografía que sí tiene el dataset completo. Para no entrenar con columnas que nunca van a existir en producción, el modelo v1 solo usa **features reproducibles en tiempo real**: el monto y features derivadas del timestamp (`log_amount`, `hour_of_day`, `day_of_week`, `is_weekend`). Enriquecer el modelo con las señales que ya calculan `VelocityRule`/`GeoMismatchRule` es un candidato natural para la Fase 5.

```bash
cd ml

# Servir el modelo ya entrenado (commiteado en ml/model/)
.venv/Scripts/pip install -r requirements.txt   # Linux/Mac: .venv/bin/pip
.venv/Scripts/uvicorn service.main:app --port 8000   # Linux/Mac: .venv/bin/uvicorn

# Reentrenar (descarga el dataset de Hugging Face, sobreescribe ml/model/)
.venv/Scripts/pip install -r requirements-train.txt
.venv/Scripts/python train_model.py
```

El `Dockerfile` en `ml/` construye la imagen de *serving* (solo `requirements.txt`, sin las dependencias de entrenamiento) y es la que usa `docker-compose.yml`.

## Validación con dataset real (`ml/`)

Un script en Python ([`ml/validate_backend.py`](ml/validate_backend.py)) toma una muestra del dataset [`pointe77/credit-card-transaction`](https://huggingface.co/datasets/pointe77/credit-card-transaction) (Hugging Face) y la envía contra la API real, para validar que el modelo de datos tolera transacciones con forma real. El mismo dataset se reutiliza para entrenar el modelo de ML (ver [Servicio de ML](#servicio-de-ml-ml)).

```bash
cd ml
python -m venv .venv
.venv/bin/pip install -r requirements.txt        # Windows: .venv\Scripts\pip install -r requirements.txt
SAMPLE_SIZE=200 .venv/bin/python validate_backend.py   # Windows: .venv\Scripts\python validate_backend.py
```

## Simulación de volumen (`ml/generate_load.py`)

Un segundo script ([`ml/generate_load.py`](ml/generate_load.py)) genera tráfico sintético contra la API real para demostrar el pipeline asíncrono end-to-end (ingestión vía `202 Accepted` desacoplada de la evaluación del motor de reglas vía Kafka). Recicla un pool pequeño de usuarios para poder disparar `VelocityRule` y `GeoMismatchRule`, que dependen de actividad repetida por usuario, e inyecta una fracción configurable de transacciones deliberadamente anómalas.

```bash
cd ml
.venv/bin/python generate_load.py --rate 10 --duration 30 --users 10 --anomaly-ratio 0.1
# o, por cantidad fija en vez de duración:
.venv/bin/python generate_load.py --rate 20 --count 500
```

Al terminar, imprime el throughput real logrado y, tras una breve espera, la distribución final de `status` (`APPROVED` / `REVIEW` / `BLOCKED`) sobre una muestra de las transacciones enviadas — evidencia de que el pipeline async efectivamente resolvió las decisiones.

## Licencia

[GPL-3.0](LICENSE) — cualquier distribución del código (o de derivados) debe mantenerse open source bajo la misma licencia.
