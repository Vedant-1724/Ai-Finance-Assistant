import unittest
from unittest.mock import patch

from ocr_invoice import parse_invoice_bytes


class InvoiceParserTests(unittest.TestCase):
    @patch('ocr_invoice.extract_text_from_pdf_bytes')
    def test_pdf_invoice_returns_normalized_date_and_review_flags(self, mock_extract):
        mock_extract.return_value = {
            'text': 'Acme Supplies Pvt Ltd\nTax Invoice\nInvoice No: INV-22\nInvoice Date: 11/03/2026\nGrand Total: 1,250.00',
            'warnings': ['Used OCR fallback'],
            'ocrConfidence': 0.66,
        }

        result = parse_invoice_bytes(b'%PDF fake', filename='invoice.pdf')

        self.assertEqual(result['vendor'], 'Acme Supplies Pvt Ltd')
        self.assertEqual(result['date'], '2026-03-11')
        self.assertEqual(result['invoice_no'], 'INV-22')
        self.assertEqual(result['total'], 1250.0)
        self.assertTrue(result['reviewRequired'])
        self.assertIn('Used OCR fallback', result['warnings'])

    @patch('ocr_invoice.extract_text_from_image_bytes')
    def test_boilerplate_vendor_is_not_selected(self, mock_extract):
        mock_extract.return_value = {
            'text': 'Tax Invoice\nReceipt\nDate: 2026-03-11\nTotal Amount: 499.00',
            'warnings': [],
            'ocrConfidence': 0.91,
        }

        result = parse_invoice_bytes(b'fake-image', filename='receipt.png')

        self.assertIsNone(result['vendor'])
        self.assertEqual(result['date'], '2026-03-11')
        self.assertEqual(result['total'], 499.0)
        self.assertTrue(result['reviewRequired'])


if __name__ == '__main__':
    unittest.main()
