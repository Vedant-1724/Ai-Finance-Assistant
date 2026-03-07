// PATH: finance-frontend/src/api.ts
//
// CHANGES vs original:
//  1. 402 Payment Required interceptor — redirects to /subscription
//     This fires when SubscriptionFilter.java blocks an expired user.
//  2. All other logic unchanged (JWT auto-injection, 401 auto-logout)

import axios from 'axios'

const api = axios.create({
  baseURL: '',    // relative — nginx or Vite proxy handles routing
  timeout: 30000,
})

// ── Send cookies with every request ──────────────────────────────────────────
api.defaults.withCredentials = true;

// ── Attach JWT to every request ───────────────────────────────────────────────
api.interceptors.request.use(config => {
  return config
})

// ── Handle auth and subscription errors ──────────────────────────────────────
api.interceptors.response.use(
  res => res,
  err => {
    const status = err.response?.status

    if (status === 401) {
      // Token expired or invalid → force login
      localStorage.removeItem('auth_user')
      window.location.href = '/login'
    }

    if (status === 402) {
      // Subscription expired → redirect to upgrade page
      // SubscriptionFilter.java returns 402 for expired/cancelled users
      window.location.href = '/subscription'
    }

    return Promise.reject(err)
  }
)

export default api
