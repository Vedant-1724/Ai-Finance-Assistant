import pika
import json
import logging
import os
import requests
from datetime import datetime

from anomaly_detector import detect_anomalies

logging.basicConfig(level=logging.INFO)

SPRING_BOOT_BASE_URL = os.environ.get('BACKEND_URL', 'http://localhost:8080')


# ── Fetch real transaction data from Spring Boot ──────────────────────────────
def fetch_transactions(company_id: int, txn_ids: list) -> list:
    """
    Calls Spring Boot REST API to get real transaction data for the given IDs.
    Falls back to an empty list on any error.
    """
    try:
        url = f"{SPRING_BOOT_BASE_URL}/api/v1/{company_id}/transactions"
        resp = requests.get(url, timeout=10)
        resp.raise_for_status()
        all_txns = resp.json()

        # Filter to only the IDs we care about
        txn_id_set = set(txn_ids)
        filtered = [t for t in all_txns if t.get('id') in txn_id_set]
        logging.info("Fetched %d transaction(s) from Spring Boot for company %d",
                     len(filtered), company_id)
        return filtered

    except Exception as e:
        logging.error("Failed to fetch transactions from Spring Boot: %s", e)
        return []


# ── Publish anomaly results back to Spring Boot via RabbitMQ ──────────────────
def publish_anomaly_results(channel, company_id: int, anomalies: list):
    """
    Publishes detected anomalies to the 'ai.anomaly.results' queue so
    Spring Boot's AnomalyResultListener can save them to the DB.
    """
    payload = {
        'companyId': company_id,
        'anomalies': anomalies,
        'detectedAt': datetime.utcnow().isoformat(),
    }
    channel.basic_publish(
        exchange='finance.exchange',
        routing_key='anomalies.detected',
        body=json.dumps(payload),
        properties=pika.BasicProperties(
            delivery_mode=2,          # persistent message
            content_type='application/json',
        )
    )
    logging.info("Published %d anomaly/anomalies for company %d",
                 len(anomalies), company_id)


# ── RabbitMQ message handler ──────────────────────────────────────────────────
def on_message(channel, method, properties, body):
    try:
        event = json.loads(body)
        company_id = int(event.get('companyId', 0))
        txn_ids    = event.get('txnIds', [])

        logging.info("Received transaction event: companyId=%d txnIds=%s",
                     company_id, txn_ids)

        if not txn_ids:
            channel.basic_ack(delivery_tag=method.delivery_tag)
            return

        # Fetch real transaction data from Spring Boot
        transactions = fetch_transactions(company_id, txn_ids)

        if transactions:
            # Run Isolation Forest anomaly detection
            anomalies = detect_anomalies(transactions)
            logging.info("Detected %d anomaly/anomalies for company %d",
                         len(anomalies), company_id)

            # Publish results back to Spring Boot
            publish_anomaly_results(channel, company_id, anomalies)
        else:
            logging.warning("No transaction data returned for company %d — skipping detection",
                            company_id)

        channel.basic_ack(delivery_tag=method.delivery_tag)

    except Exception as e:
        logging.error("Error processing message: %s", e)
        channel.basic_nack(delivery_tag=method.delivery_tag, requeue=False)


# ── Entry point ───────────────────────────────────────────────────────────────
def start_consumer():
    rabbitmq_host = os.environ.get('RABBITMQ_HOST', 'localhost')
    rabbitmq_user = os.environ.get('RABBITMQ_USER', 'guest')
    rabbitmq_pass = os.environ.get('RABBITMQ_PASS', 'guest')

    credentials = pika.PlainCredentials(rabbitmq_user, rabbitmq_pass)
    parameters  = pika.ConnectionParameters(
        host=rabbitmq_host,
        port=5672,
        credentials=credentials,
        heartbeat=600,
        blocked_connection_timeout=300,
    )

    logging.info("Connecting to RabbitMQ at %s ...", rabbitmq_host)
    connection = pika.BlockingConnection(parameters)
    channel    = connection.channel()

    # Declare queue (idempotent — safe to re-declare)
    channel.queue_declare(queue='ai.anomaly.queue', durable=True)
    channel.basic_qos(prefetch_count=1)
    channel.basic_consume(
        queue='ai.anomaly.queue',
        on_message_callback=on_message,
    )

    logging.info("RabbitMQ consumer started. Waiting for messages...")
    channel.start_consuming()


if __name__ == '__main__':
    start_consumer()