# AI Finance & Accounting Assistant

Status as of March 10, 2026: core product flows are locally functional, external providers degrade safely when not configured, and the verified build/test commands are `npm run build` for the frontend and `./mvnw test` for the backend.

## Overview

This repository contains a three-part finance application:

- `finance-frontend`: React + TypeScript frontend served by Vite in development.
- `Finance and Accounting Assistant`: Spring Boot backend for auth, transactions, reporting, billing state, team management, and bank-sync orchestration.
- `finance-ai`: Python Flask AI service for chat, categorization, anomaly detection, OCR, and forecasting.

## Current Status

For a fuller matrix, see [PROJECT_STATUS.md](PROJECT_STATUS.md).

| Area | Status | Notes |
| --- | --- | --- |
| Auth, sessions, verify-email, forgot/reset password | Live locally | Cookie-backed auth with fallback links when email delivery is disabled. |
| Settings, transactions, dashboard, exports, tax and health views | Live locally | Frontend and backend routes are aligned. |
| Team invites and join flow | Live locally | Invite emails fall back to manual invite links when mail is disabled. |
| AI chat, OCR, categorization, forecasting, anomaly detection | Live when AI service is running | Requires `finance-ai` plus valid AI provider configuration. |
| Subscription and trial gating | Live locally | Trial, limits, and status payloads are consistent across auth/payment/subscription endpoints. |
| Razorpay payments | Provider-ready fallback | Disabled cleanly unless Razorpay keys are configured. |
| Bank sync | Provider-ready with mock fallback | `AUTO` uses Setu when configured, otherwise mock/demo flow. |
| Cloud deployment, CI/CD, Kubernetes | Planned | Not completed in this repository. |

## Repository Layout

```text
ai-finance-assistant/
|- Finance and Accounting Assistant/   Spring Boot backend
|- finance-ai/                         Python AI service
|- finance-frontend/                   React frontend
|- docker-compose.yml                  Local multi-service stack
|- .env.example                        Sample root environment file
`- PROJECT_STATUS.md                   In-repo feature status matrix
```

## Verified Commands

These commands were re-verified on March 10, 2026:

```powershell
cd "C:\Users\vedan\ai-finance-assistant\Finance and Accounting Assistant"
.\mvnw.cmd test

cd "C:\Users\vedan\ai-finance-assistant\finance-frontend"
npm run build
```

## Local Development

### 1. Copy environment variables

Copy `.env.example` to `.env` and fill in the values you actually want to enable.

Important behavior:

- Leave Razorpay keys blank to keep online payments disabled.
- Leave Setu credentials blank to keep bank sync in mock fallback mode when `BANK_SYNC_PROVIDER=AUTO`.
- Keep `MAIL_ENABLED=false` to use local verification/reset/invite links instead of real outbound email.

### 2. Start infrastructure

You can either run dependencies manually or use Docker Compose.

```powershell
docker compose up -d postgres redis rabbitmq
```

Optional stack services in `docker-compose.yml` also include `frontend`, `backend`, `ai-service`, `prometheus`, and `grafana`.

### 3. Run the backend

```powershell
cd "Finance and Accounting Assistant"
.\mvnw.cmd spring-boot:run
```

The backend defaults to `http://localhost:8080`.

### 4. Run the AI service

```powershell
cd finance-ai
python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt
python app.py
```

The AI service defaults to `http://localhost:5001`.

### 5. Run the frontend

```powershell
cd finance-frontend
npm install
npm run dev
```

The frontend runs on `http://localhost:5173` and proxies `/api` requests to `http://localhost:8080` in development.

## Key Configuration

### Required for basic local app startup

- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET`
- `INTERNAL_API_KEY`

### Required for AI-powered features

- `GOOGLE_API_KEY`
- `AI_SERVICE_URL` if you are not using the default `http://localhost:5001`

### Optional provider integrations

- `RAZORPAY_KEY_ID`
- `RAZORPAY_KEY_SECRET`
- `RAZORPAY_WEBHOOK_SECRET`
- `MAIL_ENABLED`
- `MAIL_HOST`
- `MAIL_PORT`
- `MAIL_USERNAME`
- `MAIL_PASSWORD`
- `MAIL_FROM`
- `MAIL_EXPOSE_LINKS_WHEN_DISABLED`
- `BANK_SYNC_PROVIDER` with `AUTO`, `SETU`, or `MOCK`
- `SETU_BASE_URL`
- `SETU_CLIENT_ID`
- `SETU_CLIENT_SECRET`
- `SETU_CALLBACK_URL`
- `APP_BASE_URL`

## Runtime Notes

- Email-disabled environments now expose verification, reset, and invite links directly in the app so local development is not blocked.
- Payment status endpoints expose whether billing is configured, allowing the frontend to disable checkout buttons before the user clicks them.
- Bank sync persists consent state and supports both a real Setu path and a safe mock fallback path.
- Backend tests use an isolated H2 test profile; they do not require PostgreSQL, RabbitMQ, or Redis to be running.

## Known Non-Blocking Warnings

- Backend tests still emit an upstream Netty/JDK `sun.misc.Unsafe` warning on Java 24.

## Remaining Roadmap

- CI/CD automation
- Cloud deployment packaging and infrastructure
- Production-grade bank/payment/email credentials and operational rollout
- Broader frontend smoke and e2e coverage beyond the current build and focused backend tests

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).

