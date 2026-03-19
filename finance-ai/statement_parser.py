import csv
import io
import logging
import re
from collections import Counter
from datetime import datetime
from typing import Callable, Optional

from document_utils import (
    IMAGE_EXTENSIONS,
    best_warning,
    detect_file_type,
    extract_text_from_image_bytes,
    extract_text_from_pdf_bytes,
    heif_enabled,
)

logger = logging.getLogger(__name__)

_DATE_FORMATS = [
    '%d/%m/%Y', '%d-%m-%Y', '%d.%m.%Y',
    '%d/%m/%y', '%d-%m-%y',
    '%Y-%m-%d',
    '%d %b %Y', '%d %B %Y',
    '%d %b %y', '%d %B %y',
    '%b %d, %Y', '%B %d, %Y',
    '%d %b, %Y', '%d %B, %Y',
]

_DATE_COLS = {
    'date', 'txn date', 'transaction date', 'value date', 'posting date', 'trans date',
    'transaction date/time', 'transaction date time', 'transaction_date', 'txn date.',
    'txn. date', 'tran date', 'date time'
}
_DESC_COLS = {
    'description', 'narration', 'particulars', 'remarks', 'details', 'trans description',
    'transaction description', 'transaction details', 'merchant', 'payee', 'beneficiary'
}
_DEBIT_COLS = {'debit', 'debit amount', 'withdrawal', 'dr', 'withdrawal amt', 'paid out'}
_CREDIT_COLS = {'credit', 'credit amount', 'deposit', 'cr', 'deposit amt', 'paid in'}
_AMT_COLS = {'amount', 'transaction amount', 'txn amount', 'net amount', 'value'}
_BALANCE_COLS = {'balance', 'closing balance', 'running balance', 'available balance'}
_CREDIT_KEYWORDS = {
    'credited', 'credit', 'received', 'refund', 'cashback', 'salary', 'income', 'deposited',
    'cr', 'inward', 'neft in', 'imps in', 'upi credit', 'credited by', 'from'
}
_DEBIT_KEYWORDS = {
    'debited', 'debit', 'paid', 'payment', 'purchase', 'withdrawal', 'dr', 'outward',
    'neft out', 'imps out', 'transfer out', 'upi debit', 'charges', 'sent to', 'to'
}
_AMOUNT_PATTERN = re.compile(r'(?<!\d)(?:[+\-]?\(?₹?\s*[\d,]+(?:\.\d{1,2})?\)?)(?!\d)')
_DATE_ANYWHERE_PATTERN = re.compile(
    r'(\d{1,2}[\/\-.]\d{1,2}[\/\-.]\d{2,4}|\d{4}-\d{2}-\d{2}|\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\w*[\s,]+\d{2,4})',
    re.IGNORECASE,
)
_CANONICAL_SOURCE_LABELS = {
    'CSV_IMPORT': 'CSV import',
    'PDF': 'PDF statement',
    'IMAGE': 'Image statement',
    'UPI_SCREENSHOT': 'UPI screenshot',
    'BANK_SYNC': 'Bank sync',
    'LLM_FALLBACK': 'AI fallback',
    'IMPORT': 'Imported transaction',
}


def _redact_account_number(text: str) -> str:
    def _maybe_redact(match):
        prefix_start = max(0, match.start() - 10)
        context = text[prefix_start:match.start()].upper()
        if any(keyword in context for keyword in ['UTR', 'REF', 'TXN', 'NEFT', 'IMPS', 'UPI REF']):
            return match.group(0)
        return 'X' * min(len(match.group(1)), 8) + match.group(2)

    return re.sub(r'\b(\d{6,14})(\d{4})\b', _maybe_redact, text)


def _redact_card_number(text: str) -> str:
    return re.sub(r'\b\d{4}[\s\-]?\d{4}[\s\-]?\d{4}[\s\-]?(\d{4})\b', r'XXXX XXXX XXXX \1', text)


def _redact_upi_id(text: str) -> str:
    merchant_keywords = {
        'swiggy', 'zomato', 'amazon', 'flipkart', 'paytm', 'phonepe', 'gpay', 'uber', 'ola',
        'netflix', 'hotstar', 'jio', 'airtel', 'bsnl', 'electricity', 'gas', 'water', 'irctc',
        'railway', 'metro', 'bus', 'tax', 'govt', 'bigbasket', 'myntra', 'nykaa', 'zepto',
        'blinkit', 'dunzo', 'instamart'
    }

    def _mask(match):
        handle = match.group(1)
        domain = match.group(2)
        if re.match(r'^\d+$', handle):
            return 'XXXXXXXXXX@' + domain
        if len(handle) <= 10 and not any(keyword in handle.lower() for keyword in merchant_keywords):
            return handle[:2] + ('*' * (len(handle) - 2)) + '@' + domain
        return match.group(0)

    return re.sub(r'([\w.\-]+)@([\w]+)', _mask, text)


def _redact_ifsc(text: str) -> str:
    return re.sub(r'\b[A-Z]{4}0[A-Z0-9]{6}\b', '[IFSC REDACTED]', text)


def _redact_mobile(text: str) -> str:
    return re.sub(r'\b([6-9]\d{2})(\d{3})(\d{4})\b', r'XXXXXX\3', text)


def _redact_pan(text: str) -> str:
    return re.sub(r'\b[A-Z]{5}[0-9]{4}[A-Z]\b', '[PAN REDACTED]', text)


def redact_sensitive_info(text: str) -> str:
    if not text:
        return text
    text = _redact_card_number(text)
    text = _redact_account_number(text)
    text = _redact_upi_id(text)
    text = _redact_ifsc(text)
    text = _redact_mobile(text)
    text = _redact_pan(text)
    return ' '.join(text.split()).strip()


def _parse_date(raw: str) -> Optional[str]:
    raw = (raw or '').strip()
    for fmt in _DATE_FORMATS:
        try:
            return datetime.strptime(raw, fmt).strftime('%Y-%m-%d')
        except ValueError:
            continue
    return None


def _parse_amount(raw: str) -> Optional[float]:
    if raw is None:
        return None
    cleaned = str(raw).strip().replace(',', '').replace('₹', '').replace(' ', '')
    cleaned = cleaned.replace('(', '-').replace(')', '')
    if cleaned.endswith('CR') or cleaned.endswith('Cr'):
        cleaned = cleaned[:-2]
    if cleaned.endswith('DR') or cleaned.endswith('Dr'):
        cleaned = '-' + cleaned[:-2]
    try:
        return float(cleaned)
    except ValueError:
        return None


def _normalize_description(text: str) -> str:
    return redact_sensitive_info(text or 'Transaction') or 'Transaction'


def _confidence(value: float) -> float:
    return round(max(0.05, min(0.99, value)), 3)


def _source_label(normalized_source: str) -> str:
    return _CANONICAL_SOURCE_LABELS.get(normalized_source, 'Imported transaction')


def _normalize_row(
    date_iso: str,
    description: str,
    amount: float,
    normalized_source: str,
    confidence: float,
    needs_review: bool,
    row_warnings: Optional[list[str]] = None,
) -> dict:
    row = {
        'date': date_iso,
        'description': _normalize_description(description),
        'amount': round(float(amount), 2),
        'source': _source_label(normalized_source),
        'confidence': _confidence(confidence),
        'needsReview': bool(needs_review or confidence < 0.72),
        'normalizedSource': normalized_source,
    }
    warnings = _dedupe_list(row_warnings or [])
    if warnings:
        row['warnings'] = warnings
    return row


def _row_key(row: dict) -> tuple[str, str, float]:
    return row['date'], row['description'], row['amount']


def _score_row(row: dict) -> tuple[float, int]:
    return float(row.get('confidence', 0.0)), 0 if row.get('needsReview') else 1


def _dedupe_transactions(rows: list[dict]) -> list[dict]:
    deduped: dict[tuple[str, str, float], dict] = {}
    for row in rows:
        key = _row_key(row)
        existing = deduped.get(key)
        if existing is None or _score_row(row) > _score_row(existing):
            deduped[key] = row
    return list(deduped.values())


def _choose_result_source(rows: list[dict], fallback_normalized: str) -> str:
    if not rows:
        return _source_label(fallback_normalized)
    counter = Counter(row.get('normalizedSource', fallback_normalized) for row in rows)
    winner = counter.most_common(1)[0][0]
    return _source_label(winner)


def _build_result(
    transactions: list[dict],
    skipped: int,
    fallback_source: str,
    parse_mode: str,
    warnings: list[str],
    raw_text: str = '',
    error: str | None = None,
) -> dict:
    transactions = _dedupe_transactions(transactions)
    document_confidence = round(sum(txn['confidence'] for txn in transactions) / len(transactions), 3) if transactions else 0.0
    result = {
        'transactions': transactions,
        'total_found': len(transactions),
        'skipped': skipped,
        'source': _choose_result_source(transactions, fallback_source),
        'parseMode': parse_mode,
        'documentConfidence': document_confidence,
        'warnings': _dedupe_list(warnings),
        'rawText': raw_text,
    }
    warning = best_warning(result['warnings'])
    if warning:
        result['warning'] = warning
    if error:
        result['error'] = error
    return result


def _dedupe_list(items: list[str]) -> list[str]:
    seen = set()
    result = []
    for item in items:
        if item and item not in seen:
            seen.add(item)
            result.append(item)
    return result


def _normalize_header(cell: str) -> str:
    return re.sub(r'[^a-z0-9]+', ' ', (cell or '').strip().lower()).strip()


def _parse_csv_bytes(data: bytes) -> dict:
    try:
        text = data.decode('utf-8-sig', errors='replace')
    except Exception:
        return _build_result([], 0, 'CSV_IMPORT', 'csv', [], error='Could not decode CSV file.')

    try:
        dialect = csv.Sniffer().sniff(text[:2048], delimiters=',;\t|')
    except csv.Error:
        dialect = csv.excel

    reader = csv.reader(io.StringIO(text), dialect)
    rows = [row for row in reader if any(cell.strip() for cell in row)]
    if len(rows) < 2:
        return _build_result([], 0, 'CSV_IMPORT', 'csv', [], raw_text=text, error='CSV has fewer than 2 rows (no data).')

    header = [_normalize_header(cell) for cell in rows[0]]
    mapping = {'date': None, 'description': None, 'debit': None, 'credit': None, 'amount': None, 'balance': None}
    for index, column in enumerate(header):
        if column in _DATE_COLS:
            mapping['date'] = index
        elif column in _DESC_COLS:
            mapping['description'] = index
        elif column in _DEBIT_COLS:
            mapping['debit'] = index
        elif column in _CREDIT_COLS:
            mapping['credit'] = index
        elif column in _AMT_COLS:
            mapping['amount'] = index
        elif column in _BALANCE_COLS:
            mapping['balance'] = index

    if mapping['date'] is None:
        return _build_result([], len(rows) - 1, 'CSV_IMPORT', 'csv', [], raw_text=text, error='Could not find a date column in the CSV statement.')

    transactions = []
    skipped = 0
    warnings = []
    for row_index, row in enumerate(rows[1:], start=2):
        raw_date = row[mapping['date']].strip() if mapping['date'] < len(row) else ''
        date_iso = _parse_date(raw_date)
        if not date_iso:
            skipped += 1
            continue

        description = row[mapping['description']].strip() if mapping['description'] is not None and mapping['description'] < len(row) else 'Transaction'
        amount = None
        needs_review = False
        row_warnings = []

        if mapping['debit'] is not None or mapping['credit'] is not None:
            debit = _parse_amount(row[mapping['debit']]) if mapping['debit'] is not None and mapping['debit'] < len(row) else None
            credit = _parse_amount(row[mapping['credit']]) if mapping['credit'] is not None and mapping['credit'] < len(row) else None
            if debit not in (None, 0):
                amount = -abs(debit)
            elif credit not in (None, 0):
                amount = abs(credit)
        elif mapping['amount'] is not None and mapping['amount'] < len(row):
            amount = _parse_amount(row[mapping['amount']])
            if amount is not None:
                lowered = description.lower()
                if amount > 0 and any(keyword in lowered for keyword in _DEBIT_KEYWORDS):
                    amount = -abs(amount)
                    needs_review = True
                    row_warnings.append('Amount sign inferred from description.')
                elif amount > 0 and not any(keyword in lowered for keyword in _CREDIT_KEYWORDS):
                    needs_review = True
                    row_warnings.append('Amount sign could not be confirmed from the CSV row.')

        if amount is None:
            skipped += 1
            continue

        if needs_review:
            warnings.append(f'Row {row_index} needs review because debit/credit direction was inferred.')

        transactions.append(_normalize_row(date_iso, description, amount, 'CSV_IMPORT', 0.97 if not needs_review else 0.78, needs_review, row_warnings))

    return _build_result(transactions, skipped, 'CSV_IMPORT', 'csv', warnings, raw_text=text)


def _clean_lines(text: str) -> list[str]:
    lines = []
    for line in text.splitlines():
        compact = ' '.join(line.strip().split())
        if compact:
            lines.append(compact)
    return lines


def _split_columns(line: str) -> list[str]:
    if '|' in line:
        parts = [part.strip() for part in line.split('|')]
    else:
        parts = [part.strip() for part in re.split(r'\s{2,}', line) if part.strip()]
    return [part for part in parts if part]


def _looks_like_header(parts: list[str]) -> bool:
    lowered = {_normalize_header(part).strip(':') for part in parts}
    return bool(lowered & _DATE_COLS) and bool((lowered & _DESC_COLS) or (lowered & _AMT_COLS) or (lowered & _DEBIT_COLS) or (lowered & _CREDIT_COLS))


def _map_header(parts: list[str]) -> dict:
    mapping = {'date': None, 'description': None, 'debit': None, 'credit': None, 'amount': None}
    for index, part in enumerate(parts):
        lowered = _normalize_header(part).strip(':')
        if lowered in _DATE_COLS:
            mapping['date'] = index
        elif lowered in _DESC_COLS:
            mapping['description'] = index
        elif lowered in _DEBIT_COLS:
            mapping['debit'] = index
        elif lowered in _CREDIT_COLS:
            mapping['credit'] = index
        elif lowered in _AMT_COLS:
            mapping['amount'] = index
    return mapping


def _parse_table_lines(lines: list[str], normalized_source: str, base_confidence: float) -> tuple[list[dict], int, list[str]]:
    transactions = []
    skipped = 0
    warnings = []
    header_map = None

    for index, line in enumerate(lines, start=1):
        parts = _split_columns(line)
        if len(parts) < 2:
            continue
        if _looks_like_header(parts):
            header_map = _map_header(parts)
            continue
        if not header_map or header_map['date'] is None:
            continue
        if header_map['date'] >= len(parts):
            skipped += 1
            continue
        date_iso = _parse_date(parts[header_map['date']])
        if not date_iso:
            skipped += 1
            continue
        description = parts[header_map['description']] if header_map['description'] is not None and header_map['description'] < len(parts) else 'Transaction'
        amount = None
        needs_review = False
        row_warnings = []
        if header_map['debit'] is not None or header_map['credit'] is not None:
            debit = _parse_amount(parts[header_map['debit']]) if header_map['debit'] is not None and header_map['debit'] < len(parts) else None
            credit = _parse_amount(parts[header_map['credit']]) if header_map['credit'] is not None and header_map['credit'] < len(parts) else None
            if debit not in (None, 0):
                amount = -abs(debit)
            elif credit not in (None, 0):
                amount = abs(credit)
        elif header_map['amount'] is not None and header_map['amount'] < len(parts):
            amount = _parse_amount(parts[header_map['amount']])
            if amount is not None:
                amount, needs_review = _infer_signed_amount([parts[header_map['amount']]], description)
                if needs_review:
                    row_warnings.append('Amount sign was inferred from table text.')
        if amount is None:
            skipped += 1
            continue
        if needs_review:
            warnings.append(f'Table row near line {index} needs review because the amount sign was inferred.')
        transactions.append(_normalize_row(date_iso, description, amount, normalized_source, base_confidence - (0.1 if needs_review else 0.0), needs_review, row_warnings))

    return transactions, skipped, warnings


def _infer_signed_amount(amount_tokens: list[str], description: str) -> tuple[Optional[float], bool]:
    values = [value for value in (_parse_amount(token) for token in amount_tokens) if value is not None]
    if not values:
        return None, False

    lowered = description.lower()
    if len(values) == 1:
        amount = values[0]
        if amount > 0:
            if any(keyword in lowered for keyword in _DEBIT_KEYWORDS):
                return -abs(amount), False
            if any(keyword in lowered for keyword in _CREDIT_KEYWORDS):
                return abs(amount), False
        return amount, True

    first, second = values[0], values[1]
    if abs(first) > 0 and abs(second) == 0:
        return -abs(first), False
    if abs(second) > 0 and abs(first) == 0:
        return abs(second), False
    if any(keyword in lowered for keyword in _DEBIT_KEYWORDS):
        return -abs(first), False
    if any(keyword in lowered for keyword in _CREDIT_KEYWORDS):
        return abs(first), False
    return first, True


def _parse_regex_lines(lines: list[str], normalized_source: str, base_confidence: float) -> tuple[list[dict], int, list[str]]:
    transactions = []
    skipped = 0
    warnings = []
    for index, line in enumerate(lines, start=1):
        if len(line) < 12:
            continue
        date_match = _DATE_ANYWHERE_PATTERN.search(line)
        if not date_match:
            continue
        date_iso = _parse_date(date_match.group(1))
        if not date_iso:
            skipped += 1
            continue
        rest = (line[:date_match.start()] + ' ' + line[date_match.end():]).strip(' |-')
        amount_matches = list(_AMOUNT_PATTERN.finditer(rest))
        if not amount_matches:
            continue
        description = rest[:amount_matches[0].start()].strip(' |-:') or rest
        amount_tokens = [match.group(0) for match in amount_matches[:3]]
        amount, needs_review = _infer_signed_amount(amount_tokens, description)
        if amount is None or not description:
            skipped += 1
            continue
        row_warnings = ['Amount sign inferred from OCR text.'] if needs_review else []
        if needs_review:
            warnings.append(f'OCR row near line {index} needs review because the amount sign was inferred.')
        confidence = base_confidence - (0.18 if needs_review else 0.04)
        transactions.append(_normalize_row(date_iso, description, amount, normalized_source, confidence, needs_review, row_warnings))
    return transactions, skipped, warnings


def _parse_upi_style(text: str, base_confidence: float) -> tuple[list[dict], int, list[str]]:
    transactions = []
    warnings = []
    paid_pattern = re.compile(r'(?:paid|sent|transferred)\s+₹?\s*([\d,]+\.?\d{0,2})\s+(?:to|for)\s+(.{3,70})', re.IGNORECASE)
    recv_pattern = re.compile(r'(?:received|got|credited)\s+₹?\s*([\d,]+\.?\d{0,2})\s+(?:from|by)\s+(.{3,70})', re.IGNORECASE)
    date_pattern = re.compile(r'(\d{1,2}[\/\-]\d{1,2}[\/\-]\d{2,4}|\d{1,2}\s+\w{3}\w*\s+\d{4})')

    dates_found = date_pattern.findall(text)
    fallback_date = datetime.today().strftime('%Y-%m-%d')
    date_value = _parse_date(dates_found[0]) if dates_found else fallback_date

    for match in paid_pattern.finditer(text):
        amount = _parse_amount(match.group(1))
        if amount is not None:
            warnings.append('UPI screenshot rows often need review because app screenshots rarely include explicit debit/credit columns.')
            transactions.append(_normalize_row(date_value, f'Payment - {match.group(2).strip()}', -abs(amount), 'UPI_SCREENSHOT', base_confidence - 0.1, True, ['UPI transaction extracted from screenshot text.']))

    for match in recv_pattern.finditer(text):
        amount = _parse_amount(match.group(1))
        if amount is not None:
            warnings.append('UPI screenshot rows often need review because app screenshots rarely include explicit debit/credit columns.')
            transactions.append(_normalize_row(date_value, f'Received - {match.group(2).strip()}', abs(amount), 'UPI_SCREENSHOT', base_confidence - 0.1, True, ['UPI transaction extracted from screenshot text.']))

    return transactions, 0, _dedupe_list(warnings)


def _parse_text_statement(text: str, fallback_source: str, parse_mode: str, base_confidence: float) -> dict:
    lines = _clean_lines(text)
    table_rows, table_skipped, table_warnings = _parse_table_lines(lines, fallback_source, base_confidence)
    regex_rows, regex_skipped, regex_warnings = _parse_regex_lines(lines, fallback_source, base_confidence)
    upi_rows, upi_skipped, upi_warnings = _parse_upi_style(text, base_confidence)

    merged_rows = _dedupe_transactions([*table_rows, *regex_rows, *upi_rows])
    warnings = _dedupe_list([*table_warnings, *regex_warnings, *upi_warnings])

    if not merged_rows:
        return _build_result([], len(lines), fallback_source, parse_mode, ['No recognizable transactions were found in the document text.'], raw_text=text)

    if len(upi_rows) >= max(1, len(merged_rows) // 2):
        fallback_source = 'UPI_SCREENSHOT'

    skipped = max(table_skipped, regex_skipped, upi_skipped)
    return _build_result(merged_rows, skipped, fallback_source, parse_mode, warnings, raw_text=text)


def _parse_pdf_bytes(data: bytes) -> dict:
    extraction = extract_text_from_pdf_bytes(data)
    text = extraction.get('text') or ''
    warnings = extraction.get('warnings') or []
    if not text.strip():
        return _build_result([], 0, 'PDF', 'pdf_text', warnings, raw_text=text, error='No readable text could be extracted from the PDF statement.')

    parse_mode = 'pdf_ocr' if extraction.get('usedOcrFallback') else 'pdf_text'
    base_confidence = 0.72 if extraction.get('usedOcrFallback') else 0.85
    result = _parse_text_statement(text, 'PDF', parse_mode, base_confidence)
    result['warnings'] = _dedupe_list(result.get('warnings', []) + warnings)
    if extraction.get('usedOcrFallback'):
        ocr_conf = extraction.get('ocrConfidence', result['documentConfidence']) or result['documentConfidence']
        result['documentConfidence'] = round(max(0.4, min(result['documentConfidence'], ocr_conf)), 3)
    warning = best_warning(result['warnings'])
    if warning:
        result['warning'] = warning
    return result


def _parse_image_bytes(data: bytes, detected_type: str) -> dict:
    extraction = extract_text_from_image_bytes(data)
    text = extraction.get('text') or ''
    warnings = extraction.get('warnings') or []
    if not text.strip():
        return _build_result([], 0, 'IMAGE', 'image_ocr', warnings, raw_text=text, error='No readable text could be extracted from the image statement.')

    result = _parse_text_statement(text, 'IMAGE', 'image_ocr', 0.68)
    result['warnings'] = _dedupe_list(result.get('warnings', []) + warnings)
    result['documentConfidence'] = round(min(result['documentConfidence'], extraction.get('ocrConfidence', result['documentConfidence']) or result['documentConfidence']), 3)
    if detected_type in {'heic', 'heif'}:
        result['warnings'] = _dedupe_list(result['warnings'] + ['HEIC/HEIF support depends on the local decoder; review extracted rows carefully.'])
    warning = best_warning(result['warnings'])
    if warning:
        result['warning'] = warning
    return result


def _validate_llm_result(candidate: dict | None) -> dict | None:
    if not candidate or not isinstance(candidate, dict):
        return None
    rows = candidate.get('transactions')
    if not isinstance(rows, list) or not rows:
        return None
    normalized = []
    for row in rows:
        if not isinstance(row, dict):
            continue
        raw_date = str(row.get('date', ''))
        date_iso = _parse_date(raw_date) or (raw_date if re.fullmatch(r'\d{4}-\d{2}-\d{2}', raw_date) else None)
        amount = _parse_amount(str(row.get('amount', '')))
        description = str(row.get('description', '')).strip()
        if not date_iso or amount is None or not description:
            continue
        normalized.append(_normalize_row(date_iso, description, amount, 'LLM_FALLBACK', 0.58, True, ['AI fallback generated this row.']))
    if not normalized:
        return None
    warnings = candidate.get('warnings') if isinstance(candidate.get('warnings'), list) else []
    return _build_result(
        normalized,
        int(candidate.get('skipped', 0) or 0),
        'LLM_FALLBACK',
        'llm_fallback',
        [*warnings, 'AI fallback was used for uncertain document rows. Please review before import.'],
    )


def _should_try_llm(result: dict, raw_text: str) -> bool:
    total_found = result.get('total_found', 0)
    if total_found == 0:
        return True

    document_confidence = float(result.get('documentConfidence', 0.0) or 0.0)
    if document_confidence < 0.6:
        return True

    transactions = result.get('transactions', []) or []
    review_count = sum(1 for txn in transactions if txn.get('needsReview'))
    review_ratio = review_count / total_found if total_found else 1.0
    warning_text = ' '.join(result.get('warnings', [])).lower()
    parse_mode = result.get('parseMode')
    raw_length = len((raw_text or '').strip())

    if review_ratio >= 0.6:
        return True
    if parse_mode in {'pdf_ocr', 'image_ocr'} and total_found < 3 and raw_length > 120:
        return True
    if total_found <= 2 and raw_length > 220:
        return True
    if ('ocr' in warning_text or 'noisy' in warning_text) and total_found < 4:
        return True
    return False


def parse_statement(file_bytes: bytes, filename: str, mime_type: str = '', llm_fallback: Optional[Callable[[str, str, str], dict | None]] = None) -> dict:
    safe_name = re.sub(r'[^a-zA-Z0-9._\-]', '_', filename or 'statement')
    detected_type = detect_file_type(file_bytes, safe_name, mime_type)

    if detected_type == 'csv':
        result = _parse_csv_bytes(file_bytes)
    elif detected_type == 'pdf':
        result = _parse_pdf_bytes(file_bytes)
    elif detected_type in IMAGE_EXTENSIONS or detected_type == 'image':
        result = _parse_image_bytes(file_bytes, detected_type)
    else:
        supported = 'CSV, PDF, PNG, JPG, WEBP, BMP, TIFF'
        if heif_enabled():
            supported += ', HEIC'
        result = _build_result([], 0, 'IMPORT', 'unknown', [], error=f'Unsupported file type for statement parsing. Use {supported}.')

    raw_text = result.pop('rawText', '')
    if llm_fallback and _should_try_llm(result, raw_text):
        try:
            llm_result = _validate_llm_result(llm_fallback(raw_text, safe_name, detected_type))
        except Exception as exc:
            logger.warning('LLM statement fallback failed for %s: %s', safe_name, exc)
            llm_result = None
        if llm_result and (
            llm_result['total_found'] > result['total_found']
            or llm_result['documentConfidence'] >= result.get('documentConfidence', 0.0)
        ):
            llm_result['warnings'] = _dedupe_list((result.get('warnings') or []) + (llm_result.get('warnings') or []))
            warning = best_warning(llm_result['warnings'])
            if warning:
                llm_result['warning'] = warning
            return llm_result

    warning = best_warning(result.get('warnings') or [])
    if warning:
        result['warning'] = warning
    return result
