import axios from 'axios'

// In Docker: nginx proxies /api/ → backend:8080 and /ai/ → ai-service:5000
// In Dev: vite.config.ts proxy handles /api/ → localhost:8080
// So we NEVER hardcode localhost — always use relative paths

const api = axios.create({
  baseURL: '',   // relative — nginx or Vite proxy handles routing
  timeout: 30000,
})

// Attach JWT token to every request automatically
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Auto-logout if backend returns 401 Unauthorized
api.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('email')
      localStorage.removeItem('companyId')
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

export default api