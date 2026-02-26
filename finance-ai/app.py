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
#  CHAT  (unchanged)
# ─────────────────────────────────────────────────────────────────────────────
@app.route('/chat', methods=['POST', 'OPTIONS'])
def chat():
    if request.method == 'OPTIONS':
        return jsonify({}), 200

    body = request.get_json(force=True, silent=True)
    if not body or 'question' not in body:
        return jsonify({'error': 'Missing question field'}), 400

    try:
        from openai import OpenAI
        client = OpenAI(api_key=os.getenv('OPENAI_API_KEY'))
        response = client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[
                {
                    "role": "system",
                    "content": (
                        "You are an expert AI finance and accounting assistant. "
                        "Help users understand their financial data, income, expenses, "
                        "forecasts, and accounting principles. Be concise and professional."
                    )
                },
                {"role": "user", "content": body['question']}
            ],
            max_tokens=500
        )
        answer = response.choices[0].message.content
        return jsonify({'answer': answer})

    except Exception as e:
        question = body['question'].lower()
        logging.warning(f'OpenAI unavailable: {e}. Using fallback.')

        if any(w in question for w in ['transaction', 'count', 'how many']):
            answer = "Your transactions are stored in the database. Visit the Dashboard tab to see your full transaction list with income and expense totals."
        elif any(w in question for w in ['income', 'revenue', 'earn']):
            answer = "Your income is calculated from all positive transactions. Check the Dashboard for your current total income figure."
        elif any(w in question for w in ['expense', 'spent', 'cost', 'spend']):
            answer = "Your expenses are all negative transactions. The Dashboard shows your total expenses and breakdown by transaction."
        elif any(w in question for w in ['profit', 'net', 'balance']):
            answer = "Net profit = Total Income minus Total Expenses. Check the Net Cash Flow card on your Dashboard for the current figure."
        elif any(w in question for w in ['forecast', 'predict', 'future', 'next month']):
            answer = "Cash flow forecasting requires at least 14 days of transaction data and uses the Prophet ML model. Add more transactions and use the /forecast API endpoint."
        elif any(w in question for w in ['invoice', 'bill']):
            answer = "Invoice processing uses OCR to extract data from uploaded invoice images. This feature uses the Python AI service on port 5000."
        elif any(w in question for w in ['anomaly', 'fraud', 'unusual']):
            answer = "Anomaly detection uses Isolation Forest ML algorithm to flag unusual transactions. It learns from your historical data patterns."
        elif any(w in question for w in ['hello', 'hi', 'hey', 'help']):
            answer = "Hello! I am your Finance Assistant. I can help with questions about income, expenses, profit, forecasting, invoices, and anomaly detection. What would you like to know?"
        elif any(w in question for w in ['tax', 'gst', 'vat']):
            answer = "Tax calculations depend on your jurisdiction. I can help analyze your transaction data to estimate tax obligations. Please consult a tax professional for official advice."
        elif any(w in question for w in ['bank', 'account', 'plaid']):
            answer = "Bank account integration uses Plaid API. Once connected, transactions sync automatically. Currently you can add transactions manually via the API."
        elif any(w in question for w in ['cash', 'flow']):
            answer = "Cash flow is the movement of money in and out of your business. Positive cash flow means more money coming in than going out. Check your Dashboard for current figures."
        elif any(w in question for w in ['save', 'saving', 'reduce']):
            answer = "To reduce expenses: 1) Review all recurring subscriptions, 2) Negotiate vendor contracts, 3) Automate manual processes, 4) Track and categorize all spending carefully."
        elif any(w in question for w in ['invest', 'investment', 'grow']):
            answer = "Investment decisions should be based on your net cash flow and business goals. Ensure you have 3-6 months of operating expenses as reserve before investing surplus funds."
        elif any(w in question for w in ['categor', 'classify', 'train']):
            answer = "The category classifier uses TF-IDF + Random Forest ML trained on labeled transactions. Call POST /train to train it, then POST /categorize to classify any transaction description."
        else:
            answer = "I received your question. To get full AI-powered answers, add OpenAI API credits at platform.openai.com/settings/billing. The service is running correctly and your data is being tracked!"

        return jsonify({'answer': answer})


# ─────────────────────────────────────────────────────────────────────────────
#  FORECAST  (unchanged)
# ─────────────────────────────────────────────────────────────────────────────
@app.route('/forecast', methods=['POST'])
def forecast():
    body = request.get_json(force=True, silent=True)
    if not body or 'cash_flow' not in body:
        return jsonify({'error': 'Request body must contain cash_flow list'}), 400

    data = body['cash_flow']
    if not isinstance(data, list) or len(data) < 14:
        return jsonify({'error': 'cash_flow needs at least 14 entries'}), 400

    try:
        import pandas as pd
        from prophet import Prophet

        df = pd.DataFrame(data)
        df = df.rename(columns={'date': 'ds', 'amount': 'y'})
        df['ds'] = pd.to_datetime(df['ds'])
        df['y']  = pd.to_numeric(df['y'])

        m = Prophet(yearly_seasonality=True, weekly_seasonality=True)
        m.fit(df)
        future     = m.make_future_dataframe(periods=30)
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
#  CATEGORIZE  (unchanged signature — now uses reloadable model)
# ─────────────────────────────────────────────────────────────────────────────
@app.route('/categorize', methods=['POST'])
def categorize():
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
#  CATEGORIZE WITH CONFIDENCE  (new — returns top-3 probabilities)
# ─────────────────────────────────────────────────────────────────────────────
@app.route('/categorize-detail', methods=['POST'])
def categorize_detail():
    """
    POST { "description": "Monthly rent payment" }
    Returns predicted category + top-3 confidence scores.
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
#  TRAIN  (NEW — trains model on transactions_labeled.csv)
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
      "accuracy":     0.96,
      "accuracy_pct": "96.0%",
      "sample_count": 350,
      "train_count":  280,
      "test_count":   70,
      "categories":   [...],
      "report":       "...sklearn classification report...",
      "message":      "Model trained successfully on 350 samples..."
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
#  TRAIN STATUS  (NEW — check if model exists without triggering training)
# ─────────────────────────────────────────────────────────────────────────────
@app.route('/train/status', methods=['GET'])
def train_status():
    """
    GET /train/status
    Returns whether a trained model exists and which categories it knows.
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
            df = pd.read_csv(csv_path).dropna(subset=['description', 'category'])
            categories   = sorted(df['category'].unique().tolist())
            sample_count = len(df)
        except Exception:
            pass

    # Check if current in-memory model is loaded
    try:
        from category_classifier import _pipeline
        model_loaded = _pipeline['model'] is not None
    except Exception:
        model_loaded = False

    return jsonify({
        'model_trained':   model_exists,
        'model_loaded':    model_loaded,
        'csv_available':   csv_exists,
        'sample_count':    sample_count,
        'categories':      categories,
        'model_path':      model_path,
        'csv_path':        csv_path,
        'message': (
            'Model ready. Call POST /categorize to classify transactions.'
            if model_loaded else
            'No model loaded. Call POST /train to train the classifier.'
        ),
    })


# ─────────────────────────────────────────────────────────────────────────────
#  ANOMALIES  (unchanged)
# ─────────────────────────────────────────────────────────────────────────────
@app.route('/anomalies', methods=['POST'])
def anomalies():
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
#  OCR  (NEW — invoice image upload → extracted fields)
# ─────────────────────────────────────────────────────────────────────────────
@app.route('/ocr', methods=['POST'])
def ocr():
    """
    POST /ocr  multipart/form-data
    Field: file  (PNG, JPG, JPEG — PDF needs pdf2image)

    Returns:
    {
      "vendor":     "Acme Corp" | null,
      "date":       "2026-02-25" | null,
      "invoice_no": "INV-001" | null,
      "total":      12500.0 | null,
      "currency":   "INR",
      "raw_text":   "...",
      "note":       "..." | null   ← present only when OCR not installed or PDF issue
    }
    """
    if 'file' not in request.files:
        return jsonify({'error': 'No file uploaded. Send as multipart/form-data with field name "file".'}), 400

    file = request.files['file']

    if file.filename == '' or file.filename is None:
        return jsonify({'error': 'Empty filename. Please select a file.'}), 400

    allowed = {'.png', '.jpg', '.jpeg', '.pdf'}
    ext = os.path.splitext(file.filename.lower())[1]
    if ext not in allowed:
        return jsonify({'error': f'Unsupported file type: {ext}. Use PNG, JPG, or JPEG.'}), 400

    try:
        data = file.read()
        if len(data) == 0:
            return jsonify({'error': 'Uploaded file is empty.'}), 400

        from ocr_invoice import parse_invoice_bytes
        result = parse_invoice_bytes(data, filename=file.filename)
        logging.info('OCR complete for %s — vendor=%s total=%s', file.filename, result.get('vendor'), result.get('total'))
        return jsonify(result)

    except Exception as e:
        logging.error('OCR endpoint failed: %s', e)
        return jsonify({'error': str(e)}), 500


# ─────────────────────────────────────────────────────────────────────────────
if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)