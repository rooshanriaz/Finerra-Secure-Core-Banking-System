"""
Generates synthetic banking transaction data for fraud detection model training.

Produces a class-imbalanced dataset (~97% legitimate, ~3% fraudulent) with
realistic fraud patterns: high amounts at unusual hours, elevated velocity,
and amounts far from the account's historical average.
"""

import numpy as np
import pandas as pd


FEATURE_COLUMNS = [
    "amount",
    "amount_log",
    "transaction_type",
    "hour_of_day",
    "day_of_week",
    "is_weekend",
    "is_night",
    "amount_to_avg_ratio",
    "transaction_velocity",
    "days_since_account_creation",
]

TYPE_ENCODING = {"DEPOSIT": 0, "WITHDRAWAL": 1, "LOAN_REPAYMENT": 2}


def generate_dataset(n_samples: int = 10000, fraud_ratio: float = 0.03, seed: int = 42) -> pd.DataFrame:
    rng = np.random.RandomState(seed)
    n_fraud = int(n_samples * fraud_ratio)
    n_legit = n_samples - n_fraud

    legit = _generate_legitimate(n_legit, rng)
    fraud = _generate_fraudulent(n_fraud, rng)

    df = pd.concat([legit, fraud], ignore_index=True)
    df = df.sample(frac=1, random_state=seed).reset_index(drop=True)
    return df


def _generate_legitimate(n: int, rng: np.random.RandomState) -> pd.DataFrame:
    amounts = rng.lognormal(mean=8.5, sigma=1.2, size=n).clip(100, 500_000)
    hours = rng.choice(range(24), size=n, p=_business_hour_weights())
    days = rng.randint(0, 7, size=n)
    types = rng.choice([0, 1, 2], size=n, p=[0.45, 0.40, 0.15])
    avg_ratios = rng.normal(1.0, 0.3, size=n).clip(0.2, 3.0)
    velocities = rng.poisson(1.0, size=n).clip(0, 5)
    account_ages = rng.randint(30, 3650, size=n)

    return _build_df(amounts, hours, days, types, avg_ratios, velocities, account_ages, label=0)


def _generate_fraudulent(n: int, rng: np.random.RandomState) -> pd.DataFrame:
    amounts = rng.lognormal(mean=11.0, sigma=1.5, size=n).clip(10_000, 2_000_000)
    hours = rng.choice(range(24), size=n, p=_night_hour_weights())
    days = rng.randint(0, 7, size=n)
    types = rng.choice([0, 1, 2], size=n, p=[0.15, 0.70, 0.15])
    avg_ratios = rng.uniform(4.0, 20.0, size=n)
    velocities = rng.poisson(5.0, size=n).clip(2, 20)
    account_ages = rng.randint(1, 60, size=n)

    return _build_df(amounts, hours, days, types, avg_ratios, velocities, account_ages, label=1)


def _build_df(amounts, hours, days, types, avg_ratios, velocities, account_ages, label: int) -> pd.DataFrame:
    return pd.DataFrame({
        "amount": amounts,
        "amount_log": np.log1p(amounts),
        "transaction_type": types,
        "hour_of_day": hours,
        "day_of_week": days,
        "is_weekend": (days >= 5).astype(int),
        "is_night": ((hours < 6) | (hours > 22)).astype(int),
        "amount_to_avg_ratio": avg_ratios,
        "transaction_velocity": velocities,
        "days_since_account_creation": account_ages,
        "is_fraud": label,
    })


def _business_hour_weights() -> list:
    """Higher probability during 8am-6pm, lower at night."""
    weights = np.ones(24)
    weights[8:18] = 5.0
    weights[6:8] = 2.0
    weights[18:21] = 2.0
    weights[0:6] = 0.3
    weights[22:24] = 0.5
    return (weights / weights.sum()).tolist()


def _night_hour_weights() -> list:
    """Fraudulent transactions skew toward night hours."""
    weights = np.ones(24)
    weights[0:6] = 5.0
    weights[22:24] = 4.0
    weights[8:18] = 0.5
    return (weights / weights.sum()).tolist()
