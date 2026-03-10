# Project Status

Status captured on March 10, 2026.

## Legend

- `Live locally`: implemented and verified in the local repo flow.
- `Provider-ready fallback`: implemented, but requires external credentials to be fully live.
- `Mock fallback`: real provider is optional; repo includes a demo-safe fallback path.
- `Planned`: not completed in this repository.

## Feature Matrix

| Feature | Status | Notes |
| --- | --- | --- |
| Registration, login, logout, session restore | Live locally | Frontend uses backend auth routes and cookie-backed session restore via `/api/v1/auth/me`. |
| Email verification | Live locally | Real email works when mail is enabled; local fallback exposes verification links when mail is disabled. |
| Forgot/reset/change password | Live locally | Reset links are exposed locally when email delivery is disabled. |
| Settings | Live locally | `GET/POST /api/v1/settings` is implemented and used by the frontend. |
| Transactions CRUD | Live locally | Manual add/list/delete flows are wired through the backend. |
| Categories and transaction categorization | Live locally | Category listing is implemented; AI categorization falls back safely when AI service is unavailable. |
| Statement import | Live locally | Manual import works; bank-import UI is connected to bank-sync status/consent flow. |
| Dashboard, reports, tax view, health score | Live locally | Health score payload now includes UI-ready breakdown and delta fields. |
| CSV export | Live locally | Frontend export route matches the backend endpoint. |
| Team invites and join flow | Live locally | `/join` flow is implemented; invites expose manual links when email delivery is off. |
| Trial and subscription gating | Live locally | Subscription status payloads are normalized across auth, payment, and subscription endpoints. |
| Razorpay checkout | Provider-ready fallback | Checkout is disabled unless Razorpay credentials are configured; status payloads expose this clearly. |
| Email notifications | Provider-ready fallback | Real delivery requires mail credentials; local mode is intentionally link-based. |
| Bank sync orchestration | Mock fallback | `AUTO` selects Setu when configured and mock fallback otherwise; consent state is persisted. |
| Live Setu bank sync | Provider-ready fallback | Requires Setu credentials and endpoint configuration. |
| Monitoring stack | Live locally | Compose includes Prometheus and Grafana services. |
| CI/CD, cloud deployment, Kubernetes | Planned | Not implemented end to end in this repo. |

## Verification

Verified on March 10, 2026:

```powershell
cd "C:\Users\vedan\ai-finance-assistant\Finance and Accounting Assistant"
.\mvnw.cmd test

cd "C:\Users\vedan\ai-finance-assistant\finance-frontend"
npm run build
```

Results at that point:

- Backend tests passed.
- Frontend production build passed.
- External-provider features remained intentionally environment-dependent.

## Current Caveats

- Backend tests still emit an upstream Netty/JDK `sun.misc.Unsafe` warning on Java 24.
- The repository includes provider-ready integrations, but production rollout still depends on real credentials and operational setup.

