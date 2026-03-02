# PATH: finance-ai/app.py
# UPDATED: Added /chart-data and /health-score endpoints

import os
import json
import logging
from datetime import datetime, timedelta
from flask import Flask, request, jsonify
from flask_cors import CORS
from dotenv import load_dotenv
from openai import OpenAI

load_dotenv()
logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s %(message)s')
log = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)

client = OpenAI(api_key=os.getenv("OPENAI_API_KEY", ""))

# ── Health check ──────────────────────────────────────────────────────────────
@app.route('/health')
def health():
    return jsonify({"status": "ok", "service": "finance-ai", "version": "2.0.0"})

# ── AI Chat ───────────────────────────────────────────────────────────────────
@app.route('/chat', methods=['POST'])
def chat():
    data = request.get_json() or {}
    question  = data.get('question', '')
    context   = data.get('context', '')
    history   = data.get('history', [])

    if not question:
        return jsonify({"error": "question is required"}), 400

    system_prompt = """You are FinanceAI, an expert AI financial advisor for Indian small and medium businesses.
You help with bookkeeping, GST, cash flow, P&L analysis, budgeting, and financial planning.
Keep responses concise, actionable, and specific to the Indian financial context (₹, GST, ITR, etc.).
When asked about user-specific data, use the context provided. If no context, ask clarifying questions.
Format numbers in Indian system (lakhs, crores). Always add a disclaimer for tax/legal advice."""

    messages = [{"role": "system", "content": system_prompt}]
    if context:
        messages.append({"role": "system", "content": f"User's financial context:\n{context}"})
    for h in history[-10:]:  # last 10 turns
        messages.append(h)
    messages.append({"role": "user", "content": question})

    try:
        resp = client.chat.completions.create(
            model="gpt-4o-mini",
            messages=messages,
            max_tokens=800,
            temperature=0.4
        )
        answer = resp.choices[0].message.content
        return jsonify({"answer": answer, "tokens": resp.usage.total_tokens})
    except Exception as e:
        log.error(f"Chat error: {e}")
        return jsonify({"error": "AI service temporarily unavailable"}), 503

# ── Cash Flow Forecast (Prophet) ──────────────────────────────────────────────
@app.route('/forecast', methods=['POST'])
def forecast():
    try:
        from prophet import Prophet
        import pandas as pd
        data = request.get_json() or {}
        cash_flow = data.get('cash_flow', [])
        periods   = int(data.get('periods', 30))

        if len(cash_flow) < 2:
            return jsonify({"error": "Need at least 2 data points for forecast"}), 400

        df = pd.DataFrame(cash_flow).rename(columns={'date': 'ds', 'amount': 'y'})
        df['ds'] = pd.to_datetime(df['ds'])
        df['y'] = pd.to_numeric(df['y'], errors='coerce').fillna(0)

        m = Prophet(daily_seasonality=False, weekly_seasonality=True, yearly_seasonality=True)
        m.fit(df)
        future = m.make_future_dataframe(periods=periods)
        forecast_df = m.predict(future)

        result = forecast_df[['ds', 'yhat', 'yhat_lower', 'yhat_upper']].tail(periods)
        forecast_list = [
            {"date": str(r['ds'].date()), "predicted": round(float(r['yhat']), 2),
             "lower": round(float(r['yhat_lower']), 2), "upper": round(float(r['yhat_upper']), 2)}
            for _, r in result.iterrows()
        ]

        # Detect first negative day
        negative_day = next((f for f in forecast_list if f['predicted'] < 0), None)
        return jsonify({
            "forecast": forecast_list,
            "negative_forecast_date": negative_day['date'] if negative_day else None,
            "days_until_negative": forecast_list.index(negative_day) + 1 if negative_day else None
        })
    except ImportError:
        return jsonify({"error": "Prophet not installed. Run: pip install prophet"}), 503
    except Exception as e:
        log.error(f"Forecast error: {e}")
        return jsonify({"error": str(e)}), 500

# ── Anomaly Detection ─────────────────────────────────────────────────────────
@app.route('/anomalies', methods=['POST'])
def detect_anomalies():
    try:
        from sklearn.ensemble import IsolationForest
        import numpy as np
        data = request.get_json() or {}
        transactions = data.get('transactions', [])

        if len(transactions) < 5:
            return jsonify({"anomalies": [], "message": "Need at least 5 transactions"})

        amounts = np.array([abs(float(t.get('amount', 0))) for t in transactions]).reshape(-1, 1)
        model = IsolationForest(contamination=0.05, random_state=42)
        preds = model.fit_predict(amounts)

        anomalies = [
            {**transactions[i], "anomaly_score": float(model.score_samples(amounts[i:i+1])[0])}
            for i, p in enumerate(preds) if p == -1
        ]
        return jsonify({"anomalies": anomalies, "total_checked": len(transactions)})
    except Exception as e:
        log.error(f"Anomaly detection error: {e}")
        return jsonify({"error": str(e)}), 500

# ── Category Classification ───────────────────────────────────────────────────
@app.route('/categorize', methods=['POST'])
def categorize():
    data = request.get_json() or {}
    description = data.get('description', '').lower()

    # Rule-based + keyword matching (no ML model needed for MVP)
    rules = {
        'Salary': ['salary', 'wage', 'payroll', 'stipend'],
        'Food':   ['swiggy', 'zomato', 'food', 'restaurant', 'cafe', 'chai', 'lunch', 'dinner'],
        'Transport': ['uber', 'ola', 'fuel', 'petrol', 'metro', 'auto', 'taxi', 'rapido'],
        'Utilities': ['electricity', 'water', 'gas', 'internet', 'broadband', 'wifi', 'bsnl', 'jio'],
        'Office': ['stationery', 'office', 'printing', 'supplies', 'pen', 'paper'],
        'Marketing': ['ads', 'google ads', 'facebook', 'instagram', 'marketing', 'promotion'],
        'Software': ['aws', 'azure', 'github', 'software', 'subscription', 'saas', 'license'],
        'Bank': ['transfer', 'upi', 'neft', 'imps', 'bank', 'interest', 'charges'],
        'Healthcare': ['doctor', 'hospital', 'medical', 'pharmacy', 'medicine', 'clinic'],
        'Entertainment': ['netflix', 'spotify', 'prime', 'hotstar', 'gaming'],
    }

    for category, keywords in rules.items():
        if any(kw in description for kw in keywords):
            return jsonify({"category": category, "confidence": 0.85})

    return jsonify({"category": "Other", "confidence": 0.3})

# ── OCR Invoice Parser ────────────────────────────────────────────────────────
@app.route('/ocr', methods=['POST'])
def ocr_invoice():
    try:
        import pytesseract
        from PIL import Image
        import io, re, base64

        data = request.get_json() or {}
        image_b64 = data.get('image')
        if not image_b64:
            if 'file' not in request.files:
                return jsonify({"error": "No image provided"}), 400
            file_data = request.files['file'].read()
        else:
            file_data = base64.b64decode(image_b64)

        image = Image.open(io.BytesIO(file_data))
        text  = pytesseract.image_to_string(image)

        # Extract amounts and vendor
        amounts = re.findall(r'(?:₹|Rs\.?|INR)\s*([\d,]+(?:\.\d{1,2})?)', text, re.IGNORECASE)
        parsed_amounts = [float(a.replace(',', '')) for a in amounts]
        total = max(parsed_amounts) if parsed_amounts else 0.0

        # Try to find vendor name (first non-empty line)
        lines = [l.strip() for l in text.split('\n') if l.strip()]
        vendor = lines[0] if lines else "Unknown"

        return jsonify({
            "raw_text": text[:500],
            "vendor": vendor,
            "total": total,
            "all_amounts": parsed_amounts
        })
    except ImportError:
        return jsonify({"error": "pytesseract or Pillow not installed"}), 503
    except Exception as e:
        log.error(f"OCR error: {e}")
        return jsonify({"error": str(e)}), 500

# ── AI Health Score Recommendations ──────────────────────────────────────────
@app.route('/health-score-recommendations', methods=['POST'])
def health_score_recommendations():
    """Called by FinancialHealthService to get AI-generated recommendations."""
    data = request.get_json() or {}
    score    = data.get('score', 0)
    breakdown = data.get('breakdown', {})

    prompt = f"""A small business has a financial health score of {score}/100.
Score breakdown: {json.dumps(breakdown)}
Give exactly 3 short, specific, actionable recommendations (each max 2 sentences).
Format as bullet points. Be direct. Use Indian business context."""

    try:
        resp = client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[{"role":"user","content":prompt}],
            max_tokens=300, temperature=0.3
        )
        return jsonify({"recommendations": resp.choices[0].message.content})
    except Exception as e:
        return jsonify({"recommendations": "• Monitor expenses closely.\n• Track all income regularly.\n• Review budget monthly."}), 200

if __name__ == '__main__':
    port = int(os.getenv('PORT', 5001))
    debug = os.getenv('FLASK_DEBUG', 'false').lower() == 'true'
    log.info(f"Starting Finance AI service on port {port}")
    app.run(host='0.0.0.0', port=port, debug=debug)
