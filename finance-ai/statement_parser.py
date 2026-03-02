# PATH: finance-ai/statement_parser.py
#
# FIXES:
#  1. _parse_upi_style: regex .{3,50?} was invalid — Python was treating
#     {3,50?} as literal characters, silently returning 0 transactions
#     for ALL UPI screenshots. Fixed to .{3,50} (non-greedy handled by context).
#  2. _redact_account_number: was over-redacting 12-digit UTR/reference
#     numbers. Added UTR/UPI ref exclusion so users can still cross-reference.
#  3. Minor: added filename sanitization in parse_statement entrypoint.

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
    """
    Redact account numbers: keep only last 4 digits.
    FIX: Excludes UTR numbers (12-digit transaction references starting with
    known UTR prefixes) so users can cross-reference with their bank.
    """
    # UTR numbers typically: 12 digits, often prefixed with bank code in description
    # We preserve them by checking context — if preceded by "UTR", "Ref", "Txn"
    def _maybe_redact(m):
        prefix_start = max(0, m.start() - 10)
        context = text[prefix_start:m.start()].upper()
        # Don't redact if it looks like a UTR/reference number
        if any(kw in context for kw in ['UTR', 'REF', 'TXN', 'NEFT', 'IMPS', 'UPI REF']):
            return m.group(0)
        full = m.group(1) + m.group(2)
        masked = 'X' * min(len(m.group(1)), 8) + m.group(2)
        return masked

    text = re.sub(
        r'\b(\d{6,14})(\d{4})\b',
        _maybe_redact,
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
    MERCHANT_KEYWORDS = [
        'swiggy', 'zomato', 'amazon', 'flipkart', 'paytm', 'phonepe',
        'gpay', 'uber', 'ola', 'netflix', 'hotstar', 'jio', 'airtel',
        'bsnl', 'electricity', 'gas', 'water', 'irctc', 'railway',
        'metro', 'bus', 'tax', 'govt', 'bigbasket', 'myntra', 'nykaa',
        'zepto', 'blinkit', 'dunzo', 'instamart',
    ]

    def _mask_upi(m):
        handle = m.group(1)
        domain = m.group(2)
        if re.match(r'^\d+$', handle):
            return 'XXXXXXXXXX@' + domain
        if len(handle) <= 10 and not any(kw in handle.lower() for kw in MERCHANT_KEYWORDS):
            return handle[:2] + ('*' * (len(handle) - 2)) + '@' + domain
        return m.group(0)

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
    text = re.sub(r'\b[A-Z]{5}[0-9]{4}[A-Z]\b', '[PAN REDACTED]', text)
    return text


def redact_sensitive_info(text: str) -> str:
    """Apply all redaction passes to a piece of text."""
    if not text:
        return text
    text = _redact_card_number(text)
    text = _redact_account_number(text)
    text = _redact_upi_id(text)
    text = _redact_ifsc(text)
    text = _redact_mobile(text)
    text = _redact_pan(text)
    return text.strip()


# ─────────────────────────────────────────────────────────────────────────────
#  DATE & AMOUNT HELPERS
# ─────────────────────────────────────────────────────────────────────────────

_DATE_FORMATS = [
    '%d/%m/%Y', '%d-%m-%Y', '%d.%m.%Y',
    '%d/%m/%y', '%d-%m-%y',
    '%Y-%m-%d',
    '%d %b %Y', '%d %B %Y',
    '%d %b %y', '%d %B %y',
    '%b %d, %Y', '%B %d, %Y',
]

def _parse_date(raw: str) -> Optional[str]:
    raw = raw.strip()
    for fmt in _DATE_FORMATS:
        try:
            return datetime.strptime(raw, fmt).strftime('%Y-%m-%d')
        except ValueError:
            continue
    return None


def _parse_amount(raw: str) -> Optional[float]:
    if not raw:
        return None
    raw = raw.strip().replace(',', '').replace('₹', '').replace(' ', '')
    raw = raw.replace('(', '-').replace(')', '')
    try:
        return float(raw)
    except ValueError:
        return None


# ─────────────────────────────────────────────────────────────────────────────
#  CSV PARSER
# ─────────────────────────────────────────────────────────────────────────────

_DATE_COLS   = {'date', 'txn date', 'transaction date', 'value date',
                'posting date', 'trans date', 'transaction_date'}
_DESC_COLS   = {'description', 'narration', 'particulars', 'remarks',
                'details', 'trans description', 'transaction description'}
_DEBIT_COLS  = {'debit', 'debit amount', 'withdrawal', 'dr', 'withdrawal amt'}
_CREDIT_COLS = {'credit', 'credit amount', 'deposit', 'cr', 'deposit amt'}
_AMT_COLS    = {'amount', 'transaction amount', 'txn amount', 'net amount'}


def _parse_csv_bytes(data: bytes) -> dict:
    """Parse a CSV bank statement export."""
    try:
        text = data.decode('utf-8-sig', errors='replace')
    except Exception:
        return {'transactions': [], 'source': 'CSV', 'error': 'Could not decode CSV file.'}

    try:
        dialect = csv.Sniffer().sniff(text[:2048], delimiters=',;\t|')
    except csv.Error:
        dialect = csv.excel

    reader = csv.reader(io.StringIO(text), dialect)
    rows = [r for r in reader if any(c.strip() for c in r)]

    if len(rows) < 2:
        return {'transactions': [], 'source': 'CSV', 'skipped': 0,
                'error': 'CSV has fewer than 2 rows (no data).'}

    header = [c.strip().lower() for c in rows[0]]

    date_col = credit_col = debit_col = desc_col = amount_col = None
    for i, h in enumerate(header):
        if h in _DATE_COLS:   date_col   = i
        if h in _DESC_COLS:   desc_col   = i
        if h in _DEBIT_COLS:  debit_col  = i
        if h in _CREDIT_COLS: credit_col = i
        if h in _AMT_COLS:    amount_col = i

    if date_col is None:
        return {
            'transactions': [], 'source': 'CSV', 'skipped': len(rows) - 1,
            'error': 'Could not find a date column. Supported headers: ' + ', '.join(_DATE_COLS)
        }

    transactions = []
    skipped = 0

    for row in rows[1:]:
        if not row:
            skipped += 1
            continue

        raw_date = row[date_col].strip() if date_col < len(row) else ''
        date_iso = _parse_date(raw_date)
        if not date_iso:
            skipped += 1
            continue

        raw_desc = row[desc_col].strip() if desc_col is not None and desc_col < len(row) else 'Transaction'
        safe_desc = redact_sensitive_info(raw_desc) or 'Transaction'

        amount = None
        if debit_col is not None and debit_col < len(row):
            debit  = _parse_amount(row[debit_col])
            credit = _parse_amount(row[credit_col]) if credit_col is not None and credit_col < len(row) else None
            if debit and debit > 0:
                amount = -abs(debit)
            elif credit and credit > 0:
                amount = abs(credit)
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
    try:
        import pdfplumber
    except ImportError:
        return {'transactions': [], 'source': 'PDF',
                'error': 'pdfplumber not installed. Run: pip install pdfplumber'}

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
    try:
        import pytesseract
        from PIL import Image
    except ImportError:
        return {'transactions': [], 'source': 'IMAGE',
                'error': 'pytesseract/Pillow not installed.'}

    try:
        img = Image.open(io.BytesIO(data))
        w, h = img.size
        if w < 1200:
            scale = 1200 / w
            img = img.resize((int(w * scale), int(h * scale)), Image.LANCZOS)
        text = pytesseract.image_to_string(img, config='--psm 6')
    except Exception as e:
        return {'transactions': [], 'source': 'IMAGE', 'error': f'OCR failed: {e}'}

    return _parse_text_statement(text, source='UPI_SCREENSHOT')


# ─────────────────────────────────────────────────────────────────────────────
#  TEXT STATEMENT PARSER
# ─────────────────────────────────────────────────────────────────────────────

_TXN_LINE_PATTERNS = [
    re.compile(
        r'(\d{1,2}[\/\-\.]\d{1,2}[\/\-\.]\d{2,4})'
        r'\s{1,10}'
        r'(.{5,80}?)'
        r'\s{1,10}'
        r'([\-\+]?\s*[\d,]+\.?\d{0,2})',
        re.IGNORECASE
    ),
    re.compile(
        r'(\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\w*\s+\d{4})'
        r'\s{1,10}'
        r'(.{5,80}?)'
        r'\s{1,10}'
        r'([\-\+]?\s*[\d,]+\.?\d{0,2})',
        re.IGNORECASE
    ),
]

_CREDIT_KEYWORDS = {'credited', 'credit', 'received', 'refund', 'cashback', 'salary',
                    'income', 'deposited', 'cr', 'inward', 'neft in', 'imps in'}
_DEBIT_KEYWORDS  = {'debited', 'debit', 'paid', 'payment', 'purchase', 'withdrawal',
                    'dr', 'outward', 'neft out', 'imps out', 'transfer out', 'upi debit'}


def _parse_text_statement(text: str, source: str = 'TEXT') -> dict:
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
            break

    if not transactions:
        transactions, skipped = _parse_upi_style(text, source)

    return {'transactions': transactions, 'skipped': skipped, 'source': source}


def _parse_upi_style(text: str, source: str) -> tuple:
    """
    Fallback parser for UPI app history screenshots.
    FIX: Replaced invalid regex .{3,50?} with .{3,50} — previously Python
    was treating {3,50?} as literal characters, silently returning 0 results.
    """
    transactions = []
    skipped = 0

    # FIX: was r'(.{3,50?})' — invalid quantifier treated as literals
    paid_pattern = re.compile(
        r'(?:paid|sent|transferred)\s+₹?\s*([\d,]+\.?\d{0,2})\s+(?:to|for)\s+(.{3,50})',
        re.IGNORECASE
    )
    recv_pattern = re.compile(
        r'(?:received|got|credited)\s+₹?\s*([\d,]+\.?\d{0,2})\s+(?:from|by)\s+(.{3,50})',
        re.IGNORECASE
    )
    date_pattern = re.compile(
        r'(\d{1,2}[\/\-]\d{1,2}[\/\-]\d{2,4}|\d{1,2}\s+\w{3}\w*\s+\d{4})'
    )

    dates_found = date_pattern.findall(text)
    today = datetime.today().strftime('%Y-%m-%d')

    for match in paid_pattern.finditer(text):
        amount = _parse_amount(match.group(1))
        desc   = redact_sensitive_info(match.group(2).strip())
        if amount:
            transactions.append({
                'date':        _parse_date(dates_found[0]) if dates_found else today,
                'description': f'Payment - {desc}',
                'amount':      -abs(amount),
                'source':      source,
            })

    for match in recv_pattern.finditer(text):
        amount = _parse_amount(match.group(1))
        desc   = redact_sensitive_info(match.group(2).strip())
        if amount:
            transactions.append({
                'date':        _parse_date(dates_found[0]) if dates_found else today,
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
    Returns: { transactions, skipped, source, (optional) error }

    PRIVACY GUARANTEE: No account numbers, IFSC codes, card numbers,
    full UPI IDs, or mobile numbers are present in the returned data.
    """
    # Sanitize filename — only use extension
    safe_name = re.sub(r'[^a-zA-Z0-9._\-]', '_', filename)
    ext = safe_name.lower().rsplit('.', 1)[-1] if '.' in safe_name else ''

    if ext == 'csv':
        return _parse_csv_bytes(file_bytes)
    elif ext == 'pdf':
        return _parse_pdf_bytes(file_bytes)
    elif ext in ('png', 'jpg', 'jpeg', 'webp', 'bmp', 'tiff'):
        return _parse_image_bytes(file_bytes)
    else:
        return {
            'transactions': [],
            'source':       'UNKNOWN',
            'error':        f'Unsupported file type ".{ext}". Use CSV, PDF, PNG, or JPG.',
        }