# 💼 AI Finance & Accounting Assistant

> An intelligent, full-stack financial management platform powered by Spring Boot, Python AI services, and React — with Gemini 1.5 Flash integration for natural language financial insights.

## 📋 Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Installation & Setup](#installation--setup)
- [Database Setup](#database-setup)
- [Running the Application](#running-the-application)
- [API Documentation](#api-documentation)
- [Environment Variables](#environment-variables)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)

---

## 🌟 Overview

The **AI Finance & Accounting Assistant** is a comprehensive financial management platform designed for businesses and individuals. It combines traditional accounting features with cutting-edge AI capabilities to provide intelligent financial insights, anomaly detection, cash flow forecasting, and natural language interactions.

### What it does:
- 📊 **Track** income, expenses, and transactions in real time
- 🤖 **Chat** with an AI assistant about your finances using natural language
- 🔮 **Forecast** future cash flow using Prophet ML model
- 🚨 **Detect** anomalies and unusual transactions automatically
- 📄 **Parse** invoices using OCR technology
- 📈 **Generate** Profit & Loss reports with caching

---

## 🏗️ Architecture

```
┌─────────────────┐     ┌──────────────────────┐     ┌─────────────────────┐
│  React Frontend │────▶│  Spring Boot Backend  │────▶│  Python AI Service  │
│  (Port 5173)    │     │  (Port 8080)          │     │  (Port 5000)        │
│                 │     │                       │     │                     │
│  Dashboard      │     │  REST API             │     │  Gemini 1.5 Flash   │
│  AI Chat        │     │  Spring Security      │     │  Prophet Forecast   │
│  Transactions   │     │  JPA / Hibernate      │     │  Anomaly Detection  │
└─────────────────┘     │  Flyway Migrations    │     │  OCR Invoice Parse  │
                        │  Redis Cache          │     │  Category Classify  │
                        └──────────┬────────────┘     └─────────────────────┘
                                   │
                    ┌──────────────┼──────────────┐
                    │              │              │
             ┌──────▼─────┐ ┌─────▼──────┐ ┌────▼────────┐
             │ PostgreSQL  │ │   Redis    │ │  RabbitMQ   │
             │  (Port 5432)│ │ (Port 6379)│ │ (Port 5672) │
             └─────────────┘ └────────────┘ └─────────────┘
```

---

## ✨ Features

### Current Features
| Feature | Status | Description |
|---|---|---|
| Transaction Management | ✅ Live | Add, view, and track financial transactions |
| Financial Dashboard | ✅ Live | Real-time income, expense, and net cash flow metrics (Recharts) |
| AI Chat Assistant | ✅ Live | Gemini 1.5 Flash powered financial Q&A |
| Anomaly Detection | ✅ Live | ML-based unusual transaction detection |
| Cash Flow Forecasting | ✅ Live | 30-day Prophet ML forecast |
| Invoice OCR | ✅ Live | Extract data from invoice images |
| P&L Reports | ✅ Live | Profit & Loss with Redis caching |
| CORS Support | ✅ Live | Cross-origin React frontend support |
| Flyway Migrations | ✅ Live | Database version control |

### Current Extended Features
| Feature | Status |
|---|---|
| JWT Authentication (HttpOnly) | ✅ Live |
| Interactive Charts (Recharts) | ✅ Live |
| Invoice Upload UI | ✅ Live |
| Email Notifications | ✅ Live |
| Docker Compose Setup | ✅ Live |
| Forgotten Password Reset | ✅ Live |
| New Sign-up Email Verification | ✅ Live |
| Bank Account Integration (Setu/Plaid)| 📋 Planned |
| Cloud Deployment (AWS) | 📋 Planned |

---

## 🛠️ Tech Stack

### Backend (Spring Boot)
- **Java 21** — LTS version
- **Spring Boot 3.5.11** — Application framework
- **Spring Data JPA** — Database ORM
- **Spring Security** — Authentication & authorization
- **Spring Cache + Redis** — Response caching
- **Spring AMQP + RabbitMQ** — Async message processing
- **Flyway** — Database migrations
- **PostgreSQL** — Primary database
- **Lombok** — Boilerplate reduction

### AI Service (Python Flask)
- **Python 3.10+** — Runtime
- **Flask 3.1** — Web framework
- **Google Gemini 1.5 Flash** — Natural language AI
- **Prophet** — Time series forecasting
- **Scikit-learn** — Machine learning (anomaly detection, categorization)
- **Pytesseract + Pillow** — Invoice OCR
- **Pika** — RabbitMQ client
- **Pandas / NumPy** — Data processing

### Frontend (React)
- **React 18** — UI framework
- **TypeScript** — Type safety
- **Vite** — Build tool
- **Axios** — HTTP client
- **CSS3** — Custom dark theme styling

### Infrastructure
- **PostgreSQL 16.12** — Relational database
- **Redis 3.0** — Caching layer
- **RabbitMQ 3.13** — Message broker (Erlang OTP 26)
- **DBeaver 25.3** — Database management

---

## 📁 Project Structure

```
ai-finance-assistant/
│
├── 📁 finance-backend/                 # Spring Boot Backend
│   ├── src/main/java/com/financeassistant/financeassistant/
│   │   ├── config/
│   │   │   ├── CorsConfig.java         # CORS + Security configuration
│   │   │   └── GlobalExceptionHandler.java
│   │   ├── controller/
│   │   │   └── TransactionController.java
│   │   ├── dto/
│   │   │   ├── TransactionDTO.java
│   │   │   ├── CreateTransactionRequest.java
│   │   │   └── PnLReport.java
│   │   ├── entity/
│   │   │   ├── Transaction.java
│   │   │   ├── Company.java
│   │   │   ├── Account.java
│   │   │   └── Category.java
│   │   ├── repository/
│   │   │   └── TransactionRepository.java
│   │   └── service/
│   │       ├── TransactionService.java
│   │       ├── ReportingService.java
│   │       ├── TransactionEventPublisher.java
│   │       └── CompanySecurityService.java
│   ├── src/main/resources/
│   │   ├── application.yml             # App configuration
│   │   └── db/migration/              # Flyway SQL scripts
│   └── pom.xml
│
├── 📁 finance-ai/                      # Python AI Service
│   ├── app.py                          # Main Flask application
│   ├── category_classifier.py          # ML transaction categorizer
│   ├── anomaly_detector.py             # Isolation Forest anomaly detection
│   ├── ocr_invoice.py                  # Tesseract OCR invoice parser
│   ├── rabbitmq_consumer.py            # Async RabbitMQ consumer
│   ├── .env                            # Environment variables (not committed)
│   ├── requirements.txt                # Python dependencies
│   └── models/                         # Trained ML model files
│
├── 📁 finance-frontend/                # React TypeScript Frontend
│   ├── src/
│   │   ├── components/
│   │   │   ├── Dashboard.tsx           # Financial dashboard
│   │   │   └── ChatAssistant.tsx       # AI chat interface
│   │   ├── api.ts                      # Axios API client
│   │   ├── App.tsx                     # Root component
│   │   └── App.css                     # Global styles
│   ├── package.json
│   └── vite.config.ts
│
└── 📁 database/
    └── V1__init_schema.sql             # Initial database schema
```

---

## 📋 Prerequisites

Make sure you have the following installed:

| Tool | Version | Download |
|---|---|---|
| Java JDK | 21 (LTS) | [adoptium.net](https://adoptium.net) |
| Maven | 3.9+ | Bundled with IntelliJ |
| Python | 3.10+ | [python.org](https://python.org) |
| Node.js | 20+ LTS | [nodejs.org](https://nodejs.org) |
| PostgreSQL | 16.x | [postgresql.org](https://postgresql.org) |
| Redis | 3.0+ | [redis.io](https://redis.io) |
| RabbitMQ | 3.13 | [rabbitmq.com](https://rabbitmq.com) |
| Erlang OTP | 26.x | Required by RabbitMQ |
| Git | Latest | [git-scm.com](https://git-scm.com) |

---

## 🚀 Installation & Setup

### 1. Clone the Repository

```bash
git clone https://github.com/vedantjoshi/ai-finance-assistant.git
cd ai-finance-assistant
```

### 2. Database Setup

Connect to PostgreSQL and run:

```sql
-- Create database
CREATE DATABASE financedb;

-- Connect to financedb then run:
CREATE TABLE users (
    id         BIGSERIAL PRIMARY KEY,
    email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(50)  NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE companies (
    id         BIGSERIAL PRIMARY KEY,
    owner_id   BIGINT       NOT NULL REFERENCES users(id),
    name       VARCHAR(255) NOT NULL,
    currency   VARCHAR(10)  NOT NULL DEFAULT 'USD',
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE accounts (
    id         BIGSERIAL PRIMARY KEY,
    company_id BIGINT        NOT NULL REFERENCES companies(id),
    name       VARCHAR(255)  NOT NULL,
    type       VARCHAR(50)   NOT NULL,
    balance    NUMERIC(19,4) NOT NULL DEFAULT 0,
    currency   VARCHAR(10)   NOT NULL DEFAULT 'USD'
);

CREATE TABLE categories (
    id         BIGSERIAL PRIMARY KEY,
    company_id BIGINT       REFERENCES companies(id),
    name       VARCHAR(255) NOT NULL,
    type       VARCHAR(20)  NOT NULL
);

CREATE TABLE transactions (
    id          BIGSERIAL PRIMARY KEY,
    company_id  BIGINT        NOT NULL REFERENCES companies(id),
    account_id  BIGINT        REFERENCES accounts(id),
    category_id BIGINT        REFERENCES categories(id),
    date        DATE          NOT NULL,
    amount      NUMERIC(19,4) NOT NULL,
    description VARCHAR(512)  NOT NULL,
    source      VARCHAR(50)   NOT NULL DEFAULT 'MANUAL',
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE TABLE invoices (
    id          BIGSERIAL PRIMARY KEY,
    company_id  BIGINT        NOT NULL REFERENCES companies(id),
    vendor      VARCHAR(255),
    total       NUMERIC(19,4),
    file_path   VARCHAR(1024),
    parsed_at   TIMESTAMP,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- Seed initial data
-- Password is hashed with BCrypt
INSERT INTO users (email, password, role)
VALUES ('admin@finance.com', '$2y$10$wB5V.LInM1sE37E4ZqGATuYV7M.kY.RjO0YOf8H1Z8B1fF/T3266O', 'ADMIN');

INSERT INTO companies (owner_id, name, currency)
VALUES (1, 'My Company', 'USD');
```

### 3. Backend Setup (Spring Boot)

```bash
cd finance-backend
```

Update `src/main/resources/application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/financedb
    username: postgres
    password: YOUR_POSTGRES_PASSWORD
```

Run the application:
```bash
./mvnw spring-boot:run
```

Backend starts at: `http://localhost:8080`

### 4. Python AI Service Setup

```bash
cd finance-ai
pip install -r requirements.txt
```

Create `.env` file:
```env
GOOGLE_API_KEY=your-gemini-api-key-here
```

Run the service:
```bash
python app.py
```

AI service starts at: `http://localhost:5000`

### 5. Frontend Setup (React)

```bash
cd finance-frontend
npm install
npm run dev
```

Frontend starts at: `http://localhost:5173`

---

## 🗄️ Database Setup

The project uses **Flyway** for database migrations. Place SQL files in:
```
finance-backend/src/main/resources/db/migration/
```

Naming convention: `V1__description.sql`, `V2__description.sql`

---

## ▶️ Running the Application

Start all services in this order:

```bash
# 1. Start PostgreSQL (Windows Service)
# 2. Start Redis
redis-server

# 3. Start RabbitMQ (Windows Service)

# 4. Start Spring Boot backend
cd finance-backend
./mvnw spring-boot:run

# 5. Start Python AI service
cd finance-ai
python app.py

# 6. Start React frontend
cd finance-frontend
npm run dev
```

Then open: **http://localhost:5173**

---

## 📡 API Documentation

### Transaction Endpoints (Spring Boot - Port 8080)

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/{companyId}/transactions` | Get all transactions |
| `POST` | `/api/v1/{companyId}/transactions` | Create transaction |
| `GET` | `/actuator/health` | Health check |

**Example - Create Transaction:**
```bash
curl -X POST http://localhost:8080/api/v1/1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "date": "2026-02-24",
    "amount": 50000.00,
    "description": "Client Payment"
  }'
```

### AI Service Endpoints (Python Flask - Port 5000)

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/health` | Health check |
| `POST` | `/chat` | AI chat with GPT-4o-mini |
| `POST` | `/forecast` | 30-day cash flow forecast |
| `POST` | `/categorize` | Categorize transaction |
| `POST` | `/anomalies` | Detect anomalies |

**Example - AI Chat:**
```bash
curl -X POST http://localhost:5000/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "What is my net profit this month?"}'
```

**Example - Forecast:**
```bash
curl -X POST http://localhost:5000/forecast \
  -H "Content-Type: application/json" \
  -d '{
    "cash_flow": [
      {"date": "2026-01-01", "amount": 5000},
      {"date": "2026-01-02", "amount": -1200}
    ]
  }'
```

---

## 🔐 Environment Variables

### Python AI Service (`.env`)
```env
GOOGLE_API_KEY=your-gemini-key-here
PLAID_CLIENT_ID=your-plaid-client-id
PLAID_SECRET=your-plaid-secret
PLAID_ENV=sandbox
```

### Spring Boot (`application.yml`)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/financedb
    username: postgres
    password: your-password
  data:
    redis:
      host: localhost
      port: 6379
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

> ⚠️ **Never commit `.env` files or passwords to GitHub!**

---

## 🗺️ Roadmap

- [x] Transaction CRUD API
- [x] Financial Dashboard UI (with Recharts)
- [x] AI Chat Integration (Gemini 1.5 Flash)
- [x] Anomaly Detection (Isolation Forest)
- [x] Cash Flow Forecasting (Prophet)
- [x] Invoice OCR Parser
- [x] Redis Caching
- [x] RabbitMQ Event Publishing
- [x] JWT Authentication
- [x] Interactive Charts (Recharts)
- [x] Invoice Upload UI
- [x] Email Alerts
- [x] Docker Compose
- [ ] Bank Account Integration (Setu/Plaid)
- [ ] Kubernetes Deployment
- [ ] CI/CD Pipeline (GitHub Actions)
- [ ] Cloud Deployment (AWS/GCP)

---

## 🤝 Contributing

1. Fork the repository
2. Create your feature branch: `git checkout -b feature/amazing-feature`
3. Commit your changes: `git commit -m 'Add amazing feature'`
4. Push to the branch: `git push origin feature/amazing-feature`
5. Open a Pull Request

---

## 👨‍💻 Author

**Vedant Joshi**
- GitHub: [@vedantjoshi](https://github.com/vedantjoshi)

---

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgements

- [Spring Boot](https://spring.io/projects/spring-boot)
- [Google Gemini](https://ai.google.dev/)
- [Prophet by Meta](https://facebook.github.io/prophet/)
- [Scikit-learn](https://scikit-learn.org)
- [React](https://react.dev)
- [PostgreSQL](https://www.postgresql.org)
=======
# Ai-Finance-Assistant
Full-stack AI-powered finance &amp; accounting platform built with Spring Boot, Python Flask, and React
