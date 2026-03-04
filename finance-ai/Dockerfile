# PATH: finance-ai/Dockerfile
# FIX: EXPOSE changed from 5000 → 5001 to match app.py PORT default (5001).
#      All three must agree: Dockerfile, app.py, and application.yaml.

FROM python:3.11-slim

# Install Tesseract OCR (required by ocr_invoice.py)
RUN apt-get update && apt-get install -y \
    tesseract-ocr \
    tesseract-ocr-eng \
    libgl1 \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Install Python dependencies first (Docker layer cache — only reruns when
# requirements.txt changes, not on every code change)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application source
COPY . .

# Create models directory for persisted ML model files
RUN mkdir -p models

# FIX: was 5000, now matches app.py PORT env-var default of 5001
EXPOSE 5001

# Healthcheck so Docker Compose / Kubernetes knows when the service is ready
HEALTHCHECK --interval=30s --timeout=10s --start-period=20s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:5001/health')"

CMD ["python", "app.py"]