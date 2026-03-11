import json
import logging
import os
import re
from pathlib import Path

from dotenv import load_dotenv
from flask import Flask, jsonify, request

load_dotenv()
logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s %(message)s')
log = logging.getLogger(__name__)
app = Flask(__name__)

def _read_secret(name: str):
    value = os.getenv(name)
    if value:
        return value.strip()
    file_path = os.getenv(f'{name}_FILE')
    if file_path and Path(file_path).is_file():
        return Path(file_path).read_text(encoding='utf-8').strip()
    return None

INTERNAL_API_KEY = _read_secret('INTERNAL_API_KEY')
if not INTERNAL_API_KEY:
    log.error('CRITICAL: INTERNAL_API_KEY environment variable is missing. Halting startup.')
    raise SystemExit(1)

GEMINI_API_KEY = _read_secret('GEMINI_API_KEY') or _read_secret('GOOGLE_API_KEY')
GEMINI_MODEL_NAME = os.getenv('GEMINI_MODEL', 'gemini-2.5-pro')
GEMINI_STATEMENT_MODEL_NAME = os.getenv('GEMINI_STATEMENT_MODEL', GEMINI_MODEL_NAME)
STATEMENT_LLM_FALLBACK = os.getenv('STATEMENT_LLM_FALLBACK', 'true').lower() == 'true'
OPT_IN_SAMPLE_DIR = Path(__file__).resolve().parent / 'data' / 'opt_in_samples'
OPT_IN_SAMPLE_DIR.mkdir(parents=True, exist_ok=True)

gemini_model = None
gemini_statement_model = None
try:
    import google.generativeai as genai

    if GEMINI_API_KEY:
        genai.configure(api_key=GEMINI_API_KEY)
        gemini_model = genai.GenerativeModel(GEMINI_MODEL_NAME)
        gemini_statement_model = genai.GenerativeModel(GEMINI_STATEMENT_MODEL_NAME)
        log.info('Gemini initialized successfully (general=%s, statement=%s)', GEMINI_MODEL_NAME, GEMINI_STATEMENT_MODEL_NAME)
    else:
        log.warning('GEMINI_API_KEY is not configured. Chat and structured fallbacks will use local fallbacks.')
except Exception as exc:
    log.warning('Gemini initialization failed: %s. Chat and structured fallbacks will use local fallbacks.', exc)


@app.before_request
def require_api_key():
    if request.endpoint in {'health'} or request.method == 'OPTIONS':
        return None
    key = request.headers.get('X-API-Key')
    if not key or key != INTERNAL_API_KEY:
        return jsonify({'error': 'Unauthorized. Missing or invalid X-API-Key header.'}), 401
    return None


def _provider_status() -> dict:
    return {
        'gemini': {
            'configured': bool(GEMINI_API_KEY),
            'ready': gemini_model is not None,
            'statementFallbackEnabled': STATEMENT_LLM_FALLBACK,
            'model': GEMINI_MODEL_NAME,
            'statementModel': GEMINI_STATEMENT_MODEL_NAME,
        }
    }


def _extract_json_object(text: str):
    if not text:
        return None
    cleaned = text.strip()
    cleaned = re.sub(r'^```json\s*', '', cleaned)
    cleaned = re.sub(r'^```\s*', '', cleaned)
    cleaned = re.sub(r'```$', '', cleaned).strip()
    try:
        return json.loads(cleaned)
    except json.JSONDecodeError:
        match = re.search(r'\{.*\}', cleaned, re.DOTALL)
        if not match:
            return None
        try:
            return json.loads(match.group(0))
        except json.JSONDecodeError:
            return None


def _gemini_statement_fallback(document_text: str, filename: str, detected_type: str):
    if gemini_statement_model is None or not STATEMENT_LLM_FALLBACK:
        return None
    compact = (document_text or '').strip()
    if len(compact) < 40:
        return None

    prompt = f"""You are extracting bank statement transactions from OCR/text.
Return ONLY valid JSON with this schema:
{{
  "transactions": [
    {{"date": "YYYY-MM-DD", "description": "text", "amount": -123.45}}
  ],
  "warnings": ["optional warning"]
}}
Rules:
- Expenses must be negative and income positive.
- Only include rows that look like real transactions.
- Skip balances, headers, and uncertain garbage.
- Preserve merchant/reference text, but do not invent missing values.
File: {filename}
Detected type: {detected_type}
Document text:
{compact[:12000]}
"""

    try:
        response = gemini_statement_model.generate_content(
            prompt,
            generation_config={
                'temperature': 0.1,
                'max_output_tokens': 1400,
                'response_mime_type': 'application/json',
            },
        )
    except Exception as exc:
        log.warning('Gemini statement fallback failed for %s: %s', filename, exc)
        return None

    payload = _extract_json_object(getattr(response, 'text', '') or '')
    return payload if isinstance(payload, dict) else None


def _normalize_bullets(text: str, fallback_lines: list[str]) -> str:
    lines = [line.strip() for line in (text or '').splitlines() if line.strip()]
    bullet_lines = [line if line.startswith("•") else f"• {line.lstrip('- ').strip()}" for line in lines[:3] if line]
    if len(bullet_lines) >= 3:
        return '\n'.join(bullet_lines[:3])
    return '\n'.join(fallback_lines)


@app.route('/health')
def health():
    return jsonify({
        'status': 'ok',
        'service': 'finance-ai',
        'version': '4.0.0',
        'port': int(os.getenv('PORT', 5001)),
        'providers': _provider_status(),
    })


@app.route('/providers/status')
def providers_status():
    return jsonify(_provider_status())


@app.route('/chat', methods=['POST'])
def chat():
    data = request.get_json() or {}
    question = data.get('question', '').strip()
    context = data.get('context', '')
    history = data.get('history', [])

    if not question:
        return jsonify({'error': 'question is required'}), 400

    system_prompt = """You are FinanceAI, an expert AI financial advisor for Indian small and medium businesses.
You help with bookkeeping, GST, cash flow analysis, P&L analysis, budgeting, TDS, ITR filing guidance, and financial planning.
Give concise, actionable answers.
Format numbers in the Indian number system when relevant.
Always include this disclaimer for tax or legal advice: This is general financial guidance. Please consult a CA/tax professional for specific compliance."""

    full_prompt = system_prompt + '\n\n'
    if context:
        full_prompt += f"User financial context:\n{context}\n\n"
    for item in history[-10:]:
        role = item.get('role', 'user')
        content = item.get('content', '')
        full_prompt += f"{'User' if role == 'user' else 'FinanceAI'}: {content}\n"
    full_prompt += f'User: {question}\nFinanceAI:'

    try:
        if gemini_model is None:
            raise RuntimeError('Gemini model not initialized')
        response = gemini_model.generate_content(
            full_prompt,
            generation_config={'max_output_tokens': 800, 'temperature': 0.35},
        )
        answer = (getattr(response, 'text', '') or '').strip()
        if not answer:
            raise RuntimeError('Empty answer returned by Gemini')
        return jsonify({'answer': answer, 'tokens': len(answer.split()), 'provider': 'gemini'})
    except Exception as exc:
        log.error('Chat error: %s', exc)
        return jsonify({
            'answer': 'The AI assistant is temporarily running in fallback mode. Please try again shortly, or configure GEMINI_API_KEY to re-enable Gemini-powered answers.',
            'tokens': 0,
            'fallback': True,
            'provider': 'local-fallback',
        }), 200


@app.route('/forecast', methods=['POST'])
def forecast():
    try:
        from prophet import Prophet
        import pandas as pd

        data = request.get_json() or {}
        cash_flow = data.get('cash_flow', [])
        periods = int(data.get('periods', 30))

        if len(cash_flow) < 2:
            return jsonify({'error': 'Need at least 2 data points for forecast'}), 400

        df = pd.DataFrame(cash_flow).rename(columns={'date': 'ds', 'amount': 'y'})
        df['ds'] = pd.to_datetime(df['ds'])
        df['y'] = pd.to_numeric(df['y'], errors='coerce').fillna(0)

        model = Prophet(daily_seasonality=False, weekly_seasonality=True, yearly_seasonality=True)
        model.fit(df)
        future = model.make_future_dataframe(periods=periods)
        forecast_df = model.predict(future)
        result = forecast_df[['ds', 'yhat', 'yhat_lower', 'yhat_upper']].tail(periods)
        forecast_list = [
            {
                'date': str(row['ds'].date()),
                'predicted': round(float(row['yhat']), 2),
                'lower': round(float(row['yhat_lower']), 2),
                'upper': round(float(row['yhat_upper']), 2),
            }
            for _, row in result.iterrows()
        ]
        negative_day = next((item for item in forecast_list if item['predicted'] < 0), None)
        return jsonify({
            'forecast': forecast_list,
            'negative_forecast_date': negative_day['date'] if negative_day else None,
            'days_until_negative': forecast_list.index(negative_day) + 1 if negative_day else None,
        })
    except ImportError:
        return jsonify({'error': 'Prophet is not installed.'}), 503
    except Exception as exc:
        log.error('Forecast error: %s', exc)
        return jsonify({'error': str(exc)}), 500


@app.route('/anomalies', methods=['POST'])
def anomalies():
    try:
        from anomaly_detector import detect_anomalies as detect_saved_anomalies

        data = request.get_json() or {}
        transactions = data.get('transactions', [])
        if len(transactions) < 5:
            return jsonify({'anomalies': [], 'message': 'Need at least 5 transactions'})
        anomalies_found = detect_saved_anomalies(transactions)
        return jsonify({'anomalies': anomalies_found, 'total_checked': len(transactions)})
    except Exception as exc:
        log.error('Anomaly detection error: %s', exc)
        return jsonify({'error': str(exc)}), 500


@app.route('/train-anomaly-model', methods=['POST'])
def train_anomaly_model():
    try:
        from anomaly_detector import train_anomaly_model as train_detector

        data = request.get_json() or {}
        transactions = data.get('transactions', [])
        result = train_detector(transactions)
        status = 200 if result.get('status') == 'ok' else 400
        return jsonify(result), status
    except Exception as exc:
        log.error('Anomaly model training error: %s', exc)
        return jsonify({'status': 'error', 'message': str(exc)}), 500


@app.route('/categorize', methods=['POST'])
def categorize():
    from category_classifier import predict_with_confidence

    data = request.get_json() or {}
    description = data.get('description', '')
    if not description:
        return jsonify({'error': 'description is required'}), 400
    return jsonify(predict_with_confidence(description))


@app.route('/train-classifier', methods=['POST'])
def train_classifier():
    from category_classifier import train_model

    data = request.get_json() or {}
    result = train_model(data.get('csvPath'))
    status = 200 if result.get('status') == 'ok' else 500
    return jsonify(result), status


@app.route('/ocr', methods=['POST'])
def ocr_invoice():
    from ocr_invoice import parse_invoice_bytes

    if 'file' in request.files:
        uploaded = request.files['file']
        raw = uploaded.read()
        filename = uploaded.filename or 'invoice'
    else:
        data = request.get_json() or {}
        b64 = data.get('image')
        if not b64:
            return jsonify({'error': 'No image provided'}), 400
        import base64

        raw = base64.b64decode(b64)
        filename = data.get('filename', 'invoice.png')

    return jsonify(parse_invoice_bytes(raw, filename=filename))


@app.route('/parse-statement', methods=['POST'])
def parse_statement():
    from statement_parser import parse_statement as parse_statement_file

    if 'file' not in request.files:
        return jsonify({'error': 'No file uploaded'}), 400

    uploaded = request.files['file']
    raw = uploaded.read()
    filename = uploaded.filename or 'statement'
    mime_type = uploaded.mimetype or ''

    try:
        result = parse_statement_file(
            raw,
            filename,
            mime_type=mime_type,
            llm_fallback=_gemini_statement_fallback,
        )
        return jsonify(result)
    except Exception as exc:
        log.error('parse-statement error: %s', exc)
        return jsonify({'error': str(exc)}), 500


@app.route('/samples/opt-in', methods=['POST'])
def ingest_opt_in_sample():
    data = request.get_json() or {}
    if not data.get('optIn'):
        return jsonify({'error': 'optIn=true is required to store a sample.'}), 400

    kind = re.sub(r'[^a-zA-Z0-9_-]', '_', str(data.get('kind', 'statement')))
    filename = re.sub(r'[^a-zA-Z0-9._-]', '_', str(data.get('filename', 'sample.txt')))
    content = str(data.get('content', '')).strip()
    metadata = data.get('metadata', {}) if isinstance(data.get('metadata'), dict) else {}
    if not content:
        return jsonify({'error': 'content is required'}), 400

    target_dir = OPT_IN_SAMPLE_DIR / kind
    target_dir.mkdir(parents=True, exist_ok=True)
    stem = Path(filename).stem[:60] or 'sample'
    sample_id = f"{stem}-{len(list(target_dir.glob(stem + '*'))) + 1}"
    text_path = target_dir / f'{sample_id}.txt'
    meta_path = target_dir / f'{sample_id}.json'
    text_path.write_text(content, encoding='utf-8')
    meta_path.write_text(json.dumps(metadata, indent=2), encoding='utf-8')
    return jsonify({'status': 'ok', 'sampleId': sample_id})


@app.route('/chart-data', methods=['POST'])
def chart_data():
    data = request.get_json() or {}
    transactions = data.get('transactions', [])
    months = int(data.get('months', 6))

    import pandas as pd
    from collections import defaultdict

    if not transactions:
        return jsonify({'monthly': [], 'categoryBreakdown': [], 'dailyBalance': []})

    df = pd.DataFrame(transactions)
    df['date'] = pd.to_datetime(df['date'], errors='coerce')
    df['amount'] = pd.to_numeric(df['amount'], errors='coerce').fillna(0)
    df = df.dropna(subset=['date'])

    cutoff = pd.Timestamp.now() - pd.DateOffset(months=months)
    df = df[df['date'] >= cutoff].copy()

    df['month_str'] = df['date'].dt.strftime('%b %Y')
    monthly_groups = df.groupby('month_str')
    monthly = []
    for month, group in sorted(monthly_groups, key=lambda item: pd.to_datetime(item[0], format='%b %Y')):
        income = float(group[group['amount'] > 0]['amount'].sum())
        expense = float(abs(group[group['amount'] < 0]['amount'].sum()))
        monthly.append({'month': month, 'income': round(income, 2), 'expense': round(expense, 2), 'net': round(income - expense, 2)})

    category_totals = defaultdict(float)
    for _, row in df[df['amount'] < 0].iterrows():
        category = row.get('categoryName') or 'Other'
        category_totals[category] += abs(float(row['amount']))
    total_expense = sum(category_totals.values()) or 1
    category_breakdown = [
        {'name': key, 'value': round(value, 2), 'percent': round(value / total_expense * 100, 1)}
        for key, value in sorted(category_totals.items(), key=lambda item: -item[1])
    ]

    sixty_days_ago = pd.Timestamp.now() - pd.DateOffset(days=60)
    daily_df = df[df['date'] >= sixty_days_ago].copy()
    daily_df['day'] = daily_df['date'].dt.date
    daily_sum = daily_df.groupby('day')['amount'].sum().sort_index()
    cumulative = daily_sum.cumsum()
    daily_balance = [{'date': str(day), 'balance': round(float(balance), 2)} for day, balance in cumulative.items()]

    return jsonify({'monthly': monthly, 'categoryBreakdown': category_breakdown, 'dailyBalance': daily_balance})


@app.route('/health-score-recommendations', methods=['POST'])
def health_score_recommendations():
    data = request.get_json() or {}
    score = data.get('score', 0)
    breakdown = data.get('breakdown', {})
    fallback_lines = [
        '• Monitor your expense-to-income ratio monthly and aim to keep it under 70%.',
        '• Ensure GST filings such as GSTR-1 and GSTR-3B are submitted on time to avoid penalties.',
        '• Maintain a cash reserve covering at least 3 months of operating expenses.',
    ]

    prompt = f"""A small Indian business has a financial health score of {score}/100.
Score breakdown: {json.dumps(breakdown)}
Give exactly 3 short, specific, actionable recommendations.
Each item must be one bullet starting with •.
Use Indian business context where relevant and avoid filler text."""

    try:
        if gemini_model is None:
            raise RuntimeError('Gemini model not initialized')
        response = gemini_model.generate_content(
            prompt,
            generation_config={'max_output_tokens': 300, 'temperature': 0.25},
        )
        recommendations = _normalize_bullets(getattr(response, 'text', '') or '', fallback_lines)
        return jsonify({'recommendations': recommendations, 'provider': 'gemini'})
    except Exception as exc:
        log.error('Health score AI error: %s', exc)
        return jsonify({'recommendations': '\n'.join(fallback_lines), 'provider': 'local-fallback'}), 200


if __name__ == '__main__':
    port = int(os.getenv('PORT', 5001))
    debug = os.getenv('FLASK_DEBUG', 'false').lower() == 'true'
    log.info('Finance AI service starting on port %s (debug=%s)', port, debug)
    log.info('Gemini ready: %s', gemini_model is not None)
    app.run(host='0.0.0.0', port=port, debug=debug)


