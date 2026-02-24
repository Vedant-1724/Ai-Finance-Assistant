import numpy as np
from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import StandardScaler
import joblib
import os

ANOMALY_MODEL_PATH = 'models/anomaly_detector.joblib'


def extract_features(transactions: list) -> np.ndarray:
    return np.array([
        [
            abs(t.get('amount', 0)),
            t.get('day_of_week', 0),
            t.get('hour', 12),
            t.get('category_id', -1),
        ]
        for t in transactions
    ])


def train_anomaly_model(historical_transactions: list):
    X = extract_features(historical_transactions)
    scaler = StandardScaler()
    X_scaled = scaler.fit_transform(X)

    model = IsolationForest(contamination=0.01, random_state=42)
    model.fit(X_scaled)

    os.makedirs('models', exist_ok=True)
    joblib.dump({'model': model, 'scaler': scaler}, ANOMALY_MODEL_PATH)
    return model, scaler


def detect_anomalies(new_transactions: list) -> list:
    if not os.path.exists(ANOMALY_MODEL_PATH):
        return []

    saved = joblib.load(ANOMALY_MODEL_PATH)
    model, scaler = saved['model'], saved['scaler']

    X = extract_features(new_transactions)
    X_scaled = scaler.transform(X)
    predictions = model.predict(X_scaled)

    return [
        txn for txn, pred in zip(new_transactions, predictions)
        if pred == -1
    ]