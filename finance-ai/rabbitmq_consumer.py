import pika
import json
import logging
from anomaly_detector import detect_anomalies

logging.basicConfig(level=logging.INFO)


def fetch_transactions(txn_ids: list) -> list:
    # Placeholder — replace with actual DB or API call
    return [
        {
            'id': tid,
            'amount': 0,
            'day_of_week': 0,
            'hour': 12,
            'category_id': 1
        }
        for tid in txn_ids
    ]


def on_message(ch, method, properties, body):
    try:
        event = json.loads(body)
        company_id = event['companyId']
        txn_ids = event['txnIds']

        logging.info(f"Processing {len(txn_ids)} transactions for company {company_id}")

        transactions = fetch_transactions(txn_ids)
        anomalies = detect_anomalies(transactions)

        if anomalies:
            logging.warning(f"Found {len(anomalies)} anomalies for company {company_id}")

        ch.basic_ack(delivery_tag=method.delivery_tag)

    except Exception as e:
        logging.error(f"Failed to process message: {e}")
        ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)


def start_consumer():
    try:
        connection = pika.BlockingConnection(
            pika.ConnectionParameters(host='localhost')
        )
        channel = connection.channel()
        channel.exchange_declare(exchange='finance.exchange', exchange_type='topic')
        channel.queue_declare(queue='ai.anomaly.queue', durable=True)
        channel.queue_bind(
            exchange='finance.exchange',
            queue='ai.anomaly.queue',
            routing_key='transactions.new'
        )
        channel.basic_qos(prefetch_count=1)
        channel.basic_consume(
            queue='ai.anomaly.queue',
            on_message_callback=on_message
        )
        logging.info("Waiting for messages...")
        channel.start_consuming()

    except Exception as e:
        logging.error(f"RabbitMQ connection failed: {e}")


if __name__ == '__main__':
    start_consumer()