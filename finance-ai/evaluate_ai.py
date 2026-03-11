from pathlib import Path

from anomaly_detector import detect_anomalies, train_anomaly_model
from category_classifier import predict_with_confidence, train_model
from statement_parser import parse_statement

FIXTURE_DIR = Path(__file__).resolve().parent / 'tests' / 'fixtures'


def main() -> None:
    csv_bytes = (FIXTURE_DIR / 'sample_statement.csv').read_bytes()
    parse_result = parse_statement(csv_bytes, 'sample_statement.csv')
    clf_result = train_model()
    prediction = predict_with_confidence('AWS monthly cloud subscription payment')
    anomaly_seed = [
        {'date': '2026-02-01', 'amount': -1200, 'categoryName': 'Software'},
        {'date': '2026-02-02', 'amount': -1400, 'categoryName': 'Software'},
        {'date': '2026-02-03', 'amount': -1350, 'categoryName': 'Software'},
        {'date': '2026-02-04', 'amount': -1500, 'categoryName': 'Software'},
        {'date': '2026-02-05', 'amount': -1300, 'categoryName': 'Software'},
        {'date': '2026-02-06', 'amount': -1450, 'categoryName': 'Software'},
        {'date': '2026-02-07', 'amount': -1380, 'categoryName': 'Software'},
        {'date': '2026-02-08', 'amount': -20000, 'categoryName': 'Software'},
    ]
    anomaly_train = train_anomaly_model(anomaly_seed)
    anomalies = detect_anomalies(anomaly_seed)

    print('Statement parser:', parse_result['total_found'], 'rows, mode=', parse_result.get('parseMode'))
    print('Classifier:', clf_result.get('status'), clf_result.get('accuracy_pct'), clf_result.get('modelVersion'))
    print('Classifier prediction:', prediction)
    print('Anomaly train:', anomaly_train)
    print('Anomalies found:', len(anomalies))


if __name__ == '__main__':
    main()
