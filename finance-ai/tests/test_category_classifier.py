import unittest
from pathlib import Path

from category_classifier import predict_with_confidence, train_model

FIXTURE_DIR = Path(__file__).resolve().parent / 'fixtures'


class CategoryClassifierTests(unittest.TestCase):
    def test_training_and_prediction(self):
        csv_path = str(FIXTURE_DIR / 'transactions_labeled_test.csv')
        result = train_model(csv_path)
        self.assertEqual(result['status'], 'ok')
        self.assertIn('modelVersion', result)
        prediction = predict_with_confidence('AWS monthly cloud subscription payment')
        self.assertIn('category', prediction)
        self.assertIn('confidence', prediction)
        self.assertIn('modelVersion', prediction)
        self.assertGreaterEqual(prediction['confidence'], 0.0)


if __name__ == '__main__':
    unittest.main()

