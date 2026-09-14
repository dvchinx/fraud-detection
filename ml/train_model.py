"""Train the Fase 4 fraud-scoring model on the same pointe77/credit-card-transaction
dataset already validated against the backend in validate_backend.py, and save the
artifact consumed by service/model.py.

Feature parity note: the Java backend's POST /transactions only captures amount,
merchant (free text), country and currency - not the dataset's category, geolocation
or demographic columns. To keep training/serving honest, this model only uses
features actually reproducible at score time: the transaction amount and features
derived from its timestamp. See README.md for the rationale.
"""

import json
import os
from datetime import datetime, timezone
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from datasets import load_dataset
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    average_precision_score,
    classification_report,
    roc_auc_score,
)
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

TRAIN_SAMPLE_SIZE = int(os.environ.get("TRAIN_SAMPLE_SIZE", "300000"))
TEST_SAMPLE_SIZE = int(os.environ.get("TEST_SAMPLE_SIZE", "100000"))
MODEL_DIR = Path(__file__).parent / "model"
FEATURES = ["amount", "log_amount", "hour_of_day", "day_of_week", "is_weekend"]
MODEL_VERSION = "logreg-v1"


def engineer_features(df: pd.DataFrame) -> pd.DataFrame:
    timestamps = pd.to_datetime(df["trans_date_trans_time"])
    return pd.DataFrame({
        "amount": df["amt"].astype(float),
        "log_amount": np.log1p(df["amt"].astype(float)),
        "hour_of_day": timestamps.dt.hour,
        "day_of_week": timestamps.dt.dayofweek,
        "is_weekend": (timestamps.dt.dayofweek >= 5).astype(int),
    })


def load_split(split: str, sample_size: int) -> pd.DataFrame:
    print(f"Cargando split '{split}' (hasta {sample_size} filas)...")
    dataset = load_dataset("pointe77/credit-card-transaction", split=f"{split}[:{sample_size}]")
    return dataset.to_pandas()


def main():
    train_df = load_split("train", TRAIN_SAMPLE_SIZE)
    test_df = load_split("test", TEST_SAMPLE_SIZE)

    X_train, y_train = engineer_features(train_df), train_df["is_fraud"].astype(int)
    X_test, y_test = engineer_features(test_df), test_df["is_fraud"].astype(int)

    print(f"Entrenando LogisticRegression sobre {len(X_train)} filas "
          f"({y_train.mean():.3%} fraude)...")

    pipeline = Pipeline([
        ("scaler", StandardScaler()),
        ("classifier", LogisticRegression(class_weight="balanced", max_iter=1000)),
    ])
    pipeline.fit(X_train[FEATURES], y_train)

    probabilities = pipeline.predict_proba(X_test[FEATURES])[:, 1]
    predictions = (probabilities >= 0.5).astype(int)

    roc_auc = roc_auc_score(y_test, probabilities)
    pr_auc = average_precision_score(y_test, probabilities)
    report = classification_report(y_test, predictions, output_dict=True)

    print()
    print(f"ROC-AUC: {roc_auc:.4f}")
    print(f"PR-AUC:  {pr_auc:.4f}")
    print(classification_report(y_test, predictions))

    MODEL_DIR.mkdir(exist_ok=True)
    joblib.dump(pipeline, MODEL_DIR / "fraud_model.joblib")

    metadata = {
        "modelVersion": MODEL_VERSION,
        "trainedAt": datetime.now(timezone.utc).isoformat(),
        "features": FEATURES,
        "trainRows": len(X_train),
        "testRows": len(X_test),
        "trainFraudRate": float(y_train.mean()),
        "metrics": {
            "rocAuc": roc_auc,
            "prAuc": pr_auc,
            "precisionFraud": report["1"]["precision"],
            "recallFraud": report["1"]["recall"],
            "f1Fraud": report["1"]["f1-score"],
        },
        "suggestedThresholds": {
            "review": 0.5,
            "block": 0.85,
        },
    }
    with open(MODEL_DIR / "metadata.json", "w", encoding="utf-8") as f:
        json.dump(metadata, f, indent=2)

    print(f"\nModelo guardado en {MODEL_DIR / 'fraud_model.joblib'}")
    print(f"Metadata guardada en {MODEL_DIR / 'metadata.json'}")


if __name__ == "__main__":
    main()
