import json
from pathlib import Path

import joblib
import numpy as np
import pandas as pd

MODEL_DIR = Path(__file__).parent.parent / "model"
FEATURES = ["amount", "log_amount", "hour_of_day", "day_of_week", "is_weekend"]

_pipeline = joblib.load(MODEL_DIR / "fraud_model.joblib")
with open(MODEL_DIR / "metadata.json", encoding="utf-8") as f:
    _metadata = json.load(f)

MODEL_VERSION = _metadata["modelVersion"]


def _build_features(amount: float, timestamp) -> pd.DataFrame:
    day_of_week = timestamp.weekday()
    return pd.DataFrame([{
        "amount": amount,
        "log_amount": np.log1p(amount),
        "hour_of_day": timestamp.hour,
        "day_of_week": day_of_week,
        "is_weekend": 1 if day_of_week >= 5 else 0,
    }], columns=FEATURES)


def score(amount: float, timestamp) -> tuple[float, list[dict]]:
    raw_features = _build_features(amount, timestamp)
    risk_score = float(_pipeline.predict_proba(raw_features)[0, 1])

    scaler = _pipeline.named_steps["scaler"]
    classifier = _pipeline.named_steps["classifier"]
    scaled_features = scaler.transform(raw_features)[0]
    contributions = classifier.coef_[0] * scaled_features

    top_indices = np.argsort(-np.abs(contributions))[:3]
    top_factors = [
        {"feature": FEATURES[i], "contribution": float(contributions[i])}
        for i in top_indices
    ]

    return risk_score, top_factors
