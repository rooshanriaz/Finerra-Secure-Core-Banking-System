from pydantic import BaseModel, Field
from typing import List, Optional


class PredictionRequest(BaseModel):
    amount: float = Field(..., gt=0, description="Transaction amount")
    transaction_type: str = Field(..., description="DEPOSIT, WITHDRAWAL, or LOAN_REPAYMENT")
    hour_of_day: int = Field(..., ge=0, le=23)
    day_of_week: int = Field(..., ge=0, le=6)
    amount_to_avg_ratio: float = Field(default=1.0, ge=0, description="amount / account historical average")
    transaction_velocity: int = Field(default=0, ge=0, description="Transactions in last hour for this account")
    days_since_account_creation: int = Field(default=365, ge=0)


class PredictionResponse(BaseModel):
    risk_score: float = Field(..., ge=0.0, le=1.0)
    is_fraudulent: bool
    rf_probability: float
    isolation_score: float
    risk_level: str
    risk_factors: List[str]


class HealthResponse(BaseModel):
    status: str
    model_loaded: bool
    model_version: str


class ModelInfoResponse(BaseModel):
    model_type: str
    features: List[str]
    training_samples: int
    fraud_ratio: float
    rf_accuracy: Optional[float] = None
    isolation_contamination: float
