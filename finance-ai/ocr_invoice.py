"""
OCR Invoice Parser
==================
Accepts either raw bytes (used by /ocr Flask route) or a disk file path.

Graceful degradation:
  - Tesseract not installed → returns demo result with installation note.
  - PDF uploaded           → returns clear instructions (needs pdf2image).
"""

import re
import os
import io
import logging

logger = logging.getLogger(__name__)

_TOTAL_PATTERNS = [
    r'(?i)grand\s*total\s*[:\-₹$]?\s*([\d,]+\.?\d{0,2})',
    r'(?i)total\s*amount\s*[:\-₹$]?\s*([\d,]+\.?\d{0,2})',
    r'(?i)amount\s*due\s*[:\-₹$]?\s*([\d,]+\.?\d{0,2})',
    r'(?i)net\s*amount\s*[:\-₹$]?\s*([\d,]+\.?\d{0,2})',
    r'(?i)total\s*[:\-₹$]?\s*([\d,]+\.?\d{0,2})',
    r'(?i)subtotal\s*[:\-₹$]?\s*([\d,]+\.?\d{0,2})',
    r'₹\s*([\d,]+\.?\d{0,2})',
    r'\$\s*([\d,]+\.?\d{0,2})',
]

_DATE_PATTERNS = [
    r'(?i)(?:invoice\s*date|date|dated|bill\s*date)\s*[:\-]?\s*(\d{1,2}[\/\-\.]\d{1,2}[\/\-\.]\d{2,4})',
    r'(\d{4}[\/\-]\d{2}[\/\-]\d{2})',
    r'(\d{1,2}[\/\-]\d{1,2}[\/\-]\d{4})',
    r'(\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+\d{4})',
    r'((?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+\d{1,2},?\s+\d{4})',
]

_INVOICE_NO_PATTERNS = [
    r'(?i)invoice\s*(?:no|number|#|num)\s*[:\-]?\s*([A-Z0-9\-\/]+)',
    r'(?i)bill\s*(?:no|number|#)\s*[:\-]?\s*([A-Z0-9\-\/]+)',
    r'(?i)receipt\s*(?:no|number|#)\s*[:\-]?\s*([A-Z0-9\-\/]+)',
]


def _try_float(s: str) -> float | None:
    try:
        return float(s.replace(',', ''))
    except (ValueError, AttributeError):
        return None


def _extract_fields(text: str) -> dict:
    result: dict = {
        'raw_text':   text,
        'vendor':     None,
        'date':       None,
        'invoice_no': None,
        'total':      None,
        'currency':   'INR',
        'note':       None,
    }

    lines = [ln.strip() for ln in text.splitlines() if ln.strip()]

    skip = {'invoice', 'bill', 'receipt', 'tax', 'gst', 'original', 'copy',
            'duplicate', 'tax invoice', 'proforma invoice'}
    for line in lines[:8]:
        if len(line) > 3 and line.lower() not in skip and not line.isdigit():
            result['vendor'] = line
            break

    for pattern in _TOTAL_PATTERNS:
        m = re.search(pattern, text)
        if m:
            val = _try_float(m.group(1))
            if val is not None and val > 0:
                result['total'] = val
                break

    if '₹' in text or 'INR' in text or 'Rs.' in text or 'Rs ' in text:
        result['currency'] = 'INR'
    elif '$' in text or 'USD' in text:
        result['currency'] = 'USD'
    elif '€' in text or 'EUR' in text:
        result['currency'] = 'EUR'

    for pattern in _DATE_PATTERNS:
        m = re.search(pattern, text)
        if m:
            result['date'] = m.group(1)
            break

    for pattern in _INVOICE_NO_PATTERNS:
        m = re.search(pattern, text)
        if m:
            result['invoice_no'] = m.group(1)
            break

    return result


def _demo_result(filename: str) -> dict:
    return {
        'vendor':     'Demo Vendor Pvt Ltd',
        'date':       '2026-02-25',
        'invoice_no': 'INV-2026-0042',
        'total':      12500.0,
        'currency':   'INR',
        'raw_text':   f'[Demo mode — Tesseract not installed]\nFile: {filename}',
        'note': (
            'Tesseract OCR is not installed. '
            'Windows: https://github.com/UB-Mannheim/tesseract/wiki  |  '
            'pip install pytesseract pillow'
        ),
    }


# ── PRIMARY: accepts raw bytes — used by Flask /ocr ──────────────────────────
def parse_invoice_bytes(data: bytes, filename: str = 'invoice.png') -> dict:
    ext = os.path.splitext(filename.lower())[1]

    if ext == '.pdf':
        try:
            from pdf2image import convert_from_bytes
            pages = convert_from_bytes(data, first_page=1, last_page=1)
            img = pages[0]
        except ImportError:
            return {
                'vendor': None, 'date': None, 'invoice_no': None,
                'total': None, 'currency': 'INR', 'raw_text': '',
                'note': (
                    'PDF support requires pdf2image + poppler. '
                    'Upload a PNG or JPG screenshot of the invoice instead.'
                ),
            }
        except Exception as e:
            return {
                'vendor': None, 'date': None, 'invoice_no': None,
                'total': None, 'currency': 'INR', 'raw_text': '',
                'note': f'PDF conversion failed: {e}',
            }
    else:
        try:
            from PIL import Image
            img = Image.open(io.BytesIO(data)).convert('L')
        except Exception as e:
            return {
                'vendor': None, 'date': None, 'invoice_no': None,
                'total': None, 'currency': 'INR', 'raw_text': '',
                'note': f'Could not open image: {e}',
            }

    try:
        import pytesseract
        text = pytesseract.image_to_string(img, lang='eng')
        logger.info('OCR: %d chars from %s', len(text), filename)
        return _extract_fields(text)
    except ImportError:
        logger.warning('pytesseract not installed — demo mode')
        return _demo_result(filename)
    except Exception as e:
        logger.error('OCR failed for %s: %s', filename, e)
        return {
            'vendor': None, 'date': None, 'invoice_no': None,
            'total': None, 'currency': 'INR', 'raw_text': '',
            'note': f'OCR error: {e}',
        }


# ── LEGACY: disk path ─────────────────────────────────────────────────────────
def parse_invoice(image_path: str) -> dict:
    if not os.path.exists(image_path):
        return {'error': f'File not found: {image_path}'}
    with open(image_path, 'rb') as f:
        data = f.read()
    return parse_invoice_bytes(data, filename=os.path.basename(image_path))


if __name__ == '__main__':
    print('OCR Invoice Parser ready.')
    print('Usage: parse_invoice("path/to/invoice.png")')