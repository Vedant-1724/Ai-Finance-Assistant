import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30000,
  withCredentials: true,
})

api.interceptors.response.use(
  response => response,
  error => {
    const status = error.response?.status
    const headers = error.config?.headers as Record<string, unknown> | undefined
    const skipRedirect = String(headers?.['X-Skip-401-Redirect'] ?? 'false') === 'true'

    if (status === 401 && !skipRedirect) {
      localStorage.removeItem('auth_user')
      window.location.href = '/login'
    }

    return Promise.reject(error)
  }
)

export default api
