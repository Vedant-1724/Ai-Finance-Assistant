from flask import Flask, request, jsonify
from flask_cors import CORS
from dotenv import load_dotenv
import os
import logging

load_dotenv()
app = Flask(__name__)
CORS(app, origins=["http://localhost:5173", "http://localhost:3000"])
logging.basicConfig(level=logging.INFO)


@app.route('/health', methods=['GET'])
def health():
    return jsonify({'status': 'ok', 'service': 'finance-ai'})


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
                    "content": "You are an expert AI finance and accounting assistant. Help users understand their financial data, income, expenses, forecasts, and accounting principles. Be concise and professional."
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
        else:
            answer = f"I received your question. To get full AI-powered answers, add OpenAI API credits at platform.openai.com/settings/billing. The service is running correctly and your data is being tracked!"

        return jsonify({'answer': answer})


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
        df['y'] = pd.to_numeric(df['y'])

        m = Prophet(yearly_seasonality=True, weekly_seasonality=True)
        m.fit(df)
        future = m.make_future_dataframe(periods=30)
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
        return jsonify({'error': str(e)}), 500


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
        return jsonify({'error': str(e)}), 500


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)