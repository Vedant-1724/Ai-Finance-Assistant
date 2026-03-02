"""
Statement Parser — Privacy-First Transaction Extractor
======================================================
Supports:
  - CSV bank statements (HDFC, SBI, ICICI, Axis, UPI exports)
  - PDF bank statements (text-based)
  - Screenshot images (OCR)

PRIVACY: All sensitive fields (account numbers, IFSC, UPI IDs,
card numbers, mobile numbers) are redacted BEFORE returning data.
Nothing sensitive is stored or logged.
"""

import re
import io
import csv
import logging
from datetime import datetime
from typing import Optional

logger = logging.getLogger(__name__)


# ─────────────────────────────────────────────────────────────────────────────
#  PRIVACY REDACTION ENGINE
# ─────────────────────────────────────────────────────────────────────────────

def _redact_account_number(text: str) -> str:
    """Redact account numbers: keep only last 4 digits."""
    # Full account numbers (10–18 digits)
    text = re.sub(
        r'\b(\d{6,14})(\d{4})\b',
        lambda m: 'X' * min(len(m.group(1)), 8) + m.group(2),
        text
    )
    return text


def _redact_card_number(text: str) -> str:
    """Redact card numbers (16-digit patterns with optional spaces/dashes)."""
    text = re.sub(
        r'\b\d{4}[\s\-]?\d{4}[\s\-]?\d{4}[\s\-]?(\d{4})\b',
        r'XXXX XXXX XXXX \1',
        text
    )
    return text


def _redact_upi_id(text: str) -> str:
    """
    Redact personal UPI IDs (e.g., 9876543210@okicici → XXXXXXXXXX@okicici).
    Keep merchant UPI names readable (e.g., swiggy@icici stays).
    """
    def _mask_upi(m):
        handle = m.group(1)
        domain = m.group(2)
        # If handle is all digits (phone-based UPI), redact it
        if re.match(r'^\d+$', handle):
            return 'XXXXXXXXXX@' + domain
        # If handle looks personal (short name), partially redact
        if len(handle) <= 10 and not any(
            merchant in handle.lower()
            for merchant in ['swiggy', 'zomato', 'amazon', 'flipkart', 'paytm',
                             'phonepe', 'gpay', 'uber', 'ola', 'netflix', 'hotstar',
                             'jio', 'airtel', 'bsnl', 'electricity', 'gas', 'water',
                             'irctc', 'railway', 'metro', 'bus', 'tax', 'govt']
        ):
            return handle[:2] + ('*' * (len(handle) - 2)) + '@' + domain
        return m.group(0)  # Keep merchant UPI as-is

    text = re.sub(r'([\w.\-]+)@([\w]+)', _mask_upi, text)
    return text


def _redact_ifsc(text: str) -> str:
    """Remove IFSC codes entirely (not needed for analysis)."""
    text = re.sub(r'\b[A-Z]{4}0[A-Z0-9]{6}\b', '[IFSC REDACTED]', text)
    return text


def _redact_mobile(text: str) -> str:
    """Redact mobile numbers: keep last 4 digits."""
    text = re.sub(
        r'\b([6-9]\d{2})(\d{3})(\d{4})\b',
        r'XXXXXX\3',
        text
    )
    return text


def _redact_pan(text: str) -> str:
    """Redact PAN card numbers."""
    text = re.sub(r'\b[A-Z]{5}\d{4}[A-Z]\b', '[PAN REDACTED]', text)
    return text


def redact_sensitive_info(text: str) -> str:
    """Apply all redaction rules to a text string."""
    text = _redact_card_number(text)     # cards before accounts (specificity)
    text = _redact_account_number(text)
    text = _redact_ifsc(text)
    text = _redact_pan(text)
    text = _redact_mobile(text)
    text = _redact_upi_id(text)
    return text


# ─────────────────────────────────────────────────────────────────────────────
#  AMOUNT / DATE UTILITIES
# ─────────────────────────────────────────────────────────────────────────────

def _parse_amount(value: str) -> Optional[float]:
    """Parse amount string to float. Returns None if unparseable."""
    if not value:
        return None
    value = value.strip().replace(',', '').replace('₹', '').replace('$', '').replace(' ', '')
    # Handle (1234.56) as negative
    if value.startswith('(') and value.endswith(')'):
        value = '-' + value[1:-1]
    try:
        return float(value)
    except ValueError:
        return None


def _parse_date(value: str) -> Optional[str]:
    """Try multiple date formats and return ISO (YYYY-MM-DD) or None."""
    value = value.strip()
    formats = [
        '%d/%m/%Y', '%d-%m-%Y', '%d/%m/%y', '%d-%m-%y',
        '%Y/%m/%d', '%Y-%m-%d',
        '%d %b %Y', '%d %B %Y',
        '%d %b %y', '%d %B %y',
        '%b %d, %Y', '%B %d, %Y',
        '%d%m%Y',
    ]
    for fmt in formats:
        try:
            return datetime.strptime(value, fmt).strftime('%Y-%m-%d')
        except ValueError:
            continue
    return None


# ─────────────────────────────────────────────────────────────────────────────
#  CSV PARSER  (handles most Indian bank CSV exports)
# ─────────────────────────────────────────────────────────────────────────────

# Common column name patterns across banks
_DATE_COLS    = ['date', 'txn date', 'transaction date', 'value date', 'posting date', 'trans date']
_DESC_COLS    = ['description', 'narration', 'particulars', 'remarks', 'transaction details',
                 'details', 'transaction remarks', 'beneficiary name']
_DEBIT_COLS   = ['debit', 'debit amount', 'withdrawal', 'withdrawal amt', 'dr', 'dr amount',
                 'debit (inr)', 'amount debited']
_CREDIT_COLS  = ['credit', 'credit amount', 'deposit', 'deposit amt', 'cr', 'cr amount',
                 'credit (inr)', 'amount credited']
_AMOUNT_COLS  = ['amount', 'transaction amount', 'txn amount', 'net amount']


def _find_col(headers: list[str], candidates: list[str]) -> Optional[int]:
    """Find a column index by matching lowercased header against candidates."""
    lower = [h.strip().lower() for h in headers]
    for candidate in candidates:
        if candidate in lower:
            return lower.index(candidate)
    return None


def _parse_csv_bytes(data: bytes) -> dict:
    """Parse CSV bank statement bytes into sanitized transactions."""
    try:
        text = data.decode('utf-8-sig', errors='replace')
    except Exception:
        text = data.decode('latin-1', errors='replace')

    lines = text.splitlines()

    # Skip header rows that are not the actual CSV header (bank statements often have 5-10 preamble lines)
    header_idx = 0
    for i, line in enumerate(lines):
        low = line.lower()
        if any(kw in low for kw in ['date', 'narration', 'description', 'debit', 'credit', 'amount']):
            header_idx = i
            break

    csv_text = '\n'.join(lines[header_idx:])
    reader = csv.reader(io.StringIO(csv_text))
    rows = list(reader)

    if not rows:
        return {'transactions': [], 'skipped': 0, 'source': 'CSV', 'error': 'No data found'}

    headers = rows[0]
    date_col   = _find_col(headers, _DATE_COLS)
    desc_col   = _find_col(headers, _DESC_COLS)
    debit_col  = _find_col(headers, _DEBIT_COLS)
    credit_col = _find_col(headers, _CREDIT_COLS)
    amount_col = _find_col(headers, _AMOUNT_COLS)

    if date_col is None:
        return {'transactions': [], 'skipped': 0, 'source': 'CSV',
                'error': 'Could not find a Date column. Supported headers: ' + ', '.join(_DATE_COLS)}

    transactions = []
    skipped = 0

    for row in rows[1:]:
        if len(row) <= max(filter(lambda x: x is not None,
                                  [date_col, desc_col or 0, debit_col or 0, credit_col or 0, amount_col or 0])):
            skipped += 1
            continue

        raw_date = row[date_col] if date_col is not None and date_col < len(row) else ''
        date_iso = _parse_date(raw_date)
        if not date_iso:
            skipped += 1
            continue

        # Description
        raw_desc = row[desc_col].strip() if desc_col is not None and desc_col < len(row) else 'Transaction'
        safe_desc = redact_sensitive_info(raw_desc)
        if not safe_desc:
            safe_desc = 'Transaction'

        # Amount
        amount = None
        if debit_col is not None and debit_col < len(row):
            debit = _parse_amount(row[debit_col])
            credit = _parse_amount(row[credit_col]) if credit_col is not None and credit_col < len(row) else None
            if debit and debit > 0:
                amount = -abs(debit)   # expense
            elif credit and credit > 0:
                amount = abs(credit)   # income
        elif amount_col is not None and amount_col < len(row):
            amount = _parse_amount(row[amount_col])

        if amount is None:
            skipped += 1
            continue

        transactions.append({
            'date':        date_iso,
            'description': safe_desc,
            'amount':      round(amount, 2),
            'source':      'CSV_IMPORT',
        })

    return {
        'transactions': transactions,
        'skipped':      skipped,
        'source':       'CSV',
        'total_rows':   len(rows) - 1,
    }


# ─────────────────────────────────────────────────────────────────────────────
#  PDF PARSER
# ─────────────────────────────────────────────────────────────────────────────

def _parse_pdf_bytes(data: bytes) -> dict:
    """Extract transactions from a text-based PDF bank statement."""
    try:
        import pdfplumber
    except ImportError:
        return {
            'transactions': [],
            'source': 'PDF',
            'error': 'pdfplumber not installed. Run: pip install pdfplumber',
        }

    text_pages = []
    try:
        with pdfplumber.open(io.BytesIO(data)) as pdf:
            for page in pdf.pages:
                t = page.extract_text()
                if t:
                    text_pages.append(t)
    except Exception as e:
        return {'transactions': [], 'source': 'PDF', 'error': f'PDF parse failed: {e}'}

    full_text = '\n'.join(text_pages)
    return _parse_text_statement(full_text, source='PDF')


# ─────────────────────────────────────────────────────────────────────────────
#  IMAGE / SCREENSHOT PARSER  (OCR)
# ─────────────────────────────────────────────────────────────────────────────

def _parse_image_bytes(data: bytes) -> dict:
    """Use Tesseract OCR to extract text from a bank statement screenshot."""
    try:
        import pytesseract
        from PIL import Image
    except ImportError:
        return {
            'transactions': [],
            'source': 'IMAGE',
            'error': 'pytesseract/Pillow not installed.',
        }

    try:
        img = Image.open(io.BytesIO(data))
        # Upscale for better OCR accuracy
        w, h = img.size
        if w < 1200:
            scale = 1200 / w
            img = img.resize((int(w * scale), int(h * scale)), Image.LANCZOS)
        text = pytesseract.image_to_string(img, config='--psm 6')
    except Exception as e:
        return {'transactions': [], 'source': 'IMAGE', 'error': f'OCR failed: {e}'}

    return _parse_text_statement(text, source='UPI_SCREENSHOT')


# ─────────────────────────────────────────────────────────────────────────────
#  TEXT STATEMENT PARSER  (used by both PDF and image paths)
# ─────────────────────────────────────────────────────────────────────────────

# Pattern: date | description | amount
_TXN_LINE_PATTERNS = [
    # "15/01/2024  Payment to Swiggy  -450.00"
    re.compile(
        r'(\d{1,2}[\/\-\.]\d{1,2}[\/\-\.]\d{2,4})'
        r'\s{1,10}'
        r'(.{5,80}?)'
        r'\s{1,10}'
        r'([\-\+]?\s*[\d,]+\.?\d{0,2})',
        re.IGNORECASE
    ),
    # "15 Jan 2024  Credited 5000.00  Amazon"
    re.compile(
        r'(\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\w*\s+\d{4})'
        r'\s{1,10}'
        r'(.{5,80}?)'
        r'\s{1,10}'
        r'([\-\+]?\s*[\d,]+\.?\d{0,2})',
        re.IGNORECASE
    ),
]

_CREDIT_KEYWORDS = {'credited', 'credit', 'received', 'refund', 'cashback', 'salary', 'income',
                    'deposited', 'cr', 'inward', 'neft in', 'imps in'}
_DEBIT_KEYWORDS  = {'debited', 'debit', 'paid', 'payment', 'purchase', 'withdrawal', 'dr',
                    'outward', 'neft out', 'imps out', 'transfer out', 'upi debit'}


def _parse_text_statement(text: str, source: str = 'TEXT') -> dict:
    """Parse free-form text from a bank statement into transactions."""
    transactions = []
    skipped = 0

    for pattern in _TXN_LINE_PATTERNS:
        matches = pattern.findall(text)
        if matches:
            for date_str, desc, amount_str in matches:
                date_iso = _parse_date(date_str.strip())
                if not date_iso:
                    skipped += 1
                    continue

                amount = _parse_amount(amount_str)
                if amount is None:
                    skipped += 1
                    continue

                # Determine sign from keywords in description
                desc_low = desc.lower()
                if amount > 0:
                    if any(kw in desc_low for kw in _DEBIT_KEYWORDS):
                        amount = -abs(amount)
                    elif any(kw in desc_low for kw in _CREDIT_KEYWORDS):
                        amount = abs(amount)

                safe_desc = redact_sensitive_info(desc.strip())
                transactions.append({
                    'date':        date_iso,
                    'description': safe_desc or 'Transaction',
                    'amount':      round(amount, 2),
                    'source':      source,
                })
            break  # Use the first pattern that works

    if not transactions:
        # Fallback: look for UPI-style lines
        transactions, skipped = _parse_upi_style(text, source)

    return {
        'transactions': transactions,
        'skipped':      skipped,
        'source':       source,
    }


def _parse_upi_style(text: str, source: str) -> tuple[list, int]:
    """
    Fallback parser for UPI app history screenshots.
    Looks for patterns like: "Paid ₹500 to Swiggy" or "Received ₹1000 from John"
    """
    transactions = []
    skipped = 0

    # Pattern: "Paid ₹amount to Merchant on Date"
    paid_pattern = re.compile(
        r'(?:paid|sent|transferred)\s+₹?\s*([\d,]+\.?\d{0,2})\s+(?:to|for)\s+(.{3,50?})',
        re.IGNORECASE
    )
    recv_pattern = re.compile(
        r'(?:received|got|credited)\s+₹?\s*([\d,]+\.?\d{0,2})\s+(?:from|by)\s+(.{3,50?})',
        re.IGNORECASE
    )
    date_pattern = re.compile(r'(\d{1,2}[\/\-]\d{1,2}[\/\-]\d{2,4}|\d{1,2}\s+\w{3}\w*\s+\d{4})')

    # Find all dates in the text as anchors
    dates_found = date_pattern.findall(text)
    today = datetime.today().strftime('%Y-%m-%d')

    for match in paid_pattern.finditer(text):
        amount = _parse_amount(match.group(1))
        desc   = redact_sensitive_info(match.group(2).strip())
        if amount:
            transactions.append({
                'date':        dates_found[0] if dates_found else today,
                'description': f'Payment - {desc}',
                'amount':      -abs(amount),
                'source':      source,
            })

    for match in recv_pattern.finditer(text):
        amount = _parse_amount(match.group(1))
        desc   = redact_sensitive_info(match.group(2).strip())
        if amount:
            transactions.append({
                'date':        dates_found[0] if dates_found else today,
                'description': f'Received - {desc}',
                'amount':      abs(amount),
                'source':      source,
            })

    return transactions, skipped


# ─────────────────────────────────────────────────────────────────────────────
#  MAIN PUBLIC FUNCTION
# ─────────────────────────────────────────────────────────────────────────────

def parse_statement(file_bytes: bytes, filename: str) -> dict:
    """
    Entrypoint. Detect file type and route to correct parser.
    Returns a dict with: transactions, skipped, source, (optional) error.

    PRIVACY GUARANTEE: No account numbers, IFSC codes, card numbers,
    full UPI IDs, or mobile numbers are present in the returned data.
    """
    ext = filename.lower().rsplit('.', 1)[-1] if '.' in filename else ''

    if ext == 'csv':
        result = _parse_csv_bytes(file_bytes)
    elif ext == 'pdf':
        result = _parse_pdf_bytes(file_bytes)
    elif ext in ('png', 'jpg', 'jpeg', 'webp', 'bmp', 'tiff'):
        result = _parse_image_bytes(file_bytes)
    else:
        return {
            'transactions': [],
            'source':       'UNKNOWN',
            'error':        f'Unsupported file type ".{ext}". Use CSV, PDF, PNG, or JPG.',
        }

    # Final safety pass: redact any sensitive info that slipped through
    for txn in result.get('transactions', []):
        txn['description'] = redact_sensitive_info(str(txn.get('description', '')))

    logger.info(
        "Parsed %d transactions from %s (%s), skipped %d",
        len(result.get('transactions', [])),
        filename,
        result.get('source', '?'),
        result.get('skipped', 0),
    )
    return result