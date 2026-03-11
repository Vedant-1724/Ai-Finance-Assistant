from datetime import datetime
from pathlib import Path

import joblib
import numpy as np
from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import StandardScaler

BASE_DIR = Path(__file__).resolve().parent
ANOMALY_MODEL_PATH = BASE_DIR / 'models' / 'anomaly_detector.joblib'


def _safe_float(value, default: float = 0.0) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def _parse_day_of_week(transaction: dict) -> int:
    raw_date = transaction.get('date')
    if not raw_date:
        return int(transaction.get('day_of_week', 0) or 0)
    for fmt in ('%Y-%m-%d', '%d/%m/%Y', '%d-%m-%Y'):
        try:
            return datetime.strptime(str(raw_date), fmt).weekday()
        except ValueError:
            continue
    return int(transaction.get('day_of_week', 0) or 0)


def _parse_hour(transaction: dict) -> int:
    raw = transaction.get('hour')
    if raw is None:
        return 12
    try:
        hour = int(raw)
        return max(0, min(hour, 23))
    except (TypeError, ValueError):
        return 12


def _category_signal(transaction: dict) -> int:
    if transaction.get('category_id') is not None:
        try:
            return int(transaction['category_id'])
        except (TypeError, ValueError):
            pass
    name = str(transaction.get('categoryName') or transaction.get('category') or '')
    return abs(hash(name)) % 97 if name else -1


def extract_features(transactions: list[dict]) -> np.ndarray:
    rows = []
    for transaction in transactions:
        amount = abs(_safe_float(transaction.get('amount')))
        rows.append([
            amount,
            float(_parse_day_of_week(transaction)),
            float(_parse_hour(transaction)),
            float(_category_signal(transaction)),
        ])
    return np.array(rows, dtype=float) if rows else np.empty((0, 4), dtype=float)


def _save_bundle(model, scaler, metadata: dict) -> None:
    ANOMALY_MODEL_PATH.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump({'model': model, 'scaler': scaler, 'metadata': metadata}, ANOMALY_MODEL_PATH)


def load_bundle() -> dict | None:
    if not ANOMALY_MODEL_PATH.exists():
        return None
    saved = joblib.load(ANOMALY_MODEL_PATH)
    if isinstance(saved, dict) and 'model' in saved and 'scaler' in saved:
        return saved
    return None


def train_anomaly_model(historical_transactions: list[dict]) -> dict:
    X = extract_features(historical_transactions)
    if len(X) < 8:
        return {'status': 'error', 'message': 'Need at least 8 transactions to train anomaly model.'}

    scaler = StandardScaler()
    X_scaled = scaler.fit_transform(X)
    model = IsolationForest(contamination=0.05, random_state=42)
    model.fit(X_scaled)

    metadata = {
        'trainedAtUtc': datetime.utcnow().isoformat(),
        'sampleCount': int(len(X)),
        'featureCount': int(X.shape[1]),
    }
    _save_bundle(model, scaler, metadata)
    return {'status': 'ok', 'metadata': metadata}


def score_transactions(transactions: list[dict], auto_train: bool = False) -> list[dict]:
    if not transactions:
        return []

    bundle = load_bundle()
    if bundle is None:
        if not auto_train:
            return []
        trained = train_anomaly_model(transactions)
        if trained.get('status') != 'ok':
            return []
        bundle = load_bundle()
        if bundle is None:
            return []

    X = extract_features(transactions)
    if len(X) == 0:
        return []

    scaler = bundle['scaler']
    model = bundle['model']
    X_scaled = scaler.transform(X)
    predictions = model.predict(X_scaled)
    scores = model.score_samples(X_scaled)

    result = []
    for transaction, prediction, score in zip(transactions, predictions, scores):
        if int(prediction) == -1:
            result.append({
                **transaction,
                'anomaly_score': round(float(score), 4),
            })
    return result


def detect_anomalies(new_transactions: list[dict]) -> list[dict]:
    return score_transactions(new_transactions, auto_train=True)
