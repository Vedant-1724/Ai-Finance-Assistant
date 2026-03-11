import io
import os
import unittest
from pathlib import Path

os.environ['INTERNAL_API_KEY'] = os.environ.get('INTERNAL_API_KEY', 'test-key')

from app import app  # noqa: E402

FIXTURE_DIR = Path(__file__).resolve().parent / 'fixtures'
API_KEY = os.environ['INTERNAL_API_KEY']


class AppEndpointTests(unittest.TestCase):
    def setUp(self):
        self.client = app.test_client()

    def test_health_is_public(self):
        response = self.client.get('/health')
        self.assertEqual(response.status_code, 200)
        body = response.get_json()
        self.assertIn('providers', body)

    def test_parse_statement_endpoint_returns_review_metadata(self):
        payload = io.BytesIO((FIXTURE_DIR / 'sample_statement.csv').read_bytes())
        response = self.client.post(
            '/parse-statement',
            headers={'X-API-Key': API_KEY},
            data={'file': (payload, 'sample_statement.csv')},
            content_type='multipart/form-data',
        )
        self.assertEqual(response.status_code, 200)
        body = response.get_json()
        self.assertEqual(body['parseMode'], 'csv')
        self.assertGreaterEqual(body['documentConfidence'], 0.9)

    def test_opt_in_sample_endpoint_requires_explicit_opt_in(self):
        response = self.client.post(
            '/samples/opt-in',
            headers={'X-API-Key': API_KEY},
            json={'optIn': True, 'kind': 'statement', 'filename': 'sample.txt', 'content': 'sample content'},
        )
        self.assertEqual(response.status_code, 200)
        body = response.get_json()
        self.assertEqual(body['status'], 'ok')


if __name__ == '__main__':
    unittest.main()
