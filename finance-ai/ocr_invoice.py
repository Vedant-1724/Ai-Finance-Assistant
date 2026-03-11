import logging
import os
import re
from datetime import datetime

from document_utils import IMAGE_EXTENSIONS, detect_file_type, extract_text_from_image_bytes, extract_text_from_pdf_bytes, heif_enabled

logger = logging.getLogger(__name__)

_TOTAL_PATTERNS = [
    r'(?i)grand\s*total\s*[:\-₹$]?\s*([\d,]+\.?\d{0,2})',
    r'(?i)total\s*amount\s*[:\-₹$]?\s*([\d,]+\.?\d{0,2})',
    r'(?i)amount\s*due\s*[:\-₹$]?\s*([\d,]+\.?\d{0,2})',
    r'(?i)net\s*amount\s*[:\-₹$]?\s*([\d,]+\.?\d{0,2})',
    r'(?i)invoice\s*value\s*[:\-₹$]?\s*([\d,]+\.?\d{0,2})',
    r'(?i)total\s*[:\-₹$]?\s*([\d,]+\.?\d{0,2})',
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
_DATE_FORMATS = [
    '%d/%m/%Y', '%d-%m-%Y', '%d.%m.%Y',
    '%d/%m/%y', '%d-%m-%y', '%d.%m.%y',
    '%Y-%m-%d', '%Y/%m/%d',
    '%d %b %Y', '%d %B %Y',
    '%b %d, %Y', '%B %d, %Y',
]
_INVOICE_NO_PATTERNS = [
    r'(?i)invoice\s*(?:no|number|#|num)\s*[:\-]?\s*([A-Z0-9\-\/]+)',
    r'(?i)bill\s*(?:no|number|#)\s*[:\-]?\s*([A-Z0-9\-\/]+)',
    r'(?i)receipt\s*(?:no|number|#)\s*[:\-]?\s*([A-Z0-9\-\/]+)',
]
_VENDOR_PATTERNS = [
    r'(?im)^(?:vendor|supplier|merchant|billed\s+by|sold\s+by|issued\s+by|from)\s*[:\-]\s*(.+)$',
]
_VENDOR_SKIP = {
    'invoice', 'tax invoice', 'receipt', 'bill', 'cash memo', 'original', 'copy', 'duplicate',
    'gst invoice', 'payment receipt', 'invoice summary', 'tax', 'gst'
}
_VENDOR_SKIP_TOKENS = {
    'invoice', 'receipt', 'gst', 'cgst', 'sgst', 'igst', 'tax', 'amount', 'total', 'subtotal',
    'date', 'invoice no', 'bill no', 'hsn', 'sac', 'qty', 'quantity', 'unit price', 'description'
}


def _try_float(value: str):
    try:
        return float(str(value).replace(',', ''))
    except (ValueError, AttributeError):
        return None


def _normalize_date(raw: str | None) -> str | None:
    if not raw:
        return None
    cleaned = raw.strip()
    for fmt in _DATE_FORMATS:
        try:
            return datetime.strptime(cleaned, fmt).strftime('%Y-%m-%d')
        except ValueError:
            continue
    return None


def _looks_like_vendor(line: str) -> bool:
    cleaned = ' '.join((line or '').split()).strip(':- ')
    lowered = cleaned.lower()
    if len(cleaned) < 3 or len(cleaned) > 80:
        return False
    if lowered in _VENDOR_SKIP:
        return False
    if any(token in lowered for token in _VENDOR_SKIP_TOKENS):
        return False
    if re.search(r'\d{2,}', cleaned):
        return False
    if re.search(r'[₹$€]', cleaned):
        return False
    if '@' in cleaned or 'www.' in lowered:
        return False
    return bool(re.search(r'[A-Za-z]', cleaned))


def _extract_vendor(lines: list[str], text: str) -> str | None:
    for pattern in _VENDOR_PATTERNS:
        match = re.search(pattern, text)
        if match:
            candidate = ' '.join(match.group(1).split()).strip(':- ')
            if _looks_like_vendor(candidate):
                return candidate

    for line in lines[:12]:
        candidate = ' '.join(line.split()).strip(':- ')
        if _looks_like_vendor(candidate):
            return candidate
    return None


def _build_note(reasons: list[str], warnings: list[str]) -> str | None:
    merged = []
    for item in reasons + warnings:
        if item and item not in merged:
            merged.append(item)
    if not merged:
        return None
    return ' '.join(merged[:3])


def _extract_fields(text: str, confidence: float, warnings: list[str]) -> dict:
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    result = {
        'raw_text': text,
        'vendor': _extract_vendor(lines, text),
        'date': None,
        'invoice_no': None,
        'total': None,
        'currency': 'INR',
        'note': None,
        'warnings': warnings,
        'ocr_confidence': round(confidence, 3),
        'reviewRequired': False,
    }

    for pattern in _TOTAL_PATTERNS:
        match = re.search(pattern, text)
        if not match:
            continue
        value = _try_float(match.group(1))
        if value is not None and value > 0:
            result['total'] = value
            break

    if '₹' in text or 'INR' in text or 'Rs.' in text or 'Rs ' in text:
        result['currency'] = 'INR'
    elif '$' in text or 'USD' in text:
        result['currency'] = 'USD'
    elif '€' in text or 'EUR' in text:
        result['currency'] = 'EUR'

    for pattern in _DATE_PATTERNS:
        match = re.search(pattern, text)
        if match:
            result['date'] = _normalize_date(match.group(1))
            if result['date']:
                break

    for pattern in _INVOICE_NO_PATTERNS:
        match = re.search(pattern, text)
        if match:
            result['invoice_no'] = match.group(1)
            break

    review_reasons = []
    if not result['vendor']:
        review_reasons.append('Vendor name could not be extracted confidently.')
    if not result['date']:
        review_reasons.append('Invoice date could not be normalized confidently.')
    if result['total'] is None:
        review_reasons.append('Invoice total could not be extracted confidently.')
    if confidence < 0.72:
        review_reasons.append('OCR confidence is low, so the invoice should be reviewed manually.')
    if warnings:
        review_reasons.append('OCR reported extraction warnings.')

    result['reviewRequired'] = bool(review_reasons)
    result['note'] = _build_note(review_reasons, warnings)
    return result


def parse_invoice_bytes(data: bytes, filename: str = 'invoice.png') -> dict:
    detected_type = detect_file_type(data, filename)
    warnings = []

    if detected_type == 'pdf':
        extraction = extract_text_from_pdf_bytes(data)
        text = extraction.get('text') or ''
        warnings.extend(extraction.get('warnings') or [])
        confidence = extraction.get('ocrConfidence') or (0.82 if text else 0.0)
    elif detected_type in IMAGE_EXTENSIONS or detected_type == 'image':
        extraction = extract_text_from_image_bytes(data)
        text = extraction.get('text') or ''
        warnings.extend(extraction.get('warnings') or [])
        confidence = extraction.get('ocrConfidence') or 0.0
    else:
        supported = 'PDF, PNG, JPG, WEBP, BMP, TIFF'
        if heif_enabled():
            supported += ', HEIC'
        return {
            'vendor': None,
            'date': None,
            'invoice_no': None,
            'total': None,
            'currency': 'INR',
            'raw_text': '',
            'note': f'Unsupported invoice format. Use {supported}.',
            'warnings': [f'Unsupported file type: {os.path.splitext(filename.lower())[1] or "unknown"}'],
            'ocr_confidence': 0.0,
            'reviewRequired': True,
        }

    if not text.strip():
        return {
            'vendor': None,
            'date': None,
            'invoice_no': None,
            'total': None,
            'currency': 'INR',
            'raw_text': '',
            'note': 'No readable invoice text could be extracted. Try a clearer scan or PDF export.',
            'warnings': warnings,
            'ocr_confidence': round(float(confidence or 0.0), 3),
            'reviewRequired': True,
        }

    result = _extract_fields(text, float(confidence or 0.0), warnings)
    logger.info('Invoice OCR extracted %d chars from %s', len(text), filename)
    return result


def parse_invoice(image_path: str) -> dict:
    if not os.path.exists(image_path):
        return {'error': f'File not found: {image_path}'}
    with open(image_path, 'rb') as handle:
        data = handle.read()
    return parse_invoice_bytes(data, filename=os.path.basename(image_path))


if __name__ == '__main__':
    print('OCR Invoice Parser ready.')
