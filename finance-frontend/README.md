# FinanceAI Frontend

React + TypeScript frontend for the AI Finance & Accounting Assistant.

## What this app covers

- authentication and session restore
- dashboard, transactions, reports, tax, and health views
- statement import and category workflows
- settings, subscription, team invites, and join flow
- provider-aware degraded states for email, payment, and bank sync

## Development

```powershell
cd C:\Users\vedan\ai-finance-assistant\finance-frontend
npm install
npm run dev
```

The Vite dev server runs on `http://localhost:5173` and proxies `/api` requests to `http://localhost:8080`.

## Production Build

```powershell
npm run build
```

Verified successfully on March 10, 2026.

## Backend Expectations

For local development, the frontend expects:

- Spring Boot backend on `http://localhost:8080`
- Python AI service reachable through the backend
- cookie-based auth via backend `/api/v1/auth/*` routes

## Notes

- Registration, password reset, and team invite screens now show direct fallback links when mail is disabled.
- Subscription screens disable paid checkout when Razorpay is not configured.
- Bank sync UI supports both real-provider consent and mock fallback behavior.
