import json
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
import shap

MODEL_DIR = Path(__file__).parent.parent / "model"
FEATURES = ["amount", "log_amount", "hour_of_day", "day_of_week", "is_weekend"]

_pipeline = joblib.load(MODEL_DIR / "fraud_model.joblib")
with open(MODEL_DIR / "metadata.json", encoding="utf-8") as f:
    _metadata = json.load(f)

MODEL_VERSION = _metadata["modelVersion"]

_scaler = _pipeline.named_steps["scaler"]
_classifier = _pipeline.named_steps["classifier"]

# El StandardScaler deja la media de cada feature (en espacio escalado) en 0,
# asi que un background de puros ceros equivale exactamente a la media de
# entrenamiento sin tener que commitear una muestra del dataset real.
_explainer = shap.LinearExplainer(_classifier, np.zeros((1, len(FEATURES))), feature_names=FEATURES)


def _build_features(amount: float, timestamp) -> pd.DataFrame:
    day_of_week = timestamp.weekday()
    return pd.DataFrame([{
        "amount": amount,
        "log_amount": np.log1p(amount),
        "hour_of_day": timestamp.hour,
        "day_of_week": day_of_week,
        "is_weekend": 1 if day_of_week >= 5 else 0,
    }], columns=FEATURES)


def score(amount: float, timestamp) -> tuple[float, float, list[dict]]:
    raw_features = _build_features(amount, timestamp)
    risk_score = float(_pipeline.predict_proba(raw_features)[0, 1])

    scaled_features = _scaler.transform(raw_features)
    shap_values = _explainer.shap_values(scaled_features)[0]
    base_value = float(np.atleast_1d(_explainer.expected_value)[0])

    top_indices = np.argsort(-np.abs(shap_values))[:3]
    top_factors = [
        {"feature": FEATURES[i], "contribution": float(shap_values[i])}
        for i in top_indices
    ]

    return risk_score, base_value, top_factors
