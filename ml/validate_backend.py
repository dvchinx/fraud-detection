"""Feed a sample of the pointe77/credit-card-transaction dataset into the running
fraud-detection backend, to validate that the Fase 1 data model and API tolerate
real-shaped transactions. The real `is_fraud` label is only kept in the local
output file - the backend has no rules/ML engine yet to consume it.
"""

import base64
import csv
import json
import os

import requests
from datasets import load_dataset

BACKEND_URL = os.environ.get("BACKEND_URL", "http://localhost:8080")
SAMPLE_SIZE = int(os.environ.get("SAMPLE_SIZE", "200"))
OUTPUT_FILE = os.environ.get(
    "OUTPUT_FILE", os.path.join(os.path.dirname(__file__), "validation_results.csv")
)
PASSWORD = "Dataset123!"


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


def main():
    print(f"Cargando hasta {SAMPLE_SIZE} filas de pointe77/credit-card-transaction...")
    dataset = load_dataset("pointe77/credit-card-transaction", split=f"train[:{SAMPLE_SIZE}]")

    rows = [row for row in dataset if row["amt"] > 0]
    skipped = len(dataset) - len(rows)
    if skipped:
        print(f"Se descartaron {skipped} filas con amt <= 0")

    session = requests.Session()

    unique_cards = {}
    for row in rows:
        unique_cards.setdefault(row["cc_num"], row)

    print(f"Registrando/logueando {len(unique_cards)} usuarios únicos...")
    card_to_auth = {}
    for cc_num, row in unique_cards.items():
        email = f"user{cc_num}@dataset.local"
        full_name = f"{row['first']} {row['last']}"
        token, user_id = register_and_login(session, email, full_name)
        card_to_auth[cc_num] = (token, user_id)

    print(f"Enviando {len(rows)} transacciones a {BACKEND_URL}/transactions...")
    results = []
    success_count = 0
    fraud_actual_count = 0

    for row in rows:
        token, user_id = card_to_auth[row["cc_num"]]
        payload = {
            "userId": user_id,
            "amount": round(row["amt"], 2),
            "currency": "USD",
            "merchant": row["merchant"],
            "country": "US",
        }
        response = session.post(
            f"{BACKEND_URL}/transactions",
            json=payload,
            headers={"Authorization": f"Bearer {token}"},
            timeout=10,
        )

        transaction_id = None
        if response.status_code == 201:
            success_count += 1
            transaction_id = response.json().get("id")

        is_fraud_actual = row["is_fraud"]
        fraud_actual_count += is_fraud_actual
        results.append(
            {
                "trans_num": row["trans_num"],
                "amt": row["amt"],
                "merchant": row["merchant"],
                "is_fraud_actual": is_fraud_actual,
                "http_status": response.status_code,
                "transaction_id": transaction_id,
            }
        )

    with open(OUTPUT_FILE, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(
            f,
            fieldnames=["trans_num", "amt", "merchant", "is_fraud_actual", "http_status", "transaction_id"],
        )
        writer.writeheader()
        writer.writerows(results)

    print()
    print("Resumen:")
    print(f"  Transacciones procesadas: {len(results)}")
    print(f"  Creadas exitosamente (201): {success_count}")
    print(f"  Fallidas: {len(results) - success_count}")
    print(f"  Fraudes reales en la muestra: {fraud_actual_count} ({fraud_actual_count / len(results):.1%})")
    print(f"  Resultados guardados en: {OUTPUT_FILE}")


if __name__ == "__main__":
    main()
