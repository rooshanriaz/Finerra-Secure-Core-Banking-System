"""
Model loading, inference, and risk factor extraction.
"""

import os
import logging
import numpy as np
import joblib
from typing import Dict, Any, List, Optional

from .data_generator import FEATURE_COLUMNS, TYPE_ENCODING

logger = logging.getLogger(__name__)

MODEL_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), "models")
RF_PATH = os.path.join(MODEL_DIR, "random_forest.joblib")
IF_PATH = os.path.join(MODEL_DIR, "isolation_forest.joblib")
SCALER_PATH = os.path.join(MODEL_DIR, "scaler.joblib")
META_PATH = os.path.join(MODEL_DIR, "model_meta.joblib")

MODEL_VERSION = "1.0.0"

_rf_model = None
_if_model = None
_scaler = None
_meta: Optional[Dict[str, Any]] = None


def load_models() -> bool:
    global _rf_model, _if_model, _scaler, _meta
    try:
        _rf_model = joblib.load(RF_PATH)
        _if_model = joblib.load(IF_PATH)
        _scaler = joblib.load(SCALER_PATH)
        if os.path.exists(META_PATH):
            _meta = joblib.load(META_PATH)
        logger.info("Models loaded successfully from %s", MODEL_DIR)
        return True
    except Exception as e:
        logger.error("Failed to load models: %s", e)
        return False


def is_loaded() -> bool:
    return _rf_model is not None and _if_model is not None and _scaler is not None


def get_meta() -> Dict[str, Any]:
    return _meta or {}


def predict(features: Dict[str, Any]) -> Dict[str, Any]:
    if not is_loaded():
        raise RuntimeError("Models not loaded")

    feature_vector = _prepare_features(features)
    scaled = _scaler.transform([feature_vector])

    rf_proba = _rf_model.predict_proba(scaled)[0][1]

    raw_score = _if_model.decision_function(scaled)[0]
    isolation_score = float(np.clip(1.0 - (raw_score + 0.5), 0.0, 1.0))

    combined = 0.7 * rf_proba + 0.3 * isolation_score
    combined = float(np.clip(combined, 0.0, 1.0))

    risk_level = _classify_risk(combined)
    risk_factors = _extract_risk_factors(features, rf_proba, isolation_score)

    return {
        "risk_score": round(combined, 4),
        "is_fraudulent": combined >= 0.60,
        "rf_probability": round(float(rf_proba), 4),
        "isolation_score": round(isolation_score, 4),
        "risk_level": risk_level,
        "risk_factors": risk_factors,
    }


def _prepare_features(raw: Dict[str, Any]) -> List[float]:
    txn_type_str = raw.get("transaction_type", "DEPOSIT")
    txn_type = TYPE_ENCODING.get(txn_type_str, 0)

    amount = float(raw["amount"])
    hour = int(raw.get("hour_of_day", 12))
    dow = int(raw.get("day_of_week", 2))

    return [
        amount,
        float(np.log1p(amount)),
        txn_type,
        hour,
        dow,
        1 if dow >= 5 else 0,
        1 if (hour < 6 or hour > 22) else 0,
        float(raw.get("amount_to_avg_ratio", 1.0)),
        int(raw.get("transaction_velocity", 0)),
        int(raw.get("days_since_account_creation", 365)),
    ]


def _classify_risk(score: float) -> str:
    if score >= 0.85:
        return "CRITICAL"
    if score >= 0.60:
        return "HIGH"
    if score >= 0.30:
        return "MEDIUM"
    return "LOW"


def _extract_risk_factors(features: Dict[str, Any], rf_prob: float, iso_score: float) -> List[str]:
    factors = []
    ratio = float(features.get("amount_to_avg_ratio", 1.0))
    if ratio > 3.0:
        factors.append("high_amount_ratio")
    hour = int(features.get("hour_of_day", 12))
    if hour < 6 or hour > 22:
        factors.append("night_transaction")
    velocity = int(features.get("transaction_velocity", 0))
    if velocity >= 4:
        factors.append("high_velocity")
    amount = float(features.get("amount", 0))
    if amount > 200_000:
        factors.append("large_amount")
    age = int(features.get("days_since_account_creation", 365))
    if age < 30:
        factors.append("new_account")
    if iso_score > 0.7:
        factors.append("anomaly_detected")
    return factors
