# ─── Python AI Service ───────────────────────────────────────────────────────
FROM python:3.11-slim

# Install Tesseract OCR (required by ocr_invoice.py)
RUN apt-get update && apt-get install -y \
    tesseract-ocr \
    tesseract-ocr-eng \
    libgl1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Install Python dependencies (cached layer — only reruns when requirements.txt changes)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application source
COPY . .

# Create models directory if it doesn't exist
RUN mkdir -p models

EXPOSE 5000

CMD ["python", "app.py"]
