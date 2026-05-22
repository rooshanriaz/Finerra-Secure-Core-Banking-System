"""
Training script: generates synthetic data, trains Random Forest + Isolation Forest,
and persists model artifacts to ml-fraud-model/models/.

Usage:
    python -m app.train
"""

import os
import logging

import joblib
import numpy as np
from sklearn.ensemble import RandomForestClassifier, IsolationForest
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import classification_report, roc_auc_score

from .data_generator import generate_dataset, FEATURE_COLUMNS

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger(__name__)

MODEL_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), "models")


def train():
    os.makedirs(MODEL_DIR, exist_ok=True)

    logger.info("Generating synthetic dataset...")
    df = generate_dataset(n_samples=10_000, fraud_ratio=0.03, seed=42)
    logger.info("Dataset: %d samples, fraud ratio=%.2f%%", len(df), df["is_fraud"].mean() * 100)

    X = df[FEATURE_COLUMNS].values
    y = df["is_fraud"].values

    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42, stratify=y)

    logger.info("Fitting StandardScaler...")
    scaler = StandardScaler()
    X_train_scaled = scaler.fit_transform(X_train)
    X_test_scaled = scaler.transform(X_test)

    # --- Random Forest ---
    logger.info("Training Random Forest Classifier...")
    rf = RandomForestClassifier(
        n_estimators=200,
        max_depth=15,
        min_samples_split=5,
        class_weight="balanced",
        random_state=42,
        n_jobs=-1,
    )
    rf.fit(X_train_scaled, y_train)

    y_pred = rf.predict(X_test_scaled)
    y_proba = rf.predict_proba(X_test_scaled)[:, 1]
    auc = roc_auc_score(y_test, y_proba)
    logger.info("Random Forest AUC-ROC: %.4f", auc)
    logger.info("\n%s", classification_report(y_test, y_pred, target_names=["Legitimate", "Fraud"]))

    # --- Isolation Forest ---
    logger.info("Training Isolation Forest...")
    iso = IsolationForest(
        n_estimators=150,
        contamination=0.03,
        random_state=42,
        n_jobs=-1,
    )
    iso.fit(X_train_scaled)

    # --- Save artifacts ---
    joblib.dump(rf, os.path.join(MODEL_DIR, "random_forest.joblib"))
    joblib.dump(iso, os.path.join(MODEL_DIR, "isolation_forest.joblib"))
    joblib.dump(scaler, os.path.join(MODEL_DIR, "scaler.joblib"))

    meta = {
        "training_samples": len(X_train),
        "test_samples": len(X_test),
        "fraud_ratio": float(df["is_fraud"].mean()),
        "rf_auc_roc": float(auc),
        "features": FEATURE_COLUMNS,
        "rf_n_estimators": 200,
        "iso_contamination": 0.03,
    }
    joblib.dump(meta, os.path.join(MODEL_DIR, "model_meta.joblib"))

    logger.info("Models saved to %s", MODEL_DIR)
    logger.info("Feature importances:")
    for name, imp in sorted(zip(FEATURE_COLUMNS, rf.feature_importances_), key=lambda x: -x[1]):
        logger.info("  %-30s %.4f", name, imp)


if __name__ == "__main__":
    train()
