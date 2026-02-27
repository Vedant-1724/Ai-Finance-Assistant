import axios from 'axios'

const api = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 15000,
})

api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      // FIX: No redirect — login page not implemented yet (Feature #7: JWT Auth)
      // Previously this redirected to '/login' which doesn't exist, causing blank page
      console.warn('401 Unauthorized — JWT authentication not yet implemented.')
    }
    return Promise.reject(error)
  }
)

export default api