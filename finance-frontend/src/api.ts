import axios from 'axios'

const api = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 15000,
})

// ── Request interceptor — attach JWT from localStorage ────────────────────────
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// ── Response interceptor — handle 401 by redirecting to login ─────────────────
api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      // Clear stored credentials
      localStorage.removeItem('token')
      localStorage.removeItem('email')
      localStorage.removeItem('companyId')
      // Redirect to login page (now that React Router has a /login route)
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default api