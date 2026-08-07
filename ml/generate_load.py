"""Simulate a stream of transactions against the running fraud-detection backend,
to demonstrate the Fase 3 async pipeline (POST /transactions -> Kafka -> motor de
reglas). Recycles a small pool of users so VelocityRule and GeoMismatchRule (which
depend on repeated activity per user) actually get exercised.
"""

import argparse
import base64
import json
import os
import random
import time
import uuid

import requests

BACKEND_URL = os.environ.get("BACKEND_URL", "http://localhost:8080")
PASSWORD = "LoadTest123!"
MERCHANTS = ["Amazon", "Uber", "Netflix", "Steam", "Walmart", "Spotify"]
COUNTRIES = ["US", "CA", "MX", "BR", "AR"]


def decode_jwt_claims(token):
    payload_b64 = token.split(".")[1]
    padding = "=" * (-len(payload_b64) % 4)
    payload_bytes = base64.urlsafe_b64decode(payload_b64 + padding)
    return json.loads(payload_bytes)


def register_and_login(session, email, full_name):
    register_response = session.post(
        f"{BACKEND_URL}/auth/register",
        json={"email": email, "password": PASSWORD, "fullName": full_name},
        timeout=10,
    )
    if register_response.status_code not in (201, 409):
        raise RuntimeError(
            f"Register failed for {email}: {register_response.status_code} {register_response.text}"
        )

    login_response = session.post(
        f"{BACKEND_URL}/auth/login",
        json={"email": email, "password": PASSWORD},
        timeout=10,
    )
    if login_response.status_code != 200:
        raise RuntimeError(
            f"Login failed for {email}: {login_response.status_code} {login_response.text}"
        )

    token = login_response.json()["token"]
    claims = decode_jwt_claims(token)
    return token, claims["userId"]


def build_user_pool(session, pool_size):
    run_id = uuid.uuid4().hex[:8]
    users = []
    for i in range(pool_size):
        email = f"load-{run_id}-{i}@dataset.local"
        token, user_id = register_and_login(session, email, f"Load User {i}")
        users.append({"token": token, "user_id": user_id, "last_country": "US"})
    return users


def build_transaction(user, anomaly_ratio):
    is_anomaly = random.random() < anomaly_ratio
    amount = round(random.uniform(5, 300), 2)
    country = user["last_country"]

    if is_anomaly:
        kind = random.choice(["amount", "country"])
        if kind == "amount":
            amount = round(random.uniform(11000, 20000), 2)
        else:
            country = random.choice([c for c in COUNTRIES if c != user["last_country"]])

    user["last_country"] = country
    return {
        "userId": user["user_id"],
        "amount": amount,
        "currency": "USD",
        "merchant": random.choice(MERCHANTS),
        "country": country,
    }


def run_load(session, users, rate, count, anomaly_ratio):
    accepted_ids = []
    failed = 0
    interval = 1.0 / rate if rate > 0 else 0
    start = time.monotonic()

    for i in range(count):
        user = users[i % len(users)]
        payload = build_transaction(user, anomaly_ratio)

        response = session.post(
            f"{BACKEND_URL}/transactions",
            json=payload,
            headers={"Authorization": f"Bearer {user['token']}"},
            timeout=10,
        )

        if response.status_code == 202:
            accepted_ids.append(response.json()["id"])
        else:
            failed += 1
            print(f"  Fallo inesperado: {response.status_code} {response.text}")

        if interval:
            time.sleep(interval)

    elapsed = time.monotonic() - start
    return accepted_ids, failed, elapsed


def sample_final_status(session, token, ids, sample_size, wait_seconds):
    print(f"\nEsperando {wait_seconds}s a que el consumer async resuelva las decisiones...")
    time.sleep(wait_seconds)

    sample = random.sample(ids, min(sample_size, len(ids)))
    distribution = {}
    for transaction_id in sample:
        response = session.get(
            f"{BACKEND_URL}/transactions/{transaction_id}",
            headers={"Authorization": f"Bearer {token}"},
            timeout=10,
        )
        status = response.json().get("status", "ERROR") if response.status_code == 200 else "ERROR"
        distribution[status] = distribution.get(status, 0) + 1

    print(f"Distribución de status sobre una muestra de {len(sample)} transacciones:")
    for status, n in sorted(distribution.items()):
        print(f"  {status}: {n}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rate", type=float, default=10, help="Transacciones por segundo (default: 10)")
    parser.add_argument("--duration", type=float, default=None, help="Duración en segundos (alternativa a --count)")
    parser.add_argument("--count", type=int, default=None, help="Cantidad total de transacciones a enviar")
    parser.add_argument("--users", type=int, default=10, help="Tamaño del pool de usuarios a reciclar (default: 10)")
    parser.add_argument("--anomaly-ratio", type=float, default=0.1,
                         help="Probabilidad de generar una transacción anómala (default: 0.1)")
    args = parser.parse_args()

    if args.count is None and args.duration is None:
        parser.error("Especificá --count o --duration")

    count = args.count if args.count is not None else int(args.rate * args.duration)

    session = requests.Session()

    print(f"Creando pool de {args.users} usuarios...")
    users = build_user_pool(session, args.users)

    print(f"Enviando {count} transacciones a {args.rate} tx/seg contra {BACKEND_URL}/transactions...")
    accepted_ids, failed, elapsed = run_load(session, users, args.rate, count, args.anomaly_ratio)

    print()
    print("Resumen de ingestión:")
    print(f"  Aceptadas (202): {len(accepted_ids)}")
    print(f"  Fallidas: {failed}")
    print(f"  Tiempo total: {elapsed:.1f}s (throughput real: {len(accepted_ids) / elapsed:.1f} tx/seg)")

    if accepted_ids:
        sample_final_status(session, users[0]["token"], accepted_ids, sample_size=20, wait_seconds=5)


if __name__ == "__main__":
    main()
