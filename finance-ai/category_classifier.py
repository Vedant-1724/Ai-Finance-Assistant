import logging
from datetime import datetime, timezone
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics import accuracy_score, classification_report, f1_score
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.svm import LinearSVC

logger = logging.getLogger(__name__)

BASE_DIR = Path(__file__).resolve().parent
MODEL_PATH = BASE_DIR / 'models' / 'category_classifier.joblib'
CSV_CANDIDATES = [
    BASE_DIR / 'data' / 'transactions_labeled.csv',
    BASE_DIR / 'transactions_labeled.csv',
]

_pipeline = {'model': None, 'metadata': {}}


def _resolve_csv_path(csv_path: str | None = None) -> Path:
    if csv_path:
        candidate = Path(csv_path)
        if candidate.is_absolute() and candidate.exists():
            return candidate
        candidate = BASE_DIR / csv_path
        if candidate.exists():
            return candidate
    for candidate in CSV_CANDIDATES:
        if candidate.exists():
            return candidate
    return CSV_CANDIDATES[0]


def _load_model_from_disk() -> None:
    if not MODEL_PATH.exists():
        logger.info('No trained classifier found at %s', MODEL_PATH)
        _pipeline['model'] = None
        _pipeline['metadata'] = {}
        return

    try:
        saved = joblib.load(MODEL_PATH)
        if isinstance(saved, dict) and 'model' in saved:
            _pipeline['model'] = saved.get('model')
            _pipeline['metadata'] = saved.get('metadata') or {}
        else:
            _pipeline['model'] = saved
            _pipeline['metadata'] = {'modelVersion': 'legacy'}
        logger.info('Category classifier loaded from %s', MODEL_PATH)
    except Exception as exc:
        logger.warning('Failed to load classifier from %s: %s', MODEL_PATH, exc)
        _pipeline['model'] = None
        _pipeline['metadata'] = {}


def train_model(csv_path: str | None = None) -> dict:
    resolved_csv = _resolve_csv_path(csv_path)
    if not resolved_csv.exists():
        return {
            'status': 'error',
            'message': f'Training CSV not found at: {resolved_csv}',
        }

    try:
        data = pd.read_csv(resolved_csv)
    except Exception as exc:
        return {'status': 'error', 'message': f'Cannot read CSV: {exc}'}

    if 'description' not in data.columns or 'category' not in data.columns:
        return {
            'status': 'error',
            'message': 'CSV must contain columns: description, category',
        }

    data = data.dropna(subset=['description', 'category']).copy()
    data['description'] = data['description'].astype(str).str.strip()
    data['category'] = data['category'].astype(str).str.strip()
    data = data[(data['description'] != '') & (data['category'] != '')]
    if len(data) < 20:
        return {
            'status': 'error',
            'message': f'Only {len(data)} valid rows found; need at least 20.',
        }

    X = data['description']
    y = data['category']
    categories = sorted(y.unique().tolist())
    sample_count = len(data)

    try:
        X_train, X_test, y_train, y_test = train_test_split(
            X,
            y,
            test_size=0.2,
            random_state=42,
            stratify=y,
        )
    except ValueError:
        X_train, X_test, y_train, y_test = train_test_split(
            X,
            y,
            test_size=0.2,
            random_state=42,
        )

    pipeline = Pipeline([
        ('tfidf', TfidfVectorizer(
            ngram_range=(1, 3),
            max_features=20000,
            sublinear_tf=True,
            strip_accents='unicode',
            min_df=1,
        )),
        ('clf', LinearSVC(
            C=1.1,
            class_weight='balanced',
            max_iter=4000,
            random_state=42,
        )),
    ])

    pipeline.fit(X_train, y_train)
    y_pred = pipeline.predict(X_test)
    accuracy = float(accuracy_score(y_test, y_pred))
    macro_f1 = float(f1_score(y_test, y_pred, average='macro', zero_division=0))
    report = classification_report(y_test, y_pred, zero_division=0)

    model_version = f"clf-{datetime.now(timezone.utc).strftime('%Y%m%d%H%M%S')}-{sample_count}"
    metadata = {
        'modelVersion': model_version,
        'trainedAtUtc': datetime.now(timezone.utc).isoformat(),
        'datasetPath': str(resolved_csv),
        'sampleCount': sample_count,
        'accuracy': round(accuracy, 4),
        'macroF1': round(macro_f1, 4),
        'categories': categories,
    }

    MODEL_PATH.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump({'model': pipeline, 'metadata': metadata}, MODEL_PATH)
    _pipeline['model'] = pipeline
    _pipeline['metadata'] = metadata

    logger.info('Classifier trained on %d samples with accuracy %.1f%%', sample_count, accuracy * 100)
    return {
        'status': 'ok',
        'accuracy': round(accuracy, 4),
        'accuracy_pct': f'{accuracy * 100:.1f}%',
        'macroF1': round(macro_f1, 4),
        'sample_count': sample_count,
        'train_count': len(X_train),
        'test_count': len(X_test),
        'categories': categories,
        'report': report,
        'modelVersion': model_version,
        'datasetPath': str(resolved_csv),
        'message': f'Model trained successfully on {sample_count} samples. Accuracy: {accuracy * 100:.1f}%.',
    }


def predict_category(description: str) -> str:
    model = _pipeline['model']
    if model is None:
        return 'Uncategorized'
    try:
        return str(model.predict([description])[0])
    except Exception as exc:
        logger.error('Prediction error: %s', exc)
        return 'Uncategorized'


def predict_with_confidence(description: str) -> dict:
    model = _pipeline['model']
    metadata = _pipeline.get('metadata') or {}
    if model is None:
        return {
            'category': 'Uncategorized',
            'confidence': 0.0,
            'top3': [],
            'modelVersion': metadata.get('modelVersion', 'untrained'),
            'source': 'FALLBACK',
        }

    try:
        decision = model.decision_function([description])
        scores = np.atleast_2d(decision)
        if scores.shape[0] == 1 and scores.shape[1] == 1:
            scores = np.array([[-scores[0][0], scores[0][0]]])
        scores = scores[0]
        shift = scores - scores.min() + 0.1
        probabilities = shift / shift.sum()
        classes = model.classes_
        top_indices = np.argsort(probabilities)[::-1][:3]
        top3 = [
            {'category': str(classes[index]), 'confidence': round(float(probabilities[index]), 3)}
            for index in top_indices
        ]
        return {
            'category': top3[0]['category'],
            'confidence': top3[0]['confidence'],
            'top3': top3,
            'modelVersion': metadata.get('modelVersion', 'unknown'),
            'source': 'MODEL',
        }
    except Exception as exc:
        logger.error('Confidence prediction error: %s', exc)
        return {
            'category': 'Uncategorized',
            'confidence': 0.0,
            'top3': [],
            'modelVersion': metadata.get('modelVersion', 'error'),
            'source': 'FALLBACK',
        }


_load_model_from_disk()
