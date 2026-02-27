from flask import Flask, request, jsonify
from flask_cors import CORS
from dotenv import load_dotenv
import os
import logging

load_dotenv()
app = Flask(__name__)
CORS(app, origins=["http://localhost:5173", "http://localhost:3000"])
logging.basicConfig(level=logging.INFO)


# ─────────────────────────────────────────────────────────────────────────────
#  HEALTH
# ─────────────────────────────────────────────────────────────────────────────
@app.route('/health', methods=['GET'])
def health():
    return jsonify({'status': 'ok', 'service': 'finance-ai'})


# ─────────────────────────────────────────────────────────────────────────────
#  CHAT  — Powered by Gemini Pro
# ─────────────────────────────────────────────────────────────────────────────
@app.route('/chat', methods=['POST', 'OPTIONS'])
def chat():
    if request.method == 'OPTIONS':
        return jsonify({}), 200

    body = request.get_json(force=True, silent=True)
    if not body or 'question' not in body:
        return jsonify({'error': 'Missing question field'}), 400

    try:
        import google.generativeai as genai

        genai.configure(api_key=os.getenv('GEMINI_API_KEY'))

        model = genai.GenerativeModel(
            model_name='gemini-1.5-pro',
            system_instruction=(
                "You are an expert AI finance and accounting assistant for small and "
                "medium businesses. Help users understand their financial data including "
                "income, expenses, cash flow, forecasts, invoices, and accounting "
                "principles. Always be concise, professional, and actionable. "
                "When discussing numbers, use Indian Rupee (₹) format where relevant."
            )
        )

        response = model.generate_content(body['question'])
        answer   = response.text
        return jsonify({'answer': answer})

    except Exception as e:
        logging.warning(f'Gemini unavailable: {e}. Using fallback.')

        # ── Keyword-based fallback (runs when Gemini API is unreachable) ─────
        question = body['question'].lower()

        if any(w in question for w in ['transaction', 'count', 'how many']):
            answer = (
                "Your transactions are stored in the database. Visit the Dashboard tab "
                "to see your full transaction list with income and expense totals."
            )
        elif any(w in question for w in ['income', 'revenue', 'earn']):
            answer = (
                "Your income is calculated from all positive transactions. "
                "Check the Dashboard for your current total income figure and "
                "the P&L report for a period-wise breakdown."
            )
        elif any(w in question for w in ['expense', 'spent', 'cost', 'spend']):
            answer = (
                "Your expenses are all negative transactions. The Dashboard shows "
                "your total expenses, and the Expense Breakdown chart shows spending "
                "by category."
            )
        elif any(w in question for w in ['profit', 'net', 'balance']):
            answer = (
                "Net profit = Total Income minus Total Expenses. Check the Net Cash "
                "Flow card on your Dashboard for the current figure, or the P&L "
                "Report section for monthly, quarterly, and yearly breakdowns."
            )
        elif any(w in question for w in ['forecast', 'predict', 'future', 'next month']):
            answer = (
                "Cash flow forecasting uses the Prophet ML model and requires at "
                "least 14 days of transaction data. The 6-Month Forecast chart on "
                "your Dashboard shows projected income and expenses based on your "
                "historical patterns."
            )
        elif any(w in question for w in ['invoice', 'bill']):
            answer = (
                "Invoice processing uses OCR to extract vendor, date, invoice number, "
                "and total amount from uploaded invoice images. Go to the Invoices tab, "
                "upload a PNG or JPG of your invoice, review the extracted data, and "
                "save it directly as a transaction."
            )
        elif any(w in question for w in ['anomaly', 'fraud', 'unusual', 'suspicious']):
            answer = (
                "Anomaly detection uses the Isolation Forest ML algorithm to flag "
                "unusual transactions automatically. It analyses amount, day of week, "
                "and category patterns. Flagged transactions are saved and will appear "
                "in the anomaly alerts panel."
            )
        elif any(w in question for w in ['hello', 'hi', 'hey', 'help']):
            answer = (
                "Hello! I am your AI Finance Assistant powered by Gemini Pro. "
                "I can help with questions about income, expenses, profit, cash flow "
                "forecasting, invoice scanning, anomaly detection, and general "
                "accounting. What would you like to know?"
            )
        elif any(w in question for w in ['tax', 'gst', 'vat', 'tds']):
            answer = (
                "Tax calculations depend on your business type and jurisdiction. "
                "I can help analyse your transaction data to estimate GST, TDS, or "
                "income tax obligations based on your income and expense categories. "
                "Always consult a chartered accountant for official filings."
            )
        elif any(w in question for w in ['bank', 'account', 'plaid', 'sync']):
            answer = (
                "Bank account integration is on the roadmap using the Plaid API. "
                "Once connected, transactions will sync automatically. Currently you "
                "can add transactions manually via the Dashboard or by uploading "
                "invoice images in the Invoices tab."
            )
        elif any(w in question for w in ['cash', 'flow']):
            answer = (
                "Cash flow is the movement of money in and out of your business. "
                "Positive cash flow means more money is coming in than going out. "
                "Check the Cash Flow Over Time chart on your Dashboard for a daily "
                "view of income versus expenses."
            )
        elif any(w in question for w in ['save', 'saving', 'reduce', 'cut']):
            answer = (
                "To reduce expenses: "
                "1) Review and cancel unused recurring subscriptions, "
                "2) Negotiate better rates with vendors, "
                "3) Automate manual processes to save labour costs, "
                "4) Track all spending by category using the Expense Breakdown chart, "
                "5) Set monthly budgets for each category and monitor variances."
            )
        elif any(w in question for w in ['invest', 'investment', 'grow', 'surplus']):
            answer = (
                "Investment decisions should be based on your net cash flow position. "
                "Ensure you have at least 3 to 6 months of operating expenses as a "
                "cash reserve before investing surplus funds. Review your Net Cash "
                "Flow trend over the past quarter before committing capital."
            )
        elif any(w in question for w in ['categor', 'classify', 'train', 'label']):
            answer = (
                "The category classifier uses TF-IDF + LinearSVC ML trained on your "
                "labeled transaction data. It achieves around 85% accuracy on financial "
                "transaction descriptions. The model auto-categorises new transactions "
                "when you add them. You can retrain it via the POST /train endpoint "
                "whenever you add new labeled data to transactions_labeled.csv."
            )
        elif any(w in question for w in ['report', 'pnl', 'profit and loss', 'statement']):
            answer = (
                "Profit & Loss reports are available for the current month, quarter, "
                "and year on your Dashboard. Each report shows total income, total "
                "expenses, net profit, and a full category breakdown. Reports are "
                "cached for performance and refresh automatically when you add new "
                "transactions."
            )
        elif any(w in question for w in ['budget', 'limit', 'overspend', 'over budget']):
            answer = (
                "Budget tracking is on the roadmap. The plan is to allow you to set "
                "monthly spend limits per category and receive alerts when you approach "
                "or exceed them. For now, use the P&L category breakdown to manually "
                "review spending against your expected limits."
            )
        else:
            answer = (
                "I received your question. Make sure your GEMINI_API_KEY is correctly "
                "set in the .env file to get full AI-powered contextual answers. "
                "The finance service is running correctly and all your data is being "
                "tracked. Try asking about income, expenses, forecasts, invoices, "
                "or anomaly detection."
            )

        return jsonify({'answer': answer})


# ─────────────────────────────────────────────────────────────────────────────
#  FORECAST  — Prophet 30-day cash flow projection
# ─────────────────────────────────────────────────────────────────────────────
@app.route('/forecast', methods=['POST'])
def forecast():
    """
    POST /forecast
    Body: { "cash_flow": [ {"date": "2026-01-01", "amount": 5000.0}, ... ] }
    Requires at least 14 entries.

    Returns list of 30 forecast points:
    [ {"ds": "2026-03-01", "yhat": 4800.0, "yhat_lower": 3200.0, "yhat_upper": 6400.0}, ... ]
    """
    body = request.get_json(force=True, silent=True)
    if not body or 'cash_flow' not in body:
        return jsonify({'error': 'Request body must contain cash_flow list'}), 400

    data = body['cash_flow']
    if not isinstance(data, list) or len(data) < 14:
        return jsonify({'error': 'cash_flow needs at least 14 entries'}), 400

    try:
        import pandas as pd
        from prophet import Prophet

        df          = pd.DataFrame(data)
        df          = df.rename(columns={'date': 'ds', 'amount': 'y'})
        df['ds']    = pd.to_datetime(df['ds'])
        df['y']     = pd.to_numeric(df['y'])

        m           = Prophet(yearly_seasonality=True, weekly_seasonality=True)
        m.fit(df)
        future      = m.make_future_dataframe(periods=30)
        forecast_df = m.predict(future)

        result = (
            forecast_df[forecast_df['ds'] > df['ds'].max()]
            [['ds', 'yhat', 'yhat_lower', 'yhat_upper']]
            .head(30)
            .assign(ds=lambda x: x['ds'].dt.strftime('%Y-%m-%d'))
            .to_dict(orient='records')
        )
        return jsonify(result)

    except Exception as e:
        logging.error('Forecast failed: %s', e)
        return jsonify({'error': str(e)}), 500


# ─────────────────────────────────────────────────────────────────────────────
#  CATEGORIZE  — Single prediction (no confidence scores)
# ─────────────────────────────────────────────────────────────────────────────
@app.route('/categorize', methods=['POST'])
def categorize():
    """
    POST /categorize
    Body: { "description": "Monthly office rent payment" }
    Returns: { "category": "Office Rent" }
    """
    body = request.get_json(force=True, silent=True)
    if not body or 'description' not in body:
        return jsonify({'error': 'Missing description field'}), 400

    try:
        from category_classifier import predict_category
        category = predict_category(body['description'])
        return jsonify({'category': category})
    except Exception as e:
        logging.error('Categorize failed: %s', e)
        return jsonify({'error': str(e)}), 500


# ─────────────────────────────────────────────────────────────────────────────
#  CATEGORIZE WITH CONFIDENCE  — Top-3 predictions with confidence scores
# ─────────────────────────────────────────────────────────────────────────────
@app.route('/categorize-detail', methods=['POST'])
def categorize_detail():
    """
    POST /categorize-detail
    Body: { "description": "Monthly rent payment" }
    Returns: {
      "category":   "Office Rent",
      "confidence": 0.842,
      "top3": [
        { "category": "Office Rent",  "confidence": 0.842 },
        { "category": "Utilities",    "confidence": 0.091 },
        { "category": "Miscellaneous","confidence": 0.067 }
      ]
    }
    """
    body = request.get_json(force=True, silent=True)
    if not body or 'description' not in body:
        return jsonify({'error': 'Missing description field'}), 400

    try:
        from category_classifier import predict_with_confidence
        result = predict_with_confidence(body['description'])
        return jsonify(result)
    except Exception as e:
        logging.error('Categorize-detail failed: %s', e)
        return jsonify({'error': str(e)}), 500


# ─────────────────────────────────────────────────────────────────────────────
#  TRAIN  — Train category classifier on transactions_labeled.csv
# ─────────────────────────────────────────────────────────────────────────────
@app.route('/train', methods=['POST'])
def train():
    """
    POST /train
    Optional body: { "csv_path": "path/to/custom.csv" }
    Default: uses transactions_labeled.csv in the same directory as app.py

    Returns:
    {
      "status":       "ok",
      "accuracy":     0.855,
      "accuracy_pct": "85.5%",
      "sample_count": 807,
      "train_count":  645,
      "test_count":   162,
      "categories":   ["Consulting Income", "Office Rent", ...],
      "report":       "...sklearn classification report...",
      "message":      "Model trained successfully on 807 samples..."
    }
    """
    body     = request.get_json(force=True, silent=True) or {}
    csv_path = body.get('csv_path', 'transactions_labeled.csv')

    logging.info('Training category classifier from: %s', csv_path)

    try:
        from category_classifier import train_model
        result = train_model(csv_path=csv_path)

        if result.get('status') == 'ok':
            logging.info('Training complete. Accuracy: %s', result.get('accuracy_pct'))
            return jsonify(result), 200
        else:
            logging.error('Training failed: %s', result.get('message'))
            return jsonify(result), 422

    except Exception as e:
        logging.error('Train endpoint crashed: %s', e)
        return jsonify({'status': 'error', 'message': str(e)}), 500


# ─────────────────────────────────────────────────────────────────────────────
#  TRAIN STATUS  — Check model state without triggering training
# ─────────────────────────────────────────────────────────────────────────────
@app.route('/train/status', methods=['GET'])
def train_status():
    """
    GET /train/status
    Returns whether a trained model exists on disk, whether it is loaded
    in memory, and which categories it knows.
    """
    model_path = 'models/category_classifier.joblib'
    csv_path   = 'transactions_labeled.csv'

    model_exists = os.path.exists(model_path)
    csv_exists   = os.path.exists(csv_path)
    categories   = []
    sample_count = 0

    if csv_exists:
        try:
            import pandas as pd
            df           = pd.read_csv(csv_path).dropna(subset=['description', 'category'])
            categories   = sorted(df['category'].unique().tolist())
            sample_count = len(df)
        except Exception:
            pass

    try:
        from category_classifier import _pipeline
        model_loaded = _pipeline['model'] is not None
    except Exception:
        model_loaded = False

    return jsonify({
        'model_trained':  model_exists,
        'model_loaded':   model_loaded,
        'csv_available':  csv_exists,
        'sample_count':   sample_count,
        'categories':     categories,
        'model_path':     model_path,
        'csv_path':       csv_path,
        'message': (
            'Model ready. Call POST /categorize to classify transactions.'
            if model_loaded else
            'No model loaded. Call POST /train to train the classifier.'
        ),
    })


# ─────────────────────────────────────────────────────────────────────────────
#  ANOMALIES  — Direct Isolation Forest detection (sync, no RabbitMQ)
# ─────────────────────────────────────────────────────────────────────────────
@app.route('/anomalies', methods=['POST'])
def anomalies():
    """
    POST /anomalies
    Body: {
      "transactions": [
        { "id": 1, "amount": 50000.0, "day_of_week": 2, "hour": 12, "category_id": 5 },
        ...
      ]
    }
    Returns: { "anomalies": [ ...flagged transaction objects... ] }
    """
    body = request.get_json(force=True, silent=True)
    if not body or 'transactions' not in body:
        return jsonify({'error': 'Missing transactions field'}), 400

    try:
        from anomaly_detector import detect_anomalies
        flagged = detect_anomalies(body['transactions'])
        return jsonify({'anomalies': flagged})
    except Exception as e:
        logging.error('Anomaly detection failed: %s', e)
        return jsonify({'error': str(e)}), 500


# ─────────────────────────────────────────────────────────────────────────────
#  OCR  — Invoice image upload → extracted fields
# ─────────────────────────────────────────────────────────────────────────────
@app.route('/ocr', methods=['POST'])
def ocr():
    """
    POST /ocr  multipart/form-data
    Field: file  (PNG, JPG, JPEG — PDF requires pdf2image + poppler)

    Returns:
    {
      "vendor":     "Acme Corp Pvt Ltd" | null,
      "date":       "2026-02-25"        | null,
      "invoice_no": "INV-2026-0042"     | null,
      "total":      12500.0             | null,
      "currency":   "INR",
      "raw_text":   "full OCR text...",
      "note":       "..."               | null
    }
    """
    if 'file' not in request.files:
        return jsonify({
            'error': 'No file uploaded. Send as multipart/form-data with field name "file".'
        }), 400

    file = request.files['file']

    if not file.filename:
        return jsonify({'error': 'Empty filename. Please select a file.'}), 400

    allowed = {'.png', '.jpg', '.jpeg', '.pdf'}
    ext     = os.path.splitext(file.filename.lower())[1]
    if ext not in allowed:
        return jsonify({
            'error': f'Unsupported file type: {ext}. Use PNG, JPG, or JPEG.'
        }), 400

    try:
        data = file.read()
        if len(data) == 0:
            return jsonify({'error': 'Uploaded file is empty.'}), 400

        from ocr_invoice import parse_invoice_bytes
        result = parse_invoice_bytes(data, filename=file.filename)
        logging.info(
            'OCR complete for %s — vendor=%s total=%s',
            file.filename, result.get('vendor'), result.get('total')
        )
        return jsonify(result)

    except Exception as e:
        logging.error('OCR endpoint failed: %s', e)
        return jsonify({'error': str(e)}), 500


# ─────────────────────────────────────────────────────────────────────────────
if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)