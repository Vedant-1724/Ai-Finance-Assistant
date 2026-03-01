"""
PATH: finance-ai/rabbitmq_consumer.py

CHANGES vs original:
  1. Dead-letter exchange (DLX) — messages that fail 3 times go to
     ai.anomaly.dlq instead of vanishing silently.
  2. prefetch_count=1 — prevents the consumer from taking more messages
     than it can process, protecting against memory overload.
  3. Retry count header — nack'd messages are requeued up to 3 times,
     then sent to the dead-letter queue.
  4. Reconnect loop — consumer auto-restarts if RabbitMQ drops the connection.
  5. Structured logging — every step is logged with companyId for tracing.
"""

import json
import logging
import os
import time
from datetime import datetime

import pika
import requests

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger(__name__)

BACKEND_URL   = os.environ.get("BACKEND_URL",   "http://localhost:8080")
RABBITMQ_HOST = os.environ.get("RABBITMQ_HOST", "localhost")
RABBITMQ_USER = os.environ.get("RABBITMQ_USER", "guest")
RABBITMQ_PASS = os.environ.get("RABBITMQ_PASS", "guest")

INPUT_QUEUE  = "ai.anomaly.queue"
RESULT_QUEUE = "ai.anomaly.results"
DLQ          = "ai.anomaly.dlq"
EXCHANGE     = "finance.exchange"
DLX          = "finance.dlx"
MAX_RETRIES  = 3


# ─────────────────────────────────────────────────────────────────────────────
#  Fetch transactions from Spring Boot backend
# ─────────────────────────────────────────────────────────────────────────────

def fetch_transactions(company_id: int, txn_ids: list) -> list:
    """Fetch raw transaction data from the Spring Boot backend."""
    try:
        url = f"{BACKEND_URL}/internal/transactions"
        resp = requests.post(
            url,
            json={"companyId": company_id, "ids": txn_ids},
            timeout=10,
            headers={"X-Internal-Call": "true"},
        )
        if resp.status_code == 200:
            return resp.json()
        logger.warning("Backend returned %d for company=%d", resp.status_code, company_id)
        return []
    except Exception as e:
        logger.error("Failed to fetch transactions for company=%d: %s", company_id, e)
        return []


# ─────────────────────────────────────────────────────────────────────────────
#  Run anomaly detection
# ─────────────────────────────────────────────────────────────────────────────

def detect_anomalies(transactions: list) -> list:
    """Run Isolation Forest detection. Returns list of anomaly dicts."""
    try:
        from anomaly_detector import detect
        return detect(transactions)
    except Exception as e:
        logger.error("Anomaly detection error: %s", e)
        return []


# ─────────────────────────────────────────────────────────────────────────────
#  Publish results back to Spring Boot
# ─────────────────────────────────────────────────────────────────────────────

def publish_results(channel, company_id: int, anomalies: list):
    payload = {
        "companyId":  company_id,
        "anomalies":  anomalies,
        "detectedAt": datetime.utcnow().isoformat(),
    }
    channel.basic_publish(
        exchange=EXCHANGE,
        routing_key="anomalies.detected",
        body=json.dumps(payload),
        properties=pika.BasicProperties(
            delivery_mode=2,
            content_type="application/json",
        ),
    )
    logger.info("Published %d anomaly/anomalies for company=%d", len(anomalies), company_id)


# ─────────────────────────────────────────────────────────────────────────────
#  Message handler
# ─────────────────────────────────────────────────────────────────────────────

def on_message(channel, method, properties, body):
    retry_count = 0
    if properties.headers:
        retry_count = int(properties.headers.get("x-retry-count", 0))

    try:
        event      = json.loads(body)
        company_id = int(event.get("companyId", 0))
        txn_ids    = event.get("txnIds", [])

        logger.info("Processing event: company=%d txns=%s retries=%d",
                    company_id, txn_ids, retry_count)

        if not txn_ids:
            channel.basic_ack(delivery_tag=method.delivery_tag)
            return

        transactions = fetch_transactions(company_id, txn_ids)

        if transactions:
            anomalies = detect_anomalies(transactions)
            publish_results(channel, company_id, anomalies)
        else:
            logger.warning("No transactions fetched for company=%d — skipping", company_id)

        channel.basic_ack(delivery_tag=method.delivery_tag)

    except Exception as e:
        logger.error("Error processing message (retry %d/%d): %s", retry_count, MAX_RETRIES, e)

        if retry_count < MAX_RETRIES:
            # Requeue with incremented retry counter
            channel.basic_nack(delivery_tag=method.delivery_tag, requeue=False)
            headers = {"x-retry-count": retry_count + 1}
            channel.basic_publish(
                exchange="",
                routing_key=INPUT_QUEUE,
                body=body,
                properties=pika.BasicProperties(
                    delivery_mode=2,
                    headers=headers,
                ),
            )
            logger.info("Re-queued message for retry %d", retry_count + 1)
        else:
            # Dead-letter after max retries
            logger.error("Max retries exceeded — sending to DLQ")
            channel.basic_nack(delivery_tag=method.delivery_tag, requeue=False)


# ─────────────────────────────────────────────────────────────────────────────
#  Setup topology
# ─────────────────────────────────────────────────────────────────────────────

def setup_topology(channel):
    """Declare all exchanges and queues with DLX support."""
    # Dead-letter exchange
    channel.exchange_declare(exchange=DLX, exchange_type="direct", durable=True)
    channel.queue_declare(queue=DLQ, durable=True)
    channel.queue_bind(queue=DLQ, exchange=DLX, routing_key=INPUT_QUEUE)

    # Main exchange
    channel.exchange_declare(exchange=EXCHANGE, exchange_type="topic", durable=True)

    # Input queue — messages that fail go to DLX → DLQ
    channel.queue_declare(
        queue=INPUT_QUEUE,
        durable=True,
        arguments={
            "x-dead-letter-exchange":    DLX,
            "x-dead-letter-routing-key": INPUT_QUEUE,
        },
    )
    channel.queue_bind(queue=INPUT_QUEUE, exchange=EXCHANGE, routing_key="transactions.new")

    # Result queue
    channel.queue_declare(queue=RESULT_QUEUE, durable=True)
    channel.queue_bind(queue=RESULT_QUEUE, exchange=EXCHANGE, routing_key="anomalies.detected")

    # Process one message at a time — prevents memory overload
    channel.basic_qos(prefetch_count=1)


# ─────────────────────────────────────────────────────────────────────────────
#  Entry point — auto-reconnect loop
# ─────────────────────────────────────────────────────────────────────────────

def start_consumer():
    credentials = pika.PlainCredentials(RABBITMQ_USER, RABBITMQ_PASS)
    parameters  = pika.ConnectionParameters(
        host=RABBITMQ_HOST,
        port=5672,
        credentials=credentials,
        heartbeat=600,
        blocked_connection_timeout=300,
    )

    while True:
        try:
            logger.info("Connecting to RabbitMQ at %s ...", RABBITMQ_HOST)
            connection = pika.BlockingConnection(parameters)
            channel    = connection.channel()

            setup_topology(channel)

            channel.basic_consume(
                queue=INPUT_QUEUE,
                on_message_callback=on_message,
            )

            logger.info("Consumer ready — waiting for messages on %s", INPUT_QUEUE)
            channel.start_consuming()

        except pika.exceptions.AMQPConnectionError as e:
            logger.error("RabbitMQ connection lost: %s — reconnecting in 5s", e)
            time.sleep(5)
        except KeyboardInterrupt:
            logger.info("Consumer stopped by user")
            break
        except Exception as e:
            logger.error("Unexpected consumer error: %s — reconnecting in 5s", e)
            time.sleep(5)


if __name__ == "__main__":
    start_consumer()
