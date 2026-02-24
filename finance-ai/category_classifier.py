import pandas as pd
from sklearn.pipeline import Pipeline
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report
import joblib
import os

MODEL_PATH = 'models/category_classifier.joblib'


def train_model(csv_path: str = 'transactions_labeled.csv'):
    data = pd.read_csv(csv_path)
    data = data.dropna(subset=['description', 'category'])

    X = data['description']
    y = data['category']

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42
    )

    pipeline = Pipeline([
        ('tfidf', TfidfVectorizer(ngram_range=(1, 2), max_features=10000)),
        ('clf', RandomForestClassifier(n_estimators=200, n_jobs=-1, random_state=42)),
    ])

    pipeline.fit(X_train, y_train)
    print(classification_report(y_test, pipeline.predict(X_test)))

    os.makedirs('models', exist_ok=True)
    joblib.dump(pipeline, MODEL_PATH)
    return pipeline


def load_or_train_model():
    if os.path.exists(MODEL_PATH):
        return joblib.load(MODEL_PATH)
    print("No trained model found. Train the model first using train_model().")
    return None


pipeline = load_or_train_model()


def predict_category(description: str) -> str:
    if pipeline is None:
        return "uncategorized"
    return pipeline.predict([description])[0]