# PATH: finance-ai/app.py
# AI Service — uses Google Gemini 1.5 for chat and health score recommendations
# ──────────────────────────────────────────────────────────────────────────────
# IMPORTANT: Paste your Google API key below or set GOOGLE_API_KEY environment variable
# Get your key from: https://aistudio.google.com/app/apikey
# ──────────────────────────────────────────────────────────────────────────────

import os
import json
import logging
from datetime import datetime, timedelta
from flask import Flask, request, jsonify
from flask_cors import CORS
from dotenv import load_dotenv

load_dotenv()
logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s %(message)s')
log = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app, origins=os.getenv('ALLOWED_ORIGINS', '*').split(','))

# ──────────────────────────────────────────────────────────────────────────────
# GOOGLE GEMINI API KEY — Paste your key here or set as environment variable
# ──────────────────────────────────────────────────────────────────────────────
GOOGLE_API_KEY = os.getenv("GOOGLE_API_KEY")

# Initialize Gemini
gemini_model = None
try:
    import google.generativeai as genai
    genai.configure(api_key=GOOGLE_API_KEY)
    gemini_model = genai.GenerativeModel('gemini-1.5-flash')
    log.info("✅ Google Gemini 1.5 Flash initialized successfully")
except Exception as e:
    log.warning(f"⚠️ Gemini initialization failed: {e}. Chat will use fallback responses.")

# ── Health check ──────────────────────────────────────────────────────────────
@app.route('/health')
def health():
    return jsonify({
        "status": "ok",
        "service": "finance-ai",
        "version": "3.0.0",
        "ai_provider": "google-gemini-1.5-flash",
        "ai_ready": gemini_model is not None,
        "port": int(os.getenv('PORT', 5001))
    })

# ── AI Chat (Gemini 1.5) ─────────────────────────────────────────────────────
@app.route('/chat', methods=['POST'])
def chat():
    data     = request.get_json() or {}
    question = data.get('question', '')
    context  = data.get('context', '')
    history  = data.get('history', [])

    if not question:
        return jsonify({"error": "question is required"}), 400

    system_prompt = """You are FinanceAI, an expert AI financial advisor for Indian small and
medium businesses. You help with bookkeeping, GST (Goods & Services Tax), cash flow analysis,
P&L (Profit & Loss) analysis, budgeting, TDS, ITR filing guidance, and financial planning.

Your expertise includes:
- Indian GST: GSTR-1, GSTR-3B, Input Tax Credit (ITC), GST rates (0%, 5%, 12%, 18%, 28%)
- Income Tax: New Tax Regime slabs, advance tax schedules, Section 44AD/44ADA
- TDS (Tax Deducted at Source): rates, filing, Form 26AS
- Bank reconciliation and cash flow management
- Business expense categorization for Indian businesses
- Working capital optimization for SMBs
- Invoice management and compliance
- International transactions and forex considerations
- Industry-specific advice (retail, services, manufacturing, IT, freelancing)

Global finance knowledge:
- VAT/Sales Tax for international businesses
- USD, EUR, GBP currency considerations
- International payment compliance
- Cross-border tax implications

Keep responses concise, actionable, and specific.
Format numbers in Indian system (lakhs, crores) when relevant.
Always add a disclaimer for tax/legal advice: "This is general financial guidance. Please consult a CA/tax professional for specific compliance."
If no financial context is provided, ask clarifying questions about their business."""

    # Build conversation for Gemini
    full_prompt = system_prompt + "\n\n"
    if context:
        full_prompt += f"User's financial context:\n{context}\n\n"

    # Add conversation history
    for h in history[-10:]:
        role = h.get('role', 'user')
        content = h.get('content', '')
        if role == 'user':
            full_prompt += f"User: {content}\n"
        else:
            full_prompt += f"FinanceAI: {content}\n"

    full_prompt += f"User: {question}\nFinanceAI:"

    try:
        if gemini_model is None:
            raise Exception("Gemini model not initialized")

        response = gemini_model.generate_content(
            full_prompt,
            generation_config={
                "max_output_tokens": 800,
                "temperature": 0.4,
            }
        )
        answer = response.text
        return jsonify({"answer": answer, "tokens": len(answer.split())})
    except Exception as e:
        log.error(f"Chat error: {e}")
        # Fallback response when API is unavailable
        return jsonify({
            "answer": "I'm currently unable to connect to the AI service. Please check that your GOOGLE_API_KEY is configured correctly in the finance-ai/.env file or environment variables. You can get a free API key from https://aistudio.google.com/app/apikey",
            "tokens": 0,
            "fallback": True
        }), 200

# ── Cash Flow Forecast (Prophet) ──────────────────────────────────────────────
@app.route('/forecast', methods=['POST'])
def forecast():
    try:
        from prophet import Prophet
        import pandas as pd

        data      = request.get_json() or {}
        cash_flow = data.get('cash_flow', [])
        periods   = int(data.get('periods', 30))

        if len(cash_flow) < 2:
            return jsonify({"error": "Need at least 2 data points for forecast"}), 400

        df = pd.DataFrame(cash_flow).rename(columns={'date': 'ds', 'amount': 'y'})
        df['ds'] = pd.to_datetime(df['ds'])
        df['y']  = pd.to_numeric(df['y'], errors='coerce').fillna(0)

        m = Prophet(
            daily_seasonality=False,
            weekly_seasonality=True,
            yearly_seasonality=True
        )
        m.fit(df)
        future      = m.make_future_dataframe(periods=periods)
        forecast_df = m.predict(future)

        result = forecast_df[['ds', 'yhat', 'yhat_lower', 'yhat_upper']].tail(periods)
        forecast_list = [
            {
                "date":      str(r['ds'].date()),
                "predicted": round(float(r['yhat']),       2),
                "lower":     round(float(r['yhat_lower']), 2),
                "upper":     round(float(r['yhat_upper']), 2)
            }
            for _, r in result.iterrows()
        ]

        negative_day = next((f for f in forecast_list if f['predicted'] < 0), None)
        return jsonify({
            "forecast": forecast_list,
            "negative_forecast_date":  negative_day['date']              if negative_day else None,
            "days_until_negative":     forecast_list.index(negative_day) + 1 if negative_day else None
        })

    except ImportError:
        return jsonify({"error": "Prophet not installed. Run: pip install prophet"}), 503
    except Exception as e:
        log.error(f"Forecast error: {e}")
        return jsonify({"error": str(e)}), 500

# ── Anomaly Detection (Isolation Forest) ─────────────────────────────────────
@app.route('/anomalies', methods=['POST'])
def detect_anomalies():
    try:
        from sklearn.ensemble import IsolationForest
        import numpy as np

        data         = request.get_json() or {}
        transactions = data.get('transactions', [])

        if len(transactions) < 5:
            return jsonify({"anomalies": [], "message": "Need at least 5 transactions"})

        amounts = np.array(
            [abs(float(t.get('amount', 0))) for t in transactions]
        ).reshape(-1, 1)

        model = IsolationForest(contamination=0.05, random_state=42)
        preds = model.fit_predict(amounts)

        anomalies = [
            {
                **transactions[i],
                "anomaly_score": float(model.score_samples(amounts[i:i+1])[0])
            }
            for i, p in enumerate(preds) if p == -1
        ]
        return jsonify({"anomalies": anomalies, "total_checked": len(transactions)})

    except Exception as e:
        log.error(f"Anomaly detection error: {e}")
        return jsonify({"error": str(e)}), 500

# ── Category Classification ───────────────────────────────────────────────────
@app.route('/categorize', methods=['POST'])
def categorize():
    from category_classifier import predict_with_confidence

    data        = request.get_json() or {}
    description = data.get('description', '')

    if not description:
        return jsonify({"error": "description is required"}), 400

    result = predict_with_confidence(description)
    return jsonify(result)

# ── Train / retrain classifier ────────────────────────────────────────────────
@app.route('/train-classifier', methods=['POST'])
def train_classifier():
    from category_classifier import train_model
    result = train_model()
    status = 200 if result.get('status') == 'ok' else 500
    return jsonify(result), status

# ── OCR Invoice Parser ────────────────────────────────────────────────────────
@app.route('/ocr', methods=['POST'])
def ocr_invoice():
    from ocr_invoice import parse_invoice_bytes

    if 'file' in request.files:
        raw = request.files['file'].read()
    else:
        data    = request.get_json() or {}
        b64     = data.get('image')
        if not b64:
            return jsonify({"error": "No image provided"}), 400
        import base64
        raw = base64.b64decode(b64)

    result = parse_invoice_bytes(raw)
    return jsonify(result)

# ── Parse Bank Statement (CSV / PDF / image) ──────────────────────────────────
@app.route('/parse-statement', methods=['POST'])
def parse_statement():
    if 'file' not in request.files:
        return jsonify({"error": "No file uploaded"}), 400

    file     = request.files['file']
    filename = (file.filename or '').lower()
    raw      = file.read()

    try:
        if filename.endswith('.csv'):
            return _parse_csv(raw)
        elif filename.endswith('.pdf'):
            return _parse_pdf(raw)
        else:
            return _parse_image_statement(raw)
    except Exception as e:
        log.error(f"parse-statement error: {e}")
        return jsonify({"error": str(e)}), 500


def _parse_csv(raw: bytes):
    import csv, io
    text  = raw.decode('utf-8', errors='replace')
    lines = list(csv.DictReader(io.StringIO(text)))
    txns  = []
    for row in lines:
        date   = row.get('Date') or row.get('date') or row.get('VALUE DATE', '')
        desc   = row.get('Description') or row.get('Narration') or row.get('description', '')
        debit  = row.get('Debit')  or row.get('debit')  or row.get('DR', '0')
        credit = row.get('Credit') or row.get('credit') or row.get('CR', '0')
        try:
            d = -abs(float(str(debit).replace(',',  '').strip() or '0'))
            c =  abs(float(str(credit).replace(',', '').strip() or '0'))
            amount = c if c != 0 else d
        except ValueError:
            continue
        if date and (amount != 0):
            txns.append({"date": date.strip(), "description": desc.strip(),
                         "amount": amount, "source": "CSV"})
    return jsonify({"transactions": txns, "total_found": len(txns),
                    "skipped": 0, "source": "CSV"})


def _parse_pdf(raw: bytes):
    try:
        import pdfplumber, io, re
        txns = []
        with pdfplumber.open(io.BytesIO(raw)) as pdf:
            for page in pdf.pages:
                text = page.extract_text() or ''
                for line in text.split('\n'):
                    m = re.search(
                        r'(\d{1,2}[-/]\d{1,2}[-/]\d{2,4})'
                        r'.{5,80}'
                        r'([\-\+]?\s*[\d,]+\.\d{2})',
                        line
                    )
                    if m:
                        try:
                            amt = float(m.group(2).replace(' ', '').replace(',', ''))
                        except ValueError:
                            continue
                        desc = line[m.end(1):m.start(2)].strip(' -|')
                        txns.append({"date": m.group(1), "description": desc,
                                     "amount": amt, "source": "PDF"})
        return jsonify({"transactions": txns, "total_found": len(txns),
                        "skipped": 0, "source": "PDF"})
    except ImportError:
        return jsonify({"error": "pdfplumber not installed. Run: pip install pdfplumber"}), 503


def _parse_image_statement(raw: bytes):
    from ocr_invoice import parse_invoice_bytes
    result = parse_invoice_bytes(raw)
    txns = []
    if result.get('total'):
        txns.append({
            "date":        result.get('date', datetime.today().strftime('%Y-%m-%d')),
            "description": result.get('vendor', 'Invoice'),
            "amount":      -abs(float(result['total'])),
            "source":      "OCR"
        })
    return jsonify({"transactions": txns, "total_found": len(txns),
                    "skipped": 0, "source": "OCR",
                    "warning": "Image statements have limited accuracy. Please review carefully."})

# ── Chart data endpoint ───────────────────────────────────────────────────────
@app.route('/chart-data', methods=['POST'])
def chart_data():
    data         = request.get_json() or {}
    transactions = data.get('transactions', [])
    months       = int(data.get('months', 6))

    import pandas as pd
    from collections import defaultdict

    if not transactions:
        return jsonify({"monthly": [], "categoryBreakdown": [], "dailyBalance": []})

    df = pd.DataFrame(transactions)
    df['date']   = pd.to_datetime(df['date'], errors='coerce')
    df['amount'] = pd.to_numeric(df['amount'], errors='coerce').fillna(0)
    df = df.dropna(subset=['date'])

    cutoff = pd.Timestamp.now() - pd.DateOffset(months=months)
    df = df[df['date'] >= cutoff].copy()

    # Monthly income / expense
    df['month_str'] = df['date'].dt.strftime('%b %Y')
    monthly_groups  = df.groupby('month_str')
    monthly = []
    for m, grp in sorted(monthly_groups, key=lambda x: pd.to_datetime(x[0], format='%b %Y')):
        inc = float(grp[grp['amount'] > 0]['amount'].sum())
        exp = float(abs(grp[grp['amount'] < 0]['amount'].sum()))
        monthly.append({"month": m, "income": round(inc, 2),
                         "expense": round(exp, 2), "net": round(inc - exp, 2)})

    # Category breakdown (expenses only)
    cat_totals: dict = defaultdict(float)
    for _, row in df[df['amount'] < 0].iterrows():
        cat = row.get('categoryName') or 'Other'
        cat_totals[cat] += abs(float(row['amount']))
    total_exp = sum(cat_totals.values()) or 1
    cat_breakdown = [
        {"name": k, "value": round(v, 2), "percent": round(v / total_exp * 100, 1)}
        for k, v in sorted(cat_totals.items(), key=lambda x: -x[1])
    ]

    # Daily running balance (last 60 days)
    sixty_ago = pd.Timestamp.now() - pd.DateOffset(days=60)
    daily_df  = df[df['date'] >= sixty_ago].copy()
    daily_df['day'] = daily_df['date'].dt.date
    daily_sum = daily_df.groupby('day')['amount'].sum().sort_index()
    cum_bal   = daily_sum.cumsum()
    daily_bal = [{"date": str(d), "balance": round(float(v), 2)}
                 for d, v in cum_bal.items()]

    return jsonify({
        "monthly":           monthly,
        "categoryBreakdown": cat_breakdown,
        "dailyBalance":      daily_bal
    })

# ── AI Health Score Recommendations (Gemini) ─────────────────────────────────
@app.route('/health-score-recommendations', methods=['POST'])
def health_score_recommendations():
    data      = request.get_json() or {}
    score     = data.get('score', 0)
    breakdown = data.get('breakdown', {})

    prompt = f"""A small Indian business has a financial health score of {score}/100.
Score breakdown: {json.dumps(breakdown)}
Give exactly 3 short, specific, actionable recommendations (each max 2 sentences).
Format as bullet points starting with •. Be direct. Use Indian business context (GST, ITR, TDS, etc.).
Include specific numbers or percentages where possible."""

    try:
        if gemini_model is None:
            raise Exception("Gemini model not initialized")

        response = gemini_model.generate_content(
            prompt,
            generation_config={
                "max_output_tokens": 300,
                "temperature": 0.3,
            }
        )
        return jsonify({"recommendations": response.text})
    except Exception as e:
        log.error(f"Health score AI error: {e}")
        return jsonify({
            "recommendations":
                "• Monitor your expense-to-income ratio monthly — aim for under 70%.\n"
                "• Ensure GST filings (GSTR-1 and GSTR-3B) are submitted on time to avoid penalties.\n"
                "• Maintain a cash reserve of at least 3 months of operating expenses."
        }), 200

# ── Entry point ───────────────────────────────────────────────────────────────
if __name__ == '__main__':
    port  = int(os.getenv('PORT', 5001))
    debug = os.getenv('FLASK_DEBUG', 'false').lower() == 'true'
    log.info(f"🚀 Finance AI service starting on port {port} (debug={debug})")
    log.info(f"   AI Provider: Google Gemini 1.5 Flash")
    log.info(f"   Gemini Ready: {gemini_model is not None}")
    app.run(host='0.0.0.0', port=port, debug=debug)