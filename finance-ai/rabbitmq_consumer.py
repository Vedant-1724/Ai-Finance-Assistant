import pika
import json
import logging
import requests
from datetime import datetime

from anomaly_detector import detect_anomalies

logging.basicConfig(level=logging.INFO)

SPRING_BOOT_BASE_URL = "http://localhost:8080"


# ── Fetch real transaction data from Spring Boot ──────────────────────────────
def fetch_transactions(company_id: int, txn_ids: list) -> list:
    """
    Calls Spring Boot REST API to get real transaction data for the given IDs.
    Falls back to an empty list on any error.
    """
    try:
        url = f"{SPRING_BOOT_BASE_URL}/api/v1/{company_id}/transactions?page=0&size=200"
        response = requests.get(url, timeout=5)
        response.raise_for_status()

        all_txns = response.json()

        # Filter to only the IDs we received in the event
        txn_id_set = set(txn_ids)
        filtered = [t for t in all_txns if t.get("id") in txn_id_set]

        # Map to the feature format anomaly_detector expects
        result = []
        for t in filtered:
            date_str = t.get("date", "")
            try:
                dt = datetime.strptime(date_str, "%Y-%m-%d")
                day_of_week = dt.weekday()   # 0=Monday … 6=Sunday
            except Exception:
                day_of_week = 0

            # category is a string name — convert to a stable int via hash
            category_name = t.get("categoryName") or ""
            category_id   = abs(hash(category_name)) % 1000 if category_name else -1

            result.append({
                "id":           t.get("id"),
                "amount":       float(t.get("amount", 0)),
                "day_of_week":  day_of_week,
                "hour":         12,          # no time stored — default midday
                "category_id":  category_id,
            })

        logging.info(f"Fetched {len(result)} transaction(s) for company {company_id}")
        return result

    except Exception as e:
        logging.error(f"Failed to fetch transactions from Spring Boot: {e}")
        return []


# ── Publish anomaly results back to Spring Boot via RabbitMQ ──────────────────
def publish_anomaly_results(channel, company_id: int, anomalies: list):
    payload = json.dumps({
        "companyId": company_id,
        "anomalies": anomalies
    })
    channel.basic_publish(
        exchange="finance.exchange",
        routing_key="anomalies.detected",
        body=payload,
        properties=pika.BasicProperties(
            delivery_mode=2,                         # persistent
            content_type="application/json"
        )
    )
    logging.info(f"Published {len(anomalies)} anomaly result(s) back to Spring Boot")


# ── Message handler ───────────────────────────────────────────────────────────
def on_message(ch, method, properties, body):
    try:
        event      = json.loads(body)
        company_id = event["companyId"]
        txn_ids    = event["txnIds"]

        logging.info(f"Processing {len(txn_ids)} transaction(s) for company {company_id}")

        # 1. Fetch real data
        transactions = fetch_transactions(company_id, txn_ids)

        if not transactions:
            logging.warning("No transactions fetched — skipping anomaly detection")
            ch.basic_ack(delivery_tag=method.delivery_tag)
            return

        # 2. Run Isolation Forest
        anomalies = detect_anomalies(transactions)

        if anomalies:
            logging.warning(f"Found {len(anomalies)} anomaly/anomalies for company {company_id}")
        else:
            logging.info(f"No anomalies for company {company_id}")

        # 3. Publish results back regardless (Spring Boot handles empty list gracefully)
        publish_anomaly_results(ch, company_id, anomalies)

        ch.basic_ack(delivery_tag=method.delivery_tag)

    except Exception as e:
        logging.error(f"Failed to process message: {e}")
        ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)


# ── Consumer startup ──────────────────────────────────────────────────────────
def start_consumer():
    try:
        connection = pika.BlockingConnection(
            pika.ConnectionParameters(host="localhost")
        )
        channel = connection.channel()

        # Declare topology (idempotent — safe to re-run)
        channel.exchange_declare(exchange="finance.exchange", exchange_type="topic", durable=True)
        channel.queue_declare(queue="ai.anomaly.queue",   durable=True)
        channel.queue_declare(queue="ai.anomaly.results", durable=True)
        channel.queue_bind(
            exchange="finance.exchange",
            queue="ai.anomaly.queue",
            routing_key="transactions.new"
        )
        channel.queue_bind(
            exchange="finance.exchange",
            queue="ai.anomaly.results",
            routing_key="anomalies.detected"
        )

        channel.basic_qos(prefetch_count=1)
        channel.basic_consume(
            queue="ai.anomaly.queue",
            on_message_callback=on_message
        )

        logging.info("✅ Anomaly consumer started — waiting for messages...")
        channel.start_consuming()

    except Exception as e:
        logging.error(f"RabbitMQ connection failed: {e}")


if __name__ == "__main__":
    start_consumer()