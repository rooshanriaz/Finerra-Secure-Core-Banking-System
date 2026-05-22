"""
FastAPI application for ML-based fraud detection inference.

Endpoints:
  POST /predict     - Score a transaction for fraud risk
  GET  /health      - Health check
  GET  /model-info  - Model metadata
"""

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from .schemas import PredictionRequest, PredictionResponse, HealthResponse, ModelInfoResponse
from . import model

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    loaded = model.load_models()
    if not loaded:
        logger.warning("Models not found — run 'python -m app.train' first")
    yield


app = FastAPI(
    title="Fraud Detection ML Service",
    description="Random Forest + Isolation Forest fraud scoring for banking transactions",
    version="1.0.0",
    lifespan=lifespan,
)


@app.get("/")
async def root():
    return {
        "service": "Fraud Detection ML Service",
        "version": "1.0.0",
        "status": "UP" if model.is_loaded() else "DEGRADED",
        "endpoints": {
            "POST /predict": "Score a transaction for fraud risk",
            "GET /health": "Health check",
            "GET /model-info": "Model metadata",
            "GET /docs": "Interactive API documentation (Swagger UI)",
        },
    }


@app.post("/predict", response_model=PredictionResponse)
async def predict(request: PredictionRequest):
    if not model.is_loaded():
        raise HTTPException(status_code=503, detail="Models not loaded — run training first")

    features = request.model_dump()
    result = model.predict(features)
    return PredictionResponse(**result)


@app.get("/health", response_model=HealthResponse)
async def health():
    return HealthResponse(
        status="UP" if model.is_loaded() else "DEGRADED",
        model_loaded=model.is_loaded(),
        model_version=model.MODEL_VERSION,
    )


@app.get("/model-info", response_model=ModelInfoResponse)
async def model_info():
    if not model.is_loaded():
        raise HTTPException(status_code=503, detail="Models not loaded")
    meta = model.get_meta()
    return ModelInfoResponse(
        model_type="RandomForest + IsolationForest ensemble",
        features=meta.get("features", []),
        training_samples=meta.get("training_samples", 0),
        fraud_ratio=meta.get("fraud_ratio", 0.0),
        rf_accuracy=meta.get("rf_auc_roc"),
        isolation_contamination=meta.get("iso_contamination", 0.03),
    )
