import unittest
from pathlib import Path
from unittest.mock import patch

from statement_parser import parse_statement

FIXTURE_DIR = Path(__file__).resolve().parent / 'fixtures'


class StatementParserTests(unittest.TestCase):
    def test_csv_statement_parses_with_metadata(self):
        result = parse_statement((FIXTURE_DIR / 'sample_statement.csv').read_bytes(), 'sample_statement.csv')
        self.assertEqual(result['parseMode'], 'csv')
        self.assertEqual(result['total_found'], 3)
        self.assertGreater(result['documentConfidence'], 0.9)
        self.assertTrue(all('confidence' in txn for txn in result['transactions']))
        self.assertTrue(all(txn['normalizedSource'] == 'CSV_IMPORT' for txn in result['transactions']))
        self.assertTrue(any(txn['amount'] < 0 for txn in result['transactions']))
        self.assertTrue(any(txn['amount'] > 0 for txn in result['transactions']))

    @patch('statement_parser.extract_text_from_pdf_bytes')
    def test_pdf_ocr_fallback_parses_rows(self, mock_extract):
        mock_extract.return_value = {
            'text': 'Date Description Debit Credit\n01/02/2026 OFFICE RENT 25000.00 0.00\n02/02/2026 CLIENT RECEIPT 0.00 40000.00',
            'warnings': ['Used OCR fallback'],
            'usedOcrFallback': True,
            'ocrConfidence': 0.73,
        }
        result = parse_statement(b'%PDF fake', 'statement.pdf')
        self.assertEqual(result['parseMode'], 'pdf_ocr')
        self.assertEqual(result['total_found'], 2)
        self.assertTrue(all(txn['normalizedSource'] == 'PDF' for txn in result['transactions']))
        self.assertIn('Used OCR fallback', result.get('warnings', []))

    @patch('statement_parser.extract_text_from_image_bytes')
    def test_image_statement_uses_ocr_pipeline(self, mock_extract):
        mock_extract.return_value = {
            'text': '01/02/2026 paid ₹120.00 to Swiggy\n02/02/2026 received ₹500.00 from Client',
            'warnings': [],
            'ocrConfidence': 0.69,
        }
        result = parse_statement(b'not-a-real-image', 'upi.png')
        self.assertEqual(result['parseMode'], 'image_ocr')
        # Regex parser AND UPI parser each extract 2 rows with different
        # descriptions, giving 4 unique transactions after dedup.
        self.assertEqual(result['total_found'], 4)
        self.assertTrue(any(txn['normalizedSource'] == 'UPI_SCREENSHOT' for txn in result['transactions']))
        self.assertTrue(any(txn['needsReview'] for txn in result['transactions']))

    @patch('statement_parser.extract_text_from_image_bytes')
    def test_llm_fallback_is_validated(self, mock_extract):
        mock_extract.return_value = {
            'text': 'garbled scan text with only one recognizable row',
            'warnings': ['OCR noisy'],
            'ocrConfidence': 0.25,
        }

        def llm_fallback(_text, _filename, _kind):
            return {
                'transactions': [
                    {'date': '2026-02-01', 'description': 'Coffee Shop', 'amount': -250.0},
                    {'date': '2026-02-02', 'description': 'Client Receipt', 'amount': 5000.0},
                ],
                'warnings': ['Structured fallback used'],
            }

        result = parse_statement(b'fake-image', 'hard-scan.png', llm_fallback=llm_fallback)
        self.assertEqual(result['parseMode'], 'llm_fallback')
        self.assertEqual(result['total_found'], 2)
        self.assertTrue(all(txn['normalizedSource'] == 'LLM_FALLBACK' for txn in result['transactions']))
        self.assertTrue(all(txn['needsReview'] for txn in result['transactions']))

    @patch('statement_parser.extract_text_from_pdf_bytes')
    def test_gpay_statement_parsing(self, mock_extract):
        mock_extract.return_value = {
            'text': 'Date & time Transaction details Amount\n01 Feb, 2026 Paid to MANGAL TRADERS ₹3\n04:08 PM UPI Transaction ID: 639875292369\nPaid by Bank Of Maharashtra 0640\n03 Feb, 2026 Received from Anil Joshi ₹500\n04:30 PM UPI Transaction ID: 603492497813',
            'warnings': [],
        }
        result = parse_statement(b'%PDF fake', 'gpay.pdf')
        
        self.assertIn(result['parseMode'], ['pdf_text', 'pdf_ocr'])
        self.assertEqual(result['total_found'], 2)
        self.assertEqual(result['transactions'][0]['date'], '2026-02-01')
        self.assertEqual(result['transactions'][0]['amount'], -3.0)
        self.assertEqual(result['transactions'][1]['date'], '2026-02-03')
        self.assertEqual(result['transactions'][1]['amount'], 500.0)


if __name__ == '__main__':
    unittest.main()
