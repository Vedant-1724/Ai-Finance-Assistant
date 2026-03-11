import unittest

from category_classifier import predict_with_confidence, train_model


class CategoryClassifierTests(unittest.TestCase):
    def test_training_and_prediction(self):
        result = train_model()
        self.assertEqual(result['status'], 'ok')
        self.assertIn('modelVersion', result)
        prediction = predict_with_confidence('AWS monthly cloud subscription payment')
        self.assertIn('category', prediction)
        self.assertIn('confidence', prediction)
        self.assertIn('modelVersion', prediction)
        self.assertGreaterEqual(prediction['confidence'], 0.0)


if __name__ == '__main__':
    unittest.main()
